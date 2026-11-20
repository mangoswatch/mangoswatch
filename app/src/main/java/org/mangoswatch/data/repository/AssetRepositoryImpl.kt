package org.mangoswatch.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mangoswatch.domain.model.AssetSyncError
import org.mangoswatch.domain.model.AssetSyncProgress
import org.mangoswatch.domain.model.AssetSyncState
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.domain.model.AssetSync
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class AssetRepositoryImpl(
    private val context: Context,
    private val assetSync: AssetSync,
    private val assetFetcher: AssetFetcher = HttpAssetFetcher
) : AssetRepository {

    private val GITHUB_PAGES_BASE_URL = "https://mangoswatch.github.io/mangoswatch/data/"
    private val INDEX_URL = "${GITHUB_PAGES_BASE_URL}index.json"
    private val PREFS_NAME = "mangoswatch_remote_prefs"
    private val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    private val WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L
    private val ASSET_FILENAME_REGEX =
        Regex("^[a-z0-9_]+_(ipv4|ipv6)\\.txt$|^[a-z0-9_]+_taxonomy\\.json$")
    private val MAX_ASSET_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB

    init {
        if (!isCachePopulated() || isCacheStale()) {
            assetSync.updateState(AssetSyncState.SyncNeeded)
        } else {
            assetSync.updateState(AssetSyncState.Synced)
        }
    }

    override suspend fun syncAssets() = withContext(Dispatchers.IO) {
        assetSync.updateState(AssetSyncState.Syncing())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            val remoteFilesList = fetchRemoteAssetList()
            val cacheDir = getAssetsCacheDir()
            assetSync.updateState(
                AssetSyncState.Syncing(progress = AssetSyncProgress(0, remoteFilesList.size))
            )
            for ((index, fileName) in remoteFilesList.withIndex()) {
                downloadAsset(fileName, cacheDir)
                assetSync.updateState(
                    AssetSyncState.Syncing(
                        progress = AssetSyncProgress(index + 1, remoteFilesList.size)
                    )
                )
            }

            prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis()) }
            assetSync.updateState(AssetSyncState.Synced)
        } catch (e: kotlinx.coroutines.CancellationException) {
            assetSync.updateState(AssetSyncState.Error(AssetSyncError.Cancelled))
            throw e
        } catch (e: AssetSyncFailure) {
            Log.e("AssetRepositoryImpl", "Asset sync failed: ${e.syncError}", e)
            assetSync.updateState(AssetSyncState.Error(e.syncError))
        } catch (e: Exception) {
            Log.e("AssetRepositoryImpl", "Asset sync failed", e)
            assetSync.updateState(AssetSyncState.Error(AssetSyncError.Unexpected))
        }
    }

    override fun isCachePopulated(): Boolean = getTaxonomyFiles().isNotEmpty()

    override fun getTaxonomyFiles(): List<String> {
        val cacheDir = getAssetsCacheDir()
        return cacheDir.listFiles()
            ?.filter {
                it.isFile && it.length() > 0L && isValidFileName(it.name) &&
                    it.name.endsWith("_taxonomy.json")
            }
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }

    override fun getFileFromAssetsCache(fileName: String): InputStream {
        if (!isValidFileName(fileName)) {
            Log.w("AssetRepository", "Access denied to unauthorized asset filename: $fileName")
            return "".byteInputStream()
        }
        val cacheDir = getAssetsCacheDir()
        val cachedFile = File(cacheDir, fileName)

        if (!isSafeFile(cachedFile, cacheDir)) {
            Log.e("AssetRepository", "Path traversal attempt detected on getAsset: $fileName")
            return "".byteInputStream()
        }

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            try {
                return cachedFile.inputStream()
            } catch (e: Exception) {
                Log.e("AssetRepository", "Failed to read cached asset file: $fileName", e)
            }
        }
        return "".byteInputStream()
    }

    private fun fetchRemoteAssetList(): List<String> {
        val connection = try {
            assetFetcher.openConnection(URL(INDEX_URL))
        } catch (e: Exception) {
            throw AssetSyncFailure(AssetSyncError.IndexDownloadFailed, e)
        }
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw AssetSyncFailure(AssetSyncError.IndexDownloadFailed)
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = try {
                JSONArray(jsonString)
            } catch (e: JSONException) {
                throw AssetSyncFailure(AssetSyncError.InvalidIndex, e)
            }
            if (jsonArray.length() == 0) {
                throw AssetSyncFailure(AssetSyncError.InvalidIndex)
            }

            val assetList = linkedSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val fileName = try {
                    jsonArray.getString(i)
                } catch (e: JSONException) {
                    throw AssetSyncFailure(AssetSyncError.InvalidIndex, e)
                }
                if (!isValidFileName(fileName)) {
                    throw AssetSyncFailure(AssetSyncError.InvalidIndex)
                }
                assetList.add(fileName)
            }
            return assetList.toList()
        } catch (e: AssetSyncFailure) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AssetSyncFailure(AssetSyncError.IndexDownloadFailed, e)
        } finally {
            connection.disconnect()
        }
    }

    private class AssetSyncFailure(
        val syncError: AssetSyncError,
        cause: Throwable? = null
    ) : IOException("Asset synchronization failed: $syncError", cause)

    private fun downloadAsset(fileName: String, cacheDir: File) {
        val url = URL("$GITHUB_PAGES_BASE_URL$fileName")
        val connection = try {
            assetFetcher.openConnection(url)
        } catch (e: Exception) {
            throw AssetSyncFailure(
                AssetSyncError.AssetDownloadFailed(fileName),
                e
            )
        }
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val tempFile = File(cacheDir, "$fileName.tmp")
        val finalFile = File(cacheDir, fileName)
        try {
            if (!isSafeFile(tempFile, cacheDir) || !isSafeFile(finalFile, cacheDir)) {
                throw IOException("Asset path is outside the cache directory")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Asset request failed with HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    var totalBytes = 0L
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > MAX_ASSET_SIZE_BYTES) {
                            throw IOException("Asset file exceeds max size")
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            validateAssetContent(tempFile, fileName)
            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Failed to move downloaded asset into cache")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: AssetSyncFailure) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw AssetSyncFailure(
                AssetSyncError.AssetDownloadFailed(fileName),
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validateAssetContent(file: File, fileName: String) {
        when {
            fileName.endsWith("_ipv4.txt") -> validateCidrFile(file, fileName, isIpv6 = false)
            fileName.endsWith("_ipv6.txt") -> validateCidrFile(file, fileName, isIpv6 = true)
            fileName.endsWith("_taxonomy.json") -> validateTaxonomyFile(file, fileName)
            else -> throw AssetSyncFailure(AssetSyncError.InvalidAssetContent(fileName))
        }
    }

    private fun validateCidrFile(file: File, fileName: String, isIpv6: Boolean) {
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val cidr = line.trim()
                    if (cidr.isEmpty() || cidr.startsWith("#")) return@forEachIndexed
                    if (!isValidCidr(cidr, isIpv6)) {
                        throw IOException("Invalid CIDR on line ${index + 1}")
                    }
                }
            }
        } catch (e: Exception) {
            throw AssetSyncFailure(AssetSyncError.InvalidAssetContent(fileName), e)
        }
    }

    private fun isValidCidr(cidr: String, isIpv6: Boolean): Boolean {
        val slash = cidr.indexOf('/')
        if (slash <= 0 || slash != cidr.lastIndexOf('/')) return false

        val address = cidr.substring(0, slash)
        val prefix = cidr.substring(slash + 1)
        if (prefix.isEmpty() || prefix.any { it !in '0'..'9' }) return false
        val prefixLength = prefix.toIntOrNull() ?: return false

        return if (isIpv6) {
            prefixLength in 0..128 && isValidIpv6(address)
        } else {
            prefixLength in 0..32 && isValidIpv4(address)
        }
    }

    private fun isValidIpv4(address: String): Boolean {
        val octets = address.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() &&
                octet.length <= 3 &&
                octet.all { it in '0'..'9' } &&
                octet.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isValidIpv6(address: String): Boolean {
        return !(!address.contains(':') || address.contains('%')) && try {
            val byteCount = InetAddress.getByName(address).address.size
            byteCount == 4 || byteCount == 16
        } catch (_: Exception) {
            false
        }
    }

    private fun validateTaxonomyFile(file: File, fileName: String) {
        try {
            val root = JSONObject(file.readText())
            requiredString(root, "provider")
            val endpoints = root.opt("endpoints") as? JSONArray
                ?: throw IOException("Taxonomy endpoints must be an array")

            for (index in 0 until endpoints.length()) {
                val endpoint = endpoints.opt(index) as? JSONObject
                    ?: throw IOException("Taxonomy endpoint must be an object")
                requiredString(endpoint, "id")
                val hostnamePattern = requiredString(endpoint, "hostname_pattern")
                requiredString(endpoint, "display_name")
                val category = requiredString(endpoint, "category")
                if (EndpointTaxonomyCategory.entries.none { it.name == category }) {
                    throw IOException("Unknown taxonomy category")
                }
                requiredString(endpoint, "description")
                val dataCollected = endpoint.opt("data_collected") as? JSONArray
                    ?: throw IOException("Taxonomy data_collected must be an array")
                if ((0 until dataCollected.length()).any { dataCollected.opt(it) !is String }) {
                    throw IOException("Taxonomy data_collected entries must be strings")
                }
                if (endpoint.has("opt_out_description") &&
                    endpoint.opt("opt_out_description") !is String
                ) {
                    throw IOException("Taxonomy opt_out_description must be a string")
                }
                try {
                    Pattern.compile(hostnamePattern, Pattern.CASE_INSENSITIVE)
                } catch (e: PatternSyntaxException) {
                    throw IOException("Invalid taxonomy hostname pattern", e)
                }
            }
        } catch (e: Exception) {
            throw AssetSyncFailure(AssetSyncError.InvalidAssetContent(fileName), e)
        }
    }

    private fun requiredString(json: JSONObject, key: String): String {
        val value = json.opt(key) as? String
            ?: throw IOException("Taxonomy field '$key' must be a string")
        if (value.isBlank()) throw IOException("Taxonomy field '$key' must not be blank")
        return value
    }

    private fun isCacheStale(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastSyncTimestamp > WEEK_IN_MILLIS
    }
    private fun isValidFileName(fileName: String): Boolean = ASSET_FILENAME_REGEX.matches(fileName)

    private fun isSafeFile(file: File, cacheDir: File): Boolean {
        return try {
            file.canonicalPath.startsWith(cacheDir.canonicalPath)
        } catch (_: Exception) {
            false
        }
    }

    private fun getAssetsCacheDir(): File {
        val dir = File(context.cacheDir, "assets_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}



package org.mangoswatch.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mangoswatch.domain.repository.AssetRepository
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class AssetsSyncState {
    SYNCING, SYNCED, SYNC_NEEDED, ERROR
}

class AssetSync {
    private val _assetSyncState = MutableStateFlow(AssetsSyncState.SYNCING)
    val assetSyncState: StateFlow<AssetsSyncState> = _assetSyncState.asStateFlow()

    var state: AssetsSyncState
        get() = _assetSyncState.value
        set(value) {
            _assetSyncState.value = value
        }
}

class AssetRepositoryImpl(
    private val context: Context,
    private val assetSync: AssetSync
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
            assetSync.state = AssetsSyncState.SYNC_NEEDED
        } else assetSync.state = AssetsSyncState.SYNCED
    }

    override fun isCachePopulated(): Boolean = getAssetsCacheDir().list()?.isNotEmpty() ?: false

    override fun getTaxonomyFiles(): List<String> {
        val cacheDir = getAssetsCacheDir()
        return cacheDir.list()?.filter { isValidFileName(it) && it.endsWith("_taxonomy.json") }
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

    override suspend fun syncAssets() = withContext(Dispatchers.IO) {
        assetSync.state = AssetsSyncState.SYNCING
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()

        try {
            val remoteFilesList = fetchRemoteAssetList()
            val cacheDir = getAssetsCacheDir()
            val cachedFiles = cacheDir.list()
            val filesToSync = remoteFilesList.ifEmpty {
                cachedFiles?.toList()?.filter { isValidFileName(it) } ?: emptyList()
            }

            var downloadSuccessCount = 0
            for (fileName in filesToSync) {
                try {
                    if (!isValidFileName(fileName)) continue
                    val fileUrl = "$GITHUB_PAGES_BASE_URL$fileName"
                    val url = URL(fileUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    if (connection.responseCode == 200) {
                        val tempFile = File(cacheDir, "$fileName.tmp")
                        val finalFile = File(cacheDir, fileName)

                        if (!isSafeFile(tempFile, cacheDir) || !isSafeFile(
                                finalFile,
                                cacheDir
                            )
                        ) continue

                        connection.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                var totalBytes = 0L
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    totalBytes += bytesRead
                                    if (totalBytes > MAX_ASSET_SIZE_BYTES) {
                                        throw java.io.IOException("Asset file exceeds max size")
                                    }
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        if (tempFile.renameTo(finalFile)) {
                            downloadSuccessCount++
                        } else {
                            tempFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AssetRepositoryImpl", "Failed to download $fileName", e)
                    assetSync.state = AssetsSyncState.ERROR
                }
            }

            if (isCachePopulated() && downloadSuccessCount > 0) {
                prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, currentTime) }
                assetSync.state = AssetsSyncState.SYNCED
            }
        } catch (e: Exception) {
            Log.e("AssetRepositoryImpl", "Asset sync failed", e)
            assetSync.state = AssetsSyncState.ERROR
        }

    }

    private suspend fun fetchRemoteAssetList(): List<String> = withContext(Dispatchers.IO) {
        val assetList = mutableSetOf<String>()
        try {
            val url = URL(INDEX_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val fileName = jsonArray.getString(i)
                    if (fileName.isNotBlank()) {
                        assetList.add(fileName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AssetRepositoryImpl", "Failed to fetch remote asset index", e)
        }
        assetList.toList()
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



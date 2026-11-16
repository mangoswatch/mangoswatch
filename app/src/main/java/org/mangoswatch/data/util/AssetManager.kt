package org.mangoswatch.data.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mangoswatch.domain.model.IpOwner
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit

object AssetManager {
    private const val TAG = "AssetManager"
    private const val GITHUB_PAGES_BASE_URL = "https://mangoswatch.github.io/mangoswatch/data/"
    private const val INDEX_URL = "${GITHUB_PAGES_BASE_URL}index.json"
    private const val PREFS_NAME = "mangoswatch_remote_prefs"
    private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    private const val WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L
    private val ASSET_FILENAME_REGEX = Regex("^[a-z0-9_]+_(ipv4|ipv6)\\.txt$|^[a-z0-9_]+_taxonomy\\.json$")
    private const val MAX_ASSET_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB

    private fun isValidFileName(fileName: String): Boolean = ASSET_FILENAME_REGEX.matches(fileName)

    private fun isSafeFile(file: File, cacheDir: File): Boolean {
        return try {
            file.canonicalPath.startsWith(cacheDir.canonicalPath)
        } catch (_: Exception) {
            false
        }
    }

    fun getIpV4FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv4.txt"
    fun getIpV6FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv6.txt"

    fun getTaxonomyFiles(context: Context): List<String> {
        val cacheDir = getAssetsCacheDir(context)
        return cacheDir.list()?.filter { isValidFileName(it) && it.endsWith("_taxonomy.json") }?.sorted() ?: emptyList()
    }

    private fun getAssetsCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "assets_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getAsset(context: Context, fileName: String): InputStream {
        if (!isValidFileName(fileName)) {
            Log.w(TAG, "Access denied to unauthorized asset filename: $fileName")
            return "".byteInputStream()
        }
        val cacheDir = getAssetsCacheDir(context)
        val cachedFile = File(cacheDir, fileName)

        if (!isSafeFile(cachedFile, cacheDir)) {
            Log.e(TAG, "Path traversal attempt detected on getAsset: $fileName")
            return "".byteInputStream()
        }

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            try {
                return cachedFile.inputStream()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read cached asset file: $fileName", e)
            }
        }
        return "".byteInputStream()
    }

    suspend fun fetchRemoteAssetList(): List<String> = withContext(Dispatchers.IO) {
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
                        if (isValidFileName(fileName)) {
                            assetList.add(fileName)
                        } else {
                            Log.w(TAG, "Ignored invalid asset filename from index.json: $fileName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote asset index from index.json", e)
        }
        assetList.toList()
    }

    fun syncAssetsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        val cacheDir = getAssetsCacheDir(context)
        val cachedFiles = cacheDir.list()
        val isCacheEmpty = cachedFiles.isNullOrEmpty()

        if (currentTime - lastSyncTimestamp > WEEK_IN_MILLIS || isCacheEmpty) {
            CoroutineScope(Dispatchers.IO).launch {
                val remoteFiles = fetchRemoteAssetList()
                val filesToSync = remoteFiles.ifEmpty {
                    cachedFiles?.toList()?.filter { isValidFileName(it) } ?: emptyList()
                }

                for (fileName in filesToSync) {
                    try {
                        if (!isValidFileName(fileName)) {
                            Log.w(TAG, "Skipping asset with invalid filename: $fileName")
                            continue
                        }

                        val fileUrl = "$GITHUB_PAGES_BASE_URL$fileName"
                        val url = URL(fileUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        if (connection.responseCode == 200) {
                            val tempFile = File(cacheDir, "$fileName.tmp")
                            val finalFile = File(cacheDir, fileName)

                            if (!isSafeFile(tempFile, cacheDir) || !isSafeFile(finalFile, cacheDir)) {
                                Log.e(TAG, "Path traversal attempt detected for: $fileName")
                                continue
                            }

                            connection.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    var totalBytes = 0L
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        totalBytes += bytesRead
                                        if (totalBytes > MAX_ASSET_SIZE_BYTES) {
                                            throw java.io.IOException("Asset file exceeds maximum allowed size ($MAX_ASSET_SIZE_BYTES bytes)")
                                        }
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                            }

                            if (!tempFile.renameTo(finalFile)) {
                                Log.e(TAG, "Failed to rename temp file for: $fileName")
                                tempFile.delete()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download remote asset: $fileName", e)
                    }
                }

                if (filesToSync.isNotEmpty()) {
                    prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, currentTime) }
                }
            }
        }
    }
}

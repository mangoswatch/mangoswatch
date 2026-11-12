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

// TODO remove logging

object AssetManager {
    private const val TAG = "AssetManager"
    private const val GITHUB_PAGES_BASE_URL = "https://mangoswatch.github.io/mangoswatch/data/"
    private const val INDEX_URL = "${GITHUB_PAGES_BASE_URL}index.json"
    private const val PREFS_NAME = "mangoswatch_remote_prefs"
    private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    private const val WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun getIpV4FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv4.txt"
    fun getIpV6FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv6.txt"

    fun getTaxonomyFiles(context: Context): List<String> {
        val cacheDir = getAssetsCacheDir(context)
        val files = cacheDir.list()?.filter { it.endsWith("_taxonomy.json") }?.sorted() ?: emptyList()
        Log.d(TAG, "getTaxonomyFiles found ${files.size} files in cache: $files")
        return files
    }

    private fun getAssetsCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "assets_cache")
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "Created assets cache dir: ${dir.absolutePath}")
        }
        return dir
    }

    fun getAsset(context: Context, fileName: String): InputStream {
        val cacheDir = getAssetsCacheDir(context)
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            try {
                Log.d(TAG, "Reading asset from cache: $fileName (${cachedFile.length()} bytes)")
                return cachedFile.inputStream()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read cached asset file: $fileName", e)
            }
        } else {
            Log.w(TAG, "Asset not found in cache: $fileName")
        }

        return "".byteInputStream()
    }

    suspend fun fetchRemoteAssetList(): List<String> = withContext(Dispatchers.IO) {
        val assetList = mutableSetOf<String>()
        try {
            Log.d(TAG, "Fetching remote asset index: $INDEX_URL")
            val url = URL(INDEX_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val responseCode = connection.responseCode
            Log.d(TAG, "Remote index response code: $responseCode")
            if (responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val fileName = jsonArray.getString(i)
                    if (fileName.isNotBlank()) {
                        assetList.add(fileName)
                    }
                }
                Log.d(TAG, "Discovered remote assets from index.json: $assetList")
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

        Log.d(TAG, "syncAssetsIfNeeded: lastSync=$lastSyncTimestamp, now=$currentTime, cacheEmpty=$isCacheEmpty, cachedFiles=${cachedFiles?.contentToString()}")

        if (currentTime - lastSyncTimestamp > WEEK_IN_MILLIS || isCacheEmpty) {
            Log.d(TAG, "Triggering background sync from GitHub Pages...")
            CoroutineScope(Dispatchers.IO).launch {
                val remoteFiles = fetchRemoteAssetList()
                val filesToSync = remoteFiles.ifEmpty {
                    cachedFiles?.toList() ?: emptyList()
                }

                Log.d(TAG, "Files to sync: $filesToSync")

                for (fileName in filesToSync) {
                    try {
                        val fileUrl = "$GITHUB_PAGES_BASE_URL$fileName"
                        val url = URL(fileUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        val code = connection.responseCode
                        Log.d(TAG, "Downloading $fileName -> response code: $code")
                        if (code == 200) {
                            val tempFile = File(cacheDir, "$fileName.tmp")
                            connection.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val finalFile = File(cacheDir, fileName)
                            if (tempFile.renameTo(finalFile)) {
                                Log.d(TAG, "Successfully cached asset: $fileName (${finalFile.length()} bytes)")
                            } else {
                                Log.e(TAG, "Failed to rename temp file for: $fileName")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download remote asset: $fileName", e)
                    }
                }

                if (filesToSync.isNotEmpty()) {
                    prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, currentTime) }
                    Log.d(TAG, "Successfully completed remote assets sync from GitHub Pages.")
                }
            }
        } else {
            Log.d(TAG, "Sync skipped: Cache is populated and within 1-week TTL.")
        }
    }
}

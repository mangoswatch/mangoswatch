package org.mangoswatch.data.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mangoswatch.domain.model.IpOwner
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit

object AssetManager {
    private const val TAG = "AssetManager"
    private const val GITHUB_PAGES_BASE_URL = "https://mangoswatch.github.io/mangoswatch/data/"
    private const val PREFS_NAME = "mangoswatch_remote_prefs"
    private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    private const val WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun getIpV4FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv4.txt"
    fun getIpV6FileName(ipOwner: IpOwner): String = "${ipOwner.name.lowercase()}_ipv6.txt"

    fun getTaxonomyFiles(context: Context): List<String> {
        val cacheDir = getAssetsCacheDir(context)
        return cacheDir.list()?.filter { it.endsWith("_taxonomy.json") }?.sorted() ?: emptyList()
    }

    private fun getAssetsCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "assets_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns an InputStream from the local disk cache populated by GitHub Pages.
     * If not cached yet, returns an empty byte stream while background sync populates it.
     */
    fun getAsset(context: Context, fileName: String): InputStream {
        val cacheDir = getAssetsCacheDir(context)
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            try {
                return cachedFile.inputStream()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read cached asset file: $fileName", e)
            }
        }

        return "".byteInputStream()
    }

    /**
     * Fetches the dynamic asset list on the fly from the remote GitHub Pages directory listing.
     */
    suspend fun fetchRemoteAssetList(): List<String> = withContext(Dispatchers.IO) {
        val assetList = mutableSetOf<String>()
        try {
            val url = URL(GITHUB_PAGES_BASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex("href=\"([^\"]+\\.(json|txt))\"")
                for (match in regex.findAll(html)) {
                    val fileName = match.groupValues[1].substringAfterLast('/')
                    if (fileName.isNotBlank()) {
                        assetList.add(fileName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote asset list from directory listing", e)
        }
        assetList.toList()
    }

    /**
     * Triggers a background sync from GitHub Pages if the cache is empty or older than 1 week.
     */
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
                    cachedFiles?.toList() ?: emptyList()
                }

                for (fileName in filesToSync) {
                    try {
                        val url = URL("$GITHUB_PAGES_BASE_URL$fileName")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        if (connection.responseCode == 200) {
                            val tempFile = File(cacheDir, "$fileName.tmp")
                            connection.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val finalFile = File(cacheDir, fileName)
                            tempFile.renameTo(finalFile)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to download remote asset: $fileName", e)
                    }
                }

                if (filesToSync.isNotEmpty()) {
                    prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, currentTime) }
                    Log.d(TAG, "Successfully synced ${filesToSync.size} remote assets from GitHub Pages.")
                }
            }
        }
    }
}

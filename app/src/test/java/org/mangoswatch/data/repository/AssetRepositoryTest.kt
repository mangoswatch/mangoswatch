package org.mangoswatch.data.repository

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.mangoswatch.domain.model.AssetSyncError
import org.mangoswatch.domain.model.AssetSyncProgress
import org.mangoswatch.domain.model.AssetSyncState
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class AssetRepositoryTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cacheDir = File(context.cacheDir, "assets_cache")
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        context.getSharedPreferences("mangoswatch_remote_prefs", 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun testGetTaxonomyFiles() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync)
        File(cacheDir, "google_taxonomy.json").writeText("{}")
        File(cacheDir, "apple_taxonomy.json").writeText("{}")
        File(cacheDir, "invalid_file.txt").writeText("data")

        val files = repo.getTaxonomyFiles()
        assertEquals(2, files.size)
        assertTrue(files.contains("apple_taxonomy.json"))
        assertTrue(files.contains("google_taxonomy.json"))
    }

    @Test
    fun testGetAssetValid() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync)
        File(cacheDir, "google_ipv4.txt").writeText("1.2.3.4/32")

        val stream = repo.getFileFromAssetsCache("google_ipv4.txt")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("1.2.3.4/32", content)
    }

    @Test
    fun testGetAssetPathTraversalDenied() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync)
        val stream = repo.getFileFromAssetsCache("../shared_prefs/secret.xml")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }

    @Test
    fun testGetAssetUnauthorizedFilenameDenied() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync)
        val stream = repo.getFileFromAssetsCache("unauthorized_script.sh")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }

    @Test
    fun testCachePopulationCheck() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync)
        assertFalse(repo.isCachePopulated())

        File(cacheDir, "google_ipv4.txt").writeText("8.8.8.0/24")
        File(cacheDir, "google_taxonomy.json.tmp").writeText("{}")
        assertFalse(repo.isCachePopulated())

        File(cacheDir, "google_taxonomy.json").writeText("{}")
        assertTrue(repo.isCachePopulated())
    }

    @Test
    fun testIndexHttpFailureEndsInError() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val fetcher = fakeFetcher(
            mapOf("https://mangoswatch.github.io/mangoswatch/data/index.json" to (503 to ""))
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(AssetSyncState.Error(AssetSyncError.IndexDownloadFailed), assetSync.state.value)
    }

    @Test
    fun testInvalidIndexEndsInSpecificErrorState() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val fetcher = fakeFetcher(
            mapOf("https://mangoswatch.github.io/mangoswatch/data/index.json" to (200 to "not-json"))
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(AssetSyncState.Error(AssetSyncError.InvalidIndex), assetSync.state.value)
    }

    @Test
    fun testPartialDownloadFailureDoesNotMarkSyncCompleteOrUpdateTimestamp() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to (200 to """["google_ipv4.txt","google_taxonomy.json"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv4.txt" to (200 to "8.8.8.0/24"),
                "https://mangoswatch.github.io/mangoswatch/data/google_taxonomy.json" to (503 to "")
            )
        )
        val prefs = context.getSharedPreferences("mangoswatch_remote_prefs", 0)
        prefs.edit().putLong("last_sync_timestamp", 123L).commit()
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(
            AssetSyncState.Error(AssetSyncError.AssetDownloadFailed("google_taxonomy.json")),
            assetSync.state.value
        )
        assertEquals(123L, prefs.getLong("last_sync_timestamp", 0L))
    }

    @Test
    fun testInvalidIpv4AssetDoesNotReplaceCachedFile() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cachedAsset = File(cacheDir, "google_ipv4.txt")
        cachedAsset.writeText("8.8.8.0/24")
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to (200 to """["google_ipv4.txt"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv4.txt" to
                    (200 to "999.8.8.0/24")
            )
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(
            AssetSyncState.Error(AssetSyncError.InvalidAssetContent("google_ipv4.txt")),
            assetSync.state.value
        )
        assertEquals("8.8.8.0/24", cachedAsset.readText())
    }

    @Test
    fun testInvalidIpv6AssetDoesNotReplaceCachedFile() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cachedAsset = File(cacheDir, "google_ipv6.txt")
        cachedAsset.writeText("2001:db8::/32")
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to (200 to """["google_ipv6.txt"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv6.txt" to
                    (200 to "2001:db8::gg/32")
            )
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(
            AssetSyncState.Error(AssetSyncError.InvalidAssetContent("google_ipv6.txt")),
            assetSync.state.value
        )
        assertEquals("2001:db8::/32", cachedAsset.readText())
    }

    @Test
    fun testInvalidTaxonomyDoesNotReplaceCachedFile() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cachedAsset = File(cacheDir, "google_taxonomy.json")
        cachedAsset.writeText("""{"provider":"GOOGLE","endpoints":[]}""")
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to (200 to """["google_taxonomy.json"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_taxonomy.json" to
                    (200 to """{"provider":"GOOGLE","endpoints":[{"id":"missing-fields"}]}""")
            )
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(
            AssetSyncState.Error(AssetSyncError.InvalidAssetContent("google_taxonomy.json")),
            assetSync.state.value
        )
        assertEquals("""{"provider":"GOOGLE","endpoints":[]}""", cachedAsset.readText())
    }

    @Test
    fun testCompleteDownloadMarksSyncCompleteAndUpdatesTimestamp() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to
                    (200 to """["google_ipv4.txt","google_ipv6.txt","google_taxonomy.json"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv4.txt" to (200 to "8.8.8.0/24"),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv6.txt" to
                    (200 to "2001:4860::/32"),
                "https://mangoswatch.github.io/mangoswatch/data/google_taxonomy.json" to
                    (
                        200 to
                            """{"provider":"GOOGLE","endpoints":[{"id":"google_api","hostname_pattern":"google\\.com","display_name":"Google API","category":"SEARCH_USAGE","description":"API traffic","data_collected":["usage"],"opt_out_description":"Settings"}]}"""
                    )
            )
        )
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(AssetSyncState.Synced, assetSync.state.value)
        assertTrue(context.getSharedPreferences("mangoswatch_remote_prefs", 0)
            .getLong("last_sync_timestamp", 0L) > 0L)
    }

    @Test
    fun testCheckedInAssetsPassValidation() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val docsDataDir = File("../docs/data")
        val indexFile = File(docsDataDir, "index.json")
        assertTrue("docs/data/index.json must exist", indexFile.isFile)
        val indexContent = indexFile.readText()
        val index = org.json.JSONArray(indexContent)
        val responses = mutableMapOf(
            "https://mangoswatch.github.io/mangoswatch/data/index.json" to
                (200 to indexContent)
        )
        for (i in 0 until index.length()) {
            val fileName = index.getString(i)
            responses["https://mangoswatch.github.io/mangoswatch/data/$fileName"] =
                200 to File(docsDataDir, fileName).readText()
        }
        val assetSync = AssetSyncImpl()
        val repo = AssetRepositoryImpl(context, assetSync, fakeFetcher(responses))

        repo.syncAssets()

        assertEquals(AssetSyncState.Synced, assetSync.state.value)
    }

    @Test
    fun testSyncProgressTracksCompletedAssetFiles() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val indexUrl = "https://mangoswatch.github.io/mangoswatch/data/index.json"
        val assetSync = AssetSyncImpl()
        val statesAtDownloadStart = mutableListOf<AssetSyncState>()
        val fetcher = fakeFetcher(
            mapOf(
                indexUrl to (200 to """["google_ipv4.txt","google_taxonomy.json"]"""),
                "https://mangoswatch.github.io/mangoswatch/data/google_ipv4.txt" to
                    (200 to "8.8.8.0/24"),
                "https://mangoswatch.github.io/mangoswatch/data/google_taxonomy.json" to
                    (200 to """{"provider":"GOOGLE","endpoints":[]}""")
            )
        ) { url ->
            if (url.toString() != indexUrl) {
                statesAtDownloadStart.add(assetSync.state.value)
            }
        }
        val repo = AssetRepositoryImpl(context, assetSync, fetcher)

        repo.syncAssets()

        assertEquals(
            listOf(
                AssetSyncState.Syncing(AssetSyncProgress(0, 2)),
                AssetSyncState.Syncing(AssetSyncProgress(1, 2))
            ),
            statesAtDownloadStart
        )
        assertEquals(AssetSyncState.Synced, assetSync.state.value)
    }

    private fun fakeFetcher(
        responses: Map<String, Pair<Int, String>>,
        onOpenConnection: (URL) -> Unit = {}
    ) = object : AssetFetcher {
        override fun openConnection(url: URL): HttpURLConnection {
            onOpenConnection(url)
            val (status, content) = responses[url.toString()] ?: error("Unexpected URL: $url")
            return mock(HttpURLConnection::class.java).apply {
                whenever(responseCode).thenReturn(status)
                if (status == HttpURLConnection.HTTP_OK) {
                    whenever(inputStream).thenReturn(content.byteInputStream())
                }
            }
        }
    }
}

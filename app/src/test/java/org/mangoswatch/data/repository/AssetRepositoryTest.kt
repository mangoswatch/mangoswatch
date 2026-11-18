package org.mangoswatch.data.repository

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AssetRepositoryTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cacheDir = File(context.cacheDir, "assets_cache")
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun testGetTaxonomyFiles() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSync()
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
        val assetSync = AssetSync()
        val repo = AssetRepositoryImpl(context, assetSync)
        File(cacheDir, "google_ipv4.txt").writeText("1.2.3.4/32")

        val stream = repo.getFileFromAssetsCache("google_ipv4.txt")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("1.2.3.4/32", content)
    }

    @Test
    fun testGetAssetPathTraversalDenied() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSync()
        val repo = AssetRepositoryImpl(context, assetSync)
        val stream = repo.getFileFromAssetsCache("../shared_prefs/secret.xml")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }

    @Test
    fun testGetAssetUnauthorizedFilenameDenied() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSync()
        val repo = AssetRepositoryImpl(context, assetSync)
        val stream = repo.getFileFromAssetsCache("unauthorized_script.sh")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }

    @Test
    fun testCachePopulationCheck() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSync()
        val repo = AssetRepositoryImpl(context, assetSync)
        assertFalse(repo.isCachePopulated())

        File(cacheDir, "google_taxonomy.json").writeText("{}")
        assertTrue(repo.isCachePopulated())
    }
}

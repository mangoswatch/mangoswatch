package org.mangoswatch.data.util

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.domain.model.IpOwner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AssetManagerTest {

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
    fun testFileNameGenerators() {
        assertEquals("google_ipv4.txt", AssetManager.getIpV4FileName(IpOwner.GOOGLE))
        assertEquals("google_ipv6.txt", AssetManager.getIpV6FileName(IpOwner.GOOGLE))
        assertEquals("x_twitter_ipv4.txt", AssetManager.getIpV4FileName(IpOwner.X_TWITTER))
    }

    @Test
    fun testGetTaxonomyFiles() {
        val context = RuntimeEnvironment.getApplication()
        File(cacheDir, "google_taxonomy.json").writeText("{}")
        File(cacheDir, "apple_taxonomy.json").writeText("{}")
        File(cacheDir, "invalid_file.txt").writeText("data")

        val files = AssetManager.getTaxonomyFiles(context)
        assertEquals(2, files.size)
        assertTrue(files.contains("apple_taxonomy.json"))
        assertTrue(files.contains("google_taxonomy.json"))
    }

    @Test
    fun testGetAssetValid() {
        val context = RuntimeEnvironment.getApplication()
        File(cacheDir, "google_ipv4.txt").writeText("1.2.3.4/32")

        val stream = AssetManager.getAsset(context, "google_ipv4.txt")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("1.2.3.4/32", content)
    }

    @Test
    fun testGetAssetPathTraversalDenied() {
        val context = RuntimeEnvironment.getApplication()
        val stream = AssetManager.getAsset(context, "../shared_prefs/secret.xml")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }

    @Test
    fun testGetAssetUnauthorizedFilenameDenied() {
        val context = RuntimeEnvironment.getApplication()
        val stream = AssetManager.getAsset(context, "unauthorized_script.sh")
        val content = stream.bufferedReader().use { it.readText() }
        assertEquals("", content)
    }
}

package org.mangoswatch.data.repository

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
class EndpointTaxonomyRepositoryTest {

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val cacheDir = context.cacheDir
        val assetsCacheDir = File(cacheDir, "assets_cache")
        assetsCacheDir.mkdirs()

        val sampleJson = """
        {
          "provider": "OPENAI",
          "endpoints": [
            {
              "id": "openai_api_001",
              "hostname_pattern": "api\\.openai\\.com",
              "display_name": "OpenAI API",
              "category": "INVALID_OR_UNKNOWN_CATEGORY",
              "description": "Core API",
              "data_collected": ["Prompt text"],
              "opt_out_description": "None"
            }
          ]
        }
        """.trimIndent()

        File(assetsCacheDir, "openai_taxonomy.json").writeText(sampleJson)
    }

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        File(context.cacheDir, "assets_cache").deleteRecursively()
    }

    @Test
    fun testFindMatchSuccessAndCaseInsensitive() {
        val context = RuntimeEnvironment.getApplication()
        val repository = EndpointTaxonomyRepositoryImpl(context)
        val match = repository.findMatch("API.OpenAI.COM")
        assertNotNull(match)
        assertEquals("openai_api_001", match?.id)
        assertEquals("OPENAI", match?.provider)
        // Invalid category string in JSON should fall back to UNKNOWN gracefully
        assertEquals(EndpointTaxonomyCategory.UNKNOWN, match?.category)
    }

    @Test
    fun testFindMatchNoMatch() {
        val context = RuntimeEnvironment.getApplication()
        val repository = EndpointTaxonomyRepositoryImpl(context)
        val match = repository.findMatch("unknown.domain.com")
        assertNull(match)
    }

    @Test
    fun testFindMatchBlank() {
        val context = RuntimeEnvironment.getApplication()
        val repository = EndpointTaxonomyRepositoryImpl(context)
        assertNull(repository.findMatch(null))
        assertNull(repository.findMatch(""))
        assertNull(repository.findMatch("   "))
    }

    @Test
    fun testGetAllItems() {
        val context = RuntimeEnvironment.getApplication()
        val repository = EndpointTaxonomyRepositoryImpl(context)
        val items = repository.getAllItems()
        assertEquals(1, items.size)
        assertEquals("openai_api_001", items[0].id)
    }

    @Test
    fun testTaxonomyPatternsReDoSProtection() {
        val docsDir = File("../docs/data")
        if (!docsDir.exists()) return

        val taxonomyFiles = docsDir.listFiles { _, name -> name.endsWith("_taxonomy.json") } ?: return
        val longTestString = "sub.".repeat(100) + "example.com"

        for (file in taxonomyFiles) {
            val jsonString = file.readText()
            val root = org.json.JSONObject(jsonString)
            val endpoints = root.getJSONArray("endpoints")
            for (i in 0 until endpoints.length()) {
                val endpoint = endpoints.getJSONObject(i)
                val patternStr = endpoint.getString("hostname_pattern")

                val startTime = System.currentTimeMillis()
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(longTestString)
                matcher.find()
                val duration = System.currentTimeMillis() - startTime

                assertTrue("Potential ReDoS or slow regex in ${file.name}: $patternStr", duration < 200)
            }
        }
    }
}

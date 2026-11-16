package org.mangoswatch.data.repository

import android.content.Context
import android.util.Log
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.data.util.AssetManager
import org.json.JSONObject
import java.util.regex.Pattern

class EndpointTaxonomyRepositoryImpl(
    private val context: Context
) : EndpointTaxonomyRepository {

    private val items = mutableListOf<EndpointTaxonomy>()
    private val compiledPatterns = mutableMapOf<EndpointTaxonomy, Pattern>()

    private val noMatchSentinel = EndpointTaxonomy(
        id = "__NO_MATCH__",
        provider = "",
        hostnamePattern = "",
        displayName = "",
        category = EndpointTaxonomyCategory.UNKNOWN,
        description = "",
        dataCollected = emptyList(),
        optOutDescription = ""
    )

    private val matchCache = object : LinkedHashMap<String, EndpointTaxonomy>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EndpointTaxonomy>?): Boolean {
            return size > 1024
        }
    }

    init {
        loadTaxonomy()
    }

    private fun loadTaxonomy() {
        for (fileName in AssetManager.getTaxonomyFiles(context)) {
            try {
                val assetStream = AssetManager.getAsset(context, fileName)
                val jsonString = assetStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val provider = root.optString("provider", "UNKNOWN")
                val endpointsArray = root.getJSONArray("endpoints")

                for (i in 0 until endpointsArray.length()) {
                    val obj = endpointsArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val hostnamePattern = obj.getString("hostname_pattern")
                    val displayName = obj.getString("display_name")
                    val categoryStr = obj.getString("category")
                    val category = try {
                        EndpointTaxonomyCategory.valueOf(categoryStr)
                    } catch (e: Exception) {
                        EndpointTaxonomyCategory.UNKNOWN
                    }
                    val description = obj.getString("description")

                    val dataCollectedArr = obj.getJSONArray("data_collected")
                    val dataCollected = mutableListOf<String>()
                    for (j in 0 until dataCollectedArr.length()) {
                        dataCollected.add(dataCollectedArr.getString(j))
                    }

                    val optOutDescription = obj.optString("opt_out_description", "")

                    val item = EndpointTaxonomy(
                        id = id,
                        provider = provider,
                        hostnamePattern = hostnamePattern,
                        displayName = displayName,
                        category = category,
                        description = description,
                        dataCollected = dataCollected,
                        optOutDescription = optOutDescription
                    )

                    items.add(item)
                    try {
                        compiledPatterns[item] = Pattern.compile(hostnamePattern, Pattern.CASE_INSENSITIVE)
                    } catch (e: Exception) {
                        Log.e("EndpointTaxonomyRepo", "Invalid regex pattern: $hostnamePattern in $fileName", e)
                    }
                }
                Log.d("EndpointTaxonomyRepo", "Loaded taxonomy items from $fileName.")
            } catch (e: Exception) {
                Log.e("EndpointTaxonomyRepo", "Failed to load $fileName", e)
            }
        }
        Log.d("EndpointTaxonomyRepo", "Loaded total ${items.size} taxonomy items across all providers.")
    }

    override fun findMatch(hostnameOrUrl: String?): EndpointTaxonomy? {
        if (hostnameOrUrl.isNullOrBlank()) return null

        synchronized(matchCache) {
            val cached = matchCache[hostnameOrUrl]
            if (cached != null) {
                return if (cached === noMatchSentinel) null else cached
            }
        }

        var matchedItem: EndpointTaxonomy? = null
        for (item in items) {
            val pattern = compiledPatterns[item] ?: continue
            if (pattern.matcher(hostnameOrUrl).find()) {
                matchedItem = item
                break
            }
        }

        synchronized(matchCache) {
            matchCache[hostnameOrUrl] = matchedItem ?: noMatchSentinel
        }

        // DEBUG Log: this serves for an observation of untracked host to report in the assets
        if (matchedItem == null) {
            try {
                if (org.mangoswatch.BuildConfig.DEBUG) {
                    Log.d("EndpointTaxonomyRepo", "Unclassified endpoint host: $hostnameOrUrl")
                }
            } catch (_: Exception) {}
        }
        return matchedItem
    }

    override fun getAllItems(): List<EndpointTaxonomy> = items
}

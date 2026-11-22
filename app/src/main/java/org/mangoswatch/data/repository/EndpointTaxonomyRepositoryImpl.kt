package org.mangoswatch.data.repository

import android.util.Log
import org.json.JSONObject
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import java.util.regex.Pattern

class EndpointTaxonomyRepositoryImpl(
    private val assetRepository: AssetRepository
) : EndpointTaxonomyRepository {

    private data class TaxonomySnapshot(
        val items: List<EndpointTaxonomy> = emptyList(),
        val compiledPatterns: Map<EndpointTaxonomy, Pattern> = emptyMap()
    )

    @Volatile
    private var snapshot: TaxonomySnapshot = TaxonomySnapshot()

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
        reloadTaxonomy()
    }

    override fun reloadTaxonomy() {
        val newSnapshot = loadTaxonomySnapshot()
        synchronized(this) {
            snapshot = newSnapshot
            synchronized(matchCache) { matchCache.clear() }
        }
    }

    private fun loadTaxonomySnapshot(): TaxonomySnapshot {
        val newItems = mutableListOf<EndpointTaxonomy>()
        val newCompiledPatterns = mutableMapOf<EndpointTaxonomy, Pattern>()

        for (fileName in assetRepository.getTaxonomyFiles()) {
            try {
                val assetStream = assetRepository.getFileFromAssetsCache(fileName)
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

                    newItems.add(item)
                    try {
                        newCompiledPatterns[item] = Pattern.compile(hostnamePattern, Pattern.CASE_INSENSITIVE)
                    } catch (e: Exception) {
                        Log.e("EndpointTaxonomyRepo", "Invalid regex pattern: $hostnamePattern in $fileName", e)
                    }
                }
                Log.d("EndpointTaxonomyRepo", "Loaded taxonomy items from $fileName.")
            } catch (e: Exception) {
                Log.e("EndpointTaxonomyRepo", "Failed to load $fileName", e)
            }
        }
        Log.d("EndpointTaxonomyRepo", "Loaded total ${newItems.size} taxonomy items across all providers.")
        return TaxonomySnapshot(
            items = newItems.toList(),
            compiledPatterns = newCompiledPatterns.toMap()
        )
    }

    override fun findMatch(hostnameOrUrl: String?): EndpointTaxonomy? {
        if (hostnameOrUrl.isNullOrBlank()) return null

        synchronized(matchCache) {
            val cached = matchCache[hostnameOrUrl]
            if (cached != null) {
                return if (cached === noMatchSentinel) null else cached
            }
        }

        val currentSnapshot = snapshot
        var matchedItem: EndpointTaxonomy? = null
        for (item in currentSnapshot.items) {
            val pattern = currentSnapshot.compiledPatterns[item] ?: continue
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
}

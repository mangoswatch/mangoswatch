package org.mangoswatch.domain.repository

import java.io.InputStream

interface AssetRepository {
    fun isCachePopulated(): Boolean
    suspend fun syncAssets(): Any
    fun getTaxonomyFiles(): List<String>
    fun getFileFromAssetsCache(fileName: String): InputStream
}

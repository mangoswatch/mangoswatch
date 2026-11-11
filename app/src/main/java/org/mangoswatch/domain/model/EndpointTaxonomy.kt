package org.mangoswatch.domain.model

data class EndpointTaxonomy(
    val id: String,
    val provider: String,
    val hostnamePattern: String,
    val displayName: String,
    val category: EndpointTaxonomyCategory,
    val description: String,
    val dataCollected: List<String>,
    val optOutDescription: String
)

package org.mangoswatch.domain.repository

import org.mangoswatch.domain.model.EndpointTaxonomy

interface EndpointTaxonomyRepository {
    fun findMatch(hostnameOrUrl: String?): EndpointTaxonomy?
    fun getAllItems(): List<EndpointTaxonomy>
}

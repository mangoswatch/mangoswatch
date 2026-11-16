package org.mangoswatch.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mangoswatch.domain.model.DnsSettings

interface UserPreferencesRepository {
    val dnsSettings: Flow<DnsSettings>
    suspend fun updateDnsSettings(settings: DnsSettings)
}

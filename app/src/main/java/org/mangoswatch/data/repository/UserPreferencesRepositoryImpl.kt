package org.mangoswatch.data.repository

import kotlinx.coroutines.flow.Flow
import org.mangoswatch.data.datasource.UserPreferences
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.repository.UserPreferencesRepository

class UserPreferencesRepositoryImpl(
    private val userPreferences: UserPreferences
) : UserPreferencesRepository {

    override val dnsSettings: Flow<DnsSettings> = userPreferences.dnsSettings

    override suspend fun updateDnsSettings(settings: DnsSettings) {
        userPreferences.updateDnsSettings(settings)
    }
}

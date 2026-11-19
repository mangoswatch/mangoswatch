package org.mangoswatch.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.model.UserSettings

interface UserSettingsRepository {
    val userSettings: Flow<UserSettings>
    suspend fun updateDnsSettings(settings: DnsSettings)
}

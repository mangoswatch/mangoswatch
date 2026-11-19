package org.mangoswatch.data.repository

import kotlinx.coroutines.flow.Flow
import org.mangoswatch.data.datasource.UserSettingsDataSource
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.model.UserSettings
import org.mangoswatch.domain.repository.UserSettingsRepository

class UserSettingsRepositoryImpl(
    private val userSettingsDataSource: UserSettingsDataSource
) : UserSettingsRepository {

    override val userSettings: Flow<UserSettings> = userSettingsDataSource.userSettings

    override suspend fun updateDnsSettings(settings: DnsSettings) {
        userSettingsDataSource.updateDnsSettings(settings)
    }
}

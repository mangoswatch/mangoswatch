package org.mangoswatch.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.model.UserSettings

private val Context.dataStore by preferencesDataStore(name = "mangoswatch_user_settings")

class UserSettingsDataSource(private val context: Context) {
    private object PreferencesKeys {
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val DNS_V4 = stringPreferencesKey("dns_v4")
        val DNS_V6 = stringPreferencesKey("dns_v6")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data
        .map { prefs ->
            val modeStr = prefs[PreferencesKeys.DNS_MODE] ?: DnsMode.SYSTEM.name
            val dnsMode = try {
                DnsMode.valueOf(modeStr)
            } catch (_: Exception) {
                DnsMode.SYSTEM
            }
            val dnsV4 = prefs[PreferencesKeys.DNS_V4] ?: ""
            val dnsV6 = prefs[PreferencesKeys.DNS_V6] ?: ""
            val dnsSettings = DnsSettings(dnsMode, dnsV4, dnsV6)

            UserSettings(dnsSettings)
        }

    suspend fun updateDnsSettings(settings: DnsSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DNS_MODE] = settings.dnsMode.name
            prefs[PreferencesKeys.DNS_V4] = settings.dnsV4
            prefs[PreferencesKeys.DNS_V6] = settings.dnsV6
        }
    }
}

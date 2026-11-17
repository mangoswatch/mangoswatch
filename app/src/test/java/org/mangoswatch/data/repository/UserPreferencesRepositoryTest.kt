package org.mangoswatch.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.data.datasource.UserPreferences
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {

    private lateinit var repository: UserPreferencesRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val userPreferences = UserPreferences(context)
        repository = UserPreferencesRepositoryImpl(userPreferences)
    }

    @Test
    fun testDefaultDnsSettings() = runTest {
        val settings = repository.dnsSettings.first()
        assertNotNull(settings)
        assertEquals(DnsMode.CLOUDFLARE, settings.dnsMode)
    }

    @Test
    fun testUpdateDnsSettings() = runTest {
        val customSettings = DnsSettings(
            dnsMode = DnsMode.CUSTOM,
            dnsV4 = "8.8.8.8,8.8.4.4",
            dnsV6 = "2001:4860:4860::8888"
        )
        repository.updateDnsSettings(customSettings)

        val updated = repository.dnsSettings.first { it.dnsMode == DnsMode.CUSTOM }
        assertEquals(DnsMode.CUSTOM, updated.dnsMode)
        assertEquals("8.8.8.8,8.8.4.4", updated.dnsV4)
        assertEquals("2001:4860:4860::8888", updated.dnsV6)
    }
}

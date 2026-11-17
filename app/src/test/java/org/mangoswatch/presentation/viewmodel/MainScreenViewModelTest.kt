package org.mangoswatch.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.data.resolver.AppInfoResolver
import org.mangoswatch.data.vpn.VpnAction
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.data.vpn.VpnStateEnum
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.mangoswatch.domain.repository.UserPreferencesRepository
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var streamsRepository: StreamsRepository
    private lateinit var taxonomyRepository: EndpointTaxonomyRepository
    private lateinit var vpnState: VpnState
    private lateinit var appInfoResolver: AppInfoResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        streamsRepository = mock(StreamsRepository::class.java)
        taxonomyRepository = mock(EndpointTaxonomyRepository::class.java)
        vpnState = VpnState()
        appInfoResolver = mock(AppInfoResolver::class.java)
        userPreferencesRepository = mock(UserPreferencesRepository::class.java)

        `when`(streamsRepository.getStreams()).thenReturn(flowOf(emptyList()))
        `when`(userPreferencesRepository.dnsSettings).thenReturn(flowOf(DnsSettings()))

        viewModel = MainScreenViewModel(
            streamsRepository = streamsRepository,
            taxonomyRepository = taxonomyRepository,
            vpnState = vpnState,
            appInfo = appInfoResolver,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest {
        val state = viewModel.state.value
        assertFalse(state.isSettingsOpen)
        assertNull(state.selectedUid)
        assertEquals(VpnStateEnum.IDLE, state.vpnState)
    }

    @Test
    fun testOnAppClickEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnAppClick(12345))
        assertEquals(12345, viewModel.state.value.selectedUid)
    }

    @Test
    fun testOnBackEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnAppClick(12345))
        assertEquals(12345, viewModel.state.value.selectedUid)

        viewModel.state.value.eventTunnel(MainScreenEvent.OnBack)
        assertNull(viewModel.state.value.selectedUid)
    }

    @Test
    fun testOnShowSettingsEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnShowSettings(true))
        assertTrue(viewModel.state.value.isSettingsOpen)

        viewModel.state.value.eventTunnel(MainScreenEvent.OnShowSettings(false))
        assertFalse(viewModel.state.value.isSettingsOpen)
    }

    @Test
    fun testVpnStartStopRequests() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnStartVpnRequest)
        assertEquals(VpnAction.START, vpnState.actionRequest)

        viewModel.state.value.eventTunnel(MainScreenEvent.OnStopVpnRequest)
        assertEquals(VpnAction.STOP, vpnState.actionRequest)
    }

    @Test
    fun testOnUpdateDnsEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnUpdateDns(DnsMode.CUSTOM, "1.1.1.1", "2606:4700:4700::1111"))
        testDispatcher.scheduler.advanceUntilIdle()
        verify(userPreferencesRepository).updateDnsSettings(any())
    }

    @Test
    fun testStreamProcessingWhenVpnRunning() = runTest {
        vpnState.state = VpnStateEnum.RUNNING
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(VpnStateEnum.RUNNING, state.vpnState)
    }
}

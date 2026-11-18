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
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.data.repository.AssetSync
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.StreamModel
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
    private lateinit var assetRepository: AssetRepository
    private lateinit var assetSync: AssetSync

    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        streamsRepository = mock(StreamsRepository::class.java)
        taxonomyRepository = mock(EndpointTaxonomyRepository::class.java)
        vpnState = VpnState()
        appInfoResolver = mock(AppInfoResolver::class.java)
        userPreferencesRepository = mock(UserPreferencesRepository::class.java)
        assetRepository = mock(AssetRepository::class.java)
        assetSync = AssetSync().apply { state = org.mangoswatch.data.repository.AssetsSyncState.SYNCED }

        `when`(streamsRepository.getStreams()).thenReturn(flowOf(emptyList()))
        `when`(userPreferencesRepository.dnsSettings).thenReturn(flowOf(DnsSettings()))
        `when`(assetRepository.isCachePopulated()).thenReturn(true)

        viewModel = MainScreenViewModel(
            streamsRepository = streamsRepository,
            taxonomyRepository = taxonomyRepository,
            vpnState = vpnState,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userPreferencesRepository = userPreferencesRepository,
            assetRepository = assetRepository
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
    fun testToAppTrafficFilterOtherAndReserved() = runTest {
        val taxonomyItem = EndpointTaxonomy(
            id = "appsflyer_sdk_001",
            provider = "APPSFLYER",
            hostnamePattern = "(?:^|\\.)appsflyersdk\\.com$",
            displayName = "AppsFlyer",
            category = EndpointTaxonomyCategory.ADVERTISING_ID,
            description = "Desc",
            dataCollected = emptyList(),
            optOutDescription = ""
        )
        `when`(taxonomyRepository.findMatch("appsflyersdk.com")).thenReturn(taxonomyItem)
        `when`(taxonomyRepository.findMatch("unknown.com")).thenReturn(null)
        `when`(taxonomyRepository.findMatch("?")).thenReturn(null)

        val matchingOtherStream = StreamModel(
            protocol = 6,
            srcIpStr = "10.0.0.1",
            srcPort = 12345,
            dstIpStr = "1.2.3.4",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis(),
            uid = 10001,
            sni = "appsflyersdk.com"
        )

        val nonMatchingOtherStream = StreamModel(
            protocol = 6,
            srcIpStr = "10.0.0.1",
            srcPort = 12346,
            dstIpStr = "5.6.7.8",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis(),
            uid = 10002,
            sni = "unknown.com"
        )

        val reservedMatchingStream = StreamModel(
            protocol = 6,
            srcIpStr = "10.0.0.1",
            srcPort = 12347,
            dstIpStr = "127.0.0.1",
            dstPort = 443,
            ipOwner = IpOwner.RESERVED,
            firstSeen = System.currentTimeMillis(),
            uid = 10003,
            sni = "appsflyersdk.com"
        )

        val mockStreamsRepo = mock(StreamsRepository::class.java)
        `when`(mockStreamsRepo.getStreams()).thenReturn(flowOf(listOf(
            matchingOtherStream,
            nonMatchingOtherStream,
            reservedMatchingStream
        )))

        vpnState.state = VpnStateEnum.RUNNING

        val vm = MainScreenViewModel(
            streamsRepository = mockStreamsRepo,
            taxonomyRepository = taxonomyRepository,
            vpnState = vpnState,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userPreferencesRepository = userPreferencesRepository,
            assetRepository = assetRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val apps = vm.state.value.apps
        assertEquals(1, apps.size)
        assertEquals(10001, apps[0].uid)
        assertEquals("appsflyersdk.com", apps[0].hosts[0].host)
        assertEquals(IpOwner.APPSFLYER, apps[0].hosts[0].ipOwner)
    }

    @Test
    fun testOnUpdateDnsEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnUpdateDns(DnsMode.CUSTOM, "1.1.1.1", "2606:4700:4700::1111"))
        testDispatcher.scheduler.advanceUntilIdle()
        verify(userPreferencesRepository).updateDnsSettings(any())
    }

    @Test
    fun testStartVpnWhenAssetsMissingFailsWithMessage() = runTest {
        assetSync.state = org.mangoswatch.data.repository.AssetsSyncState.SYNCING

        viewModel.state.value.eventTunnel(MainScreenEvent.OnStartVpnRequest)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
        assertNotEquals(VpnAction.START, vpnState.actionRequest)
    }

    @Test
    fun testStreamProcessingWhenVpnRunning() = runTest {
        vpnState.state = VpnStateEnum.RUNNING
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(VpnStateEnum.RUNNING, state.vpnState)
    }
}

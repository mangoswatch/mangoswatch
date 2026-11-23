package org.mangoswatch.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.data.repository.AssetSyncImpl
import org.mangoswatch.data.vpn.VpnImpl
import org.mangoswatch.domain.model.AssetSyncError
import org.mangoswatch.domain.model.AssetSyncState
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.model.StreamProtocol
import org.mangoswatch.domain.parser.ip.IpRangesCache
import org.mangoswatch.domain.repository.AppInfoResolver
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.domain.model.AssetSync
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.mangoswatch.domain.repository.UserSettingsRepository
import org.mangoswatch.domain.model.VpnAction
import org.mangoswatch.domain.model.Vpn
import org.mangoswatch.domain.model.VpnStateEnum
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.R
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var streamsRepository: StreamsRepository
    private lateinit var taxonomyRepository: EndpointTaxonomyRepository
    private lateinit var vpn: Vpn
    private lateinit var appInfoResolver: AppInfoResolver
    private lateinit var userSettingsRepository: UserSettingsRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var assetSync: AssetSync
    private lateinit var ipRangesCache: IpRangesCache

    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        streamsRepository = mock(StreamsRepository::class.java)
        taxonomyRepository = mock(EndpointTaxonomyRepository::class.java)
        vpn = VpnImpl()
        appInfoResolver = mock(AppInfoResolver::class.java)
        userSettingsRepository = mock(UserSettingsRepository::class.java)
        assetRepository = mock(AssetRepository::class.java)
        assetSync = AssetSyncImpl().apply {
            updateState(AssetSyncState.Synced)
        }
        ipRangesCache = mock(IpRangesCache::class.java)

        `when`(streamsRepository.getStreams()).thenReturn(flowOf(emptyList()))
        `when`(userSettingsRepository.userSettings).thenReturn(flowOf(org.mangoswatch.domain.model.UserSettings()))
        `when`(assetRepository.isCachePopulated()).thenReturn(true)

        viewModel = MainScreenViewModel(
            streamsRepository = streamsRepository,
            taxonomyRepository = taxonomyRepository,
            vpn = vpn,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userSettingsRepository = userSettingsRepository,
            assetRepository = assetRepository,
            ipRangesCache = ipRangesCache
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
        viewModel.state.value.eventTunnel(MainScreenEvent.OnStopVpnRequest)

        assertEquals(
            listOf(VpnAction.START, VpnAction.STOP),
            vpn.requests.take(2).toList()
        )
    }

    @Test
    fun testToAppTrafficIncludesUnclassifiedAndFiltersReserved() = runTest {
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

        vpn.updateState(VpnStateEnum.RUNNING)

        val vm = MainScreenViewModel(
            streamsRepository = mockStreamsRepo,
            taxonomyRepository = taxonomyRepository,
            vpn = vpn,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userSettingsRepository = userSettingsRepository,
            assetRepository = assetRepository,
            ipRangesCache = ipRangesCache
        )

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val apps = vm.state.value.apps
        assertEquals(2, apps.size)
        val uids = apps.map { it.uid }.toSet()
        assertTrue(uids.contains(10001))
        assertTrue(uids.contains(10002))
        assertFalse(uids.contains(10003))

        val unclassifiedApp = apps.first { it.uid == 10002 }
        assertEquals("unknown.com", unclassifiedApp.hosts[0].host)
        assertEquals(IpOwner.OTHER, unclassifiedApp.hosts[0].ipOwner)
    }

    @Test
    fun testOnUpdateDnsEvent() = runTest {
        viewModel.state.value.eventTunnel(MainScreenEvent.OnUpdateDns(DnsMode.CUSTOM, "1.1.1.1", "2606:4700:4700::1111"))
        testDispatcher.scheduler.advanceUntilIdle()
        verify(userSettingsRepository).updateDnsSettings(any())
    }

    @Test
    fun testStartVpnWhenAssetsMissingFailsWithMessage() = runTest {
        assetSync.updateState(AssetSyncState.Syncing())
        `when`(assetRepository.isCachePopulated()).thenReturn(false)
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            vpn.requests.first()
        }

        viewModel.state.value.eventTunnel(MainScreenEvent.OnStartVpnRequest)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(R.string.error_no_assets_vpn, viewModel.state.value.errorMessage)
        assertFalse(request.isCompleted)
        request.cancel()
    }

    @Test
    fun testStartVpnWithCachedAssetsWhenSyncFailed() = runTest {
        assetSync.updateState(
            AssetSyncState.Error(AssetSyncError.Unexpected)
        )
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            vpn.requests.first()
        }

        viewModel.state.value.eventTunnel(MainScreenEvent.OnStartVpnRequest)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(VpnAction.START, request.await())
        assertEquals(R.string.asset_sync_failed_subtitle, viewModel.state.value.errorMessage)
    }

    @Test
    fun testStartVpnWaitsForAssetsSyncWhenCacheExists() = runTest {
        assetSync.updateState(AssetSyncState.Syncing())
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            vpn.requests.first()
        }

        viewModel.state.value.eventTunnel(MainScreenEvent.OnStartVpnRequest)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(request.isCompleted)
        request.cancel()
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun testStreamProcessingWhenVpnRunning() = runTest {
        vpn.updateState(VpnStateEnum.RUNNING)
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(VpnStateEnum.RUNNING, state.vpnState)
    }

    @Test
    fun testToAppTrafficGroupsByHostAndAvoidsDuplicateKeys() = runTest {
        val streamA = StreamModel(
            protocol = 6,
            srcIpStr = "10.0.0.1",
            srcPort = 12345,
            dstIpStr = "172.217.16.206",
            dstPort = 5228,
            ipOwner = IpOwner.GOOGLE,
            firstSeen = System.currentTimeMillis(),
            uid = 10001,
            sni = "alt4-mtalk.google.com",
            isTls = true,
            bytesUp = 100L,
            bytesDown = 200L
        )

        val streamB = StreamModel(
            protocol = 17,
            srcIpStr = "10.0.0.1",
            srcPort = 54321,
            dstIpStr = "1.1.1.1",
            dstPort = 53,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis(),
            uid = 10001,
            dnsQuery = "alt4-mtalk.google.com",
            bytesUp = 50L,
            bytesDown = 50L
        )

        val mockStreamsRepo = mock(StreamsRepository::class.java)
        `when`(mockStreamsRepo.getStreams()).thenReturn(flowOf(listOf(streamA, streamB)))

        vpn.updateState(VpnStateEnum.RUNNING)

        val vm = MainScreenViewModel(
            streamsRepository = mockStreamsRepo,
            taxonomyRepository = taxonomyRepository,
            vpn = vpn,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userSettingsRepository = userSettingsRepository,
            assetRepository = assetRepository,
            ipRangesCache = ipRangesCache
        )

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val apps = vm.state.value.apps
        assertEquals(1, apps.size)
        val app = apps[0]
        assertEquals(10001, app.uid)
        assertEquals(1, app.hosts.size)
        assertEquals(app.hosts.map { it.host }.distinct().size, app.hosts.size)

        val hostTraffic = app.hosts[0]
        assertEquals("alt4-mtalk.google.com", hostTraffic.host)
        assertEquals(IpOwner.GOOGLE, hostTraffic.ipOwner)
        assertEquals(150L, hostTraffic.bytesUp)
        assertEquals(250L, hostTraffic.bytesDown)
        assertTrue(hostTraffic.protocols.contains(StreamProtocol.TLS))
        assertTrue(hostTraffic.protocols.contains(StreamProtocol.DNS))
    }

    @Test
    fun testToAppTrafficTaxonomyMatchesHostOwner() = runTest {
        val taxonomyItem = EndpointTaxonomy(
            id = "google_push_001",
            provider = "GOOGLE",
            hostnamePattern = ".*google\\.com$",
            displayName = "Google Push",
            category = EndpointTaxonomyCategory.NOTIFICATION_PUSH,
            description = "Push service",
            dataCollected = emptyList(),
            optOutDescription = ""
        )
        `when`(taxonomyRepository.findMatch("alt4-mtalk.google.com")).thenReturn(taxonomyItem)

        val stream = StreamModel(
            protocol = 17,
            srcIpStr = "10.0.0.1",
            srcPort = 54321,
            dstIpStr = "1.1.1.1",
            dstPort = 53,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis(),
            uid = 10001,
            dnsQuery = "alt4-mtalk.google.com",
            bytesUp = 50L,
            bytesDown = 50L
        )

        val mockStreamsRepo = mock(StreamsRepository::class.java)
        `when`(mockStreamsRepo.getStreams()).thenReturn(flowOf(listOf(stream)))

        vpn.updateState(VpnStateEnum.RUNNING)

        val vm = MainScreenViewModel(
            streamsRepository = mockStreamsRepo,
            taxonomyRepository = taxonomyRepository,
            vpn = vpn,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userSettingsRepository = userSettingsRepository,
            assetRepository = assetRepository,
            ipRangesCache = ipRangesCache
        )

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val apps = vm.state.value.apps
        assertEquals(1, apps.size)
        val hostTraffic = apps[0].hosts[0]
        assertEquals(IpOwner.GOOGLE, hostTraffic.ipOwner)
        assertEquals(taxonomyItem, hostTraffic.taxonomyItem)
    }

    @Test
    fun testManualAssetSyncSuccessTriggersReloadOnRepositories() = runTest {
        `when`(assetRepository.syncAssets()).thenAnswer {
            assetSync.updateState(AssetSyncState.Synced)
        }

        viewModel.state.value.eventTunnel(MainScreenEvent.OnManualAssetSync)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(ipRangesCache).reload()
        verify(taxonomyRepository).reloadTaxonomy()
    }

    @Test
    fun testManualAssetSyncFailureDoesNotTriggerReloadOnRepositories() = runTest {
        `when`(assetRepository.syncAssets()).thenAnswer {
            assetSync.updateState(AssetSyncState.Error(AssetSyncError.Unexpected))
        }

        viewModel.state.value.eventTunnel(MainScreenEvent.OnManualAssetSync)
        testDispatcher.scheduler.advanceUntilIdle()

        org.mockito.Mockito.verifyNoInteractions(ipRangesCache)
        org.mockito.Mockito.verifyNoInteractions(taxonomyRepository)
    }

    @Test
    fun testResolveTitleReturnsNullForUnidentifiedApp() = runTest {
        val unidentifiedStream = StreamModel(
            protocol = 6,
            srcIpStr = "10.0.0.1",
            srcPort = 12345,
            dstIpStr = "1.2.3.4",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis(),
            uid = 10099,
            sni = "unknown.com"
        )
        `when`(appInfoResolver.resolve(10099)).thenReturn(null)

        val mockStreamsRepo = mock(StreamsRepository::class.java)
        `when`(mockStreamsRepo.getStreams()).thenReturn(flowOf(listOf(unidentifiedStream)))

        vpn.updateState(VpnStateEnum.RUNNING)

        val vm = MainScreenViewModel(
            streamsRepository = mockStreamsRepo,
            taxonomyRepository = taxonomyRepository,
            vpn = vpn,
            assetSync = assetSync,
            appInfo = appInfoResolver,
            userSettingsRepository = userSettingsRepository,
            assetRepository = assetRepository,
            ipRangesCache = ipRangesCache
        )

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(200)
        testDispatcher.scheduler.advanceUntilIdle()

        val apps = vm.state.value.apps
        assertEquals(1, apps.size)
        assertNull(apps[0].title)
        assertNull(apps[0].packageName)
    }
}

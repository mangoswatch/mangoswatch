package org.mangoswatch.presentation.viewmodel

import android.os.Process
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.repository.EndpointTaxonomyRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.mangoswatch.data.resolver.AppInfoResolver
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.presentation.contracts.MainScreenState
import org.mangoswatch.presentation.contracts.VpnAction
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.domain.model.StreamProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModel(
    private val streamsRepository: StreamsRepository,
    private val taxonomyRepository: EndpointTaxonomyRepository,
    private val vpnState: VpnState,
    private val appInfo: AppInfoResolver
) : ViewModel() {

    private val _state = MutableStateFlow(
        MainScreenState(
            eventTunnel = { event ->
                processEvent(event)
            }
        )
    )
    val state: StateFlow<MainScreenState> = _state.asStateFlow()

    private fun processEvent(event: MainScreenEvent) {
        when (event) {
            MainScreenEvent.OnBack -> onBack()
            is MainScreenEvent.OnAppClick -> onAppClick(event.appUid)
            MainScreenEvent.OnStartVpnClick -> _state.update { it.copy(vpnAction = VpnAction.START) }
            MainScreenEvent.OnStopVpnClick -> _state.update { it.copy(vpnAction = VpnAction.STOP) }
        }
    }

    init {
        Log.d("[MANGOS_WATCH] - MAINSCREENVIEWMODEL ", "VM Initialized")
        viewModelScope.launch {
            vpnState.running
                .flatMapLatest { running ->
                    _state.update { it.copy(isVpnRunning = running) }
                    if (running) streamsRepository.getStreams() else emptyFlow()
                }
                .map { it.toAppTraffic() }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .collect { apps ->
                    _state.update { it.copy(apps = apps) }
                }
        }

    }

    private fun onAppClick(uid: Int) {
        _state.update { it.copy(selectedUid = uid) }
    }

    private fun onBack() {
        _state.update { it.copy(selectedUid = null) }
    }

    private fun List<StreamModel>.toAppTraffic(): List<AppTrafficUi> =
        filter { it.ipOwner != IpOwner.RESERVED && it.ipOwner != IpOwner.OTHER } // We only show confirmed big tech
            .groupBy { it.uid }
            .map { (uid, streams) ->
                val (title, pkg) = resolveTitle(uid, streams)
                AppTrafficUi(
                    uid = uid,
                    title = title,
                    packageName = pkg,
                    bytesUp = streams.sumOf { it.bytesUp },
                    bytesDown = streams.sumOf { it.bytesDown },
                    streamCount = streams.size,
                    hosts = streams
                        .groupBy { stream ->
                            Pair(stream.hostname().takeIf { it != "?" } ?: stream.dstIpStr,
                                stream.ipOwner)
                        }
                        .map { (host, s) ->
                            val taxonomy = taxonomyRepository.findMatch(host.first)
                            val resolvedIpOwner = taxonomy?.provider?.let { provider ->
                                try { IpOwner.valueOf(provider) } catch (e: Exception) { host.second }
                            } ?: host.second

                            HostTrafficUi(
                                host = host.first, // hostname
                                ipOwner = resolvedIpOwner, // ipOwner
                                bytesUp = s.sumOf { it.bytesUp },
                                bytesDown = s.sumOf { it.bytesDown },
                                streamCount = s.size,
                                protocols = s.groupBy { it.semanticProtocol }.entries
                                    .sortedByDescending { (_, ss) -> ss.sumOf { it.bytesUp + it.bytesDown } }
                                    .map { it.key },
                                taxonomyItem = taxonomy
                            )
                        }
                        .sortedByDescending { it.totalBytes }
                )
            }
            .sortedByDescending { it.totalBytes }


    private fun resolveTitle(uid: Int, streams: List<StreamModel>): Pair<String, String?> {
        val identity = if (uid == Process.INVALID_UID) null else appInfo.resolve(uid)
        val pkg = identity?.pkg ?: streams.firstNotNullOfOrNull { it.pkg }
        val title = identity?.label
            ?: pkg
            ?: if (uid == Process.INVALID_UID) "Not identified" else "uid $uid"
        return title to pkg
    }

}

@Immutable
data class AppTrafficUi(
    val uid: Int,
    val title: String,
    val packageName: String?,
    val bytesUp: Long,
    val bytesDown: Long,
    val streamCount: Int,
    val hosts: List<HostTrafficUi>
) {
    val totalBytes: Long get() = bytesUp + bytesDown
}

@Immutable
data class HostTrafficUi(
    val host: String,          // SNI/DNS/IP
    val ipOwner: IpOwner,
    val bytesUp: Long,
    val bytesDown: Long,
    val streamCount: Int,
    val protocols: List<StreamProtocol>,
    val taxonomyItem: EndpointTaxonomy? = null
) {
    val totalBytes: Long get() = bytesUp + bytesDown
}


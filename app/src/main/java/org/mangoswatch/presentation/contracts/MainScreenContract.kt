package org.mangoswatch.presentation.contracts

import androidx.compose.runtime.compositionLocalOf
import org.mangoswatch.data.repository.AssetsSyncState
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.data.vpn.VpnStateEnum
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.presentation.viewmodel.AppTrafficUi

sealed class MainScreenEvent {
    object OnBack : MainScreenEvent()
    data class OnAppClick(val appUid: Int) : MainScreenEvent()
    object OnStartVpnRequest : MainScreenEvent()
    object OnStopVpnRequest : MainScreenEvent()
    data class OnShowSettings(val show: Boolean) : MainScreenEvent()
    data class OnUpdateDns(val mode: DnsMode, val v4: String, val v6: String) : MainScreenEvent()
    object OnManualAssetSync : MainScreenEvent()
}

data class MainScreenState(
    val vpnState: VpnStateEnum = VpnStateEnum.IDLE,
    val assetsSyncState: AssetsSyncState = AssetsSyncState.SYNCING,
    val selectedUid: Int? = null,
    val apps: List<AppTrafficUi> = emptyList(),
    val errorMessage: String? = null,
    val isSettingsOpen: Boolean = false,
    val currentDnsSetting: DnsSettings? = null,
    // TUNNEL
    val eventTunnel: (MainScreenEvent) -> Unit = {}
) {
    val detail: AppTrafficUi? get() = apps.firstOrNull { it.uid == selectedUid }
}

val LocalMainScreenState = compositionLocalOf<MainScreenState> { error("MainScreenState not provided") }
val LocalEventTunnel = compositionLocalOf<(MainScreenEvent) -> Unit> { error("EventTunnel not provided") }

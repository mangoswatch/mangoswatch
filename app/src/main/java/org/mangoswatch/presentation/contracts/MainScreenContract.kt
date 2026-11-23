package org.mangoswatch.presentation.contracts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import org.mangoswatch.R
import org.mangoswatch.domain.model.AssetSyncState
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.UserSettings
import org.mangoswatch.domain.model.VpnStateEnum
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
    val assetSyncState: AssetSyncState = AssetSyncState.Syncing(),
    val selectedUid: Int? = null,
    val apps: List<AppTrafficUi> = emptyList(),
    @StringRes val errorMessage: Int? = null,
    val isSettingsOpen: Boolean = false,
    val currentUserSetting: UserSettings? = null,
    // TUNNEL
    val eventTunnel: (MainScreenEvent) -> Unit = {}
) {
    val detail: AppTrafficUi? get() = apps.firstOrNull { it.uid == selectedUid }
}

val LocalMainScreenState = compositionLocalOf<MainScreenState> { error("MainScreenState not provided") }
val LocalEventTunnel = compositionLocalOf<(MainScreenEvent) -> Unit> { error("EventTunnel not provided") }

@Composable
fun AppTrafficUi.displayTitle(): String = title ?: when (uid) {
    android.os.Process.INVALID_UID -> stringResource(R.string.label_not_identified)
    else -> stringResource(R.string.label_uid_format, uid)
}

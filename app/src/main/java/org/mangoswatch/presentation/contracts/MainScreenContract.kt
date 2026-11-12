package org.mangoswatch.presentation.contracts

import org.mangoswatch.presentation.viewmodel.AppTrafficUi

sealed class MainScreenEvent {
    object OnBack : MainScreenEvent()
    data class OnAppClick (val appUid: Int) : MainScreenEvent()
    object OnStartVpnClick : MainScreenEvent()
    object OnStopVpnClick : MainScreenEvent()
}

enum class VpnAction {
    START,
    STOP
}



data class MainScreenState(
    val isVpnRunning: Boolean = false,
    val selectedUid: Int? = null,
    val apps: List<AppTrafficUi> = emptyList(),
    val errorMessage: String? = null,
    val vpnAction: VpnAction? = null,
    // TUNNEL
    val eventTunnel: (MainScreenEvent) -> Unit = {}
){
    val detail: AppTrafficUi? get() = apps.firstOrNull { it.uid == selectedUid }
}



package org.mangoswatch.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Vpn {
    val state: StateFlow<VpnStateEnum>
    val requests: Flow<VpnAction>

    fun requestStart()
    fun requestStop()
    fun updateState(state: VpnStateEnum)
}

enum class VpnStateEnum { RUNNING, STOPPED, LOADING, IDLE }
enum class VpnAction { START, STOP }

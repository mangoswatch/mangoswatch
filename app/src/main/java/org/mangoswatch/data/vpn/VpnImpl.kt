package org.mangoswatch.data.vpn

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.mangoswatch.domain.model.VpnAction
import org.mangoswatch.domain.model.VpnStateEnum
import org.mangoswatch.domain.model.Vpn

class VpnImpl : Vpn {
    private val _state = MutableStateFlow(VpnStateEnum.IDLE)
    override val state: StateFlow<VpnStateEnum> = _state.asStateFlow()

    private val _requests = Channel<VpnAction>(Channel.BUFFERED)
    override val requests: Flow<VpnAction> = _requests.receiveAsFlow()

    override fun requestStart() = enqueueRequest(VpnAction.START)

    override fun requestStop() = enqueueRequest(VpnAction.STOP)

    override fun updateState(state: VpnStateEnum) {
        _state.value = state
    }

    private fun enqueueRequest(action: VpnAction) {
        check(_requests.trySend(action).isSuccess) {
            "Unable to enqueue VPN action $action"
        }
    }
}

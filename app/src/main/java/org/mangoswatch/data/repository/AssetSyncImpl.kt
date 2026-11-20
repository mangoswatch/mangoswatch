package org.mangoswatch.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mangoswatch.domain.model.AssetSyncState
import org.mangoswatch.domain.model.AssetSync

class AssetSyncImpl : AssetSync {
    private val _state = MutableStateFlow<AssetSyncState>(AssetSyncState.Syncing())
    override val state: StateFlow<AssetSyncState> = _state.asStateFlow()

    override fun updateState(state: AssetSyncState) {
        _state.value = state
    }
}

package org.mangoswatch.domain.model

import kotlinx.coroutines.flow.StateFlow

interface AssetSync {
    val state: StateFlow<AssetSyncState>
    fun updateState(state: AssetSyncState)
}

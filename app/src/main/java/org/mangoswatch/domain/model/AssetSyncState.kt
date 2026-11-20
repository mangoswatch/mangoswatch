package org.mangoswatch.domain.model

data class AssetSyncProgress(
    val completedFiles: Int,
    val totalFiles: Int
) {
    init {
        require(totalFiles > 0)
        require(completedFiles in 0..totalFiles)
    }

    val fraction: Float
        get() = completedFiles.toFloat() / totalFiles
}

sealed interface AssetSyncError {
    data object IndexDownloadFailed : AssetSyncError
    data object InvalidIndex : AssetSyncError
    data class AssetDownloadFailed(val fileName: String) : AssetSyncError
    data class InvalidAssetContent(val fileName: String) : AssetSyncError
    data object Cancelled : AssetSyncError
    data object Unexpected : AssetSyncError
}

sealed interface AssetSyncState {
    data object SyncNeeded : AssetSyncState
    data object Synced : AssetSyncState
    data class Syncing(val progress: AssetSyncProgress? = null) : AssetSyncState
    data class Error(val reason: AssetSyncError) : AssetSyncState
}

package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mangoswatch.R
import org.mangoswatch.domain.model.AssetSyncError

@Composable
fun AssetSyncNotice(
    messageResId: Int?,
    error: AssetSyncError?,
    onRetry: () -> Unit
) {
    val message = when {
        messageResId != null -> stringResource(messageResId)
        error == AssetSyncError.IndexDownloadFailed ->
            stringResource(R.string.asset_sync_error_index_download)
        error == AssetSyncError.InvalidIndex ->
            stringResource(R.string.asset_sync_error_invalid_index)
        error is AssetSyncError.AssetDownloadFailed ->
            stringResource(R.string.asset_sync_error_asset_download, error.fileName)
        error is AssetSyncError.InvalidAssetContent ->
            stringResource(R.string.asset_sync_error_invalid_content, error.fileName)
        error == AssetSyncError.Cancelled ->
            stringResource(R.string.asset_sync_error_cancelled)
        else -> stringResource(R.string.asset_sync_failed_subtitle)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.asset_sync_failed_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.btn_retry_sync))
            }
        }
    }
}

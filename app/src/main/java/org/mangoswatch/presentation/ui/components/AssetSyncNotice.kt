package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp),
                    tint = colors.error
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.asset_sync_failed_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_retry_sync))
            }
        }
    }
}

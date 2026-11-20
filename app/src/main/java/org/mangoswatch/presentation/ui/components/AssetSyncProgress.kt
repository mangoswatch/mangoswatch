package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mangoswatch.R
import org.mangoswatch.domain.model.AssetSyncProgress

@Composable
fun AssetSyncProgressIndicator(
    progress: AssetSyncProgress?,
    modifier: Modifier = Modifier
) {
    val knownProgress = progress?.takeIf { it.totalFiles > 0 }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (knownProgress != null) {
            Text(
                text = stringResource(
                    R.string.asset_sync_progress_files,
                    knownProgress.completedFiles,
                    knownProgress.totalFiles
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { knownProgress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = stringResource(R.string.asset_sync_progress_preparing),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun AssetSyncProgressNotice(progress: AssetSyncProgress?) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Downloading,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.primary
                )
                Text(
                    text = stringResource(R.string.asset_sync_progress_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            AssetSyncProgressIndicator(progress)
        }
    }
}

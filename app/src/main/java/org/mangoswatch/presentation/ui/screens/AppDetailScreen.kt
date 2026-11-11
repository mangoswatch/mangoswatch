package org.mangoswatch.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.ui.components.AppIcon
import org.mangoswatch.presentation.ui.components.HostItem
import org.mangoswatch.presentation.viewmodel.AppTrafficUi
import org.mangoswatch.util.formatBytes

@Composable
fun AppDetailScreen(
    appTraffic: AppTrafficUi,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top header") {
            AppDetailHeader(appTraffic)
        }
        items(appTraffic.hosts, key = { it.host }) { host ->
            HostItem(
                host = host,
                isDetail = true,
                modifier = Modifier.animateItem()
            )
        }

    }

}

@Composable
fun AppDetailHeader(
    appTraffic: AppTrafficUi,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(
                    packageName = appTraffic.packageName,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(
                        text = appTraffic.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    appTraffic.packageName?.takeIf { it != appTraffic.title }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "↑ ${formatBytes(appTraffic.bytesUp)}   ↓ ${formatBytes(appTraffic.bytesDown)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${appTraffic.streamCount} streams / ${appTraffic.hosts.size} host",
                    style = MaterialTheme.typography.bodyMedium
                )

            }
        }
    }
}


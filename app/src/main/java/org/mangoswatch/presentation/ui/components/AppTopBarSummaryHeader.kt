package org.mangoswatch.presentation.ui.components

import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.viewmodel.AppTrafficUi
import org.mangoswatch.util.formatBytes

@Composable
fun AppTopBarSummaryHeader(
    apps: List<AppTrafficUi>
) {
    val totalUp = remember(apps) { apps.sumOf { it.bytesUp } }
    val totalDown = remember(apps) { apps.sumOf { it.bytesDown } }
    val appCount = remember(apps) { apps.count { it.uid != Process.INVALID_UID } }
    val totalsByOwner = remember(apps) {
        apps
            .flatMap { it.hosts }
            .groupingBy { it.ipOwner }
            .fold(0L) { acc, host -> acc + host.totalBytes }
    }
    var openSummaryDetail by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1F)
            ) {
                Text(
                    text = "Big Tech intercepted bytes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatBytes(totalUp + totalDown)} from $appCount app",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "↑ ${formatBytes(totalUp)}   ↓ ${formatBytes(totalDown)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { openSummaryDetail = !openSummaryDetail },
                content = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Open Total traffic detail",
                        modifier = Modifier.rotate(if (openSummaryDetail) 180f else 0f)
                    )
                }
            )

        }

        if (openSummaryDetail) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Big Tech traffic detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                totalsByOwner.entries
                    .sortedByDescending { it.value }
                    .forEach { (owner, bytes) ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$owner",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatBytes(bytes),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
            }
        }
    }
}


@Preview
@Composable
fun AppTopBarSummaryHeaderPreview() {
    Column() {
        AppTopBarSummaryHeader(
            apps = listOf(
                AppTrafficUi(
                    uid = 12,
                    title = "sdfsdf",
                    packageName = "sdfgdfg",
                    bytesUp = 152,
                    bytesDown = 456,
                    streamCount = 9,
                    hosts = emptyList()
                )
            )
        )
    }
}

package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.viewmodel.AppTrafficUi
import org.mangoswatch.util.formatBytes

@Composable
fun AppOverviewItem(
    app: AppTrafficUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(app.packageName, Modifier.size(40.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppOverviewHeader(app)

                HorizontalDivider()

                if (app.hosts.isNotEmpty()) {
                    app.hosts.take(3).forEach {
                        HostItem(
                            host = it,
                            isDetail = false
                        )
                    }
                    if (app.hosts.size > 3) {
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }
            }
        }

    }
}

@Composable
fun AppOverviewHeader(app: AppTrafficUi) {
    Column{
        Text(
            text = app.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("↑ ${formatBytes(app.bytesUp)}")
            Text("↓ ${formatBytes(app.bytesDown)}")
            Text(
                text = "${app.streamCount} stream",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}




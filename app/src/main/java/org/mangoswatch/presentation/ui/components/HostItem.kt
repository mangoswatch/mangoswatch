package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.model.EndpointTaxonomyCategory
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.StreamProtocol
import org.mangoswatch.presentation.viewmodel.HostTrafficUi
import org.mangoswatch.util.formatBytes

@Composable
fun HostItem(
    host: HostTrafficUi,
    isDetail: Boolean,
    modifier: Modifier = Modifier
) {
    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showPrivacyDialog && host.taxonomyItem != null) {
        PrivacyDetailsDialog(
            item = host.taxonomyItem,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    val hostStyle =
        if (isDetail) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = { if (host.taxonomyItem != null) showPrivacyDialog = true },
        enabled = host.taxonomyItem != null,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 6.dp,
                    horizontal = 4.dp
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
        // Top Row: Hostname (weighted) + Protocol & IP Owner grouped together side by side
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = host.host,
                style = hostStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1F)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = host.protocols.joinToString(
                        prefix = "(",
                        postfix = ")",
                        separator = " / "
                    ) { protocol ->
                        protocol.name
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = host.ipOwner.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom Row: Privacy Badge (if any) + Stream count + Traffic stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            if (host.taxonomyItem != null) {
                Text(
                    text = host.taxonomyItem.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        //.weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 2.dp
                        )
                )
            }
            else {
                Spacer(
                    modifier = Modifier
                        //.weight(1f)
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                if(isDetail) {
                    Text(
                        text = "${host.streamCount} streams",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )
                }


                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                    //.weight(1f)
                ) {
                    Text(
                        text = "↑ ${formatBytes(host.bytesUp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "↓ ${formatBytes(host.bytesDown)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


        }
      }
    }
}

@Preview(showBackground = true)
@Composable
fun HostItemPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        HostItem(
            host = HostTrafficUi(
                host = "play.googleapis.com",
                protocols = listOf(StreamProtocol.DNS, StreamProtocol.TLS),
                ipOwner = IpOwner.GOOGLE,
                bytesUp = 123,
                bytesDown = 12341324,
                streamCount = 5,
                taxonomyItem = EndpointTaxonomy(
                    id = "clearcut_batch_001",
                    provider = "GOOGLE",
                    hostnamePattern = "play\\.googleapis\\.com",
                    displayName = "Clearcut Logger",
                    category = EndpointTaxonomyCategory.CLEARCUT_TELEMETRY,
                    description = "Batch telemetry logging.",
                    dataCollected = listOf("App usage frequency", "Crash reports"),
                    optOutDescription = "Disable Usage & diagnostics."
                )
            ),
            isDetail = false
        )

        HostItem(
            host = HostTrafficUi(
                host = "192.168.1.1dfgsdrfte4w5trg534terg54ges54tesrgse45t3e44567the456hrth",
                protocols = listOf(StreamProtocol.DNS, StreamProtocol.TCP),
                ipOwner = IpOwner.GOOGLE,
                bytesUp = 123,
                bytesDown = 12341324,
                streamCount = 5,
/*                taxonomyItem = EndpointTaxonomy(
                    id = "clearcut_batch_001",
                    provider = "GOOGLE",
                    hostnamePattern = "play\\.googleapis\\.com",
                    displayName = "Clearcut Logger",
                    category = EndpointTaxonomyCategory.CLEARCUT_TELEMETRY,
                    description = "Batch telemetry logging.",
                    dataCollected = listOf("App usage frequency", "Crash reports"),
                    optOutDescription = "Disable Usage & diagnostics."
                )*/
            ),
            isDetail = true
        )
    }
}

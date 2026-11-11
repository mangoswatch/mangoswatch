package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mangoswatch.domain.model.EndpointTaxonomy
import org.mangoswatch.domain.model.EndpointTaxonomyCategory

@Composable
fun PrivacyDetailsDialog(
    item: EndpointTaxonomy,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Category: ${item.category.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (item.dataCollected.isNotEmpty()) {
                    Text(
                        text = "Data Collected:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    item.dataCollected.forEach { data ->
                        Text(
                            text = "• $data",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (item.optOutDescription.isNotBlank()) {
                    Text(
                        text = "Opt-out / Mitigation:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = item.optOutDescription,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
@Preview
fun PrivacyDetailsDialogPreview(){
    PrivacyDetailsDialog(
        item = EndpointTaxonomy(
            id = "clearcut_batch_001",
            provider = "GOOGLE",
            hostnamePattern = "play\\.googleapis\\.com",
            displayName = "Clearcut Logger",
            category = EndpointTaxonomyCategory.CLEARCUT_TELEMETRY,
            description = "TODO()",
            dataCollected = listOf(),
            optOutDescription = "TODO(),"
        )
    ) { }
}
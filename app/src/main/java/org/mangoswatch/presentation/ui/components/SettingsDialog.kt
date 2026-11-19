package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mangoswatch.R
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings

@Composable
fun SettingsDialog(
    dnsSettings: DnsSettings,
    onDismiss: () -> Unit,
    onUpdateDns: (DnsMode, String, String) -> Unit,
    onManualSync: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(dnsSettings.dnsMode) }
    var v4 by remember { mutableStateOf(dnsSettings.dnsV4) }
    var v6 by remember { mutableStateOf(dnsSettings.dnsV6) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: DNS Configuration
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_section_dns),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(modifier = Modifier.selectableGroup()) {
                        // Cloudflare option
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (selectedMode == DnsMode.CLOUDFLARE),
                                    onClick = { selectedMode = DnsMode.CLOUDFLARE },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedMode == DnsMode.CLOUDFLARE),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stringResource(R.string.dns_cloudflare_title), style = MaterialTheme.typography.bodyLarge)
                                Text(text = stringResource(R.string.dns_cloudflare_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // System / Private DNS option
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (selectedMode == DnsMode.SYSTEM),
                                    onClick = { selectedMode = DnsMode.SYSTEM },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedMode == DnsMode.SYSTEM),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stringResource(R.string.dns_system_title), style = MaterialTheme.typography.bodyLarge)
                                Text(text = stringResource(R.string.dns_system_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Custom DNS option
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (selectedMode == DnsMode.CUSTOM),
                                    onClick = { selectedMode = DnsMode.CUSTOM },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedMode == DnsMode.CUSTOM),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stringResource(R.string.dns_custom_title), style = MaterialTheme.typography.bodyLarge)
                                Text(text = stringResource(R.string.dns_custom_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (selectedMode == DnsMode.CUSTOM) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = v4,
                            onValueChange = { v4 = it },
                            label = { Text(stringResource(R.string.dns_custom_ipv4_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = v6,
                            onValueChange = { v6 = it },
                            label = { Text(stringResource(R.string.dns_custom_ipv6_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // SECTION 2: Asset Management
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_section_assets),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = onManualSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_sync_assets))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onUpdateDns(selectedMode, v4, v6)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

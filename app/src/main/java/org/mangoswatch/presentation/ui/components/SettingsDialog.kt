package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings

@Composable
fun SettingsDialog(
    dnsSettings: DnsSettings,
    onDismiss: () -> Unit,
    onUpdateDns: (DnsMode,String, String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(dnsSettings.dnsMode) }
    var v4 by remember { mutableStateOf(dnsSettings.dnsV4) }
    var v6 by remember { mutableStateOf(dnsSettings.dnsV6) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("VPN DNS Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose how DNS is handled inside the VPN tunnel. If you use a private DNS or local resolver, select System/Private DNS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                        Text(text = "Cloudflare DNS (Default)", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "1.1.1.1, 2606:4700:4700::1111", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(text = "System / Private DNS", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Respects your private DNS or local resolver", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(text = "Custom DNS Servers", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Specify your own IPv4 and IPv6 servers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (selectedMode == DnsMode.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = v4,
                        onValueChange = { v4 = it },
                        label = { Text("Custom IPv4 DNS (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = v6,
                        onValueChange = { v6 = it },
                        label = { Text("Custom IPv6 DNS (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onUpdateDns(selectedMode,v4,v6)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

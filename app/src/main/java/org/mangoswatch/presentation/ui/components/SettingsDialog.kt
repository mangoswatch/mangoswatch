package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mangoswatch.R
import org.mangoswatch.domain.model.DnsMode
import org.mangoswatch.domain.model.DnsSettings
import org.mangoswatch.domain.parser.ip.IpValidator

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
    var validationError by remember { mutableStateOf<String?>(null) }

    val dnsChanged = (selectedMode != dnsSettings.dnsMode) || (v4 != dnsSettings.dnsV4) || (v6 != dnsSettings.dnsV6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            SettingsDialogContent(
                selectedMode = selectedMode,
                onModeChange = { selectedMode = it },
                v4 = v4,
                onV4Change = { v4 = it },
                v6 = v6,
                onV6Change = { v6 = it },
                validationError = validationError,
                dnsChanged = dnsChanged,
                onManualSync = {
                    onManualSync()
                    onDismiss()
                }
            )
        },
        confirmButton = {
            val invalidIpErrorText = stringResource(R.string.error_invalid_dns_ip)
            val customRequiredErrorText = stringResource(R.string.dns_custom_required_error)
            TextButton(
                onClick = {
                    if (selectedMode == DnsMode.CUSTOM) {
                        val v4List = v4.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val v6List = v6.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        if (v4List.isEmpty() && v6List.isEmpty()) {
                            validationError = customRequiredErrorText
                            return@TextButton
                        }

                        val v4Valid = v4List.all { IpValidator.isValidIpv4(it) }
                        val v6Valid = v6List.all { IpValidator.isValidIpv6(it) }

                        if (!v4Valid || !v6Valid) {
                            validationError = invalidIpErrorText
                            return@TextButton
                        }
                    }
                    validationError = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialogContent(
    selectedMode: DnsMode,
    onModeChange: (DnsMode) -> Unit,
    v4: String,
    onV4Change: (String) -> Unit,
    v6: String,
    onV6Change: (String) -> Unit,
    validationError: String?,
    dnsChanged: Boolean,
    onManualSync: () -> Unit
) {
    var dnsDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // DNS Configuration Dropdown
        ExposedDropdownMenuBox(
            expanded = dnsDropdownExpanded,
            onExpandedChange = { dnsDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = when (selectedMode) {
                    DnsMode.SYSTEM -> stringResource(R.string.dns_system_title)
                    DnsMode.CUSTOM -> stringResource(R.string.dns_custom_title)
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_section_dns)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = dnsDropdownExpanded,
                onDismissRequest = { dnsDropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dns_system_title)) },
                    onClick = {
                        onModeChange(DnsMode.SYSTEM)
                        dnsDropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dns_custom_title)) },
                    onClick = {
                        onModeChange(DnsMode.CUSTOM)
                        dnsDropdownExpanded = false
                    }
                )
            }
        }

        if (selectedMode == DnsMode.CUSTOM) {
            OutlinedTextField(
                value = v4,
                onValueChange = onV4Change,
                label = { Text(stringResource(R.string.dns_custom_ipv4_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = v6,
                onValueChange = onV6Change,
                label = { Text(stringResource(R.string.dns_custom_ipv6_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        if (dnsChanged) {
            Text(
                text = stringResource(R.string.dns_restart_vpn_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Sync Assets Option with Reload Icon
        OutlinedButton(
            onClick = onManualSync,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_sync_assets))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsDialogPreview() {
    var mode by remember { mutableStateOf(DnsMode.SYSTEM) }
    var v4 by remember { mutableStateOf("") }
    var v6 by remember { mutableStateOf("") }
    val initial = DnsSettings(dnsMode = DnsMode.SYSTEM, dnsV4 = "", dnsV6 = "")
    val dnsChanged = (mode != initial.dnsMode) || (v4 != initial.dnsV4) || (v6 != initial.dnsV6)

    MaterialTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
                SettingsDialogContent(
                    selectedMode = mode,
                    onModeChange = { mode = it },
                    v4 = v4,
                    onV4Change = { v4 = it },
                    v6 = v6,
                    onV6Change = { v6 = it },
                    validationError = null,
                    dnsChanged = dnsChanged,
                    onManualSync = {}
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {}) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    TextButton(onClick = {}) {
                        Text(stringResource(R.string.dialog_save))
                    }
                }
            }
        }
    }
}

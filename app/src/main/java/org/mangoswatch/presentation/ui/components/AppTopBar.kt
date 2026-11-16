package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.contracts.LocalEventTunnel
import org.mangoswatch.presentation.contracts.LocalMainScreenState
import org.mangoswatch.presentation.contracts.MainScreenEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar() {
    val mainScreenState = LocalMainScreenState.current
    val eventTunnel = LocalEventTunnel.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MangosWatch",
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                navigationIcon = {
                    if (mainScreenState.detail != null) {
                        IconButton(
                            onClick = { eventTunnel(MainScreenEvent.OnBack) },
                            content = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Back"
                                )
                            }
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { eventTunnel(MainScreenEvent.OnShowSettings(true)) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
            )

            if (mainScreenState.apps.isNotEmpty()) {
                AppTopBarSummaryHeader(apps = mainScreenState.apps)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                thickness = 1.5.dp
            )
        }
    }
}

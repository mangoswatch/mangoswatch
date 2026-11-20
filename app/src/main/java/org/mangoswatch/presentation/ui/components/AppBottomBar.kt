package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.contracts.LocalEventTunnel
import org.mangoswatch.presentation.contracts.LocalMainScreenState
import org.mangoswatch.presentation.contracts.MainScreenEvent

@Composable
fun AppBottomBar() {
    val mainScreenState = LocalMainScreenState.current
    val eventTunnel = LocalEventTunnel.current

    Column(
        modifier = Modifier.navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            thickness = 1.5.dp
        )
        BottomAppBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            AppBottomBarContent(
                onStartVPN = { eventTunnel(MainScreenEvent.OnStartVpnRequest) },
                onStopVPN = { eventTunnel(MainScreenEvent.OnStopVpnRequest) },
                vpnState = mainScreenState.vpnState,
                assetSyncState = mainScreenState.assetSyncState
            )
        }
    }
}

package org.mangoswatch.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

@Composable
fun AppBottomBar(
    mainScreenViewModel: MainScreenViewModel = koinViewModel()
) {

    val mainScreenState by mainScreenViewModel.state.collectAsState()
    val eventTunnel = mainScreenState.eventTunnel

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
                onStartVPN = { eventTunnel(MainScreenEvent.OnStartVpnClick) },
                onStopVPN = { eventTunnel(MainScreenEvent.OnStopVpnClick) },
                vpnRunning = mainScreenState.isVpnRunning
            )
        }
    }
}
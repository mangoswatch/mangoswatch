package org.mangoswatch.presentation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.presentation.ui.components.AppOverviewItem
import org.mangoswatch.presentation.ui.components.MainScreenBottomBarContent
import org.mangoswatch.presentation.ui.components.TrafficSummaryHeader
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartVPN: () -> Unit,
    onStopVPN: () -> Unit,
    mainScreenViewModel: MainScreenViewModel = koinViewModel()
) {

    val mainScreenState by mainScreenViewModel.state.collectAsState()
    val eventTunnel = mainScreenState.eventTunnel

    BackHandler(enabled = mainScreenState.detail != null) { eventTunnel(MainScreenEvent.OnBack) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            // Unified Single Block Top Bar + Telemetry HUD preserving CenterAlignedTopAppBar
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
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        navigationIcon = {
                            if (mainScreenState.detail != null) {
                                IconButton(
                                    onClick = { eventTunnel(MainScreenEvent.OnBack) },
                                    content = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                    )

                    // Integrated Traffic Summary Content seamlessly inside the same unified top block
                    TrafficSummaryHeader(apps = mainScreenState.apps)

                    // Bottom border framing the unified top header block
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        thickness = 1.5.dp
                    )
                }
            }
        },
        bottomBar = {
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
                    MainScreenBottomBarContent(
                        onStartVPN = { onStartVPN() },
                        onStopVPN = { onStopVPN() },
                        vpnRunning = mainScreenState.isVpnRunning
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(8.dp),
        ) {

            if (mainScreenState.detail != null) {
                AppDetailScreen(
                    appTraffic = mainScreenState.detail!!,
                    modifier = Modifier.weight(1f)
                )

            } else {
                LazyColumn(
                    modifier = Modifier.weight(1F),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                        items(mainScreenState.apps, key = { it.uid }) { app ->
                            AppOverviewItem(
                                app = app,
                                onClick = { eventTunnel(MainScreenEvent.OnAppClick(app.uid)) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                )
            }

        }

    }
}

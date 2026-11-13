package org.mangoswatch.presentation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.mangoswatch.data.vpn.VpnStateEnum
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.presentation.ui.components.AppBottomBar
import org.mangoswatch.presentation.ui.components.AppOverviewItem
import org.mangoswatch.presentation.ui.components.AppPlaceholder
import org.mangoswatch.presentation.ui.components.AppTopBar
import org.mangoswatch.presentation.ui.components.LoadingIndicator
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

@Composable
fun MainScreen(
    mainScreenViewModel: MainScreenViewModel = koinViewModel()
) {

    val mainScreenState by mainScreenViewModel.state.collectAsState()
    val eventTunnel = mainScreenState.eventTunnel

    BackHandler(enabled = mainScreenState.detail != null) { eventTunnel(MainScreenEvent.OnBack) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar()
        },
        bottomBar = {
            AppBottomBar()
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(8.dp),
        ) {

            when (mainScreenState.vpnState) {

                VpnStateEnum.RUNNING,
                VpnStateEnum.STOPPED -> {
                    if (mainScreenState.detail != null) {
                        AppDetailScreen(
                            appTraffic = mainScreenState.detail!!,
                            modifier = Modifier.weight(1f)
                        )

                    } else {
                        if (mainScreenState.apps.isEmpty()) {
                            AppPlaceholder(
                                icon = Icons.Default.Radar,
                                title = "Waiting for Traffic",
                                subtitle = "Inspect network activity as apps generate traffic",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(mainScreenState.apps.size) {
                                if (mainScreenState.apps.isNotEmpty()) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            LazyColumn(
                                state = listState,
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

                VpnStateEnum.LOADING -> {
                    LoadingIndicator(
                        modifier = Modifier.weight(1f)
                    )
                }

                VpnStateEnum.IDLE -> {
                    AppPlaceholder(
                        icon = Icons.Default.Visibility,
                        title = "Inspection Idle",
                        subtitle = "Start the traffic inspection with the button below",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }


        }

    }
}

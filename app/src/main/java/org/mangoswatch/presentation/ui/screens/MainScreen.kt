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
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.mangoswatch.R
import org.mangoswatch.domain.model.AssetSyncState
import org.mangoswatch.domain.model.VpnStateEnum
import org.mangoswatch.presentation.contracts.LocalEventTunnel
import org.mangoswatch.presentation.contracts.LocalMainScreenState
import org.mangoswatch.presentation.contracts.MainScreenEvent
import org.mangoswatch.presentation.ui.components.AppBottomBar
import org.mangoswatch.presentation.ui.components.AppOverviewItem
import org.mangoswatch.presentation.ui.components.AppPlaceholder
import org.mangoswatch.presentation.ui.components.AppTopBar
import org.mangoswatch.presentation.ui.components.AssetSyncNotice
import org.mangoswatch.presentation.ui.components.AssetSyncProgressIndicator
import org.mangoswatch.presentation.ui.components.AssetSyncProgressNotice
import org.mangoswatch.presentation.ui.components.ErrorMessageNotice
import org.mangoswatch.presentation.ui.components.LoadingIndicator
import org.mangoswatch.presentation.ui.components.SettingsDialog
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

@Composable
fun MainScreen(
    mainScreenViewModel: MainScreenViewModel = koinViewModel()
) {
    val mainScreenState by mainScreenViewModel.state.collectAsState()
    val userSettings by mainScreenViewModel.userSettings.collectAsState()

    CompositionLocalProvider(
        LocalMainScreenState provides mainScreenState,
        LocalEventTunnel provides mainScreenState.eventTunnel
    ) {
        val eventTunnel = LocalEventTunnel.current

        BackHandler(enabled = mainScreenState.detail != null) { eventTunnel(MainScreenEvent.OnBack) }

        if (mainScreenState.isSettingsOpen) {
            SettingsDialog(
                dnsSettings = userSettings.dnsSettings,
                onDismiss = { eventTunnel(MainScreenEvent.OnShowSettings(false)) },
                onUpdateDns = { mode, v4, v6 -> eventTunnel(MainScreenEvent.OnUpdateDns(mode, v4, v6)) },
                onManualSync = {
                    eventTunnel(MainScreenEvent.OnManualAssetSync)
                    eventTunnel(MainScreenEvent.OnShowSettings(false))
                }
            )
        }

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
                val messageResId = mainScreenState.errorMessage
                val assetSyncState = mainScreenState.assetSyncState
                val isAssetSyncMessage = messageResId == R.string.error_no_assets_vpn ||
                    messageResId == R.string.asset_sync_failed_subtitle
                val assetSyncMessageResId = resolveAssetSyncMessageResId(
                    messageResId,
                    assetSyncState
                )

                if (
                    assetSyncState is AssetSyncState.Syncing &&
                    mainScreenState.vpnState != VpnStateEnum.IDLE
                ) {
                    AssetSyncProgressNotice(assetSyncState.progress)
                }
                if (
                    assetSyncMessageResId != null ||
                    assetSyncState is AssetSyncState.Error
                ) {
                    AssetSyncNotice(
                        messageResId = assetSyncMessageResId,
                        error = if (assetSyncMessageResId == R.string.error_no_assets_vpn) {
                            null
                        } else {
                            (assetSyncState as? AssetSyncState.Error)?.reason
                        },
                        onRetry = { eventTunnel(MainScreenEvent.OnManualAssetSync) }
                    )
                }
                if (messageResId != null && !isAssetSyncMessage) {
                    ErrorMessageNotice(messageResId)
                }

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
                                    title = stringResource(R.string.placeholder_waiting_title),
                                    subtitle = stringResource(R.string.placeholder_waiting_subtitle),
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
                        if (assetSyncState !is AssetSyncState.Syncing) {
                            AppPlaceholder(
                                icon = Icons.Default.Visibility,
                                title = stringResource(R.string.placeholder_idle_title),
                                subtitle = stringResource(R.string.placeholder_idle_subtitle),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            AppPlaceholder(
                                icon = Icons.Default.Downloading,
                                title = stringResource(R.string.asset_sync_loading_title),
                                subtitle = stringResource(R.string.asset_sync_loading_subtitle),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                                additionalContent = {
                                    AssetSyncProgressIndicator(assetSyncState.progress)
                                }
                            )
                        }
                    }

                }
            }
        }
    }
}

private fun resolveAssetSyncMessageResId(
    messageResId: Int?,
    assetSyncState: AssetSyncState
): Int? {
    return when {
        assetSyncState is AssetSyncState.Syncing -> null
        messageResId == R.string.error_no_assets_vpn -> messageResId
        assetSyncState is AssetSyncState.Error -> null
        messageResId == R.string.asset_sync_failed_subtitle -> messageResId
        else -> null
    }
}

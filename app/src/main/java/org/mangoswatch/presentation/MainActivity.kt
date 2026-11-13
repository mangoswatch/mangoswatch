package org.mangoswatch.presentation

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.data.vpn.VpnAction
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.data.vpn.VpnStateEnum
import org.mangoswatch.data.vpn.VpnTrafficManager
import org.mangoswatch.presentation.ui.screens.MainScreen
import org.mangoswatch.presentation.ui.theme.MangosWatchTheme

const val TAG = "[MANGOS_WATCH] - MAIN_ACTIVITY"

class MainActivity : ComponentActivity(), KoinComponent {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MangosWatchTheme {

                val vpnState by inject<VpnState>()

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    Log.d(TAG, "Notification permission granted: $isGranted")
                }

                val vpnLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        startVpnService()
                    } else {
                        Log.d(TAG, "VPN consent denied")
                    }
                }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }

                    vpnState.request.collect {
                        when (it) {
                            VpnAction.START -> {
                                if (vpnState.state == VpnStateEnum.RUNNING) return@collect
                                val intent = VpnService.prepare(this@MainActivity)
                                if (intent != null) {
                                    Log.d(TAG, "VPN permission NOT Granted. Launching permission dialog.")
                                    vpnLauncher.launch(intent)
                                } else {
                                    Log.d(TAG, "VPN permission Granted. Starting VPN service.")
                                    startVpnService()
                                }
                                vpnState.actionRequest = VpnAction.CONSUMED
                            }
                            VpnAction.STOP -> {
                                if (vpnState.state == VpnStateEnum.STOPPED) return@collect
                                stopVpnService()
                                vpnState.actionRequest = VpnAction.CONSUMED
                            }
                            VpnAction.CONSUMED -> {}
                        }
                    }
                }

                MainScreen()

            }
        }
    }

    private fun startVpnService() {
        val vpnIntent = Intent(this, VpnTrafficManager::class.java).apply {
            action = "START_VPN_SERVICE"
        }
        startService(vpnIntent)
    }


    private fun stopVpnService() {
        val vpnIntent = Intent(this, VpnTrafficManager::class.java).apply {
            action = "STOP_VPN_SERVICE"
        }
        startService(vpnIntent)
    }

}


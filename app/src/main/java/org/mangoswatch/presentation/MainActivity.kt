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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel
import org.mangoswatch.data.vpn.VpnStateEnum
import org.mangoswatch.data.vpn.VpnTrafficManager
import org.mangoswatch.presentation.contracts.VpnAction
import org.mangoswatch.presentation.ui.screens.MainScreen
import org.mangoswatch.presentation.ui.theme.MangosWatchTheme
import org.mangoswatch.presentation.viewmodel.MainScreenViewModel

const val TAG = "[MANGOS_WATCH] - MAIN_ACTIVITY"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MangosWatchTheme {

                val context = LocalContext.current
                val mainScreenViewModel: MainScreenViewModel = koinViewModel()
                val state by mainScreenViewModel.state.collectAsState()

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    Log.d(TAG, "Notification permission granted: $isGranted")
                }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
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

                LaunchedEffect(state.vpnAction) {
                    when (state.vpnAction) {
                        VpnAction.START -> {
                            if (state.vpnState == VpnStateEnum.RUNNING) return@LaunchedEffect
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                Log.d(TAG, "VPN permission NOT Granted. Launching permission dialog.")
                                vpnLauncher.launch(intent)
                            } else {
                                Log.d(TAG, "VPN permission Granted. Starting VPN service.")
                                startVpnService()
                            }
                        }
                        VpnAction.STOP -> {
                            if (state.vpnState == VpnStateEnum.STOPPED) return@LaunchedEffect
                            stopVpnService()
                        }
                        null -> {}
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


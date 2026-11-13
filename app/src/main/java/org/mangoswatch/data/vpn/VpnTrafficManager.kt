package org.mangoswatch.data.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresPermission
import org.mangoswatch.data.analyzer.TrafficAnalyzer
import org.mangoswatch.data.proxy.SocksProxyRelay
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.parser.ip.IpRanges
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

/**
 * It's the starting point of interception, it tells the OS:
 * "don't treat the device traffic normally, but let it all be handled by me",
 * then pass it to its file descriptor (the "vpnInterface" variable).
 * The [VpnTrafficManager] has a manager and configuration role.
 * */
@SuppressLint("VpnServicePolicy")
class VpnTrafficManager : VpnService() {

    private val vpnState by inject<VpnState>()
    private val connectivityManager by inject<ConnectivityManager>()
    private lateinit var trafficAnalyzer: TrafficAnalyzer
    private var socksProxyRelay = SocksProxyRelay()
    private var vpnInterface: ParcelFileDescriptor? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        trafficAnalyzer = TrafficAnalyzer(
            amazonRanges = IpRanges.load(this, IpOwner.AMAZON),
            anthropicRanges = IpRanges.load(this, IpOwner.ANTHROPIC),
            appleRanges = IpRanges.load(this, IpOwner.APPLE),
            googleRanges = IpRanges.load(this, IpOwner.GOOGLE),
            metaRanges = IpRanges.load(this, IpOwner.META),
            microsoftRanges = IpRanges.load(this, IpOwner.MICROSOFT),
            netflixRanges = IpRanges.load(this, IpOwner.NETFLIX),
            nvidiaRanges = IpRanges.load(this, IpOwner.NVIDIA),
            openaiRanges = IpRanges.load(this, IpOwner.OPENAI),
            reservedRanges = IpRanges.load(this, IpOwner.RESERVED)
        )
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        intent?.let {
            when (it.action) {
                "STOP_VPN_SERVICE" -> {
                    stopVpn()
                }

                "START_VPN_SERVICE" -> {
                    startVpn()
                }
            }
        }

        // Return the appropriate service restart behavior
        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun startVpn() {
        if (vpnState.state == VpnStateEnum.RUNNING) return
        vpnState.state = VpnStateEnum.LOADING
        Log.d(TAG, "startVpn - Starting VPN...")

        startForeground(1001, createNotification())

        serviceScope.launch {
            try {
                delay(500.milliseconds)         // hacky hacky to show the loader in mainscreen
                establishVpnConnection()
                if (vpnState.state == VpnStateEnum.STOPPED) {
                    Log.d(TAG, "startVpn - establish() returned null (revoked VPN permission?)")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    return@launch
                }
                initSocksProxyRelay()
                initTrafficAnalyzer()
                val fd = trafficAnalyzer.mirrorOutputPfd?.fd ?: return@launch
                startTun2Socks(fd)
            } catch (e: Exception) {
                Log.d(TAG, "startVpn failed: ${e.message}")
                vpnState.state = VpnStateEnum.STOPPED
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    // No LOADING state here, since what is already currently shown (= the app list) should stay visible
    fun stopVpn() {
        if (vpnState.state == VpnStateEnum.STOPPED) return
        Log.d(TAG, "stopVpn - Stopping VPN connection...")
        serviceScope.launch {
            try {
                TProxyService.TProxyStopService()
                socksProxyRelay.closeSocksServer()
                trafficAnalyzer.stopTrafficAnalyzer()
                serviceJob.cancelChildren()
                vpnInterface?.close()
                vpnInterface = null
                vpnState.state = VpnStateEnum.STOPPED
                stopForeground(STOP_FOREGROUND_REMOVE)
                Log.d(TAG, "stopVpn - VPN Connection stopped")
            } catch (e: Exception) {
                Log.d(TAG, "stopVpn failed: ${e.message}")
                vpnState.state = VpnStateEnum.RUNNING
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "mangoswatch_vpn_channel"
        val channel = android.app.NotificationChannel(
            channelId,
            "Mangos Watch VPN Service",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active VPN traffic monitoring service"
        }
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, org.mangoswatch.presentation.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mangos Watch Active")
            .setContentText("Monitoring network traffic and Big Tech endpoints")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: App removed from recents. Stopping VPN.")
        stopVpn()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun isGlobalIpv6(a: InetAddress): Boolean =
        a is Inet6Address &&
                !a.isLinkLocalAddress && !a.isLoopbackAddress && !a.isMulticastAddress && !a.isAnyLocalAddress &&
                (a.address[0].toInt() and 0xFE) != 0xFC   // fc00::/7 (ULA), come il nostro fd00::2

    /** La rete fisica (non VPN) ha un indirizzo IPv6 globale e una rotta di default v6? */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @Suppress("DEPRECATION")
    private fun upstreamHasIpv6(): Boolean {
        val cm = connectivityManager
        return cm.allNetworks.any { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@any false
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@any false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@any false
            val lp = cm.getLinkProperties(n) ?: return@any false
            lp.linkAddresses.any { isGlobalIpv6(it.address) } &&
                    lp.routes.any { it.destination.address is Inet6Address && it.destination.prefixLength == 0 }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun establishVpnConnection() {
        Log.d(TAG, "establishVpnConnection - Preparing VPN connection...")

        val withV6 = upstreamHasIpv6()
        Log.d(TAG, "upstream IPv6: $withV6")
        val builder = Builder()
            .setSession("LocalVPNService")
            .setMtu(8500)
            .addAddress("172.16.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("1.0.0.1")
            .addDisallowedApplication(packageName) // avoid loop
        if (withV6) {
            builder.addAddress("fd00::2", 126)
                .addRoute("::", 0)
                .addDnsServer("2606:4700:4700::1111")
                .addDnsServer("2606:4700:4700::1001")
        }
        vpnInterface = builder.establish()

        if (vpnInterface != null) {
            vpnState.state = VpnStateEnum.RUNNING
        } else {
            vpnState.state = VpnStateEnum.STOPPED
        }
        Log.d(TAG, "establishVpnConnection - VPN Connection established")
    }

    private fun initSocksProxyRelay() {
        socksProxyRelay.startSocksProxyRelay(
            protectSocket = {
                protect(it)
            },
            protectDatagramSocket = {
                protect(it)
            }
        )
    }

    private fun initTrafficAnalyzer() {
        trafficAnalyzer.startTrafficAnalyzer(vpnInterface = vpnInterface!!)
    }

    private fun startTun2Socks(fd: Int) {
        val config = File(cacheDir, "hev.yml").apply {
            writeText(
                """
                        tunnel:
                            mtu: 8500
                            ipv4: 172.16.0.2
                            ipv6: 'fd00::2'
                        socks5:
                            port: 1080
                            address: 127.0.0.1
                            udp: 'udp'
                        misc:
                            log-level: info
                        """.trimIndent()
            )
        }
        val ok = TProxyService.TProxyStartService(
            config.absolutePath,
            fd
        )

        Log.d(TAG, "hev started: $ok")
    }

    companion object {
        const val TAG = "[MANGOS_WATCH] - VPN_TRAFFIC_MANAGER"
    }
}

enum class VpnStateEnum { RUNNING, STOPPED, LOADING, IDLE } // LOADING as an intermediate state when starting the service
enum class VpnAction { START, STOP, CONSUMED }

class VpnState {
    private val _running = MutableStateFlow(VpnStateEnum.IDLE)
    val running: StateFlow<VpnStateEnum> = _running.asStateFlow()

    private val _request = MutableStateFlow(VpnAction.CONSUMED)
    val request: StateFlow<VpnAction> = _request.asStateFlow()

    var state: VpnStateEnum
        get() = _running.value
        set(value) {
            _running.value = value
        }

    var actionRequest: VpnAction
        get() = _request.value
        set(value) {
            _request.value = value
        }
}
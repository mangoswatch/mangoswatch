package org.mangoswatch.data.analyzer

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import org.mangoswatch.data.vpn.VpnState
import org.mangoswatch.domain.parser.ip.IpRanges
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.data.vpn.VpnStateEnum
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * This component works as an "observer" placed between the VPN service and the output to the Internet.
 * Using a mirroring strategy thru socketPair, it doubles the input and output data stream:
 * while a stream passes thru, the other gets fed into the [StreamTrafficProcessor] and analyzed
 * */
class TrafficAnalyzer(
    amazonRanges: IpRanges,
    anthropicRanges: IpRanges,
    appleRanges: IpRanges,
    googleRanges: IpRanges,
    metaRanges: IpRanges,
    microsoftRanges: IpRanges,
    netflixRanges: IpRanges,
    nvidiaRanges: IpRanges,
    openaiRanges: IpRanges,
    reservedRanges: IpRanges,
) : KoinComponent {

    private val vpnState by inject<VpnState>()

    var mirrorInputPfd: ParcelFileDescriptor? = null
    var mirrorOutputPfd: ParcelFileDescriptor? = null

    private val trafficProcessor = StreamTrafficProcessor(
        amazonRanges = amazonRanges,
        anthropicRanges = anthropicRanges,
        appleRanges = appleRanges,
        googleRanges = googleRanges,
        metaRanges = metaRanges,
        microsoftRanges = microsoftRanges,
        netflixRanges = netflixRanges,
        nvidiaRanges = nvidiaRanges,
        openaiRanges = openaiRanges,
        reservedRanges = reservedRanges
    )

    fun startTrafficAnalyzer(vpnInterface: ParcelFileDescriptor) {
        createTapEnds()

        val tunInput = FileInputStream(vpnInterface.fileDescriptor)
        val tunOutput = FileOutputStream(vpnInterface.fileDescriptor)
        val mirrorReader = FileInputStream(mirrorInputPfd!!.fileDescriptor)
        val mirrorWriter = FileOutputStream(mirrorInputPfd!!.fileDescriptor)

        Thread({                                  // app -> hev(proxy)
            val buf = ByteArray(32767)
            val pollFds = arrayOf(StructPollfd().apply {
                fd = vpnInterface.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            })
            try {
                while (vpnState.state == VpnStateEnum.RUNNING) {
                    if (Os.poll(pollFds, 500) <= 0) continue
                    val n = tunInput.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    trafficProcessor.onPacket(buf, n, outbound = true)
                    mirrorWriter.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.d(TAG, "tap out ended: ${e.message}")
            }
        }, "analyzer-outbound (app--->>>proxy)").start()

        Thread({                                  // hev(proxy) -> app
            val buf = ByteArray(32767)
            try {
                while (vpnState.state == VpnStateEnum.RUNNING) {
                    val n = mirrorReader.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    trafficProcessor.onPacket(buf, n, outbound = false)
                    tunOutput.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.d(TAG, "tap in ended: ${e.message}")
            }
        }, "analyzer-inbound (proxy--->>>app)").start()
    }

    fun stopTrafficAnalyzer() {
        trafficProcessor.shutdown()
        mirrorInputPfd?.let {
            runCatching {
                Os.shutdown(
                    it.fileDescriptor,
                    OsConstants.SHUT_RDWR
                )
            }
        }
        runCatching { mirrorInputPfd?.close() }; mirrorInputPfd = null
        runCatching { mirrorOutputPfd?.close() }; mirrorOutputPfd = null
    }

    private fun createTapEnds() {
        val a = FileDescriptor()
        val b = FileDescriptor()
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, a, b)
        mirrorInputPfd = ParcelFileDescriptor.dup(a)
        mirrorOutputPfd = ParcelFileDescriptor.dup(b)
        Os.close(a)
        Os.close(b)
    }

    companion object {
        const val TAG = "[MANGOS_WATCH] - TRAFFIC_ANALYZER"
    }
}

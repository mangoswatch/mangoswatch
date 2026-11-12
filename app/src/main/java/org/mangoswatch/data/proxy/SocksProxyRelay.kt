package org.mangoswatch.data.proxy

import android.util.Log
import org.mangoswatch.data.vpn.VpnState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.data.vpn.VpnStateEnum
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * The final dispatcher, receives the converted streams from the native engine.
 * Its role is to receive the connection request, establish TCP or UDP to the actual internet destination
 * and manage the bidirectional stream of data
 * */
class SocksProxyRelay: KoinComponent {

    private val vpnState by inject<VpnState>()
    var socksServer: ServerSocket? = null

    fun startSocksProxyRelay(
        protectSocket: (socket: Socket) -> Unit,
        protectDatagramSocket: (datagramSocket: DatagramSocket) -> Unit
    ) {
        Log.d(TAG,"startSocksServer - Starting Socket Server...")

        val server = ServerSocket(1080, 50, InetAddress.getByName("127.0.0.1"))
        socksServer = server
        Thread {
            try {
                while (vpnState.state == VpnStateEnum.RUNNING) {
                    val client = server.accept()
                    Thread {
                        dispatchSocksRequest(
                            client,
                            protectSocket,
                            protectDatagramSocket
                        )
                    }.start()
                }
            } catch (e: Exception) {
                Log.d(TAG, "socks server ended: ${e.message}")
            }
        }.start()

        Log.d(TAG,"startSocksServer - Socket Server Started")
    }

    fun closeSocksServer() {
        socksServer?.close(); socksServer = null
    }

    private fun dispatchSocksRequest(
        client: Socket,
        protectSocket: (socket: Socket) -> Unit,
        protectDatagramSocket: (datagramSocket: DatagramSocket) -> Unit
    ) {
        try {
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            input.readByte(); val n = input.readUnsignedByte(); input.skipBytes(n)   // greeting
            output.write(byteArrayOf(5, 0))

            input.skipBytes(1)                          // ver
            val cmd = input.readUnsignedByte()              // 1 = CONNECT, 3 = UDP ASSOCIATE
            input.skipBytes(1)                          // rsv
            val host = readAddr(input)
            val port = input.readUnsignedShort()

            when (cmd) {
                1 -> {
                    val remote = Socket()
                    try {
                        protectSocket(remote)
                        remote.connect(InetSocketAddress(host, port), 10_000)
                    } catch (e: IOException) {
                        val family = if (host.contains(':')) "IPv6" else "IPv4"
                        Log.w(TAG, "connect failed ($family) $host:$port: " +
                                "${e.javaClass.simpleName}: ${e.message}")
                        runCatching { output.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0)) }
                        runCatching { remote.close() }
                        return
                    }
                    remote.use { remote ->
                        output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                        val t = Thread {
                            try {
                                remote.getInputStream().copyTo(output)
                            } catch (_: IOException) { }
                            runCatching { client.shutdownOutput() }
                        }.also { it.start() }

                        var clean = true
                        try {
                            client.getInputStream().copyTo(remote.getOutputStream())
                        } catch (_: IOException) {
                            clean = false
                        }
                        if (clean) runCatching { remote.shutdownOutput() }
                        else runCatching { remote.close() }
                        t.join()
                    }
                }
                3 -> handleUdp(client, protectDatagramSocket)
                else -> output.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))   // command not supported
            }
        } catch (e: Exception) {
            Log.d(TAG, "client error: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun readAddr(inp: DataInputStream): String = when (inp.readUnsignedByte()) {
        1 -> ByteArray(4).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
        3 -> ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) }.decodeToString()
        4 -> ByteArray(16).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
        else -> throw IOException("bad atyp")
    }

    private fun handleUdp(
        control: Socket,
        protectDatagramSocket: (datagramSocket: DatagramSocket) -> Unit
    ) {
        val relay = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val outSock = DatagramSocket().also { protectDatagramSocket(it) }
        val clientAddr = AtomicReference<SocketAddress?>(null)
        val p = relay.localPort
        control.getOutputStream().write(
            byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, (p shr 8).toByte(), p.toByte())
        )

        // client -> internet
        Thread {
            try {
                while (true) {
                    val pkt = DatagramPacket(ByteArray(65535), 65535)
                    relay.receive(pkt)
                    clientAddr.set(pkt.socketAddress)
                    val d = pkt.data
                    if (d[2].toInt() != 0) continue                       // frammentazione non supportata
                    var off = 4
                    val host = when (d[3].toInt()) {
                        1 -> { off = 8;  InetAddress.getByAddress(d.copyOfRange(4, 8)).hostAddress!! }
                        3 -> { val l = d[4].toInt() and 0xFF; off = 5 + l; String(d, 5, l) }
                        4 -> { off = 20; InetAddress.getByAddress(d.copyOfRange(4, 20)).hostAddress!! }
                        else -> continue
                    }
                    val port = ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
                    off += 2
                    val len = pkt.length - off
                    try {
                        outSock.send(DatagramPacket(d, off, len, InetSocketAddress(host, port)))
                    } catch (e: Exception) {
                        if (host.contains(':')) {
                            Log.w(TAG, "UDP IPv6 send to $host:$port failed: " +
                                    "${e.javaClass.simpleName}: ${e.message}")
                        } else {
                            Log.w(TAG, "UDP send to $host:$port failed: ${e.message}")
                        }
                    }                }
            } catch (_: Exception) {}
        }.start()

        // internet -> client
        Thread {
            try {
                while (true) {
                    val pkt = DatagramPacket(ByteArray(65535), 65535)
                    outSock.receive(pkt)
                    val src = pkt.address.address
                    val bos = ByteArrayOutputStream()
                    bos.write(byteArrayOf(0, 0, 0, if (src.size == 4) 1 else 4))
                    bos.write(src)
                    bos.write(pkt.port shr 8); bos.write(pkt.port and 0xFF)
                    bos.write(pkt.data, 0, pkt.length)
                    val data = bos.toByteArray()
                    clientAddr.get()?.let { relay.send(DatagramPacket(data, data.size, it)) }
                }
            } catch (_: Exception) {}
        }.start()

        // l'associazione vive finché vive la connessione TCP di controllo
        try {
            while (control.getInputStream().read() != -1) { }
        } catch (_: Exception) {}
        relay.close(); outSock.close()
    }

    companion object {
        const val TAG = "[MANGOS_WATCH] - SOCKS_PROXY_RELAY"
    }


}
package org.mangoswatch.domain.model

import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

data class StreamModel(
    val protocol: Int,              // Raw IP protocol number from packet header (e.g., 6 = TCP, 17 = UDP)
    val srcIpStr: String,
    val srcPort: Int,
    val dstIpStr: String,
    val dstPort: Int,
    val ipOwner: IpOwner,
    val firstSeen: Long,
    val uid: Int = Process.INVALID_UID,
    val pkg: String? = null,
    val bytesUp: Long = 0L,         // scritto solo da tap-out
    val bytesDown: Long = 0L,       // scritto solo da tap-in
    val lastSeen: Long = firstSeen,
    val finSeen: Boolean = false,
    val uidAttempts: Int = 0,
    val lastUidAttempt: Long = 0L,
    val sni: String? = null,        // from ClientHello TLS (TCP)
    val dnsQuery: String? = null,   // from QNAME DNS (UDP/53 o TCP/53)
    val tlsProbed: Boolean = false, // already tried reading SNI
    val isTls: Boolean = false,     // whether it's verified TLS/QUIC connection
    val ended: AtomicBoolean = AtomicBoolean(false),
) {
    fun hostname(): String = sni ?: dnsQuery ?: "?" // SNI preferred, then QNAME DNS, otherwise "?"

    /**
     * High-level semantic / application protocol classification (QUIC, TLS, DNS, TCP, UDP, OTHER)
     * derived dynamically from raw protocol number, port, and TLS inspection state.
     */
    val semanticProtocol: StreamProtocol
        get() = when (protocol) {
            17 -> when {
                isTls -> StreamProtocol.QUIC
                dstPort == 53 -> StreamProtocol.DNS
                else -> StreamProtocol.UDP
            }
            6 -> when {
                isTls -> StreamProtocol.TLS
                dstPort == 53 -> StreamProtocol.DNS
                else -> StreamProtocol.TCP
            }
            else -> StreamProtocol.OTHER
        }

    companion object {
        fun key(protocol: Int, localPort: Int, remoteIp: String, remotePort: Int): String =
            "$protocol|$localPort|$remoteIp|$remotePort"
    }

}

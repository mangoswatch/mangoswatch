package org.mangoswatch.domain.parser.ip

import org.mangoswatch.domain.model.Packet
import org.mangoswatch.domain.parser.ip.IpHelpers.ipStrV6
import org.mangoswatch.domain.parser.ip.IpHelpers.u16
import org.mangoswatch.domain.parser.ip.IpHelpers.u8

class IpV6Parser : IpParser() {

    fun parseV6Packet(b: ByteArray, n: Int, outbound: Boolean): Packet? {
        if (n < 40) return null
        val nextHdr = u8(b, 6)
        // Supportiamo solo TCP/UDP diretti (senza extension header).
        if (nextHdr != 6 && nextHdr != 17) return null

        val srcIp = ipStrV6(b, 8)
        val dstIp = ipStrV6(b, 24)

        if (n < 40 + 8) return null
        val srcPort = u16(b, 40)
        val dstPort = u16(b, 42)

        val tcpHdrLen = if (nextHdr == 6) {
            if (n < 40 + 20) return null
            val hlen = ((b[40 + 12].toInt() and 0xF0) ushr 4) * 4
            if (hlen < 20) return null
            hlen
        } else 8
        if (n < 40 + tcpHdrLen) return null
        val payloadOff = 40 + tcpHdrLen
        val payloadLen = n - payloadOff
        val flags = if (nextHdr == 6) u8(b, 40 + 13) else 0

        val remoteOff = if (outbound) 24 else 8
        val ipOwner = identifyOwner(b, remoteOff)

        return Packet(
            protocol = nextHdr,
            packetSrcIp = srcIp,
            packetSrcPort = srcPort,
            packetDstIp = dstIp,
            packetDstPort = dstPort,
            ipOwner = ipOwner,
            flags = flags,
            b = b,
            payloadOff = payloadOff,
            payloadLen = payloadLen,
            totalLen = n,
            outbound = outbound
        )

    }

}

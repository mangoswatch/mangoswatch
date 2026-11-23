package org.mangoswatch.domain.parser.ip

import org.mangoswatch.domain.model.Packet
import org.mangoswatch.domain.parser.ip.IpHelpers.ipStrV4
import org.mangoswatch.domain.parser.ip.IpHelpers.u16
import org.mangoswatch.domain.parser.ip.IpHelpers.u32
import org.mangoswatch.domain.parser.ip.IpHelpers.u8

class IpV4Parser {

    fun parseV4Packet(b: ByteArray, n: Int, outbound: Boolean): Packet? {
        if (n < 20) return null
        val ihl = (b[0].toInt() and 0x0F) * 4
        if (ihl < 20 || n < ihl + 8) return null
        val protocol = u8(b, 9)
        if (protocol != 6 && protocol != 17) return null
        if ((u16(b, 6) and 0x1FFF) != 0) return null         // non-initial fragment

        val srcIpL = u32(b, 12)
        val dstIpL = u32(b, 16)
        val srcIp  = ipStrV4(srcIpL)
        val dstIp  = ipStrV4(dstIpL)
        val srcPort = u16(b, ihl)
        val dstPort = u16(b, ihl + 2)

        val tcpHdrLen = if (protocol == 6) {
            if (n < ihl + 20) return null
            val hlen = ((b[ihl + 12].toInt() and 0xF0) ushr 4) * 4
            if (hlen < 20) return null
            hlen
        } else 8
        if (n < ihl + tcpHdrLen) return null
        val payloadOff = ihl + tcpHdrLen
        val payloadLen = n - payloadOff
        val flags = if (protocol == 6) u8(b, ihl + 13) else 0

        return Packet(
            protocol = protocol,
            packetSrcIp = srcIp,
            packetSrcPort = srcPort,
            packetDstIp = dstIp,
            packetDstPort = dstPort,
            flags = flags,
            b = b,
            payloadOff = payloadOff,
            payloadLen = payloadLen,
            totalLen = n,
            outbound = outbound
        )

    }

}

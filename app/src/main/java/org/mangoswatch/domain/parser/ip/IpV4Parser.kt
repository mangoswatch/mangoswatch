package org.mangoswatch.domain.parser.ip

import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.Packet
import org.mangoswatch.domain.parser.ip.IpHelpers.ipStrV4
import org.mangoswatch.domain.parser.ip.IpHelpers.u16
import org.mangoswatch.domain.parser.ip.IpHelpers.u32
import org.mangoswatch.domain.parser.ip.IpHelpers.u8

class IpV4Parser(
    private val amazonRanges: IpRanges,
    private val anthropicRanges: IpRanges,
    private val appleRanges: IpRanges,
    private val googleRanges: IpRanges,
    private val metaRanges: IpRanges,
    private val microsoftRanges: IpRanges,
    private val netflixRanges: IpRanges,
    private val nvidiaRanges: IpRanges,
    private val openaiRanges: IpRanges,
    private val reservedRanges: IpRanges,
) {

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

        val remoteIpLong  = if (outbound) dstIpL else srcIpL
        val ipOwner = identifyV4Owner(remoteIpLong)

        return Packet(
            protocol = protocol,
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

    private fun identifyV4Owner(ipL: Long): IpOwner = when {
        amazonRanges.containsV4(ipL)        -> IpOwner.AMAZON
        anthropicRanges.containsV4(ipL)     -> IpOwner.ANTHROPIC
        appleRanges.containsV4(ipL)         -> IpOwner.APPLE
        googleRanges.containsV4(ipL)        -> IpOwner.GOOGLE
        metaRanges.containsV4(ipL)          -> IpOwner.META
        microsoftRanges.containsV4(ipL)     -> IpOwner.MICROSOFT
        netflixRanges.containsV4(ipL)       -> IpOwner.NETFLIX
        nvidiaRanges.containsV4(ipL)        -> IpOwner.NVIDIA
        openaiRanges.containsV4(ipL)        -> IpOwner.OPENAI
        reservedRanges.containsV4(ipL)      -> IpOwner.RESERVED
        else                                -> IpOwner.OTHER
    }

}
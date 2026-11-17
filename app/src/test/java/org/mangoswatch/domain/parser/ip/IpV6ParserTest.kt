package org.mangoswatch.domain.parser.ip

import org.junit.Assert.*
import org.junit.Test
import org.mangoswatch.domain.model.IpOwner

class IpV6ParserTest {

    private val parser = IpV6Parser(
        amazonRanges = IpRanges.empty(),
        anthropicRanges = IpRanges.empty(),
        appleRanges = IpRanges.empty(),
        googleRanges = IpRanges.empty(),
        metaRanges = IpRanges.empty(),
        microsoftRanges = IpRanges.empty(),
        netflixRanges = IpRanges.empty(),
        nvidiaRanges = IpRanges.empty(),
        openaiRanges = IpRanges.empty(),
        reservedRanges = IpRanges.empty()
    )

    @Test
    fun testParseV6PacketTruncated() {
        val buf = ByteArray(30)
        assertNull(parser.parseV6Packet(buf, 30, true))
    }

    @Test
    fun testParseV6PacketUnsupportedHeader() {
        val buf = ByteArray(50)
        buf[0] = 0x60 // IPv6 version 6
        buf[6] = 58 // ICMPv6 (next header 58)
        assertNull(parser.parseV6Packet(buf, 50, true))
    }

    @Test
    fun testParseV6PacketValidUdp() {
        val buf = ByteArray(60)
        buf[0] = 0x60 // Version 6
        buf[6] = 17   // Next header: UDP (17)

        // Source IPv6: fd00::1 (16 bytes starting at offset 8)
        buf[8] = 0xfd.toByte()
        buf[23] = 0x01.toByte()

        // Destination IPv6: fd00::2 (16 bytes starting at offset 24)
        buf[24] = 0xfd.toByte()
        buf[39] = 0x02.toByte()

        // UDP Header at offset 40: srcPort 5000 (0x1388), dstPort 53 (0x0035)
        buf[40] = 0x13
        buf[41] = 0x88.toByte()
        buf[42] = 0x00
        buf[43] = 0x35

        val pkt = parser.parseV6Packet(buf, 60, true)
        assertNotNull(pkt)
        assertEquals(17, pkt?.protocol)
        assertEquals(5000, pkt?.packetSrcPort)
        assertEquals(53, pkt?.packetDstPort)
        assertEquals(IpOwner.OTHER, pkt?.ipOwner)
        assertTrue(pkt?.outbound == true)
        assertEquals(12, pkt?.payloadLen) // 60 - 48
    }

    @Test
    fun testParseV6PacketValidTcp() {
        val buf = ByteArray(60)
        buf[0] = 0x60 // Version 6
        buf[6] = 6    // Next header: TCP (6)

        // Source IPv6 at offset 8
        buf[8] = 0x20
        buf[9] = 0x01

        // Destination IPv6 at offset 24
        buf[24] = 0x20
        buf[25] = 0x01

        // TCP Header at offset 40: srcPort 4000 (0x0FA0), dstPort 443 (0x01BB)
        buf[40] = 0x0F
        buf[41] = 0xA0.toByte()
        buf[42] = 0x01
        buf[43] = 0xBB.toByte()
        // Data offset (hlen = 20 bytes -> 5 * 4 -> upper 4 bits = 5)
        buf[52] = 0x50.toByte()
        buf[53] = 0x02 // TCP SYN flag

        val pkt = parser.parseV6Packet(buf, 60, outbound = false)
        assertNotNull(pkt)
        assertEquals(6, pkt?.protocol)
        assertEquals(4000, pkt?.packetSrcPort)
        assertEquals(443, pkt?.packetDstPort)
        assertEquals(2, pkt?.flags)
        assertFalse(pkt?.outbound == true)
    }
}

package org.mangoswatch.domain.parser.ip

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.repository.AssetRepository
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
class IpV4ParserTest {

    private lateinit var assetRepository: AssetRepository
    private val parser = IpV4Parser()

    @Before
    fun setUp() {
        assetRepository = mock(AssetRepository::class.java)
        whenever(assetRepository.getFileFromAssetsCache(any())).thenReturn("".byteInputStream())
        startKoin {
            modules(
                module {
                    single { assetRepository }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testParseV4PacketTooShortReturnsNull() {
        val packet = byteArrayOf(0x45, 0x00)
        assertNull(parser.parseV4Packet(packet, packet.size, true))
    }

    @Test
    fun testParseV4PacketInvalidProtocolReturnsNull() {
        val buf = ByteArray(28)
        buf[0] = 0x45 // Version 4, IHL 5 (20 bytes)
        buf[9] = 1    // Protocol ICMP (1) - not TCP/UDP
        assertNull(parser.parseV4Packet(buf, buf.size, true))
    }

    @Test
    fun testParseV4PacketFragmentedReturnsNull() {
        val buf = ByteArray(28)
        buf[0] = 0x45
        buf[6] = 0x01 // Fragment offset non-zero
        buf[9] = 17   // UDP
        assertNull(parser.parseV4Packet(buf, buf.size, true))
    }

    @Test
    fun testParseV4PacketValidUdp() {
        val buf = ByteArray(28)
        buf[0] = 0x45 // Version 4, IHL 5 (20 bytes)
        buf[9] = 17   // Protocol UDP

        // Src IP: 192.168.1.10
        buf[12] = 192.toByte(); buf[13] = 168.toByte(); buf[14] = 1; buf[15] = 10
        // Dst IP: 8.8.8.8
        buf[16] = 8; buf[17] = 8; buf[18] = 8; buf[19] = 8

        // UDP Header at offset 20: SrcPort 12345 (0x3039), DstPort 53 (0x0035)
        buf[20] = 0x30; buf[21] = 0x39
        buf[22] = 0x00; buf[23] = 0x35

        val pkt = parser.parseV4Packet(buf, buf.size, outbound = true)
        assertNotNull(pkt)
        assertEquals(17, pkt?.protocol)
        assertEquals("192.168.1.10", pkt?.packetSrcIp)
        assertEquals(12345, pkt?.packetSrcPort)
        assertEquals("8.8.8.8", pkt?.packetDstIp)
        assertEquals(53, pkt?.packetDstPort)
        assertEquals(IpOwner.OTHER, pkt?.ipOwner)
        assertTrue(pkt?.outbound == true)
    }

    @Test
    fun testParseV4PacketValidTcp() {
        val buf = ByteArray(40)
        buf[0] = 0x45 // Version 4, IHL 5
        buf[9] = 6    // Protocol TCP

        // Src IP: 10.0.0.1
        buf[12] = 10; buf[13] = 0; buf[14] = 0; buf[15] = 1
        // Dst IP: 1.1.1.1
        buf[16] = 1; buf[17] = 1; buf[18] = 1; buf[19] = 1

        // TCP Header at offset 20: SrcPort 54321 (0xD431), DstPort 443 (0x01BB)
        buf[20] = 0xD4.toByte(); buf[21] = 0x31
        buf[22] = 0x01; buf[23] = 0xBB.toByte()

        // TCP Header Len at offset 32 (20 + 12): hlen = 20 bytes (0x50)
        buf[32] = 0x50
        buf[33] = 0x18 // TCP PSH+ACK flags

        val pkt = parser.parseV4Packet(buf, buf.size, outbound = false)
        assertNotNull(pkt)
        assertEquals(6, pkt?.protocol)
        assertEquals("10.0.0.1", pkt?.packetSrcIp)
        assertEquals(54321, pkt?.packetSrcPort)
        assertEquals("1.1.1.1", pkt?.packetDstIp)
        assertEquals(443, pkt?.packetDstPort)
        assertEquals(0x18, pkt?.flags)
        assertFalse(pkt?.outbound == true)
    }
}

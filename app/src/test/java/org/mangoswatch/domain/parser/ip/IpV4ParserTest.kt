package org.mangoswatch.domain.parser.ip

import org.junit.Assert.*
import org.junit.Test

class IpV4ParserTest {

    @Test
    fun testParseV4PacketTooShortReturnsNull() {
        val parser = IpV4Parser(
            IpRanges.empty(), IpRanges.empty(), IpRanges.empty(), IpRanges.empty(),
            IpRanges.empty(), IpRanges.empty(), IpRanges.empty(), IpRanges.empty(),
            IpRanges.empty(), IpRanges.empty()
        )
        val packet = byteArrayOf(0x45, 0x00)
        val result = parser.parseV4Packet(packet, packet.size, true)
        assertNull(result)
    }
}

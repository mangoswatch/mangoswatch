package org.mangoswatch.domain.parser

import org.junit.Assert.*
import org.junit.Test

class DnsQueryParserTest {

    @Test
    fun testExtractDnsQueryValid() {
        val parser = DnsQueryParser()
        val packet = byteArrayOf(
            0x12, 0x34,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            3, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            6, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0,
            0x00, 0x01,
            0x00, 0x01
        )

        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertEquals("www.google.com", query)
    }

    @Test
    fun testExtractDnsQueryResponseReturnsNull() {
        val parser = DnsQueryParser()
        val packet = byteArrayOf(
            0x12, 0x34,
            (0x81).toByte(), (0x80).toByte(),
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }

    @Test
    fun testExtractDnsQueryTooShortReturnsNull() {
        val parser = DnsQueryParser()
        val packet = byteArrayOf(0x12, 0x34)
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }
}

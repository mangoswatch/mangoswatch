package org.mangoswatch.domain.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsQueryParserTest {

    private val parser = DnsQueryParser()

    private fun createDnsQueryPacket(qname: String, isResponse: Boolean = false, qdCount: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        // Transaction ID (2 bytes)
        out.write(byteArrayOf(0x12, 0x34))
        // Flags (2 bytes): 0x0100 for standard query, 0x8180 for response
        if (isResponse) {
            out.write(byteArrayOf(0x81.toByte(), 0x80.toByte()))
        } else {
            out.write(byteArrayOf(0x01, 0x00))
        }
        // Questions count (2 bytes)
        out.write(byteArrayOf((qdCount shr 8).toByte(), qdCount.toByte()))
        // Answer, Authority, Additional count (6 bytes)
        out.write(ByteArray(6))

        if (qname.isNotEmpty()) {
            val parts = qname.split(".")
            for (part in parts) {
                val bytes = part.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0) // Null terminator label
            // QTYPE (A) + QCLASS (IN)
            out.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        }

        return out.toByteArray()
    }

    @Test
    fun testExtractDnsQueryValidStandard() {
        val packet = createDnsQueryPacket("www.google.com")
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertEquals("www.google.com", query)
    }

    @Test
    fun testExtractDnsQueryMultiSubdomain() {
        val packet = createDnsQueryPacket("api.sub.domain.openai.com")
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertEquals("api.sub.domain.openai.com", query)
    }

    @Test
    fun testExtractDnsQueryResponseReturnsNull() {
        val packet = createDnsQueryPacket("www.google.com", isResponse = true)
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }

    @Test
    fun testExtractDnsQueryTooShortReturnsNull() {
        val packet = byteArrayOf(0x12, 0x34, 0x01)
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }

    @Test
    fun testExtractDnsQueryZeroQuestionsReturnsNull() {
        val packet = createDnsQueryPacket("www.google.com", qdCount = 0)
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }

    @Test
    fun testExtractDnsQueryLabelTooLongReturnsNull() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        out.write(64) // Invalid label length > 63
        out.write(ByteArray(64) { 'a'.code.toByte() })
        out.write(0)

        val packet = out.toByteArray()
        val query = parser.extractDnsQuery(packet, 0, packet.size)
        assertNull(query)
    }
}

package org.mangoswatch.domain.parser.handshake.tls

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class TlsParserTest {

    private fun createClientHelloRecord(sniHost: String?): ByteArray {
        val hsBody = ByteArrayOutputStream()
        // Legacy Client Version (0x03, 0x03 = TLS 1.2)
        hsBody.write(byteArrayOf(0x03, 0x03))
        // Random (32 bytes)
        hsBody.write(ByteArray(32) { it.toByte() })
        // Session ID Length (0)
        hsBody.write(0)
        // Cipher Suites Length (2) + Cipher Suite (0x002F)
        hsBody.write(byteArrayOf(0x00, 0x02, 0x00, 0x2F.toByte()))
        // Compression Methods Length (1) + Compression Method (0x00)
        hsBody.write(byteArrayOf(0x01, 0x00))

        if (sniHost != null) {
            val sniBytes = sniHost.toByteArray(Charsets.US_ASCII)
            val sniListLen = sniBytes.size + 3
            val extDataLen = sniListLen + 2

            val exts = ByteArrayOutputStream()
            // Extension 0x0000 (server_name)
            exts.write(byteArrayOf(0x00, 0x00))
            exts.write(byteArrayOf((extDataLen shr 8).toByte(), extDataLen.toByte()))
            exts.write(byteArrayOf((sniListLen shr 8).toByte(), sniListLen.toByte()))
            exts.write(0) // name_type = host_name
            exts.write(byteArrayOf((sniBytes.size shr 8).toByte(), sniBytes.size.toByte()))
            exts.write(sniBytes)

            val extsBytes = exts.toByteArray()
            hsBody.write(byteArrayOf((extsBytes.size shr 8).toByte(), extsBytes.size.toByte()))
            hsBody.write(extsBytes)
        }

        val hsBodyBytes = hsBody.toByteArray()
        val hsHeader = ByteArrayOutputStream()
        hsHeader.write(0x01) // Handshake type = ClientHello
        val hsLen = hsBodyBytes.size
        hsHeader.write(byteArrayOf((hsLen shr 16).toByte(), (hsLen shr 8).toByte(), hsLen.toByte()))
        hsHeader.write(hsBodyBytes)

        val handshakeBytes = hsHeader.toByteArray()

        // TLS Record Header: 0x16 (Handshake), 0x03 0x01 (TLS 1.0 legacy version), length
        val record = ByteArrayOutputStream()
        record.write(0x16)
        record.write(byteArrayOf(0x03, 0x01))
        val recLen = handshakeBytes.size
        record.write(byteArrayOf((recLen shr 8).toByte(), recLen.toByte()))
        record.write(handshakeBytes)

        return record.toByteArray()
    }

    @Test
    fun testParseClientHelloEmptyReturnsNeedMore() {
        val result = parseClientHello(byteArrayOf(), 0)
        assertTrue(result is NeedMore)
    }

    @Test
    fun testParseClientHelloNotTlsReturnsNotTls() {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        val result = parseClientHello(data, data.size)
        assertTrue(result is NotTls)
    }

    @Test
    fun testParseClientHelloValidSni() {
        val data = createClientHelloRecord("example.com")
        val result = parseClientHello(data, data.size)
        assertTrue(result is ClientHello)
        assertEquals("example.com", (result as ClientHello).sni)
    }

    @Test
    fun testParseClientHelloSubdomainSni() {
        val data = createClientHelloRecord("api.sub.domain.org")
        val result = parseClientHello(data, data.size)
        assertTrue(result is ClientHello)
        assertEquals("api.sub.domain.org", (result as ClientHello).sni)
    }

    @Test
    fun testParseClientHelloPartialRecordReturnsNeedMore() {
        val fullData = createClientHelloRecord("example.com")
        val partialData = fullData.copyOf(fullData.size - 5)
        val result = parseClientHello(partialData, partialData.size)
        assertTrue(result is NeedMore)
    }

    @Test
    fun testParseClientHelloNoSni() {
        val data = createClientHelloRecord(null)
        val result = parseClientHello(data, data.size)
        assertTrue(result is ClientHello)
        assertNull((result as ClientHello).sni)
    }
}

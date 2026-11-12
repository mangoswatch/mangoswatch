package org.mangoswatch.domain.parser.handshake.tls

import org.junit.Assert.*
import org.junit.Test

class TlsParserTest {

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
}

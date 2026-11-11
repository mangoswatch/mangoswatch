package org.mangoswatch.domain.parser

import org.mangoswatch.domain.parser.ip.IpHelpers.u16
import org.mangoswatch.domain.parser.ip.IpHelpers.u8

class DnsQueryParser {

    /** Extracts the query domain name (QNAME) from a DNS packet over UDP/TCP. */
    fun extractDnsQuery(b: ByteArray, off: Int, len: Int): String? {
        if (len < 12) return null
        if ((u8(b, off + 2) and 0x80) != 0) return null                // response, not query
        val qdCount = u16(b, off + 4)
        if (qdCount < 1) return null

        val end = off + len
        var p = off + 12
        val sb = StringBuilder()
        while (p < end) {
            val l = u8(b, p)
            if (l == 0) break
            if ((l and 0xC0) == 0xC0) break                            // pointer (not expected in query)
            if (l > 63) return null
            if (p + 1 + l > end) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(b, p + 1, l))
            p += 1 + l
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }
}

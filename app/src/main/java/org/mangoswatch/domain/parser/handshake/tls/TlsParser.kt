package org.mangoswatch.domain.parser.handshake.tls

import java.io.ByteArrayOutputStream

private class Truncated : Exception()

private class Reader(val b: ByteArray, var p: Int, val end: Int) {
    fun u8(): Int { if (p + 1 > end) throw Truncated(); return b[p++].toInt() and 0xFF }
    fun u16(): Int = (u8() shl 8) or u8()
    fun u24(): Int = (u8() shl 16) or u16()
    fun skip(n: Int) { if (n < 0 || p + n > end) throw Truncated(); p += n }
    /** Sub-reader over the next n bytes; advances this reader past them. */
    fun sub(n: Int): Reader { if (n < 0 || p + n > end) throw Truncated(); val r = Reader(b, p, p + n); p += n; return r }
    fun bytes(n: Int): ByteArray { if (n < 0 || p + n > end) throw Truncated(); val r = b.copyOfRange(p, p + n); p += n; return r }
    fun hasMore() = p < end
}

private const val MAX_HELLO = 64 * 1024

/**
 * Parse the first bytes of a client->server TCP stream. Call again with the grown buffer on NeedMore.
 * Handles a ClientHello split over several TLS records.
 */
fun parseClientHello(buf: ByteArray, len: Int): HelloResult {
    if (len == 0) return NeedMore
    // Reassemble handshake bytes from consecutive handshake records.
    val hs = ByteArrayOutputStream()
    var p = 0
    var needed = -1 // total handshake length incl. 4-byte header, known once the header is in
    while (needed < 0 || hs.size() < needed) {
        if (len - p < 5) return if (looksLikeTls(buf, len, p)) NeedMore else NotTls
        if (buf[p].toInt() != 0x16 || buf[p + 1].toInt() != 0x03) return NotTls
        val recLen = ((buf[p + 3].toInt() and 0xFF) shl 8) or (buf[p + 4].toInt() and 0xFF)
        if (recLen == 0 || recLen > 16384 + 256) return NotTls
        if (len - p - 5 < recLen) return NeedMore
        hs.write(buf, p + 5, recLen)
        p += 5 + recLen
        if (needed < 0 && hs.size() >= 4) {
            val h = hs.toByteArray()
            if (h[0].toInt() != 0x01) return NotTls
            needed = 4 + (((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF))
            if (needed > MAX_HELLO) return NotTls
        }
        if (hs.size() > MAX_HELLO) return NotTls
    }
    val body = hs.toByteArray()
    return try {
        parseBody(Reader(body, 4, needed))
    } catch (e: Truncated) {
        NotTls
    }
}

private fun looksLikeTls(buf: ByteArray, len: Int, p: Int): Boolean {
    if (len - p >= 1 && buf[p].toInt() != 0x16) return false
    if (len - p >= 2 && buf[p + 1].toInt() != 0x03) return false
    return true
}

private fun parseBody(r: Reader): HelloResult {
    r.u16() // legacy version
    r.skip(32) // random
    r.skip(r.u8()) // session id
    r.skip(r.u16()) // ciphers
    r.skip(r.u8()) // compression methods

    var sni: String? = null

    if (r.hasMore()) {
        val exts = r.sub(r.u16())
        while (exts.hasMore()) {
            val type = exts.u16()
            val data = exts.sub(exts.u16())
            if (type == 0x0000) { // server_name
                val list = data.sub(data.u16())
                while (list.hasMore()) {
                    val nameType = list.u8()
                    val name = list.bytes(list.u16())
                    if (nameType == 0 && sni == null) sni = sanitizeHost(name)
                }
            }
        }
    }
    return ClientHello(sni)
}

/** Lowercase DNS-ish name or null. Rejects control chars / odd bytes so it is safe to log and display. */
private fun sanitizeHost(raw: ByteArray): String? {
    if (raw.isEmpty() || raw.size > 253) return null
    val s = String(raw, Charsets.ISO_8859_1).lowercase()
    return if (s.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) s else null
}

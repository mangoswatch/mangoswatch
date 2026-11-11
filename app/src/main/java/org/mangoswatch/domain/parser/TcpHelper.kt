package org.mangoswatch.domain.parser

/**
 * TCP sequence number of a raw IP packet (IPv4, or IPv6 without extension headers), or null when the
 * TCP header is not where expected. The ports the caller already parsed are used as a sanity check, so
 * an IPv6 packet with extension headers yields null instead of garbage.
 */
fun tcpSeq(b: ByteArray, payloadOff: Int, srcPort: Int, dstPort: Int): Int? {
    if (b.isEmpty()) return null
    val tcpOff = if (((b[0].toInt() shr 4) and 0xF) == 4) (b[0].toInt() and 0x0F) * 4 else 40
    if (tcpOff < 20 || tcpOff + 20 > payloadOff || payloadOff > b.size) return null
    fun u8(i: Int) = b[i].toInt() and 0xFF
    if (((u8(tcpOff) shl 8) or u8(tcpOff + 1)) != srcPort) return null
    if (((u8(tcpOff + 2) shl 8) or u8(tcpOff + 3)) != dstPort) return null
    return (u8(tcpOff + 4) shl 24) or (u8(tcpOff + 5) shl 16) or (u8(tcpOff + 6) shl 8) or u8(tcpOff + 7)
}

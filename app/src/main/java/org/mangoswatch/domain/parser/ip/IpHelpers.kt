package org.mangoswatch.domain.parser.ip

object IpHelpers {

    fun u8(b: ByteArray, o: Int): Int = b[o].toInt() and 0xFF
    fun u16(b: ByteArray, o: Int): Int = (u8(b, o) shl 8) or u8(b, o + 1)
    fun u32(b: ByteArray, o: Int): Long =
        (u8(b, o).toLong() shl 24) or (u8(b, o + 1).toLong() shl 16) or
                (u8(b, o + 2).toLong() shl 8) or u8(b, o + 3).toLong()

    fun ipStrV4(ip: Long): String =
        "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"

    /** 16 byte IPv6 → extended 8×hextet form (no compression: stable key). */
    fun ipStrV6(b: ByteArray, off: Int): String {
        val sb = StringBuilder(39)
        for (i in 0 until 16 step 2) {
            if (i > 0) sb.append(':')
            val v = (u8(b, off + i) shl 8) or u8(b, off + i + 1)
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

}

package org.mangoswatch.domain.parser.handshake.quic

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cheap pre-filter, safe to run on every outbound UDP payload: a client Initial datagram is at least
 * 1200 bytes and starts with a long header (form+fixed bits), type Initial, version 1.
 */
fun looksLikeQuicInitial(b: ByteArray, off: Int, len: Int): Boolean =
    len >= 1200 && off >= 0 && off + 5 <= b.size &&
            (b[off].toInt() and 0xF0) == 0xC0 &&
            b[off + 1].toInt() == 0 && b[off + 2].toInt() == 0 && b[off + 3].toInt() == 0 && b[off + 4].toInt() == 1

internal class QuicTruncated : Exception()

internal class QuicCursor(val b: ByteArray, var p: Int, val end: Int) {
    fun u8(): Int {
        if (p >= end) throw QuicTruncated()
        return b[p++].toInt() and 0xFF
    }

    fun varint(): Long {
        val first = u8()
        var v = (first and 0x3F).toLong()
        repeat((1 shl (first ushr 6)) - 1) { v = (v shl 8) or u8().toLong() }
        return v
    }

    fun skip(n: Long) {
        if (n < 0 || p + n > end) throw QuicTruncated()
        p += n.toInt()
    }
}

internal class Opened(val frames: ByteArray, val packetEnd: Int)

/** Remove header + payload protection of one client Initial packet. Null if it is not one, or does not authenticate. */
internal fun openQuicPacket(
    d: ByteArray,
    off: Int,
    end: Int,
    keysDcid: ByteArray?,
    keys: InitialKeys?,
    updateKeys: (ByteArray, InitialKeys) -> Unit
): Opened? {
    try {
        val c = QuicCursor(d, off, end)
        val first = c.u8()
        if ((first and 0xF0) != 0xC0) return null // long header, fixed bit, type Initial
        val version = (c.u8() shl 24) or (c.u8() shl 16) or (c.u8() shl 8) or c.u8()
        if (version != 1) return null
        val dcidLen = c.u8()
        if (dcidLen > 20) return null
        val dcidStart = c.p
        c.skip(dcidLen.toLong())
        val dcid = d.copyOfRange(dcidStart, dcidStart + dcidLen)
        val scidLen = c.u8()
        if (scidLen > 20) return null
        c.skip(scidLen.toLong())
        c.skip(c.varint()) // token
        val length = c.varint()
        val pnOff = c.p
        if (length < 20 || pnOff + length > end) return null
        val pktEnd = pnOff + length.toInt()

        val k = if (keysDcid != null && keysDcid.contentEquals(dcid)) keys!! else deriveClientInitialKeys(
            dcid
        ).also {
            updateKeys(dcid, it)
        }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.hp, "AES"))
        val mask = ecb.doFinal(d, pnOff + 4, 16) // sample = 16 bytes starting 4 bytes after the pn field start

        val firstU = first xor (mask[0].toInt() and 0x0F)
        val pnLen = (firstU and 0x03) + 1
        if (pnOff + pnLen > pktEnd - 16) return null
        val header = d.copyOfRange(off, pnOff + pnLen)
        header[0] = firstU.toByte()
        val nonce = k.iv.copyOf()
        for (i in 0 until pnLen) {
            val pb = (d[pnOff + i].toInt() xor mask[1 + i].toInt()) and 0xFF
            header[pnOff - off + i] = pb.toByte()
            nonce[12 - pnLen + i] = (nonce[12 - pnLen + i].toInt() xor pb).toByte()
        }
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.DECRYPT_MODE, SecretKeySpec(k.key, "AES"), GCMParameterSpec(128, nonce))
        gcm.updateAAD(header)
        val plain = gcm.doFinal(d, pnOff + pnLen, pktEnd - (pnOff + pnLen))
        return Opened(plain, pktEnd)
    } catch (_: QuicTruncated) {
        return null
    } catch (_: GeneralSecurityException) {
        return null // tag mismatch: not for these keys / corrupted
    }
}

package org.mangoswatch.domain.parser.handshake.quic

import org.mangoswatch.domain.parser.handshake.tls.HelloResult
import org.mangoswatch.domain.parser.handshake.tls.NeedMore
import org.mangoswatch.domain.parser.handshake.tls.NotTls
import org.mangoswatch.domain.parser.handshake.tls.parseClientHello
import java.io.ByteArrayOutputStream

/**
 * Collects the CRYPTO data of a client's Initial flight, across datagrams (a post-quantum ClientHello does not
 * fit in one), until parseClientHello can decide. Same contract as ClientHelloAssembler: NeedMore while
 * pending, then ClientHello or NotTls exactly once, then null forever. Not thread-safe; one per UDP flow.
 */
class QuicInitialAssembler {
    private val chunks = ArrayList<Pair<Int, ByteArray>>() // (CRYPTO offset, data)
    private var datagrams = 0
    private var done = false
    private var keysDcid: ByteArray? = null
    private var keys: InitialKeys? = null

    fun feed(d: ByteArray, off: Int, len: Int): HelloResult? {
        if (done) return null
        if (++datagrams > MAX_DATAGRAMS) return finish(NotTls)
        var p = off
        val end = off + len
        var sawInitial = false
        while (p < end) { // datagrams may coalesce several packets
            val pkt = openQuicPacket(d, p, end, keysDcid, keys) { dcid, k ->
                keysDcid = dcid
                keys = k
            } ?: break
            sawInitial = true
            if (!collectCrypto(pkt.frames)) return finish(NotTls)
            p = pkt.packetEnd
        }
        if (!sawInitial) return if (datagrams == 1) finish(NotTls) else NeedMore
        return resolve()
    }

    private fun finish(r: HelloResult): HelloResult {
        done = true
        chunks.clear()
        return r
    }

    private fun resolve(): HelloResult {
        val sorted = chunks.sortedBy { it.first }
        val hs = ByteArrayOutputStream()
        var have = 0
        for ((o, data) in sorted) {
            if (o > have) break // gap: wait for the missing datagram
            val e = o + data.size
            if (e > have) {
                hs.write(data, have - o, e - have)
                have = e
            }
        }
        if (have == 0) return NeedMore
        val buf = hs.toByteArray()
        val rec = asTlsRecords(buf)
        val r = parseClientHello(rec, rec.size)
        return if (r === NeedMore) r else finish(r)
    }

    /** parseClientHello wants TLS records; CRYPTO data is the bare handshake stream, so wrap it. */
    private fun asTlsRecords(hs: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(hs.size + 16)
        var i = 0
        while (i < hs.size) {
            val c = minOf(16000, hs.size - i)
            out.write(0x16); out.write(0x03); out.write(0x01); out.write(c shr 8); out.write(c and 0xFF)
            out.write(hs, i, c)
            i += c
        }
        return out.toByteArray()
    }

    /** Collect CRYPTO frames. False means "implausible, give up". Unknown frames end parsing but keep what we have. */
    private fun collectCrypto(plain: ByteArray): Boolean {
        val c = QuicCursor(plain, 0, plain.size)
        try {
            while (c.p < plain.size) {
                when (c.varint()) {
                    0L, 1L -> {} // PADDING, PING
                    2L, 3L -> { // ACK (+ECN counts for type 3)
                        val type3 = plain[c.p - 1].toInt() == 3
                        c.varint(); c.varint()
                        val ranges = c.varint()
                        c.varint()
                        var i = 0L
                        while (i < ranges) { c.varint(); c.varint(); i++ }
                        if (type3) { c.varint(); c.varint(); c.varint() }
                    }
                    6L -> { // CRYPTO
                        val o = c.varint()
                        val l = c.varint()
                        if (o + l > MAX_CRYPTO) return false
                        val start = c.p
                        c.skip(l)
                        chunks.add(o.toInt() to plain.copyOfRange(start, start + l.toInt()))
                    }
                    0x1CL -> { c.varint(); c.varint(); c.skip(c.varint()) } // CONNECTION_CLOSE (transport)
                    else -> return true
                }
            }
        } catch (_: QuicTruncated) {
            // keep the chunks collected so far
        }
        return true
    }

    private companion object {
        const val MAX_DATAGRAMS = 8
        const val MAX_CRYPTO = 64 * 1024
    }
}

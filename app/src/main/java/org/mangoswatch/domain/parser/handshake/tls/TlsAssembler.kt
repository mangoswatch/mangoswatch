package org.mangoswatch.domain.parser.handshake.tls

/**
 * Collects the first client->server bytes of one TCP stream, from raw TCP segments seen on the TUN,
 * until parseClientHello can decide. Not thread-safe: use one instance per stream from one thread.
 *
 * feed() returns NeedMore while pending, then ClientHello or NotTls exactly once, then null forever.
 * It is stateful, so call it once per packet (never from a lambda that may be retried).
 */
class TlsAssembler {
    private var buf = ByteArray(2048)
    private var len = 0
    private var nextSeq = 0
    private var started = false
    private var done = false

    /** [seq] is the TCP sequence number of data[off] (raw 32-bit value; wraparound is handled). */
    fun feed(seq: Int, data: ByteArray, off: Int, n: Int): HelloResult? {
        if (done) return null
        if (n <= 0) return NeedMore
        if (!started) {
            started = true
            nextSeq = seq
        }
        val diff = seq - nextSeq // modular 32-bit arithmetic
        if (diff > 0) return finish(NotTls) // gap or reordering: give up, do not guess
        val skip = -diff // bytes of this segment we already have (retransmission/overlap)
        if (skip >= n) return NeedMore
        val take = n - skip
        if (len + take > MAX) return finish(NotTls)
        if (len + take > buf.size) buf = buf.copyOf(minOf(MAX, maxOf(buf.size * 2, len + take)))
        System.arraycopy(data, off + skip, buf, len, take)
        len += take
        nextSeq += take
        val r = parseClientHello(buf, len)
        return if (r === NeedMore) r else finish(r)
    }

    private fun finish(r: HelloResult): HelloResult {
        done = true
        buf = EMPTY // finished assemblers stay in the map as tiny tombstones
        return r
    }

    private companion object {
        const val MAX = 16 * 1024
        val EMPTY = ByteArray(0)
    }
}

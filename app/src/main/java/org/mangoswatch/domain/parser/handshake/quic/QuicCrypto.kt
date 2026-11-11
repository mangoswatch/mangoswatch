package org.mangoswatch.domain.parser.handshake.quic

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// QUIC v1 (RFC 9000 / RFC 9001). Initial packets are protected with keys derived from a public salt and the
// Destination Connection ID, so anyone on the path can decrypt them: that is what makes the SNI readable.

private val INITIAL_SALT_V1 = "38762cf7f55934b34d179ae6a4c80cadccbb7f0a".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal class InitialKeys(val key: ByteArray, val iv: ByteArray, val hp: ByteArray)

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val m = Mac.getInstance("HmacSHA256")
    m.init(SecretKeySpec(key, "HmacSHA256"))
    return m.doFinal(data)
}

/** HKDF-Expand-Label with an empty context and length <= 32 (one HMAC block). */
private fun expandLabel(secret: ByteArray, label: String, length: Int): ByteArray {
    val full = "tls13 $label".toByteArray(Charsets.US_ASCII)
    val info = byteArrayOf((length shr 8).toByte(), length.toByte(), full.size.toByte()) + full + byteArrayOf(0)
    return hmacSha256(secret, info + byteArrayOf(1)).copyOf(length)
}

internal fun deriveClientInitialKeys(dcid: ByteArray): InitialKeys {
    val initial = hmacSha256(INITIAL_SALT_V1, dcid)
    val client = expandLabel(initial, "client in", 32)
    return InitialKeys(
        key = expandLabel(client, "quic key", 16),
        iv = expandLabel(client, "quic iv", 12),
        hp = expandLabel(client, "quic hp", 16),
    )
}

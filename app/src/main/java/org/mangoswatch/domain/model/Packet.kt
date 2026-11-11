package org.mangoswatch.domain.model

data class Packet (
    val protocol: Int,
    val packetSrcIp: String,
    val packetSrcPort: Int,
    val packetDstIp: String,
    val packetDstPort: Int,
    val ipOwner: IpOwner,
    val flags: Int,
    val b: ByteArray,
    val payloadOff: Int,
    val payloadLen: Int,
    val totalLen: Int,
    val outbound: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (protocol != other.protocol) return false
        if (packetSrcPort != other.packetSrcPort) return false
        if (packetDstPort != other.packetDstPort) return false
        if (flags != other.flags) return false
        if (payloadOff != other.payloadOff) return false
        if (payloadLen != other.payloadLen) return false
        if (totalLen != other.totalLen) return false
        if (outbound != other.outbound) return false
        if (packetSrcIp != other.packetSrcIp) return false
        if (packetDstIp != other.packetDstIp) return false
        if (ipOwner != other.ipOwner) return false
        if (!b.contentEquals(other.b)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocol
        result = 31 * result + packetSrcPort
        result = 31 * result + packetDstPort
        result = 31 * result + flags
        result = 31 * result + payloadOff
        result = 31 * result + payloadLen
        result = 31 * result + totalLen
        result = 31 * result + outbound.hashCode()
        result = 31 * result + packetSrcIp.hashCode()
        result = 31 * result + packetDstIp.hashCode()
        result = 31 * result + ipOwner.hashCode()
        result = 31 * result + b.contentHashCode()
        return result
    }

}
package org.mangoswatch.data.analyzer

import android.os.SystemClock
import org.mangoswatch.data.resolver.UidResolver
import org.mangoswatch.domain.parser.DnsQueryParser
import org.mangoswatch.domain.parser.ip.IpRanges
import org.mangoswatch.domain.parser.ip.IpV4Parser
import org.mangoswatch.domain.parser.ip.IpV6Parser
import org.mangoswatch.domain.parser.handshake.tls.ClientHello
import org.mangoswatch.domain.parser.handshake.tls.TlsAssembler
import org.mangoswatch.domain.parser.handshake.tls.HelloResult
import org.mangoswatch.domain.parser.handshake.tls.NotTls
import org.mangoswatch.domain.parser.handshake.quic.QuicInitialAssembler
import org.mangoswatch.domain.parser.handshake.quic.looksLikeQuicInitial
import org.mangoswatch.domain.parser.tcpSeq
import org.mangoswatch.domain.model.Packet
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.repository.StreamsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StreamTrafficProcessor(
    amazonRanges: IpRanges,
    anthropicRanges: IpRanges,
    appleRanges: IpRanges,
    googleRanges: IpRanges,
    metaRanges: IpRanges,
    microsoftRanges: IpRanges,
    netflixRanges: IpRanges,
    nvidiaRanges: IpRanges,
    openaiRanges: IpRanges,
    reservedRanges: IpRanges
): KoinComponent {

    private val repository by inject<StreamsRepository>()

    private val ipV4Parser = IpV4Parser(
        amazonRanges = amazonRanges,
        anthropicRanges = anthropicRanges,
        appleRanges = appleRanges,
        googleRanges = googleRanges,
        metaRanges = metaRanges,
        microsoftRanges = microsoftRanges,
        netflixRanges = netflixRanges,
        nvidiaRanges = nvidiaRanges,
        openaiRanges = openaiRanges,
        reservedRanges = reservedRanges
    )
    private val ipV6Parser = IpV6Parser(
        amazonRanges = amazonRanges,
        anthropicRanges = anthropicRanges,
        appleRanges = appleRanges,
        googleRanges = googleRanges,
        metaRanges = metaRanges,
        microsoftRanges = microsoftRanges,
        netflixRanges = netflixRanges,
        nvidiaRanges = nvidiaRanges,
        openaiRanges = openaiRanges,
        reservedRanges = reservedRanges
    )
    private val dnsQueryParser = DnsQueryParser()
    private val uidResolver = UidResolver()

    private val helloAssemblers =
        object : LinkedHashMap<Any, TlsAssembler>(256, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, TlsAssembler>) =
                size > 2048
        }

    private val quicAssemblers =
        object : LinkedHashMap<Any, QuicInitialAssembler>(256, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, QuicInitialAssembler>) =
                size > 2048
        }

    fun onPacket(b: ByteArray, n: Int, outbound: Boolean) {
        if (n < 1) return
        when ((b[0].toInt() shr 4) and 0xF) {
            4 -> processPacket(ipV4Parser.parseV4Packet(b, n, outbound) ?: return)
            6 -> processPacket(ipV6Parser.parseV6Packet(b, n, outbound) ?: return)
        }
    }

    fun shutdown() {
        uidResolver.shutdown()
    }

    private fun assembleHello(key: Any, p: Packet, syn: Boolean): HelloResult? {
        if (syn) helloAssemblers.remove(key) // porta riusata
        var r: HelloResult? = null
        if (p.payloadLen > 0) {
            val seq = tcpSeq(p.b, p.payloadOff, p.packetSrcPort, p.packetDstPort)
            r = if (seq == null) NotTls
            else helloAssemblers.getOrPut(key) { TlsAssembler() }
                .feed(seq, p.b, p.payloadOff, p.payloadLen)
        }
        if ((p.flags and 0x05) != 0) helloAssemblers.remove(key) // FIN o RST
        return r
    }

    private fun assembleQuic(key: Any, p: Packet): HelloResult? {
        if (!looksLikeQuicInitial(p.b, p.payloadOff, p.payloadLen)) return null
        return quicAssemblers.getOrPut(key) { QuicInitialAssembler() }
            .feed(p.b, p.payloadOff, p.payloadLen)
    }

    private fun processPacket(packet: Packet) {
        packet.apply {
            val localPort = if (outbound) packetSrcPort else packetDstPort
            val remotePort = if (outbound) packetDstPort else packetSrcPort
            val remoteIp = if (outbound) packetDstIp else packetSrcIp

            val now = SystemClock.elapsedRealtime()
            val key = StreamModel.key(protocol, localPort, remoteIp, remotePort)

            val syn = protocol == 6 && (flags and 0x12) == 0x02
            val hello = if (outbound && protocol == 6) assembleHello(key, this, syn) else null
            val quic = if (outbound && protocol == 17) assembleQuic(key, this) else null

            var claimed: StreamModel? = null

            repository.updateStream(key) { old ->
                val reused = outbound && old != null && syn && old.finSeen
                val base = when {
                    old != null && !reused -> old
                    outbound -> StreamModel(
                        protocol = protocol,
                        srcIpStr = packetSrcIp,
                        srcPort = packetSrcPort,
                        dstIpStr = packetDstIp,
                        dstPort = packetDstPort,
                        ipOwner = ipOwner,
                        firstSeen = now
                    )
                    else -> return@updateStream null
                }

                var stream = base.copy(
                    bytesUp = base.bytesUp + if (outbound) totalLen else 0,
                    bytesDown = base.bytesDown + if (outbound) 0 else totalLen,
                    lastSeen = now,
                    finSeen = base.finSeen || (protocol == 6 && (flags and 0x05) != 0)
                )

                if (outbound && payloadLen > 0) {
                    if (protocol == 6 && !stream.tlsProbed) {
                        if (hello is ClientHello) {
                            stream = stream.copy(
                                tlsProbed = true,
                                isTls = true,
                                sni = hello.sni ?: stream.sni
                            )
                        } else if (hello === NotTls) {
                            stream = stream.copy(tlsProbed = true)
                        }
                    } else if (protocol == 17 && !stream.tlsProbed && quic != null) {
                        stream = when {
                            quic is ClientHello -> stream.copy(
                                tlsProbed = true,
                                isTls = true,
                                sni = quic.sni ?: stream.sni
                            )
                            quic === NotTls -> stream.copy(tlsProbed = true)
                            else -> stream
                        }
                    } else if (stream.dnsQuery == null && remotePort == 53) {
                        val q = when (protocol) {
                            17 -> dnsQueryParser.extractDnsQuery(b, payloadOff, payloadLen)
                            6 if payloadLen > 2 ->
                                dnsQueryParser.extractDnsQuery(b, payloadOff + 2, payloadLen - 2)
                            else -> null
                        }
                        if (q != null) stream = stream.copy(dnsQuery = q)
                    }
                }

                if (uidResolver.shouldAttempt(stream, now)) {
                    claimed = stream
                    stream = stream.copy(uidAttempts = stream.uidAttempts + 1, lastUidAttempt = now)
                }
                stream
            }

            claimed?.let { c ->
                val queued = uidResolver.resolveAsync(c) { uid, pkg ->
                    repository.updateStream(key) { stream ->
                        if (stream != null && stream.firstSeen == c.firstSeen) {
                            stream.copy(uid = uid, pkg = pkg)
                        } else stream
                    }
                }
                if (!queued) {
                    repository.updateStream(key) { stream ->
                        stream?.copy(uidAttempts = (stream.uidAttempts - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }
}

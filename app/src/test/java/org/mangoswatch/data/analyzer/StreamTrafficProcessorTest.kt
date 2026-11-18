package org.mangoswatch.data.analyzer

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.repository.AssetRepository
import org.mangoswatch.domain.repository.StreamsRepository
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
class StreamTrafficProcessorTest {

    private lateinit var streamsRepository: StreamsRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var processor: StreamTrafficProcessor

    @Before
    fun setUp() {
        streamsRepository = mock(StreamsRepository::class.java)
        assetRepository = mock(AssetRepository::class.java)

        whenever(assetRepository.getFileFromAssetsCache(any())).thenReturn("".byteInputStream())

        startKoin {
            modules(
                module {
                    single { streamsRepository }
                    single { assetRepository }
                }
            )
        }

        whenever(streamsRepository.getStreams()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        doAnswer { invocation ->
            val transform = invocation.getArgument<(StreamModel?) -> StreamModel?>(1)
            transform(null)
            null
        }.whenever(streamsRepository).updateStream(any(), any())

        processor = StreamTrafficProcessor()
    }

    @After
    fun tearDown() {
        processor.shutdown()
        stopKoin()
    }

    @Test
    fun testOnPacketInvalidLengthReturnsEarly() {
        val bytes = byteArrayOf()
        processor.onPacket(bytes, 0, true)
        verify(streamsRepository, never()).updateStream(any(), any())
    }

    @Test
    fun testOnPacketValidIpv4TcpParsesAndUpdatesStream() {
        val packet = byteArrayOf(
            0x45.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2C.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x40.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xC0.toByte(), 0xA8.toByte(), 0x01.toByte(), 0x02.toByte(),
            0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(),
            0x04.toByte(), 0xD2.toByte(), 0x01.toByte(), 0xBB.toByte(), // Ports
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), // Seq
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), // Ack
            0x50.toByte(), 0x02.toByte(), 0x71.toByte(), 0x10.toByte(), // Data offset / flags (SYN) / window
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Checksum / urg
        )

        processor.onPacket(packet, packet.size, true)

        verify(streamsRepository, atLeastOnce()).updateStream(any(), any())
    }

    @Test
    fun testOnPacketValidIpv6UdpParsesAndUpdatesStream() {
        val packet = ByteArray(48) { 0 }
        packet[0] = 0x60.toByte() // Version 6
        packet[4] = 0x00.toByte() // Payload len hi
        packet[5] = 0x08.toByte() // Payload len lo (8 bytes UDP header)
        packet[6] = 17.toByte()   // Next Header = UDP
        packet[7] = 64.toByte()   // Hop limit
        packet[23] = 1.toByte()   // Src IP
        packet[39] = 2.toByte()   // Dst IP
        packet[40] = 0x04.toByte(); packet[41] = 0xD2.toByte() // Src Port 1234
        packet[42] = 0x00.toByte(); packet[43] = 0x35.toByte() // Dst Port 53 (DNS)

        processor.onPacket(packet, packet.size, true)

        verify(streamsRepository, atLeastOnce()).updateStream(any(), any())
    }
}

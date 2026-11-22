package org.mangoswatch.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.model.StreamModel

class StreamsRepositoryImplTest {

    @Test
    fun testUpdateAndGetStreams() = runTest {
        val repo = StreamsRepositoryImpl()
        val initialList = repo.getStreams().first()
        assertTrue(initialList.isEmpty())

        val key = StreamModel.key(6, 12345, "1.1.1.1", 443)
        val stream = StreamModel(
            protocol = 6,
            srcIpStr = "192.168.1.10",
            srcPort = 12345,
            dstIpStr = "1.1.1.1",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = System.currentTimeMillis()
        )

        repo.updateStream(key) { stream }

        val listAfterAdd = repo.getStreams().first()
        assertEquals(1, listAfterAdd.size)
        assertEquals("1.1.1.1", listAfterAdd[0].dstIpStr)

        // Remove stream
        repo.updateStream(key) { null }
        val listAfterRemove = repo.getStreams().first()
        assertTrue(listAfterRemove.isEmpty())
    }

    @Test
    fun testStreamInactiveTimeoutEvictionOnLimit() = runTest {
        var currentTime = 1000000L
        val repo = StreamsRepositoryImpl(
            maxStreams = 1,
            inactiveTimeoutMs = 30000L,
            clock = { currentTime }
        )

        val key1 = StreamModel.key(6, 12345, "1.1.1.1", 443)
        val stream1 = StreamModel(
            protocol = 6,
            srcIpStr = "192.168.1.10",
            srcPort = 12345,
            dstIpStr = "1.1.1.1",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = currentTime,
            lastSeen = currentTime,
            finSeen = true
        )

        repo.updateStream(key1) { stream1 }
        assertEquals(1, repo.getStreams().first().size)

        // Advance time beyond 30s timeout and trigger update at limit
        currentTime += 30001L
        val key2 = StreamModel.key(6, 12346, "1.1.1.1", 443)
        val stream2 = StreamModel(
            protocol = 6,
            srcIpStr = "192.168.1.10",
            srcPort = 12346,
            dstIpStr = "1.1.1.1",
            dstPort = 443,
            ipOwner = IpOwner.OTHER,
            firstSeen = currentTime,
            lastSeen = currentTime,
            finSeen = false
        )
        repo.updateStream(key2) { stream2 }

        val listAfterEviction = repo.getStreams().first()
        assertEquals(1, listAfterEviction.size)
        assertEquals(12346, listAfterEviction[0].srcPort)
    }

    @Test
    fun testMaxStreamsEviction() = runTest {
        val currentTime = 1000000L
        val repo = StreamsRepositoryImpl(
            maxStreams = 3,
            clock = { currentTime }
        )

        for (i in 1..5) {
            val key = StreamModel.key(6, 1000 + i, "1.1.1.1", 443)
            val stream = StreamModel(
                protocol = 6,
                srcIpStr = "192.168.1.10",
                srcPort = 1000 + i,
                dstIpStr = "1.1.1.1",
                dstPort = 443,
                ipOwner = IpOwner.OTHER,
                firstSeen = currentTime,
                lastSeen = currentTime + i, // ascending lastSeen
                finSeen = false
            )
            repo.updateStream(key) { stream }
        }

        val list = repo.getStreams().first()
        assertEquals(3, list.size)
        // Oldest (lastSeen +1 and +2) should be evicted, keeping +3, +4, +5
        val ports = list.map { it.srcPort }.toSet()
        assertTrue(ports.contains(1003))
        assertTrue(ports.contains(1004))
        assertTrue(ports.contains(1005))
        assertFalse(ports.contains(1001))
        assertFalse(ports.contains(1002))
    }

    @Test
    fun testMaxStreamsEvictionOnUpdateWithoutFlowReader() = runTest {
        val currentTime = 1000000L
        val repo = StreamsRepositoryImpl(
            maxStreams = 2,
            clock = { currentTime }
        )

        for (i in 1..4) {
            val key = StreamModel.key(6, 2000 + i, "1.1.1.1", 443)
            val stream = StreamModel(
                protocol = 6,
                srcIpStr = "192.168.1.10",
                srcPort = 2000 + i,
                dstIpStr = "1.1.1.1",
                dstPort = 443,
                ipOwner = IpOwner.OTHER,
                firstSeen = currentTime,
                lastSeen = currentTime + i,
                finSeen = false
            )
            repo.updateStream(key) { stream }
        }

        // Without collecting getStreams() beforehand, updateStream must trigger performEviction
        val snapshot = repo.getStreams().first()
        assertEquals(2, snapshot.size)
        val ports = snapshot.map { it.srcPort }.toSet()
        assertTrue(ports.contains(2003))
        assertTrue(ports.contains(2004))
        assertFalse(ports.contains(2001))
        assertFalse(ports.contains(2002))
    }
}

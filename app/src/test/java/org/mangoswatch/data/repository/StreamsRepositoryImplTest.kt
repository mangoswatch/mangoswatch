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
    fun testStreamInactiveTimeoutEviction() = runTest {
        var currentTime = 1000000L
        val repo = StreamsRepositoryImpl(
            inactiveTimeoutMs = 30000L,
            clock = { currentTime }
        )

        val key = StreamModel.key(6, 12345, "1.1.1.1", 443)
        val stream = StreamModel(
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

        repo.updateStream(key) { stream }
        assertEquals(1, repo.getStreams().first().size)

        // Advance time beyond 30s timeout
        currentTime += 30001L
        val listAfterEviction = repo.getStreams().first()
        assertTrue(listAfterEviction.isEmpty())
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
}

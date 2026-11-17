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
}

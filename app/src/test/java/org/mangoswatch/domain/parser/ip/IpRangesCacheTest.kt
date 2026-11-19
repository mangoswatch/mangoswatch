package org.mangoswatch.domain.parser.ip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.repository.AssetRepository
import java.net.InetAddress

class IpRangesCacheTest {

    @Test
    fun `v4 lookup reads only v4 once and reload replaces cached ranges`() {
        val assetRepository = mock(AssetRepository::class.java)
        whenever(assetRepository.getFileFromAssetsCache("google_ipv4.txt"))
            .thenReturn("8.8.8.0/24\n".byteInputStream())
        whenever(assetRepository.getFileFromAssetsCache("google_ipv6.txt"))
            .thenReturn("".byteInputStream())

        val cache = IpRangesCache(assetRepository)

        repeat(10) {
            assertTrue(cache.ownerV4(IpOwner.GOOGLE).containsV4(0x08080808L))
        }

        verify(assetRepository, times(1)).getFileFromAssetsCache("google_ipv4.txt")
        verify(assetRepository, times(0)).getFileFromAssetsCache("google_ipv6.txt")

        whenever(assetRepository.getFileFromAssetsCache("google_ipv4.txt"))
            .thenReturn("1.1.1.0/24\n".byteInputStream())

        cache.reloadV4()

        assertFalse(cache.ownerV4(IpOwner.GOOGLE).containsV4(0x08080808L))
        assertTrue(cache.ownerV4(IpOwner.GOOGLE).containsV4(0x01010101L))
        verify(assetRepository, times(2)).getFileFromAssetsCache("google_ipv4.txt")
    }

    @Test
    fun `v6 lookup reads only v6 once`() {
        val assetRepository = mock(AssetRepository::class.java)
        whenever(assetRepository.getFileFromAssetsCache("google_ipv4.txt"))
            .thenReturn("".byteInputStream())
        whenever(assetRepository.getFileFromAssetsCache("google_ipv6.txt"))
            .thenReturn("2001:4860::/32\n".byteInputStream())

        val cache = IpRangesCache(assetRepository)
        val address = InetAddress.getByName("2001:4860:4860::8888").address

        repeat(10) {
            assertTrue(cache.ownerV6(IpOwner.GOOGLE).containsV6(address))
        }

        verify(assetRepository, times(1)).getFileFromAssetsCache("google_ipv6.txt")
        verify(assetRepository, times(0)).getFileFromAssetsCache("google_ipv4.txt")
    }
}

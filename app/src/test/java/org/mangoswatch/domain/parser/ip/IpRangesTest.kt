package org.mangoswatch.domain.parser.ip

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mangoswatch.data.repository.AssetRepositoryImpl
import org.mangoswatch.data.repository.AssetSyncImpl
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
class IpRangesTest {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cacheDir = File(context.cacheDir, "assets_cache")
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun ipToLong(ipStr: String): Long {
        val parts = ipStr.split(".").map { it.toLong() }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    @Test
    fun testEmptyIpRanges() {
        val empty = IpRanges.empty()
        assertFalse(empty.containsV4(ipToLong("8.8.8.8")))
        assertFalse(empty.containsV6(InetAddress.getByName("2001:db8::1").address))
    }

    @Test
    fun testLoadV4Ranges() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val assetRepository = AssetRepositoryImpl(context, assetSync)
        val v4Content = """
            # Sample IPv4 Ranges
            192.168.1.0/24
            10.0.0.0/8
        """.trimIndent()

        File(cacheDir, "google_ipv4.txt").writeText(v4Content)

        val ranges = IpRanges.loadV4FromFile(assetRepository.getFileFromAssetsCache("google_ipv4.txt"))

        assertTrue(ranges.containsV4(ipToLong("192.168.1.1")))
        assertTrue(ranges.containsV4(ipToLong("192.168.1.254")))
        assertFalse(ranges.containsV4(ipToLong("192.168.2.1")))

        assertTrue(ranges.containsV4(ipToLong("10.0.0.1")))
        assertTrue(ranges.containsV4(ipToLong("10.255.255.254")))
        assertFalse(ranges.containsV4(ipToLong("11.0.0.1")))
    }

    @Test
    fun testLoadV6Ranges() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val assetRepository = AssetRepositoryImpl(context, assetSync)
        val v6Content = """
            # Sample IPv6 Ranges
            2001:db8::/32
            fd00::/16
        """.trimIndent()

        File(cacheDir, "google_ipv6.txt").writeText(v6Content)

        val ranges = IpRanges.loadV6FromFile(assetRepository.getFileFromAssetsCache("google_ipv6.txt"))

        val targetV6In = InetAddress.getByName("2001:db8::1").address
        val targetV6Out = InetAddress.getByName("2001:db9::1").address

        assertTrue(ranges.containsV6(targetV6In))
        assertFalse(ranges.containsV6(targetV6Out))

        val localV6In = InetAddress.getByName("fd00::1234").address
        assertTrue(ranges.containsV6(localV6In))
    }

    @Test
    fun testLoadV4RangesWithMalformedLines() {
        val context = RuntimeEnvironment.getApplication()
        val assetSync = AssetSyncImpl()
        val assetRepository = AssetRepositoryImpl(context, assetSync)
        val v4Content = """
            invalid.ip.address/24
            192.168.1.0/24
            bad-line-without-slash
            10.0.0.0/8
        """.trimIndent()

        File(cacheDir, "google_ipv4.txt").writeText(v4Content)

        val ranges = IpRanges.loadV4FromFile(assetRepository.getFileFromAssetsCache("google_ipv4.txt"))

        assertTrue(ranges.containsV4(ipToLong("192.168.1.1")))
        assertTrue(ranges.containsV4(ipToLong("10.0.0.1")))
    }
}

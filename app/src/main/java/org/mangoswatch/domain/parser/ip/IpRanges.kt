package org.mangoswatch.domain.parser.ip

import android.content.Context
import org.mangoswatch.data.util.AssetManager
import org.mangoswatch.domain.model.IpOwner
import java.net.InetAddress

class IpRanges private constructor(
    private val v4Starts: LongArray,
    private val v4Ends: LongArray,
    private val v6Starts: LongArray, // 2 longs for range (hi, lo), sorted, merged, disjoint
    private val v6Ends: LongArray,

    ) {

    companion object {

        fun load(
            context: Context,
            ipOwner: IpOwner
        ): IpRanges {

            val assetV4 = AssetManager.getIpV4FileName(ipOwner)
            val assetV6 = AssetManager.getIpV6FileName(ipOwner)
            val (v4starts, v6ends) = loadV4(context, assetV4)
            val (v6Starts, v6Ends) = loadV6(context, assetV6)

            return IpRanges(
                v4Starts = v4starts,
                v4Ends = v6ends,
                v6Starts = v6Starts,
                v6Ends = v6Ends
            )

        }

        private fun loadV4(context: Context, asset: String): Pair<LongArray, LongArray> {
            val ranges = ArrayList<LongArray>()
            try {
                AssetManager.getAsset(context, asset).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val s = line.trim()
                        if (s.isEmpty() || s.startsWith("#")) continue
                        val slash = s.indexOf('/')
                        if (slash < 0) continue
                        val addr = s.substring(0, slash)
                        val len  = s.substring(slash + 1).toIntOrNull() ?: continue
                        if (len !in 0..32) continue
                        val base = addr.split(".").fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
                        val size = 1L shl (32 - len)
                        val start = base and (size - 1).inv()
                        ranges.add(longArrayOf(start, start + size - 1))
                    }
                }
            } catch (_: Exception) { /* asset mancante: nessun range */ }
            ranges.sortBy { it[0] }
            val st = ArrayList<Long>()
            val en = ArrayList<Long>()
            for (r in ranges) {
                if (en.isNotEmpty() && r[0] <= en.last() + 1) {
                    if (r[1] > en.last()) en[en.lastIndex] = r[1]
                } else {
                    st.add(r[0]); en.add(r[1])
                }
            }
            return st.toLongArray() to en.toLongArray()
        }
        private fun loadV6(context: Context, asset: String): Pair<LongArray, LongArray> {
            val ranges = ArrayList<LongArray>() // [startHi, startLo, endHi, endLo]
            try {
                AssetManager.getAsset(context, asset).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val s = line.trim()
                        if (s.isEmpty() || s.startsWith("#")) continue
                        try {
                            val slash = s.indexOf('/')
                            if (slash < 0) continue
                            val prefixLen = s.substring(slash + 1).toIntOrNull() ?: continue
                            if (prefixLen !in 0..128) continue
                            val bytes = InetAddress.getByName(s.substring(0, slash)).address
                            if (bytes.size != 16) continue
                            ranges.add(v6Range(bytes, prefixLen))
                        } catch (_: Exception) { /* riga malformata */ }
                    }
                }
            } catch (_: Exception) { /* asset mancante: nessun range IPv6 */ }
            ranges.sortWith { a, b -> cmp128(a[0], a[1], b[0], b[1]) }
            val merged = ArrayList<LongArray>()
            for (r in ranges) {
                val last = merged.lastOrNull()
                if (last != null && cmp128(r[0], r[1], last[2], last[3]) <= 0) {
                    if (cmp128(r[2], r[3], last[2], last[3]) > 0) { last[2] = r[2]; last[3] = r[3] }
                } else {
                    merged.add(r)
                }
            }
            val starts = LongArray(merged.size * 2)
            val ends = LongArray(merged.size * 2)
            merged.forEachIndexed { i, r ->
                starts[2 * i] = r[0]; starts[2 * i + 1] = r[1]
                ends[2 * i] = r[2]; ends[2 * i + 1] = r[3]
            }
            return starts to ends
        }

        /** 16 byte address + prefix length -> [startHi, startLo, endHi, endLo]. */
        private fun v6Range(bytes: ByteArray, prefixLen: Int): LongArray {
            var hi = 0L
            var lo = 0L
            for (i in 0 until 8) hi = (hi shl 8) or (bytes[i].toLong() and 0xFF)
            for (i in 8 until 16) lo = (lo shl 8) or (bytes[i].toLong() and 0xFF)
            val h = 128 - prefixLen // host bits
            val mHi = when { h <= 64 -> 0L; h < 128 -> (1L shl (h - 64)) - 1; else -> -1L }
            val mLo = when { h <= 0 -> 0L; h < 64 -> (1L shl h) - 1; else -> -1L }
            val sHi = hi and mHi.inv()
            val sLo = lo and mLo.inv()
            return longArrayOf(sHi, sLo, sHi or mHi, sLo or mLo)
        }

        private fun cmp128(aHi: Long, aLo: Long, bHi: Long, bLo: Long): Int {
            val c = java.lang.Long.compareUnsigned(aHi, bHi)
            return if (c != 0) c else java.lang.Long.compareUnsigned(aLo, bLo)
        }


    }

    fun containsV4(ip: Long): Boolean {
        var lo = 0
        var hi = v4Starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                ip < v4Starts[mid] -> hi = mid - 1
                ip > v4Ends[mid]   -> lo = mid + 1
                else               -> return true
            }
        }
        return false
    }

    fun containsV6(addr: ByteArray, off: Int = 0): Boolean {
        if (addr.size < off + 16) return false
        var ipHi = 0L
        var ipLo = 0L
        for (i in 0 until 8) ipHi = (ipHi shl 8) or (addr[off + i].toLong() and 0xFF)
        for (i in 8 until 16) ipLo = (ipLo shl 8) or (addr[off + i].toLong() and 0xFF)
        var lo = 0
        var hi = v6Starts.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                cmp128(ipHi, ipLo, v6Starts[2 * mid], v6Starts[2 * mid + 1]) < 0    -> hi = mid - 1
                cmp128(ipHi, ipLo, v6Ends[2 * mid], v6Ends[2 * mid + 1]) > 0        -> lo = mid + 1
                else                                                                                          -> return true
            }
        }
        return false
    }


}
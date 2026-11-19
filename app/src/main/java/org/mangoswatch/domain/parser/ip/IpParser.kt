package org.mangoswatch.domain.parser.ip

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.domain.model.IpOwner
import java.net.InetAddress

open class IpParser : KoinComponent {

    private val ipRangesCache by inject<IpRangesCache>()

    // v6
    private fun identifyOwnerV6(b: ByteArray, off: Int): IpOwner {
        IpOwner.entries.forEach {
            if (ipRangesCache.ownerV6(it).containsV6(b, off)) return it
        }
        return IpOwner.OTHER
    }

    // v4
    private fun identifyOwnerV4(ipL: Long): IpOwner {
        IpOwner.entries.forEach {
            if (ipRangesCache.ownerV4(it).containsV4(ipL)) return it
        }
        return IpOwner.OTHER
    }

    fun identifyOwner(ip: String): IpOwner {
        val address = runCatching { InetAddress.getByName(ip).address }.getOrNull() ?: return IpOwner.OTHER
        return if (address.size == 4) {
            val ipLong = address.fold(0L) { value, byte ->
                (value shl 8) or (byte.toLong() and 0xFF)
            }
            identifyOwnerV4(ipLong)
        } else {
            identifyOwnerV6(address, 0)
        }
    }
}

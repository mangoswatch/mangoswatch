package org.mangoswatch.domain.parser.ip

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.domain.model.IpOwner

open class IpParser : KoinComponent {

    private val ipRangesCache by inject<IpRangesCache>()

    // v6
    fun identifyOwner(b: ByteArray, off: Int): IpOwner {
        IpOwner.entries.forEach {
            if (ipRangesCache.ownerV6(it).containsV6(b, off)) return it
        }
        return IpOwner.OTHER
    }

    // v4
    fun identifyOwner(ipL: Long): IpOwner {
        IpOwner.entries.forEach {
            if (ipRangesCache.ownerV4(it).containsV4(ipL)) return it
        }
        return IpOwner.OTHER
    }
}

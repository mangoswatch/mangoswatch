package org.mangoswatch.domain.parser.ip

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.repository.AssetRepository

open class IpParser : KoinComponent {

    private val assetRepository by inject<AssetRepository>()

    fun ownerRanges(owner: IpOwner): IpRanges {
        val ownerIpv4FileName = "${owner.name.lowercase()}_ipv4.txt"
        val ownerIpv6FileName = "${owner.name.lowercase()}_ipv6.txt"
        return IpRanges.loadFromFile(
            assetRepository.getFileFromAssetsCache(ownerIpv4FileName),
            assetRepository.getFileFromAssetsCache(ownerIpv6FileName)
        )
    }

    // v6
    fun identifyOwner(b: ByteArray, off: Int): IpOwner {
        IpOwner.entries.forEach {
            if (ownerRanges(it).containsV6(b, off)) return it
        }
        return IpOwner.OTHER
    }

    // v4
    fun identifyOwner(ipL: Long): IpOwner {
        IpOwner.entries.forEach {
            if (ownerRanges(it).containsV4(ipL)) return it
        }
        return IpOwner.OTHER
    }
}

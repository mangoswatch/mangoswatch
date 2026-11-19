package org.mangoswatch.domain.parser.ip

import org.mangoswatch.domain.model.IpOwner
import org.mangoswatch.domain.repository.AssetRepository

class IpRangesCache(
    private val assetRepository: AssetRepository
) {
    private val v4Lock = Any()
    private val v6Lock = Any()

    @Volatile
    private var v4Ranges: Map<IpOwner, IpRanges>? = null

    @Volatile
    private var v6Ranges: Map<IpOwner, IpRanges>? = null

    fun ownerV4(owner: IpOwner): IpRanges =
        v4Ranges?.get(owner) ?: synchronized(v4Lock) {
            v4Ranges?.get(owner) ?: buildV4Ranges().getValue(owner)
        }

    fun ownerV6(owner: IpOwner): IpRanges =
        v6Ranges?.get(owner) ?: synchronized(v6Lock) {
            v6Ranges?.get(owner) ?: buildV6Ranges().getValue(owner)
        }

    fun reload() {
        synchronized(v4Lock) {
            v4Ranges = buildV4RangesLocked()
        }
        synchronized(v6Lock) {
            v6Ranges = buildV6RangesLocked()
        }
    }

    fun reloadV4() {
        synchronized(v4Lock) {
            v4Ranges = buildV4RangesLocked()
        }
    }

    private fun buildV4Ranges(): Map<IpOwner, IpRanges> =
        synchronized(v4Lock) {
            v4Ranges?.also { return it }
            buildV4RangesLocked()
        }

    private fun buildV4RangesLocked(): Map<IpOwner, IpRanges> {
        val ranges = IpOwner.entries.associateWith { owner ->
            val fileName = "${owner.name.lowercase()}_ipv4.txt"
            IpRanges.loadV4FromFile(assetRepository.getFileFromAssetsCache(fileName))
        }
        v4Ranges = ranges
        return ranges
    }

    private fun buildV6Ranges(): Map<IpOwner, IpRanges> =
        synchronized(v6Lock) {
            v6Ranges?.also { return it }
            buildV6RangesLocked()
        }

    private fun buildV6RangesLocked(): Map<IpOwner, IpRanges> {
        val ranges = IpOwner.entries.associateWith { owner ->
            val fileName = "${owner.name.lowercase()}_ipv6.txt"
            IpRanges.loadV6FromFile(assetRepository.getFileFromAssetsCache(fileName))
        }
        v6Ranges = ranges
        return ranges
    }
}

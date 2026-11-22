package org.mangoswatch.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.repository.StreamsRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class StreamsRepositoryImpl(
    private val maxStreams: Int = DEFAULT_MAX_STREAMS,
    private val inactiveTimeoutMs: Long = DEFAULT_INACTIVE_TIMEOUT_MS,
    private val clock: () -> Long = { defaultClock() }
) : StreamsRepository {

    private val _streams = ConcurrentHashMap<String, StreamModel>()
    private val evicting = AtomicBoolean(false)

    override fun getStreams(): Flow<List<StreamModel>> {
        return flow {
            while (true) {
                emit(_streams.values.toList())
                delay(500.milliseconds)
            }
        }.distinctUntilChanged()
    }

    override fun updateStream(key: String, transform: (StreamModel?) -> StreamModel?) {
        _streams.compute(key) { _, old -> transform(old) }   // null = no stream (removed if already existing)
        performEviction()
    }

    fun performEviction(now: Long = clock()) {
        if (_streams.size < maxStreams) return
        if (!evicting.compareAndSet(false, true)) return
        try {
            if (_streams.size < maxStreams) return

            val iterator = _streams.entries.iterator()
            while (iterator.hasNext()) {
                val (_, stream) = iterator.next()
                if (now - stream.lastSeen > inactiveTimeoutMs) {
                    iterator.remove()
                }
            }

            if (_streams.size >= maxStreams) {
                val targetSize = if (maxStreams <= 2) maxStreams else (maxStreams * 0.9).toInt().coerceAtLeast(1)
                val overflow = _streams.size - targetSize
                if (overflow > 0) {
                    val keysToRemove = _streams.entries
                        .sortedBy { it.value.lastSeen }
                        .take(overflow)
                        .map { it.key }
                    for (k in keysToRemove) {
                        _streams.remove(k)
                    }
                }
            }
        } finally {
            evicting.set(false)
        }
    }

    companion object {
        const val DEFAULT_MAX_STREAMS = 2000
        const val DEFAULT_INACTIVE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        private fun defaultClock(): Long {
            return try {
                android.os.SystemClock.elapsedRealtime()
            } catch (_: Throwable) {
                System.currentTimeMillis()
            }
        }
    }
}

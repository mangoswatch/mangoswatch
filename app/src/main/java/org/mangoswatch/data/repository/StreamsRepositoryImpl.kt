package org.mangoswatch.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.mangoswatch.domain.model.StreamModel
import org.mangoswatch.domain.repository.StreamsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class StreamsRepositoryImpl(): StreamsRepository {

    private val _streams = ConcurrentHashMap<String, StreamModel>()

    override fun getStreams(): Flow<List<StreamModel>> {
        return flow {
            while (true) {
                emit(_streams.values.toList())
                delay(500.milliseconds)
            }

        }.distinctUntilChanged()
    }

    override fun updateStream(key: String, transform: (StreamModel?) -> StreamModel?) {
        _streams.compute(key) { _, old -> transform(old) }   // null = nessuno stream (se esisteva, viene rimosso)
    }
}
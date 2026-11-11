package org.mangoswatch.domain.repository

import org.mangoswatch.domain.model.StreamModel
import kotlinx.coroutines.flow.Flow

interface StreamsRepository {
    fun getStreams(): Flow<List<StreamModel>>
    fun updateStream(key: String, transform: (StreamModel?) -> StreamModel?)
}
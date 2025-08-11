package com.example.noiceapp.data

import kotlinx.coroutines.flow.Flow

class NoiseRepository(private val dao: NoiseDao) {
    suspend fun save(measurement: NoiseMeasurement) {
        dao.insert(measurement)
    }

    fun observeLast(limit: Int = 300): Flow<List<NoiseMeasurement>> = dao.observeLatest(limit)
}



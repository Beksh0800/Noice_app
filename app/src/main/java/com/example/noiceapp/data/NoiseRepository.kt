package com.example.noiceapp.data

import kotlinx.coroutines.flow.Flow

class NoiseRepository(private val dao: NoiseDao) {
    suspend fun save(measurement: NoiseMeasurement) {
        dao.insert(measurement)
    }

    fun observeLast(limit: Int = 300): Flow<List<NoiseMeasurement>> = dao.observeLatest(limit)

     suspend fun all(): List<NoiseMeasurement> = dao.getAll()

     suspend fun getNotUploaded(): List<NoiseMeasurement> = dao.getNotUploaded()

     suspend fun markUploaded(id: Long) = dao.markUploaded(id)
}

class CitySamplesRepository(private val dao: CitySampleDao) {
    suspend fun add(sample: CitySample) = dao.insert(sample)
    fun observe(): Flow<List<CitySample>> = dao.observeAll()
    suspend fun get(id: Long): CitySample? = dao.getById(id)
}



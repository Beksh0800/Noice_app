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


class NoiseAnalysesRepository(private val dao: NoiseAnalysisDao) {
    suspend fun add(analysis: NoiseAnalysis) = dao.insert(analysis)
    fun observe(): Flow<List<NoiseAnalysis>> = dao.observeAll()
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun count(): Int = dao.count()
    suspend fun totalSamples(): Int = dao.totalSamples()
}

class CityNoiseAnalysisRepository(private val dao: CityNoiseAnalysisDao) {
    suspend fun add(analysis: CityNoiseAnalysis) = dao.insert(analysis)
    fun observe(): Flow<List<CityNoiseAnalysis>> = dao.observeAll()
    fun observeByCity(cityName: String): Flow<List<CityNoiseAnalysis>> = dao.observeByCity(cityName)
    fun observeByCityAndDistrict(cityName: String, district: String): Flow<List<CityNoiseAnalysis>> = dao.observeByCityAndDistrict(cityName, district)
    suspend fun getById(id: Long): CityNoiseAnalysis? = dao.getById(id)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun getAllCities(): List<String> = dao.getAllCities()
    suspend fun getDistrictsByCity(cityName: String): List<String> = dao.getDistrictsByCity(cityName)
    suspend fun getAverageNoiseForCity(cityName: String, since: Long): Double? = dao.getAverageNoiseForCity(cityName, since)
    suspend fun getAnalysisCountForCity(cityName: String): Int = dao.getAnalysisCountForCity(cityName)
}




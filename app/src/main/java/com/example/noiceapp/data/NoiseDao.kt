package com.example.noiceapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoiseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: NoiseMeasurement): Long

    @Query("SELECT * FROM noise_measurements ORDER BY timestampMillis ASC")
    suspend fun getAll(): List<NoiseMeasurement>

    @Query("SELECT * FROM noise_measurements WHERE timestampMillis BETWEEN :from AND :to ORDER BY timestampMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<NoiseMeasurement>>

    @Query("SELECT * FROM noise_measurements ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<NoiseMeasurement>>

    @Query("SELECT * FROM noise_measurements ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<NoiseMeasurement>

    @Query("SELECT * FROM noise_measurements WHERE uploaded = 0 ORDER BY timestampMillis ASC")
    suspend fun getNotUploaded(): List<NoiseMeasurement>

    @Query("UPDATE noise_measurements SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("DELETE FROM noise_measurements")
    suspend fun clearAll()
}

@Dao
interface CitySampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: CitySample): Long

    @Query("SELECT * FROM city_samples ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CitySample>>

    @Query("SELECT * FROM city_samples WHERE id = :id")
    suspend fun getById(id: Long): CitySample?
}


@Dao
interface NoiseAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: NoiseAnalysis): Long

    @Query("SELECT * FROM noise_analyses ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<NoiseAnalysis>>

    @Query("DELETE FROM noise_analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM noise_analyses")
    suspend fun count(): Int

    @Query("SELECT IFNULL(SUM(count),0) FROM noise_analyses")
    suspend fun totalSamples(): Int
}

@Dao
interface CityNoiseAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: CityNoiseAnalysis): Long

    @Query("SELECT * FROM city_noise_analyses ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CityNoiseAnalysis>>

    @Query("SELECT * FROM city_noise_analyses WHERE cityName = :cityName ORDER BY timestampMillis DESC")
    fun observeByCity(cityName: String): Flow<List<CityNoiseAnalysis>>

    @Query("SELECT * FROM city_noise_analyses WHERE cityName = :cityName AND district = :district ORDER BY timestampMillis DESC")
    fun observeByCityAndDistrict(cityName: String, district: String): Flow<List<CityNoiseAnalysis>>

    @Query("SELECT DISTINCT cityName FROM city_noise_analyses ORDER BY cityName")
    suspend fun getAllCities(): List<String>

    @Query("SELECT DISTINCT district FROM city_noise_analyses WHERE cityName = :cityName ORDER BY district")
    suspend fun getDistrictsByCity(cityName: String): List<String>

    @Query("SELECT * FROM city_noise_analyses WHERE id = :id")
    suspend fun getById(id: Long): CityNoiseAnalysis?

    @Query("DELETE FROM city_noise_analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT AVG(avgDb) FROM city_noise_analyses WHERE cityName = :cityName AND timestampMillis >= :since")
    suspend fun getAverageNoiseForCity(cityName: String, since: Long): Double?

    @Query("SELECT COUNT(*) FROM city_noise_analyses WHERE cityName = :cityName")
    suspend fun getAnalysisCountForCity(cityName: String): Int
}




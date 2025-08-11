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

    @Query("SELECT * FROM noise_measurements WHERE timestampMillis BETWEEN :from AND :to ORDER BY timestampMillis ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<NoiseMeasurement>>

    @Query("SELECT * FROM noise_measurements ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<NoiseMeasurement>>

    @Query("SELECT * FROM noise_measurements ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<NoiseMeasurement>

    @Query("DELETE FROM noise_measurements")
    suspend fun clearAll()
}



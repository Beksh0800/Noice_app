package com.example.noiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "noise_measurements")
data class NoiseMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val laeqDb: Double,
    val lamaxDb: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val uploaded: Boolean = false
)



package com.example.noiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сохранённый анализ шумовых измерений (агрегированные метрики за период/сессию).
 */
@Entity(tableName = "noise_analyses")
data class NoiseAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val avgDb: Double,
    val minDb: Double,
    val maxDb: Double,
    val count: Int,
    val lastDb: Double
)
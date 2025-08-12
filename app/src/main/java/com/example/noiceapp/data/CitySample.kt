package com.example.noiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Городская шумовая выборка (агрегированная запись для анализа/прогноза).
 * Это не «сырые» аудио-измерения, а семантические точки с рейтингом 1..10,
 * типом шума и человеко-читаемым описанием локации.
 */
@Entity(tableName = "city_samples")
data class CitySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val locationText: String,
    val noiseType: String,    // пример: Транспорт, Люди, Стройка
    val rating10: Int,        // 1..10
    val note: String? = null
)






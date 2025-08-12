package com.example.noiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Анализ шума в городах Казахстана с возможностью прогнозирования.
 */
@Entity(tableName = "city_noise_analyses")
data class CityNoiseAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val cityName: String,           // Название города (Алматы, Астана, и т.д.)
    val district: String,           // Район города
    val street: String,             // Улица
    val coordinates: String?,       // Координаты в формате "lat,lng"
    val avgDb: Double,              // Средний уровень шума
    val minDb: Double,              // Минимальный уровень
    val maxDb: Double,              // Максимальный уровень
    val peakDb: Double,             // Пиковый уровень
    val measurementCount: Int,      // Количество измерений
    val durationMinutes: Int,       // Продолжительность измерений в минутах
    val noiseType: String,          // Тип шума (транспорт, строительство, люди, и т.д.)
    val timeOfDay: String,          // Время суток (утро, день, вечер, ночь)
    val weatherConditions: String?, // Погодные условия
    val trafficLevel: Int,          // Уровень трафика 1-5
    val notes: String?,             // Дополнительные заметки
    val forecastNextHour: Double?,  // Прогноз на следующий час
    val forecastNextDay: Double?,   // Прогноз на следующий день
    val riskLevel: String           // Уровень риска (низкий, средний, высокий)
)



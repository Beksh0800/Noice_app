package com.example.noiceapp.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Подсчёт LAeq (энергетическое среднее) и LAFmax за блок A-взвешенных отсчётов.
 *
 * Вход:
 * - aWeighted: A-взвешенные линейные отсчёты (не dB)
 * - length: фактическое число отсчётов для расчёта (будет ограничено до размера массива)
 * - reference: опорная амплитуда (1.0 для нормализованных данных ±1.0, либо 32767.0 для 16-bit PCM)
 * - offsetDb: калибровочный сдвиг (для приближения к SPL)
 */
object LeqCalculator {
    /**
     * Результаты: LAeq по блоку и LApeak (максимум по амплитуде в блоке).
     * LAF будет рассчитываться в сервисе через экспоненциальное взвешивание.
     */
    data class Result(val laeqDb: Double, val laPeakDb: Double)

    private const val EPS = 1e-9

    fun computeAWeightedLeqAndPeak(
        aWeighted: DoubleArray,
        length: Int,
        reference: Double = 1.0,
        offsetDb: Double = 0.0
    ): Result {
        val n = length.coerceIn(1, aWeighted.size)

        var sumSquares = 0.0
        var peakAbs = 0.0
        for (i in 0 until n) {
            val s = aWeighted[i]
            sumSquares += s * s
            val abs = kotlin.math.abs(s)
            if (abs > peakAbs) peakAbs = abs
        }

        val rms = sqrt(sumSquares / n)
        val laeq = 20.0 * log10((rms / reference).coerceAtLeast(EPS)) + offsetDb
        val lapeak = 20.0 * log10((peakAbs / reference).coerceAtLeast(EPS)) + offsetDb
        return Result(laeqDb = laeq, laPeakDb = lapeak)
    }
}





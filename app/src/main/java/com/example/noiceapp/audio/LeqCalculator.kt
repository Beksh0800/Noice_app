package com.example.noiceapp.audio

import kotlin.math.log10

/**
 * Подсчёт LAeq (энергетическое среднее) и LAFmax за блок выборок.
 * Принимает уже A-взвешанные отсчёты в Double (после фильтра), базовые единицы – сырые PCM уровни.
 */
object LeqCalculator {
    data class Result(val laeqDb: Double, val lamaxDb: Double)

    fun computeAWeightedLeqAndMax(aWeighted: DoubleArray, length: Int, reference: Double = 32768.0, offsetDb: Double): Result {
        var sumSquares = 0.0
        var peakAbs = 0.0
        for (i in 0 until length) {
            val s = aWeighted[i]
            sumSquares += s * s
            val abs = kotlin.math.abs(s)
            if (abs > peakAbs) peakAbs = abs
        }
        val rms = kotlin.math.sqrt(sumSquares / length)
        val laeq = 20 * log10((rms / reference).coerceAtLeast(1e-12)) + offsetDb
        val lamax = 20 * log10((peakAbs / reference).coerceAtLeast(1e-12)) + offsetDb
        return Result(laeqDb = laeq, lamaxDb = lamax)
    }
}



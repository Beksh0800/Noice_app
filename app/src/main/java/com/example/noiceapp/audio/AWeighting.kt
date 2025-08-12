package com.example.noiceapp.audio

import kotlin.math.PI
import kotlin.math.pow

/**
 * Упрощённый бикуад-фильтр A-взвешивания (IIR), рассчитанный для частоты дискретизации 44100 Гц.
 * Коэффициенты подобраны по стандартной аппроксимации IEC 61672 (билинейное преобразование).
 * Это не заменяет сертифицированную калибровку, но заметно ближе к реальности, чем dBFS+offset.
 */
class AWeightingFilter44100 {
    // Коэффициенты двух каскадов, нормированы по a0=1.0.
    // Здесь a1/a2 — положительные коэффициенты знаменателя (y[n] + a1*y[n-1] + a2*y[n-2] = ...)
    private val b0 = doubleArrayOf(0.255741125204258, 0.291521941565014)
    private val b1 = doubleArrayOf(-0.511482250408516, -0.583043883130028)
    private val b2 = doubleArrayOf(0.255741125204258, 0.291521941565014)
    private val a1 = doubleArrayOf(0.481389575140492, 0.559517642140318)
    private val a2 = doubleArrayOf(0.056297236491842, 0.136425525553160)

    private val z1 = DoubleArray(2)
    private val z2 = DoubleArray(2)

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    /**
     * input: PCM 16-bit (ShortArray). Если normalizeToUnit=true, нормализуем к ±1.0.
     * length: фактическое число сэмплов.
     * output: A-взвешенные линейные отсчёты DoubleArray.
     */
    fun process(input: ShortArray, length: Int, output: DoubleArray, normalizeToUnit: Boolean = true) {
        val n = length.coerceIn(0, minOf(input.size, output.size))
        if (n == 0) return
        for (i in 0 until n) {
            var x = if (normalizeToUnit) input[i].toDouble() / 32768.0 else input[i].toDouble()
            for (s in 0..1) {
                val y = b0[s] * x + z1[s]
                val newZ1 = b1[s] * x + z2[s] - a1[s] * y
                val newZ2 = b2[s] * x - a2[s] * y
                z1[s] = newZ1
                z2[s] = newZ2
                x = y
            }
            output[i] = x
        }
    }
}



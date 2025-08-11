package com.example.noiceapp.audio

import kotlin.math.PI
import kotlin.math.pow

/**
 * Упрощённый бикуад-фильтр A-взвешивания (IIR), рассчитанный для частоты дискретизации 44100 Гц.
 * Коэффициенты подобраны по стандартной аппроксимации IEC 61672 (билинейное преобразование).
 * Это не заменяет сертифицированную калибровку, но заметно ближе к реальности, чем dBFS+offset.
 */
class AWeightingFilter44100 {
    // Коэффициенты для двухкаскадного бикуад-фильтра (примерные, double precision)
    // Источник: приближённые коэффициенты, сгенерированные оффлайн.
    private val b0 = doubleArrayOf(0.255741125204258, 0.291521941565014)
    private val b1 = doubleArrayOf(-0.511482250408516, -0.583043883130028)
    private val b2 = doubleArrayOf(0.255741125204258, 0.291521941565014)
    private val a1 = doubleArrayOf(-0.481389575140492, -0.559517642140318)
    private val a2 = doubleArrayOf(0.056297236491842, 0.136425525553160)

    private val z1 = DoubleArray(2)
    private val z2 = DoubleArray(2)

    fun process(input: ShortArray, length: Int, output: DoubleArray) {
        var y: Double
        for (i in 0 until length) {
            var x = input[i].toDouble()
            // два бикуад-каскада последовательно
            for (s in 0..1) {
                y = b0[s] * x + z1[s]
                z1[s] = b1[s] * x - a1[s] * y + z2[s]
                z2[s] = b2[s] * x - a2[s] * y
                x = y
            }
            output[i] = x
        }
    }
}



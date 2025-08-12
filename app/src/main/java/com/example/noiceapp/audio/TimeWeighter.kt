package com.example.noiceapp.audio

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Экспоненциальное временное взвешивание энергии (FAST/SLOW) для A-взвешенных отсчётов.
 * Поддерживает потоковую обработку: передавайте по одному отсчёту и получайте текущий RMS.
 */
class TimeWeighter(
    sampleRate: Int,
    tauSeconds: Double
) {
    // Коэффициент экспоненциального сглаживания по энергии
    // e[n] = alpha * e[n-1] + (1 - alpha) * x[n]^2
    private val alpha: Double = exp(-1.0 / (sampleRate * tauSeconds))
    private var energy: Double = 0.0

    fun reset() {
        energy = 0.0
    }

    /**
     * Обрабатывает один отсчёт (после A-взвешивания). Возвращает текущее RMS.
     */
    fun processSample(aWeightedSample: Double): Double {
        val x2 = aWeightedSample * aWeightedSample
        energy = alpha * energy + (1.0 - alpha) * x2
        return sqrt(energy)
    }
}




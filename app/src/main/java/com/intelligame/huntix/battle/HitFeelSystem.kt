package com.intelligame.huntix.battle

import android.os.VibrationEffect

/** Effetto di vibrazione richiesto dall'HitFeelSystem. */
data class Vibration(val durationMs: Long)

/** Hit-feel: code di vibrazioni per dare feedback tattile ai colpi. */
class HitFeelSystem {
    private val queue = ArrayDeque<Vibration>()

    /** Accoda una vibrazione di durata data. */
    fun requestHit(strong: Boolean) {
        queue.addLast(Vibration(if (strong) 60L else 25L))
    }

    /** Consuma (se presente) la prossima vibrazione in coda. */
    fun consumeVibration(): Vibration? = queue.removeFirstOrNull()
}

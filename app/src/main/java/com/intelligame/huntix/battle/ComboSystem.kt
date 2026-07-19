package com.intelligame.huntix.battle

/** Sistema combo: tiene traccia di colpi, combo e livello. */
class ComboSystem {
    var currentCombo: Int = 0
        private set
    var comboLevel: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var totalHits: Int = 0
        private set

    /** Registra un colpo riuscito e aggiorna il combo. */
    fun registerHit() {
        totalHits++
        currentCombo++
        if (currentCombo > maxCombo) maxCombo = currentCombo
        comboLevel = (currentCombo / 5).coerceIn(0, 5)
    }

    /** Interrompe il combo (es. dopo un colpo subito). */
    fun breakCombo() {
        currentCombo = 0
        comboLevel = 0
    }

    fun reset() {
        currentCombo = 0
        comboLevel = 0
        maxCombo = 0
        totalHits = 0
    }
}

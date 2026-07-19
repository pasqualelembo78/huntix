package com.intelligame.huntix.battle

/** Controlla il giocatore: posizione sull'arena e stato di difesa. */
class PlayerController {
    var positionX: Float = 0.25f
    var isBlocking: Boolean = false

    fun releaseBlock() { isBlocking = false }
}

package com.intelligame.huntix.battle

/** Risultato di un "tick" di battaglia usato per il calcolo ricompense. */
class BattleEngine(
    val creature: CreatureData,
    val element: ElementType,
    val difficultyScale: Float
) {
    enum class TickResult { ENEMY_DEFEATED, PLAYER_DEFEATED, TIME_UP }
}

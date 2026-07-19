package com.intelligame.huntix.battle

/** Nemico controllato dall'AI. */
class Enemy(
    val creature: CreatureData,
    val element: ElementType,
    val style: AIStyle,
    val difficultyScale: Float
) {
    enum class AIStyle { AGGRESSIVE, BALANCED, DEFENSIVE }

    var positionX: Float = 0.75f
    var hp: Float = 100f * difficultyScale
    val maxHp: Float = hp
}

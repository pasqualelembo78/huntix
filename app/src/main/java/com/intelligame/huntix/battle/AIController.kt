package com.intelligame.huntix.battle

import com.intelligame.huntix.battle.Enemy.AIStyle

/** AI del nemico: decide lo stile di attacco in base all'elemento/rarità. */
class AIController(element: ElementType, style: AIStyle) {
    var animController: AnimationController? = null
    private val aggression = when (style) {
        Enemy.AIStyle.AGGRESSIVE -> 0.9f
        Enemy.AIStyle.BALANCED -> 0.6f
        Enemy.AIStyle.DEFENSIVE -> 0.35f
    }
    /** Moltiplicatore danno base dell'AI. */
    val damageMultiplier: Float = aggression

    companion object {
        /** Deriva uno stile di combattimento dalla rarità della creatura. */
        fun fromRarity(rarityId: String): AIStyle = when (rarityId) {
            "legendary", "epic" -> Enemy.AIStyle.AGGRESSIVE
            "rare", "uncommon" -> Enemy.AIStyle.BALANCED
            else -> Enemy.AIStyle.DEFENSIVE
        }
    }
}

package com.intelligame.huntix.battle

import com.intelligame.huntix.battle.Enemy.AIStyle

class AIController(element: ElementType, style: AIStyle) {
    var animController: AnimationController? = null
    private val aggression = when (style) {
        Enemy.AIStyle.AGGRESSIVE -> 0.9f
        Enemy.AIStyle.BALANCED -> 0.6f
        Enemy.AIStyle.DEFENSIVE -> 0.35f
    }
    val damageMultiplier: Float = aggression

    var attackCooldown: Float = 0f
    var decisionTimer: Float = 0f
    var reactionBonus: Float = when (style) {
        Enemy.AIStyle.AGGRESSIVE -> 0.8f
        Enemy.AIStyle.BALANCED -> 1.0f
        Enemy.AIStyle.DEFENSIVE -> 1.2f
    }

    fun shouldAttack(playerSuperBar: Float, enemyHpRatio: Float): Boolean {
        val blockChance = when {
            playerSuperBar >= 1f -> 0.4f * reactionBonus
            enemyHpRatio < 0.3f -> 0.3f * reactionBonus
            else -> 0.1f
        }
        return Math.random().toFloat() > blockChance
    }

    companion object {
        fun fromRarity(rarityId: String): AIStyle = when (rarityId) {
            "legendary", "epic" -> Enemy.AIStyle.AGGRESSIVE
            "rare", "uncommon" -> Enemy.AIStyle.BALANCED
            else -> Enemy.AIStyle.DEFENSIVE
        }
    }
}

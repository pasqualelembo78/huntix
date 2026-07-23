package com.intelligame.huntix.battle

import com.intelligame.huntix.battle.CharacterRenderer.AnimState

class Enemy(
    val creature: CreatureData,
    val element: ElementType,
    val style: AIStyle,
    val difficultyScale: Float
) {
    enum class AIStyle { AGGRESSIVE, BALANCED, DEFENSIVE }

    var positionX: Float = 0.70f
    var hp: Float = 100f * difficultyScale
    val maxHp: Float = hp
    var attackTimer: Float = 0f
    var hitFlash: Float = 0f

    var animState: AnimState = AnimState.IDLE
        private set
    var animProgress: Float = 0f
    var facing: Int = -1
    var stunTimer: Float = 0f
    var isKO: Boolean = false
    var velocityY: Float = 0f
    var posY: Float = 0f

    var telegraphTimer: Float = 0f
    var telegraphActive: Boolean = false
        private set
    var telegraphType: TelegraphType = TelegraphType.NONE
        private set

    enum class TelegraphType { NONE, WARNING, HEAVY, SPECIAL }

    private var oneShotDone: Boolean = false

    fun setAnimState(state: AnimState, force: Boolean = false) {
        if (state != animState || force) {
            animState = state
            animProgress = 0f
            oneShotDone = false
        }
    }

    fun isAnimFinished(): Boolean = oneShotDone

    fun update(dt: Float) {
        if (stunTimer > 0f) {
            stunTimer -= dt
        }

        val duration = CharacterRenderer.animDuration(animState)
        if (duration > 0f) {
            animProgress += dt / duration
            if (!CharacterRenderer.IDLE.loop && animState != AnimState.IDLE
                && animState != AnimState.BLOCK
            ) {
                if (animProgress >= 1f) {
                    animProgress = 1f
                    oneShotDone = true
                }
            } else {
                animProgress %= 1f
            }
        }

        attackTimer = maxOf(0f, attackTimer - dt)
        hitFlash = maxOf(0f, hitFlash - dt)

        if (telegraphActive) {
            telegraphTimer -= dt
            if (telegraphTimer <= 0f) {
                telegraphActive = false
                telegraphType = TelegraphType.NONE
            }
        }

        if (posY > 0f) {
            velocityY -= 1200f * dt
            posY += velocityY * dt
            if (posY <= 0f) {
                posY = 0f
                velocityY = 0f
            }
        }
    }

    fun isStunned(): Boolean = stunTimer > 0f

    fun startTelegraph(type: TelegraphType, durationSec: Float = 0.4f) {
        telegraphType = type
        telegraphTimer = durationSec
        telegraphActive = true
    }

    fun cancelTelegraph() {
        telegraphActive = false
        telegraphTimer = 0f
        telegraphType = TelegraphType.NONE
    }
}

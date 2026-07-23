package com.intelligame.huntix.battle

import com.intelligame.huntix.battle.CharacterRenderer.AnimState

class PlayerController {
    var positionX: Float = 0.30f
    var isBlocking: Boolean = false
    var attackTimer: Float = 0f
    var hitFlash: Float = 0f
    var blockStamina: Float = 1f

    var creatureData: CreatureData? = null
    var elementType: ElementType = ElementType.NORMAL

    var animState: AnimState = AnimState.IDLE
        private set
    var animProgress: Float = 0f
    var facing: Int = 1
    var stunTimer: Float = 0f
    var superBar: Float = 0f
    var velocityY: Float = 0f
    var posY: Float = 0f
    var isMoving: Boolean = false
    var moveDir: Int = 0

    private var oneShotDone: Boolean = false

    fun setAnimState(state: AnimState, force: Boolean = false) {
        if (state != animState || force) {
            animState = state
            animProgress = 0f
            oneShotDone = false
        }
    }

    fun isAnimFinished(): Boolean = oneShotDone

    fun releaseBlock() {
        isBlocking = false
    }

    fun update(dt: Float) {
        if (stunTimer > 0f) {
            stunTimer -= dt
        }

        if (isBlocking) {
            blockStamina = (blockStamina - dt * 0.15f).coerceAtLeast(0f)
            if (blockStamina <= 0f) {
                releaseBlock()
                stunTimer = 0.2f
            }
        } else {
            blockStamina = (blockStamina + dt * 0.08f).coerceAtMost(1f)
        }

        val duration = CharacterRenderer.animDuration(animState)
        if (duration > 0f) {
            animProgress += dt / duration
            if (!CharacterRenderer.IDLE.loop && animState != AnimState.IDLE
                && animState != AnimState.WALK && animState != AnimState.BLOCK
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
}

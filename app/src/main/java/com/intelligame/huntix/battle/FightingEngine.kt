package com.intelligame.huntix.battle

import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.battle.CharacterRenderer.AnimState
import kotlin.math.max
import kotlin.random.Random

class FightingEngine(
    private val player: PlayerController,
    private val enemy: Enemy,
    private val ai: AIController,
    private val combo: ComboSystem,
    private val combat: CombatSystem,
    private val hitFeel: HitFeelSystem,
    private val durationMs: Long
) {
    enum class GameState { COUNTDOWN, FIGHTING, PAUSED, ENDED }
    enum class BattleEventType {
        CRIT, HIT, SPECIAL, ENEMY_HIT, ROUND_END, COUNTDOWN,
        PLAYER_STUN, ENEMY_STUN, KO, SUPER_READY
    }
    enum class BattleResult { PLAYER_WIN, ENEMY_WIN, DRAW }

    var animController: AnimationController? = null
    var impactEffects: ImpactEffects? = null
    var onBattleEvent: ((BattleEventType, String) -> Unit)? = null

    val playerController: PlayerController get() = player
    val enemyUnit: Enemy get() = enemy

    var gameState: GameState = GameState.COUNTDOWN
        private set
    var battleResult: BattleResult = BattleResult.DRAW
        private set
    var totalPlayerDamage: Int = 0
        private set
    var timeRemainingMs: Long = durationMs
        private set
    var currentRound: Int = 1
        private set
    var maxRounds: Int = 3
        private set
    var playerRoundWins: Int = 0
        private set
    var enemyRoundWins: Int = 0
        private set

    var lastPlayerDamage: Int = 0
        private set
    var lastEnemyDamage: Int = 0
        private set
    var playerMaxHp: Float = 100f
        private set
    var enemyBlocking: Boolean = false
        private set

    val playerHpRatio: Float get() = (playerHp / playerMaxHp).coerceIn(0f, 1f)
    val enemyHpRatio: Float get() = (enemyHp / enemy.maxHp).coerceIn(0f, 1f)

    private var playerHp = 100f
    private var enemyHp = enemy.hp
    private var startTime = 0L
    private var aiDecisionTimer = 0L
    private var aiActionCooldown = 0f
    private var aiBlockTimer = 0f
    private var aiTelegraphTimer = 0f
    private var aiPendingAttack = false
    private var gameTime = 0f
    private val handler = Handler(Looper.getMainLooper())

    private val PLAYER_ATTACK_COOLDOWN = 0.22f
    private val HEAVY_ATTACK_COOLDOWN = 0.38f
    private val SPECIAL_ATTACK_COOLDOWN = 0.5f
    private val ENEMY_ATTACK_COOLDOWN = 0.4f
    private val KNOCKBACK_FORCE = 0.02f
    private val STUN_DURATION_LIGHT = 0.15f
    private val STUN_DURATION_HEAVY = 0.3f
    private val STUN_DURATION_SPECIAL = 0.5f
    private val BLOCK_STAMINA_COST = 0.12f

    private fun elementMultiplier(atkElement: ElementType, defElement: ElementType): Float {
        if (atkElement == ElementType.NORMAL || defElement == ElementType.NORMAL) return 1f
        return when (atkElement) {
            ElementType.FIRE -> if (defElement == ElementType.AIR) 1.3f else if (defElement == ElementType.WATER) 0.7f else 1f
            ElementType.AIR -> if (defElement == ElementType.EARTH) 1.3f else if (defElement == ElementType.FIRE) 0.7f else 1f
            ElementType.WATER -> if (defElement == ElementType.FIRE) 1.3f else if (defElement == ElementType.EARTH) 0.7f else 1f
            ElementType.EARTH -> if (defElement == ElementType.WATER) 1.3f else if (defElement == ElementType.AIR) 0.7f else 1f
            else -> 1f
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (gameState != GameState.FIGHTING) return
            val slowMo = impactEffects?.currentSlowMoFactor() ?: 1f
            step(0.016f * slowMo)
            handler.postDelayed(this, 16)
        }
    }

    fun startCountdown() {
        gameState = GameState.COUNTDOWN
        if (currentRound == 1) {
            playerMaxHp = 80f + (player.creatureData?.power ?: 0L) * 0.02f
            playerHp = playerMaxHp
            player.blockStamina = 1f
            enemyHp = enemy.hp
            enemyBlocking = false
            aiBlockTimer = 0f
            totalPlayerDamage = 0
        }
        onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "3")
        handler.postDelayed({
            onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "2")
        }, 500)
        handler.postDelayed({
            onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "1")
        }, 1000)
        handler.postDelayed({
            startTime = System.currentTimeMillis()
            gameTime = 0f
            gameState = GameState.FIGHTING
            player.setAnimState(AnimState.IDLE, true)
            enemy.setAnimState(AnimState.IDLE, true)
            onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "COMBATTI!")
            handler.postDelayed({
                onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "")
            }, 800)
            handler.post(tick)
        }, 1500)
    }

    fun pause() {
        if (gameState == GameState.FIGHTING) gameState = GameState.PAUSED
    }

    fun resume() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.FIGHTING
            handler.post(tick)
        }
    }

    private fun step(dt: Float) {
        gameTime += dt

        player.update(dt)
        enemy.update(dt)
        impactEffects?.update(dt)

        val pxNorm = player.positionX
        val exNorm = enemy.positionX
        player.facing = if (pxNorm <= exNorm) 1 else -1
        enemy.facing = if (exNorm <= pxNorm) 1 else -1

        val minDist = 0.2f
        val overlap = minDist - (exNorm - pxNorm)
        if (overlap > 0f) {
            player.positionX = (player.positionX - overlap * 0.5f).coerceAtLeast(0.08f)
            enemy.positionX = (enemy.positionX + overlap * 0.5f).coerceAtMost(0.82f)
        }

        if (!player.isMoving && !player.isStunned() && !player.isBlocking
            && player.animState != AnimState.KO
        ) {
            if (player.isAnimFinished() || player.animState == AnimState.LIGHT_ATTACK
                || player.animState == AnimState.HEAVY_ATTACK
                || player.animState == AnimState.SPECIAL_ATTACK
                || player.animState == AnimState.HIT_REACT
            ) {
                player.setAnimState(AnimState.IDLE)
            }
        }

        if (!enemy.isStunned() && !enemy.isKO && enemy.animState != AnimState.KO) {
            if (enemy.isAnimFinished() || enemy.animState == AnimState.LIGHT_ATTACK
                || enemy.animState == AnimState.HEAVY_ATTACK
                || enemy.animState == AnimState.HIT_REACT
            ) {
                enemy.setAnimState(AnimState.IDLE)
            }
        }

        timeRemainingMs = max(0L, durationMs - (System.currentTimeMillis() - startTime))

        aiDecisionTimer -= (dt * 1000).toLong()
        aiActionCooldown -= dt

        if (aiTelegraphTimer > 0f) {
            aiTelegraphTimer -= dt
            if (aiTelegraphTimer <= 0f && aiPendingAttack) {
                aiPendingAttack = false
                executeEnemyAttack()
            }
        }

        if (aiDecisionTimer <= 0 && !enemy.isStunned() && !enemy.isKO && !enemy.telegraphActive) {
            aiDecisionTimer = Random.nextLong(600, 1400)
            queueEnemyAttack()
        }

        if (player.isStunned() && player.animState != AnimState.HIT_REACT
            && player.animState != AnimState.KO
        ) {
            player.setAnimState(AnimState.HIT_REACT)
        }

        if (player.animState == AnimState.HIT_REACT && player.isAnimFinished()) {
            player.setAnimState(AnimState.IDLE)
        }

        if (enemy.animState == AnimState.HIT_REACT && enemy.isAnimFinished()) {
            enemy.setAnimState(AnimState.IDLE)
        }

        if (timeRemainingMs <= 0L) {
            battleResult = when {
                playerRoundWins > enemyRoundWins -> BattleResult.PLAYER_WIN
                enemyRoundWins > playerRoundWins -> BattleResult.ENEMY_WIN
                playerHp > enemyHp -> BattleResult.PLAYER_WIN
                enemyHp > playerHp -> BattleResult.ENEMY_WIN
                else -> BattleResult.DRAW
            }
            finish()
        }
    }

    private fun queueEnemyAttack() {
        if (aiActionCooldown > 0f) return
        aiPendingAttack = true
        val telegraphDuration = 0.35f + Random.nextFloat() * 0.15f
        aiTelegraphTimer = telegraphDuration
        val tType = when {
            player.superBar >= 1f && Random.nextFloat() < 0.3f -> Enemy.TelegraphType.SPECIAL
            Random.nextFloat() < 0.2f -> Enemy.TelegraphType.HEAVY
            else -> Enemy.TelegraphType.WARNING
        }
        enemy.startTelegraph(tType, telegraphDuration)
        onBattleEvent?.invoke(BattleEventType.ENEMY_STUN, "telegraph")
    }

    private fun executeEnemyAttack() {
        performEnemyAttack()
    }

    private fun performEnemyAttack() {
        if (aiActionCooldown > 0f) return
        if (enemy.attackTimer > 0f) return

        if (enemyBlocking) {
            aiBlockTimer -= 0.016f
            if (aiBlockTimer <= 0f) {
                enemyBlocking = false
                enemy.setAnimState(AnimState.IDLE, true)
            }
            return
        }

        val shouldBlock = player.superBar >= 1f && ai.shouldAttack(player.superBar, enemyHp / enemy.maxHp)
        if (shouldBlock) {
            enemyBlocking = true
            aiBlockTimer = 0.8f
            enemy.setAnimState(AnimState.BLOCK, true)
            enemy.attackTimer = 0.3f
            return
        }

        val isCrit = Random.nextFloat() < 0.12f * enemy.difficultyScale
        val telegraphBonus = when (enemy.telegraphType) {
            Enemy.TelegraphType.SPECIAL -> 2.5f
            Enemy.TelegraphType.HEAVY -> 1.6f
            else -> 1.0f
        }
        val baseDmg = (Random.nextFloat() * 7f + 4f) * ai.damageMultiplier * enemy.difficultyScale * telegraphBonus
        var dmg = baseDmg
        if (player.isBlocking) dmg *= 0.25f
        if (isCrit) dmg *= 2f
        dmg *= elementMultiplier(enemy.element, player.elementType)

        playerHp = max(0f, playerHp - dmg)
        lastEnemyDamage = dmg.toInt()
        combo.breakCombo()
        hitFeel.requestHit(isCrit)
        enemy.attackTimer = ENEMY_ATTACK_COOLDOWN
        player.hitFlash = 0.2f
        aiActionCooldown = ENEMY_ATTACK_COOLDOWN

        val attackAnim = when (enemy.telegraphType) {
            Enemy.TelegraphType.SPECIAL -> AnimState.SPECIAL_ATTACK
            Enemy.TelegraphType.HEAVY -> AnimState.HEAVY_ATTACK
            else -> AnimState.LIGHT_ATTACK
        }
        enemy.setAnimState(attackAnim)
        enemy.cancelTelegraph()

        if (!player.isBlocking) {
            player.stunTimer = if (isCrit) STUN_DURATION_HEAVY else STUN_DURATION_LIGHT
            player.setAnimState(AnimState.HIT_REACT)
            val kbDir = if (player.positionX > enemy.positionX) 1 else -1
            player.positionX = (player.positionX + kbDir * KNOCKBACK_FORCE * (if (isCrit) 1.5f else 1f)).coerceIn(0.08f, 0.82f)
        }

        val px = player.positionX
        impactEffects?.spawnSparks(px * 1000f, 0f, android.graphics.Color.parseColor("#FF6644"), if (isCrit) 15 else 8)
        if (isCrit) {
            impactEffects?.requestCameraShake(6f, 0.25f)
            impactEffects?.requestScreenFlash(android.graphics.Color.RED, 0.3f)
            impactEffects?.spawnMotionBlurLines(px * 400f, 0f, -player.facing, android.graphics.Color.parseColor("#FF4444"), 5)
        } else {
            impactEffects?.requestCameraShake(3f, 0.12f)
        }

        combat.log("enemy hit -${dmg.toInt()}${if (isCrit) " CRIT" else ""}")
        onBattleEvent?.invoke(
            if (isCrit) BattleEventType.CRIT else BattleEventType.ENEMY_HIT,
            "-${dmg.toInt()}"
        )
        animController?.spawnDamageNumber(
            player.positionX * 400f, 200f,
            dmg.toInt(), isCrit
        )

        if (player.isBlocking) {
            player.blockStamina = (player.blockStamina - BLOCK_STAMINA_COST).coerceAtLeast(0f)
            if (player.blockStamina <= 0f) {
                player.releaseBlock()
                player.stunTimer = STUN_DURATION_LIGHT
            }
        }

        if (playerHp <= 0f) {
            player.setAnimState(AnimState.KO, true)
            enemy.setAnimState(AnimState.VICTORY, true)
            impactEffects?.requestCameraShake(12f, 0.6f)
            impactEffects?.requestSlowMo(0.3f, 1.5f)
            impactEffects?.requestScreenFlash(android.graphics.Color.RED, 0.5f)
            onBattleEvent?.invoke(BattleEventType.KO, "K.O.!")
            enemyRoundWins++
            handler.postDelayed({
                checkRoundOrFinish()
            }, 2000)
        }
    }

    fun onPlayerLightAttack() {
        if (gameState != GameState.FIGHTING || player.isStunned()) return
        if (player.attackTimer > 0f) return
        if (player.isBlocking) player.releaseBlock()
        player.setAnimState(AnimState.LIGHT_ATTACK)
        player.isMoving = false
        performPlayerAttack(8f, 0.2f, BattleEventType.HIT)
    }

    fun onPlayerHeavyAttack() {
        if (gameState != GameState.FIGHTING || player.isStunned()) return
        if (player.attackTimer > 0f) return
        if (player.isBlocking) player.releaseBlock()
        player.setAnimState(AnimState.HEAVY_ATTACK)
        player.isMoving = false
        performPlayerAttack(16f, 0.35f, BattleEventType.HIT)
        impactEffects?.requestCameraShake(5f, 0.2f)
    }

    fun onPlayerSpecialAttack() {
        if (gameState != GameState.FIGHTING || player.isStunned()) return
        if (player.attackTimer > 0f) return
        if (player.superBar < 1f) return
        if (player.isBlocking) player.releaseBlock()
        player.superBar = 0f
        player.setAnimState(AnimState.SPECIAL_ATTACK)
        player.isMoving = false
        performPlayerAttack(32f, 0.5f, BattleEventType.SPECIAL)
        impactEffects?.requestCameraShake(10f, 0.35f)
        impactEffects?.requestScreenFlash(android.graphics.Color.parseColor("#AA00FF"), 0.25f)
        impactEffects?.spawnGroundRing(
            enemy.positionX * 400f, 0f,
            android.graphics.Color.parseColor("#AA00FF"), 80f
        )
    }

    fun onPlayerBlock() {
        if (gameState != GameState.FIGHTING || player.isStunned()) return
        if (player.blockStamina <= 0f) return
        player.isBlocking = true
        player.setAnimState(AnimState.BLOCK)
        player.isMoving = false
    }

    fun onPlayerMoveDirection(dir: Int) {
        if (gameState != GameState.FIGHTING || player.isStunned()) return
        if (player.isBlocking) player.releaseBlock()

        when (dir) {
            6 -> player.positionX = (player.positionX + 0.012f).coerceAtMost(0.82f)
            4 -> player.positionX = (player.positionX - 0.012f).coerceAtLeast(0.08f)
            2 -> {
                if (player.posY == 0f) {
                    player.velocityY = 500f
                    player.posY = 1f
                }
            }
            8 -> {}
            else -> {}
        }

        player.isMoving = dir != 0
        player.moveDir = dir
        if (dir != 0 && player.animState != AnimState.WALK
            && player.animState != AnimState.LIGHT_ATTACK
            && player.animState != AnimState.HEAVY_ATTACK
            && player.animState != AnimState.SPECIAL_ATTACK
        ) {
            player.setAnimState(AnimState.WALK)
        }
    }

    fun onPlayerStopMoving() {
        player.isMoving = false
        player.moveDir = 0
        if (player.animState == AnimState.WALK) {
            player.setAnimState(AnimState.IDLE)
        }
    }

    private fun performPlayerAttack(base: Float, critChance: Float, type: BattleEventType) {
        if (gameState != GameState.FIGHTING) return
        val isEnemyBlocking = enemyBlocking
        val crit = Random.nextFloat() < critChance
        var dmg = base + combo.comboLevel * 2f
        if (crit) dmg *= 2f
        if (isEnemyBlocking) dmg *= 0.25f
        dmg *= elementMultiplier(player.elementType, enemy.element)

        enemyHp = max(0f, enemyHp - dmg)
        lastPlayerDamage = dmg.toInt()
        totalPlayerDamage += dmg.toInt()
        combo.registerHit()
        hitFeel.requestHit(crit)
        animController?.showComboCounter(combo.currentCombo, combo.comboLevel)
        combat.log("player -${dmg.toInt()}${if (crit) " CRIT" else ""}")

        val cooldown = when (type) {
            BattleEventType.SPECIAL -> SPECIAL_ATTACK_COOLDOWN
            BattleEventType.HIT -> {
                if (player.animState == AnimState.HEAVY_ATTACK) HEAVY_ATTACK_COOLDOWN
                else PLAYER_ATTACK_COOLDOWN
            }
            else -> PLAYER_ATTACK_COOLDOWN
        }
        player.attackTimer = cooldown
        enemy.hitFlash = 0.18f

        if (!isEnemyBlocking) {
            val stunDur = when (type) {
                BattleEventType.SPECIAL -> STUN_DURATION_SPECIAL
                BattleEventType.HIT -> {
                    if (player.animState == AnimState.HEAVY_ATTACK) STUN_DURATION_HEAVY
                    else STUN_DURATION_LIGHT
                }
                else -> STUN_DURATION_LIGHT
            }
            enemy.stunTimer = stunDur
            val kbDir = if (enemy.positionX > player.positionX) 1 else -1
            val kbForce = when (type) {
                BattleEventType.SPECIAL -> KNOCKBACK_FORCE * 2f
                BattleEventType.HIT -> {
                    if (player.animState == AnimState.HEAVY_ATTACK) KNOCKBACK_FORCE * 1.5f
                    else KNOCKBACK_FORCE
                }
                else -> KNOCKBACK_FORCE
            }
            enemy.positionX = (enemy.positionX + kbDir * kbForce).coerceIn(0.08f, 0.82f)
        }

        val superGain = if (crit) 0.15f else 0.05f
        player.superBar = (player.superBar + superGain).coerceAtMost(1f)
        if (player.superBar >= 1f && superGain > 0f) {
            onBattleEvent?.invoke(BattleEventType.SUPER_READY, "SUPER!")
        }

        val ex = enemy.positionX
        val ey = 0f
        val sparkColor = when (type) {
            BattleEventType.SPECIAL -> android.graphics.Color.parseColor("#E879F9")
            else -> if (crit) android.graphics.Color.parseColor("#FFD700") else android.graphics.Color.parseColor("#66FF88")
        }
        impactEffects?.spawnSparks(ex * 400f, ey, sparkColor, if (crit) 18 else 10, if (crit) 10f else 6f)

        if (crit) {
            impactEffects?.requestCameraShake(7f, 0.3f)
            impactEffects?.requestScreenFlash(android.graphics.Color.parseColor("#FFD700"), 0.15f)
            impactEffects?.spawnMotionBlurLines(ex * 400f, ey, -enemy.facing, sparkColor, 6)
        } else if (type == BattleEventType.SPECIAL) {
            impactEffects?.spawnGroundRing(ex * 400f, ey, sparkColor, 70f)
        } else {
            impactEffects?.requestCameraShake(2.5f, 0.1f)
            impactEffects?.spawnMotionBlurLines(ex * 400f, ey, -enemy.facing, sparkColor, 3)
        }

        if (crit && !isEnemyBlocking) {
            enemy.setAnimState(AnimState.HIT_REACT, true)
        } else if (isEnemyBlocking) {
            impactEffects?.spawnSparks(ex * 400f, ey, android.graphics.Color.parseColor("#6080FF"), 5, 4f)
        }

        onBattleEvent?.invoke(if (crit) BattleEventType.CRIT else type, "-${dmg.toInt()}")

        if (isEnemyBlocking) {
            enemyBlocking = false
        }

        if (enemyHp <= 0f) {
            enemy.setAnimState(AnimState.KO, true)
            enemy.isKO = true
            player.setAnimState(AnimState.VICTORY, true)
            impactEffects?.requestCameraShake(14f, 0.7f)
            impactEffects?.requestSlowMo(0.25f, 1.8f)
            impactEffects?.requestScreenFlash(android.graphics.Color.WHITE, 0.4f)
            impactEffects?.showStunStars(5, enemy.positionX * 400f, 0f)
            impactEffects?.spawnGroundRing(ex * 400f, ey, android.graphics.Color.parseColor("#FFD700"), 100f)
            onBattleEvent?.invoke(BattleEventType.KO, "K.O.!")
            playerRoundWins++
            handler.postDelayed({
                checkRoundOrFinish()
            }, 2500)
        }
    }

    private fun checkRoundOrFinish() {
        if (playerRoundWins >= (maxRounds + 1) / 2 || enemyRoundWins >= (maxRounds + 1) / 2) {
            battleResult = when {
                playerRoundWins > enemyRoundWins -> BattleResult.PLAYER_WIN
                enemyRoundWins > playerRoundWins -> BattleResult.ENEMY_WIN
                else -> BattleResult.DRAW
            }
            finish()
            return
        }

        currentRound++
        playerHp = playerMaxHp
        enemyHp = enemy.maxHp
        enemy.isKO = false
        player.hitFlash = 0f
        enemy.hitFlash = 0f
        player.stunTimer = 0f
        enemy.stunTimer = 0f
        player.positionX = 0.30f
        enemy.positionX = 0.70f
        player.setAnimState(AnimState.IDLE, true)
        enemy.setAnimState(AnimState.IDLE, true)
        combo.reset()
        impactEffects?.reset()
        impactEffects?.clearStunStars()
        enemyBlocking = false
        aiBlockTimer = 0f
        player.blockStamina = 1f

        gameState = GameState.COUNTDOWN
        onBattleEvent?.invoke(BattleEventType.ROUND_END, "Round $currentRound")
        startCountdown()
    }

    private fun finish() {
        gameState = GameState.ENDED
        handler.removeCallbacks(tick)
        onBattleEvent?.invoke(BattleEventType.ROUND_END, "Fine")
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }
}

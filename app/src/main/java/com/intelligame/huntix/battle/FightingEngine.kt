package com.intelligame.huntix.battle

import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.random.Random

/**
 * FightingEngine — motore di combattimento semplificato stile arcade.
 * Gestisce HP, loop di gioco, input del giocatore, AI del nemico ed eventi.
 */
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
    enum class BattleEventType { CRIT, HIT, SPECIAL, ENEMY_HIT, ROUND_END, COUNTDOWN }
    enum class BattleResult { PLAYER_WIN, ENEMY_WIN, DRAW }

    var animController: AnimationController? = null
    var onBattleEvent: ((BattleEventType, String) -> Unit)? = null

    /** Accesso in sola lettura per la UI/arena. */
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
    val playerHpRatio: Float get() = (playerHp / 100f).coerceIn(0f, 1f)
    val enemyHpRatio: Float get() = (enemyHp / enemy.maxHp).coerceIn(0f, 1f)

    private var playerHp = 100f
    private var enemyHp = enemy.hp
    private var startTime = 0L
    private var aiCooldown = 800L
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (gameState != GameState.FIGHTING) return
            step()
            handler.postDelayed(this, 16)
        }
    }

    fun startCountdown() {
        gameState = GameState.COUNTDOWN
        onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "Pronti...")
        handler.postDelayed({
            startTime = System.currentTimeMillis()
            gameState = GameState.FIGHTING
            onBattleEvent?.invoke(BattleEventType.COUNTDOWN, "Combatti!")
            handler.post(tick)
        }, 1400)
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

    private fun step() {
        // Animazione timer
        player.attackTimer = max(0f, player.attackTimer - 0.016f)
        player.hitFlash = max(0f, player.hitFlash - 0.016f)
        enemy.attackTimer = max(0f, enemy.attackTimer - 0.016f)
        enemy.hitFlash = max(0f, enemy.hitFlash - 0.016f)
        timeRemainingMs = max(0L, durationMs - (System.currentTimeMillis() - startTime))
        // IA nemica
        aiCooldown -= 16
        if (aiCooldown <= 0) {
            aiCooldown = Random.nextLong(700, 1500)
            val raw = Random.nextFloat() * 8f + 5f
            var dmg = raw * ai.damageMultiplier * enemy.difficultyScale
            if (player.isBlocking) dmg *= 0.3f
            playerHp = max(0f, playerHp - dmg)
            combo.breakCombo()
            hitFeel.requestHit(false)
            enemy.attackTimer = 0.25f
            player.hitFlash = 0.15f
            onBattleEvent?.invoke(BattleEventType.ENEMY_HIT, "Subito -${dmg.toInt()}")
        }
        // Tempo scaduto
        if (System.currentTimeMillis() - startTime >= durationMs) {
            battleResult = when {
                enemyHp < playerHp -> BattleResult.PLAYER_WIN
                playerHp < enemyHp -> BattleResult.ENEMY_WIN
                else -> BattleResult.DRAW
            }
            finish()
        }
    }

    // ─── Input giocatore ───────────────────────────────────────
    fun onPlayerMoveForward() { player.positionX = (player.positionX + 0.02f).coerceAtMost(0.85f) }
    fun onPlayerMoveBackward() { player.positionX = (player.positionX - 0.02f).coerceAtLeast(0.05f) }
    fun onPlayerStopMoving() {}
    fun onPlayerJump() {}
    fun onPlayerBlock() { player.isBlocking = true }

    fun onPlayerLightAttack() = attack(8f, 0.2f, BattleEventType.HIT)
    fun onPlayerHeavyAttack() = attack(16f, 0.35f, BattleEventType.HIT)
    fun onPlayerSpecialAttack() {
        if (gameState != GameState.FIGHTING) return
        attack(32f, 0.5f, BattleEventType.SPECIAL)
    }

    private fun attack(base: Float, critChance: Float, type: BattleEventType) {
        if (gameState != GameState.FIGHTING) return
        val crit = Random.nextFloat() < critChance
        var dmg = base + combo.comboLevel * 2f
        if (crit) dmg *= 2f
        enemyHp = max(0f, enemyHp - dmg)
        totalPlayerDamage += dmg.toInt()
        combo.registerHit()
        hitFeel.requestHit(crit)
        animController?.showComboCounter(combo.currentCombo, combo.comboLevel)
        combat.log("player -${dmg.toInt()}")
        player.attackTimer = if (type == BattleEventType.SPECIAL) 0.4f else 0.22f
        enemy.hitFlash = 0.15f
        onBattleEvent?.invoke(if (crit) BattleEventType.CRIT else type, "Colpito -${dmg.toInt()}")
        if (enemyHp <= 0f) {
            battleResult = BattleResult.PLAYER_WIN
            finish()
        }
    }

    private fun finish() {
        gameState = GameState.ENDED
        handler.removeCallbacks(tick)
        onBattleEvent?.invoke(BattleEventType.ROUND_END, "Fine")
    }
}

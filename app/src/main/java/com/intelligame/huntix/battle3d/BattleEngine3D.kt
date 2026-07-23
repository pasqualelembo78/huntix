package com.intelligame.huntix.battle3d

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BattleEngine3D(
    val playerFighter: FighterDef,
    val enemyFighter: FighterDef,
    val durationMs: Long = 99000L
) {
    enum class GameState { COUNTDOWN, FIGHTING, PAUSED, ENDED }
    enum class BattleResult { PLAYER_WIN, ENEMY_WIN, DRAW }
    enum class AnimEvent { IDLE, WALK, PUNCH, KICK, SPECIAL, HIT, BLOCK, KO, VICTORY }

    var gameState = GameState.COUNTDOWN
        private set
    var battleResult = BattleResult.DRAW
        private set
    var timeRemainingMs = durationMs
        private set
    var playerRoundWins = 0
        private set
    var enemyRoundWins = 0
        private set
    var currentRound = 1
    var maxRounds = 3

    val player: FighterState
    val enemy: FighterState

    var playerAnimEvent: AnimEvent = AnimEvent.IDLE
        private set
    var enemyAnimEvent: AnimEvent = AnimEvent.IDLE
        private set

    var onCountdown: ((String) -> Unit)? = null
    var onDamageDealt: ((Boolean, Int, Boolean) -> Unit)? = null
    var onKO: ((Boolean) -> Unit)? = null
    var onRoundEnd: ((Int) -> Unit)? = null
    var onBattleEnd: ((BattleResult) -> Unit)? = null
    var onSuperReady: ((Boolean) -> Unit)? = null
    var onComboUpdate: ((Int, Int) -> Unit)? = null
    var onEnergyWave: ((Boolean) -> Unit)? = null
    var onCameraShake: ((Float, Float) -> Unit)? = null

    private var gameTime = 0f
    private var aiDecisionTimer = 0L
    private var aiActionCooldown = 0f
    private var aiStyle: Int = Random.nextInt(3)
    private var aiAggressiveness = 0.5f + Random.nextFloat() * 0.5f

    companion object {
        const val STAGE_LEFT = 0.08f
        const val STAGE_RIGHT = 0.92f
        const val MIN_DIST = 0.25f
        const val FIGHT_CENTER = 0.5f
        const val PUNCH_DIST = 0.30f
        const val KICK_DIST = 0.35f
        const val SPECIAL_DIST = 0.50f
        const val KNOCKBACK = 0.04f
        const val KNOCKBACK_HEAVY = 0.07f
        const val KNOCKBACK_SPECIAL = 0.12f

        const val STUN_LIGHT = 0.15f
        const val STUN_HEAVY = 0.30f
        const val STUN_SPECIAL = 0.50f
    }

    init {
        player = createState(playerFighter, FIGHT_CENTER - 0.28f, 1)
        enemy = createState(enemyFighter, FIGHT_CENTER + 0.28f, -1)
    }

    private fun createState(f: FighterDef, x: Float, facing: Int) = FighterState(
        hp = f.baseHp, maxHp = f.baseHp, x = x, facing = facing
    )

    private var tickCount = 0L
    private var tickHandler: (() -> Unit)? = null

    fun setTickHandler(h: () -> Unit) { tickHandler = h }

    fun startCountdown() {
        gameState = GameState.COUNTDOWN
        resetFighters()
        onCountdown?.invoke("3")
        schedule(500) { onCountdown?.invoke("2") }
        schedule(1000) { onCountdown?.invoke("1") }
        schedule(1500) {
            gameState = GameState.FIGHTING
            gameTime = 0f
            onCountdown?.invoke("COMBATTI!")
            schedule(800) { onCountdown?.invoke("") }
        }
    }

    fun tick(dt: Float) {
        if (gameState != GameState.FIGHTING) return
        gameTime += dt
        tickCount++

        player.animTime += dt
        enemy.animTime += dt
        player.stunTimer = max(0f, player.stunTimer - dt)
        enemy.stunTimer = max(0f, enemy.stunTimer - dt)
        player.attackTimer = max(0f, player.attackTimer - dt)
        enemy.attackTimer = max(0f, enemy.attackTimer - dt)
        player.specialCooldown = max(0f, player.specialCooldown - dt)
        enemy.specialCooldown = max(0f, enemy.specialCooldown - dt)

        updateGravity(player, dt)
        updateGravity(enemy, dt)
        resolveCollision()
        updateFacing(player, enemy)
        updateAnimations()

        timeRemainingMs = max(0L, durationMs - (tickCount * 16))

        aiDecisionTimer -= 16
        aiActionCooldown -= dt

        if (aiDecisionTimer <= 0 && enemy.canAct() && !enemy.isKO) {
            aiDecisionTimer = Random.nextLong(400, 1200)
            aiDecide()
        }

        if (timeRemainingMs <= 0L) {
            battleResult = when {
                playerRoundWins > enemyRoundWins -> BattleResult.PLAYER_WIN
                enemyRoundWins > playerRoundWins -> BattleResult.ENEMY_WIN
                player.hp > enemy.hp -> BattleResult.PLAYER_WIN
                enemy.hp > player.hp -> BattleResult.ENEMY_WIN
                else -> BattleResult.DRAW
            }
            endBattle()
        }

        tickHandler?.invoke()
    }

    fun playerMove(dir: Int) {
        if (gameState != GameState.FIGHTING || player.isStunned() || player.isKO) return
        if (player.isBlocking) player.isBlocking = false
        val spd = 0.012f * playerFighter.speed
        when (dir) {
            1 -> player.x = min(STAGE_RIGHT, player.x + spd * enemy.facing)
            -1 -> player.x = max(STAGE_LEFT, player.x - spd * enemy.facing)
        }
        player.isMoving = dir != 0
    }

    fun playerStop() {
        player.isMoving = false
    }

    fun playerPunch() {
        if (gameState != GameState.FIGHTING || !player.canAct() || player.isKO) return
        if (player.isBlocking) player.isBlocking = false
        playerAnimEvent = AnimEvent.PUNCH
        player.animTime = 0f
        player.attackTimer = 0.22f
        val dist = enemy.x - player.x
        if (dist * player.facing > 0 && dist * enemy.facing < 0 && kotlin.math.abs(dist) <= PUNCH_DIST) {
            if (enemy.isBlocking) applyBlockedDamage(player, enemy, 8f)
            else applyDamage(player, enemy, 8f + player.comboCount * 1.5f, 1f)
        }
    }

    fun playerKick() {
        if (gameState != GameState.FIGHTING || !player.canAct() || player.isKO) return
        if (player.isBlocking) player.isBlocking = false
        playerAnimEvent = AnimEvent.KICK
        player.animTime = 0f
        player.attackTimer = 0.35f
        val dist = enemy.x - player.x
        if (dist * player.facing > 0 && dist * enemy.facing < 0 && kotlin.math.abs(dist) <= KICK_DIST) {
            if (enemy.isBlocking) applyBlockedDamage(player, enemy, 16f)
            else applyDamage(player, enemy, 16f + player.comboCount * 2f, 1.5f)
        }
    }

    fun playerSpecial() {
        if (gameState != GameState.FIGHTING || !player.canAct() || player.isKO) return
        if (player.superBar < 1f) return
        if (player.isBlocking) player.isBlocking = false
        player.superBar = 0f
        playerAnimEvent = AnimEvent.SPECIAL
        player.animTime = 0f
        player.attackTimer = 0.5f
        player.specialCooldown = 2f
        onEnergyWave?.invoke(true)
        val dist = enemy.x - player.x
        if (dist * player.facing > 0 && dist * enemy.facing < 0 && kotlin.math.abs(dist) <= SPECIAL_DIST) {
            if (enemy.isBlocking) applyBlockedDamage(player, enemy, playerFighter.specialDamage * 0.5f)
            else applyDamage(player, enemy, playerFighter.specialDamage + player.comboCount * 3f, 3f)
        }
    }

    fun playerBlock() {
        if (gameState != GameState.FIGHTING || player.isStunned() || player.isKO) return
        player.isBlocking = true
        player.isMoving = false
        playerAnimEvent = AnimEvent.BLOCK
    }

    fun playerReleaseBlock() {
        player.isBlocking = false
    }

    fun playerJump() {
        if (gameState != GameState.FIGHTING || player.isStunned() || player.isKO) return
        if (!player.isGrounded) return
        if (player.isBlocking) player.isBlocking = false
        player.vy = 350f
        player.isGrounded = false
    }

    private fun applyDamage(attacker: FighterState, defender: FighterState, base: Float, kbMul: Float) {
        val crit = Random.nextFloat() < 0.12f
        var dmg = base * attacker.comboCount.coerceAtLeast(1).toFloat().coerceAtMost(5f)
        if (crit) dmg *= 2f
        defender.hp = max(0f, defender.hp - dmg)
        attacker.totalDamage += dmg.toInt()
        attacker.hitsLanded++
        attacker.comboCount++
        defender.stunTimer = if (crit) STUN_HEAVY else STUN_LIGHT
        val kb = if (crit) KNOCKBACK_HEAVY else KNOCKBACK * kbMul
        val kbDir = if (defender.x > attacker.x) 1 else -1
        defender.x = (defender.x + kbDir * kb).coerceIn(STAGE_LEFT, STAGE_RIGHT)
        setAnim(defender, AnimEvent.HIT)
        val superGain = if (crit) 0.15f else 0.05f
        attacker.superBar = min(1f, attacker.superBar + superGain)
        if (attacker.superBar >= 1f && superGain > 0f) {
            onSuperReady?.invoke(attacker === player)
        }
        onDamageDealt?.invoke(attacker === player, dmg.toInt(), crit)
        if (crit) onCameraShake?.invoke(7f, 0.3f)
        else onCameraShake?.invoke(3f, 0.12f)
        onComboUpdate?.invoke(attacker.comboCount, attacker.totalDamage)
        if (defender.hp <= 0f) {
            defender.isKO = true
            setAnim(defender, AnimEvent.KO)
            setAnim(attacker, AnimEvent.VICTORY)
            if (attacker === player) {
                playerRoundWins++
                onKO?.invoke(true)
            } else {
                enemyRoundWins++
                onKO?.invoke(false)
            }
            onCameraShake?.invoke(12f, 0.6f)
            schedule(2000) { checkRoundOrEnd() }
        }
    }

    private fun applyBlockedDamage(attacker: FighterState, defender: FighterState, base: Float) {
        val chip = base * 0.25f
        defender.hp = max(0f, defender.hp - chip)
        defender.stunTimer = STUN_LIGHT * 0.3f
        setAnim(defender, AnimEvent.HIT)
        onDamageDealt?.invoke(attacker === player, chip.toInt(), false)
    }

    private fun checkRoundOrEnd() {
        if (playerRoundWins >= (maxRounds + 1) / 2 || enemyRoundWins >= (maxRounds + 1) / 2) {
            battleResult = when {
                playerRoundWins > enemyRoundWins -> BattleResult.PLAYER_WIN
                enemyRoundWins > playerRoundWins -> BattleResult.ENEMY_WIN
                else -> BattleResult.DRAW
            }
            endBattle()
            return
        }
        currentRound++
        onRoundEnd?.invoke(currentRound)
        resetFighters()
        startCountdown()
    }

    private fun endBattle() {
        gameState = GameState.ENDED
        onBattleEnd?.invoke(battleResult)
    }

    private fun resetFighters() {
        player.hp = player.maxHp
        enemy.hp = enemy.maxHp
        player.x = FIGHT_CENTER - 0.28f
        enemy.x = FIGHT_CENTER + 0.28f
        player.y = 0f; enemy.y = 0f
        player.vy = 0f; enemy.vy = 0f
        player.isGrounded = true; enemy.isGrounded = true
        player.stunTimer = 0f; enemy.stunTimer = 0f
        player.attackTimer = 0f; enemy.attackTimer = 0f
        player.isKO = false; enemy.isKO = false
        player.isBlocking = false; enemy.isBlocking = false
        player.isMoving = false; enemy.isMoving = false
        player.comboCount = 0; enemy.comboCount = 0
        playerAnimEvent = AnimEvent.IDLE
        enemyAnimEvent = AnimEvent.IDLE
        player.animTime = 0f; enemy.animTime = 0f
    }

    private fun resolveCollision() {
        val dist = enemy.x - player.x
        if (dist < MIN_DIST) {
            val push = (MIN_DIST - dist) * 0.5f
            player.x = max(STAGE_LEFT, player.x - push)
            enemy.x = min(STAGE_RIGHT, enemy.x + push)
        }
    }

    private fun updateFacing(a: FighterState, b: FighterState) {
        a.facing = if (a.x <= b.x) 1 else -1
        b.facing = if (b.x <= a.x) 1 else -1
    }

    private fun updateGravity(f: FighterState, dt: Float) {
        if (!f.isGrounded) {
            f.vy -= 980f * dt
            f.y += f.vy * dt
            if (f.y <= 0f) { f.y = 0f; f.vy = 0f; f.isGrounded = true }
        }
    }

    private fun aiDecide() {
        if (aiActionCooldown > 0f) return
        val dist = kotlin.math.abs(enemy.x - player.x)
        val hpRatio = enemy.hp / enemy.maxHp
        val playerHpRatio = player.hp / player.maxHp
        val r = Random.nextFloat()

        when {
            enemy.superBar >= 1f && dist <= SPECIAL_DIST && r < 0.35f -> {
                enemy.superBar = 0f
                enemyAnimEvent = AnimEvent.SPECIAL
                enemy.attackTimer = 0.5f
                enemy.specialCooldown = 2f
                onEnergyWave?.invoke(false)
                if (player.isBlocking) applyBlockedDamage(enemy, player, enemyFighter.specialDamage * 0.5f)
                else applyDamage(enemy, player, enemyFighter.specialDamage, 3f)
                aiActionCooldown = 0.6f
            }
            dist <= KICK_DIST && r < 0.3f * aiAggressiveness -> {
                enemyAnimEvent = AnimEvent.KICK
                enemy.attackTimer = 0.35f
                if (player.isBlocking) applyBlockedDamage(enemy, player, 16f)
                else applyDamage(enemy, player, 16f * enemyFighter.power, 1.5f)
                aiActionCooldown = 0.4f
            }
            dist <= PUNCH_DIST && r < 0.7f * aiAggressiveness -> {
                enemyAnimEvent = AnimEvent.PUNCH
                enemy.attackTimer = 0.22f
                if (player.isBlocking) applyBlockedDamage(enemy, player, 8f)
                else applyDamage(enemy, player, 8f * enemyFighter.power, 1f)
                aiActionCooldown = 0.3f
            }
            hpRatio < 0.2f && r < 0.5f -> {
                enemyAnimEvent = AnimEvent.BLOCK
                enemy.isBlocking = true
                aiActionCooldown = 0.8f
                schedule(800) { enemy.isBlocking = false }
            }
            else -> {
                val dir = if (dist > PUNCH_DIST) {
                    if (player.x > enemy.x) 1 else -1
                } else if (r < 0.4f) {
                    if (player.x > enemy.x) -1 else 1
                } else 0
                val spd = 0.01f * enemyFighter.speed
                enemy.x = (enemy.x + dir * spd).coerceIn(STAGE_LEFT, STAGE_RIGHT)
                enemy.isMoving = dir != 0
                if (dir != 0) enemyAnimEvent = AnimEvent.WALK
                aiActionCooldown = 0.2f
            }
        }
    }

    private fun updateAnimations() {
        if (player.isKO) playerAnimEvent = AnimEvent.KO
        else if (player.isStunned()) playerAnimEvent = AnimEvent.HIT
        else if (player.isBlocking) playerAnimEvent = AnimEvent.BLOCK
        else if (player.isMoving) playerAnimEvent = AnimEvent.WALK
        else if (playerAnimTimeFinished() && playerAnimEvent == AnimEvent.PUNCH) playerAnimEvent = AnimEvent.IDLE
        else if (playerAnimTimeFinished() && playerAnimEvent == AnimEvent.KICK) playerAnimEvent = AnimEvent.IDLE
        else if (playerAnimTimeFinished() && playerAnimEvent == AnimEvent.SPECIAL) playerAnimEvent = AnimEvent.IDLE
        else if (playerAnimTimeFinished() && playerAnimEvent == AnimEvent.HIT) playerAnimEvent = AnimEvent.IDLE

        if (enemy.isKO) enemyAnimEvent = AnimEvent.KO
        else if (enemy.isStunned()) enemyAnimEvent = AnimEvent.HIT
        else if (enemy.isBlocking) enemyAnimEvent = AnimEvent.BLOCK
        else if (enemy.isMoving) enemyAnimEvent = AnimEvent.WALK
        else if (enemyAnimTimeFinished() && enemyAnimEvent == AnimEvent.PUNCH) enemyAnimEvent = AnimEvent.IDLE
        else if (enemyAnimTimeFinished() && enemyAnimEvent == AnimEvent.KICK) enemyAnimEvent = AnimEvent.IDLE
        else if (enemyAnimTimeFinished() && enemyAnimEvent == AnimEvent.SPECIAL) enemyAnimEvent = AnimEvent.IDLE
        else if (enemyAnimTimeFinished() && enemyAnimEvent == AnimEvent.HIT) enemyAnimEvent = AnimEvent.IDLE
    }

    private fun playerAnimTimeFinished(): Boolean {
        val dur = when (playerAnimEvent) {
            AnimEvent.PUNCH -> 0.22f; AnimEvent.KICK -> 0.35f
            AnimEvent.SPECIAL -> 0.5f; AnimEvent.HIT -> 0.2f
            else -> 0f
        }
        return player.animTime >= dur
    }

    private fun enemyAnimTimeFinished(): Boolean {
        val dur = when (enemyAnimEvent) {
            AnimEvent.PUNCH -> 0.22f; AnimEvent.KICK -> 0.35f
            AnimEvent.SPECIAL -> 0.5f; AnimEvent.HIT -> 0.2f
            else -> 0f
        }
        return enemy.animTime >= dur
    }

    private fun setAnim(f: FighterState, e: AnimEvent) {
        val isPlayer = f === player
        when (e) {
            AnimEvent.HIT -> { if (isPlayer) playerAnimEvent = AnimEvent.HIT else enemyAnimEvent = AnimEvent.HIT; f.animTime = 0f }
            AnimEvent.KO -> { if (isPlayer) playerAnimEvent = AnimEvent.KO else enemyAnimEvent = AnimEvent.KO; f.animTime = 0f }
            AnimEvent.VICTORY -> { if (isPlayer) playerAnimEvent = AnimEvent.VICTORY else enemyAnimEvent = AnimEvent.VICTORY; f.animTime = 0f }
            else -> {}
        }
    }

    private var scheduleId = 0
    private val scheduled = mutableMapOf<Int, Runnable>()

    private fun schedule(delayMs: Long, action: () -> Unit) {
        val id = scheduleId++
        scheduled[id] = Runnable {
            scheduled.remove(id)
            action()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(scheduled[id]!!, delayMs)
    }

    fun destroy() {
        scheduled.values.forEach { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) }
        scheduled.clear()
    }
}

package com.intelligame.huntix.managers

import com.intelligame.huntix.*
import kotlin.math.*

/**
 * BattleManager — Sistema di combattimento 1v1 basato su XP.
 *
 * === v5: RISCRITTURA COMPLETA ===
 * - 1 uovo del giocatore vs 1 uovo avversario (random)
 * - Forza = livello esperienza del player
 * - Aspetto uovo evolve con il livello (colore + emoji)
 * - Probabilità vittoria proporzionale alla differenza di forza
 * - Pareggio → premio di consolazione
 * - Vittoria → piccolo aumento XP
 */
object BattleManager {

    // ── Aspetto uovo basato sul livello ────────────────────────────

    data class EggAppearance(
        val emoji: String,
        val name: String,
        val colorHex: String,
        val glowHex: String,
        val description: String
    )

    fun getEggAppearance(level: Int): EggAppearance = when {
        level >= 50 -> EggAppearance("🌟", "Uovo Cosmico",    "#FFD700", "#FFF9C4", "Risplende di energia cosmica")
        level >= 40 -> EggAppearance("🐉", "Uovo Draconico",  "#4A148C", "#D8B4FE", "Arde di fuoco ancestrale")
        level >= 30 -> EggAppearance("⭐", "Uovo Stellare",   "#FF6F00", "#FFE082", "Brillante come una stella")
        level >= 20 -> EggAppearance("💎", "Uovo Diamante",   "#00ACC1", "#80DEEA", "Duro come il diamante")
        level >= 15 -> EggAppearance("🔥", "Uovo Infuocato",  "#D84315", "#FF8A65", "Caldo e potente")
        level >= 10 -> EggAppearance("💜", "Uovo Mistico",    "#7B1FA2", "#D8B4FE", "Avvolto da un'aura viola")
        level >= 5  -> EggAppearance("🔵", "Uovo Azzurro",    "#00E5FF", "#66CCFF", "Robusto e resistente")
        else        -> EggAppearance("🥚", "Uovo Bianco",     "#9E9E9E", "#E0E0E0", "Fragile e inesperto")
    }

    // ── Risultato battaglia ────────────────────────────────────────

    enum class BattleOutcome { WIN, LOSE, DRAW }

    data class BattleFighter(
        val level: Int,
        val strength: Int,         // = level
        val appearance: EggAppearance
    )

    data class BattleResult(
        val outcome: BattleOutcome,
        val player: BattleFighter,
        val enemy: BattleFighter,
        val winChancePercent: Int,
        val xpGained: Long,
        val mvcGained: Long,
        val gemsGained: Int,
        val narrativeLog: List<String>
    )

    // ── Logica principale ──────────────────────────────────────────

    /**
     * Esegue un combattimento 1v1.
     *
     * @param playerLevel Livello del giocatore (da XP)
     * @return BattleResult con esito, premi e log narrativo
     */
    fun fight(playerLevel: Int): BattleResult {
        val playerStrength = playerLevel.coerceAtLeast(1)
        val playerAppearance = getEggAppearance(playerLevel)

        // Avversario random: livello tra max(1, playerLevel-5) e playerLevel+5
        val enemyMinLevel = (playerLevel - 5).coerceAtLeast(1)
        val enemyMaxLevel = (playerLevel + 5).coerceAtLeast(2)
        val enemyLevel = (enemyMinLevel..enemyMaxLevel).random()
        val enemyStrength = enemyLevel
        val enemyAppearance = getEggAppearance(enemyLevel)

        val player = BattleFighter(playerLevel, playerStrength, playerAppearance)
        val enemy = BattleFighter(enemyLevel, enemyStrength, enemyAppearance)

        // Calcola probabilità di vittoria
        val totalStrength = (playerStrength + enemyStrength).toFloat()
        val rawWinChance = playerStrength.toFloat() / totalStrength
        // Zona pareggio: se la differenza di forza è ≤ 1, possibilità pareggio
        val strengthDiff = abs(playerStrength - enemyStrength)
        val drawChance = if (strengthDiff <= 1) 0.25f else if (strengthDiff <= 3) 0.10f else 0.03f

        val winChancePercent = (rawWinChance * 100).toInt().coerceIn(5, 95)

        // Determina esito
        val roll = Math.random().toFloat()
        val outcome = when {
            roll < drawChance -> BattleOutcome.DRAW
            roll < drawChance + (rawWinChance * (1f - drawChance)) -> BattleOutcome.WIN
            else -> BattleOutcome.LOSE
        }

        // Calcola premi
        val (xpGained, mvcGained, gemsGained) = when (outcome) {
            BattleOutcome.WIN -> {
                val xp = (10L + enemyLevel * 3L).coerceAtLeast(10L)
                val mvc = (5L + enemyLevel * 2L).coerceAtLeast(5L)
                val gems = if (enemyLevel > playerLevel) 1 else 0   // Bonus gemma se batti uno più forte
                Triple(xp, mvc, gems)
            }
            BattleOutcome.DRAW -> {
                val xp = 3L   // Premio di consolazione
                val mvc = 2L
                Triple(xp, mvc, 0)
            }
            BattleOutcome.LOSE -> Triple(0L, 0L, 0)
        }

        // Log narrativo della battaglia
        val log = buildNarrativeLog(player, enemy, outcome, winChancePercent)

        return BattleResult(
            outcome = outcome,
            player = player,
            enemy = enemy,
            winChancePercent = winChancePercent,
            xpGained = xpGained,
            mvcGained = mvcGained,
            gemsGained = gemsGained,
            narrativeLog = log
        )
    }

    private fun buildNarrativeLog(
        player: BattleFighter, enemy: BattleFighter,
        outcome: BattleOutcome, winChance: Int
    ): List<String> {
        val log = mutableListOf<String>()

        log.add("${player.appearance.emoji} ${player.appearance.name} (Lv.${player.level}) si prepara...")
        log.add("${enemy.appearance.emoji} ${enemy.appearance.name} (Lv.${enemy.level}) appare!")
        log.add("")
        log.add("⚖️ Forza: ${player.strength} vs ${enemy.strength}")
        log.add("📊 Probabilità vittoria: $winChance%")
        log.add("")

        // Fase 1: Avvicinamento
        val approachMsg = when {
            player.strength > enemy.strength + 5 -> "${player.appearance.emoji} intimorisce l'avversario con la sua potenza!"
            enemy.strength > player.strength + 5 -> "${enemy.appearance.emoji} emana un'aura travolgente!"
            else -> "I due uova si studiano attentamente..."
        }
        log.add(approachMsg)

        // Fase 2: Scontro
        log.add("💥 SCONTRO!")
        when (outcome) {
            BattleOutcome.WIN -> {
                log.add("${player.appearance.emoji} colpisce con forza devastante!")
                log.add("${enemy.appearance.emoji} non regge l'impatto...")
                log.add("")
                log.add("🏆 VITTORIA!")
            }
            BattleOutcome.LOSE -> {
                log.add("${enemy.appearance.emoji} contrattacca con potenza!")
                log.add("${player.appearance.emoji} non riesce a resistere...")
                log.add("")
                log.add("💀 SCONFITTA...")
            }
            BattleOutcome.DRAW -> {
                log.add("Entrambi colpiscono contemporaneamente!")
                log.add("Le forze si equivalgono perfettamente...")
                log.add("")
                log.add("🤝 PAREGGIO! Premio di consolazione assegnato.")
            }
        }

        return log
    }

    /**
     * Applica i premi della battaglia al profilo del giocatore.
     */
    fun applyRewards(result: BattleResult) {
        if (result.xpGained > 0) {
            PlayerProfileManager.myProfile?.let {
                it.xp += result.xpGained
                it.weeklyXp += result.xpGained
                PlayerProfileManager.persistMyProfile()
            }
        }
        if (result.gemsGained > 0) {
            PlayerProfileManager.myProfile?.let {
                it.addGems(result.gemsGained)
                PlayerProfileManager.persistMyProfile()
            }
        }
    }
}

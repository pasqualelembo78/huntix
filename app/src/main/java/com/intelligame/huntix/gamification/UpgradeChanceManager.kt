package com.intelligame.huntix.gamification

import android.content.Context
import com.intelligame.huntix.EggRarity

/**
 * UpgradeChanceManager — Meccanica "potenzia rarità" con pity system.
 *
 * Ogni tentativo fallito aumenta la probabilità del 2% (pity).
 * Stored in SharedPreferences per persistenza locale rapida.
 *
 * Esempio: COMMON → 15% base, dopo 10 fail = 35%, dopo 42 fail = 99%
 */
object UpgradeChanceManager {

    private const val PREF = "upgrade_chance_prefs"

    data class UpgradeResult(
        val success: Boolean,
        val newRarity: EggRarity,
        val oldRarity: EggRarity,
        val chanceUsed: Float,
        val pityCount: Int
    )

    // Probabilità base di upgrade per rarità
    private fun baseChance(rarity: EggRarity): Float = when (rarity) {
        EggRarity.COMMON    -> 0.20f   // 20% da Comune a Insolito
        EggRarity.UNCOMMON  -> 0.15f   // 15% da Insolito a Raro
        EggRarity.RARE      -> 0.08f   // 8%  da Raro a Epico
        EggRarity.EPIC      -> 0.03f   // 3%  da Epico a Leggendario
        EggRarity.LEGENDARY -> 0f      // Non upgradabile
    }

    fun getNextRarity(rarity: EggRarity): EggRarity? = when (rarity) {
        EggRarity.COMMON    -> EggRarity.UNCOMMON
        EggRarity.UNCOMMON  -> EggRarity.RARE
        EggRarity.RARE      -> EggRarity.EPIC
        EggRarity.EPIC      -> EggRarity.LEGENDARY
        EggRarity.LEGENDARY -> null
    }

    private fun pityKey(rarity: EggRarity) = "pity_${rarity.id}"

    fun getCurrentChance(ctx: Context, rarity: EggRarity): Float {
        if (rarity == EggRarity.LEGENDARY) return 0f
        val pity = getPityCount(ctx, rarity)
        return (baseChance(rarity) + pity * 0.02f).coerceAtMost(0.99f)
    }

    fun getPityCount(ctx: Context, rarity: EggRarity): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(pityKey(rarity), 0)

    fun attemptUpgrade(ctx: Context, rarity: EggRarity): UpgradeResult {
        val nextRarity = getNextRarity(rarity)
            ?: return UpgradeResult(false, rarity, rarity, 0f, 0)

        val currentChance = getCurrentChance(ctx, rarity)
        val roll = Math.random().toFloat()
        val success = roll < currentChance
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        return if (success) {
            // Reset pity su successo
            prefs.edit().putInt(pityKey(rarity), 0).apply()
            UpgradeResult(true, nextRarity, rarity, currentChance, getPityCount(ctx, rarity))
        } else {
            // Incrementa pity
            val newPity = getPityCount(ctx, rarity) + 1
            prefs.edit().putInt(pityKey(rarity), newPity).apply()
            UpgradeResult(false, rarity, rarity, currentChance, newPity)
        }
    }

    fun resetPity(ctx: Context, rarity: EggRarity) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
           .putInt(pityKey(rarity), 0).apply()
    }

    fun formatChance(chance: Float): String = "${(chance * 100).toInt()}%"
}

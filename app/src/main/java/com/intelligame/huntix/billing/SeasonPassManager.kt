package com.intelligame.huntix.billing

import android.content.Context
import android.content.SharedPreferences

/**
 * SeasonPassManager — Gestione Season Pass (acquisto una tantum, durata 90 giorni).
 *
 * Benefici Season Pass:
 * - Uova esclusive stagionali
 * - Eventi speciali sbloccati
 * - Ricompense doppie per 3 mesi
 * - Badge "Season" in classifica
 */
object SeasonPassManager {

    private const val PREFS = "season_pass_prefs"
    private const val KEY_HAS_PASS = "has_season_pass"
    private const val KEY_PURCHASE_TIME = "season_pass_purchased_at"
    private const val SEASON_DURATION_MS = 90L * 24 * 3600 * 1000  // 90 giorni

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasActivePass(ctx: Context): Boolean {
        if (!prefs(ctx).getBoolean(KEY_HAS_PASS, false)) return false
        val purchasedAt = prefs(ctx).getLong(KEY_PURCHASE_TIME, 0L)
        if (purchasedAt == 0L) return false
        return System.currentTimeMillis() - purchasedAt < SEASON_DURATION_MS
    }

    fun activate(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_HAS_PASS, true)
            .putLong(KEY_PURCHASE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getDaysRemaining(ctx: Context): Int {
        val purchasedAt = prefs(ctx).getLong(KEY_PURCHASE_TIME, 0L)
        if (purchasedAt == 0L) return 0
        val elapsed = System.currentTimeMillis() - purchasedAt
        val remaining = SEASON_DURATION_MS - elapsed
        return if (remaining > 0) (remaining / (24 * 3600 * 1000)).toInt() else 0
    }

    // ── Benefici ─────────────────────────────────────────────────

    fun getRewardMultiplier(ctx: Context): Double = if (hasActivePass(ctx)) 2.0 else 1.0
    fun hasExclusiveEggs(ctx: Context): Boolean = hasActivePass(ctx)
    fun hasSpecialEvents(ctx: Context): Boolean = hasActivePass(ctx)
}

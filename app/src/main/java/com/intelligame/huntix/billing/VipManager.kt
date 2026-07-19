package com.intelligame.huntix.billing

import android.content.Context
import android.content.SharedPreferences

/**
 * VipManager — Gestione stato VIP e benefici.
 *
 * Benefici VIP:
 * - Zero ads (solo rewarded opzionali)
 * - Mining x2
 * - 4 slot schiusura (invece di 2)
 * - +200 MVC/giorno bonus
 * - 5 missioni giornaliere (invece di 3)
 * - Minigiochi illimitati
 * - Cosmetici esclusivi
 * - Badge VIP in chat/classifica
 */
object VipManager {

    private const val PREFS = "vip_prefs"
    private const val KEY_IS_VIP = "is_vip"
    private const val KEY_LAST_DAILY_BONUS = "last_daily_bonus_date"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Stato VIP ───────────────────────────────────────────────

    fun isVip(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_IS_VIP, false)

    fun setVip(ctx: Context, active: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_IS_VIP, active).apply()
    }

    /**
     * Verifica con Google Play e aggiorna stato locale.
     * Chiamare all'avvio dell'app e periodicamente.
     */
    fun syncVipStatus(ctx: Context, onResult: ((Boolean) -> Unit)? = null) {
        BillingManager.checkVipStatus { isActive ->
            setVip(ctx, isActive)
            onResult?.invoke(isActive)
        }
    }

    // ── Benefici ─────────────────────────────────────────────────

    fun getMiningMultiplier(ctx: Context): Double = if (isVip(ctx)) 2.0 else 1.0
    fun getHatchingSlots(ctx: Context): Int = if (isVip(ctx)) 4 else 2
    fun getDailyQuestLimit(ctx: Context): Int = if (isVip(ctx)) 5 else 3
    fun shouldShowAds(ctx: Context): Boolean = !isVip(ctx)
    fun getMiniGameDailyLimit(ctx: Context): Int = if (isVip(ctx)) Int.MAX_VALUE else 3

    /**
     * Bonus giornaliero VIP: +200 MVC.
     * Restituisce true se il bonus e stato accreditato (una volta al giorno).
     */
    fun claimDailyVipBonus(ctx: Context): Boolean {
        if (!isVip(ctx)) return false

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val lastClaim = prefs(ctx).getString(KEY_LAST_DAILY_BONUS, "") ?: ""

        if (lastClaim == today) return false // gia ritirato oggi

        prefs(ctx).edit().putString(KEY_LAST_DAILY_BONUS, today).apply()
        SavedManager.addMvc(ctx, 200.0)
        return true
    }
}

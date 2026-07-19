package com.intelligame.huntix.billing

import android.content.Context
import android.content.SharedPreferences

/**
 * MultiplayerProManager — Gestione Multiplayer Pro (acquisto permanente).
 *
 * Benefici Multiplayer Pro:
 * - Lobby private illimitate
 * - Stanze fino a 8 giocatori (invece di 4)
 * - Badge "PRO" nelle lobby
 * - Priorità matchmaking
 */
object MultiplayerProManager {

    private const val PREFS = "multiplayer_pro_prefs"
    private const val KEY_HAS_PRO = "has_multiplayer_pro"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPro(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HAS_PRO, false)

    fun activate(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_HAS_PRO, true).apply()
    }

    // ── Benefici ─────────────────────────────────────────────────

    fun getMaxPlayers(ctx: Context): Int = if (hasPro(ctx)) 8 else 4
    fun canCreatePrivateRoom(ctx: Context): Boolean = hasPro(ctx)
    fun hasMatchmakingPriority(ctx: Context): Boolean = hasPro(ctx)
}

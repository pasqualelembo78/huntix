package com.intelligame.huntix.reallife

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * RealLifeAuth — gestione locale di token e identità per il backend Real Life.
 *
 * Strategia (Fase A):
 *  - Identità anonima per-device generata una volta (username/password casuali).
 *  - Al primo avvio il backend crea l'utente via POST /auth/local-login.
 *  - Salviamo access_token (15 min), refresh_token e persistent_token.
 *  - Se l'access token non funziona (401) lo rinnoviamo con persistent_token
 *    via POST /auth/reauth.
 *
 * NOTA: password e token sono in SharedPreferences plain. Per la produzione
 * usare EncryptedSharedPreferences (AndroidX Security). Qui è sufficiente per Fase A.
 */
object RealLifeAuth {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(RealLifeConfig.PREFS_NAME, Context.MODE_PRIVATE)

    // ── Identità device ──────────────────────────────────────────
    fun getUsername(context: Context): String {
        val p = prefs(context)
        var u = p.getString("rl_username", null)
        if (u.isNullOrBlank()) {
            u = "huntix_" + UUID.randomUUID().toString().replace("-", "").take(12)
            p.edit().putString("rl_username", u).apply()
        }
        return u
    }

    fun getPassword(context: Context): String {
        val p = prefs(context)
        var pw = p.getString("rl_password", null)
        if (pw.isNullOrBlank()) {
            pw = UUID.randomUUID().toString().replace("-", "") +
                    UUID.randomUUID().toString().replace("-", "").take(8)
            p.edit().putString("rl_password", pw).apply()
        }
        return pw
    }

    // ── Token ────────────────────────────────────────────────────
    fun getAccessToken(context: Context): String =
        prefs(context).getString("rl_access_token", "") ?: ""

    fun getPersistentToken(context: Context): String =
        prefs(context).getString("rl_persistent_token", "") ?: ""

    fun saveTokens(context: Context, access: String, refresh: String, persistent: String) {
        prefs(context).edit().apply {
            putString("rl_access_token", access)
            if (refresh.isNotBlank()) putString("rl_refresh_token", refresh)
            if (persistent.isNotBlank()) putString("rl_persistent_token", persistent)
            apply()
        }
    }

    fun hasTokens(context: Context): Boolean =
        getAccessToken(context).isNotBlank() || getPersistentToken(context).isNotBlank()
}

package com.intelligame.huntix.reallife

import android.content.Context
import android.content.SharedPreferences

/**
 * LocalNeeds — gestione locale dei bisogni Sims (per-device, senza backend).
 *
 * Se il backend è online, i bisogni vengono sincronizzati da lì.
 * Se il backend è offline (errore di rete), i bisogni locali vengono usati come fallback.
 */
object LocalNeeds {
    private const val PREFS = "rl_needs"
    private const val K_HUNGER = "hunger"
    private const val K_SLEEP = "sleep"
    private const val K_HYGIENE = "hygiene"
    private const val K_FUN = "fun"
    private const val K_THIRST = "thirst"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Leggi tutti i bisogni (default: 60/100) */
    fun load(ctx: Context): MutableMap<String, Float> {
        val p = prefs(ctx)
        return mutableMapOf(
            "hunger" to p.getFloat(K_HUNGER, 60f),
            "sleep" to p.getFloat(K_SLEEP, 60f),
            "hygiene" to p.getFloat(K_HYGIENE, 60f),
            "fun" to p.getFloat(K_FUN, 60f),
            "thirst" to p.getFloat(K_THIRST, 60f)
        )
    }

    /** Salva tutti i bisogni */
    fun save(ctx: Context, needs: Map<String, Float>) {
        prefs(ctx).edit().apply {
            putFloat(K_HUNGER, (needs["hunger"] ?: 60f).coerceIn(0f, 100f))
            putFloat(K_SLEEP, (needs["sleep"] ?: 60f).coerceIn(0f, 100f))
            putFloat(K_HYGIENE, (needs["hygiene"] ?: 60f).coerceIn(0f, 100f))
            putFloat(K_FUN, (needs["fun"] ?: 60f).coerceIn(0f, 100f))
            putFloat(K_THIRST, (needs["thirst"] ?: 60f).coerceIn(0f, 100f))
            apply()
        }
    }

    /** Applica un'azione: aggiungi gain al need specificato, clamp a [0, 100] */
    fun applyAction(ctx: Context, needKey: String, gain: Float): Map<String, Float> {
        val needs = load(ctx)
        val current = needs[needKey] ?: 60f
        needs[needKey] = (current + gain).coerceIn(0f, 100f)
        save(ctx, needs)
        return needs
    }

    /** Sync da server Needs -> local */
    fun syncFromServer(ctx: Context, serverNeeds: Needs) {
        save(ctx, mutableMapOf(
            "hunger" to serverNeeds.hunger.toFloat(),
            "sleep" to serverNeeds.sleep.toFloat(),
            "hygiene" to serverNeeds.hygiene.toFloat(),
            "fun" to serverNeeds.funLevel.toFloat(),
            "thirst" to 60f // server non ha thirst, mantieni locale
        ))
    }
}

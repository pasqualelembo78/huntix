package com.intelligame.huntix

import org.json.JSONArray
import org.json.JSONObject

/**
 * HatchedEgg — uovo schiuso, disponibile per il mining MVC e la fusione.
 *
 * Sistema di livelli (fusione 3→1):
 *  Lv.1 → uovo schiuso base                (1x mining)
 *  Lv.2 → fusione di 3 × Lv.1              (2x mining)
 *  Lv.3 → fusione di 3 × Lv.2              (4x mining)
 *  MAX  → fusione di 3 × Lv.3              (8x mining)
 *
 * Mining power base per rarità (h/s):
 *  COMMON    → 0.01  h/s   (~0.02 MVC/giorno)
 *  UNCOMMON  → 0.03  h/s   (~0.07 MVC/giorno)
 *  RARE      → 0.10  h/s   (~0.24 MVC/giorno)
 *  EPIC      → 0.30  h/s   (~0.72 MVC/giorno)
 *  LEGENDARY → 1.00  h/s   (~2.4 MVC/giorno)
 */
data class HatchedEgg(
    val instanceId:     String  = java.util.UUID.randomUUID().toString().take(12),
    val sourceRarityId: String  = "common",
    val level:          Int     = 1,
    val hatchedAt:      Long    = System.currentTimeMillis(),
    val fusedFrom:      List<String> = emptyList(),
    val creatureId:     String  = ""
) {
    val rarity: EggRarity get() = EggRarity.fromId(sourceRarityId)

    val miningPowerHps: Double
        get() = baseMiningPower(rarity) * Math.pow(2.0, (level - 1).toDouble())

    val levelTag: String get() = when (level) { 4 -> "MAX"; else -> "Lv.$level" }

    val emoji: String get() = when (level) {
        1 -> rarity.emoji
        2 -> "✨${rarity.emoji}"
        3 -> "💫${rarity.emoji}"
        4 -> "👑${rarity.emoji}"
        else -> rarity.emoji
    }

    val displayName: String get() = when (level) {
        1 -> rarity.displayName
        2 -> "${rarity.displayName} Potenziato"
        3 -> "${rarity.displayName} Supremo"
        4 -> "${rarity.displayName} MAXIMUM"
        else -> rarity.displayName
    }

    val glowColorHex: String get() = when (level) {
        1 -> rarity.colorHex
        2 -> "#00BFFF"
        3 -> "#FF00FF"
        4 -> "#E0E0FF"
        else -> rarity.colorHex
    }

    val isMaxLevel: Boolean get() = level >= 4

    fun toJson(): JSONObject = JSONObject().apply {
        put("instanceId", instanceId)
        put("sourceRarityId", sourceRarityId)
        put("level", level)
        put("hatchedAt", hatchedAt)
        put("creatureId", creatureId)
        val arr = JSONArray(); fusedFrom.forEach { arr.put(it) }
        put("fusedFrom", arr)
    }

    companion object {
        fun fromJson(j: JSONObject) = HatchedEgg(
            instanceId     = j.optString("instanceId", java.util.UUID.randomUUID().toString().take(12)),
            sourceRarityId = j.optString("sourceRarityId", "common"),
            level          = j.optInt("level", 1),
            hatchedAt      = j.optLong("hatchedAt", System.currentTimeMillis()),
            fusedFrom      = (j.optJSONArray("fusedFrom") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            creatureId     = j.optString("creatureId", "")
        )

        fun baseMiningPower(rarity: EggRarity): Double = when (rarity) {
            EggRarity.COMMON    -> 0.01
            EggRarity.UNCOMMON  -> 0.03
            EggRarity.RARE      -> 0.10
            EggRarity.EPIC      -> 0.30
            EggRarity.LEGENDARY -> 1.00
        }

        fun formatHps(hps: Double): String = when {
            hps <= 0        -> "0 h/s"
            hps < 1e-6      -> "${"%.2f".format(hps * 1e9)} nh/s"
            hps < 1e-3      -> "${"%.2f".format(hps * 1e6)} µh/s"
            hps < 1.0       -> "${"%.2f".format(hps * 1e3)} mh/s"
            hps < 1_000.0   -> "${"%.3f".format(hps)} h/s"
            else            -> "${"%.2f".format(hps / 1_000)} Kh/s"
        }

        fun formatMvc(mvc: Double): String = when {
            mvc < 0.000001 -> "${"%.8f".format(mvc)} MVC"
            mvc < 0.001    -> "${"%.6f".format(mvc)} MVC"
            mvc < 1.0      -> "${"%.4f".format(mvc)} MVC"
            mvc < 1000.0   -> "${"%.3f".format(mvc)} MVC"
            else           -> "${"%.2f".format(mvc / 1000)} kMVC"
        }
    }
}

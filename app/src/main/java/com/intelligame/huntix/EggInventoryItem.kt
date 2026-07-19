package com.intelligame.huntix

import org.json.JSONObject

/**
 * EggInventoryItem — singolo uovo catturato nell'inventario del giocatore.
 * Salvato localmente in SharedPreferences + opzionalmente su Firestore.
 *
 * [inBattleTeam] = true → uovo selezionato per il futuro "Inventario Combattimento" (max 3).
 */
data class EggInventoryItem(
    val instanceId:   String  = java.util.UUID.randomUUID().toString().take(12),
    val eggId:        String  = "",
    val rarityId:     String  = "common",
    val fantasyName:  String  = "",
    val power:        Int     = 0,
    val xpReward:     Int     = 0,
    val caughtAt:     Long    = System.currentTimeMillis(),
    var inBattleTeam: Boolean = false
) {
    val rarity: EggRarity get() = EggRarity.fromId(rarityId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("instanceId",   instanceId)
        put("eggId",        eggId)
        put("rarityId",     rarityId)
        put("fantasyName",  fantasyName)
        put("power",        power)
        put("xpReward",     xpReward)
        put("caughtAt",     caughtAt)
        put("inBattleTeam", inBattleTeam)
    }

    companion object {
        fun fromJson(j: JSONObject) = EggInventoryItem(
            instanceId   = j.optString("instanceId",   java.util.UUID.randomUUID().toString().take(12)),
            eggId        = j.optString("eggId",        ""),
            rarityId     = j.optString("rarityId",     "common"),
            fantasyName  = j.optString("fantasyName",  ""),
            power        = j.optInt("power",           0),
            xpReward     = j.optInt("xpReward",        0),
            caughtAt     = j.optLong("caughtAt",       System.currentTimeMillis()),
            inBattleTeam = j.optBoolean("inBattleTeam", false)
        )

        fun fromWorldEgg(egg: WorldEgg) = EggInventoryItem(
            eggId       = egg.id,
            rarityId    = egg.rarity.id,
            fantasyName = egg.displayLabel,
            power       = if (egg.currentPower >= 0) egg.currentPower else egg.rarity.basePower,
            xpReward    = egg.rarity.xpReward
        )
    }
}

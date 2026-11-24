// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggElement
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggNutrimentManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import com.intelligame.huntix.manager.OutdoorManager
import org.json.JSONObject

/**
 * EggBaitManager — Fase 2, voce 13 (consumabili).
 *
 * Collega le esche gia' definite in [EggNutrimentManager] alla cattura reale:
 * il giocatore sceglie un cibo, l'esca resta attiva per pochi minuti e il
 * primo lancio la consuma aumentando la probabilita' di presa. Non tocca
 * l'inventario uova, i premi, le valute, ne' le tabelle di drop esistenti:
 * senza una scelta esplicita il bonus resta 1f, come prima.
 *
 * Riflesso della caccia alle uova: il PRIMO utilizzo di ogni aroma regala un
 * uovo posizionale idempotente ("bait_<foodId>") e lo registra nello STESSO
 * EggWhereLog con luogo e coordinate, cosi' il diario della Fase 1 conserva
 * anche "dove ho usato un'esca".
 */
object EggBaitManager {

    private const val TAG = "HuntixBait"
    private const val PREFS = "egg_bait_prefs"
    private const val KEY_FOOD = "active_food_id"
    private const val KEY_SELECTED_AT = "selected_at_ms"
    private const val KEY_LAST_USED = "last_used_ms"
    private const val KEY_USES = "uses_left"

    /** Durata dell'esca dopo la scelta: abbastanza per raggiungere un uovo. */
    const val DURATION_MS = 5 * 60 * 1000L
    /** Fra due esche: evita l'uso ripetuto senza movimento. */
    const val COOLDOWN_MS = 30_000L
    /** Un solo lancio beneficia di ogni esca scelta. */
    private const val MAX_USES = 1

    data class ActiveBait(
        val food: EggNutrimentManager.EggFood,
        val bonus: Float,
        val expiresAtMs: Long
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Esca attiva e non scaduta, oppure null. Non consuma nulla. */
    fun current(ctx: Context): ActiveBait? {
        val p = prefs(ctx)
        val id = p.getString(KEY_FOOD, "").orEmpty()
        if (id.isBlank() || p.getInt(KEY_USES, 0) <= 0) return null
        val selectedAt = p.getLong(KEY_SELECTED_AT, 0L)
        if (selectedAt <= 0L || System.currentTimeMillis() - selectedAt > DURATION_MS) return null
        val food = EggNutrimentManager.EggFood.fromId(id) ?: return null
        return ActiveBait(food, food.baseCatchBonus, selectedAt + DURATION_MS)
    }

    fun cooldownRemainingMs(ctx: Context): Long {
        val last = prefs(ctx).getLong(KEY_LAST_USED, 0L)
        if (last <= 0L) return 0L
        return maxOf(0L, COOLDOWN_MS - (System.currentTimeMillis() - last))
    }

    fun availableCount(ctx: Context): Int =
        EggNutrimentManager.EggFood.values().sumOf { EggNutrimentManager.getQuantity(ctx, it) }

    /**
     * Sceglie e consuma un'esca. Ritorna un messaggio in italiano oppure null
     * se il cibo non esiste, non e' disponibile o il cooldown e' ancora attivo.
     */
    fun select(ctx: Context, foodId: String): String? {
        val food = EggNutrimentManager.EggFood.fromId(foodId) ?: return null
        val wait = cooldownRemainingMs(ctx)
        if (wait > 0L) return "Aspetta ancora ${(wait / 1000L) + 1}s prima di usare un'altra esca"
        if (EggNutrimentManager.getQuantity(ctx, food) <= 0) return "Non hai più ${food.displayName}"

        // consume() e' l'unica fonte di verita' delle quantita': non duplichiamo
        // l'inventario e non aggiungiamo premi. La quest spend_food viene
        // aggiornata qui perche' non passiamo da applyFood().
        if (!EggNutrimentManager.consume(ctx, food)) return "Non hai più ${food.displayName}"
        ResearchTaskManager.trackProgress(ctx, "spend_food")

        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putString(KEY_FOOD, food.id)
            .putLong(KEY_SELECTED_AT, now)
            .putInt(KEY_USES, MAX_USES)
            .apply()

        notifyUnity(food, food.baseCatchBonus, now + DURATION_MS)
        grantAromaEggOnce(ctx, food)
        AppLog.i(TAG, "Esca scelta: ${food.id} bonus=${food.baseCatchBonus}")
        return "${food.emoji} ${food.displayName} pronta: hai 5 minuti per catturare un uovo!"
    }

    /**
     * Applica l'esca al lancio corrente. Ritorna il moltiplicatore di cattura
     * (1f = nessuna esca, identico al comportamento precedente). Dopo l'uso
     * l'esca si consuma e lo specchio Unity viene azzerato.
     */
    fun consumeForCatch(ctx: Context, element: EggElement? = null): Float {
        val bait = current(ctx) ?: return 1f
        val bonus = if (element == null) bait.food.baseCatchBonus
                    else EggNutrimentManager.calculateCatchBonus(bait.food, element)

        prefs(ctx).edit()
            .putString(KEY_FOOD, "")
            .putInt(KEY_USES, 0)
            .putLong(KEY_LAST_USED, System.currentTimeMillis())
            .apply()
        notifyUnityCleared()

        val loc = OutdoorManager.get().currentLocation ?: OutdoorManager.lastKnownLocation(ctx)
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        EggWhereLog.recordActivity(
            ctx,
            EggWhereLog.KIND_EVENT,
            "Esca usata: ${bait.food.emoji} ${bait.food.displayName}",
            "esca→${bait.food.id}",
            lat,
            lng
        )
        return bonus
    }

    /** Annulla un'esca scelta (nessun consumo, nessun cooldown). */
    fun cancel(ctx: Context) {
        prefs(ctx).edit().putString(KEY_FOOD, "").putInt(KEY_USES, 0).apply()
        notifyUnityCleared()
    }

    /** Stato passivo rimandato a Unity in setMode: nessun uovo all'avvio. */
    fun snapshot(ctx: Context): String {
        val bait = current(ctx)
        return JSONObject().apply {
            put("activeBaitId", bait?.food?.id.orEmpty())
            put("bonus", bait?.bonus?.toDouble() ?: 1.0)
            put("expiresMs", bait?.expiresAtMs ?: 0L)
            put("count", availableCount(ctx))
        }.toString()
    }

    private fun notifyUnity(food: EggNutrimentManager.EggFood, bonus: Float, expiresMs: Long) {
        try {
            val json = JSONObject().apply {
                put("foodId", food.id)
                put("bonus", bonus.toDouble())
                put("expiresMs", expiresMs)
                put("label", "${food.emoji} ${food.displayName}")
            }.toString()
            PoiUnityBridge.sendEvent("ActiveBaitChanged", json)
        } catch (e: Exception) {
            AppLog.w(TAG, "notifyUnity failed: ${e.message}")
        }
    }

    private fun notifyUnityCleared() {
        try {
            val json = JSONObject().apply {
                put("foodId", "")
                put("bonus", 1.0)
                put("expiresMs", 0L)
            }.toString()
            PoiUnityBridge.sendEvent("ActiveBaitChanged", json)
        } catch (e: Exception) {
            AppLog.w(TAG, "notifyUnityCleared failed: ${e.message}")
        }
    }

    /** Uovo posizionale una tantum per aroma: idempotente via eggId. */
    private fun grantAromaEggOnce(ctx: Context, food: EggNutrimentManager.EggFood) {
        val eggId = "bait_${food.id}"
        if (EggInventoryManager.getInventory(ctx).any { it.eggId == eggId }) return
        val rarity = EggRarity.UNCOMMON
        val name = "Uovo dell'Aroma · ${food.displayName}"
        val added = EggInventoryManager.addEgg(
            ctx,
            EggInventoryItem(
                eggId = eggId,
                rarityId = rarity.id,
                fantasyName = name,
                power = rarity.basePower,
                xpReward = 0
            )
        )
        if (!added) return
        val loc = OutdoorManager.get().currentLocation ?: OutdoorManager.lastKnownLocation(ctx)
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        EggWhereLog.record(ctx, rarity.id, "esca→${food.displayName}", lat, lng, name)
        AppLog.i(TAG, "Uovo dell'Aroma registrato: $eggId a $lat,$lng")
    }
}

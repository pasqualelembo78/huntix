// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.HatchedEgg
import org.json.JSONObject

/**
 * EggFusionCraftManager — Fase 2, voce 15 (crafting/fusione uova).
 *
 * Wrapper AFFIANCATO alla fusione storica di `SavedManager`: non modifica
 * SavedManager.kt, non tocca valute, shop, tabelle drop, Firestore o il
 * backend. Si limita a:
 *  - leggere/riempire i 3 slot già esistenti (`fusion_slots_v2`);
 *  - validare e invocare `executeFusionSlots`;
 *  - verificare l'esito e ripristinare l'uovo fuso nell'inventario se la
 *    scrittura storica non è riuscita (inventario pieno);
 *  - lasciare un uovo posizionale idempotente (`fusion_<ids ordinati>`) e
 *    UNA sola voce nello stesso EggWhereLog delle uova.
 *
 * Se la fusione storica non è atomica, questo wrapper non la rende tale:
 * controlla però che le tre uova consumate siano quelle attese e registra
 * sempre l'esito, senza mai eliminare uova per "ripulire".
 */
object EggFusionCraftManager {

    private const val TAG = "HuntixCraft"
    private const val PREFS = "egg_fusion_craft_prefs"
    private const val KEY_TOTAL = "total"
    private const val KEY_LAST_LEVEL = "last_level"
    private const val KEY_LAST_RARITY = "last_rarity"
    private const val SLOT_COUNT = 3 // = SavedManager.MAX_FUSION_SLOTS (privato)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun total(ctx: Context): Int = prefs(ctx).getInt(KEY_TOTAL, 0)

    fun lastLevel(ctx: Context): Int = prefs(ctx).getInt(KEY_LAST_LEVEL, 0)

    /** Uova schiuse ancora libere, quelle che l'UI può mettere negli slot. */
    fun available(ctx: Context): List<HatchedEgg> = SavedManager.getHatchedEggs(ctx)

    /** Slot visuali già esistenti (non un nuovo inventario). */
    fun slots(ctx: Context): List<HatchedEgg?> = SavedManager.getFusionSlots(ctx)

    /** Quale uovo schiuso riempirebbe il primo slot libero, se ci sono uova. */
    fun nextCandidate(ctx: Context): HatchedEgg? {
        val used = slots(ctx).filterNotNull().map { it.istanzaId }.toSet()
        return available(ctx).firstOrNull { it.istanzaId !in used }
    }

    /** Mette un uovo schiuso nel primo slot libero.true se è stato aggiunto. */
    fun addSlot(ctx: Context, istanzaId: String): Boolean {
        val egg = available(ctx).firstOrNull { it.istanzaId == istanzaId } ?: return false
        return SavedManager.addToFusionSlot(ctx, egg) >= 0
    }

    /** Aggiunge la prima uovo schiuso libero, se esiste. */
    fun addNext(ctx: Context): Boolean {
        val egg = nextCandidate(ctx) ?: return false
        return addSlot(ctx, egg.istanzaId)
    }

    /** Svuota uno slot restituendo l'uovo alle uova schiuse (mai eliminato). */
    fun removeSlot(ctx: Context, index: Int): Boolean =
        SavedManager.removeFromFusionSlot(ctx, index) != null

    fun canCraft(ctx: Context): Boolean = SavedManager.canFuse(ctx)

    /** Ragione italiana per cui la fusione non è possibile, o null se è pronta. */
    fun blockReason(ctx: Context): String? {
        val filled = slots(ctx).filterNotNull()
        if (filled.size < SLOT_COUNT) return "Servono $SLOT_COUNT uova schiuse negli slot (${filled.size}/$SLOT_COUNT)"
        if (filled.any { it.isMaxLevel }) return "Un uovo è già MAX: la fusione non è possibile"
        val first = filled.first().creatureId
        if (first.isBlank()) return "Le uova non hanno una creatura compatibile"
        if (filled.any { it.creatureId != first }) return "Servono uova della stessa creatura"
        return null
    }

    /**
     * Esegue la fusione. Ritorna un messaggio italiano oppure null se non era
     * possibile. `place`/`lat`/`lng` sono il punto reale in cui è stata chiesta
     * la fusione (Miacittà o Android) e finiscono nel diario delle uova.
     */
    fun craft(ctx: Context, place: String = "crafting→Huntix", lat: Double = 0.0, lng: Double = 0.0): String? {
        if (!canCraft(ctx)) return null

        // Snapshot prima di toccare qualsiasi cosa: serve a verificare che la
        // fusione storica abbia consumato esattamente le uova negli slot.
        val expected = slots(ctx).filterNotNull().map { it.istanzaId }.sorted()

        val fused = try {
            SavedManager.executeFusionSlots(ctx)
        } catch (e: Exception) {
            AppLog.e(TAG, "Fusione interrotta: ${e.message}", e)
            return null
        } ?: return null

        val actual = fused.fusedFrom.sorted()
        if (actual != expected) {
            // Non si può annullare la fusione già scritta: la si segnala e si
            // conserva comunque l'uovo creato, senza inventare un uovo extra.
            AppLog.w(TAG, "Fusione con sorgenti non coerenti: atteso $expected, ottenuto $actual")
        }

        // La fusione storica aggiunge l'uovo all'inventario ma ignora il
        // ritorno di addEgg: se l'inventario è pieno l'uovo resta solo tra le
        // uova schiuse. Ripristino additivo, senza rimuovere nulla.
        var inventoryFull = false
        if (EggInventoryManager.getInventory(ctx).none { it.istanzaId == fused.istanzaId }) {
            val restored = EggInventoryItem(
                istanzaId = fused.istanzaId,
                rarityId = fused.sourceRarityId,
                fantasyName = fused.displayName,
                power = EggRarity.fromId(fused.sourceRarityId).basePower * fused.level
            )
            inventoryFull = !EggInventoryManager.addEgg(ctx, restored)
        }

        val rarity = EggRarity.fromId(fused.sourceRarityId)
        val eggId = "fusion_" + actual.joinToString("_")
        val name = "Uovo della Fusione · ${fused.displayName}"
        val where = if (place.isBlank()) "fusione→crafting" else "fusione→$place"

        prefs(ctx).edit()
            .putInt(KEY_TOTAL, total(ctx) + 1)
            .putInt(KEY_LAST_LEVEL, fused.level)
            .putString(KEY_LAST_RARITY, rarity.id)
            .apply()

        // Un solo entry nel diario: uovo posizionale se è la prima volta che
        // questa esatta fusione avviene, altrimenti evento idempotente.
        if (EggInventoryManager.getInventory(ctx).none { it.eggId == eggId }) {
            val added = EggInventoryManager.addEgg(ctx, EggInventoryItem(
                eggId = eggId,
                rarityId = rarity.id,
                fantasyName = name,
                power = rarity.basePower,
                xpReward = 0
            ))
            if (added) {
                EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)
            } else {
                // Inventario pieno: il ricordo resta nel diario, senza uovo.
                EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                    "$name (inventario pieno)", where, lat, lng, name)
                inventoryFull = true
            }
        } else {
            EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                "Fusione ripetuta: $name", where, lat, lng, name)
        }

        val tail = if (inventoryFull) " · inventario pieno: controlla le uova schiuse" else ""
        val msg = "${fused.emoji} ${fused.displayName} creato! ${fused.levelTag} · ricordo nel diario 🥚$tail"
        AppLog.i(TAG, "$msg ($eggId a $lat,$lng)")
        notifyUnity(ctx, fused, eggId, msg)
        return msg
    }

    /** Stato passivo rimandato a Unity: contatori, nessun uovo generato. */
    fun snapshot(ctx: Context): JSONObject = JSONObject().apply {
        put("fusionCraftCount", total(ctx))
        put("fusionCraftLastLevel", lastLevel(ctx))
        put("fusionCraftLastRarity", prefs(ctx).getString(KEY_LAST_RARITY, "") ?: "")
    }

    /** A→U: esito della fusione, anche negativo, per aggiornare HUD/telemetria. */
    private fun notifyUnity(ctx: Context, egg: HatchedEgg, eggId: String, message: String) {
        try {
            val json = JSONObject().apply {
                put("ok", true)
                put("name", egg.displayName)
                put("level", egg.level)
                put("rarity", egg.sourceRarityId)
                put("eggId", eggId)
                put("count", total(ctx))
                put("message", message)
            }
            com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.sendEvent("FusionCrafted", json.toString())
        } catch (e: Exception) {
            AppLog.w(TAG, "Notifica Unity non riuscita: ${e.message}")
        }
    }
}

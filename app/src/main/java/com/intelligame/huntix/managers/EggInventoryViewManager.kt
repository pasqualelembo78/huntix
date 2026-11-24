// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.manager.OutdoorManager
import org.json.JSONObject

/**
 * EggInventoryViewManager — Fase 2, voce 11 (inventario evoluto).
 *
 * Collega le API già esistenti ma mai chiamate di `EggInventoryManager`
 * (ordinamenti per rarità/potenza, squadra uova) a una vista italiana e al
 * bridge. È un wrapper AFFIANCATO: non modifica `EggInventoryManager.kt`, non
 * crea un secondo inventario e non tocca valute, shop, drop o Firestore.
 *
 * Regola di conservazione: l'ordinamento è SOLO una preferenza di vista. Le
 * `getSorted*` restituiscono nuove liste e non vengono mai risalvate, quindi
 * l'ordine "più recente prima" dell'inventario storico resta invariato.
 *
 * Riflesso della caccia alle uova: la prima volta che un uovo di una data
 * rarità entra nella squadra nasce un uovo posizionale idempotente
 * `inventory_team_<rarityId>`, registrato nello STESSO EggWhereLog con luogo
 * e coordinate. Ogni cambio squadra scrive una sola voce nel diario.
 */
object EggInventoryViewManager {

    private const val TAG = "HuntixInventory"
    private const val PREFS = "egg_inventory_view_prefs"
    private const val KEY_MODE = "sort_mode"

    enum class SortMode(val id: String, val label: String) {
        RARITY("rarity", "Rarità"),
        POWER("power", "Potenza"),
        RECENT("recent", "Recenti");

        companion object {
            fun fromId(id: String): SortMode = values().firstOrNull { it.id == id } ?: RECENT
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sortMode(ctx: Context): SortMode =
        SortMode.fromId(prefs(ctx).getString(KEY_MODE, SortMode.RECENT.id) ?: SortMode.RECENT.id)

    /** Cambia SOLO la preferenza di vista: nessuna riscrittura dell'inventario. */
    fun setSortMode(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_MODE, SortMode.fromId(id).id).apply()
        notifyUnity(ctx)
    }

    /** Vista ordinata: nuove liste, mai persistite. */
    fun sorted(ctx: Context): List<EggInventoryItem> = when (sortMode(ctx)) {
        SortMode.RARITY -> EggInventoryManager.getSortedByRarity(ctx)
        SortMode.POWER -> EggInventoryManager.getSortedByPower(ctx)
        SortMode.RECENT -> EggInventoryManager.getInventory(ctx)
    }

    /** Squadra uova: distinta dalla squadra creature usata nei raid. */
    fun team(ctx: Context): List<EggInventoryItem> = EggInventoryManager.getBattleTeam(ctx)

    fun isInTeam(ctx: Context, istanzaId: String): Boolean =
        team(ctx).any { it.istanzaId == istanzaId }

    /**
     * Aggiunge/toglie un uovo dalla squadra uova. Delega tutto
     * all'API storica `toggleBattleTeam` e lascia un solo ricordo nel diario.
     */
    fun toggleTeam(ctx: Context, istanzaId: String): String? {
        val egg = EggInventoryManager.getInventory(ctx).firstOrNull { it.istanzaId == istanzaId }
            ?: return null
        val wasIn = isInTeam(ctx, istanzaId)
        if (!EggInventoryManager.toggleBattleTeam(ctx, istanzaId)) return null
        val nowIn = isInTeam(ctx, istanzaId)
        if (wasIn == nowIn) return null

        val loc = OutdoorManager.get().currentLocation ?: OutdoorManager.lastKnownLocation(ctx)
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        val where = "inventario→squadra uova"
        val rarityId = egg.rarityId.ifBlank { "common" }

        if (nowIn && EggInventoryManager.getInventory(ctx).none { it.eggId == "inventory_team_$rarityId" }) {
            val eggId = "inventory_team_$rarityId"
            val added = EggInventoryManager.addEgg(ctx, EggInventoryItem(
                eggId = eggId,
                rarityId = rarityId,
                fantasyName = "Uovo della Collezione · ${egg.fantasyName.ifBlank { egg.rarity.displayName }}",
                power = egg.rarity.basePower,
                xpReward = 0
            ))
            val name = "Uovo della Collezione · ${egg.fantasyName.ifBlank { egg.rarity.displayName }}"
            if (added) {
                EggWhereLog.record(ctx, rarityId, where, lat, lng, name)
            } else {
                EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                    "Prima squadra $rarityId · inventario pieno", where, lat, lng, name)
            }
        } else {
            val action = if (nowIn) "Uovo in squadra" else "Uovo tolto dalla squadra"
            EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                "$action: ${egg.fantasyName.ifBlank { egg.displayName() }}", where, lat, lng)
        }

        val msg = if (nowIn) "🥚 ${egg.fantasyName.ifBlank { egg.rarity.displayName }} entra nella squadra uova"
                  else "↩️ Uovo rimosso dalla squadra uova"
        AppLog.i(TAG, "$msg (totale squadra ${team(ctx).size})")
        notifyUnity(ctx)
        return msg
    }

    private fun EggInventoryItem.displayName(): String = fantasyName.ifBlank { rarity.displayName }

    /** Stato passivo rimandato a Unity: solo contatori e preferenza di vista. */
    fun snapshot(ctx: Context): JSONObject = JSONObject().apply {
        put("inventoryCount", EggInventoryManager.getInventoryCount(ctx))
        put("inventoryTeamCount", team(ctx).size)
        put("inventorySortMode", sortMode(ctx).id)
    }

    /** Richiede a Unity il solo specchio dei contatori, senza aprire UI. */
    fun report(ctx: Context) = notifyUnity(ctx)

    private fun notifyUnity(ctx: Context) {
        try {
            val json = snapshot(ctx).apply { put("ok", true) }
            com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge.sendEvent(
                "EggInventoryChanged", json.toString())
        } catch (e: Exception) {
            AppLog.w(TAG, "Notifica Unity non riuscita: ${e.message}")
        }
    }
}

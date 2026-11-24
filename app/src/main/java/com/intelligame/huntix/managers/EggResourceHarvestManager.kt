// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.manager.OutdoorManager
import org.json.JSONObject

/**
 * EggResourceHarvestManager — Fase 2, voce 14 (raccolta risorse).
 *
 * Registra le risorse raccolte negli interni di Miacittà (scaffali, panificio,
 * frutta) e in qualunque altra scena che invii `ResourceHarvested`. È un sistema
 * AFFIANCATO: non tocca il secchiello di cattura, le valute, il negozio,
 * l'inventario cibi o il backlog del backend.
 *
 * Riflesso della caccia alle uova: la PRIMA raccolta di ogni tipo regala un
 * uovo posizionale idempotente (`harvest_<resourceId>`) e lo scrive nello
 * STESSO EggWhereLog con luogo e coordinate del punto di raccolta. Ogni
 * raccolta successiva è solo una voce del diario: nessun premio ripetuto.
 */
object EggResourceHarvestManager {

    private const val TAG = "HuntixHarvest"
    private const val PREFS = "egg_resource_prefs"
    private const val KEY_TOTAL = "total"
    private const val PREFIX_COUNT = "count_"
    private const val PREFIX_LAST = "last_"
    private const val COOLDOWN_MS = 15_000L

    /** Catalogo noto: le risorse collect già presenti in StoreBuilder.cs. */
    enum class Resource(
        val id: String,
        val displayName: String,
        val emoji: String
    ) {
        SNACK("snack", "Snack", "📦"),
        WATER("water", "Bevanda", "🛍️"),
        CAN("can", "Lattina", "🥫"),
        BREAD("bread", "Pane fresco", "🍞"),
        FRUIT("fruit", "Frutta fresca", "🍎");

        companion object {
            fun fromId(id: String): Resource? = values().firstOrNull { it.id == id }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun count(ctx: Context, id: String): Int = prefs(ctx).getInt(PREFIX_COUNT + id, 0)

    fun total(ctx: Context): Int = prefs(ctx).getInt(KEY_TOTAL, 0)

    fun kinds(ctx: Context): Int = Resource.values().count { count(ctx, it.id) > 0 }

    fun allCounts(ctx: Context): List<Pair<Resource, Int>> =
        Resource.values().map { it to count(ctx, it.id) }

    /**
     * Registra una risorsa raccolta. Ritorna un messaggio italiano oppure null
     * se l'id è vuoto o il cooldown per quel tipo non è ancora scaduto.
     * Nessun premio in valuta: solo contatori, uovo posizionale una tantum e
     * una voce nel diario con posizione.
     */
    fun harvest(
        ctx: Context,
        itemId: String,
        place: String,
        lat: Double,
        lng: Double
    ): String? {
        val id = itemId.trim()
        if (id.isEmpty()) return null
        val now = System.currentTimeMillis()
        val last = prefs(ctx).getLong(PREFIX_LAST + id, 0L)
        if (last > 0L && now - last < COOLDOWN_MS) return null

        val before = count(ctx, id)
        val p = prefs(ctx)
        p.edit()
            .putInt(PREFIX_COUNT + id, before + 1)
            .putInt(KEY_TOTAL, total(ctx) + 1)
            .putLong(PREFIX_LAST + id, now)
            .apply()

        val known = Resource.fromId(id)
        val label = if (known != null) "${known.emoji} ${known.displayName}" else id
        val where = if (place.isBlank()) "raccolta→$id" else "raccolta→$place"

        val loc = OutdoorManager.get().currentLocation ?: OutdoorManager.lastKnownLocation(ctx)
        val latValue = if (lat != 0.0) lat else loc?.latitude ?: 0.0
        val lngValue = if (lng != 0.0) lng else loc?.longitude ?: 0.0

        EggWhereLog.recordActivity(
            ctx,
            EggWhereLog.KIND_EVENT,
            "Risorsa raccolta: $label",
            where,
            latValue,
            lngValue
        )
        if (before == 0) grantHarvestEggOnce(ctx, id, label, where, latValue, lngValue)

        AppLog.i(TAG, "Raccolta $id a $latValue,$lngValue (totale ${total(ctx)})")
        return "$label raccolto! ${EggWhereLog.count(ctx)} voci nel diario"
    }

    /** Uovo posizionale una tantum per tipo di risorsa, idempotente via eggId. */
    private fun grantHarvestEggOnce(
        ctx: Context,
        id: String,
        label: String,
        where: String,
        lat: Double,
        lng: Double
    ) {
        val eggId = "harvest_$id"
        if (EggInventoryManager.getInventory(ctx).any { it.eggId == eggId }) return
        val rarity = EggRarity.COMMON
        val name = "Uovo della Raccolta · $label"
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
        EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)
        AppLog.i(TAG, "Uovo della Raccolta registrato: $eggId a $lat,$lng")
    }

    /** Stato passivo rimandato a Unity: solo contatori, nessun uovo generato. */
    fun snapshot(ctx: Context): JSONObject = JSONObject().apply {
        put("resourceHarvestCount", total(ctx))
        put("resourceHarvestKinds", kinds(ctx))
        put("resourceHarvestDetail", allCounts(ctx).joinToString(",") { "${it.first.id}:${it.second}" })
    }
}

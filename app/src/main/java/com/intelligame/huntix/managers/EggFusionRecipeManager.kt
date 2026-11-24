// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge
import org.json.JSONObject

/**
 * EggFusionRecipeManager — Fase 2, voce 16 (ricette di fusione).
 *
 * Catalogo in SOLA LETTURA che riproduce la regola già presente nel motore:
 * tre uova schiuse della stessa creatura, non MAX, diventano un uovo del
 * livello successivo (`SavedManager.executeFusionSlots`). Non impone che i
 * tre livelli siano uguali: il motore usa il livello della prima uovo nello
 * slot, quindi una regola più stretta sarebbe un cambio di comportamento.
 *
 * Wrapper AFFIANCATO: non modifica `SavedManager`, `EggFusionCraftManager`
 * oltre alla notifica di scoperta, né valute, shop, drop, Firestore o default.
 * La scoperta lascia un uovo posizionale idempotente con `eggId` dedicato
 * (`recipe_<da>_to_<a>`), distinto da quello della fusione (`fusion_<ids>`),
 * nello stesso inventario e nello stesso EggWhereLog delle altre uova.
 */
object EggFusionRecipeManager {

    private const val TAG = "HuntixRecipe"
    private const val PREFS = "egg_recipe_prefs"
    private const val KEY_DISCOVERED = "discovered"
    private const val KEY_LAST = "last_id"
    private const val KEY_USES = "uses"

    data class Recipe(
        val fromLevel: Int,
        val toLevel: Int,
        val title: String,
        val description: String
    ) {
        val id: String get() = "recipe_${fromLevel}_to_${toLevel}"
        val targetTag: String get() = if (toLevel >= 4) "MAX" else "Lv.$toLevel"
    }

    data class RecipeView(val recipe: Recipe, val discovered: Boolean, val readyGroups: Int)

    private val CATALOG = listOf(
        Recipe(1, 2, "𝄞 Cristallo Potenziato",
            "3 uova Lv.1 della stessa creatura → 1 uovo Lv.2 (potenza ×2)"),
        Recipe(2, 3, "✧ Cristallo Supremo",
            "3 uova Lv.2 della stessa creatura → 1 uovo Lv.3 (potenza ×4)"),
        Recipe(3, 4, "✦ Cristallo MAXIMUM",
            "3 uova Lv.3 della stessa creatura → 1 uovo MAX (potenza ×8)")
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun catalog(): List<Recipe> = CATALOG

    fun discovered(ctx: Context): Set<String> =
        (prefs(ctx).getString(KEY_DISCOVERED, "") ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun isDiscovered(ctx: Context, id: String): Boolean = id in discovered(ctx)

    fun discoveredCount(ctx: Context): Int = discovered(ctx).size

    fun lastId(ctx: Context): String = prefs(ctx).getString(KEY_LAST, "") ?: ""

    fun uses(ctx: Context): Int = prefs(ctx).getInt(KEY_USES, 0)

    /**
     * Vista in sola lettura: conta i gruppi di uova schiuse che potrebbero
     * essere messe negli slot per ogni ricetta. Nessun uovo viene spostato.
     */
    fun views(ctx: Context): List<RecipeView> {
        val groups = EggFusionCraftManager.available(ctx)
            .filter { !it.isMaxLevel }
            .groupBy { if (it.creatureId.isBlank()) it.sourceRarityId else it.creatureId }
        return CATALOG.map { recipe ->
            val ready = groups.values.count { group ->
                group.any { it.level == recipe.fromLevel } && group.size >= 3
            }
            RecipeView(recipe, isDiscovered(ctx, recipe.id), ready)
        }
    }

    /**
     * Chiamato UNA volta dal wrapper di fusione dopo un esito positivo.
     * La prima scoperta crea un uovo posizionale; le volte successive lasciano
     * un solo evento nel diario. In entrambi i casi un solo evento A→U.
     */
    fun markUsed(
        ctx: Context,
        fromLevel: Int,
        rarityId: String,
        fusedName: String,
        place: String,
        lat: Double,
        lng: Double
    ): String? {
        val recipe = CATALOG.firstOrNull { it.fromLevel == fromLevel } ?: return null
        val id = recipe.id
        val name = "Ricetta scoperta · $fusedName"
        val basePlace = if (place.isBlank()) "crafting" else place
        val where = "ricetta→$basePlace"
        val rarity = EggRarity.fromId(rarityId.ifBlank { "common" })
        val already = isDiscovered(ctx, id)

        if (!already) {
            val found = discovered(ctx) + id
            prefs(ctx).edit()
                .putString(KEY_DISCOVERED, found.sorted().joinToString(","))
                .putString(KEY_LAST, id)
                .putInt(KEY_USES, uses(ctx) + 1)
                .apply()

            if (EggInventoryManager.getInventory(ctx).none { it.eggId == id }) {
                val added = EggInventoryManager.addEgg(ctx, EggInventoryItem(
                    eggId = id,
                    rarityId = rarity.id,
                    fantasyName = name,
                    power = rarity.basePower,
                    xpReward = 0
                ))
                if (added) {
                    EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)
                } else {
                    EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                        "$name (inventario pieno)", where, lat, lng, name)
                }
            } else {
                // Uno stesso eggId non può generare due uova: il ricordo resta
                // nel diario come evento, senza duplicazioni.
                EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                    name, where, lat, lng, name)
            }
            AppLog.i(TAG, "Ricetta scoperta: $id ($where a $lat,$lng)")
        } else {
            prefs(ctx).edit().putInt(KEY_USES, uses(ctx) + 1).apply()
            EggWhereLog.recordActivity(ctx, EggWhereLog.KIND_EVENT,
                "Ricetta riusata: ${recipe.title} → ${recipe.targetTag}", where, lat, lng, name)
        }

        notifyUnity(ctx)
        return if (already) null else "📖 ${recipe.title} scoperto! $name 🥚"
    }

    /** Stato passivo rimandato a Unity: solo contatori, nessuna uovo generata. */
    fun snapshot(ctx: Context): JSONObject = JSONObject().apply {
        put("recipeDiscoveredCount", discoveredCount(ctx))
        put("recipeLastId", lastId(ctx))
        put("recipeUses", uses(ctx))
    }

    /** `action=snapshot` da Unity: aggiorna solo lo specchio, senza UI. */
    fun report(ctx: Context) = notifyUnity(ctx)

    private fun notifyUnity(ctx: Context) {
        try {
            val json = snapshot(ctx).apply { put("ok", true) }
            PoiUnityBridge.sendEvent("RecipeDiscoveryChanged", json.toString())
        } catch (e: Exception) {
            AppLog.w(TAG, "Notifica Unity non riuscita: ${e.message}")
        }
    }
}

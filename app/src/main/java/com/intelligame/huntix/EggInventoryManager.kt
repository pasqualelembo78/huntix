package com.intelligame.huntix

import android.content.Context
import org.json.JSONArray

/**
 * EggInventoryManager — inventario uova catturate, persistito in SharedPreferences.
 *
 * - Capienza massima: 300 uova
 * - Battle team: max 3 uova selezionate per i futuri combattimenti speciali
 * - Le uova sono ordinate dalla più recente alla più vecchia (insert-front)
 */
object EggInventoryManager {

    private const val PREF        = "egg_inventory_prefs"
    private const val KEY_INV     = "inventory"
    const val MAX_INVENTORY       = 300
    const val MAX_BATTLE_TEAM     = 3

    // ── Read ──────────────────────────────────────────────────────

    fun getInventory(ctx: Context): MutableList<EggInventoryItem> {
        val json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_INV, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { EggInventoryItem.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun getInventoryCount(ctx: Context) = getInventory(ctx).size
    fun getBattleTeamCount(ctx: Context) = getInventory(ctx).count { it.inBattleTeam }
    fun getBattleTeam(ctx: Context): List<EggInventoryItem> = getInventory(ctx).filter { it.inBattleTeam }

    // ── Write ─────────────────────────────────────────────────────

    private fun saveInventory(ctx: Context, list: List<EggInventoryItem>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_INV, arr.toString()).apply()
    }

    /** Aggiunge un uovo all'inventario. Restituisce false se l'inventario è pieno. */
    fun addEgg(ctx: Context, item: EggInventoryItem): Boolean {
        val inv = getInventory(ctx)
        if (inv.size >= MAX_INVENTORY) return false
        inv.add(0, item)   // più recente in cima
        saveInventory(ctx, inv)
        return true
    }

    /**
     * Attiva/Disattiva un uovo nel battle team.
     * Restituisce false se il team è già pieno (3) e si tenta di aggiungere.
     */
    fun toggleBattleTeam(ctx: Context, instanceId: String): Boolean {
        val inv = getInventory(ctx)
        val item = inv.firstOrNull { it.instanceId == instanceId } ?: return false
        val teamCount = inv.count { it.inBattleTeam }
        if (!item.inBattleTeam && teamCount >= MAX_BATTLE_TEAM) return false
        item.inBattleTeam = !item.inBattleTeam
        saveInventory(ctx, inv)
        return true
    }

    /** Elimina un uovo dall'inventario (e dal battle team se presente). */
    fun removeEgg(ctx: Context, instanceId: String) {
        val inv = getInventory(ctx).filter { it.instanceId != instanceId }
        saveInventory(ctx, inv)
    }

    /** Restituisce gli egg ordinati per rarità decrescente + power decrescente. */
    fun getSortedByRarity(ctx: Context): List<EggInventoryItem> =
        getInventory(ctx).sortedWith(compareByDescending<EggInventoryItem> { it.rarity.ordinal }.thenByDescending { it.power })

    /** Restituisce gli egg ordinati per potere decrescente. */
    fun getSortedByPower(ctx: Context): List<EggInventoryItem> =
        getInventory(ctx).sortedByDescending { it.power }

    /** Restituisce gli egg di una specifica rarità. */
    fun getByRarity(ctx: Context, rarity: EggRarity): List<EggInventoryItem> =
        getInventory(ctx).filter { it.rarityId == rarity.id }
}

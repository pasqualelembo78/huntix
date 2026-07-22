package com.intelligame.huntix.managers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intelligame.huntix.*
import com.intelligame.huntix.ZoneType
import com.intelligame.huntix.WeatherType

object SurpriseManager {
    private const val PREFS      = "surprise_inventory_v1"
    private const val KEY_LIST   = "owned_surprises"
    private const val KEY_BUDDY  = "buddy_creature_id"
    private val gson = Gson()

    fun getAll(ctx: Context): List<OwnedSurprise> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return emptyList()
        return try {
            val type = object : TypeToken<List<OwnedSurprise>>() {}.type
            val result: List<OwnedSurprise>? = gson.fromJson(json, type)
            // Filtra fuori eventuali oggetti corrotti (creatureId null)
            result?.filter { !it.creatureId.isNullOrBlank() } ?: emptyList()
        } catch (e: Exception) {
            Log.e("SurpriseManager", "ERRORE deserializzazione borsa: ${e.message}", e)
            // Pulisci dati corrotti per evitare crash successivi
            try {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_LIST).apply()
                Log.w("SurpriseManager", "Dati corrotti rimossi dalla borsa")
            } catch (_: Exception) {}
            emptyList()
        }
    }

    fun saveAll(ctx: Context, list: List<OwnedSurprise>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }

    fun addFromHatch(ctx: Context, rarityId: String,
                     zone: ZoneType = ZoneType.UNKNOWN,
                     weather: WeatherType = WeatherType.CLEAR): OwnedSurprise {
        val creature = SurpriseCreature.pickForHatch(rarityId, zone, weather)
        val owned = OwnedSurprise(
            creatureId   = creature.id,
            catchZone    = zone.id,
            catchWeather = weather.id
        )
        val list = getAll(ctx).toMutableList()
        list.add(owned)
        saveAll(ctx, list)
        return owned
    }

    /**
     * Aggiunge una creatura specifica (già selezionata) alla borsa del giocatore.
     * Usato quando l'utente tocca un'uova schiusa per spostarla in borsa.
     */
    @Suppress("UNUSED_PARAMETER")
    fun addCreatureToInventory(
        ctx: Context,
        creatureId: String,
        rarityId: String,
        zone: ZoneType = ZoneType.UNKNOWN,
        weather: WeatherType = WeatherType.CLEAR
    ): OwnedSurprise {
        val owned = OwnedSurprise(
            creatureId   = creatureId,
            catchZone    = zone.id,
            catchWeather = weather.id
        )
        val list = getAll(ctx).toMutableList()
        list.add(owned)
        saveAll(ctx, list)
        return owned
    }

    fun getBattleTeam(ctx: Context): List<OwnedSurprise> =
        getAll(ctx).filter { it.inBattleTeam }.take(3)

    fun setBattleTeam(ctx: Context, ids: Set<String>) {
        val list = getAll(ctx).map { it.copy(inBattleTeam = ids.contains(it.id)) }
        saveAll(ctx, list)
    }

    fun getBuddy(ctx: Context): SurpriseCreature? {
        return getAll(ctx).firstOrNull { it.isBuddy }?.creature
    }

    fun setBuddy(ctx: Context, ownedId: String) {
        val list = getAll(ctx).map { it.copy(isBuddy = it.id == ownedId) }
        saveAll(ctx, list)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BUDDY, ownedId).apply()
    }

    fun addCandies(ctx: Context, ownedId: String, count: Int = 1) {
        val list = getAll(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == ownedId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(candies = list[idx].candies + count)
            saveAll(ctx, list)
        }
    }

    fun updateOwned(ctx: Context, updated: OwnedSurprise) {
        val list = getAll(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) list[idx] = updated else list.add(updated)
        saveAll(ctx, list)
    }

    // ── Evolution ────────────────────────────────────────────────

    /** Check if an owned creature can evolve. */
    fun canEvolve(ctx: Context, ownedId: String): Boolean {
        val owned = getAll(ctx).firstOrNull { it.id == ownedId } ?: return false
        val creature = owned.creature ?: return false
        return creature.canEvolve && owned.candies >= creature.candyCost
    }

    /** Get the evolved creature for an owned creature, or null. */
    fun getEvolvedCreature(ctx: Context, ownedId: String): SurpriseCreature? {
        val owned = getAll(ctx).firstOrNull { it.id == ownedId } ?: return null
        return owned.creature?.getEvolvedCreature()
    }

    /** Get candy cost for evolution, or 0 if not evolvable. */
    fun getEvolutionCandyCost(ctx: Context, ownedId: String): Int {
        val creature = getAll(ctx).firstOrNull { it.id == ownedId }?.creature ?: return 0
        return creature.candyCost
    }

    /** Perform evolution: consume candies, change creatureId, boost level+1. */
    fun evolve(ctx: Context, ownedId: String): OwnedSurprise? {
        val list = getAll(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == ownedId }
        if (idx < 0) return null

        val owned = list[idx]
        val creature = owned.creature ?: return null
        if (!creature.canEvolve) return null
        if (owned.candies < creature.candyCost) return null

        val evolved = creature.getEvolvedCreature() ?: return null

        // Consume candies, change creature, boost level
        val newOwned = owned.copy(
            creatureId = evolved.id,
            candies = owned.candies - creature.candyCost,
            level = owned.level + 1
        )
        list[idx] = newOwned
        saveAll(ctx, list)
        return newOwned
    }

    /** Count how many of the same creature species are owned. */
    fun countSpecies(ctx: Context, creatureId: String): Int {
        return getAll(ctx).count { it.creatureId == creatureId }
    }

    /** Get all creatures that can evolve right now. */
    fun getEvolvableCreatures(ctx: Context): List<OwnedSurprise> {
        return getAll(ctx).filter { owned ->
            val creature = owned.creature ?: return@filter false
            creature.canEvolve && owned.candies >= creature.candyCost
        }
    }
}

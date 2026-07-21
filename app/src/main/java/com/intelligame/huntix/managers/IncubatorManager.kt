package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggRarity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object IncubatorManager {

    private const val PREFS = "incubator_prefs_v1"
    private const val KEY_INCUBATORS = "incubators"
    private const val KEY_ACTIVE_EGGS = "active_eggs"

    // ── Distance requirements per rarity (km) ────────────────────────
    fun distanceKmForRarity(rarity: EggRarity): Float = when (rarity) {
        EggRarity.COMMON -> 2f
        EggRarity.UNCOMMON -> 5f
        EggRarity.RARE -> 10f
        EggRarity.EPIC -> 15f
        EggRarity.LEGENDARY -> 20f
    }

    fun distanceLabelForRarity(rarity: EggRarity): String = when (rarity) {
        EggRarity.COMMON -> "2 km"
        EggRarity.UNCOMMON -> "5 km"
        EggRarity.RARE -> "10 km"
        EggRarity.EPIC -> "15 km"
        EggRarity.LEGENDARY -> "20 km"
    }

    // ── Incubator data ───────────────────────────────────────────────
    data class Incubator(
        val id: String,
        val type: String,         // "basic" (unlimited) or "super" (limited)
        val usesRemaining: Int,   // -1 = unlimited
        val name: String = if (type == "basic") "🧰 Incubatrice Base" else "⚡ Super Incubatrice"
    ) {
        val isUnlimited: Boolean get() = usesRemaining < 0
        val isBroken: Boolean get() = !isUnlimited && usesRemaining <= 0

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("type", type); put("usesRemaining", usesRemaining); put("name", name)
        }

        companion object {
            fun fromJson(j: JSONObject) = Incubator(
                id = j.optString("id", UUID.randomUUID().toString().take(8)),
                type = j.optString("type", "basic"),
                usesRemaining = j.optInt("usesRemaining", -1)
            )
        }
    }

    // ── Active egg in incubator ──────────────────────────────────────
    data class ActiveEgg(
        val instanceId: String,
        val incubatorId: String,
        val rarityId: String,
        val distanceRequired: Float,
        val distanceWalked: Float,
        val startMs: Long,
        val fantasyName: String = ""
    ) {
        val progress: Float get() = (distanceWalked / distanceRequired).coerceIn(0f, 1f)
        val isReady: Boolean get() = distanceWalked >= distanceRequired
        val remainingKm: Float get() = (distanceRequired - distanceWalked).coerceAtLeast(0f)
        val rarity: EggRarity get() = EggRarity.fromId(rarityId)

        fun toJson(): JSONObject = JSONObject().apply {
            put("instanceId", instanceId); put("incubatorId", incubatorId)
            put("rarityId", rarityId); put("distanceRequired", distanceRequired)
            put("distanceWalked", distanceWalked); put("startMs", startMs)
            put("fantasyName", fantasyName)
        }

        companion object {
            fun fromJson(j: JSONObject) = ActiveEgg(
                instanceId = j.optString("instanceId"),
                incubatorId = j.optString("incubatorId"),
                rarityId = j.optString("rarityId", "common"),
                distanceRequired = j.optDouble("distanceRequired", 2.0).toFloat(),
                distanceWalked = j.optDouble("distanceWalked", 0.0).toFloat(),
                startMs = j.optLong("startMs"),
                fantasyName = j.optString("fantasyName", "")
            )
        }
    }

    // ── Init ─────────────────────────────────────────────────────────
    private fun defaultIncubators(): List<Incubator> = listOf(
        Incubator("basic_1", "basic", -1),
        Incubator("super_1", "super", 3),
        Incubator("super_2", "super", 3)
    )

    fun getIncubators(ctx: Context): List<Incubator> {
        val json = prefs(ctx).getString(KEY_INCUBATORS, null)
        if (json == null) {
            val defaults = defaultIncubators()
            saveIncubators(ctx, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Incubator.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { defaultIncubators() }
    }

    fun getActiveEggs(ctx: Context): List<ActiveEgg> {
        val json = prefs(ctx).getString(KEY_ACTIVE_EGGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ActiveEgg.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun getAvailableIncubators(ctx: Context): List<Incubator> =
        getIncubators(ctx).filter { !it.isBroken }

    fun getFreeIncubators(ctx: Context): List<Incubator> =
        getAvailableIncubators(ctx).filter { !getActiveEggs(ctx).any { egg -> egg.incubatorId == it.id } }

    fun hasFreeIncubator(ctx: Context): Boolean = getFreeIncubators(ctx).isNotEmpty()

    fun canAddEgg(ctx: Context, item: EggInventoryItem): Boolean = getFreeIncubators(ctx).isNotEmpty()

    fun startIncubation(ctx: Context, item: EggInventoryItem, incubatorId: String): Boolean {
        val incubator = getIncubators(ctx).firstOrNull { it.id == incubatorId } ?: return false
        if (incubator.isBroken) return false

        val activeEggs = getActiveEggs(ctx).toMutableList()
        if (activeEggs.any { it.incubatorId == incubatorId }) return false

        val rarity = EggRarity.fromId(item.rarityId)
        activeEggs.add(ActiveEgg(
            instanceId = item.instanceId,
            incubatorId = incubatorId,
            rarityId = item.rarityId,
            distanceRequired = distanceKmForRarity(rarity),
            distanceWalked = 0f,
            startMs = System.currentTimeMillis(),
            fantasyName = item.fantasyName
        ))

        saveActiveEggs(ctx, activeEggs)
        Log.d("IncubatorManager", "Started incubating ${rarity.displayName} in $incubatorId")
        return true
    }

    fun addDistanceToIncubators(ctx: Context, km: Float): List<String> {
        if (km <= 0f) return emptyList()
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val readyIds = mutableListOf<String>()

        activeEggs.forEachIndexed { idx, egg ->
            val newWalked = (egg.distanceWalked + km).coerceAtMost(egg.distanceRequired)
            activeEggs[idx] = egg.copy(distanceWalked = newWalked)
            if (newWalked >= egg.distanceRequired) readyIds.add(egg.instanceId)
        }

        saveActiveEggs(ctx, activeEggs)

        if (readyIds.isNotEmpty()) {
            val incubators = getIncubators(ctx).toMutableList()
            activeEggs.filter { it.isReady }.forEach { egg ->
                val incIdx = incubators.indexOfFirst { it.id == egg.incubatorId }
                if (incIdx >= 0 && !incubators[incIdx].isUnlimited) {
                    val old = incubators[incIdx]
                    incubators[incIdx] = old.copy(usesRemaining = old.usesRemaining - 1)
                }
            }
            saveIncubators(ctx, incubators)
        }

        return readyIds
    }

    fun collectHatchedEgg(ctx: Context, instanceId: String): ActiveEgg? {
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val egg = activeEggs.firstOrNull { it.instanceId == instanceId && it.isReady } ?: return null
        activeEggs.removeAll { it.instanceId == instanceId }
        saveActiveEggs(ctx, activeEggs)
        return egg
    }

    fun removeEggFromIncubator(ctx: Context, instanceId: String): Boolean {
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val removed = activeEggs.removeAll { it.instanceId == instanceId }
        if (removed) saveActiveEggs(ctx, activeEggs)
        return removed
    }

    private fun saveIncubators(ctx: Context, list: List<Incubator>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_INCUBATORS, arr.toString()).apply()
    }

    private fun saveActiveEggs(ctx: Context, list: List<ActiveEgg>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_ACTIVE_EGGS, arr.toString()).apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggRarity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TermocullaManager {

    private const val PREFS = "termoculla_prefs_v1"
    private const val KEY_TERMOCULLE = "termocullas"
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

    // ── Termoculla data ───────────────────────────────────────────────
    data class Termoculla(
        val id: String,
        val type: String,         // "basic" (unlimited) or "super" (limited)
        val usiRimanenti: Int,   // -1 = unlimited
        val name: String = if (type == "basic") "🧰 Termocolla Base" else "⚡ Super Termocolla"
    ) {
        val isIllimitato: Boolean get() = usiRimanenti < 0
        val isBroken: Boolean get() = !isIllimitato && usiRimanenti <= 0

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("type", type); put("usiRimanenti", usiRimanenti); put("name", name)
        }

        companion object {
            fun fromJson(j: JSONObject) = Termoculla(
                id = j.optString("id", UUID.randomUUID().toString().take(8)),
                type = j.optString("type", "basic"),
                usiRimanenti = j.optInt("usiRimanenti", -1)
            )
        }
    }

    // ── Active egg in termoculla ──────────────────────────────────────
    data class ActiveEgg(
        val istanzaId: String,
        val termocullaId: String,
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
            put("istanzaId", istanzaId); put("termocullaId", termocullaId)
            put("rarityId", rarityId); put("distanceRequired", distanceRequired)
            put("distanceWalked", distanceWalked); put("startMs", startMs)
            put("fantasyName", fantasyName)
        }

        companion object {
            fun fromJson(j: JSONObject) = ActiveEgg(
                istanzaId = j.optString("istanzaId"),
                termocullaId = j.optString("termocullaId"),
                rarityId = j.optString("rarityId", "common"),
                distanceRequired = j.optDouble("distanceRequired", 2.0).toFloat(),
                distanceWalked = j.optDouble("distanceWalked", 0.0).toFloat(),
                startMs = j.optLong("startMs"),
                fantasyName = j.optString("fantasyName", "")
            )
        }
    }

    // ── Init ─────────────────────────────────────────────────────────
    private fun defaultTermocullas(): List<Termoculla> = listOf(
        Termoculla("basic_1", "basic", -1),
        Termoculla("super_1", "super", 3),
        Termoculla("super_2", "super", 3)
    )

    fun getTermocullas(ctx: Context): List<Termoculla> {
        val json = prefs(ctx).getString(KEY_TERMOCULLE, null)
        if (json == null) {
            val defaults = defaultTermocullas()
            saveTermocullas(ctx, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Termoculla.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { defaultTermocullas() }
    }

    fun getActiveEggs(ctx: Context): List<ActiveEgg> {
        val json = prefs(ctx).getString(KEY_ACTIVE_EGGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ActiveEgg.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun getAvailableTermocullas(ctx: Context): List<Termoculla> =
        getTermocullas(ctx).filter { !it.isBroken }

    fun getFreeTermocullas(ctx: Context): List<Termoculla> =
        getAvailableTermocullas(ctx).filter { !getActiveEggs(ctx).any { egg -> egg.termocullaId == it.id } }

    fun hasFreeTermoculla(ctx: Context): Boolean = getFreeTermocullas(ctx).isNotEmpty()

    fun canPlaceEgg(ctx: Context, item: EggInventoryItem): Boolean = getFreeTermocullas(ctx).isNotEmpty()

    fun startSchiusa(ctx: Context, item: EggInventoryItem, termocullaId: String): Boolean {
        val termoculla = getTermocullas(ctx).firstOrNull { it.id == termocullaId } ?: return false
        if (termoculla.isBroken) return false

        val activeEggs = getActiveEggs(ctx).toMutableList()
        if (activeEggs.any { it.termocullaId == termocullaId }) return false

        val rarity = EggRarity.fromId(item.rarityId)
        activeEggs.add(ActiveEgg(
            istanzaId = item.istanzaId,
            termocullaId = termocullaId,
            rarityId = item.rarityId,
            distanceRequired = distanceKmForRarity(rarity),
            distanceWalked = 0f,
            startMs = System.currentTimeMillis(),
            fantasyName = item.fantasyName
        ))

        saveActiveEggs(ctx, activeEggs)
        Log.d("TermocullaManager", "Started inSchiusa ${rarity.displayName} in $termocullaId")
        return true
    }

    fun addDistanceToTermocullas(ctx: Context, km: Float): List<String> {
        if (km <= 0f) return emptyList()
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val readyIds = mutableListOf<String>()

        activeEggs.forEachIndexed { idx, egg ->
            val newWalked = (egg.distanceWalked + km).coerceAtMost(egg.distanceRequired)
            activeEggs[idx] = egg.copy(distanceWalked = newWalked)
            if (newWalked >= egg.distanceRequired) readyIds.add(egg.istanzaId)
        }

        saveActiveEggs(ctx, activeEggs)

        if (readyIds.isNotEmpty()) {
            val termocullas = getTermocullas(ctx).toMutableList()
            activeEggs.filter { it.isReady }.forEach { egg ->
                val incIdx = termocullas.indexOfFirst { it.id == egg.termocullaId }
                if (incIdx >= 0 && !termocullas[incIdx].isIllimitato) {
                    val old = termocullas[incIdx]
                    termocullas[incIdx] = old.copy(usiRimanenti = old.usiRimanenti - 1)
                }
            }
            saveTermocullas(ctx, termocullas)
        }

        return readyIds
    }

    fun collectHatchedEgg(ctx: Context, istanzaId: String): ActiveEgg? {
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val egg = activeEggs.firstOrNull { it.istanzaId == istanzaId && it.isReady } ?: return null
        activeEggs.removeAll { it.istanzaId == istanzaId }
        saveActiveEggs(ctx, activeEggs)
        return egg
    }

    fun removeEggFromTermoculla(ctx: Context, istanzaId: String): Boolean {
        val activeEggs = getActiveEggs(ctx).toMutableList()
        val removed = activeEggs.removeAll { it.istanzaId == istanzaId }
        if (removed) saveActiveEggs(ctx, activeEggs)
        return removed
    }

    private fun saveTermocullas(ctx: Context, list: List<Termoculla>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_TERMOCULLE, arr.toString()).apply()
    }

    private fun saveActiveEggs(ctx: Context, list: List<ActiveEgg>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_ACTIVE_EGGS, arr.toString()).apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

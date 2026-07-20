package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.intelligame.huntix.EggInventoryItem
import kotlin.math.floor
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.HatchedEgg
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.SurpriseCreature
import com.intelligame.huntix.WeatherType
import com.intelligame.huntix.ZoneType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SavedManager {

    private const val PREFS = "huntix_saved_v1"
    private const val KEY_MVC = "current_mvc"
    private const val KEY_TOTAL_EARNED = "total_mvc_earned"
    private const val KEY_TOTAL_SPENT = "total_mvc_spent"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private const val KEY_MVC_FRACTION = "mvc_fraction"
    private const val KEY_LAST_CHECKIN_DAY = "mvc_last_checkin_day"
    private const val KEY_INSTALL_BASE_MS = "mvc_install_base_ms"
    private const val KEY_INSTALL_ACCRUE_MS = "mvc_install_accrue_ms"
    private const val DAILY_CHECKIN_REWARD = 10.0
    private const val INSTALL_MVC_PER_HOUR = 0.05      // piccolissima frazione/ora
    private const val MAX_INSTALL_ACCRUE_H = 24.0 * 7  // cap anti-manomissione orologio

    // ── Hatching keys ──────────────────────────────────────────────────────
    private const val KEY_PENDING = "pending_eggs"
    private const val KEY_HATCHING = "hatching_eggs"
    private const val KEY_HATCHED = "hatched_eggs"
    private const val KEY_FUSION_SLOTS = "fusion_slots_v2"
    private const val KEY_LAST_CALC_MS = "last_mining_calc_ms"
    private const val KEY_MIGRATED = "inventory_migrated_v1"
    private const val MAX_HATCHING_SLOTS = 2
    private const val MAX_FUSION_SLOTS = 3
    private const val MAX_OFFLINE_H = 8.0

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════════════════════
    //  MVC — Valuta principale
    // ═══════════════════════════════════════════════════════════════════════

    fun getMvcBalance(ctx: Context): Double =
        prefs(ctx).getLong(KEY_MVC, 0L).toDouble()

    fun setMvcBalance(ctx: Context, amount: Double) {
        prefs(ctx).edit().putLong(KEY_MVC, amount.toLong()).apply()
    }

    @Synchronized
    fun addMvc(ctx: Context, amount: Double): Double {
        if (amount <= 0) return getMvcBalance(ctx)
        val p = prefs(ctx)
        val current = p.getLong(KEY_MVC, 0L) + amount.toLong()
        val earned = p.getLong(KEY_TOTAL_EARNED, 0L) + max(0L, amount.toLong())
        p.edit()
            .putLong(KEY_MVC, current)
            .putLong(KEY_TOTAL_EARNED, earned)
            .apply()
        Log.d("SavedManager", "addMvc +$amount → balance=$current")
        return current.toDouble()
    }

    @Synchronized
    fun spendMvc(ctx: Context, amount: Double): Boolean {
        val p = prefs(ctx)
        val current = p.getLong(KEY_MVC, 0L)
        val cost = amount.toLong()
        if (current < cost) return false
        val next = current - cost
        p.edit()
            .putLong(KEY_MVC, next)
            .putLong(KEY_TOTAL_SPENT, p.getLong(KEY_TOTAL_SPENT, 0L) + cost)
            .apply()
        Log.d("SavedManager", "spendMvc -$amount → balance=$next")
        return true
    }

    fun getTotalEarned(ctx: Context): Double = prefs(ctx).getLong(KEY_TOTAL_EARNED, 0L).toDouble()
    fun getTotalSpent(ctx: Context): Double = prefs(ctx).getLong(KEY_TOTAL_SPENT, 0L).toDouble()

    fun canAfford(ctx: Context, amount: Double): Boolean = getMvcBalance(ctx) >= amount

    fun formatMvc(amount: Double): String = when {
        amount >= 1_000_000 -> "${String.format("%.1f", amount / 1_000_000)}M MVC"
        amount >= 1_000 -> "${String.format("%.1f", amount / 1_000)}K MVC"
        else -> "${amount.toLong()} MVC"
    }

    // ── Accredito frazionario ──────────────────────────────────
    // Il saldo MVC è intero (Long): accumula la parte frazionaria in un
    // apposito pref e versala al saldo solo quando supera 1 MVC.
    private fun creditFractional(ctx: Context, amount: Double): Double {
        if (amount <= 0) return 0.0
        val p = prefs(ctx)
        val remainder = p.getFloat(KEY_MVC_FRACTION, 0f).toDouble() + amount
        val whole = floor(remainder)
        p.edit().putFloat(KEY_MVC_FRACTION, (remainder - whole).toFloat()).apply()
        if (whole >= 1.0) addMvc(ctx, whole)
        return whole
    }

    // ── Check-in giornaliero ───────────────────────────────────
    fun todayDayString(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return fmt.format(java.util.Date())
    }

    fun getLastCheckinDay(ctx: Context): String =
        prefs(ctx).getString(KEY_LAST_CHECKIN_DAY, "") ?: ""

    fun canCheckInToday(ctx: Context): Boolean = getLastCheckinDay(ctx) != todayDayString()

    /** Riscatta il bonus giornaliero (+10 MVC). Ritorna 0 se già riscattato oggi. */
    @Synchronized
    fun doDailyCheckIn(ctx: Context): Double {
        if (!canCheckInToday(ctx)) return 0.0
        prefs(ctx).edit().putString(KEY_LAST_CHECKIN_DAY, todayDayString()).apply()
        addMvc(ctx, DAILY_CHECKIN_REWARD)
        return DAILY_CHECKIN_REWARD
    }

    /** Millisecondi rimanenti al prossimo check-in (mezzanotte locale). */
    fun millisUntilNextCheckIn(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    // ── Bonus presenza app (dal momento dell'installazione) ────
    fun getInstallBaseMs(ctx: Context): Long {
        val p = prefs(ctx)
        var base = p.getLong(KEY_INSTALL_BASE_MS, 0L)
        if (base == 0L) {
            base = try {
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                pi.firstInstallTime
            } catch (_: Exception) { System.currentTimeMillis() }
            p.edit().putLong(KEY_INSTALL_BASE_MS, base).apply()
        }
        return base
    }

    /** Accredita MVC passivi dal momento dell'installazione (piccola frazione/ora). */
    @Synchronized
    fun accrueInstallRewards(ctx: Context): Double {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        var last = p.getLong(KEY_INSTALL_ACCRUE_MS, 0L)
        if (last == 0L) {
            last = getInstallBaseMs(ctx)
            p.edit().putLong(KEY_INSTALL_ACCRUE_MS, last).apply()
        }
        val elapsedMs = (now - last).coerceAtLeast(0L)
        val cappedMs = elapsedMs.coerceAtMost((MAX_INSTALL_ACCRUE_H * 3_600_000L).toLong())
        p.edit().putLong(KEY_INSTALL_ACCRUE_MS, now).apply()
        val hours = cappedMs / 3_600_000.0
        return creditFractional(ctx, hours * INSTALL_MVC_PER_HOUR)
    }

    /** Stima MVC/ora del bonus installazione (per la UI). */
    fun getInstallRatePerHour(): Double = INSTALL_MVC_PER_HOUR

    fun syncWithProfile(ctx: Context) {
        val profile = PlayerProfileManager.myProfile
        val local = getMvcBalance(ctx)
        // MVC is stored separately in SavedManager, not in PlayerProfile (which uses gems)
        prefs(ctx).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Uova Regalo (Gift Eggs)
    // ═══════════════════════════════════════════════════════════════════════

    private const val GIFT_PREFS = "gift_eggs_v1"

    fun giftEgg(ctx: Context, rarityId: String) {
        val p = ctx.getSharedPreferences(GIFT_PREFS, Context.MODE_PRIVATE)
        val key = "gift_${rarityId}_count"
        val count = p.getInt(key, 0) + 1
        p.edit().putInt(key, count).apply()
        Log.d("SavedManager", "giftEgg: $rarityId × $count")
    }

    fun getGiftEggCount(ctx: Context, rarityId: String): Int =
        ctx.getSharedPreferences(GIFT_PREFS, Context.MODE_PRIVATE)
            .getInt("gift_${rarityId}_count", 0)

    // ═══════════════════════════════════════════════════════════════════════
    //  Hatching System — Uova in schiusura
    // ═══════════════════════════════════════════════════════════════════════

    data class HatchingSlot(
        val instanceId: String,
        val sourceRarityId: String,
        val startMs: Long,
        val endMs: Long,
        val fantasyName: String = ""
    ) {
        val isReady: Boolean get() = System.currentTimeMillis() >= endMs
        val remainingSec: Long get() = maxOf(0L, (endMs - System.currentTimeMillis()) / 1000L)
        val progressFraction: Float get() {
            val total = (endMs - startMs).toFloat()
            val elapsed = (System.currentTimeMillis() - startMs).toFloat()
            return (elapsed / total).coerceIn(0f, 1f)
        }
        val rarity: EggRarity get() = EggRarity.fromId(sourceRarityId)

        fun toJson(): JSONObject = JSONObject().apply {
            put("instanceId", instanceId)
            put("sourceRarityId", sourceRarityId)
            put("startMs", startMs)
            put("endMs", endMs)
            put("fantasyName", fantasyName)
        }

        companion object {
            fun fromJson(j: JSONObject) = HatchingSlot(
                instanceId = j.optString("instanceId"),
                sourceRarityId = j.optString("sourceRarityId", "common"),
                startMs = j.optLong("startMs"),
                endMs = j.optLong("endMs"),
                fantasyName = j.optString("fantasyName", "")
            )
        }
    }

    fun hatchDurationMs(rarity: EggRarity): Long = when (rarity) {
        EggRarity.COMMON -> 10 * 60_000L
        EggRarity.UNCOMMON -> 30 * 60_000L
        EggRarity.RARE -> 60 * 60_000L
        EggRarity.EPIC -> 120 * 60_000L
        EggRarity.LEGENDARY -> 360 * 60_000L
    }

    fun hatchDurationLabel(rarity: EggRarity): String = when (rarity) {
        EggRarity.COMMON -> "10 min"
        EggRarity.UNCOMMON -> "30 min"
        EggRarity.RARE -> "1 ora"
        EggRarity.EPIC -> "2 ore"
        EggRarity.LEGENDARY -> "6 ore"
    }

    // ── Pending eggs ─────────────────────────────────────────────────────

    fun getPendingEggs(ctx: Context): MutableList<EggInventoryItem> {
        val json = prefs(ctx).getString(KEY_PENDING, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { EggInventoryItem.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun addPendingEgg(ctx: Context, item: EggInventoryItem) {
        val list = getPendingEggs(ctx)
        if (list.none { it.instanceId == item.instanceId }) {
            list.add(0, item)
            savePendingEggs(ctx, list)
        }
    }

    fun removePendingEgg(ctx: Context, instanceId: String) {
        val list = getPendingEggs(ctx).filter { it.instanceId != instanceId }
        savePendingEggs(ctx, list)
    }

    private fun savePendingEggs(ctx: Context, list: List<EggInventoryItem>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    // ── Hatching slots ───────────────────────────────────────────────────

    fun getHatchingSlots(ctx: Context): MutableList<HatchingSlot> {
        val json = prefs(ctx).getString(KEY_HATCHING, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { HatchingSlot.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun startHatching(ctx: Context, item: EggInventoryItem): Boolean {
        val slots = getHatchingSlots(ctx)
        if (slots.size >= MAX_HATCHING_SLOTS) return false
        val dur = hatchDurationMs(item.rarity)
        val now = System.currentTimeMillis()
        slots.add(HatchingSlot(
            instanceId = item.instanceId,
            sourceRarityId = item.rarityId,
            startMs = now,
            endMs = now + dur,
            fantasyName = item.fantasyName
        ))
        saveHatchingSlots(ctx, slots)
        removePendingEgg(ctx, item.instanceId)
        EggInventoryManager.removeEgg(ctx, item.instanceId)
        return true
    }

    fun collectReady(ctx: Context): List<HatchedEgg> {
        val slots = getHatchingSlots(ctx)
        val (ready, stillHatching) = slots.partition { it.isReady }
        val newHatched = ready.map { slot ->
            val creature = SurpriseCreature.pickForHatch(
                slot.sourceRarityId, ZoneType.UNKNOWN, WeatherType.CLEAR)
            HatchedEgg(
                instanceId = slot.instanceId,
                sourceRarityId = slot.sourceRarityId,
                hatchedAt = System.currentTimeMillis(),
                creatureId = creature.id
            )
        }
        saveHatchingSlots(ctx, stillHatching)
        if (newHatched.isNotEmpty()) {
            val existing = getHatchedEggs(ctx)
            existing.addAll(newHatched)
            saveHatchedEggs(ctx, existing)
        }
        return newHatched
    }

    private fun saveHatchingSlots(ctx: Context, list: List<HatchingSlot>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_HATCHING, arr.toString()).apply()
    }

    // ── Hatched eggs ─────────────────────────────────────────────────────

    fun getHatchedEggs(ctx: Context): MutableList<HatchedEgg> {
        val json = prefs(ctx).getString(KEY_HATCHED, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { HatchedEgg.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun removeHatchedEgg(ctx: Context, instanceId: String) {
        val list = getHatchedEggs(ctx).filter { it.instanceId != instanceId }
        saveHatchedEggs(ctx, list)
    }

    private fun saveHatchedEggs(ctx: Context, list: List<HatchedEgg>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_HATCHED, arr.toString()).apply()
    }

    // ── Fusion Slots (3 slot visuali) ──────────────────────────────────

    fun getFusionSlots(ctx: Context): List<HatchedEgg?> {
        val json = prefs(ctx).getString(KEY_FUSION_SLOTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<HatchedEgg?>()
            for (i in 0 until MAX_FUSION_SLOTS) {
                if (i < arr.length() && !arr.isNull(i)) {
                    result.add(HatchedEgg.fromJson(arr.getJSONObject(i)))
                } else {
                    result.add(null)
                }
            }
            result
        } catch (e: Exception) { listOf(null, null, null) }
    }

    private fun saveFusionSlots(ctx: Context, slots: List<HatchedEgg?>) {
        val arr = JSONArray()
        for (egg in slots) {
            if (egg != null) arr.put(egg.toJson()) else arr.put(JSONObject.NULL)
        }
        prefs(ctx).edit().putString(KEY_FUSION_SLOTS, arr.toString()).apply()
    }

    fun addToFusionSlot(ctx: Context, egg: HatchedEgg): Int {
        val slots = getFusionSlots(ctx).toMutableList()
        while (slots.size < MAX_FUSION_SLOTS) slots.add(null)
        val freeIndex = slots.indexOfFirst { it == null }
        if (freeIndex == -1) return -1
        slots[freeIndex] = egg
        removeHatchedEgg(ctx, egg.instanceId)
        saveFusionSlots(ctx, slots)
        return freeIndex
    }

    fun removeFromFusionSlot(ctx: Context, index: Int): HatchedEgg? {
        val slots = getFusionSlots(ctx).toMutableList()
        if (index < 0 || index >= slots.size) return null
        val egg = slots[index] ?: return null
        slots[index] = null
        saveFusionSlots(ctx, slots)
        val hatched = getHatchedEggs(ctx)
        hatched.add(0, egg)
        saveHatchedEggs(ctx, hatched)
        return egg
    }

    fun canFuse(ctx: Context): Boolean {
        val slots = getFusionSlots(ctx)
        val filled = slots.filterNotNull()
        if (filled.size < MAX_FUSION_SLOTS) return false
        if (filled.any { it.isMaxLevel }) return false
        val firstName = filled[0].creatureId
        return firstName.isNotBlank() && filled.all { it.creatureId == firstName }
    }

    fun executeFusionSlots(ctx: Context): HatchedEgg? {
        if (!canFuse(ctx)) return null
        val slots = getFusionSlots(ctx).filterNotNull()
        if (slots.size < 3) return null

        val base = slots[0]
        val newLevel = (base.level + 1).coerceAtMost(4)
        val fusedEgg = HatchedEgg(
            sourceRarityId = base.sourceRarityId,
            level = newLevel,
            hatchedAt = System.currentTimeMillis(),
            fusedFrom = slots.map { it.instanceId },
            creatureId = base.creatureId
        )

        saveFusionSlots(ctx, listOf(null, null, null))

        val hatched = getHatchedEggs(ctx)
        hatched.add(0, fusedEgg)
        saveHatchedEggs(ctx, hatched)

        slots.forEach { EggInventoryManager.removeEgg(ctx, it.instanceId) }
        val fusedItem = EggInventoryItem(
            instanceId = fusedEgg.instanceId,
            rarityId = base.sourceRarityId,
            fantasyName = fusedEgg.displayName,
            power = EggRarity.fromId(base.sourceRarityId).basePower * newLevel
        )
        EggInventoryManager.addEgg(ctx, fusedItem)
        return fusedEgg
    }

    // ── Legacy fusion (retrocompatibilità) ──────────────────────────────

    fun getFusionGroups(ctx: Context): List<Triple<String, Int, List<HatchedEgg>>> {
        val hatched = getHatchedEggs(ctx)
        return hatched
            .filter { !it.isMaxLevel }
            .groupBy { "${it.sourceRarityId}|${it.level}" }
            .filter { it.value.size >= 3 }
            .mapNotNull { (key, eggs) ->
                val parts = key.split("|")
                if (parts.size < 2) return@mapNotNull null
                val rarityId = parts[0]
                val level = parts[1].toIntOrNull() ?: return@mapNotNull null
                Triple(rarityId, level, eggs)
            }
    }

    fun fuseEggs(ctx: Context, rarityId: String, level: Int): HatchedEgg? {
        if (level >= 4) return null
        val hatched = getHatchedEggs(ctx).toMutableList()
        val candidates = hatched.filter { it.sourceRarityId == rarityId && it.level == level }
        if (candidates.size < 3) return null
        val toFuse = candidates.take(3)
        val fusedEgg = HatchedEgg(
            sourceRarityId = rarityId,
            level = level + 1,
            hatchedAt = System.currentTimeMillis(),
            fusedFrom = toFuse.map { it.instanceId }
        )
        toFuse.forEach { fused -> hatched.removeIf { it.instanceId == fused.instanceId } }
        hatched.add(0, fusedEgg)
        saveHatchedEggs(ctx, hatched)
        toFuse.forEach { EggInventoryManager.removeEgg(ctx, it.instanceId) }
        val fusedInvItem = EggInventoryItem(
            instanceId = fusedEgg.instanceId,
            rarityId = rarityId,
            fantasyName = fusedEgg.displayName,
            power = EggRarity.fromId(rarityId).basePower * fusedEgg.level
        )
        EggInventoryManager.addEgg(ctx, fusedInvItem)
        return fusedEgg
    }

    // ── Mining MVC ──────────────────────────────────────────────────────

    fun getTotalMiningHps(ctx: Context): Double =
        getHatchedEggs(ctx).sumOf { it.miningPowerHps }

    fun accrueMiningRewards(ctx: Context): Double {
        val now = System.currentTimeMillis()
        val lastCalcMs = prefs(ctx).getLong(KEY_LAST_CALC_MS, now)
        prefs(ctx).edit().putLong(KEY_LAST_CALC_MS, now).apply()

        collectReady(ctx)

        val elapsedSec = ((now - lastCalcMs) / 1000L)
            .coerceAtMost((MAX_OFFLINE_H * 3600).toLong())
            .toDouble()
        if (elapsedSec <= 0) return 0.0

        val hps = getTotalMiningHps(ctx)
        val earned = hps * elapsedSec
        if (earned > 0) return creditFractional(ctx, earned)
        return 0.0
    }

    // ── Accelerazione schiusura via energia camminata ────────────

    fun accelerateSlot(ctx: Context, instanceId: String, reduceSec: Long) {
        if (reduceSec <= 0) return
        val slots = getHatchingSlots(ctx).toMutableList()
        val idx = slots.indexOfFirst { it.instanceId == instanceId }
        if (idx < 0) return
        val slot = slots[idx]
        val newEnd = (slot.endMs - reduceSec * 1000L).coerceAtLeast(System.currentTimeMillis())
        slots[idx] = slot.copy(endMs = newEnd)
        saveHatchingSlots(ctx, slots)
    }

    fun applyWalkingEnergyToSlots(ctx: Context, energy: Double): Long {
        if (energy <= 0) return 0L
        val slots = getHatchingSlots(ctx)
        if (slots.isEmpty()) return 0L
        val reduceSecPerSlot = (energy / 100.0 * 60.0).toLong().coerceAtLeast(1L)
        slots.forEach { slot -> accelerateSlot(ctx, slot.instanceId, reduceSecPerSlot) }
        return reduceSecPerSlot
    }

    fun speedUpHatching(ctx: Context, instanceId: String, factor: Float) {
        val slots = getHatchingSlots(ctx).map { slot ->
            if (slot.instanceId == instanceId && !slot.isReady) {
                val remaining = slot.endMs - System.currentTimeMillis()
                slot.copy(endMs = System.currentTimeMillis() + (remaining * factor).toLong())
            } else slot
        }
        saveHatchingSlots(ctx, slots)
    }

    // ── Migrazione uova esistenti ────────────────────────────────

    fun migrateFromInventory(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_MIGRATED, false)) return

        val inventoryEggs = EggInventoryManager.getInventory(ctx)
        if (inventoryEggs.isNotEmpty()) {
            val pending = getPendingEggs(ctx)
            val pendingIds = pending.map { it.instanceId }.toSet()
            var added = 0
            for (egg in inventoryEggs) {
                if (egg.instanceId !in pendingIds) {
                    pending.add(0, egg)
                    added++
                }
            }
            if (added > 0) savePendingEggs(ctx, pending)
        }
        p.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Utils
    // ═══════════════════════════════════════════════════════════════════════

    private fun max(a: Long, b: Long) = if (a > b) a else b
    private fun maxOf(a: Long, b: Long) = if (a > b) a else b
}
package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.SurpriseCreature
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

object RaidManager {

    private const val PREFS = "raid_manager_v1"
    private const val KEY_ACTIVE_RAIDS = "active_raids"
    private const val KEY_COMPLETED_RAIDS = "completed_count"
    private const val KEY_LAST_RAID_DAY = "last_raid_day"

    data class RaidBoss(
        val creatureId: String,
        val name: String,
        val emoji: String,
        val tier: Int,           // 1-5 (1=easiest, 5=legendary)
        val baseHp: Int,
        val baseAttack: Int,
        val rewardRarity: EggRarity
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("creatureId", creatureId); put("name", name); put("emoji", emoji)
            put("tier", tier); put("baseHp", baseHp); put("baseAttack", baseAttack)
            put("rewardRarity", rewardRarity.id)
        }

        companion object {
            fun fromJson(j: JSONObject) = RaidBoss(
                creatureId = j.optString("creatureId"), name = j.optString("name"),
                emoji = j.optString("emoji"), tier = j.optInt("tier", 1),
                baseHp = j.optInt("baseHp", 500), baseAttack = j.optInt("baseAttack", 50),
                rewardRarity = EggRarity.fromId(j.optString("rewardRarity", "common"))
            )
        }
    }

    data class RaidInstance(
        val id: String,
        val boss: RaidBoss,
        val currentHp: Int,
        val maxHp: Int,
        val spawnMs: Long,
        val expiresMs: Long,
        val participants: Int = 0,
        val defeated: Boolean = false
    ) {
        val progress: Float get() = (1f - currentHp.toFloat() / maxHp).coerceIn(0f, 1f)
        val isExpired: Boolean get() = System.currentTimeMillis() > expiresMs
        val remainingSec: Long get() = ((expiresMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val hpPercent: Int get() = (currentHp * 100 / maxHp).coerceIn(0, 100)

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("boss", boss.toJson()); put("currentHp", currentHp)
            put("maxHp", maxHp); put("spawnMs", spawnMs); put("expiresMs", expiresMs)
            put("participants", participants); put("defeated", defeated)
        }

        companion object {
            fun fromJson(j: JSONObject) = RaidInstance(
                id = j.optString("id"), boss = RaidBoss.fromJson(j.optJSONObject("boss") ?: JSONObject()),
                currentHp = j.optInt("currentHp", 500), maxHp = j.optInt("maxHp", 500),
                spawnMs = j.optLong("spawnMs"), expiresMs = j.optLong("expiresMs"),
                participants = j.optInt("participants", 0), defeated = j.optBoolean("defeated", false)
            )
        }
    }

    private val raidBosses = listOf(
        RaidBoss("pulcino", "Pulcino Tonante", "⚡", 1, 300, 30, EggRarity.COMMON),
        RaidBoss("coniglietto", "Coniglietto Rapidash", "💨", 1, 350, 35, EggRarity.COMMON),
        RaidBoss("volpe_luna", "Volpe della Luna Oscura", "🌙", 2, 600, 60, EggRarity.UNCOMMON),
        RaidBoss("gufo_stellato", "Gufo delle Tempeste", "🦉", 2, 700, 70, EggRarity.UNCOMMON),
        RaidBoss("drago_pasquale", "Drago delle Fiamme", "🔥", 3, 1200, 100, EggRarity.RARE),
        RaidBoss("fenice_rosa", "Fenice della Rinascita", "🌅", 3, 1400, 110, EggRarity.RARE),
        RaidBoss("behemoth", "Behemoth di Cristallo", "💎", 4, 2500, 150, EggRarity.EPIC),
        RaidBoss("unicorno", "Unicorno Arcobaleno", "🌈", 4, 2800, 170, EggRarity.EPIC),
        RaidBoss("grande_coniglio", "Il Grande Coniglio Cosmico", "🌌", 5, 5000, 250, EggRarity.LEGENDARY),
        RaidBoss("uovo_creatore", "L'Uovo del Creatore", "✨", 5, 6000, 300, EggRarity.LEGENDARY)
    )

    fun getActiveRaids(ctx: Context): List<RaidInstance> {
        val raids = loadRaids(ctx).filter { !it.isExpired && !it.defeated }
        if (raids.isEmpty()) spawnNewRaid(ctx)
        return loadRaids(ctx).filter { !it.isExpired && !it.defeated }
    }

    fun spawnNewRaid(ctx: Context) {
        val raids = loadRaids(ctx).toMutableList()
        raids.removeAll { it.isExpired || it.defeated }

        if (raids.size >= 3) return

        val tierWeights = listOf(1 to 35, 2 to 30, 3 to 20, 4 to 10, 5 to 5)
        val totalWeight = tierWeights.sumOf { it.second }
        var roll = (0 until totalWeight).random()
        var selectedTier = 1
        for ((tier, weight) in tierWeights) {
            roll -= weight
            if (roll < 0) { selectedTier = tier; break }
        }

        val candidates = raidBosses.filter { it.tier == selectedTier }
        val boss = candidates.random()
        val now = System.currentTimeMillis()
        val duration = 10 * 60 * 1000L  // 10 minutes

        val raid = RaidInstance(
            id = UUID.randomUUID().toString().take(8),
            boss = boss,
            currentHp = boss.baseHp,
            maxHp = boss.baseHp,
            spawnMs = now,
            expiresMs = now + duration
        )

        raids.add(raid)
        saveRaids(ctx, raids)
    }

    fun damageRaid(ctx: Context, raidId: String, damage: Int): RaidInstance? {
        val raids = loadRaids(ctx).toMutableList()
        val idx = raids.indexOfFirst { it.id == raidId }
        if (idx < 0) return null

        val raid = raids[idx]
        if (raid.isExpired || raid.defeated) return null

        val newHp = (raid.currentHp - damage).coerceAtLeast(0)
        val defeated = newHp <= 0

        raids[idx] = raid.copy(
            currentHp = newHp,
            participants = raid.participants + 1,
            defeated = defeated
        )
        saveRaids(ctx, raids)

        if (defeated) {
            val p = prefs(ctx)
            p.edit().putInt(KEY_COMPLETED_RAIDS, p.getInt(KEY_COMPLETED_RAIDS, 0) + 1).apply()
        }

        return raids[idx]
    }

    fun getCompletedRaids(ctx: Context): Int = prefs(ctx).getInt(KEY_COMPLETED_RAIDS, 0)

    fun getTodayRaidCount(ctx: Context): Int {
        val p = prefs(ctx)
        val lastDay = p.getString(KEY_LAST_RAID_DAY, "") ?: ""
        val today = DailyStreakManager.let {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        }
        return if (lastDay == today) p.getInt("today_raid_count", 0) else 0
    }

    fun canRaidToday(ctx: Context): Boolean = getTodayRaidCount(ctx) < 5

    fun incrementRaidCount(ctx: Context) {
        val p = prefs(ctx)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val lastDay = p.getString(KEY_LAST_RAID_DAY, "") ?: ""
        if (lastDay != today) {
            p.edit().putString(KEY_LAST_RAID_DAY, today).putInt("today_raid_count", 1).apply()
        } else {
            p.edit().putInt("today_raid_count", p.getInt("today_raid_count", 0) + 1).apply()
        }
    }

    private fun loadRaids(ctx: Context): MutableList<RaidInstance> {
        val json = prefs(ctx).getString(KEY_ACTIVE_RAIDS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { RaidInstance.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    private fun saveRaids(ctx: Context, list: List<RaidInstance>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_ACTIVE_RAIDS, arr.toString()).apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

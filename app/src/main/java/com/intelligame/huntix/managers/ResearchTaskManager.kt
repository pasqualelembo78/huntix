package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object ResearchTaskManager {

    private const val PREFS = "research_tasks_v1"
    private const val KEY_DAILY = "daily_tasks"
    private const val KEY_WEEKLY = "weekly_tasks"
    private const val KEY_LAST_DAILY_REFRESH = "last_daily_refresh"
    private const val KEY_LAST_WEEKLY_REFRESH = "last_weekly_refresh"
    private const val KEY_COMPLETED_COUNT = "total_completed"

    data class ResearchTask(
        val id: String,
        val title: String,
        val description: String,
        val emoji: String,
        val target: Int,
        var progress: Int = 0,
        val rewardMvc: Int,
        val rewardXp: Int,
        val type: String, // "daily" or "weekly"
        val category: String,
        val claimed: Boolean = false
    ) {
        val isComplete: Boolean get() = progress >= target
        val progressPct: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
        val progressLabel: String get() = "$progress/$target"

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("title", title); put("description", description)
            put("emoji", emoji); put("target", target); put("progress", progress)
            put("rewardMvc", rewardMvc); put("rewardXp", rewardXp); put("type", type)
            put("category", category); put("claimed", claimed)
        }

        companion object {
            fun fromJson(j: JSONObject) = ResearchTask(
                id = j.optString("id"), title = j.optString("title"),
                description = j.optString("description"), emoji = j.optString("emoji"),
                target = j.optInt("target", 1), progress = j.optInt("progress", 0),
                rewardMvc = j.optInt("rewardMvc", 50), rewardXp = j.optInt("rewardXp", 100),
                type = j.optString("type", "daily"), category = j.optString("category", ""),
                claimed = j.optBoolean("claimed", false)
            )
        }
    }

    private val dailyTemplates = listOf(
        Triple("catch_3", "Cattura 3 uova", "Cattura uova in Outdoor o Indoor", "🥚", 3),
        Triple("catch_rare", "Cattura un uovo raro", "Cattura un Cristallo Viola o meglio", "🔮", 1),
        Triple("hatch_1", "Schiudi 1 uovo", "Metti un uovo in incubatrice e aspetta", "🧬", 1),
        Triple("walk_2km", "Cammina 2 km", "Cammina per accumulare distanza", "🚶", 2),
        Triple("play_indoor", "Gioca in Indoor", "Nascondi o cerca uova in AR", "🏠", 1),
        Triple("play_outdoor", "Gioca in Outdoor", "Esplora la mappa e cattura uova", "🌍", 1),
        Triple("win_battle", "Vinci una battaglia", "Sfida un nemico in battaglia", "⚔️", 1),
        Triple("play_minigame", "Gioca 2 minigiochi", "Prova i minigiochi disponibili", "🎮", 2),
        Triple("spend_food", "Usa un'esca", "Usa un cibo per aumentare le chance", "🍎", 1),
        Triple("mine_mvc", "Guadagna 50 MVC", "Accumula MVC con mining o catture", "💰", 50)
    )

    private val weeklyTemplates = listOf(
        Triple("catch_20", "Cattura 20 uova", "Cattura uova in tutta la settimana", "🥚", 20),
        Triple("hatch_5", "Schiudi 5 uova", "Metti in incubatrice e raccogli", "🧬", 5),
        Triple("walk_10km", "Cammina 10 km", "Cammina per accumulare distanza", "🚶", 10),
        Triple("win_5_battles", "Vinci 5 battaglie", "Sfida e vinci nemici", "⚔️", 5),
        Triple("find_epic", "Trova un uovo Epico", "Cattura un Cristallo di Fuoco o meglio", "🔥", 1),
        Triple("earn_500_mvc", "Guadagna 500 MVC", "Accumula MVC in qualsiasi modo", "💰", 500)
    )

    fun refreshIfNeeded(ctx: Context) {
        val p = prefs(ctx)
        val today = todayString()
        val thisWeek = weekString()

        // Refresh daily
        if (p.getString(KEY_LAST_DAILY_REFRESH, "") != today) {
            val selected = dailyTemplates.shuffled().take(3)
            val tasks = selected.map { (id, title, desc, emoji, target) ->
                ResearchTask(
                    id = "${id}_${today}", title = title, description = desc,
                    emoji = emoji, target = target, rewardMvc = target * 20 + 30,
                    rewardXp = target * 50 + 50, type = "daily", category = id
                )
            }
            saveTasks(ctx, KEY_DAILY, tasks)
            p.edit().putString(KEY_LAST_DAILY_REFRESH, today).apply()
        }

        // Refresh weekly
        if (p.getString(KEY_LAST_WEEKLY_REFRESH, "") != thisWeek) {
            val selected = weeklyTemplates.shuffled().take(2)
            val tasks = selected.map { (id, title, desc, emoji, target) ->
                ResearchTask(
                    id = "${id}_${thisWeek}", title = title, description = desc,
                    emoji = emoji, target = target, rewardMvc = target * 30 + 100,
                    rewardXp = target * 80 + 200, type = "weekly", category = id
                )
            }
            saveTasks(ctx, KEY_WEEKLY, tasks)
            p.edit().putString(KEY_LAST_WEEKLY_REFRESH, thisWeek).apply()
        }
    }

    fun getDailyTasks(ctx: Context): List<ResearchTask> {
        refreshIfNeeded(ctx)
        return loadTasks(ctx, KEY_DAILY)
    }

    fun getWeeklyTasks(ctx: Context): List<ResearchTask> {
        refreshIfNeeded(ctx)
        return loadTasks(ctx, KEY_WEEKLY)
    }

    fun trackProgress(ctx: Context, category: String, amount: Int = 1) {
        val allTasks = getDailyTasks(ctx) + getWeeklyTasks(ctx)
        val matching = allTasks.filter { it.category == category && !it.isComplete && !it.claimed }
        if (matching.isEmpty()) return

        for (task in matching) {
            task.progress = (task.progress + amount).coerceAtMost(task.target)
        }

        val daily = allTasks.filter { it.type == "daily" }
        val weekly = allTasks.filter { it.type == "weekly" }
        saveTasks(ctx, KEY_DAILY, daily)
        saveTasks(ctx, KEY_WEEKLY, weekly)
    }

    fun claimReward(ctx: Context, taskId: String): Pair<Int, Int>? {
        val allTasks = (getDailyTasks(ctx) + getWeeklyTasks(ctx)).toMutableList()
        val task = allTasks.firstOrNull { it.id == taskId && it.isComplete && !it.claimed } ?: return null

        val idx = allTasks.indexOfFirst { it.id == taskId }
        allTasks[idx] = task.copy(claimed = true)

        val daily = allTasks.filter { it.type == "daily" }
        val weekly = allTasks.filter { it.type == "weekly" }
        saveTasks(ctx, KEY_DAILY, daily)
        saveTasks(ctx, KEY_WEEKLY, weekly)

        val p = prefs(ctx)
        p.edit().putInt(KEY_COMPLETED_COUNT, p.getInt(KEY_COMPLETED_COUNT, 0) + 1).apply()

        return Pair(task.rewardMvc, task.rewardXp)
    }

    fun getCompletedCount(ctx: Context): Int = prefs(ctx).getInt(KEY_COMPLETED_COUNT, 0)

    private fun loadTasks(ctx: Context, key: String): List<ResearchTask> {
        val json = prefs(ctx).getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ResearchTask.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveTasks(ctx: Context, key: String, tasks: List<ResearchTask>) {
        val arr = JSONArray(); tasks.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(key, arr.toString()).apply()
    }

    private fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun weekString(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return SimpleDateFormat("yyyy-'W'ww", Locale.US).format(cal.time)
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package com.intelligame.huntix

import android.content.Context
import android.content.SharedPreferences
import com.intelligame.huntix.model.EggObject
import com.intelligame.huntix.model.IndoorGameUiState
import com.intelligame.huntix.model.SafeObject
import kotlin.jvm.JvmName
import org.json.JSONArray
import org.json.JSONObject

class GameDataManager private constructor(ctx: Context) {

    data class SavedSession(
        val id: String = "",
        val savedAt: String = "",
        val slotName: String = "",
        val players: List<String> = emptyList(),
        val eggCount: Int = 0,
        val riddles: List<String> = emptyList(),
        val parentNote: String = "",
        val eggOffsets: List<FloatArray> = emptyList(),
        val eggColors: List<Int> = emptyList(),
        val eggShapes: List<String> = emptyList(),
        val trapMask: List<Boolean> = emptyList(),
        val safeType: String = "classic",
        val turnMode: String = "sequential"
    ) {
        fun bestMs(): Long = 0L
        fun worstMs(): Long = 0L
    }

    data class EggStat(val eggNumber: Int, val timeMs: Long)
    data class GameRun(
        val id: String, val playerName: String, val date: String,
        val eggCount: Int, val eggStats: List<EggStat>, val totalMs: Long
    ) {
        fun bestMs(): Long = eggStats.minOfOrNull { it.timeMs } ?: totalMs
        fun worstMs(): Long = eggStats.maxOfOrNull { it.timeMs } ?: totalMs
    }

    companion object {
        private const val PREF = "game_data_prefs"
        private const val KEY_SLOTS = "save_slots"
        private const val KEY_RUNS = "runs"
        private var instance: GameDataManager? = null
        fun get(ctx: Context): GameDataManager = instance ?: GameDataManager(ctx).also { instance = it }
    }

    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getSoundEnabled(): Boolean = true
    fun getArMode(): String = "depth"
    fun getRevealDistMeters(): Float = 3.0f
    fun getCatchDistMeters(): Float = 1.5f
    fun getPlayers(): List<PlayerProfile> = listOf(PlayerProfile("Giocatore"))
    fun newRunId(): String = java.util.UUID.randomUUID().toString()
    fun todayString(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    fun getUnlockedSafes(): List<String> = listOf("classic", "chest", "vault", "present")
    fun getLocalAnchorTtlDays(): Float = 7f

    fun getRunsForPlayer(name: String): List<GameRun> =
        readRuns().filter { it.playerName == name }

    fun addRun(run: GameRun) {
        val runs = readRuns().toMutableList().apply { add(run) }
        prefs.edit().putString(KEY_RUNS, runs.toJson().toString()).apply()
    }

    fun upsertSaveSlot(session: SavedSession) {
        val slots = readSlots().toMutableList()
        val idx = slots.indexOfFirst { it.id == session.id }
        if (idx >= 0) slots[idx] = session else slots.add(session)
        prefs.edit().putString(KEY_SLOTS, slots.toJson().toString()).apply()
    }

    fun loadSaveSlot(id: String): SavedSession? = readSlots().firstOrNull { it.id == id }

    fun loadSession(): SavedSession? = readSlots().firstOrNull { it.id == "current" }

    fun getSaveSlots(): List<SavedSession> = readSlots()

    fun deleteAll() {
        prefs.edit().remove(KEY_SLOTS).remove(KEY_RUNS).apply()
    }

    fun saveSession(data: IndoorGameUiState, eggs: List<EggObject>, safe: SafeObject?) {
        val safeTrans = safe?.anchorNode?.anchor?.pose?.translation
        val offsets = if (safeTrans != null) eggs.map { egg ->
            val t = egg.anchorNode.anchor?.pose?.translation ?: floatArrayOf(0f, 0f, 0f)
            floatArrayOf(t[0] - safeTrans[0], t[1] - safeTrans[1], t[2] - safeTrans[2])
        } else emptyList()
        val session = SavedSession(
            id = "current",
            savedAt = todayString(),
            slotName = "Partita corrente",
            players = data.activePlayers,
            eggCount = eggs.size,
            riddles = data.riddles,
            eggOffsets = offsets,
            eggColors = eggs.map { it.colorIdx },
            eggShapes = eggs.map { it.shape },
            trapMask = eggs.map { it.isTrap },
            safeType = safe?.type ?: "classic",
            turnMode = data.turnMode
        )
        upsertSaveSlot(session)
    }

    // ── Serialization ─────────────────────────────────────────────

    private fun readSlots(): List<SavedSession> {
        val arr = JSONArray(prefs.getString(KEY_SLOTS, "[]") ?: "[]")
        return (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
    }

    private fun readRuns(): List<GameRun> {
        val arr = JSONArray(prefs.getString(KEY_RUNS, "[]") ?: "[]")
        return (0 until arr.length()).map { parseRun(arr.getJSONObject(it)) }
    }

    private fun parseSession(o: JSONObject): SavedSession = SavedSession(
        id = o.optString("id"),
        savedAt = o.optString("savedAt"),
        slotName = o.optString("slotName"),
        players = o.optJSONArray("players").toStrList(),
        eggCount = o.optInt("eggCount"),
        riddles = o.optJSONArray("riddles").toStrList(),
        parentNote = o.optString("parentNote"),
        eggOffsets = o.optJSONArray("eggOffsets").toFloatList(),
        eggColors = o.optJSONArray("eggColors").toIntList(),
        eggShapes = o.optJSONArray("eggShapes").toStrList(),
        trapMask = o.optJSONArray("trapMask").toBoolList(),
        safeType = o.optString("safeType", "classic"),
        turnMode = o.optString("turnMode", "sequential")
    )

    private fun SavedSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("savedAt", savedAt); put("slotName", slotName)
        put("players", players.toJsonArray())
        put("eggCount", eggCount)
        put("riddles", riddles.toJsonArray())
        put("parentNote", parentNote)
        put("eggOffsets", JSONArray().apply { eggOffsets.forEach { put(it.toJsonArray()) } })
        put("eggColors", eggColors.toJsonArray())
        put("eggShapes", eggShapes.toJsonArray())
        put("trapMask", trapMask.toJsonArray())
        put("safeType", safeType)
        put("turnMode", turnMode)
    }

    private fun parseRun(o: JSONObject): GameRun = GameRun(
        id = o.optString("id"),
        playerName = o.optString("playerName"),
        date = o.optString("date"),
        eggCount = o.optInt("eggCount"),
        eggStats = o.optJSONArray("eggStats").toStatList(),
        totalMs = o.optLong("totalMs")
    )

    private fun GameRun.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("playerName", playerName); put("date", date)
        put("eggCount", eggCount)
        put("eggStats", JSONArray().apply {
            eggStats.forEach { e ->
                put(JSONObject().put("eggNumber", e.eggNumber).put("timeMs", e.timeMs))
            }
        })
        put("totalMs", totalMs)
    }

    @JvmName("sessionsToJsonArray")
    private fun List<SavedSession>.toJson(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it.toJson()) }
        return a
    }

    @JvmName("runsToJsonArray")
    private fun List<GameRun>.toJson(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it.toJson()) }
        return a
    }

    private fun JSONArray?.toStrList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }
    }
    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).map { optInt(it) }
    }
    private fun JSONArray?.toBoolList(): List<Boolean> {
        if (this == null) return emptyList()
        return (0 until length()).map { optBoolean(it) }
    }
    private fun JSONArray?.toFloatList(): List<FloatArray> {
        if (this == null) return emptyList()
        return (0 until length()).map { i -> optJSONArray(i).toFloatArray() }
    }
    private fun JSONArray?.toStatList(): List<EggStat> {
        if (this == null) return emptyList()
        return (0 until length()).map { i ->
            val o = getJSONObject(i)
            EggStat(o.optInt("eggNumber"), o.optLong("timeMs"))
        }
    }
    @JvmName("strListToJsonArray")
    private fun List<String>.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it) }
        return a
    }
    @JvmName("intListToJsonArray")
    private fun List<Int>.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it) }
        return a
    }
    @JvmName("boolListToJsonArray")
    private fun List<Boolean>.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(if (it) 1 else 0) }
        return a
    }
    @JvmName("floatArrayListToJsonArray")
    private fun List<FloatArray>.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it.toJsonArray()) }
        return a
    }
    private fun FloatArray.toJsonArray(): JSONArray {
        val a = JSONArray()
        forEach { a.put(it.toDouble()) }
        return a
    }
    private fun JSONArray?.toFloatArray(): FloatArray {
        if (this == null) return floatArrayOf()
        return FloatArray(length()) { i -> (optDouble(i, 0.0)).toFloat() }
    }

    data class PlayerProfile(val name: String, val color: Int = 0xFF4488AA.toInt())
}

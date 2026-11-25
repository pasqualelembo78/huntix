package com.intelligame.huntix.bridge

import android.app.ActivityManager
import android.content.Context
import com.intelligame.huntix.AppLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Specchio Android del World Streaming di MiCittà.
 *
 * Un "settore" e' una tile OSM (10x10 chunk da 1 km). Lo streaming live in
 * Unity non deve mai cancellare dati del profilo: per questo le visite e le
 * richieste di prefetch vivono in PREF_FILE con chiavi dedicate, separate da
 * city_offline_packs_v1 e da qualunque snapshot CityStateSync.
 *
 * Nessun download automatico: [requestSector] registra soltanto la richiesta
 * e la rimanda a Unity nello stato, cosi' il giocatore resta sempre al comando
 * (stessa regola dei pacchetti offline).
 */
object WorldStreamingMirror {

    private const val TAG = "WorldStreamingMirror"
    private const val PREF_FILE = "world_game_prefs"
    private const val KEY_VISITED = "world_streaming_visited_v1"
    private const val KEY_REQUESTED = "world_streaming_requested"
    private const val KEY_READY = "world_streaming_ready_v1"
    private const val MAX_REQUESTED = 32

    private val lock = Any()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private fun readVisited(ctx: Context): JSONObject = try {
        val raw = prefs(ctx).getString(KEY_VISITED, null)
        if (raw.isNullOrEmpty()) JSONObject() else JSONObject(raw)
    } catch (e: Exception) {
        AppLog.w(TAG, "visite settori non valide, azzerate: ${e.message}")
        JSONObject()
    }

    private fun writeVisited(ctx: Context, o: JSONObject) {
        try {
            prefs(ctx).edit().putString(KEY_VISITED, o.toString()).apply()
        } catch (e: Exception) {
            AppLog.w(TAG, "salvataggio visite settori fallito: ${e.message}")
        }
    }

    /** Catalogo pronto lato Unity: quanti settori esistono e che cache v5 usa. */
    fun onReady(ctx: Context, json: String) {
        synchronized(lock) {
            try {
                val j = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
                prefs(ctx).edit().putString(KEY_READY, JSONObject()
                    .put("sectors", j.optInt("sectors", 0))
                    .put("cacheVersion", j.optInt("cacheVersion", 0))
                    .put("tsMt", System.currentTimeMillis()).toString()).apply()
                AppLog.d(TAG, "WorldStreamingReady: ${j.optInt("sectors", 0)} settori")
            } catch (e: Exception) {
                AppLog.w(TAG, "WorldStreamingReady failed: ${e.message}")
            }
        }
        pushStatus(ctx)
    }

    /** Ingresso in un settore: conta le visite e conserva posizione/timestamp. */
    fun onSectorChanged(ctx: Context, json: String) {
        val j = try { JSONObject(json) } catch (_: Exception) { return }
        val key = j.optString("sector", "")
        if (key.isEmpty()) return
        synchronized(lock) {
            try {
                val all = readVisited(ctx)
                val old = all.optJSONObject(key) ?: JSONObject()
                val rec = JSONObject()
                val it = old.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    rec.put(k, old.opt(k))
                }
                rec.put("visits", old.optInt("visits", 0) + 1)
                rec.put("previous", j.optString("previous", ""))
                rec.put("lat", j.optString("lat", ""))
                rec.put("lng", j.optString("lng", ""))
                rec.put("chunkX", j.optInt("chunkX", 0))
                rec.put("chunkY", j.optInt("chunkY", 0))
                rec.put("teleport", j.optBoolean("teleport", false))
                rec.put("tsMt", j.optLong("tsMt", System.currentTimeMillis()))
                all.put(key, rec)
                writeVisited(ctx, all)
                AppLog.d(TAG, "settore $key attivo (${rec.getInt("visits")} visite)")
            } catch (e: Exception) {
                AppLog.w(TAG, "WorldSectorChanged failed: ${e.message}")
            }
        }
        pushStatus(ctx)
    }

    /** Unity (o l'app) chiede di preparare una tile: solo traccia, mai rete. */
    fun requestSector(ctx: Context, key: String) {
        if (key.isEmpty()) return
        synchronized(lock) {
            try {
                val req = JSONArray(prefs(ctx).getString(KEY_REQUESTED, "[]"))
                val kept = JSONArray()
                val seen = HashSet<String>()
                for (i in req.length() - 1 downTo 0) {
                    val k = req.optString(i, "")
                    if (k.isEmpty() || k == key || seen.contains(k)) continue
                    seen.add(k)
                    kept.put(k)
                }
                // Ordine cronologico: la richiesta piu' recente e' l'ultima.
                val ordered = JSONArray()
                for (i in kept.length() - 1 downTo 0) ordered.put(kept.optString(i))
                while (ordered.length() >= MAX_REQUESTED) ordered.remove(0)
                ordered.put(key)
                prefs(ctx).edit().putString(KEY_REQUESTED, ordered.toString()).apply()
                AppLog.d(TAG, "prefetch richiesto per $key")
            } catch (e: Exception) {
                AppLog.w(TAG, "requestWorldSector failed: ${e.message}")
            }
        }
        pushStatus(ctx)
    }

    /** Stato completo rimandato a Unity (bridge bidirezionale). */
    fun statusJson(ctx: Context): String = synchronized(lock) {
        val out = JSONObject()
        try {
            val visited = readVisited(ctx)
            val keys = visited.keys()
            val sectors = JSONArray()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = visited.optJSONObject(k) ?: continue
                sectors.put(JSONObject()
                    .put("key", k)
                    .put("visits", o.optInt("visits", 0))
                    .put("lat", o.optString("lat", ""))
                    .put("lng", o.optString("lng", ""))
                    .put("tsMt", o.optLong("tsMt", 0L)))
            }
            out.put("visited", visited.length())
                .put("sectors", sectors)
                .put("requested", JSONArray(prefs(ctx).getString(KEY_REQUESTED, "[]")))
            val ready = prefs(ctx).getString(KEY_READY, null)
            if (!ready.isNullOrEmpty()) out.put("ready", JSONObject(ready))
        } catch (e: Exception) {
            AppLog.w(TAG, "statusJson failed: ${e.message}")
        }
        out.toString()
    }

    /** Memoria del device: Unity riduce il budget dello skyline se low=true. */
    fun memoryInfo(ctx: Context): String {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return "{\"low\":false,\"availMb\":0,\"thresholdMb\":0}"
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = mi.availMem / (1024L * 1024L)
            val totalMb = mi.totalMem / (1024L * 1024L)
            val thresholdMb = mi.threshold / (1024L * 1024L)
            JSONObject()
                .put("low", mi.lowMemory || mi.availMem < mi.threshold)
                .put("availMb", availMb)
                .put("totalMb", totalMb)
                .put("thresholdMb", thresholdMb)
                .toString()
        } catch (e: Exception) {
            AppLog.w(TAG, "getDeviceMemoryInfo failed: ${e.message}")
            "{\"low\":false,\"availMb\":0,\"thresholdMb\":0}"
        }
    }

    private fun pushStatus(ctx: Context) {
        UnityIpc.sendToUnity("GameManager", "OnWorldStreamingStatus", statusJson(ctx))
    }
}

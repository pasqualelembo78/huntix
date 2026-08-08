package com.intelligame.huntix.managers

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 📜 CustomPageRegistry — mappa osm_id → pagina JSON personalizzata (modello pay).
 *
 * Un brand pagante è inserito nell'indice remoto
 *   https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/custom_pages_index.json
 * (osm_id → {type:"json"|"web", url:..}). Se un nodo OSM che chiede "pagina"
 * corrisponde a una voce del registry, l'app apre la pagina custom
 * (POICustomPageActivity che rende il JSON del brand). Altrimenti fallback:
 * web → browser; osm → mappa OpenStreetMap.
 *
 * Indice locale di fallback (assets/custom_pages.json) + remoto, merge idempotente.
 */
object CustomPageRegistry {

    private const val TAG = "CustomPageRegistry"
    private const val REMOTE_INDEX_URL =
        "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/custom_pages_index.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    data class PageInfo(val pageType: String, val url: String)

    private val localPages = ConcurrentHashMap<String, PageInfo>()
    private val remotePages = ConcurrentHashMap<String, PageInfo>()
    private val remoteFetched = AtomicLong(0L)
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val ctx = context.applicationContext
        loadLocal(ctx)
        initialized = true
    }

    private fun loadLocal(ctx: Context) {
        try {
            ctx.assets.open("custom_pages.json").use { inp ->
                val root = JSONObject(inp.bufferedReader().use { it.readText() })
                for (key in root.keys()) {
                    val jo = root.getJSONObject(key)
                    localPages[key] = PageInfo(jo.optString("type", "json"), jo.optString("url", ""))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadLocal failed: ${e.message}")
        }
    }

    /** Risolve la pagina custom per un osm_id (es. "osm:node:12345"). */
    fun resolve(osmId: String): PageInfo? {
        refreshRemoteIfNeeded()
        return localPages[osmId] ?: remotePages[osmId]
    }

    private fun refreshRemoteIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - remoteFetched.get() < CACHE_TTL_MS) return
        remoteFetched.set(now)
        Thread {
            try {
                val conn = (URL(REMOTE_INDEX_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(txt)
                    val merged = ConcurrentHashMap<String, PageInfo>()
                    for (key in root.keys()) {
                        val jo = root.getJSONObject(key)
                        merged[key] = PageInfo(jo.optString("type", "json"), jo.optString("url", ""))
                    }
                    remotePages.clear()
                    remotePages.putAll(merged)
                    Log.d(TAG, "remote index fetched: ${merged.size} entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "remote fetch failed: ${e.message}")
            }
        }.start()
    }
}

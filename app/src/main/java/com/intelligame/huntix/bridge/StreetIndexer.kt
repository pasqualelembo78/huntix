package com.intelligame.huntix.bridge

import android.content.Context
import com.intelligame.huntix.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Indice delle vie della mappa per il selettore di spawn ("in quale via?").
 *
 * I nomi delle strade vengono dall'endpoint /api/tiles/streets del tile
 * server (aggregato dai grafi pre-generati d'Italia: nessuna generazione
 * on-demand, risposta veloce). L'indice viene cachato per mappa in filesDir:
 * "aggiornamento della mappa" = scaricarsi le vie della mappa scelta, in base
 * a quale mappa si aggiorna. Il Finder fuzzy propone le vie piu' vicine al
 * testo digitato e, quando ci sono piu' candidati simili (es. via Silvio
 * Pellico vs via Alfonso Pellico), lascia scegliere.
 */
object StreetIndexer {

    private const val TAG = "StreetIndexer"

    private const val BASE_URL = "http://82.165.218.56:5100"
    private const val STREETS_RADIUS_M = 15000
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 60000

    /** Una via della mappa con un punto rappresentativo SULLA via. */
    data class Street(val name: String, val lat: Double, val lng: Double)

    /** Chiave cache sicura per nome mappa. */
    fun safeKey(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48)

    private fun cacheFile(ctx: Context, cacheKey: String): File =
        File(ctx.filesDir, "streets_$cacheKey.json")

    private fun areaKey(lat: Double, lng: Double): String =
        "area_" + Math.round(lat * 100) + "_" + Math.round(lng * 100)

    /** Vie cachate della mappa (lista vuota se non ancora aggiornata). */
    fun loadCached(ctx: Context, cacheKey: String): List<Street> {
        val f = cacheFile(ctx, cacheKey)
        if (!f.exists() || f.length() < 2L) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).optJSONArray("streets") ?: JSONArray()
            parseStreets(arr)
        } catch (e: Exception) {
            AppLog.w(TAG, "cache vie illeggibile ${f.name}: ${e.message}")
            emptyList()
        }
    }

    private fun parseStreets(arr: JSONArray): List<Street> {
        val out = ArrayList<Street>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("s").trim()
            if (name.isEmpty()) continue
            out.add(Street(name, o.optDouble("la", 0.0), o.optDouble("lo", 0.0)))
        }
        return out
    }

    /** Salva (o aggiorna) l'indice delle vie della mappa. */
    fun cache(ctx: Context, cacheKey: String, streets: List<Street>) {
        if (streets.isEmpty()) return
        try {
            val arr = JSONArray()
            for (s in streets) {
                arr.put(JSONObject().put("s", s.name).put("la", s.lat).put("lo", s.lng))
            }
            val payload = JSONObject().put("streets", arr).toString()
            val dest = cacheFile(ctx, cacheKey)
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(dest)) dest.writeText(payload)
        } catch (e: Exception) {
            AppLog.w(TAG, "salvataggio indice vie fallito: ${e.message}")
        }
    }

    /**
     * Vie della mappa attorno a (lat, lng): cache-first, altrimenti scarica
     * dall'endpoint e aggiorna la cache. Da chiamare su thread di background.
     */
    fun ensure(ctx: Context, cacheKey: String, lat: Double, lng: Double,
               radiusM: Int = STREETS_RADIUS_M): List<Street> {
        val cached = loadCached(ctx, cacheKey)
        if (cached.isNotEmpty()) return cached
        val fresh = fetch(lat, lng, radiusM)
        if (fresh.isNotEmpty()) cache(ctx, cacheKey, fresh)
        return fresh
    }

    /** Alias per l'aggiornamento mappa: cache per la zona vista. */
    fun ensureArea(ctx: Context, lat: Double, lng: Double,
                   force: Boolean = false): List<Street> {
        val key = areaKey(lat, lng)
        if (force && loadCached(ctx, key).isNotEmpty()) {
            val fresh = fetch(lat, lng, STREETS_RADIUS_M)
            if (fresh.isNotEmpty()) cache(ctx, key, fresh)
            return if (fresh.isNotEmpty()) fresh else loadCached(ctx, key)
        }
        return ensure(ctx, key, lat, lng, STREETS_RADIUS_M)
    }

    /** Scarica le vie dal tile server; lista vuota su errore. */
    private fun fetch(lat: Double, lng: Double, radiusM: Int): List<Street> {
        var conn: HttpURLConnection? = null
        val url = "$BASE_URL/api/tiles/streets?lat=$lat&lon=$lng&radius_m=$radiusM"
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                AppLog.w(TAG, "vie HTTP ${conn.responseCode} per $url")
                return emptyList()
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("streets") ?: JSONArray()
            val streets = parseStreets(arr)
            AppLog.d(TAG, "scaricate ${streets.size} vie della mappa (${text.length / 1024}KB)")
            return streets
        } catch (e: Exception) {
            AppLog.w(TAG, "fetch vie fallito: ${e.message}")
            return emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Finder fuzzy per i nomi delle vie: riconosce anche una digitazione parziale
 * ("pellico") proponendo le vie piu' vicine ("Via Silvio Pellico", "Via
 * Alfonso Pellico") e lasciando scegliere.
 */
object FuzzyStreet {

    private val PREFIX = Regex(
        "^(via|viale|vicolo|vico|corso|piazza|piazzale|largo|contrada|strada|" +
        "traversa|galleria|ponte|salita|calle|borgo|lungomare|circonvallazione|" +
        "tangenziale|frazione|localita)\\s+")
    private val NOISE = Regex(
        "\\b(di|del|della|delle|degli|dal|dallo|dai|dalle|dei|sul|sulla|sulle|" +
        "sugli|nel|nella|nelle|negli|san|santa|st)\\b")

    data class Scored(val street: StreetIndexer.Street, val score: Int)

    /** Normalizza: minuscole, senza accenti, via prefissi, spazi compatti. */
    fun norm(raw: String): String {
        var t = java.text.Normalizer.normalize(raw.lowercase(),
            java.text.Normalizer.Form.NFD)
        t = t.replace("\\p{Mn}+".toRegex(), "")
        t = t.trim().replace(PREFIX, "")
        t = t.replace(NOISE, " ")
        return t.trim().replace("\\s+".toRegex(), " ")
    }

    fun score(query: String, name: String): Int {
        val q = norm(query)
        val s = norm(name)
        if (q.isEmpty() || s.isEmpty()) return 0
        if (q == s) return 1000
        if (s.startsWith(q)) return 800 - (s.length - q.length) * 4
        if (s.contains(q)) return 620 - (s.length - q.length) * 3
        val qs = q.split(" ").toSet()
        val ss = s.split(" ").toSet()
        val common = qs.intersect(ss).size
        if (common == 0) {
            val d = lev(q, s)
            if (d <= 2 && s.length >= 4) return 660 - d * 100
            return 0
        }
        val matchAll = ss.containsAll(qs)
        val base = if (matchAll) 760 + common * 10 else 430 + common * 12
        return base - (ss.size - qs.size) * 6
    }

    /** Le vie piu' vicine alla query, in ordine di punteggio. */
    fun suggest(query: String, streets: List<StreetIndexer.Street>,
                limit: Int = 6): List<Scored> {
        val res = ArrayList<Scored>()
        for (s in streets) {
            val sc = score(query, s.name)
            if (sc > 0) res.add(Scored(s, sc))
        }
        res.sortByDescending { it.score }
        return if (res.size > limit) res.subList(0, limit) else res
    }

    /** Distanza Levenshtein (limitata per contrastare i refusi). */
    private fun lev(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        if (Math.abs(n - m) > 3) return 4
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val ins = curr[j - 1] + 1
                val del = prev[j] + 1
                val sub = prev[j - 1] + cost
                curr[j] = Math.min(Math.min(ins, del), sub)
            }
            val t = prev
            prev = curr
            curr = t
        }
        return prev[m]
    }
}

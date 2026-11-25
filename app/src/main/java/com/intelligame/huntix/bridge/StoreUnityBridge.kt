package com.intelligame.huntix.bridge

import android.content.Intent
import android.net.Uri
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.ui.IndoorActivity
import com.intelligame.huntix.ui.POICustomPageActivity
import com.intelligame.huntix.managers.CustomPageRegistry
import com.intelligame.huntix.managers.PoiSearchManager
import com.intelligame.huntix.reallife.OsmCityJsonFactory
import com.intelligame.huntix.reallife.OsmClient
import com.intelligame.huntix.reallife.OsmPoiRepository
import com.intelligame.huntix.reallife.PoiJsonFactory
import com.unity3d.player.UnityPlayer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * StoreUnityBridge — ponte chiamato DALLO store Unity.
 *
 * I metodi sono statici (@JvmStatic) così il codice C# può invocarli
 * via `AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge")`.
 */
object StoreUnityBridge {

    private const val TAG = "StoreUnityBridge"

    private const val MAX_POIS = 1200

    /**
     * Shutdown dell'engine Unity in corso (uscita dalla scena). I thread di fetch
     * (requestOsmCity/requestPoisNearby) verificano questo flag PRIMA di chiamare
     * UnitySendMessage: durante il teardown dell'activity il runtime Unity è in
     * smontaggio e un messaggio in volo può causare un crash nativo del processo.
     */
    @Volatile
    private var shuttingDown = false

    /**
     * Fasi progressive del caricamento Esplora: prima i negozi più vicini
     * (query Overpass veloce) e poi si espande la zona. Ogni fase arriva a
     * Unity come aggiornamento parziale (envelope con `done=false`); l'ultima
     * chiude con `done=true` e nasconde la barra di caricamento in Unity.
     */
    private val POI_STAGES_M = intArrayOf(1000, 3000, 10000)

    /** Cache POI a livello di processo: stessa zona recente → risposta immediata senza Overpass. */
    private const val POI_CACHE_MAX_AGE_MS = 10 * 60 * 1000L
    private const val POI_CACHE_MAX_DIST_M = 2000.0
    private var poiCacheCenterLat = 0.0
    private var poiCacheCenterLng = 0.0
    private var poiCacheTime = 0L
    private var poiCacheEnvelope: String? = null

    /** Scrive un log nel sistema AppLog dell'app, richiesto dal codice Unity
     *  (MiAcitma): il viewer log dell'app così mostra anche il comportamento
     *  della sezione MiAcitma che vive interamente in Unity. */
    @JvmStatic
    fun logFromUnity(tag: String, message: String) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("logFromUnity", JSONArray().put(tag).put(message).toString())
            return
        }
        AppLog.d(tag, message)
    }

    /** Chiamato da Unity quando la scena del negozio è pronta. */
    @JvmStatic
    fun onIndoorSceneReady(poiId: String) {
        IndoorActivity.instance?.onIndoorSceneReady(poiId)
    }

    /** Chiamato da Unity (scena City) quando i chunk della citta' sono pronti:
     *  chiude l'overlay di caricamento nativo mostrato da BridgeActivity finche'
     *  la citta' non e' giocabile. */
    @JvmStatic
    fun onCityReady() {
        val activity = UnityPlayer.currentActivity
        if (activity is BridgeActivity) {
            AppLog.d(TAG, "CityReady: overlay di caricamento della citta' chiuso")
            activity.runOnUiThread { activity.dismissCityLoading() }
        }
    }

    /** Chiamato da Unity (scena City) a ogni avanzamento del caricamento dei
     *  chunk: aggiorna l'overlay con fase, percentuale e byte letti. JSON:
     *  {"phase":"Mappa|Costruzione", "percent":0..95, "bytes":N, "section":".."} */
    @JvmStatic
    fun onCityProgress(json: String) {
        val activity = UnityPlayer.currentActivity
        if (activity !is BridgeActivity) return
        val j = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
        val phase = j.optString("phase", "")
        val section = j.optString("section", "")
        val percent = j.optInt("percent", 0)
        val bytes = j.optLong("bytes", 0L)
        activity.runOnUiThread {
            activity.updateCityProgress(phase, section, percent, bytes)
        }
    }

    /** Chiamato da Unity per uscire dal negozio. */
    @JvmStatic
    fun exitIndoor() {
        endUnitySession()
        finishUnityActivity(IndoorActivity.instance)
    }

    /** Chiamato da Unity (scena City, "La Mia Città") per tornare alla Home:
     *  chiude l'Activity Unity (BridgeActivity, che sta sopra HomeActivity nel
     *  back stack) come fa exitIndoor per il negozio. */
    @JvmStatic
    fun exitMiacitta() {
        val activity = UnityPlayer.currentActivity ?: return
        endUnitySession()
        AppLog.d(TAG, "exitMiacitta: ritorno alla Home (shutdown fetch + stop GPS)")
        finishUnityActivity(activity)
    }

    /** Contesto applicativo cachato all'avvio del tracking GPS: serve per
     *  fermare il tracking quando l'Activity Unity e' gia' distrutta
     *  (UnityPlayer.currentActivity == null). */
    @Volatile
    private var trackedAppContext: android.content.Context? = null

    /**
     * Unity registra il proprio persistentDataPath (dove TileClient fa
     * cache-first delle tile): il preloader di avvio (CityTilePreloader)
     * lo riusa cosi' i file vengono scritti ESATTAMENTE dove Unity li cerca,
     * indipendentemente da storage interno/esterno del device.
     */
    @Suppress("unused")
    @JvmStatic
    fun setTileCacheDir(path: String) {
        if (UnityIpc.isUnity) {
            // La prefs va scritta nel processo app: e' li' che CityTilePreloader
            // la legge per scrivere le tile nella cache di Unity.
            UnityIpc.callApp("setTileCacheDir", JSONArray().put(path).toString())
            return
        }
        try {
            val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ctx?.getSharedPreferences("world_game_prefs", android.content.Context.MODE_PRIVATE)?.edit()
                ?.putString("tile_cache_dir", path)
                ?.apply()
            AppLog.d(TAG, "setTileCacheDir: $path")
        } catch (e: Exception) {
            AppLog.w(TAG, "setTileCacheDir failed: ${e.message}")
        }
    }

    // ── Pacchetti offline (Unity <-> Android) ────────────────────────────

    /** Unity chiede lo stato dei pacchetti: flag utente + stato reale su disco. */
    @JvmStatic
    fun getOfflinePackStatus(): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("getOfflinePackStatus", JSONArray().toString())
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return "{}"
        return OfflinePackManager.statusJson(ctx)
    }

    /**Unity setterà/letterà i flag anche dal menu di MiCittà. */
    @JvmStatic
    fun setOfflinePackFlag(id: String, enabled: Boolean) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("setOfflinePackFlag",
                JSONArray().put(id).put(enabled).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        OfflinePackManager.setFlag(ctx, id, enabled)
    }

    /** Richiesta di download arrivata da Unity: gira su thread di lavoro. */
    @JvmStatic
    fun requestOfflinePack(id: String) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("requestOfflinePack", JSONArray().put(id).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        Thread {
            try {
                OfflinePackManager.downloadPack(ctx, id) { _, _, _, _ -> }
            } catch (e: Exception) {
                AppLog.w(TAG, "requestOfflinePack $id: ${e.message}")
            }
        }.start()
    }

    /** True se una tile e' completamente presente nella cache v5. */
    @JvmStatic
    fun isTileOffline(key: String): Boolean {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("isTileOffline",
                JSONArray().put(key).toString()).toBoolean()
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return false
        val graph = CityTilePreloader.cachedFile(ctx, key, "graph")
        val geo = CityTilePreloader.cachedFile(ctx, key, "geo")
        return graph.exists() && graph.length() > 0L && geo.exists() && geo.length() > 0L
    }

    // ── Gestione spazio selettiva (solo cache tile di MiCittà) ────────────

    /**
     * Pattern dei SOLI file che possiamo leggere/cancellare: tile v1..v9
     * (grafo/geo) e i loro temporanei di download. Qualsiasi altro file della
     * cartella (profilo, snapshot, log, preferenze) resta invisibile qui.
     */
    private val tileCacheFilePattern = Regex("""^.+\.v[1-9]\.(?:graph|geo)\.json(?:\.tmp)?$""")
    private val tileCacheVersionPattern = Regex("""\.v([1-9])\.(?:graph|geo)\.json""")

    private const val TILE_CACHE_CURRENT_VERSION = 5
    private const val GEOCODE_MIN_INTERVAL_MS = 1_100L
    private const val GEOCODE_CACHE_TTL_MS = 10 * 60 * 1000L
    private const val GEOCODE_CACHE_MAX = 24

    private data class GeocodeCacheEntry(val at: Long, val json: String)

    /** Piccola cache in memoria: tap ripetuti sulla stessa destinazione non
     *  generano richieste Nominatim (e il callback arriva comunque a Unity). */
    private val geocodeCache = LinkedHashMap<String, GeocodeCacheEntry>()
    private val geocodePaceLock = Any()
    private var lastGeocodeRequestMs = 0L

    private fun tileCacheContext() =
        UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context

    /** File regolari veri: esclude directory e link simbolici (che potrebbero
     *  puntare fuori dalla cartella della cache). */
    private fun isTileCacheRegularFile(f: File): Boolean = try {
        java.nio.file.Files.isRegularFile(f.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
    } catch (_: Exception) {
        f.isFile
    }

    private fun emptyTileCacheStats(): String = JSONObject()
        .put("bytes", 0L).put("files", 0).put("current", 0)
        .put("legacy", 0).put("temp", 0).put("dir", "").toString()

    /** Scansiona ESCLUSIVAMENTE CityTilePreloader.cacheDir(ctx): mai la root
     *  di persistentDataPath, mai profilo/uova/prefs/snapshot/log. */
    private fun tileCacheStatsJson(ctx: android.content.Context): String {
        val dir = try { CityTilePreloader.cacheDir(ctx) } catch (_: Exception) { null }
            ?: return emptyTileCacheStats()
        var bytes = 0L
        var files = 0
        var current = 0
        var legacy = 0
        var temp = 0
        try {
            val children = dir.listFiles() ?: emptyArray()
            for (f in children) {
                if (!isTileCacheRegularFile(f)) continue
                val name = f.name
                if (!tileCacheFilePattern.matches(name)) continue
                files++
                bytes += try { f.length() } catch (_: Exception) { 0L }
                if (name.endsWith(".tmp")) {
                    temp++
                } else {
                    val version = tileCacheVersionPattern.find(name)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    if (version == TILE_CACHE_CURRENT_VERSION) current++
                    else if (version != null && version in 1..4) legacy++
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "tileCacheStats: scansione fallita: ${e.message}")
        }
        return JSONObject()
            .put("bytes", bytes)
            .put("files", files)
            .put("current", current)
            .put("legacy", legacy)
            .put("temp", temp)
            .put("dir", dir.absolutePath)
            .toString()
    }

    /** Statistiche della cache tile v5, lette da Unity o dalla schermata Android. */
    @JvmStatic
    fun tileCacheStats(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("tileCacheStats", JSONArray().toString())
        val ctx = tileCacheContext() ?: return emptyTileCacheStats()
        return tileCacheStatsJson(ctx)
    }

    /** Overload senza argomenti per il codice C# che invoca il metodo statico
     *  diretto (Kotlin/default arg non genera un overload no-arg per JNI). */
    @JvmStatic
    fun clearTileCache(): String = clearTileCache(-1)

    /**
     * Cancella SOLO i file regolari delle tile nella cartella cache, opzionalmente
     * più vecchi di [olderThanDays] giorni (-1 = tutti). Ritorna le stesse
     * statistiche di [tileCacheStats] dopo la pulizia.
     */
    @JvmStatic
    fun clearTileCache(olderThanDays: Int = -1): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("clearTileCache", JSONArray().put(olderThanDays).toString())
        }
        val ctx = tileCacheContext() ?: return emptyTileCacheStats()
        val cutoff = if (olderThanDays >= 0)
            System.currentTimeMillis() - olderThanDays.toLong() * 86_400_000L
        else Long.MIN_VALUE
        var deleted = 0
        var failed = 0
        var freed = 0L
        val dir = try { CityTilePreloader.cacheDir(ctx) } catch (_: Exception) { null }
        try {
            if (dir != null) {
                for (f in dir.listFiles() ?: emptyArray()) {
                    if (!isTileCacheRegularFile(f)) continue
                    if (!tileCacheFilePattern.matches(f.name)) continue
                    if (f.lastModified() >= cutoff) continue
                    val len = try { f.length() } catch (_: Exception) { 0L }
                    if (f.delete()) {
                        deleted++
                        freed += len
                    } else {
                        failed++
                    }
                }
            }
            AppLog.i(TAG, "clearTileCache: cancellati $deleted file (${freed / 1_000_000} MB), " +
                "falliti $failed, olderThanDays=$olderThanDays, dir=${dir?.absolutePath ?: "-"}")
        } catch (e: Exception) {
            AppLog.w(TAG, "clearTileCache failed: ${e.message}")
        }
        val stats = tileCacheStatsJson(ctx)
        // Unity svuota il prefetch in memoria e assegna una sola volta l'uovo
        // "Archivista del Goblin". Un invio fallito non è un errore per l'utente:
        // la pulizia dei file è già completata e il JSON è lo stesso ritornato.
        try {
            UnityIpc.sendToUnity("GameManager", "TileCacheCleared", stats)
        } catch (t: Throwable) {
            AppLog.w(TAG, "TileCacheCleared: invio a Unity fallito: ${t.message}")
        }
        return stats
    }

    private fun geocodeError(query: String, error: String): String = JSONObject()
        .put("ok", false)
        .put("query", query)
        .put("error", error)
        .toString()

    /** Attende almeno ~1s fra due richieste di rete reali (Nominatim chiede
     *  pacing gentile) e rispetta l'interrupt del thread. */
    private fun paceGeocodeRequest() {
        while (true) {
            val wait = synchronized(geocodePaceLock) {
                val now = System.currentTimeMillis()
                val remaining = GEOCODE_MIN_INTERVAL_MS - (now - lastGeocodeRequestMs)
                if (remaining <= 0L) {
                    lastGeocodeRequestMs = now
                    0L
                } else remaining
            }
            if (wait <= 0L) break
            try {
                Thread.sleep(wait)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }
        }
        if (Thread.currentThread().isInterrupted) throw InterruptedException("richiesta interrotta")
    }

    /** Nominatim → envelopeUnity. BLOCCANTE: da chiamare solo dal thread di
     *  background di [geocodeAddress]. */
    private fun fetchGeocode(query: String): String {
        val key = query.lowercase(Locale.ITALIAN)
        val now = System.currentTimeMillis()
        synchronized(geocodeCache) {
            val hit = geocodeCache[key]
            if (hit != null && now - hit.at < GEOCODE_CACHE_TTL_MS) return hit.json
        }

        paceGeocodeRequest()

        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=3&q=" +
            URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        val body = try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            // User-Agent esplicito: Nominatim rifiuta le richieste senza.
            conn.setRequestProperty("User-Agent", "HuntixGame/1.0 (Android; MiCitta)")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Language", "it")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $code da Nominatim")
            }
            conn.inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } finally {
            conn.disconnect()
        }

        val found = try { JSONArray(body) } catch (_: Exception) {
            throw RuntimeException("Risposta Nominatim non valida")
        }
        val results = JSONArray()
        for (i in 0 until minOf(found.length(), 3)) {
            val item = found.optJSONObject(i) ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lng = item.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val display = item.optString("display_name", "")
            val row = JSONObject()
            row.put("name", display.ifBlank { item.optString("name", "Luogo") })
            row.put("lat", lat)
            row.put("lng", lng)
            row.put("kind", "indirizzo")
            row.put("type", item.optString("type", item.optString("category", "")))
            results.put(row)
        }
        val envelope = JSONObject()
            .put("ok", true)
            .put("query", query)
            .put("results", results)
            .toString()
        synchronized(geocodeCache) {
            geocodeCache[key] = GeocodeCacheEntry(System.currentTimeMillis(), envelope)
            while (geocodeCache.size > GEOCODE_CACHE_MAX) {
                geocodeCache.remove(geocodeCache.keys.first())
            }
        }
        return envelope
    }

    /**
     * Ricerca destinazione (via + città, POI, indirizzo) lato Android. Gira su
     * un thread di background e invia SEMPRE un callback a Unity:
     * OnGeocodeResult("GameManager") con {"ok":true,...} o {"ok":false,...}.
     * Nessuna eccezione through il bridge: errori e query vuota diventano JSON.
     */
    @JvmStatic
    fun geocodeAddress(query: String) {
        val q = try { query.trim() } catch (_: Exception) { "" }
        fun send(methodResult: String) {
            try {
                UnityIpc.sendToUnity("GameManager", "OnGeocodeResult", methodResult)
            } catch (t: Throwable) {
                AppLog.w(TAG, "OnGeocodeResult: invio fallito: ${t.message}")
            }
        }
        try {
            Thread {
                val payload = try {
                    if (q.isEmpty()) throw IllegalArgumentException("Inserisci un indirizzo o un luogo")
                    fetchGeocode(q)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    AppLog.w(TAG, "geocodeAddress interrotta: ${e.message}")
                    geocodeError(q, "Ricerca interrotta")
                } catch (e: Exception) {
                    AppLog.w(TAG, "geocodeAddress '$q' failed: ${e.message}")
                    geocodeError(q, e.message ?: "Errore nella ricerca")
                }
                send(payload)
            }.apply {
                name = "huntix-geocode"
                isDaemon = true
            }.start()
        } catch (e: Exception) {
            AppLog.w(TAG, "geocodeAddress: thread non avviato: ${e.message}")
            send(geocodeError(q, "Ricerca non disponibile"))
        }
    }

    // ── World Streaming (Unity <-> Android) ───────────────────────────────

    private fun appContext() =
        UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context

    /** Stato dei settori visitati e delle richieste di prefetch. */
    @JvmStatic
    fun getWorldStreamingStatus(): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("getWorldStreamingStatus", JSONArray().toString())
        }
        val ctx = appContext() ?: return "{}"
        return WorldStreamingMirror.statusJson(ctx)
    }

    /** Unity chiede all'app di ricordare la tile che sta per caricare. */
    @JvmStatic
    fun requestWorldSector(key: String) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("requestWorldSector", JSONArray().put(key).toString())
            return
        }
        val ctx = appContext() ?: return
        WorldStreamingMirror.requestSector(ctx, key)
    }

    /** Soglia di memoria per ridurre il dettaglio dello skyline lontano. */
    @JvmStatic
    fun getDeviceMemoryInfo(): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("getDeviceMemoryInfo", JSONArray().toString())
        }
        val ctx = appContext() ?: return "{\"low\":false}"
        return WorldStreamingMirror.memoryInfo(ctx)
    }

    /** Eventi Unity -> app instradati da Bridge (APP_EVENTS): pref dedicati. */
    @JvmStatic
    fun onWorldStreamingReady(json: String) {
        val ctx = appContext() ?: return
        WorldStreamingMirror.onReady(ctx, json)
    }

    @JvmStatic
    fun onWorldSectorChanged(json: String) {
        val ctx = appContext() ?: return
        WorldStreamingMirror.onSectorChanged(ctx, json)
    }

    /** Prefs lette da Unity (UnityBridge.GetSharedPreference): in :unity non
     *  esistono, quindi la lettura viene delegata al processo app. */
    @JvmStatic
    fun getSharedPreference(key: String): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("getSharedPreference", JSONArray().put(key).toString())
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return ""
        return ctx.getSharedPreferences("huntix_prefs", android.content.Context.MODE_PRIVATE)
            .getString(key, null)
            ?: ctx.getSharedPreferences("world_game_prefs", android.content.Context.MODE_PRIVATE)
                .getString(key, null)
            ?: ""
    }

    /** Scrittura simmetrica di [getSharedPreference], sempre nel processo app. */
    @JvmStatic
    fun setSharedPreference(key: String, value: String) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("setSharedPreference", JSONArray().put(key).put(value).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        try {
            ctx.getSharedPreferences("huntix_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString(key, value).apply()
        } catch (e: Exception) {
            AppLog.w(TAG, "setSharedPreference failed: ${e.message}")
        }
    }

    /** Lock che rende ATOMICO il check di shuttingDown + UnitySendMessage: un
     *  thread di fetch puo' passare il check (flag ancora false) un istante
     *  prima che endUnitySession fissi il flag, e inviare SUL runtime in
     *  smontaggio = SIGSEGV del processo. Con il lock invio e teardown si
     *  escludono a vicenda: endUnitySession attende ogni invio in corso e da
     *  quel momento nessun nuovo messaggio puo' piu' partire. */
    private val unitySendLock = Any()

    /** Inizio sessione Unity (BridgeActivity.onCreate): riabilita gli invii a
     *  Unity dopo l'eventuale uscita precedente. */
    @JvmStatic
    fun beginUnitySession() {
        synchronized(unitySendLock) { shuttingDown = false }
    }

    /** Fine sessione Unity (BridgeActivity.onDestroy / exitMiacitta): blocca
     *  OGNI invio a Unity da qualunque thread e ferma il tracking GPS. Con
     *  l'engine in smontaggio un UnitySendMessage in volo e' crash nativo. */
    @JvmStatic
    fun endUnitySession() {
        synchronized(unitySendLock) { shuttingDown = true }
        if (UnityIpc.isUnity) {
            // Il tracking GPS vive nel processo app: fermarlo li', altrimenti
            // resta attivo dopo la chiusura di Miacitta.
            UnityIpc.callApp("stopLocationTracking")
        }
        stopLocationTrackingInternal()
    }

    /** Ferma il tracking GPS avviato con startLocationTracking (idempotente). */
    @JvmStatic
    fun stopLocationTracking() = stopLocationTrackingInternal()

    private fun stopLocationTrackingInternal() {
        try {
            val ctx = trackedAppContext ?: UnityPlayer.currentActivity?.applicationContext
                ?: UnityAppContext.context
            if (ctx != null) {
                com.intelligame.huntix.legacy.poi.gps.OutdoorManager.get(ctx).stop()
                AppLog.d(TAG, "stopLocationTracking: tracking GPS fermato")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "stopLocationTracking failed: ${e.message}")
        }
    }

    /** Invio generico sorvegliato verso Unity: scarta i messaggi se la sessione
     *  e' chiusa (engine in teardown o assente). Da usare per TUTTI gli invii
     *  esterni a StoreUnityBridge (messenger POI, Indoor, eventi legacy). */
    @JvmStatic
    fun sendToUnityIfAlive(gameObject: String, method: String, arg: String) {
        if (UnityIpc.isUnity) {
            sendLocalToUnity(gameObject, method, arg)
        } else {
            // Nel processo app non c'e' runtime Unity: passa dal Service :unity.
            UnityIpc.sendToUnity(gameObject, method, arg)
        }
    }

    /** Invio REALE a Unity: va eseguito solo dentro :unity. Lock + flag di
     *  shutdown restano qui, cosi' check e UnitySendMessage sono atomici. */
    @JvmStatic
    fun sendLocalToUnity(gameObject: String, method: String, arg: String) {
        synchronized(unitySendLock) {
            if (shuttingDown) return
            try {
                UnityPlayer.UnitySendMessage(gameObject, method, arg)
            } catch (e: Exception) {
                AppLog.w(TAG, "sendLocalToUnity($method): ${e.message}")
            }
        }
    }

    /** Chiude una Activity Unity in modo sicuro: se la Activity e una
     *  BridgeActivity mette in pausa il renderer e attende lo svuotamento della
     *  coda buffer prima di distruggere la surface (evita BLASTBufferQueue dtor
     *  / SIG 9). Per le altre Activity chiude direttamente, comunque fuori dal
     *  callback JNI (sempre asincrono per non rientrare nel teardown engine). */
    private fun finishUnityActivity(activity: android.app.Activity?) {
        if (activity == null) return
        AppLog.d(TAG, "finishUnityActivity: chiusura asincrona (activity=" + activity.javaClass.simpleName + ")")
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (activity is BridgeActivity) {
                activity.pauseUnityThenFinish()
            } else {
                activity.finish()
            }
        }
    }

    /** Restituisce il JSON del POI corrente. */
    @JvmStatic
    fun getPoiData(): String = IndoorActivity.instance?.poiJson ?: "{}"

    /** Chiamato da Unity quando un oggetto interactable è in range. */
    @JvmStatic
    fun onInteractableFound(json: String) {
        IndoorActivity.instance?.onInteractableFound(json)
    }

    /** Chiamato da Unity quando l'interazione è completata. */
    @JvmStatic
    fun onInteractionResult(json: String) {
        IndoorActivity.instance?.onInteractionResult(json)
    }

    /** Chiamato da Unity quando un NPC è vicino. */
    @JvmStatic
    fun onNPCNearby(json: String) {
        IndoorActivity.instance?.onNPCNearby(json)
    }

    /** Chiamato da Unity quando un NPC parla. */
    @JvmStatic
    fun onNPCDialogue(json: String) {
        IndoorActivity.instance?.onNPCDialogue(json)
    }

    /** Chiamato da Unity quando il giocatore si allontana da un NPC. */
    @JvmStatic
    fun onNPCFar(json: String) {
        IndoorActivity.instance?.onNPCFar(json)
    }

    /** Chiamato da Unity quando il giocatore accetta una quest NPC. */
    @JvmStatic
    fun onNPCQuestAccepted(json: String) {
        IndoorActivity.instance?.onNPCQuestAccepted(json)
    }

    /** Chiamato da Unity quando AR trova un piano. */
    @JvmStatic
    fun onARPlaneFound(json: String) {
        IndoorActivity.instance?.onARPlaneFound(json)
    }

    /** Mostra il banner "Turno di [nome]" in Unity 3D. */
    @JvmStatic
    fun showTurnBanner(playerName: String) {
        sendToUnityIfAlive("IndoorManager", "ShowTurnBanner", playerName)
    }

    /** Nasconde il banner turno in Unity. */
    @JvmStatic
    fun hideTurnBanner() {
        sendToUnityIfAlive("IndoorManager", "HideTurnBanner", "")
    }

    /** Aggiornamento bisogni ricevuto da Unity dopo interazione con mobili. */
    @JvmStatic
    fun onNeedsUpdated(json: String) {
        IndoorActivity.instance?.onNeedsUpdated(json)
    }

    /** Chiamato da Unity quando un NPC è lontano (outdoor). */
    @JvmStatic
    fun onOutdoorNPCFar(json: String) {
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return
        val name = extractJsonField(json, "name") ?: "La guida"
        postToast(ctx, "$name si allontana", long = false)
    }

    /** Chiamato da Unity quando un NPC parla (outdoor) — mostra dialogo come toast lungo. */
    @JvmStatic
    fun onOutdoorNPCDialogue(json: String) {
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return
        val name = extractJsonField(json, "name") ?: "Guida"
        val dialogue = extractJsonField(json, "dialogue") ?: ""
        val poiName = extractJsonField(json, "poiName") ?: ""
        val msg = if (poiName.isNotEmpty()) "$name: $dialogue" else "$name dice: $dialogue"
        postToast(ctx, msg, long = true)
    }

    /** Chiamato da Unity quando un NPC è vicino (outdoor) — mostra notifica breve. */
    @JvmStatic
    fun onOutdoorNPCNearby(json: String) {
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return
        val name = extractJsonField(json, "name") ?: "La guida"
        val emoji = extractJsonField(json, "emoji") ?: ""
        val poiName = extractJsonField(json, "poiName") ?: ""
        val msg = if (poiName.isNotEmpty()) "$emoji  $name — vicino a $poiName" else "$emoji  $name"
        postToast(ctx, msg, long = false)
    }

    /** Chiamato da Unity per info su un NPC/POI (outdoor). */
    @JvmStatic
    fun onOutdoorNPCInfo(json: String) {
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return
        val poiName = extractJsonField(json, "poiName") ?: "luogo"
        val category = extractJsonField(json, "category") ?: ""
        val msg = "📍  Info su: $poiName${if (category.isNotEmpty()) " ($category)" else ""}"
        postToast(ctx, msg, long = true)
    }

    /** Toast sul main thread dell'app: qui il contesto puo' essere quello
     *  dell'Application perche' il router gira nel processo app. */
    private fun postToast(ctx: android.content.Context, msg: String, long: Boolean) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                ctx, msg,
                if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun extractJsonField(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)

    // ── Esplora (Unity) ─────────────────────────────────────────────
    /** Posizione corrente reale/mock, JSON {"lat":..,"lng":..,"mock":bool}. */
    @JvmStatic
    fun getCurrentLocation(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getCurrentLocation")
        return com.intelligame.huntix.bridge.Bridge.getCurrentLocation()
    }

    /**
     * Avvia il tracking GPS (legacy OutdoorManager, FusedLocationProviderClient)
     * così la città OSM di MiAcitma può seguire il giocatore reale.
     * Nessun effetto se il permesso location è negato (si resta sul default Roma).
     */
    @JvmStatic
    fun startLocationTracking() {
        // Nuova sessione City: il flag di shutdown della scorsa uscita non deve
        // bloccare gli invii a Unity di questa sessione.
        shuttingDown = false
        if (UnityIpc.isUnity) {
            // Il GPS e' letto dall'app (Bridge.getCurrentLocation gira li'):
            // avviare OutdoorManager in :unity creerebbe un tracker cieco.
            UnityIpc.callApp("startLocationTracking")
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        trackedAppContext = ctx   // serve a stopLocationTracking dopo la distruzione dell'Activity
        AppLog.d(TAG, "startLocationTracking: avvio tracking GPS MiAcitma")
        try {
            com.intelligame.huntix.legacy.poi.gps.OutdoorManager.get(ctx).start(true)
            AppLog.d(TAG, "startLocationTracking: OutdoorManager.start ok")
        } catch (e: Exception) {
            // Permesso location negato / sensore non disponibile: si resta sul
            // default Roma. Non propagare MAI verso Unity: ucciderebbe il
            // bootstrap della città OSM (StartCoroutine non partirebbe).
            AppLog.w(TAG, "startLocationTracking failed: ${e.message}")
        }
    }

    /**
     * Richiede i dati OSM completi (strade + edifici + alberi + parchi) per
     * l'area circolare di [radiusMeters] attorno a (lat,lng). Usata da MiAcitma
     * (scena Unity "City") per costruire la città reale al posto del quartiere finto.
     *
     * Il risultato arriva in Unity via UnitySendMessage("GameManager","OnOsmCityReceived",json).
     * In caso di errore arriva "OnOsmCityFailed" (Unity mantiene la città corrente).
     * Riusa la cache disco coordinate-aware di [OsmClient] (24h), quindi la
     * stessa zona è istantanea e lo streaming non stressa Overpass.
     */
    @JvmStatic
    fun requestOsmCity(lat: Double, lng: Double, radiusMeters: Int) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("requestOsmCity",
                JSONArray().put(lat).put(lng).put(radiusMeters).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        if (shuttingDown) return
        AppLog.d(TAG, "requestOsmCity: richiesta (${lat},${lng}) r=${radiusMeters}m")
        // ACK immediato: informa Unity che la richiesta è stata ricevuta e che il
        // fetch (cache/Overpass) è in corso, così il watchdog Unity non la rispedisce
        // mentre Overpass è lento (evita doppie fetch e HTTP 429).
        sendToUnity("OnOsmCityFetchStarted", "$lat|$lng")
        Thread {
            if (shuttingDown) return@Thread
            try {
                OsmClient.init(ctx)
                val data = OsmClient.fetchAreaCached(lat, lng, radiusMeters)
                AppLog.d(TAG, "requestOsmCity: fetch ok roads=${data.roads.size}, buildings=${data.buildings.size}, trees=${data.trees.size}, parks=${data.parks.size}")
                val json = OsmCityJsonFactory.build(data, lat, lng, radiusMeters)
                AppLog.d(TAG, "requestOsmCity: json=${json.length} chars, invio a Unity")
                sendToUnity("OnOsmCityReceived", json)
            } catch (e: Exception) {
                AppLog.w(TAG, "requestOsmCity: fresh fetch failed: ${e.message}")
                // Overpass may be down; OsmClient.fetchAreaCached already tried stale cache
                // inside its own loadStaleFromDisk fallback. If we still got here, try
                // the POI repo which also reads from disk cache independently.
                try {
                    val stale = OsmClient.fetchAreaCached(lat, lng, radiusMeters)
                    val json = OsmCityJsonFactory.build(stale, lat, lng, radiusMeters)
                    AppLog.d(TAG, "requestOsmCity: stale cache success, json=${json.length} chars")
                    sendToUnity("OnOsmCityReceived", json)
                } catch (e2: Exception) {
                    AppLog.w(TAG, "requestOsmCity failed completely: ${e2.message}")
                    sendToUnity("OnOsmCityFailed", e2.message ?: "error")
                }
            }
        }.start()
    }

    /** Bisogni locali (LocalNeeds) come JSON: {"hunger":..,"sleep":..,"hygiene":..,"fun":..,"thirst":..}.
     *  Usato dall'HUD bisogni di Esplora per mostrare le barre. */
    @JvmStatic
    fun getNeedsJson(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getNeedsJson")
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return "{}"
        return needsJson(com.intelligame.huntix.reallife.LocalNeeds.load(ctx))
    }

    /** Applica un'azione ai bisogni locali (needKey + gain) e restituisce il JSON aggiornato. */
    @JvmStatic
    fun applyNeedAction(needKey: String, gain: Float): String {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("applyNeedAction",
                JSONArray().put(needKey).put(gain.toDouble()).toString())
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return "{}"
        return needsJson(com.intelligame.huntix.reallife.LocalNeeds.applyAction(ctx, needKey, gain))
    }

    private fun needsJson(needs: Map<String, Float>): String {
        val jo = JSONObject()
        for ((k, v) in needs) jo.put(k, v.toDouble())
        return jo.toString()
    }

    /** Richiede i POI OSM entro [radiusMeters] (max 10 km) da (lat,lng).
     *  Il risultato arriva in Unity via UnitySendMessage("GameManager","OnPoisReceived",json).
     *  I POI vengono ordinati per distanza e limitati (1200) per non saturare Unity.
     *
     *  Accelerazione: caricamento a fasi (1 km → 3 km → 10 km): la prima fase
     *  arriva in pochi secondi così i negozi vicini compaiono subito, mentre le
     *  fasi successive espandono la zona in background. Ogni fase manda anche
     *  "OnPoisProgress" per aggiornare la barra di caricamento in Unity.
     *  Se la stessa zona è già in cache (memoria ≤10 min e ≤2 km, o disco ≤24h),
     *  risponde subito senza rifare la query Overpass. */
    @JvmStatic
    fun requestPoisNearby(lat: Double, lng: Double, radiusMeters: Int) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("requestPoisNearby",
                JSONArray().put(lat).put(lng).put(radiusMeters).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        if (shuttingDown) return

        // Cache in-memory: stessa zona recente → risposta immediata (niente Overpass)
        val cached = poiCacheEnvelope
        if (cached != null &&
            System.currentTimeMillis() - poiCacheTime < POI_CACHE_MAX_AGE_MS &&
            distanceMeters(lat, lng, poiCacheCenterLat, poiCacheCenterLng) <= POI_CACHE_MAX_DIST_M) {
            AppLog.d(TAG, "requestPoisNearby: memory cache HIT (${poiCacheCenterLat},${poiCacheCenterLng})")
            sendToUnity("OnPoisReceived", cached)
            return
        }

        CustomPageRegistry.init(ctx)
        Thread {
            var lastPois: List<PoiSearchManager.SearchResult>? = null
            var lastEnvelope: String? = null
            try {
                OsmClient.init(ctx)
                val fullRadius = POI_STAGES_M.last()

                // Area completa già in cache disco → un solo fetch (quasi istantaneo)
                if (OsmClient.hasPoisCache(lat, lng, fullRadius)) {
                    AppLog.d(TAG, "requestPoisNearby: disk cache HIT per raggio $fullRadius m")
                    if (shuttingDown) return@Thread
                    val pois = OsmPoiRepository.loadNearby(lat, lng, fullRadius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    if (shuttingDown) return@Thread
                    val wrapper = buildEnvelope(pois, lat, lng, fullRadius,
                        POI_STAGES_M.size, POI_STAGES_M.size, done = true)
                    poiCacheCenterLat = lat
                    poiCacheCenterLng = lng
                    poiCacheTime = System.currentTimeMillis()
                    poiCacheEnvelope = wrapper
                    sendToUnity("OnPoisReceived", wrapper)
                    return@Thread
                }

                // Caricamento progressivo: prima i vicini, poi si espande
                for (i in POI_STAGES_M.indices) {
                    if (shuttingDown) return@Thread
                    val radius = POI_STAGES_M[i]
                    val stage = i + 1
                    AppLog.d(TAG, "requestPoisNearby: stage $stage/${POI_STAGES_M.size} raggio=${radius}m")
                    sendProgress(stage, POI_STAGES_M.size, radius)

                    if (shuttingDown) return@Thread
                    val pois = OsmPoiRepository.loadNearby(lat, lng, radius)
                        .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                        .take(MAX_POIS)
                    if (shuttingDown) return@Thread
                    lastPois = pois

                    val done = stage == POI_STAGES_M.size
                    lastEnvelope = buildEnvelope(pois, lat, lng, radius, stage, POI_STAGES_M.size, done)
                    sendToUnity("OnPoisReceived", lastEnvelope)

                    if (done) break
                    // Pausa breve tra le fasi per non stressare i mirror Overpass.
                    Thread.sleep(300)
                }

                poiCacheCenterLat = lat
                poiCacheCenterLng = lng
                poiCacheTime = System.currentTimeMillis()
                poiCacheEnvelope = lastEnvelope
            } catch (e: Exception) {
                AppLog.w(TAG, "requestPoisNearby failed: ${e.message}")
                if (lastEnvelope != null && lastPois != null) {
                    // Le fasi già consegnate bastano: chiudi segnalando done=true
                    val doneEnv = buildEnvelope(lastPois, lat, lng,
                        POI_STAGES_M.last(), POI_STAGES_M.size, POI_STAGES_M.size, done = true)
                    sendToUnity("OnPoisReceived", doneEnv)
                } else {
                    val mini = miniChunkEnvelope(lat, lng)
                    if (mini != null) {
                        sendToUnity("OnPoisReceived", mini)
                    } else {
                        sendToUnity("OnPoisFailed", e.message ?: "error")
                    }
                }
            }
        }.start()
    }

    /** Invia un messaggio a Unity solo se il teardown non è in corso: durante lo
     *  shutdown (uscita dalla scena) UnitySendMessage su un runtime in smontaggio
     *  può essere un crash nativo, quindi i messaggi in volo vengono scartati.
     *  Lock congiunto con endUnitySession: nessun invio puo' partire dopo la
     *  fissazione del flag (check+invio atomici). */
    private fun sendToUnity(method: String, arg: String) {
        if (UnityIpc.isUnity) {
            sendLocalToUnity("GameManager", method, arg)
        } else {
            UnityIpc.sendToUnity("GameManager", method, arg)
        }
    }

    /** Invia l'aggiornamento di avanzamento a Unity (barra di caricamento Esplora). */
    private fun sendProgress(stage: Int, stages: Int, radiusMeters: Int) {
        val jo = JSONObject()
        jo.put("stage", stage)
        jo.put("stages", stages)
        jo.put("radiusMeters", radiusMeters)
        sendToUnity("OnPoisProgress", jo.toString())
    }

    /** Costruisce l'envelope JSON {"pois":[...],"count":N,"centerLat":..,"centerLng":..,"done":bool}. */
    private fun buildEnvelope(
        pois: List<PoiSearchManager.SearchResult>,
        lat: Double,
        lng: Double,
        radiusMeters: Int = 0,
        stage: Int = 1,
        stages: Int = 1,
        done: Boolean = true
    ): String {
        val arr = JSONArray()
        for (p in pois) {
            val custom = CustomPageRegistry.resolve(p.id)
            val jo = JSONObject()
            jo.put("id", p.id)
            jo.put("name", p.name)
            jo.put("lat", p.lat)
            jo.put("lng", p.lng)
            jo.put("buildingType", p.buildingType)
            jo.put("poiType", p.poiType)
            jo.put("category", p.category)
            jo.put("emoji", PoiJsonFactory.emojiFor(p.buildingType, p.poiType, p.category))
            if (custom != null) {
                jo.put("pageType", custom.pageType)
                jo.put("url", custom.url)
            } else {
                jo.put("pageType", p.pageType)
                jo.put("url", p.url)
            }
            jo.put("hasCustom", custom != null)
            arr.put(jo)
        }
        val wrapper = JSONObject()
        wrapper.put("pois", arr)
        wrapper.put("count", pois.size)
        wrapper.put("centerLat", lat)
        wrapper.put("centerLng", lng)
        wrapper.put("radiusMeters", radiusMeters)
        wrapper.put("stage", stage)
        wrapper.put("stages", stages)
        wrapper.put("done", done)
        return wrapper.toString()
    }

    /** Fallback offline: mini-chunk pre-inserito nell'APK (se Overpass fallisce). */
    private fun miniChunkEnvelope(lat: Double, lng: Double): String? {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return null
        try {
            OsmClient.init(ctx)
            val data = OsmClient.loadMiniChunk(lat, lng) ?: return null
            val pois = OsmPoiRepository.classify(data)
            if (pois.isEmpty()) return null
            val cLat = (data.south + data.north) / 2
            val cLng = (data.west + data.east) / 2
            val sorted = pois
                .sortedBy { distanceMeters(cLat, cLng, it.lat, it.lng) }
                .take(MAX_POIS)
            AppLog.d(TAG, "requestPoisNearby: fallback mini-chunk (${sorted.size} POIs)")
            return buildEnvelope(sorted, cLat, cLng)
        } catch (e: Exception) {
            AppLog.w(TAG, "miniChunkEnvelope failed: ${e.message}")
            return null
        }
    }

    /** Apre la pagina del POI da Unity (custom JSON / web / JSON sintetico OSM). */
    @JvmStatic
    fun openPoiPage(osmId: String, name: String, buildingType: String, poiType: String,
                    url: String, pageType: String, lat: Double, lng: Double, category: String) {
        val activity = UnityPlayer.currentActivity ?: return
        try {
            val custom = CustomPageRegistry.resolve(osmId)
            val intent = if (custom != null) {
                Intent(activity, POICustomPageActivity::class.java).apply {
                    putExtra(POICustomPageActivity.EXTRA_JSON_URL, custom.url)
                    putExtra(POICustomPageActivity.EXTRA_POI_NAME, name)
                    putExtra(POICustomPageActivity.EXTRA_POI_TYPE, poiType)
                    putExtra(POICustomPageActivity.EXTRA_POI_LAT, lat)
                    putExtra(POICustomPageActivity.EXTRA_POI_LNG, lng)
                }
            } else if (url.isNotBlank() && !url.startsWith("osm:")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            } else {
                Intent(activity, POICustomPageActivity::class.java).apply {
                    putExtra(POICustomPageActivity.EXTRA_JSON_INLINE, PoiJsonFactory.build(osmId, name, buildingType, poiType, lat, lng).toString())
                    putExtra(POICustomPageActivity.EXTRA_POI_NAME, name)
                    putExtra(POICustomPageActivity.EXTRA_POI_TYPE, poiType)
                    putExtra(POICustomPageActivity.EXTRA_POI_LAT, lat)
                    putExtra(POICustomPageActivity.EXTRA_POI_LNG, lng)
                }
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            val osmType = osmId.removePrefix("osm:").substringBefore(":")
            val ref = osmId.removePrefix("osm:").substringAfter(":", "")
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/$osmType/$ref")))
        }
    }

    /** Saldo MVC (Huntix Coins) attuale, gestito dall'app esterna. */
    @JvmStatic
    fun getMvcBalance(): Double {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("getMvcBalance").toDoubleOrNull() ?: 0.0
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return 0.0
        return com.intelligame.huntix.managers.SavedManager.getMvcBalance(ctx)
    }

    /** Tenta di spendere [amount] MVC. True se il saldo bastava, false altrimenti. */
    @JvmStatic
    fun spendMvc(amount: Double): Boolean {
        if (UnityIpc.isUnity) {
            val r = UnityIpc.callApp("spendMvc", JSONArray().put(amount).toString())
            return r.toBooleanStrictOrNull() ?: (r == "true")
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return false
        return com.intelligame.huntix.managers.SavedManager.spendMvc(ctx, amount)
    }

    /** Accredita [amount] MVC (es. scambio da soldi citta a MVC). Ritorna il nuovo saldo. */
    @JvmStatic
    fun addMvc(amount: Double): Double {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("addMvc", JSONArray().put(amount).toString())
                .toDoubleOrNull() ?: 0.0
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return 0.0
        if (amount <= 0) return getMvcBalance()
        return com.intelligame.huntix.managers.SavedManager.addMvc(ctx, amount)
    }

    // ─────────────────────────────────────────────────────────────
    // ── Unified player profile (unico giocatore, unico universo) ──
    // Questi metodi espongono/aggiornano il PlayerProfile Huntix (la
    // fonte di verita'), così Miacitta (Unity) e gli altri moduli
    // leggono/scrivo lo STESSO profilo: un solo player, una sola XP,
    // un solo nome, una sola classifica.
    // ─────────────────────────────────────────────────────────────

    /** Nome del player Huntix (Unified profile). */
    @JvmStatic
    fun getPlayerName(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerName")
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.name ?: "Giocatore"
    }
    /** JWT di accesso corrente (identita' autentica del player nel multiplayer
     *  della citta'). Vuoto se l'utente non e' autenticato. Restituito a Unity
     *  via il ponte StoreUnityBridge. NON persisterlo lato Unity. */
    @JvmStatic
    fun getAccessToken(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getAccessToken")
        val ctx = UnityPlayer.currentActivity ?: UnityAppContext.context ?: return ""
        return com.intelligame.huntix.reallife.RealLifeAuth.getAccessToken(ctx)
    }


    /** XP totale cumulato del player (Unity non ha un proprio XP separato). */
    @JvmStatic
    fun getPlayerXp(): Long {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerXp").toLongOrNull() ?: 0L
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.xp ?: 0L
    }

    /** Livello calcolato dalla XP cumulata (getter PlayerProfile.level). */
    @JvmStatic
    fun getPlayerLevel(): Int {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerLevel").toIntOrNull() ?: 1
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.level ?: 1
    }

    /** Potere totale del player. */
    @JvmStatic
    fun getPlayerPower(): Long {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerPower").toLongOrNull() ?: 0L
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.power ?: 0L
    }

    /** Gemme premium del player. */
    @JvmStatic
    fun getPlayerGems(): Int {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerGems").toIntOrNull() ?: 0
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.gems ?: 0
    }

    /** Energia corrente del player (0..100). */
    @JvmStatic
    fun getPlayerEnergy(): Int {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerEnergy").toIntOrNull() ?: 100
        return com.intelligame.huntix.PlayerProfileManager.myProfile?.energy ?: 100
    }

    /** Inventario uova del player (conteggio totale). */
    @JvmStatic
    fun getEggCount(): Int {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getEggCount").toIntOrNull() ?: 0
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return 0
        return com.intelligame.huntix.EggInventoryManager.getInventoryCount(ctx)
    }

    /**
     * Snapshot completo del profilo come JSON, così Unity può mostrare
     * lo stesso nome/XP/livello/uova ovunque. Compatibile anche quando il
     * profilo non è ancora stato inizializzato (default sicuri).
     */
    @JvmStatic
    fun getPlayerProfileJson(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getPlayerProfileJson")
        val p = com.intelligame.huntix.PlayerProfileManager.myProfile
        if (p == null)
            return "{\"name\":\"Giocatore\",\"xp\":0,\"level\":1,\"power\":0,\"gems\":0,\"energy\":100,\"eggCount\":0,\"eggsFound\":0}"
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
        val eggCount = ctx?.let { com.intelligame.huntix.EggInventoryManager.getInventoryCount(it) } ?: 0
        val j = org.json.JSONObject()
        try {
            j.put("name", p.name)
            j.put("xp", p.xp)
            j.put("level", p.level)
            j.put("power", p.power)
            j.put("gems", p.gems)
            j.put("energy", p.energy)
            j.put("eggCount", eggCount)
            j.put("eggsFound", p.eggsFound)
            j.put("playerId", p.playerId)
            // Sesso ed età scelti in registrazione (fonte di verità per Miacittà).
            j.put("gender", p.playerGender)          // "male" / "female"
            j.put("birthYear", p.birthYear)
            j.put("isMinor", p.isMinor)
        } catch (_: Exception) { }
        return j.toString()
    }

    /**
     * Accredita XP guadagnati in Miacitta (matrimonio, figli, missioni,
     * reincarnazione) direttamente sul profilo Huntix, così alimentano XP,
     * livello e classifica mondiale.
     *
     * @return il nuovo totale XP (Long).
     */
    @JvmStatic
    fun addXpFromCity(xpAmount: Long): Long {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("addXpFromCity", JSONArray().put(xpAmount).toString())
                .toLongOrNull() ?: 0L
        }
        if (xpAmount <= 0) return getPlayerXp()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0L
        p.xp += xpAmount
        p.weeklyXp += xpAmount
        p.lastSeen = System.currentTimeMillis()
        pm.persistMyProfile()
        return p.xp
    }

    /**
     * Accredita potere guadagnato in Miacitta al profilo Huntix.
     * @return il nuovo potere totale.
     */
    @JvmStatic
    fun addPowerFromCity(powerAmount: Long): Long {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("addPowerFromCity", JSONArray().put(powerAmount).toString())
                .toLongOrNull() ?: 0L
        }
        if (powerAmount <= 0) return getPlayerPower()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0L
        p.power += powerAmount
        p.lastSeen = System.currentTimeMillis()
        pm.persistMyProfile()
        return p.power
    }

    /**
     * Accreditare gemme guadagnate in Miacitta.
     * @return il nuovo saldo gemme.
     */
    @JvmStatic
    fun addGemsFromCity(amount: Int): Int {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("addGemsFromCity", JSONArray().put(amount).toString())
                .toIntOrNull() ?: 0
        }
        if (amount <= 0) return getPlayerGems()
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return 0
        p.addGems(amount)
        pm.persistMyProfile()
        return p.gems
    }

    /**
     * Sincronizza l'energia del player dalla citta' al profilo Huntix
     * (0..100). Aggiorna anche Firestore così gli altri moduli la leggono.
     */
    @JvmStatic
    fun syncEnergyFromCity(energy: Int): Int {
        if (UnityIpc.isUnity) {
            return UnityIpc.callApp("syncEnergyFromCity", JSONArray().put(energy).toString())
                .toIntOrNull() ?: energy.coerceIn(0, 100)
        }
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return energy.coerceIn(0, 100)
        val clamped = energy.coerceIn(0, 100)
        if (p.energy != clamped) {
            p.energy = clamped
            pm.persistMyProfile()
        }
        return p.energy
    }

    /**
     * Cambia il nome del player da Miacitta (es. dopo la reincarnazione).
     * Replica updatePlayerName sul profilo locale + Firestore best-effort.
     *
     * @return true se il nome è stato aggiornato.
     */
    @JvmStatic
    fun setPlayerNameFromCity(newName: String): Boolean {
        if (UnityIpc.isUnity) {
            val r = UnityIpc.callApp("setPlayerNameFromCity", JSONArray().put(newName).toString())
            return r.toBooleanStrictOrNull() ?: (r == "true")
        }
        if (newName.isBlank()) return false
        val pm = com.intelligame.huntix.PlayerProfileManager
        val p = pm.myProfile ?: return false
        p.name = newName
        pm.persistMyProfile()
        // Usa il canale esistente per tenere aggiornata anche la classifica
        pm.updatePlayerName(newName, onComplete = { }, onError = { })
        return true
    }

    // ── City State Sync (snapshot bidirezionale Unity ↔ profilo) ──

    /** Riceve da Unity (MiCittà) lo snapshot completo dello stato città e lo
     *  conserva + allinea il profilo generale (riepiloghi city*). */
    @JvmStatic
    fun onCityStateSync(json: String) {
        if (UnityIpc.isUnity) {
            UnityIpc.callApp("onCityStateSync", JSONArray().put(json).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        CityStateSync.onCitySnapshot(ctx, json)
    }

    /**
     * Riceve da Unity l'aspetto fisiologico applicato dal livello XP
     * (GrowthXpDriver → GrowthStateSync). Best-effort e non bloccante:
     * valida il JSON e conserva l'ultimo valore su prefs condivise, così
     * anche il lato Android ha il mirror della crescita raggiunta.
     * Livello XP non viene toccato: resta l'unica fonte di verità.
     *
     * Riflesso della caccia alle uova: ai livelli chiave (5/10/20/35/50)
     * schiude in inventario l'"Egg of Growth" della rarità progressiva e
     * registra su EggWhereLog il luogo di ritrovamento ("Livello X → crescita",
     * coerente col Mandato §5). Idempotente: a ogni sync rivede i livelli
     * raggiunti e salta quelli già presenti in inventario.
     */
    @JvmStatic
    fun onGrowthStateSync(json: String) {
        if (UnityIpc.isUnity) {
            // Qui vengono schiuse le uova della crescita: devono arrivare al
            // profilo dell'app, altrimenti il Riflesso uova si perde.
            UnityIpc.callApp("onGrowthStateSync", JSONArray().put(json).toString())
            return
        }
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return
        if (json.isBlank()) return
        val j = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        val level = j.optInt("level", 1)
        val prefs = ctx.getSharedPreferences("huntix_growth", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("level", level)
            .putFloat("age", j.optDouble("age", 0.5).toFloat())
            .putFloat("height", j.optDouble("height", 0.5).toFloat())
            .putFloat("proportion", j.optDouble("proportion", 0.5).toFloat())
            .putFloat("shape", j.optDouble("shape", 0.5).toFloat())
            .apply()
        android.util.Log.d("HuntixGrowth",
            "GrowthStateSync da Unity: livello=" + level + " json=" + json)

        // Mirror sul profilo generale (Firestore + cache): i valori 4D
        // della crescita sono visibili nel profilo giocatore ovunque.
        // Best-effort e non bloccante come onCityStateSync.
        try {
            val pm = com.intelligame.huntix.PlayerProfileManager
            val p = pm.myProfile
            if (p != null) {
                if (level > p.growthLevel) p.growthLevel = level
                p.growthAge = j.optDouble("age", p.growthAge)
                p.growthHeight = j.optDouble("height", p.growthHeight)
                p.growthProportion = j.optDouble("proportion", p.growthProportion)
                p.growthShape = j.optDouble("shape", p.growthShape)
                pm.persistMyProfile()
                android.util.Log.d("HuntixGrowth", "Profilo aggiornato con crescita livello " + level)
            }
        } catch (e: Exception) {
            android.util.Log.w("HuntixGrowth", "Profilo growth mirror non aggiornato: " + e.message)
        }

        hatchGrowthEggs(ctx, level)
    }

    // ── Egg of Growth (Riflesso uova): helper privati dell'object ──

    /** Livelli chiave della crescita: schiudono l'Egg of Growth. */
    private val growthKeyLevels = intArrayOf(5, 10, 20, 35, 50)

    private fun growthRarityFor(level: Int): com.intelligame.huntix.EggRarity =
        when {
            level >= 50 -> com.intelligame.huntix.EggRarity.LEGENDARY
            level >= 35 -> com.intelligame.huntix.EggRarity.EPIC
            level >= 20 -> com.intelligame.huntix.EggRarity.RARE
            level >= 10 -> com.intelligame.huntix.EggRarity.UNCOMMON
            else -> com.intelligame.huntix.EggRarity.COMMON
        }

    /**
     * Schiude in inventario l'Egg of Growth per ogni livello chiave
     * raggiunto e non ancora presente. Idempotente via eggId "growth_<lv>".
     */
    private fun hatchGrowthEggs(ctx: android.content.Context, currentLevel: Int) {
        val existing = com.intelligame.huntix.EggInventoryManager.getInventory(ctx)
            .map { it.eggId }.toHashSet()
        for (key in growthKeyLevels) {
            if (key > currentLevel) break
            val eggId = "growth_$key"
            if (existing.contains(eggId)) continue
            val rarity = growthRarityFor(key)
            val item = com.intelligame.huntix.EggInventoryItem(
                eggId = eggId,
                rarityId = rarity.id,
                fantasyName = "Uovo della Crescita (Livello $key)",
                power = rarity.basePower,
                xpReward = 0
            )
            val added = com.intelligame.huntix.EggInventoryManager.addEgg(ctx, item)
            if (added) {
                com.intelligame.huntix.managers.EggWhereLog.record(
                    ctx, rarity.id,
                    place = "Livello $key → crescita",
                    name = item.fantasyName
                )
                android.util.Log.i("HuntixGrowth",
                    "Egg of Growth schiuso al livello $key (${rarity.id})")
            }
        }
    }

    /** Restituisce l'ultimo snapshot città salvato ("" se assente): usato da
     *  Unity all'avvio della scena per ripristinare lo stato se il salvataggio
     *  locale è vergine. */
    @JvmStatic
    fun getCityStateSync(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("getCityStateSync")
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return ""
        return CityStateSync.getSnapshot(ctx)
    }

    /** Verifica di sincronizzazione: ritorna il report di allineamento dello
     *  snapshot (evento "CityStateCheck" da Unity). */
    @JvmStatic
    fun checkCityStateSync(): String {
        if (UnityIpc.isUnity) return UnityIpc.callApp("checkCityStateSync")
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return "{\"ok\":false}"
        return CityStateSync.checkSync(ctx)
    }

    /** Allineamento profilo ← ultimo snapshot (usato all'avvio app, non serve
     *  Unity attiva). Best-effort e non bloccante. */
    @JvmStatic
    fun applyStoredCityStateToProfile(): Boolean {
        val ctx = UnityPlayer.currentActivity?.applicationContext ?: UnityAppContext.context
            ?: return false
        return try {
            CityStateSync.startupSync(ctx)
            true
        } catch (_: Exception) { false }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

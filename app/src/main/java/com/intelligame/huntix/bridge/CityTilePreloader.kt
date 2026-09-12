package com.intelligame.huntix.bridge

import android.content.Context
import com.intelligame.huntix.AppLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * Preload delle tile di Miacitta (Unity).
 *
 * Quando il player entra nella scena City, ChunkManager/TileClient scaricano
 * grafo + geometria delle tile dai POI della citta': per la geo la prima
 * generazione lato server puo' richiedere 1-3 minuti. Questo preloader esegue
 * lo stesso download in due momenti:
 *  - ALL'AVVIO DELL'APP in background (posizione = ultima posizione di gioco),
 *  - su richiesta sincrona (HomeActivity) prima di lanciare l'Activity Unity,
 *    cosi' il player vede una barra di caricamento invece di restare freezato.
 *
 * In entrambi i casi viene precaricata la ZONA 3x3 (9 tile) attorno al punto
 * di spawn: i chunk costruiti nei primi secondi di gioco stanno quasi tutti
 * nelle 8 tile vicine, quindi Unity trova su disco molto piu' di un singolo
 * chunk iniziale.
 *
 * I file vengono scritti esattamente dove TileClient li cercherebbe
 * (cache-first): {key}.v2.graph.json / {key}.v2.geo.json nella directory
 * "huntix_tiles" sotto il persistentDataPath di Unity.
 */
object CityTilePreloader {

    private const val TAG = "CityTilePreloader"

    private const val BASE_URL = "http://82.165.218.56:5100"
    private const val TILE_DIR = "huntix_tiles"
    private const val CACHE_VERSION = 2

    // Griglia tile (deve combaciare con CityGrid.cs / tile_builder.py)
    private const val ORIGIN_LAT = 34.0
    private const val ORIGIN_LON = 5.0
    private const val LAT_STEP = 0.090
    private const val LON_STEP = 0.121

    // Default Roma (startLat/startLng di CityChunkedWorld)
    private const val DEFAULT_LAT = 41.9028
    private const val DEFAULT_LNG = 12.4964

    private const val PREF_FILE = "world_game_prefs"

    // Chiave prefs dove Unity scrive il proprio persistentDataPath
    // (StoreUnityBridge.setTileCacheDir) cosi' scriviamo ESATTAMENTE dove
    // TileClient fa cache-first indipendentemente da interno/esterno.
    private const val KEY_UNITY_CACHE_DIR = "tile_cache_dir"

    // L'avvio-app (background) e il tap su Miacitta possono voler scaricare la
    // STESSA tile in parallelo: l'insieme delle chiavi in download evita doppie
    // richieste e file mezzo-scritti in competizione tra i due contesti.
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Numero massimo di tile scaricate in parallelo dal preload sincrono.
    private const val PARALLEL_TILES = 3

    /**
     * Chiavi del blocco (2*radius+1)^2 attorno alla tile di spawn, in ordine
     * radiale: la tile di spawn per prima, poi gli anelli. Con radius=1 si
     * precarica la zona 3x3 attorno al punto di gioco: i chunk che Unity
     * builda nei primi secondi stanno quasi tutti nelle 8 tile vicine.
     */
    private fun tileKeysAround(lat: Double, lng: Double, radius: Int): List<String> {
        val ilat = floor((lat - ORIGIN_LAT) / LAT_STEP).toInt()
        val ilon = floor((lng - ORIGIN_LON) / LON_STEP).toInt()
        val keys = ArrayList<String>((2 * radius + 1) * (2 * radius + 1))
        keys.add(String.format("IT_%03d_%03d", ilat, ilon))
        for (d in 1..radius) {
            for (i in -d..d) {
                for (j in -d..d) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != d) continue
                    keys.add(String.format("IT_%03d_%03d", ilat + i, ilon + j))
                }
            }
        }
        return keys
    }

    /**
     * Posizione di spawn con la STESSA priorita' di Unity (CityChunkedWorld):
     * la scelta del selettore ("spawnLat/spawnLng") ha priorita', poi l'ultima
     * posizione di gioco ("worldPos"/"gpsLat/gpsLng"). Il GPS del dispositivo
     * non viene MAI letto.
     */
    private fun spawnPosition(ctx: Context): Pair<Double, Double> =
        com.intelligame.huntix.bridge.WorldPosCloud.currentSpawnChoice(ctx)

    /** Directory cache di Unity (persistentDataPath/huntix_tiles) se disponibile. */
    private fun unityTileDir(ctx: Context): File? {
        val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val p = prefs.getString(KEY_UNITY_CACHE_DIR, null)
        if (p.isNullOrEmpty()) return null
        val base = File(p)
        return if (base.isDirectory) File(base, TILE_DIR) else null
    }

    /** Directory fallback: filesDir/huntix_tiles (default Android di persistentDataPath). */
    private fun fallbackTileDir(ctx: Context): File =
        File(ctx.filesDir, TILE_DIR)

    /** Preload "fire-and-forget" all'avvio dell'app (background). */
    fun preload(ctx: Context) {
        Thread {
            try {
                preloadSync(ctx) { _, _, _, _ -> }
            } catch (e: Exception) {
                AppLog.w(TAG, "preload non avviato: ${e.message}")
            }
        }.start()
    }

    /** Tile della zona da scaricare: grafo/geo mancanti su disco. */
    private data class Tile(val key: String, val needGraph: Boolean, val needGeo: Boolean)

    /**
     * Scarica (se mancano) grafo+geo DELLE TILE della zona attorno a quella di
     * spawn gestendo un download per volta e notificando la fase via [onPhase]
     * come (fase, tile corrente, totale tile): fasi "cache" (gia' su disco),
     * "tile", "graph", "geo", "done". Restituisce true se la tile di spawn e'
     * disponibile (cache o download ok). Da chiamare su un thread: le chiamate
     * di rete sono bloccanti e [onPhase] viene invocato dai thread di lavoro
     * (chi la usa, salga sul main thread ove serva).
     * @param center coordinate di spawn esplicite (scelte dal selettore); se
     * null usa la posizione persistita (WorldPosCloud).
     */
    fun preloadSync(
        ctx: Context,
        radius: Int = 1,
        center: Pair<Double, Double>? = null,
        onPhase: (fase: String, current: Int, total: Int, bytes: Long) -> Unit
    ): Boolean {
        val (lat, lng) = center ?: spawnPosition(ctx)
        val keys = tileKeysAround(lat, lng, radius)
        val total = keys.size

        val dir = unityTileDir(ctx) ?: fallbackTileDir(ctx)
        if (!dir.exists()) dir.mkdirs()

        var cumulativeBytes = 0L

        // Cache-first come TileClient: se i file esistono non si tocca la rete.
        val pending = mutableListOf<Tile>()
        val cachedCount = mutableListOf<String>()
        keys.forEachIndexed { idx, key ->
            val graphFile = File(dir, "$key.v$CACHE_VERSION.graph.json")
            val geoFile = File(dir, "$key.v$CACHE_VERSION.geo.json")
            val needGraph = !graphFile.exists() || graphFile.length() == 0L
            val needGeo = !geoFile.exists() || geoFile.length() == 0L
            if (needGraph || needGeo) {
                pending.add(Tile(key, needGraph, needGeo))
            } else {
                cachedCount.add(key)
                // Conta anche i file gia' in cache nel totale KB
                cumulativeBytes += graphFile.length()
                cumulativeBytes += geoFile.length()
                onPhase("cache", idx + 1, total, cumulativeBytes)
            }
        }

        if (pending.isEmpty()) {
            AppLog.d(TAG, "zona ${keys.joinToString()} gia' in cache, skip")
            onPhase("done", total, total, cumulativeBytes)
            return true
        }

        AppLog.d(TAG, "preload zona ${keys.size} tile attorno a ($lat,$lng): " +
            "da scaricare ${pending.size}, gia' in cache ${cachedCount.size}")

        // Download paralleli (max PARALLEL_TILES): il centro (tile di spawn) e'
        // il primo della lista, quindi il primo ad andare in esecuzione.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(
            Math.min(PARALLEL_TILES, pending.size))
        val results = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        try {
            pending.forEachIndexed { idx, tile ->
                val index = keys.indexOf(tile.key) + 1
                pool.execute {
                    onPhase("tile", index, total, cumulativeBytes)
                    results[tile.key] = preloadTile(dir, tile, index, total,
                        cumulativeBytes) { fase, cur, tot, b ->
                        cumulativeBytes = b
                        onPhase(fase, cur, tot, b)
                    }
                }
            }
        } finally {
            pool.shutdown()
            // la prima generazione geo del server puo' richiedere minuti
            pool.awaitTermination(20, java.util.concurrent.TimeUnit.MINUTES)
        }

        val ok = results.filterValues { it }.keys
        AppLog.d(TAG, "preload zona completato: ok ${ok.size}/${pending.size}")
        onPhase("done", total, total, cumulativeBytes)
        return keys.first() in cachedCount || ok.contains(keys.first())
    }

    /** Scarica (se manca) grafo+geo di una singola tile con guardia anti-doppio. */
    private fun preloadTile(
        dir: File,
        tile: Tile,
        index: Int,
        total: Int,
        startBytes: Long,
        onPhase: (fase: String, current: Int, total: Int, bytes: Long) -> Unit
    ): Boolean {
        val key = tile.key
        if (!inFlight.add(key)) {
            AppLog.d(TAG, "tile $key gia' in download da un altro thread, skip")
            return true
        }
        var downloaded = 0L
        try {
            if (tile.needGraph) {
                onPhase("graph", index, total, startBytes + downloaded)
                downloaded += download(BASE_URL + "/api/tiles/$key/graph",
                    File(dir, "$key.v$CACHE_VERSION.graph.json"))
            }
            if (tile.needGeo) {
                onPhase("geo", index, total, startBytes + downloaded)
                downloaded += download(BASE_URL + "/api/tiles/$key/geo",
                    File(dir, "$key.v$CACHE_VERSION.geo.json"))
            }
            AppLog.d(TAG, "preload tile $key completato")
            return true
        } catch (e: Exception) {
            // La prima generazione geo lato server puo' richiedere 1-3 min:
            // un timeout qui non e' un problema, Unity riprovera' da solo.
            AppLog.w(TAG, "preload tile $key fallito: ${e.message}")
            return false
        } finally {
            inFlight.remove(key)
        }
    }

    /** Scarica l'URL in dest, restituisce il numero di byte scritti. */
    private fun download(urlString: String, dest: File): Long {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30_000
            conn.readTimeout = 240_000   // prima generazione server puo' richiedere minuti
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $code per $urlString")
            }
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            try {
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                if (tmp.exists()) tmp.delete()
                throw e
            }
            if (!tmp.renameTo(dest)) {
                throw RuntimeException("rename fallito verso ${dest.absolutePath}")
            }
            val bytes = dest.length()
            AppLog.d(TAG, "scaricato ${dest.name} (${bytes / 1024}KB)")
            return bytes
        } finally {
            conn.disconnect()
        }
    }
}
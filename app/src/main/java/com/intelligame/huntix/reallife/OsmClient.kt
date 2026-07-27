package com.intelligame.huntix.reallife

import android.content.Context
import android.util.Log
import com.intelligame.huntix.AppLog
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Client robusto per scaricare dati OpenStreetMap da Overpass API.
 *
 * Architettura:
 *   1. Cache disco coordinate-aware (una entry per zona+raggio)
 *   2. Download con 3 mirror Overpass + retry con exponential backoff
 *   3. Auto-cleanup cache stale (>24h)
 *   4. Mirror health tracking (skip mirror morti per 60s)
 *
 * Usage:
 *   OsmClient.init(context)
 *   val data = OsmClient.fetchArea(centerLat, centerLon, radiusMeters)
 */
object OsmClient {

    private const val TAG = "City3D_OSM"

    // --- HTTP client ---
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var appContext: Context? = null

    // --- Mirror configuration ---
    private val OVERPASS_MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private const val MIRROR_RETRY_ATTEMPTS = 2
    private const val MIRROR_BACKOFF_BASE_MS = 1000L
    private const val MIRROR_COOLDOWN_MS = 30_000L

    // Mirror health: track failures to skip dead mirrors quickly
    private val mirrorFailures = ConcurrentHashMap<String, Long>()

    // Cache TTL: 24 hours
    private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun init(context: Context) {
        appContext = context.applicationContext
        cleanStaleCache()
    }

    /**
     * Scarica dati OSM per un'area circolare, con cache persistente su disco.
     * La cache è basata su coordinate arrotondate + raggio, quindi:
     *   - Stessa zona → cache hit immediato
     *   - Zona diversa → nuovo download
     *   - Raggio diverso → nuovo download
     */
    suspend fun fetchArea(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1000
    ): OsmData = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(centerLat, centerLon, radiusMeters)
        AppLog.d(TAG, "fetchArea: START (lat=$centerLat, lon=$centerLon, radius=${radiusMeters}m, cache=$cacheKey)")

        val cached = loadFromDisk(cacheKey)
        if (cached != null) {
            AppLog.d(TAG, "fetchArea: cache HIT (roads=${cached.roads.size}, buildings=${cached.buildings.size})")
            CoordinateConverter.init(centerLat, centerLon)
            return@withContext cached
        }
        AppLog.d(TAG, "fetchArea: cache MISS, downloading from Overpass API...")

        CoordinateConverter.init(centerLat, centerLon)

        val latDelta = radiusMeters / 110540.0
        val lonDelta = radiusMeters / (111320.0 * Math.cos(Math.toRadians(centerLat)))
        val south = centerLat - latDelta
        val north = centerLat + latDelta
        val west = centerLon - lonDelta
        val east = centerLon + lonDelta

        val query = buildOverpassQuery(south, west, north, east)
        val requestBody = FormBody.Builder()
            .add("data", query)
            .build()

        val body = downloadWithMirrors(requestBody)

        val data = parseOverpassResponse(body, south, north, west, east)
        AppLog.d(TAG, "fetchArea: parsed (nodes=${data.nodes.size}, roads=${data.roads.size}, buildings=${data.buildings.size}, trees=${data.trees.size})")

        saveToDisk(cacheKey, body, south, north, west, east)
        AppLog.d(TAG, "fetchArea: saved to disk cache ($cacheKey)")

        data
    }

    /**
     * Download con retry su 3 mirror + exponential backoff.
     * Skip mirror morti (cooldown 60s dopo fallimento).
     */
    private fun downloadWithMirrors(requestBody: FormBody): String {
        val now = System.currentTimeMillis()

        for (mirror in OVERPASS_MIRRORS) {
            val lastFailure = mirrorFailures[mirror] ?: 0L
            if (now - lastFailure < MIRROR_COOLDOWN_MS) {
                AppLog.d(TAG, "downloadWithMirrors: skipping $mirror (cooldown, failed ${now - lastFailure}ms ago)")
                continue
            }

            for (attempt in 0..MIRROR_RETRY_ATTEMPTS) {
                try {
                    if (attempt > 0) {
                        val backoff = MIRROR_BACKOFF_BASE_MS * (1L shl (attempt - 1))
                        AppLog.d(TAG, "downloadWithMirrors: retry #$attempt for $mirror (backoff=${backoff}ms)")
                        Thread.sleep(backoff)
                    }

                    AppLog.d(TAG, "downloadWithMirrors: mirror=$mirror attempt=${attempt + 1}/${MIRROR_RETRY_ATTEMPTS + 1}")

                    val request = Request.Builder()
                        .url(mirror)
                        .header("User-Agent", "HuntixGame/1.0")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    AppLog.d(TAG, "downloadWithMirrors: ${mirror} → HTTP ${response.code}")

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        response.close()
                        if (!responseBody.isNullOrEmpty()) {
                            mirrorFailures.remove(mirror)
                            AppLog.d(TAG, "downloadWithMirrors: SUCCESS from $mirror (${responseBody.length} chars)")
                            return responseBody
                        }
                        AppLog.w(TAG, "downloadWithMirrors: empty body from $mirror")
                    } else {
                        val errorSnippet = response.body?.string()?.take(200)
                        response.close()
                        AppLog.w(TAG, "downloadWithMirrors: HTTP ${response.code} from $mirror: $errorSnippet")
                    }
                } catch (e: java.net.ConnectException) {
                    AppLog.w(TAG, "downloadWithMirrors: connect failed $mirror: ${e.message}")
                    mirrorFailures[mirror] = System.currentTimeMillis()
                    break // Don't retry this mirror, connection is dead
                } catch (e: java.net.SocketTimeoutException) {
                    AppLog.w(TAG, "downloadWithMirrors: timeout $mirror: ${e.message}")
                    mirrorFailures[mirror] = System.currentTimeMillis()
                    break
                } catch (e: Exception) {
                    AppLog.w(TAG, "downloadWithMirrors: error $mirror: ${e.message}")
                    if (attempt == MIRROR_RETRY_ATTEMPTS) {
                        mirrorFailures[mirror] = System.currentTimeMillis()
                    }
                }
            }
        }

        throw Exception("All Overpass mirrors failed after ${OVERPASS_MIRRORS.size} mirrors x ${MIRROR_RETRY_ATTEMPTS + 1} attempts")
    }

    // ========================
    //  DISK CACHE (coordinate-aware)
    // ========================

    /**
     * Genera un nome file cache basato su coordinate arrotondate + raggio.
     * Arrotonda a 2 decimali (~1.1km) per gruppare zone vicine.
     */
    private fun makeCacheKey(centerLat: Double, centerLon: Double, radiusMeters: Int): String {
        val latRounded = (centerLat * 100).toLong() / 100.0
        val lonRounded = (centerLon * 100).toLong() / 100.0
        return "osm_${latRounded}_${lonRounded}_${radiusMeters}m.json"
    }

    private fun loadFromDisk(cacheKey: String): OsmData? {
        val ctx = appContext ?: return null
        try {
            val file = File(ctx.filesDir, cacheKey)
            if (!file.exists()) return null

            // Check TTL
            val age = System.currentTimeMillis() - file.lastModified()
            if (age > CACHE_MAX_AGE_MS) {
                AppLog.d(TAG, "loadFromDisk: cache expired (${age / 3600000}h old), deleting")
                file.delete()
                return null
            }

            AppLog.d(TAG, "loadFromDisk: reading ${file.length()} bytes from $cacheKey")
            val json = file.readText(Charsets.UTF_8)
            if (json.isEmpty()) return null

            val root = JsonParser.parseString(json).asJsonObject
            val bounds = root.getAsJsonObject("bounds") ?: return null
            val south = bounds.get("south").asDouble
            val north = bounds.get("north").asDouble
            val west = bounds.get("west").asDouble
            val east = bounds.get("east").asDouble

            val elements = root.getAsJsonArray("elements") ?: return null

            val nodesMap = mutableMapOf<Long, OsmNode>()
            val waysList = mutableListOf<OsmWay>()

            for (el in elements) {
                val obj = el.asJsonObject
                val type = obj.get("type").asString
                val id = obj.get("id").asLong

                if (type == "node") {
                    val lat = obj.get("lat").asDouble
                    val lon = obj.get("lon").asDouble
                    val tags = parseTags(obj)
                    nodesMap[id] = OsmNode(id, lat, lon, tags)
                }
            }

            for (el in elements) {
                val obj = el.asJsonObject
                val type = obj.get("type").asString
                val id = obj.get("id").asLong

                if (type == "way") {
                    val nodeIds = obj.getAsJsonArray("nodes")?.map { it.asLong } ?: emptyList()
                    val tags = parseTags(obj)
                    val way = OsmWay(id, nodeIds, tags)
                    way.nodes = nodeIds.mapNotNull { nodesMap[it] }
                    waysList.add(way)
                }
            }

            return OsmData(
                nodes = nodesMap,
                ways = waysList,
                south = south,
                north = north,
                west = west,
                east = east
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "loadFromDisk: FAILED to parse $cacheKey", e)
            return null
        }
    }

    private fun saveToDisk(cacheKey: String, responseBody: String, south: Double, north: Double, west: Double, east: Double) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, cacheKey)
            val root = JsonParser.parseString(responseBody).asJsonObject
            val bounds = JsonObject().apply {
                addProperty("south", south)
                addProperty("north", north)
                addProperty("west", west)
                addProperty("east", east)
            }
            root.add("bounds", bounds)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLog.w(TAG, "saveToDisk: failed to write $cacheKey: ${e.message}")
        }
    }

    // ========================
    //  CACHE CLEANUP
    // ========================

    /**
     * Elimina file cache più vecchi di CACHE_MAX_AGE_MS.
     * Chiamata all'init per pulire dati stale.
     */
    private fun cleanStaleCache() {
        val ctx = appContext ?: return
        try {
            val now = System.currentTimeMillis()
            val deleted = ctx.filesDir.listFiles { file ->
                file.name.startsWith("osm_") && file.name.endsWith(".json") &&
                    file.name != "osm_mini_chunk.json" &&
                    (now - file.lastModified()) > CACHE_MAX_AGE_MS
            }?.onEach { it.delete() }?.size ?: 0
            if (deleted > 0) {
                AppLog.d(TAG, "cleanStaleCache: deleted $deleted expired cache files")
            }
        } catch (e: Exception) {
            // Ignora errori di pulizia
        }
    }

    /**
     * Elimina tutte le cache disco (per forzare re-download).
     */
    fun clearDiskCache() {
        val ctx = appContext ?: return
        try {
            ctx.filesDir.listFiles { file ->
                file.name.startsWith("osm_") && file.name.endsWith(".json") &&
                    file.name != "osm_mini_chunk.json"
            }?.forEach { it.delete() }
        } catch (e: Exception) { }
    }

    // ========================
    //  OVERPASS QUERY
    // ========================

    private fun buildOverpassQuery(
        south: Double, west: Double,
        north: Double, east: Double
    ): String {
        return """
[out:json][timeout:25];
(
  way["highway"]($south,$west,$north,$east);
  way["building"]($south,$west,$north,$east);
  way["leisure"="park"]($south,$west,$north,$east);
  node["natural"="tree"]($south,$west,$north,$east);
  node["amenity"]($south,$west,$north,$east);
  node["shop"]($south,$west,$north,$east);
);
out body;
>;
out skel qt;
        """.trimIndent()
    }

    // ========================
    //  PARSING
    // ========================

    private fun parseOverpassResponse(
        json: String,
        south: Double, north: Double,
        west: Double, east: Double
    ): OsmData {
        val root = JsonParser.parseString(json).asJsonObject
        val elements = root.getAsJsonArray("elements") ?: return OsmData()

        val nodesMap = mutableMapOf<Long, OsmNode>()
        val waysList = mutableListOf<OsmWay>()

        for (el in elements) {
            val obj = el.asJsonObject
            val type = obj.get("type").asString
            val id = obj.get("id").asLong

            if (type == "node") {
                val lat = obj.get("lat").asDouble
                val lon = obj.get("lon").asDouble
                val tags = parseTags(obj)
                nodesMap[id] = OsmNode(id, lat, lon, tags)
            }
        }

        for (el in elements) {
            val obj = el.asJsonObject
            val type = obj.get("type").asString
            val id = obj.get("id").asLong

            if (type == "way") {
                val nodeIds = obj.getAsJsonArray("nodes")?.map { it.asLong } ?: emptyList()
                val tags = parseTags(obj)
                val way = OsmWay(id, nodeIds, tags)
                way.nodes = nodeIds.mapNotNull { nodesMap[it] }
                waysList.add(way)
            }
        }

        return OsmData(
            nodes = nodesMap,
            ways = waysList,
            south = south,
            north = north,
            west = west,
            east = east
        )
    }

    private fun parseTags(obj: JsonObject): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        val tagsObj = obj.getAsJsonObject("tags") ?: return tags
        for (key in tagsObj.keySet()) {
            val value = tagsObj.get(key)
            if (value != null && !value.isJsonNull) {
                tags[key] = value.asString
            }
        }
        return tags
    }

    // ========================
    //  WRAPPER & MINI-CHUNK
    // ========================

    /**
     * Wrapper compatibile con CityActivity (usa runBlocking perché è già in coroutine).
     */
    fun fetchAreaCached(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1000
    ): OsmData = runBlocking {
        fetchArea(centerLat, centerLon, radiusMeters)
    }

    /**
     * Carica il mini-chunk pre-inserito nell'APK da assets/osm_mini_chunk.json.
     * Restituisce OsmData con ~500m attorno al centro per avvio immediato.
     */
    fun loadMiniChunk(): OsmData? {
        val ctx = appContext ?: return null
        try {
            AppLog.d(TAG, "loadMiniChunk: reading from assets/osm_mini_chunk.json")
            val json = ctx.assets.open("osm_mini_chunk.json").bufferedReader().readText()
            AppLog.d(TAG, "loadMiniChunk: json size=${json.length} chars")
            val root = JsonParser.parseString(json).asJsonObject
            val bounds = root.getAsJsonObject("bounds") ?: return null
            val south = bounds.get("south").asDouble
            val north = bounds.get("north").asDouble
            val west = bounds.get("west").asDouble
            val east = bounds.get("east").asDouble

            val data = parseOverpassResponse(json, south, north, west, east)
            AppLog.d(TAG, "loadMiniChunk: parsed (roads=${data.roads.size}, buildings=${data.buildings.size}, trees=${data.trees.size})")
            return data
        } catch (e: Exception) {
            AppLog.e(TAG, "loadMiniChunk: FAILED", e)
            return null
        }
    }
}

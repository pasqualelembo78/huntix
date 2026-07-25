package com.intelligame.huntix.reallife

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client per scaricare dati OpenStreetMap da Overpass API.
 *
 * Scarica una volta, salva su disco, successivamente carica da file.
 *
 * Usage:
 *   OsmClient.init(context)
 *   val data = OsmClient.fetchArea(centerLat, centerLon, radiusMeters)
 */
object OsmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var appContext: Context? = null
    private const val CACHE_FILE = "osm_rome_1km.json"

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Scarica dati OSM per un'area circolare, con cache persistente su disco.
     * Prima volta: scarica da Overpass API, salva su file.
     * Volte successive: carica da file, zero rete, avvio istantaneo.
     */
    suspend fun fetchArea(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 1000
    ): OsmData = withContext(Dispatchers.IO) {
        val cached = loadFromDisk()
        if (cached != null) {
            CoordinateConverter.init(centerLat, centerLon)
            return@withContext cached
        }

        CoordinateConverter.init(centerLat, centerLon)

        val latDelta = radiusMeters / 110540.0
        val lonDelta = radiusMeters / (111320.0 * Math.cos(Math.toRadians(centerLat)))
        val south = centerLat - latDelta
        val north = centerLat + latDelta
        val west = centerLon - lonDelta
        val east = centerLon + lonDelta

        val query = buildOverpassQuery(south, west, north, east)

        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HuntixGame/1.0")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Overpass API error: ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response from Overpass API")
        response.close()

        val data = parseOverpassResponse(body, south, north, west, east)

        saveToDisk(body, south, north, west, east)

        data
    }

    /**
     * Carica i dati OSM dalla cache su disco.
     */
    private fun loadFromDisk(): OsmData? {
        val ctx = appContext ?: return null
        try {
            val file = File(ctx.filesDir, CACHE_FILE)
            if (!file.exists()) return null

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
            return null
        }
    }

    /**
     * Salva la risposta Overpass JSON su disco con bounds.
     */
    private fun saveToDisk(responseBody: String, south: Double, north: Double, west: Double, east: Double) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, CACHE_FILE)
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
            // Ignora errori di scrittura
        }
    }

    private fun buildOverpassQuery(
        south: Double, west: Double,
        north: Double, east: Double
    ): String {
        return """
[out:json][timeout:60];
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

    /**
     * Elimina la cache disco (per forzare re-download).
     */
    fun clearDiskCache() {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, CACHE_FILE).delete()
        } catch (e: Exception) { }
    }

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
     * Restituisce OsmData con ~200m attorno al Colosseo per avvio immediato.
     */
    fun loadMiniChunk(): OsmData? {
        val ctx = appContext ?: return null
        try {
            val json = ctx.assets.open("osm_mini_chunk.json").bufferedReader().readText()
            val root = JsonParser.parseString(json).asJsonObject
            val bounds = root.getAsJsonObject("bounds") ?: return null
            val south = bounds.get("south").asDouble
            val north = bounds.get("north").asDouble
            val west = bounds.get("west").asDouble
            val east = bounds.get("east").asDouble

            // Usa lo stesso parser del download
            return parseOverpassResponse(json, south, north, west, east)
        } catch (e: Exception) {
            return null
        }
    }
}

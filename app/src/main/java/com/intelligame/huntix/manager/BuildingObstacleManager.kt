package com.intelligame.huntix.manager

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BuildingObstacleManager — rileva edifici OSM tra utente e uovo
 * e suggerisce la direzione migliore per aggirarli.
 *
 * Usa Overpass API (gratuita, no API key) per scaricare i building
 * nella zona e calcola intersezioni linea-vertedice con i poligoni.
 */
class BuildingObstacleManager {

    companion object {
        private const val TAG = "BuildingObstacle"
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val FETCH_RADIUS_M = 500.0
    }

    data class Building(val nodes: List<Pair<Double, Double>>)

    data class ObstacleResult(
        val blocked: Boolean,
        val suggestion: String?,
        val relativeBearingDeg: Float
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fetchJob: Job? = null

    private var buildings: List<Building> = emptyList()
    private var lastFetchLat = 0.0
    private var lastFetchLng = 0.0
    private var lastFetchTime = 0L
    private var loading = false

    fun fetchBuildingsIfNeeded(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val distFromLast = haversineMeters(lat, lng, lastFetchLat, lastFetchLng)
        if (buildings.isNotEmpty() && distFromLast < 100 && (now - lastFetchTime) < CACHE_TTL_MS) {
            return
        }
        if (loading) return
        loading = true
        fetchJob?.cancel()
        fetchJob = scope.launch {
            try {
                val fetched = fetchFromOverpass(lat, lng)
                withContext(Dispatchers.Main) {
                    buildings = fetched
                    lastFetchLat = lat
                    lastFetchLng = lng
                    lastFetchTime = System.currentTimeMillis()
                    loading = false
                    Log.d(TAG, "Buildings caricati: ${fetched.size}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch buildings fallito: ${e.message}")
                withContext(Dispatchers.Main) { loading = false }
            }
        }
    }

    fun checkObstacle(
        userLat: Double, userLng: Double,
        eggLat: Double, eggLng: Double,
        userHeadingDeg: Float
    ): ObstacleResult {
        if (buildings.isEmpty()) {
            return ObstacleResult(false, null, 0f)
        }

        val bearingToEgg = bearingDeg(userLat, userLng, eggLat, eggLng)
        var relativeBearing = bearingToEgg - userHeadingDeg
        if (relativeBearing > 180f) relativeBearing -= 360f
        else if (relativeBearing < -180f) relativeBearing += 360f

        val blocked = isPathBlocked(userLat, userLng, eggLat, eggLng)
        if (!blocked) {
            return ObstacleResult(false, null, relativeBearing)
        }

        val leftClear = isDirectionClear(userLat, userLng, eggLat, eggLng, userHeadingDeg, -45f)
        val rightClear = isDirectionClear(userLat, userLng, eggLat, eggLng, userHeadingDeg, 45f)

        val suggestion = when {
            leftClear && !rightClear -> "Gira a sinistra per aggirare l'ostacolo"
            rightClear && !leftClear -> "Gira a destra per aggirare l'ostacolo"
            leftClear && rightClear -> "Aggira l'ostacolo a destra o sinistra"
            else -> "Prosegui dritto e cerca un passaggio"
        }

        return ObstacleResult(true, suggestion, relativeBearing)
    }

    private fun isPathBlocked(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Boolean {
        for (building in buildings) {
            if (lineIntersectsPolygon(lat1, lng1, lat2, lng2, building.nodes)) {
                return true
            }
        }
        return false
    }

    private fun isDirectionClear(
        userLat: Double, userLng: Double,
        eggLat: Double, eggLng: Double,
        headingDeg: Float,
        offsetDeg: Float
    ): Boolean {
        val bearing = bearingDeg(userLat, userLng, eggLat, eggLng)
        val testBearing = bearing + offsetDeg.toDouble()
        val dist = haversineMeters(userLat, userLng, eggLat, eggLng)
        val testDist = dist * 1.3

        val testLatLng = offsetLatLon(userLat, userLng, testDist, testBearing)
        val midLat = (userLat + testLatLng.first) / 2.0
        val midLng = (userLng + testLatLng.second) / 2.0
        val endLat = testLatLng.first
        val endLng = testLatLng.second

        return !isPathBlocked(userLat, userLng, midLat, midLng) &&
                !isPathBlocked(midLat, midLng, endLat, endLng)
    }

    private fun lineIntersectsPolygon(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
        polygon: List<Pair<Double, Double>>
    ): Boolean {
        if (polygon.size < 3) return false

        if (pointInPolygon(lat1, lng1, polygon) || pointInPolygon(lat2, lng2, polygon)) {
            return true
        }

        for (i in polygon.indices) {
            val j = (i + 1) % polygon.size
            val (pLat1, pLng1) = polygon[i]
            val (pLat2, pLng2) = polygon[j]
            if (segmentsIntersect(lat1, lng1, lat2, lng2, pLat1, pLng1, pLat2, pLng2)) {
                return true
            }
        }
        return false
    }

    private fun pointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var i = polygon.size - 1
        var j = 0
        while (j < polygon.size) {
            val (yi, xi) = polygon[i]
            val (yj, xj) = polygon[j]
            if (((yi > lat) != (yj > lat)) &&
                (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) {
                inside = !inside
            }
            i = j
            j++
        }
        return inside
    }

    private fun segmentsIntersect(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double
    ): Boolean {
        val denom = (bx - ax) * (dy - cy) - (by - ay) * (dx - cx)
        if (abs(denom) < 1e-12) return false

        val t = ((cx - ax) * (dy - cy) - (cy - ay) * (dx - cx)) / denom
        val u = ((cx - ax) * (by - ay) - (cy - ay) * (bx - ax)) / denom

        return t in 0.0..1.0 && u in 0.0..1.0
    }

    private suspend fun fetchFromOverpass(centerLat: Double, centerLng: Double): List<Building> {
        val dLat = FETCH_RADIUS_M / 111320.0
        val cosLat = cos(Math.toRadians(centerLat))
        val dLng = if (abs(cosLat) > 1e-10) FETCH_RADIUS_M / (111320.0 * cosLat) else 0.0

        val south = centerLat - dLat
        val north = centerLat + dLat
        val west = centerLng - dLng
        val east = centerLng + dLng

        val query = """
            [out:json][timeout:10];
            way["building"]($south,$west,$north,$east);
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val formBody = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(OVERPASS_URL)
            .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Overpass HTTP ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        return parseOverpassJson(body)
    }

    private fun parseOverpassJson(json: String): List<Building> {
        val root = JsonParser.parseString(json).asJsonObject
        val elements = root.getAsJsonArray("elements") ?: return emptyList()

        val nodesMap = mutableMapOf<Long, Pair<Double, Double>>()
        for (el in elements) {
            val obj = el.asJsonObject
            if (obj.get("type").asString == "node") {
                val id = obj.get("id").asLong
                val lat = obj.get("lat").asDouble
                val lon = obj.get("lon").asDouble
                nodesMap[id] = lat to lon
            }
        }

        val buildings = mutableListOf<Building>()
        for (el in elements) {
            val obj = el.asJsonObject
            if (obj.get("type").asString == "way") {
                val nodesArray = obj.getAsJsonArray("nodes") ?: continue
                val coords = mutableListOf<Pair<Double, Double>>()
                for (n in nodesArray) {
                    val nodeId = n.asLong
                    nodesMap[nodeId]?.let { coords.add(it) }
                }
                if (coords.size >= 3) {
                    buildings.add(Building(coords))
                }
            }
        }
        return buildings
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * Math.asin(sqrt(a))
    }

    private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val la1 = Math.toRadians(lat1)
        val la2 = Math.toRadians(lat2)
        val y = sin(dLng) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun offsetLatLon(
        lat: Double, lng: Double,
        meters: Double, bearingDeg: Double
    ): Pair<Double, Double> {
        val r = 6371000.0
        val d = meters / r
        val la1 = Math.toRadians(lat)
        val lo1 = Math.toRadians(lng)
        val brng = Math.toRadians(bearingDeg)
        val la2 = Math.asin(
            sin(la1) * cos(d) + cos(la1) * sin(d) * cos(brng)
        )
        val lo2 = lo1 + atan2(
            sin(brng) * sin(d) * cos(la1),
            cos(d) - sin(la1) * sin(la2)
        )
        return Math.toDegrees(la2) to Math.toDegrees(lo2)
    }

    fun destroy() {
        fetchJob?.cancel()
        scope.cancel()
    }
}

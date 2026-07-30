package com.intelligame.huntix.manager

import android.content.Context
import com.intelligame.huntix.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 📡 OnlinePoiManager — Fetch POI per città da GitHub
 *
 * Flusso:
 *   1. GPS/levetta fornisce lat/lng
 *   2. Trova regione dal bounding box
 *   3. Scarica italia/{regione}/_citta.csv → elenco città con lat/lng
 *   4. Trova città più vicina
 *   5. Scarica italia/{regione}/{citta}/_all.csv
 *   6. Cache per città (24h)
 */
class OnlinePoiManager {

    private val BASE_URL = "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main"

    private val REGION_MAP = listOf(
        "abruzzo"          to listOf(39.5, 13.0, 42.5, 14.8),
        "basilicata"       to listOf(39.5, 15.5, 41.5, 17.0),
        "calabria"         to listOf(37.5, 15.5, 40.0, 17.5),
        "campania"         to listOf(39.5, 13.5, 41.5, 16.5),
        "emilia-romagna"   to listOf(43.5, 10.5, 45.5, 13.0),
        "friuli_v_g"       to listOf(45.5, 12.0, 47.0, 14.0),
        "lazio"            to listOf(40.5, 11.5, 43.0, 14.0),
        "liguria"          to listOf(43.5, 7.5, 44.8, 10.0),
        "lombardia"        to listOf(44.5, 8.5, 46.5, 11.5),
        "marche"           to listOf(42.5, 12.0, 44.0, 14.5),
        "molise"           to listOf(41.0, 13.5, 42.0, 15.0),
        "piemonte"         to listOf(44.0, 6.5, 46.5, 9.5),
        "puglia"           to listOf(39.5, 15.0, 42.5, 18.5),
        "sardegna"         to listOf(38.5, 8.0, 41.5, 10.0),
        "sicilia"          to listOf(36.5, 12.0, 38.5, 15.5),
        "toscana"          to listOf(42.0, 9.5, 44.0, 12.5),
        "trentino-a_a"     to listOf(45.5, 10.5, 47.0, 12.5),
        "umbria"           to listOf(42.0, 12.0, 43.5, 13.5),
        "valle_d_aosta"    to listOf(45.5, 6.5, 46.5, 8.0),
        "veneto"           to listOf(44.5, 10.5, 47.0, 13.5),
    )

    /** Dati città dall'indice _citta.csv */
    private data class CityEntry(
        val lat: Double,
        val lng: Double,
        val name: String,
        val slug: String
    )

    /** Cache: slug_citta -> (timestamp, pois) */
    private var cachedCitySlug: String? = null
    private var cachedPois: List<OnlinePoi>? = null
    private var cachedTime: Long = 0L

    /**
     * 🌐 Fetch POI per posizione GPS (reale o simulata dalla levetta).
     * Chiama con la posizione corrente del giocatore.
     */
    suspend fun fetchPoiForLocation(
        context: Context,
        lat: Double,
        lng: Double,
        maxPois: Int = 500,
        category: String? = null
    ): Result<List<OnlinePoi>> = withContext(Dispatchers.IO) {
        try {
            // 1. Se la città non è cambiata e cache valida (< 24h), usa cache
            var regionSlug = findRegion(lat, lng)
            if (regionSlug == null) {
                AppLog.d("OnlinePoi", "Fuori Italia, fallbackPois")
                return@withContext Result.success(fallbackPois())
            }

            AppLog.d("OnlinePoi", "Initial region: $regionSlug")

            // 2. Trova la città più vicina — se il primo match non ha dati, prova altre regioni
            var cities = fetchCittaIndex(context, regionSlug)
            if (cities.isEmpty()) {
                val altRegion = findAltRegion(lat, lng, regionSlug)
                if (altRegion != null) {
                    AppLog.d("OnlinePoi", "No cities for $regionSlug, trying $altRegion")
                    regionSlug = altRegion
                    cities = fetchCittaIndex(context, regionSlug)
                }
            }
            if (cities.isEmpty()) {
                AppLog.d("OnlinePoi", "Nessuna città per nessuna regione, fallbackPois")
                return@withContext Result.success(fallbackPois())
            }
            AppLog.d("OnlinePoi", "Final region: $regionSlug, ${cities.size} cities")
            val nearestCity = findNearestCity(cities, lat, lng)
            AppLog.d("OnlinePoi", "Nearest: ${nearestCity?.name} (${nearestCity?.slug})")

            if (nearestCity != null && nearestCity.slug == cachedCitySlug && cachedPois != null
                && System.currentTimeMillis() - cachedTime < 24 * 60 * 60 * 1000L) {
                AppLog.d("OnlinePoi", "Cache hit: ${nearestCity.name} (${cachedPois!!.size} POIs)")
                val cached = cachedPois!!
                val pois = if (cached.size > maxPois) {
                    val sorted = cached.sortedBy { haversine(lat, lng, it.lat, it.lng) }
                    sorted.take(maxPois)
                } else cached
                return@withContext Result.success(pois)
            }

            // 3. Scarica POI della città (già filtrati per distanza)
            val pois = if (nearestCity != null) {
                AppLog.d("OnlinePoi", "Fetching city POIs: $regionSlug/${nearestCity.slug}")
                val cityPois = fetchCityPois(regionSlug, nearestCity.slug, lat, lng, maxPois)
                if (cityPois.isEmpty()) {
                    AppLog.d("OnlinePoi", "City POIs empty, fallback to region: $regionSlug")
                    fetchRegionPois(regionSlug, lat, lng, maxPois)
                } else cityPois
            } else {
                AppLog.d("OnlinePoi", "No nearest city — fetching region POIs: $regionSlug")
                fetchRegionPois(regionSlug, lat, lng, maxPois)
            }
            AppLog.d("OnlinePoi", "Got ${pois.size} POIs")

            // 4. Salva in cache solo se ci sono POI
            if (pois.isNotEmpty()) {
                cachedCitySlug = nearestCity?.slug
                cachedPois = pois
                cachedTime = System.currentTimeMillis()
                savePoisToCache(context, regionSlug, nearestCity?.slug, pois)
            }

            Result.success(pois)
        } catch (e: Exception) {
            // Fallback offline
            val cached = loadCachedPois(context)
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.success(fallbackPois())
            }
        }
    }

    /** Backward-compatible: fetch per bounding box (delegates to fetchPoiForLocation) */
    suspend fun fetchPoiForRegion(
        context: Context,
        southwestLat: Double,
        northeastLat: Double,
        southwestLng: Double,
        northeastLng: Double,
        category: String? = null
    ): Result<List<OnlinePoi>> {
        val centerLat = (southwestLat + northeastLat) / 2
        val centerLng = (southwestLng + northeastLng) / 2
        return fetchPoiForLocation(context, centerLat, centerLng, category = category)
    }

    // --- PRIVATE ---

    private fun findRegion(lat: Double, lng: Double): String? {
        for ((slug, bbox) in REGION_MAP) {
            if (lat >= bbox[0] && lat <= bbox[2] && lng >= bbox[1] && lng <= bbox[3]) {
                return slug
            }
        }
        return null
    }

    /** Se la prima regione non ha dati (e.g. bbox troppo largo), prova altre regioni */
    private fun findAltRegion(lat: Double, lng: Double, excludeSlug: String): String? {
        for ((slug, bbox) in REGION_MAP) {
            if (slug == excludeSlug) continue
            if (lat >= bbox[0] && lat <= bbox[2] && lng >= bbox[1] && lng <= bbox[3]) {
                return slug
            }
        }
        return null
    }

    /** Fetch italia/{regione}/_citta.csv e parsifica */
    private fun fetchCittaIndex(context: Context, regionSlug: String): List<CityEntry> {
        val cacheKey = "citta_index_$regionSlug"
        val prefs = context.getSharedPreferences("huntix_poi_citta", Context.MODE_PRIVATE)
        val cached = prefs.getString(cacheKey, null)
        val lastFetch = prefs.getLong("${cacheKey}_time", 0)

        // Cache indice città per 7 giorni
        if (cached != null && System.currentTimeMillis() - lastFetch < 7 * 24 * 60 * 60 * 1000L) {
            return parseCittaIndex(cached)
        }

        val url = "$BASE_URL/italia/$regionSlug/_citta.csv"
        val text = httpGet(url)
        if (text == null) {
            AppLog.w("OnlinePoi", "Failed to fetch _citta.csv for $regionSlug")
            return emptyList()
        }
        prefs.edit().putString(cacheKey, text).putLong("${cacheKey}_time", System.currentTimeMillis()).apply()
        return parseCittaIndex(text)
    }

    /** Parse _citta.csv: lat,lng,citta,slug,... */
    private fun parseCittaIndex(csv: String): List<CityEntry> {
        val cities = mutableListOf<CityEntry>()
        for (line in csv.lines()) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split(",")
            if (parts.size < 4) continue
            val lat = parts[0].toDoubleOrNull() ?: continue
            val lng = parts[1].toDoubleOrNull() ?: continue
            val name = parts[2].trim()
            val slug = parts[3].trim()
            cities.add(CityEntry(lat, lng, name, slug))
        }
        return cities
    }

    /** Trova città più vicina alla posizione data */
    private fun findNearestCity(cities: List<CityEntry>, lat: Double, lng: Double): CityEntry? {
        if (cities.isEmpty()) return null
        var best: CityEntry? = null
        var bestDist = Double.MAX_VALUE
        for (c in cities) {
            val dLat = c.lat - lat
            val dLng = (c.lng - lng) * cos(Math.toRadians(lat))
            val dist = sqrt(dLat * dLat + dLng * dLng)
            if (dist < bestDist) {
                bestDist = dist
                best = c
            }
        }
        return best
    }

    /** Fetch italia/{regione}/{citta}/_all.csv */
    private fun fetchCityPois(regionSlug: String, citySlug: String, refLat: Double, refLng: Double, maxPois: Int): List<OnlinePoi> {
        val url = "$BASE_URL/italia/$regionSlug/$citySlug/_all.csv"
        val text = httpGet(url) ?: return emptyList()
        return parseCsvNearest(text, refLat, refLng, maxPois)
    }

    /** Fetch italia/{regione}/_all.csv (fallback regione intera) */
    private fun fetchRegionPois(regionSlug: String, refLat: Double, refLng: Double, maxPois: Int): List<OnlinePoi> {
        val url = "$BASE_URL/italia/$regionSlug/_all.csv"
        val text = httpGet(url) ?: return fetchFromGlobal(refLat, refLng, maxPois)
        return parseCsvNearest(text, refLat, refLng, maxPois)
    }

    /** Fetch global_pois.csv (ultimo fallback) */
    private fun fetchFromGlobal(refLat: Double, refLng: Double, maxPois: Int): List<OnlinePoi> {
        val url = "$BASE_URL/global_pois.csv"
        val text = httpGet(url) ?: return fallbackPois()
        return parseCsvNearest(text, refLat, refLng, maxPois)
    }

    /** HTTP GET semplice */
    private fun httpGet(url: String): String? {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Huntix/2.0")
            val code = conn.responseCode
            if (code != 200) {
                AppLog.w("OnlinePoi", "HTTP $code for $url")
                return null
            }
            val text = conn.inputStream.bufferedReader().readText()
            AppLog.d("OnlinePoi", "Downloaded ${text.length} bytes from $url")
            return text
        } catch (e: Exception) {
            AppLog.w("OnlinePoi", "httpGet failed: $url — ${e.message}")
            return null
        }
    }

    /** Parse CSV keeping only the closest maxPois to (refLat, refLng) — O(N log K) */
    private fun parseCsvNearest(csv: String, refLat: Double, refLng: Double, maxPois: Int): List<OnlinePoi> {
        if (maxPois <= 0) return emptyList()
        // Max-heap: farthest at top, so we can evict it when over capacity
        val heap = java.util.PriorityQueue<OnlinePoi>(maxPois + 1) { a, b ->
            val da = haversine(refLat, refLng, b.lat, b.lng).toDouble()
            val db = haversine(refLat, refLng, a.lat, a.lng).toDouble()
            da.compareTo(db)
        }
        for (line in csv.lines()) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split(",")
            if (parts.size < 6) continue
            val lat = parts[0].toDoubleOrNull() ?: continue
            val lng = parts[1].toDoubleOrNull() ?: continue
            val poi = OnlinePoi(
                id = parts[2].trim(),
                name = parts[3].trim().removeSurrounding("\""),
                lat = lat, lng = lng,
                buildingType = parts[4].trim(),
                poiType = parts[5].trim(),
                url = if (parts.size >= 7) parts[6].trim().removeSurrounding("\"") else "",
                pageType = if (parts.size >= 8) parts[7].trim().removeSurrounding("\"") else ""
            )
            heap.add(poi)
            if (heap.size > maxPois) heap.poll()
        }
        return heap.sortedBy { haversine(refLat, refLng, it.lat, it.lng) }
    }

    // --- CACHE OFFLINE ---

    private fun savePoisToCache(context: Context, regionSlug: String, citySlug: String?, pois: List<OnlinePoi>) {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.csv")
            FileWriter(cacheFile).use { writer ->
                writer.write("$regionSlug,$citySlug\n")
                for (p in pois) {
                    writer.write("${p.lat},${p.lng},${p.id},${p.name},${p.buildingType},${p.poiType},${p.url},${p.pageType}\n")
                }
            }
        } catch (_: Exception) {}
    }

    /** Simple parse for offline cache (file is already limited) */
    private fun parseCsvAll(csv: String): List<OnlinePoi> {
        val pois = mutableListOf<OnlinePoi>()
        for (line in csv.lines()) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split(",")
            if (parts.size < 6) continue
            val lat = parts[0].toDoubleOrNull() ?: continue
            val lng = parts[1].toDoubleOrNull() ?: continue
            pois.add(OnlinePoi(
                id = parts[2].trim(),
                name = parts[3].trim().removeSurrounding("\""),
                lat = lat, lng = lng,
                buildingType = parts[4].trim(),
                poiType = parts[5].trim(),
                url = if (parts.size >= 7) parts[6].trim().removeSurrounding("\"") else "",
                pageType = if (parts.size >= 8) parts[7].trim().removeSurrounding("\"") else ""
            ))
        }
        return pois
    }

    private fun loadCachedPois(context: Context): List<OnlinePoi>? {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.csv")
            if (!cacheFile.exists()) return null
            val lines = cacheFile.readLines()
            if (lines.isEmpty()) return null
            // Skip header line (region,city)
            return parseCsvAll(lines.drop(1).joinToString("\n"))
        } catch (_: Exception) {
            return null
        }
    }

    private fun haversine(la1: Double, ln1: Double, la2: Double, ln2: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(la2 - la1)
        val dLng = Math.toRadians(ln2 - ln1)
        val sinDLat = Math.sin(dLat / 2)
        val sinDLng = Math.sin(dLng / 2)
        val a = sinDLat * sinDLat +
                Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) *
                sinDLng * sinDLng
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }

    private fun fallbackPois(): List<OnlinePoi> {
        return listOf(
            OnlinePoi("poi_roma_colosseo", "Colosseo", 41.8902, 12.4924, "landmark", "monumento", "https://www.colosseo.it"),
            OnlinePoi("poi_roma_piazza_venezia", "Piazza Venezia", 41.8954, 12.4843, "square", "piazza"),
            OnlinePoi("poi_roma_fontana_di_trevi", "Fontana di Trevi", 41.9010, 12.4830, "fountain", "monumento"),
            OnlinePoi("poi_roma_pantheon", "Pantheon", 41.8980, 12.4769, "building", "monumento"),
            OnlinePoi("poi_roma_villa_borghese", "Villa Borghese", 41.9418, 12.4744, "park", "naturale")
        )
    }
}

/** 🎯 Dati POI in memoria */
data class OnlinePoi(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val buildingType: String,
    val poiType: String,
    val url: String = "",
    val pageType: String = ""
)

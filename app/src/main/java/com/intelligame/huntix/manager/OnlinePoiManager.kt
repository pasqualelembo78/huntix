package com.intelligame.huntix.manager

import android.content.Context
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
        category: String? = null
    ): Result<List<OnlinePoi>> = withContext(Dispatchers.IO) {
        try {
            // 1. Se la città non è cambiata e cache valida (< 24h), usa cache
            val regionSlug = findRegion(lat, lng)
            if (regionSlug == null) {
                return@withContext Result.success(fetchFromGlobal())
            }

            // 2. Trova la città più vicina
            val cities = fetchCittaIndex(context, regionSlug)
            val nearestCity = findNearestCity(cities, lat, lng)

            if (nearestCity != null && nearestCity.slug == cachedCitySlug && cachedPois != null
                && System.currentTimeMillis() - cachedTime < 24 * 60 * 60 * 1000L) {
                android.util.Log.d("OnlinePoi", "Cache hit: ${nearestCity.name}")
                return@withContext Result.success(cachedPois!!)
            }

            // 3. Scarica POI della città
            val pois = if (nearestCity != null) {
                fetchCityPois(regionSlug, nearestCity.slug)
            } else {
                // Fallback: scarica tutta la regione
                fetchRegionPois(regionSlug)
            }

            // 4. Salva in cache
            cachedCitySlug = nearestCity?.slug
            cachedPois = pois
            cachedTime = System.currentTimeMillis()
            savePoisToCache(context, regionSlug, nearestCity?.slug, pois)

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
        return fetchPoiForLocation(context, centerLat, centerLng, category)
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
        val text = httpGet(url) ?: return emptyList()
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
    private fun fetchCityPois(regionSlug: String, citySlug: String): List<OnlinePoi> {
        val url = "$BASE_URL/italia/$regionSlug/$citySlug/_all.csv"
        val text = httpGet(url) ?: return emptyList()
        return parseCsv(text)
    }

    /** Fetch italia/{regione}/_all.csv (fallback regione intera) */
    private fun fetchRegionPois(regionSlug: String): List<OnlinePoi> {
        val url = "$BASE_URL/italia/$regionSlug/_all.csv"
        val text = httpGet(url) ?: return fetchFromGlobal()
        return parseCsv(text)
    }

    /** Fetch global_pois.csv (ultimo fallback) */
    private fun fetchFromGlobal(): List<OnlinePoi> {
        val url = "$BASE_URL/global_pois.csv"
        val text = httpGet(url) ?: return fallbackPois()
        return parseCsv(text)
    }

    /** HTTP GET semplice */
    private fun httpGet(url: String): String? {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Huntix/2.0")
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            return null
        }
    }

    /** Parse CSV: lat,lng,id,name,building_type,type */
    private fun parseCsv(csv: String): List<OnlinePoi> {
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
                lat = lat,
                lng = lng,
                buildingType = parts[4].trim(),
                poiType = parts[5].trim()
            ))
        }
        return pois
    }

    // --- CACHE OFFLINE ---

    private fun savePoisToCache(context: Context, regionSlug: String, citySlug: String?, pois: List<OnlinePoi>) {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.csv")
            FileWriter(cacheFile).use { writer ->
                writer.write("$regionSlug,$citySlug\n")
                for (p in pois) {
                    writer.write("${p.lat},${p.lng},${p.id},${p.name},${p.buildingType},${p.poiType}\n")
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadCachedPois(context: Context): List<OnlinePoi>? {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.csv")
            if (!cacheFile.exists()) return null
            val lines = cacheFile.readLines()
            if (lines.isEmpty()) return null
            // Skip header line (region,city)
            return parseCsv(lines.drop(1).joinToString("\n"))
        } catch (_: Exception) {
            return null
        }
    }

    private fun fallbackPois(): List<OnlinePoi> {
        return listOf(
            OnlinePoi("poi_roma_colosseo", "Colosseo", 41.8902, 12.4924, "landmark", "monumento"),
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
    val poiType: String
)

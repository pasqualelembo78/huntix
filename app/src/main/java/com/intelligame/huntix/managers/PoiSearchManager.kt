package com.intelligame.huntix.managers

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PoiSearchManager {

    companion object {
        private const val BASE_URL = "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main"
        private const val CACHE_DIR = "poi_search_cache"

        data class SearchResult(
            val id: String,
            val name: String,
            val lat: Double,
            val lng: Double,
            val buildingType: String,
            val poiType: String,
            val url: String,
            val pageType: String,
            val city: String,
            val region: String,
            val category: String
        )
    }

    fun searchByName(query: String, context: Context, callback: (List<SearchResult>) -> Unit) {
        if (query.length < 2) { callback(emptyList()); return }
        val q = query.trim().lowercase()
        Thread {
            val results = mutableListOf<SearchResult>()
            try {
                val cityIndex = loadCityIndex(context)
                for ((regionSlug, citySlug, cityName) in cityIndex) {
                    val pois = loadCityPois(context, regionSlug, citySlug)
                    for (p in pois) {
                        val n = p.name.lowercase()
                        val t = p.poiType.lowercase()
                        if (n.contains(q) || t.contains(q)) {
                            results.add(SearchResult(
                                id = p.id, name = p.name, lat = p.lat, lng = p.lng,
                                buildingType = p.buildingType, poiType = p.poiType,
                                url = p.url, pageType = p.pageType,
                                city = cityName, region = regionSlug, category = p.poiType
                            ))
                        }
                    }
                }
            } catch (_: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(results) }
        }.start()
    }

    fun fetchCitiesForRegion(regionSlug: String, context: Context, callback: (List<Triple<String, String, String>>) -> Unit) {
        Thread {
            val cities = mutableListOf<Triple<String, String, String>>()
            try {
                val url = "$BASE_URL/italia/$regionSlug/_citta.csv"
                val text = httpGet(url) ?: run { callback(emptyList()); return@Thread }
                for (line in text.lines().drop(1)) {
                    val p = line.split(",")
                    if (p.size >= 3) {
                        cities.add(Triple(p[0].trim(), p[1].trim(), p[2].trim().removeSurrounding("\"")))
                    }
                }
            } catch (_: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(cities) }
        }.start()
    }

    fun fetchPoisForCity(regionSlug: String, citySlug: String, context: Context, callback: (List<SearchResult>) -> Unit) {
        Thread {
            val results = mutableListOf<SearchResult>()
            try {
                val url = "$BASE_URL/italia/$regionSlug/$citySlug/_all.csv"
                val text = httpGet(url) ?: run { callback(emptyList()); return@Thread }
                for (line in text.lines().drop(1)) {
                    if (line.startsWith("#") || line.isBlank()) continue
                    val parts = line.split(",")
                    if (parts.size < 6) continue
                    val lat = parts[0].toDoubleOrNull() ?: continue
                    val lng = parts[1].toDoubleOrNull() ?: continue
                    results.add(SearchResult(
                        id = parts[2].trim(), name = parts[3].trim().removeSurrounding("\""),
                        lat = lat, lng = lng,
                        buildingType = parts[4].trim(), poiType = parts[5].trim(),
                        url = if (parts.size >= 7) parts[6].trim().removeSurrounding("\"") else "",
                        pageType = if (parts.size >= 8) parts[7].trim().removeSurrounding("\"") else "",
                        city = citySlug, region = regionSlug, category = parts[5].trim()
                    ))
                }
            } catch (_: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(results) }
        }.start()
    }

    fun getJsonPageUrl(result: SearchResult): String {
        if (result.pageType == "custom" && result.url.isNotBlank()) return result.url
        val slug = result.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "$BASE_URL/output/pages/${result.city}/${result.category}/$slug.json"
    }

    private fun loadCityIndex(context: Context): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        val cacheDir = File(context.filesDir, CACHE_DIR)
        if (!cacheDir.exists()) return result
        cacheDir.listFiles()?.forEach { regionDir ->
            if (regionDir.isDirectory) {
                regionDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith("_citta.csv")) {
                        try {
                            for (line in f.readLines().drop(1)) {
                                val p = line.split(",")
                                if (p.size >= 3) {
                                    result.add(Triple(regionDir.name, f.name.removeSuffix("_citta.csv"), p[2].trim().removeSurrounding("\"")))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        return result
    }

    private fun loadCityPois(context: Context, region: String, city: String): List<SearchResult> {
        val cacheDir = File(context.filesDir, CACHE_DIR)
        val regionDir = File(cacheDir, region)
        val cached = File(regionDir, "${city}_all.csv")
        if (cached.exists()) {
            try {
                val results = mutableListOf<SearchResult>()
                for (line in cached.readLines().drop(1)) {
                    if (line.startsWith("#") || line.isBlank()) continue
                    val parts = line.split(",")
                    if (parts.size < 6) continue
                    val lat = parts[0].toDoubleOrNull() ?: continue
                    val lng = parts[1].toDoubleOrNull() ?: continue
                    results.add(SearchResult(
                        id = parts[2].trim(), name = parts[3].trim().removeSurrounding("\""),
                        lat = lat, lng = lng,
                        buildingType = parts[4].trim(), poiType = parts[5].trim(),
                        url = if (parts.size >= 7) parts[6].trim().removeSurrounding("\"") else "",
                        pageType = if (parts.size >= 8) parts[7].trim().removeSurrounding("\"") else "",
                        city = city, region = region, category = parts[5].trim()
                    ))
                }
                return results
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun httpGet(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                reader.readText()
            } else null
        } catch (_: Exception) { null }
    }
}

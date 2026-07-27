package com.intelligame.huntix.manager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 📡 OnlinePoiManager — Recupera e cache global POI database online
 * 
 * Carica:
 *   • CSV/JSON da GitHub storage pubblico (repository condiviso)
 *   • Cache locale per giocatori offline o con slow connesione
 *   • Soprascrizione sicura per POI più recenti
 */
class OnlinePoiManager {

    /** 📋 Endpoint Database Gratuiti Pubblici */
    private val ENDPOINTS = mapOf(
        "global_pois" to "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/global_pois.csv",
        "gyms" to "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/gyms.csv",
        "restaurants" to "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/restaurants.csv",
        "hospitals" to "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/hospitals.csv",
        "landmarks" to "https://raw.githubusercontent.com/pasqualelembo78/huntix-poi/main/landmarks.csv"
    )

    private val gson = Gson()

    /** 🌐 Recupera POI per regione geografica da fonti online */
    suspend fun fetchPoiForRegion(
        context: Context,
        southwestLat: Double,
        northeastLat: Double,
        southwestLng: Double,
        northeastLng: Double,
        category: String? = null // Filtro opzionale come "gym", "restaurant"
    ): Result<List<OnlinePoi>> = withContext(Dispatchers.IO) {
        try {
            // 1. Verifica se dati cache recente esiste (max 24h)
            val cachedPois = loadCachedPois(context)
            if (cachedPois != null) {
                return@withContext Result.success(cachedPois)
            }

            // 2. Recupera POI dai database online
            val pois = fetchPoiFromOnlineEndpoints(southwestLat, northeastLat, southwestLng, northeastLng, category)

            // 3. Salva in cache per uso offline
            savePoisToCache(context, pois)

            Result.success(pois)
        } catch (e: Exception) {
            // 4. Se online fallisce, restituisci POI hardcoded come fallback
            Result.success(fallbackPois())
        }
    }

    /** 📥 Recupera dati da tutti gli endpoint online abilitati */
    private fun fetchPoiFromOnlineEndpoints(
        swLat: Double, neLat: Double, swLng: Double, neLng: Double,
        category: String?
    ): List<OnlinePoi> {
        val allPois = mutableListOf<OnlinePoi>()

        // Testa ogni endpoint
        for ((name, url) in ENDPOINTS) {
            if (category == null || name == category) {
                try {
                    allPois.addAll(fetchPoiFromUrl(url, swLat, neLat, swLng, neLng))
                } catch (e: Exception) {
                    // Continua con altri endpoint se uno fallisce
                    println("⚠️  Failed to fetch $name: ${e.message}")
                }
            }
        }

        return allPois
    }

    /** 🔗 Recupera POI da singolo URL (CSV o JSON) */
    private fun fetchPoiFromUrl(
        url: String,
        swLat: Double, neLat: Double,
        swLng: Double, neLng: Double
    ): List<OnlinePoi> {
        try {
            val urlObj = URL(url)
            val connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Huntix-OfflinePoi/1.0")

            if (connection.responseCode == 200) {
                val contentType = connection.getHeaderField("Content-Type") ?: ""
                val responseData = connection.inputStream.bufferedReader().readText()

                return when {
                    contentType.contains("application/json") -> parseJsonResponse(responseData, swLat, neLat, swLng, neLng)
                    contentType.contains("text/csv") || contentType.isEmpty() -> parseCsvResponse(responseData, swLat, neLat, swLng, neLng)
                    else -> parseSimpleTextResponse(responseData, swLat, neLat, swLng, neLng)
                }
            } else {
                println("⚠️  HTTP ${connection.responseCode} per $url")
            }
        } catch (e: Exception) {
            println("❌ Errore fetch $url: ${e.message}")
        }
        return emptyList()
    }

    /** 📝 Parse CSV response (format: lat,lng,id,name,building_type,type) */
    private fun parseCsvResponse(csv: String, swLat: Double, neLat: Double, swLng: Double, neLng: Double): List<OnlinePoi> {
        val pois = mutableListOf<OnlinePoi>()
        val lines = csv.split('\n').filter { it.trim().isNotEmpty() && !it.startsWith('#') }

        for (line in lines) {
            try {
                val parts = line.split(',')
                if (parts.size >= 6) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()

                    // Filtra per perimetro di bounding box – rifiuta le coordinate fuori area
                    if (lat != null && lng != null &&
                        lat >= swLat && lat <= neLat &&
                        lng >= swLng && lng <= neLng) {

                        val poi = OnlinePoi(
                            id = parts[2].ifEmpty { "poi_${lat}_${lng}" },
                            name = parts[3],
                            lat = lat,
                            lng = lng,
                            buildingType = parts[4],
                            poiType = parts[5]
                        )
                        pois.add(poi)
                    }
                }
            } catch (e: Exception) {
                continue // Salta righe malformed
            }
        }

        return pois
    }

    /** 📝 Parse JSON response (Array di oggetti POI) */
    private fun parseJsonResponse(json: String, swLat: Double, neLat: Double, swLng: Double, neLng: Double): List<OnlinePoi> {
        return try {
            val listType = object : TypeToken<List<JsonPoi>>() {}.type
            val jsonPois = gson.fromJson<List<JsonPoi>>(json, listType)

            jsonPois.filter { poi ->
                poi.lat >= swLat && poi.lat <= neLat &&
                    poi.lng >= swLng && poi.lng <= neLng
            }.map { jsonPoi ->
                OnlinePoi(
                    id = jsonPoi.id,
                    name = jsonPoi.name,
                    lat = jsonPoi.lat,
                    lng = jsonPoi.lng,
                    buildingType = jsonPoi.buildingType,
                    poiType = jsonPoi.poiType
                )
            }
        } catch (e: Exception) {
            println("❌ JSON parsing fallito: ${e.message}")
            emptyList()
        }
    }

    /** 📝 Parse response text semplice (formato fixed line: lat,lng,id,name,type) */
    private fun parseSimpleTextResponse(text: String, swLat: Double, neLat: Double, swLng: Double, neLng: Double): List<OnlinePoi> {
        val pois = mutableListOf<OnlinePoi>()
        val lines = text.split('\n').filter { it.trim().isNotEmpty() }

        for (line in lines) {
            try {
                val parts = line.split('|')
                if (parts.size >= 5) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()

                    if (lat != null && lng != null &&
                        lat >= swLat && lat <= neLat &&
                        lng >= swLng && lng <= neLng) {

                        val poi = OnlinePoi(
                            id = parts[2],
                            name = parts[3],
                            lat = lat,
                            lng = lng,
                            buildingType = parts[4],
                            poiType = "unknown"
                        )
                        pois.add(poi)
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        return pois
    }

    /** 💾 Salva POI in cache locale persistente */
    private fun savePoisToCache(context: Context, pois: List<OnlinePoi>) {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.json")
            val json = gson.toJson(pois)
            FileWriter(cacheFile).use { writer ->
                writer.write(json)
            }
            println("💾 POI salvati in cache: ${pois.size} entry")
        } catch (e: Exception) {
            println("❌ Impossibile salvare cache: ${e.message}")
        }
    }

    /** 📂 Recupera dati cache POI se disponibili e non scaduti */
    private fun loadCachedPois(context: Context): List<OnlinePoi>? {
        try {
            val cacheFile = File(context.filesDir, "poi_cache.json")
            if (!cacheFile.exists()) {
                return null
            }

            val json = cacheFile.readText()
            val pois = gson.fromJson<List<OnlinePoi>>(json, object : TypeToken<List<OnlinePoi>>() {}.type)

            // Controlla se non è scaduto (24h)
            val prefs = context.getSharedPreferences("huntix_poi_prefs", Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong("poi_last_fetch", 0)
            val now = System.currentTimeMillis()

            if (now - lastFetch < 24 * 60 * 60 * 1000L) {
                prefs.edit().putLong("poi_last_fetch", now).apply()
                return pois
            } else {
                return null
            }
        } catch (e: Exception) {
            println("❌ Impossibile caricare cache: ${e.message}")
            return null
        }
    }

    /** 🔄 Restituisce POI hardcoded di fallback (risorsa per quando online fallisce) */
    private fun fallbackPois(): List<OnlinePoi> {
        return listOf(
            OnlinePoi("poi_roma_colosseo", "Colosseo", 41.8902, 12.4924, "landmark", "monumento"),
            OnlinePoi("poi_roma_piazza_venezia", "Piazza Venezia", 41.8954, 12.4843, "square", "piazza"),
            OnlinePoi("poi_roma_fontana_di_traiano", "Fontana di Trevi", 41.9010, 12.4830, "fountain", "monumento"),
            OnlinePoi("poi_roma_pantheon", "Pantheon", 41.8980, 12.4769, "building", "monumento"),
            OnlinePoi("poi_roma_villa_borghese", "Villa Borghese", 41.9418, 12.4744, "park", "naturale")
        )
    }
}

/** 🎯 Dati POI in memoria per recupero rapido */
data class OnlinePoi(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val buildingType: String,
    val poiType: String
)

/** 🏛️ Mappatura interna per classe dati JSON */
private data class JsonPoi(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val buildingType: String,
    val poiType: String
)

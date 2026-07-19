package com.intelligame.huntix.managers

import android.content.Context
import android.util.Log
import com.intelligame.huntix.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.Calendar

object WeatherZoneManager {
    private const val PREFS = "weather_zone_prefs_v1"
    private const val OWM_API_KEY = "YOUR_OPENWEATHERMAP_API_KEY"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    var currentWeather: WeatherType = WeatherType.CLEAR
        private set
    var currentZone: ZoneType = ZoneType.UNKNOWN
        private set

    fun refreshAsync(ctx: Context, lat: Double, lng: Double, onDone: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val lastFetch = prefs.getLong("last_fetch", 0L)
                val cachedWeatherId = prefs.getString("weather_id", null)
                val cachedZoneId    = prefs.getString("zone_id", null)

                if (System.currentTimeMillis() - lastFetch < CACHE_TTL_MS &&
                    cachedWeatherId != null && cachedZoneId != null) {
                    currentWeather = WeatherType.fromId(cachedWeatherId)
                    currentZone    = ZoneType.fromId(cachedZoneId)
                    withContext(Dispatchers.Main) { onDone?.invoke() }
                    return@launch
                }

                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val isNight = hour < 6 || hour >= 21

                val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lng&appid=$OWM_API_KEY"
                val json = JSONObject(URL(url).readText())
                val weatherCode = json.getJSONArray("weather").getJSONObject(0).getInt("id")
                val weather = WeatherType.fromOwmCode(weatherCode, isNight)
                val zone = detectZone(lat, lng, json)

                currentWeather = weather
                currentZone = zone

                prefs.edit()
                    .putLong("last_fetch", System.currentTimeMillis())
                    .putString("weather_id", weather.id)
                    .putString("zone_id", zone.id)
                    .apply()

                withContext(Dispatchers.Main) { onDone?.invoke() }
            } catch (e: Exception) {
                Log.w("WeatherZone", "Failed to fetch weather: ${e.message}")
                withContext(Dispatchers.Main) { onDone?.invoke() }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun detectZone(lat: Double, lng: Double, owmJson: JSONObject): ZoneType {
        return try {
            val weatherId = owmJson.getJSONArray("weather").getJSONObject(0).getInt("id")
            when {
                weatherId in 600..622 -> ZoneType.SNOW
                weatherId in 300..531 -> ZoneType.WATER
                else -> {
                    val absLat = Math.abs(lat)
                    when {
                        absLat > 60 -> ZoneType.SNOW
                        absLat > 45 -> ZoneType.MOUNTAIN
                        else -> ZoneType.UNKNOWN
                    }
                }
            }
        } catch (e: Exception) { ZoneType.UNKNOWN }
    }

    fun getCachedWeather(ctx: Context): WeatherType {
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("weather_id", null)
        return if (id != null) WeatherType.fromId(id) else WeatherType.CLEAR
    }

    fun getCachedZone(ctx: Context): ZoneType {
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("zone_id", null)
        return if (id != null) ZoneType.fromId(id) else ZoneType.UNKNOWN
    }

    fun getRaritySpawnWeights(weather: WeatherType, zone: ZoneType): Map<String, Float> {
        val base = mapOf("common" to 60f, "uncommon" to 25f, "rare" to 10f, "epic" to 4f, "legendary" to 1f)
        val result = base.toMutableMap()
        zone.rarityBoost.forEach { (rarityId, mult) ->
            result[rarityId] = (result[rarityId] ?: 1f) * mult
        }
        weather.rarityBoost.forEach { (rarityId, mult) ->
            result[rarityId] = (result[rarityId] ?: 1f) * mult
        }
        return result
    }

    fun pickWeightedRarity(weights: Map<String, Float>): EggRarity {
        val total = weights.values.sum()
        var rand = Math.random().toFloat() * total
        weights.forEach { (rarityId, w) ->
            rand -= w
            if (rand <= 0f) return EggRarity.fromId(rarityId)
        }
        return EggRarity.COMMON
    }
}

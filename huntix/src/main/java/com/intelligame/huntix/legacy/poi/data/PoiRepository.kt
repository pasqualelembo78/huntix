package com.intelligame.huntix.legacy.poi.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringReader
import com.google.gson.stream.JsonReader

class PoiRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(PoiStore::class.java, PoiStoreDeserializer())
        .create()
    private val storesFile: File = File(context.filesDir, "poi/shops.json")

    suspend fun fetchPoiForLocation(
        lat: Double,
        lng: Double,
        maxPois: Int = 300
    ): Result<List<PoiStore>> = withContext(Dispatchers.IO) {
        try {
            val src = if (storesFile.exists()) storesFile.readText() else loadFromAssets()
            val list: List<PoiStore> = gson.fromJson(
                com.google.gson.stream.JsonReader(StringReader(src)),
                object : TypeToken<List<PoiStore>>() {}.type
            )
            Result.success(list.sortedByDistance(lat, lng).take(maxPois))
        } catch (e: Exception) {
            Log.w("PoiRepository", "load fallito: ${e.message}")
            Result.failure(e)
        }
    }

    private fun loadFromAssets(): String =
        context.assets.open("shops.json").use { it.readBytes().decodeToString() }

    private fun List<PoiStore>.sortedByDistance(lat: Double, lng: Double): List<PoiStore> =
        sortedBy { it.distanceTo(lat, lng) }

    private fun PoiStore.distanceTo(lat: Double, lng: Double): Double = haversine(this.lat, this.lng, lat, lng)

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371e3
        val aLat = Math.toRadians(lat1)
        val bLat = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(aLat) * kotlin.math.cos(bLat) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val clamped = if (a > 1.0) 1.0 else a
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(clamped))
    }
}

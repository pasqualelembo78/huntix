package com.intelligame.huntix

import android.graphics.Color

enum class ZoneType(
    val id: String,
    val displayName: String,
    val emoji: String,
    val color: Int,
    val rarityBoost: Map<String, Float>
) {
    CITY("city", "Città", "🏙️", Color.parseColor("#9999CC"),
        mapOf("common" to 1.5f, "uncommon" to 1.2f, "rare" to 0.8f, "epic" to 0.5f)),
    COUNTRYSIDE("countryside", "Campagna", "🌾", Color.parseColor("#8BC34A"),
        mapOf("common" to 1.0f, "uncommon" to 1.3f, "rare" to 1.2f, "epic" to 0.8f)),
    MOUNTAIN("mountain", "Montagna", "⛰️", Color.parseColor("#795548"),
        mapOf("common" to 0.8f, "uncommon" to 1.0f, "rare" to 1.5f, "epic" to 1.2f, "legendary" to 1.3f)),
    WATER("water", "Acqua", "🌊", Color.parseColor("#00B4FF"),
        mapOf("common" to 0.7f, "uncommon" to 1.1f, "rare" to 1.3f, "epic" to 1.5f, "legendary" to 1.2f)),
    SNOW("snow", "Neve", "❄️", Color.parseColor("#B3E5FC"),
        mapOf("common" to 0.6f, "uncommon" to 1.0f, "rare" to 1.2f, "epic" to 1.4f, "legendary" to 1.5f)),
    ROCK("rock", "Roccia", "🪨", Color.parseColor("#9E9E9E"),
        mapOf("common" to 1.0f, "uncommon" to 1.1f, "rare" to 1.3f, "epic" to 1.1f)),
    FOREST("forest", "Foresta", "🌲", Color.parseColor("#00CC6A"),
        mapOf("common" to 1.2f, "uncommon" to 1.4f, "rare" to 1.1f, "epic" to 0.9f)),
    UNKNOWN("unknown", "Zona Sconosciuta", "🗺️", Color.parseColor("#455A64"),
        mapOf("common" to 1.0f, "uncommon" to 1.0f, "rare" to 1.0f, "epic" to 1.0f));

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: UNKNOWN

        @Suppress("UNUSED_PARAMETER")
        fun detectFromLocation(lat: Double, lng: Double, altitudeMeters: Double = 0.0): ZoneType {
            return when {
                altitudeMeters > 1500 -> SNOW
                altitudeMeters > 800  -> MOUNTAIN
                altitudeMeters > 400  -> ROCK
                else -> UNKNOWN
            }
        }
    }
}

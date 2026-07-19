package com.intelligame.huntix

import android.graphics.Color

enum class WeatherType(
    val id: String,
    val displayName: String,
    val emoji: String,
    val color: Int,
    val rarityBoost: Map<String, Float>
) {
    CLEAR("clear", "Sereno", "☀️", Color.parseColor("#E0E0FF"),
        mapOf("common" to 1.2f, "uncommon" to 1.1f)),
    CLOUDY("cloudy", "Nuvoloso", "☁️", Color.parseColor("#9E9E9E"),
        mapOf("uncommon" to 1.2f, "rare" to 1.1f)),
    RAIN("rain", "Pioggia", "🌧️", Color.parseColor("#00B4FF"),
        mapOf("water" to 2.0f, "rare" to 1.4f, "epic" to 1.2f)),
    STORM("storm", "Temporale", "⛈️", Color.parseColor("#5C35CC"),
        mapOf("epic" to 1.8f, "legendary" to 1.5f, "rare" to 1.3f)),
    SNOW("snow", "Neve", "🌨️", Color.parseColor("#B3E5FC"),
        mapOf("snow" to 2.5f, "legendary" to 1.3f, "epic" to 1.2f)),
    FOG("fog", "Nebbia", "🌫️", Color.parseColor("#666699"),
        mapOf("uncommon" to 1.5f, "rare" to 1.3f)),
    WIND("wind", "Vento", "🌬️", Color.parseColor("#80CBC4"),
        mapOf("uncommon" to 1.3f, "rare" to 1.1f)),
    NIGHT("night", "Notte", "🌙", Color.parseColor("#1A237E"),
        mapOf("epic" to 1.4f, "legendary" to 1.2f, "rare" to 1.2f));

    companion object {
        fun fromOwmCode(code: Int, isNight: Boolean = false): WeatherType {
            if (isNight) return NIGHT
            return when (code) {
                in 200..232 -> STORM
                in 300..321 -> RAIN
                in 500..531 -> RAIN
                in 600..622 -> SNOW
                in 701..781 -> FOG
                800 -> CLEAR
                in 801..804 -> CLOUDY
                else -> CLEAR
            }
        }
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: CLEAR
    }
}

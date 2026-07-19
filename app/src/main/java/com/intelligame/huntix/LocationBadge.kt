package com.intelligame.huntix

/**
 * LocationBadge — badge guadagnato visitando un Luogo di Interesse reale.
 * Salvato in SharedPreferences come JSON.
 */
data class LocationBadge(
    val id: String,
    val name: String,
    val emoji: String,
    val lat: Double,
    val lon: Double,
    val earnedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String =
        """{"id":"$id","name":"${name.replace("\"","'")}","emoji":"$emoji","lat":$lat,"lon":$lon,"earnedAt":$earnedAt}"""

    companion object {
        private const val PREFS = "location_badges_prefs"
        private const val KEY   = "badges_json_list"

        fun loadAll(ctx: android.content.Context): MutableList<LocationBadge> {
            val raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "[]") ?: "[]"
            return try {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    LocationBadge(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        emoji = o.getString("emoji"),
                        lat = o.getDouble("lat"),
                        lon = o.getDouble("lon"),
                        earnedAt = o.getLong("earnedAt")
                    )
                }.toMutableList()
            } catch (_: Exception) { mutableListOf() }
        }

        fun isEarned(ctx: android.content.Context, id: String): Boolean =
            loadAll(ctx).any { it.id == id }

        fun earn(ctx: android.content.Context, badge: LocationBadge) {
            val list = loadAll(ctx)
            if (list.any { it.id == badge.id }) return
            list.add(badge)
            val json = "[" + list.joinToString(",") { it.toJson() } + "]"
            ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, json).apply()
        }
    }
}

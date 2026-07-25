package com.intelligame.huntix.gamification

import android.content.Context
import android.content.SharedPreferences

/**
 * TombolataManager — gestisce lo stato della Tombolata di Capodanno (27-31 Dicembre).
 *
 * Pattern: object singleton con SharedPreferences (identico a ElfHuntManager).
 *
 * Gioco: Tombola italiana 5x5, numeri 1-75.
 * Premi: ambo, terno, quaterna, cinquina, tombola.
 */
object TombolataManager {

    private const val PREFS = "tombolata_v1"
    private const val KEY_CLAIMED_DAYS = "claimed_days"
    private const val KEY_TOTAL_MVC = "total_mvc_earned"
    private const val KEY_ITEMS_RECEIVED = "items_received"
    private const val KEY_BEST_SCORES = "best_scores"

    data class TombolataStatus(
        val claimedDays: Set<Int>,
        val totalMvcEarned: Int,
        val todayClaimed: Boolean,
        val canClaimToday: Boolean,
        val totalDaysClaimed: Int,
        val itemsReceived: Set<String>,
        val bestScores: Map<String, Int>
    )

    data class DayReward(
        val day: Int,
        val mvc: Int,
        val item: String?,
        val isMilestone: Boolean,
        val description: String
    )

    /**
     * MVC rewards per day:
     * Giorno 27: 100 (ambo 100, terno 200, quaterna 350, cinquina 500)
     * Giorno 28: 120
     * Giorno 29: 150 + item "Fuoco d'Artificio"
     * Giorno 30: 200
     * Giorno 31: 300 + item "Corona dell'Anno Nuovo" (doppie ricompense)
     */
    fun baseMvcForDay(day: Int): Int = when (day) {
        27 -> 100
        28 -> 120
        29 -> 150
        30 -> 200
        31 -> 300
        else -> 0
    }

    fun itemForDay(day: Int): String? = when (day) {
        29 -> "Fuoco d'Artificio"
        31 -> "Corona dell'Anno Nuovo"
        else -> null
    }

    fun itemEmojiForDay(day: Int): String = when (day) {
        29 -> "\uD83C\uDF86"
        31 -> "\uD83D\uDC51"
        else -> ""
    }

    fun itemDescriptionForDay(day: Int): String? = when (day) {
        29 -> "Fuochi d'artificio che brillano di luce dorata. +10% spawn rate per 24h."
        31 -> "La corona del nuovo anno. +20% tutte le ricompense per 24h."
        else -> null
    }

    /**
     * Premi per combinazioni della tombola:
     * Ambo (2 in fila): 100 MVC
     * Terno (3 in fila): 200 MVC
     * Quaterna (4 in fila): 350 MVC
     * Cinquina (riga completa): 500 MVC
     * Tombola (cartella completa): 1000 MVC
     */
    fun rewardForAmbo(): Int = 100
    fun rewardForTerno(): Int = 200
    fun rewardForQuaterna(): Int = 350
    fun rewardForCinquina(): Int = 500
    fun rewardForTombola(): Int = 1000

    fun getStatus(ctx: Context): TombolataStatus {
        val p = prefs(ctx)
        val claimedStrings = p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()
        val claimedDays = claimedStrings.mapNotNull { it.toIntOrNull() }.toSet()
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0)
        val items = p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()
        val scores = parseBestScores(p)

        val todayDay = getTombolataDay()
        val todayClaimed = todayDay in claimedDays
        val canClaimToday = todayDay > 0 && !todayClaimed &&
            SpecialEventManager.isEventActive("tombolata")

        return TombolataStatus(
            claimedDays = claimedDays,
            totalMvcEarned = totalMvc,
            todayClaimed = todayClaimed,
            canClaimToday = canClaimToday,
            totalDaysClaimed = claimedDays.size,
            itemsReceived = items,
            bestScores = scores
        )
    }

    @Synchronized
    fun claimDay(ctx: Context, day: Int, score: Int = 0): Pair<Boolean, Int> {
        val p = prefs(ctx)

        if (!SpecialEventManager.isEventActive("tombolata")) return Pair(false, 0)
        if (day !in 27..31) return Pair(false, 0)

        val claimed = (p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()).toMutableSet()
        if (claimed.contains(day.toString())) return Pair(false, 0)

        var reward = baseMvcForDay(day)

        val item = itemForDay(day)
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0) + reward
        val items = (p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()).toMutableSet()

        claimed.add(day.toString())
        if (item != null) items.add(item)

        val scores = parseBestScores(p).toMutableMap()
        val scoreKey = "day_$day"
        val currentBest = scores[scoreKey] ?: 0
        if (score > currentBest) {
            scores[scoreKey] = score
        }

        p.edit()
            .putStringSet(KEY_CLAIMED_DAYS, claimed)
            .putInt(KEY_TOTAL_MVC, totalMvc)
            .putStringSet(KEY_ITEMS_RECEIVED, items)
            .putStringSet(KEY_BEST_SCORES, scores.map { "${it.key}:${it.value}" }.toSet())
            .apply()

        return Pair(true, reward)
    }

    fun getRewardsCalendar(): List<DayReward> {
        return (27..31).map { day ->
            val item = itemForDay(day)
            DayReward(
                day = day,
                mvc = baseMvcForDay(day),
                item = item,
                isMilestone = item != null,
                description = when (day) {
                    27 -> "Primo giorno! Bonus ambo/terno/quaterna/cinquina"
                    28 -> "Continua a giocare per vincere di piu'!"
                    29 -> "Oggi ricevi il Fuoco d'Artificio!"
                    30 -> "Penultimo giorno! Preparati per il gran finale"
                    31 -> "Capodanno! Doppie ricompense + Corona dell'Anno Nuovo"
                    else -> ""
                }
            )
        }
    }

    fun getTombolataDay(): Int {
        if (!isEventActive()) return 0
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun isEventActive(): Boolean = SpecialEventManager.isEventActive("tombolata")

    private fun parseBestScores(p: SharedPreferences): Map<String, Int> {
        val scoreStrings = p.getStringSet(KEY_BEST_SCORES, emptySet()) ?: emptySet()
        return scoreStrings.mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size == 2) {
                val key = parts[0]
                val value = parts[1].toIntOrNull()
                if (value != null) Pair(key, value) else null
            } else null
        }.toMap()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

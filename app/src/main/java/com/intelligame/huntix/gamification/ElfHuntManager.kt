package com.intelligame.huntix.gamification

import android.content.Context
import android.content.SharedPreferences

/**
 * ElfHuntManager — gestisce lo stato della Caccia dell'Elfo (Dicembre 1-25).
 *
 * Pattern: object singleton con SharedPreferences (identico a DailyStreakManager).
 *
 * Dati persistiti:
 *  - claimed_days: Set<String> — giorni riscattati ("1", "2", ..., "25")
 *  - total_mvc_earned: Int — totale MVC guadagnati dall'evento
 *  - items_received: Set<String> — nomi degli oggetti ricevuti
 *
 * Ricompense: 25 giorni con ricompense crescenti + item esclusivi.
 */
object ElfHuntManager {

    private const val PREFS = "elf_hunt_v1"
    private const val KEY_CLAIMED_DAYS = "claimed_days"
    private const val KEY_TOTAL_MVC = "total_mvc_earned"
    private const val KEY_ITEMS_RECEIVED = "items_received"

    data class ElfHuntStatus(
        val claimedDays: Set<Int>,
        val totalMvcEarned: Int,
        val todayClaimed: Boolean,
        val canClaimToday: Boolean,
        val totalDaysClaimed: Int,
        val itemsReceived: Set<String>
    )

    data class DayReward(
        val day: Int,
        val mvc: Int,
        val item: String?,
        val isMilestone: Boolean
    )

    /**
     * MVC rewards per day (escalating):
     * Giorno  1:  50       Giorno  9: 130       Giorno 17: 240
     * Giorno  2:  60       Giorno 10: 140       Giorno 18: 260
     * Giorno  3:  70       Giorno 11: 150       Giorno 19: 280
     * Giorno  4:  80       Giorno 12: 160       Giorno 20: 300
     * Giorno  5:  90       Giorno 13: 170       Giorno 21: 320
     * Giorno  6: 100       Giorno 14: 180       Giorno 22: 370
     * Giorno  7: 110       Giorno 15: 200       Giorno 23: 400
     * Giorno  8: 120       Giorno 16: 220       Giorno 24: 430
     *                                                  Giorno 25: 500
     */
    fun rewardMvcForDay(day: Int): Int = when (day) {
        in 1..7 -> 40 + day * 10
        in 8..14 -> 50 + day * 10
        in 15..21 -> -100 + day * 20
        22 -> 370
        23 -> 400
        24 -> 430
        25 -> 500
        else -> 0
    }

    fun itemForDay(day: Int): String? = when (day) {
        7 -> "Cristallo di Neve"
        14 -> "Campana d'Oro"
        21 -> "Stella di Natale"
        24 -> "Corona dell'Elfo"
        25 -> "Babbo Cacciatore"
        else -> null
    }

    fun itemEmojiForDay(day: Int): String = when (day) {
        7 -> "\u2744\uFE0F"
        14 -> "\uD83D\uDD14"
        21 -> "\u2B50"
        24 -> "\uD83D\uDC51"
        25 -> "\uD83C\uDF85"
        else -> ""
    }

    fun itemDescriptionForDay(day: Int): String? = when (day) {
        7 -> "Un cristallo che brilla di luce azzurra. +5% XP per 24h."
        14 -> "Una campana dorata che risuona di fortuna. +10% spawn rate per 24h."
        21 -> "Una stella che illumina la caccia. +15% XP per 24h."
        24 -> "La corona dell'elfo capo. +20% tutte le ricompense per 24h."
        25 -> "La creatura leggendaria del Natale! ATK 85, DEF 70, HP 90."
        else -> null
    }

    fun getBabboCacciatoreStats(): Map<String, Any>? {
        return mapOf(
            "name" to "Babbo Cacciatore",
            "type" to "legendary",
            "element" to "ice",
            "atk" to 85,
            "def" to 70,
            "hp" to 90,
            "special" to "Regali Esplosivi \u2014 infligge danno ad area",
            "rarity" to "legendary"
        )
    }

    fun getStatus(ctx: Context): ElfHuntStatus {
        val p = prefs(ctx)
        val claimedStrings = p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()
        val claimedDays = claimedStrings.mapNotNull { it.toIntOrNull() }.toSet()
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0)
        val items = p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()

        val todayDay = SpecialEventManager.getElfHuntDay()
        val todayClaimed = todayDay in claimedDays
        val canClaimToday = todayDay > 0 && !todayClaimed &&
            SpecialEventManager.isEventActive("elf_hunt")

        return ElfHuntStatus(
            claimedDays = claimedDays,
            totalMvcEarned = totalMvc,
            todayClaimed = todayClaimed,
            canClaimToday = canClaimToday,
            totalDaysClaimed = claimedDays.size,
            itemsReceived = items
        )
    }

    /**
     * Riscatta un giorno specifico dell'evento ElfHunt.
     * @param day il giorno da riscattare (1-25)
     * @return Pair<Boolean, Int>: true + MVC se riuscito, false + 0 se fallito
     */
    @Synchronized
    fun claimDay(ctx: Context, day: Int): Pair<Boolean, Int> {
        val p = prefs(ctx)

        if (!SpecialEventManager.isEventActive("elf_hunt")) return Pair(false, 0)
        if (day <= 0 || day > 25) return Pair(false, 0)

        val claimed = (p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()).toMutableSet()
        if (claimed.contains(day.toString())) return Pair(false, 0)

        val reward = rewardMvcForDay(day)
        val item = itemForDay(day)
        val totalMvc = p.getInt(KEY_TOTAL_MVC, 0) + reward
        val items = (p.getStringSet(KEY_ITEMS_RECEIVED, emptySet()) ?: emptySet()).toMutableSet()

        claimed.add(day.toString())
        if (item != null) items.add(item)

        p.edit()
            .putStringSet(KEY_CLAIMED_DAYS, claimed)
            .putInt(KEY_TOTAL_MVC, totalMvc)
            .putStringSet(KEY_ITEMS_RECEIVED, items)
            .apply()

        return Pair(true, reward)
    }

    fun getRewardsCalendar(): List<DayReward> {
        return (1..25).map { day ->
            DayReward(
                day = day,
                mvc = rewardMvcForDay(day),
                item = itemForDay(day),
                isMilestone = day in listOf(7, 14, 21, 24, 25)
            )
        }
    }

    fun isEventActive(): Boolean = SpecialEventManager.isEventActive("elf_hunt")

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

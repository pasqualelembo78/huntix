package com.intelligame.huntix.gamification

import java.util.Calendar

/**
 * SpecialEventManager — determina quale evento speciale è attivo
 * in base alla data corrente del dispositivo.
 *
 * Eventi attuali:
 *  - ElfHunt (Caccia dell'Elfo): 1-25 Dicembre
 *
 * Pattern: object singleton, nessun SharedPreferences,
 * solo calcolo basato su Calendar.
 *
 * === v9: Rimossa Tombolata di Capodanno (attività d'azzardo) ===
 */
object SpecialEventManager {

    sealed class SpecialEvent {
        abstract val id: String
        abstract val title: String
        abstract val description: String
        abstract val emoji: String
        abstract val startMonth: Int
        abstract val startDay: Int
        abstract val endMonth: Int
        abstract val endDay: Int
        abstract val colorHex: String

        data object ElfHunt : SpecialEvent() {
            override val id = "elf_hunt"
            override val title = "Caccia dell'Elfo"
            override val description = "Trova i regali nascosti per 25 giorni!"
            override val emoji = "\uD83C\uDF84"
            override val startMonth = Calendar.DECEMBER
            override val startDay = 1
            override val endMonth = Calendar.DECEMBER
            override val endDay = 25
            override val colorHex = "#C62828"
        }
    }

    private val allEvents: List<SpecialEvent> = listOf(
        SpecialEvent.ElfHunt
    )

    fun getActiveSpecialEvent(): SpecialEvent? {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        return allEvents.firstOrNull { event ->
            isDateInRange(month, day, event.startMonth, event.startDay, event.endMonth, event.endDay)
        }
    }

    fun hasActiveEvent(): Boolean = getActiveSpecialEvent() != null

    fun isEventActive(eventId: String): Boolean =
        getActiveSpecialEvent()?.id == eventId

    private fun isDateInRange(
        month: Int, day: Int,
        startMonth: Int, startDay: Int,
        endMonth: Int, endDay: Int
    ): Boolean {
        return when {
            startMonth == endMonth -> {
                month == startMonth && day in startDay..endDay
            }
            startMonth > endMonth -> {
                (month == startMonth && day >= startDay) ||
                (month == endMonth && day <= endDay) ||
                (month in (startMonth + 1)..11 || month in 0 until endMonth)
            }
            else -> {
                (month == startMonth && day >= startDay) ||
                (month == endMonth && day <= endDay) ||
                month in (startMonth + 1) until endMonth
            }
        }
    }

    fun daysRemainingInEvent(): Int {
        val event = getActiveSpecialEvent() ?: return 0
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        val endCal = Calendar.getInstance().apply {
            set(Calendar.MONTH, event.endMonth)
            set(Calendar.DAY_OF_MONTH, event.endDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        val diffMs = endCal.timeInMillis - now
        return if (diffMs > 0) (diffMs / (24 * 60 * 60 * 1000)).toInt() + 1 else 0
    }

    fun getElfHuntDay(): Int {
        if (!isEventActive("elf_hunt")) return 0
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_MONTH)
    }
}

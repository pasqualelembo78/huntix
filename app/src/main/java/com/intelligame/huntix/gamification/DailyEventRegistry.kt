package com.intelligame.huntix.gamification

import com.intelligame.huntix.minigames.card.BriscolaActivity
import java.util.Calendar

/**
 * DailyEventRegistry — maps days-of-week to daily events.
 *
 * Adding a new event is trivial:
 * 1. Create the Activity
 * 2. Add a DailyEvent(...) below with the correct dayOfWeek
 *
 * The system uses this registry to:
 * - Determine what event is happening today
 * - Schedule notifications
 * - Decide whether to show the daily event or a monthly event
 *
 * === v9: Rimossa Tombola Rapida (attività d'azzardo) ===
 */
object DailyEventRegistry {

    private val events: List<DailyEvent> = listOf(
        DailyEvent(
            id = "briscola",
            title = "Briscola al Buio",
            description = "Sfida la CPU a briscola!",
            emoji = "\uD83C\uDCDC",
            colorHex = "#4CAF50",
            dayOfWeek = Calendar.TUESDAY,
            activityClass = BriscolaActivity::class.java
        ),
        // ── Placeholder per i giorni rimanenti ──
        // Aggiungi qui nuovi eventi seguendo lo stesso pattern
        /*
        DailyEvent(
            id = "memory",
            title = "Sfida Memoria",
            description = "Trova le coppie!",
            emoji = "\uD83E\uDDE0",
            colorHex = "#9C27B0",
            dayOfWeek = Calendar.WEDNESDAY,
            activityClass = MemoryActivity::class.java
        ),
        */
    )

    fun getEventForDayOfWeek(dayOfWeek: Int): DailyEvent? {
        return events.firstOrNull { it.dayOfWeek == dayOfWeek }
    }

    fun getTodayEvent(): DailyEvent? {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return getEventForDayOfWeek(today)
    }

    fun getAllEvents(): List<DailyEvent> = events.toList()

    fun hasEventForDay(dayOfWeek: Int): Boolean =
        getEventForDayOfWeek(dayOfWeek) != null
}

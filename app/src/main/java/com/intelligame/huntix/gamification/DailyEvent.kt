package com.intelligame.huntix.gamification

import android.content.Context
import android.graphics.Color

/**
 * DailyEvent — define a daily event type.
 *
 * Each event maps to a fixed day-of-week and has:
 * - a title, description, emoji
 * - a color for UI theming
 * - a start/end hour (default 17:00–18:00)
 * - an activityClass to launch when the event is active
 *
 * To add a new daily event:
 * 1. Create a new Activity implementing the gameplay
 * 2. Add a new DailyEvent(...) in DailyEventRegistry.kt
 * 3. Done — the system picks it up automatically
 */
data class DailyEvent(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val colorHex: String,
    val dayOfWeek: Int,          // java.util.Calendar.MONDAY … SUNDAY
    val startHour: Int = 17,
    val startMinute: Int = 0,
    val durationMinutes: Int = 60,
    val activityClass: Class<*>?
) {
    val endHour: Int get() {
        val totalMinutes = startHour * 60 + startMinute + durationMinutes
        return totalMinutes / 60
    }
    val endMinute: Int get() {
        val totalMinutes = startHour * 60 + startMinute + durationMinutes
        return totalMinutes % 60
    }
    val colorInt: Int get() = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#A78BFA") }
}

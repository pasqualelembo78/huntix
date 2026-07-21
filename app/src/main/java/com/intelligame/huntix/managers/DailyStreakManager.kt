package com.intelligame.huntix.managers

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object DailyStreakManager {

    private const val PREFS = "daily_streak_v1"
    private const val KEY_CURRENT_STREAK = "current_streak"
    private const val KEY_LONGEST_STREAK = "longest_streak"
    private const val KEY_LAST_CLAIM_DAY = "last_claim_day"
    private const val KEY_CLAIMED_DAYS = "claimed_days"
    private const val KEY_STREAK_SHIELDS = "streak_shields"
    private const val KEY_TOTAL_CLAIMS = "total_claims"

    fun rewardForDay(day: Int): Int = when {
        day <= 7 -> 10 + (day - 1) * 5          // 10, 15, 20, 25, 30, 35, 40
        day <= 14 -> 50 + (day - 8) * 10         // 50, 60, 70, 80, 90, 100, 110, 120
        day <= 21 -> 150 + (day - 15) * 20       // 150, 170, 190, 210, 230, 250, 270
        day <= 29 -> 300 + (day - 22) * 30       // 300, 330, 360, 390, 420, 450, 480, 510
        else -> 1000                               // Day 30 mega bonus
    }

    fun dayLabel(day: Int): String = when (day) {
        1 -> "Giorno 1"
        7 -> "Giorno 7 🔥"
        14 -> "Giorno 14 ⭐"
        21 -> "Giorno 21 💎"
        30 -> "GIORNO 30 🏆"
        else -> "Giorno $day"
    }

    fun isMilestone(day: Int): Boolean = day in listOf(7, 14, 21, 30)

    data class StreakStatus(
        val currentStreak: Int,
        val longestStreak: Int,
        val todayClaimed: Boolean,
        val canClaimToday: Boolean,
        val shieldsAvailable: Int,
        val totalClaims: Int,
        val claimedDays: Set<Int>
    )

    fun getStatus(ctx: Context): StreakStatus {
        val p = prefs(ctx)
        val current = p.getInt(KEY_CURRENT_STREAK, 0)
        val longest = p.getInt(KEY_LONGEST_STREAK, 0)
        val lastClaim = p.getString(KEY_LAST_CLAIM_DAY, "") ?: ""
        val shields = p.getInt(KEY_STREAK_SHIELDS, 0)
        val total = p.getInt(KEY_TOTAL_CLAIMS, 0)
        val claimed = p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()

        val today = todayString()
        val yesterday = yesterdayString()
        val todayClaimed = lastClaim == today
        val canClaim = lastClaim != today

        return StreakStatus(current, longest, todayClaimed, canClaim, shields, total, claimed.map { it.toIntOrNull() ?: 0 }.toSet())
    }

    @Synchronized
    fun claimToday(ctx: Context): Pair<Boolean, Int> {
        val p = prefs(ctx)
        val today = todayString()
        val lastClaim = p.getString(KEY_LAST_CLAIM_DAY, "") ?: ""
        if (lastClaim == today) return Pair(false, 0)

        val yesterday = yesterdayString()
        var streak = if (lastClaim == yesterday || lastClaim.isEmpty()) {
            p.getInt(KEY_CURRENT_STREAK, 0) + 1
        } else {
            // Streak broken - check for shield
            val shields = p.getInt(KEY_STREAK_SHIELDS, 0)
            if (shields > 0) {
                p.edit().putInt(KEY_STREAK_SHIELDS, shields - 1).apply()
                p.getInt(KEY_CURRENT_STREAK, 0) + 1
            } else {
                1
            }
        }

        val reward = rewardForDay(streak)
        val longest = maxOf(streak, p.getInt(KEY_LONGEST_STREAK, 0))
        val claimed = (p.getStringSet(KEY_CLAIMED_DAYS, emptySet()) ?: emptySet()).toMutableSet()
        claimed.add(streak.toString())

        p.edit()
            .putInt(KEY_CURRENT_STREAK, streak)
            .putInt(KEY_LONGEST_STREAK, longest)
            .putString(KEY_LAST_CLAIM_DAY, today)
            .putStringSet(KEY_CLAIMED_DAYS, claimed)
            .putInt(KEY_TOTAL_CLAIMS, p.getInt(KEY_TOTAL_CLAIMS, 0) + 1)
            .apply()

        return Pair(true, reward)
    }

    fun useShield(ctx: Context): Boolean {
        val p = prefs(ctx)
        val shields = p.getInt(KEY_STREAK_SHIELDS, 0)
        if (shields <= 0) return false
        p.edit().putInt(KEY_STREAK_SHIELDS, shields - 1).apply()
        return true
    }

    fun addShield(ctx: Context, count: Int = 1) {
        val p = prefs(ctx)
        p.edit().putInt(KEY_STREAK_SHIELDS, p.getInt(KEY_STREAK_SHIELDS, 0) + count).apply()
    }

    fun getShields(ctx: Context): Int = prefs(ctx).getInt(KEY_STREAK_SHIELDS, 0)

    fun resetIfBroken(ctx: Context) {
        val p = prefs(ctx)
        val lastClaim = p.getString(KEY_LAST_CLAIM_DAY, "") ?: ""
        val today = todayString()
        val yesterday = yesterdayString()
        if (lastClaim != today && lastClaim != yesterday && lastClaim.isNotEmpty()) {
            val shields = p.getInt(KEY_STREAK_SHIELDS, 0)
            if (shields <= 0) {
                p.edit().putInt(KEY_CURRENT_STREAK, 0).apply()
            }
        }
    }

    private fun todayString(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date())
    }

    private fun yesterdayString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(cal.time)
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

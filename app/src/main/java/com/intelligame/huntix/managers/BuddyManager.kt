package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.*

object BuddyManager {
    private const val PREFS     = "buddy_manager_v1"
    private const val KEY_KM    = "buddy_km"
    private const val CANDY_PER_KM = 1.0f

    fun addWalkingDistance(ctx: Context, km: Float) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val buddyId = SurpriseManager.getAll(ctx).firstOrNull { it.isBuddy }?.id ?: return
        val prev = prefs.getFloat(KEY_KM, 0f)
        val newKm = prev + km
        prefs.edit().putFloat(KEY_KM, newKm).apply()
        val candiesEarned = (newKm / CANDY_PER_KM).toInt() - (prev / CANDY_PER_KM).toInt()
        if (candiesEarned > 0) {
            SurpriseManager.addCandies(ctx, buddyId, candiesEarned)
        }
    }

    fun getBuddyKm(ctx: Context): Float =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_KM, 0f)

    fun buddyDisplayText(ctx: Context): String {
        val km = getBuddyKm(ctx)
        val buddy = SurpriseManager.getAll(ctx).firstOrNull { it.isBuddy } ?: return "Nessun buddy"
        val candies = buddy.candies
        return "%.1f km camminati · $candies Caramelle".format(km)
    }
}

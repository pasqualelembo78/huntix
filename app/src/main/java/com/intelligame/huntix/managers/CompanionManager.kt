// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.managers

import android.content.Context
import com.intelligame.huntix.*

object CompanionManager {
    private const val PREFS     = "fidato_manager_v1"
    private const val KEY_KM    = "fidato_km"
    private const val LECCORNIE_PER_KM = 1.0f

    fun addWalkingDistance(ctx: Context, km: Float) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fidatoId = SurpriseManager.getAll(ctx).firstOrNull { it.isFidato }?.id ?: return
        val prev = prefs.getFloat(KEY_KM, 0f)
        val newKm = prev + km
        prefs.edit().putFloat(KEY_KM, newKm).apply()
        val leccornieGained = (newKm / LECCORNIE_PER_KM).toInt() - (prev / LECCORNIE_PER_KM).toInt()
        if (leccornieGained > 0) {
            SurpriseManager.addLeccornie(ctx, fidatoId, leccornieGained)
        }
    }

    fun getFidatoKm(ctx: Context): Float =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_KM, 0f)

    fun fidatoDisplayText(ctx: Context): String {
        val km = getFidatoKm(ctx)
        val fidato = SurpriseManager.getAll(ctx).firstOrNull { it.isFidato } ?: return "Nessun compagno fidato"
        val leccornie = fidato.leccornie
        return "%.1f km camminati · $leccornie Leccornie".format(km)
    }
}
package com.intelligame.huntix.ui

import android.content.Context

object CapturePreferences {

    private const val PREFS_NAME = "capture_prefs"
    private const val KEY_METHOD = "preferred_method"

    fun getPreferredMethod(ctx: Context): CaptureMethod {
        val id = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_METHOD, null) ?: return CaptureMethod.SWIPE_LEGACY
        return CaptureMethod.fromId(id)
    }

    fun setPreferredMethod(ctx: Context, method: CaptureMethod) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_METHOD, method.id)
            .apply()
    }
}

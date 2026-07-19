package com.intelligame.huntix

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

/**
 * CharacterSpriteManager — fallback per i marker del player sulla mappa
 * quando non è disponibile un avatar RPM.
 */
object CharacterSpriteManager {

    fun makeCharacterDrawable(
        resources: Resources,
        sizeDp: Int,
        walkTick: Int,
        level: Int,
        facing: Float
    ): BitmapDrawable {
        val dp = resources.displayMetrics.density
        val size = (sizeDp * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fillColor = when {
            level >= 40 -> Color.parseColor("#FFD700")
            level >= 20 -> Color.parseColor("#FF6B35")
            else -> Color.parseColor("#00B4FF")
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
        return BitmapDrawable(resources, bmp)
    }
}

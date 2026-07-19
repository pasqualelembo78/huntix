package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * OutdoorRadarView — minimappa radar che mostra il giocatore al centro
 * e gli uova/POI attorno usando bearing (0 = Nord) e distanza.
 * Funziona interamente offline (nessun tile di mappa necessario).
 */
class OutdoorRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Blip(
        val bearingDeg: Float,
        val distanceM: Float,
        val color: Int,
        val label: String = ""
    )

    var blips: List<Blip> = emptyList()
    var maxRangeM: Float = 300f
    var headingDeg: Float = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3355AAFF")
        strokeWidth = 2f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
    }
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - 12f

        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.33f, ringPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, ringPaint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, ringPaint)

        canvas.drawCircle(cx, cy, 8f, centerPaint)

        val rot = Math.toRadians(headingDeg.toDouble())
        for (b in blips) {
            val d = (b.distanceM / maxRangeM).coerceIn(0f, 1f) * (radius - 10f)
            val ang = Math.toRadians(b.bearingDeg.toDouble()) - rot
            val x = cx + d * kotlin.math.sin(ang).toFloat()
            val y = cy - d * kotlin.math.cos(ang).toFloat()
            blipPaint.color = b.color
            canvas.drawCircle(x, y, 10f, blipPaint)
            if (b.label.isNotEmpty()) {
                canvas.drawText(b.label, x, y - 14f, textPaint)
            }
        }
    }
}

package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.intelligame.huntix.reallife.MapNode
import com.intelligame.huntix.reallife.MapState

/**
 * RealLifeMapView — mappa 2D minima della "città" Real Life.
 * Disegna gli NPC come punti (emoji) su una griglia 100x100 e, al tocco,
 * richiama [onNodeTap] col nodo più vicino.
 */
class RealLifeMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var map: MapState? = null
    private val zoneColors = arrayOf("#2A1B4A", "#10302A", "#3A1F12", "#2A1030", "#102A3A", "#3A2A10")
    var onNodeTap: ((MapNode) -> Unit)? = null

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#241640"); strokeWidth = 1f
    }
    private val dotPaint = Paint().apply { isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 22f
        typeface = Typeface.DEFAULT
    }
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#B9A8E0"); textAlign = Paint.Align.CENTER; textSize = 10f
    }

    fun setMap(state: MapState) {
        map = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(Color.parseColor("#0E0820"))
        // griglia di zone
        val cols = 3; val rows = 3
        for (i in 0..cols) {
            val x = w * i / cols
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }
        for (j in 0..rows) {
            val y = h * j / rows
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        val m = map ?: return
        for (node in m.nodes) {
            val px = (node.x.toFloat() / m.width) * w
            val py = (node.y.toFloat() / m.height) * h
            val colorIdx = (node.zone.hashCode().let { if (it < 0) -it else it }) % zoneColors.size
            dotPaint.color = Color.parseColor(zoneColors[colorIdx])
            canvas.drawCircle(px, py, 18f, dotPaint)
            canvas.drawCircle(px, py, 18f, gridPaint.apply { color = Color.parseColor("#5A3FA0") })
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(node.avatar.takeIf { it.length <= 2 } ?: "🙂", px, py + 8f, textPaint)
            val bounds = Rect()
            labelPaint.getTextBounds(node.name, 0, node.name.length, bounds)
            canvas.drawText(node.name, px, py + 30f, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val m = map ?: return false
            val w = width.toFloat(); val h = height.toFloat()
            val px = event.x; val py = event.y
            var best: MapNode? = null; var bestD = Float.MAX_VALUE
            for (node in m.nodes) {
                val nx = (node.x.toFloat() / m.width) * w
                val ny = (node.y.toFloat() / m.height) * h
                val d = (nx - px) * (nx - px) + (ny - py) * (ny - py)
                if (d < bestD) { bestD = d; best = node }
            }
            if (best != null && bestD < 40f * 40f) {
                onNodeTap?.invoke(best)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

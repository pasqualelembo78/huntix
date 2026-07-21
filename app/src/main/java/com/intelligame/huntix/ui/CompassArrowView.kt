package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * CompassArrowView — freccia 2D overlay che punta verso una direzione in gradi
 * rispetto al nord magnetico. Funziona senza ARCore, solo con GPS + bussola.
 */
class CompassArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var headingDeg: Float = 0f
    var targetBearingDeg: Float = 0f

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFDD835.toInt()
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000.toInt()
        style = Paint.Style.FILL
    }
    private val arrowPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = (minOf(w, h) / 2f) - 16f

        canvas.drawCircle(cx, cy, r, circlePaint)

        var relative = targetBearingDeg - headingDeg
        if (relative > 180f) relative -= 360f
        else if (relative < -180f) relative += 360f
        val angleRad = Math.toRadians(relative.toDouble())

        val tipX = cx + (r * 0.85f * Math.sin(angleRad)).toFloat()
        val tipY = cy - (r * 0.85f * Math.cos(angleRad)).toFloat()

        val baseAngle1 = angleRad + Math.PI * 0.8
        val baseAngle2 = angleRad - Math.PI * 0.8
        val baseR = r * 0.4f

        val base1X = cx + (baseR * Math.sin(baseAngle1)).toFloat()
        val base1Y = cy - (baseR * Math.cos(baseAngle1)).toFloat()
        val base2X = cx + (baseR * Math.sin(baseAngle2)).toFloat()
        val base2Y = cy - (baseR * Math.cos(baseAngle2)).toFloat()

        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(base1X, base1Y)
        arrowPath.lineTo(base2X, base2Y)
        arrowPath.close()

        canvas.drawPath(arrowPath, arrowPaint)
        canvas.drawPath(arrowPath, outlinePaint)
    }
}

package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class JoystickView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val bgRadius = 55f * density
    private val thumbRadius = 20f * density

    var dx = 0f; private set
    var dy = 0f; private set

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x50FFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val bgStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCBBBBBB.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, bgRadius, bgPaint)
        canvas.drawCircle(cx, cy, bgRadius, bgStroke)
        canvas.drawCircle(cx + dx * bgRadius, cy + dy * bgRadius, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cx = width / 2f
                val cy = height / 2f
                val rx = event.x - cx
                val ry = event.y - cy
                val dist = sqrt(rx * rx + ry * ry)
                val maxR = bgRadius
                dx = if (dist > maxR) rx / dist else rx / maxR
                dy = if (dist > maxR) ry / dist else ry / maxR
                if (dx * dx + dy * dy < 0.04f) { dx = 0f; dy = 0f }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dx = 0f; dy = 0f
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

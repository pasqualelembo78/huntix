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

    private var targetDx = 0f
    private var targetDy = 0f
    private var activePointerId = -1

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
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                computeInput(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == -1) {
                    val idx = event.actionIndex
                    activePointerId = event.getPointerId(idx)
                    computeInput(event.getX(idx), event.getY(idx))
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) computeInput(event.getX(idx), event.getY(idx))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                targetDx = 0f; targetDy = 0f
                dx = 0f; dy = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = -1
                    targetDx = 0f; targetDy = 0f
                    dx = 0f; dy = 0f
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun computeInput(x: Float, y: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val rx = x - cx
        val ry = y - cy
        val dist = sqrt(rx * rx + ry * ry)
        val maxR = bgRadius
        targetDx = if (dist > maxR) rx / dist else rx / maxR
        targetDy = if (dist > maxR) ry / dist else ry / maxR
        if (targetDx * targetDx + targetDy * targetDy < 0.04f) { targetDx = 0f; targetDy = 0f }
        dx += (targetDx - dx) * 0.3f
        dy += (targetDy - dy) * 0.3f
        if (dx * dx + dy * dy < 0.001f) { dx = 0f; dy = 0f }
        invalidate()
    }
}

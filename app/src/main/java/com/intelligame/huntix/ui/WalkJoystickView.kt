package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.intelligame.huntix.UiKit
import kotlin.math.hypot
import kotlin.math.min

/**
 * WalkJoystickView — levetta analogica per la mappa outdoor.
 *
 * Permette di camminare "a vuoto" (mock walk) senza muoversi davvero:
 * spinge OutdoorManager.setWalkVector(x, y) in continuazione, con
 * x = est(+)/ovest(-) e y = nord(+)/sud(-).
 */
class WalkJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onWalkVector: ((Float, Float) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40A78BFA.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(UiKit.ACCENT)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(UiKit.ACCENT) }

    private var active = false
    private var activePointerId = -1
    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var knobX = 0f
    private var knobY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        r = min(w, h) * 0.45f
        cx = w / 2f
        cy = h / 2f
        knobX = cx
        knobY = cy
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                activePointerId = event.getPointerId(0)
                update(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!active) {
                    val idx = event.actionIndex
                    active = true
                    activePointerId = event.getPointerId(idx)
                    update(event.getX(idx), event.getY(idx))
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (active) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) update(event.getX(idx), event.getY(idx))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                activePointerId = -1
                knobX = cx
                knobY = cy
                onWalkVector?.invoke(0f, 0f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePointerId) {
                    active = false
                    activePointerId = -1
                    knobX = cx
                    knobY = cy
                    onWalkVector?.invoke(0f, 0f)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun update(x: Float, y: Float) {
        var dx = x - cx
        var dy = y - cy
        val len = hypot(dx, dy)
        if (len > r) {
            dx = dx / len * r
            dy = dy / len * r
        }
        knobX = cx + dx
        knobY = cy + dy
        // dx positivo = est (destra schermo), dy positivo = sud (giù schermo)
        // verso OutdoorManager: x est+, y nord+  →  y = -dy
        onWalkVector?.invoke(
            if (len < r * 0.15f) 0f else dx / r,
            if (len < r * 0.15f) 0f else -dy / r
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy, r, basePaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(knobX, knobY, r * 0.4f, knobPaint)
    }
}

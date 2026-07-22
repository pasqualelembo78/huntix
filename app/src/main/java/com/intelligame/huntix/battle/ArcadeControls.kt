package com.intelligame.huntix.battle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ArcadeControls(context: Context) : View(context) {

    interface Listener {
        fun onJoystickDirection(dir: Int)
        fun onJoystickRelease()
        fun onButtonPress(buttonId: Int)
        fun onButtonRelease(buttonId: Int)
    }

    var listener: Listener? = null

    companion object {
        const val BTN_PUNCH = 0
        const val BTN_KICK = 1
        const val BTN_SPECIAL = 2
        const val BTN_BLOCK = 3
        const val BTN_HEAVY = 4
        const val BTN_SUPER = 5
    }

    private data class ButtonDef(
        val id: Int, val label: String, val color: Int,
        var cx: Float = 0f, var cy: Float = 0f, var pressed: Boolean = false
    )

    private val buttons = listOf(
        ButtonDef(BTN_PUNCH, "P", Color.parseColor("#2979FF")),
        ButtonDef(BTN_KICK, "K", Color.parseColor("#FF1744")),
        ButtonDef(BTN_SPECIAL, "S", Color.parseColor("#AA00FF")),
        ButtonDef(BTN_BLOCK, "B", Color.parseColor("#00BCD4")),
        ButtonDef(BTN_HEAVY, "H", Color.parseColor("#FF9100")),
        ButtonDef(BTN_SUPER, "SP", Color.parseColor("#FFD600"))
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val joystickBaseRadius = 50f
    private val joystickThumbRadius = 20f
    private var joystickCx = 0f
    private var joystickCy = 0f
    private var thumbX = 0f
    private var thumbY = 0f
    private var joystickPressed = false
    private var joystickTouchId = -1

    private var buttonRadius = 24f
    private var buttonTouchId = -1
    private var activeButton: ButtonDef? = null

    private var currentDir = 0
    private var density = resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = density
        joystickCx = 70f * d
        joystickCy = h - 80f * d
        thumbX = joystickCx
        thumbY = joystickCy

        val btnStartX = w - 28f * d - 3 * (buttonRadius * 2 + 10f * d)
        val btnStartY = h - 120f * d
        val spacing = buttonRadius * 2 + 10f * d

        buttons.forEachIndexed { i, btn ->
            val row = i / 3
            val col = i % 3
            btn.cx = btnStartX + col * spacing + buttonRadius
            btn.cy = btnStartY + row * spacing + buttonRadius
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIdx = event.actionIndex
        val pointerId = event.getPointerId(pointerIdx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIdx)
                val y = event.getY(pointerIdx)

                val dxJoy = x - joystickCx
                val dyJoy = y - joystickCy
                if (sqrt(dxJoy * dxJoy + dyJoy * dyJoy) < joystickBaseRadius * 2f) {
                    joystickPressed = true
                    joystickTouchId = pointerId
                    updateJoystickThumb(x, y)
                    return true
                }

                for (btn in buttons) {
                    val dx = x - btn.cx
                    val dy = y - btn.cy
                    if (sqrt(dx * dx + dy * dy) < buttonRadius * 1.5f) {
                        btn.pressed = true
                        activeButton = btn
                        buttonTouchId = pointerId
                        listener?.onButtonPress(btn.id)
                        invalidate()
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (pi in 0 until event.pointerCount) {
                    val pid = event.getPointerId(pi)
                    val x = event.getX(pi)
                    val y = event.getY(pi)

                    if (pid == joystickTouchId && joystickPressed) {
                        updateJoystickThumb(x, y)
                        return true
                    }
                    if (pid == buttonTouchId) {
                        val btn = activeButton ?: continue
                        val dx = x - btn.cx
                        val dy = y - btn.cy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > buttonRadius * 2f) {
                            btn.pressed = false
                            listener?.onButtonRelease(btn.id)
                            activeButton = null
                            buttonTouchId = -1
                            invalidate()
                        }
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == joystickTouchId) {
                    joystickPressed = false
                    joystickTouchId = -1
                    thumbX = joystickCx
                    thumbY = joystickCy
                    currentDir = 0
                    listener?.onJoystickRelease()
                    invalidate()
                    return true
                }
                if (pointerId == buttonTouchId) {
                    activeButton?.let {
                        it.pressed = false
                        listener?.onButtonRelease(it.id)
                    }
                    activeButton = null
                    buttonTouchId = -1
                    invalidate()
                    return true
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                joystickPressed = false
                joystickTouchId = -1
                thumbX = joystickCx
                thumbY = joystickCy
                currentDir = 0
                listener?.onJoystickRelease()
                activeButton?.let {
                    it.pressed = false
                    listener?.onButtonRelease(it.id)
                }
                activeButton = null
                buttonTouchId = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateJoystickThumb(x: Float, y: Float) {
        var dx = x - joystickCx
        var dy = y - joystickCy
        val dist = sqrt(dx * dx + dy * dy)
        val maxDist = joystickBaseRadius - joystickThumbRadius / 2f

        if (dist > maxDist) {
            dx = dx / dist * maxDist
            dy = dy / dist * maxDist
        }
        thumbX = joystickCx + dx
        thumbY = joystickCy + dy

        if (dist > 15f) {
            val angle = atan2(dy, dx)
            val deg = Math.toDegrees(angle.toDouble()).toFloat()
            val newDir = when {
                deg in -22.5f..22.5f -> 6
                deg in 22.5f..67.5f -> 3
                deg in 67.5f..112.5f -> 2
                deg in 112.5f..157.5f -> 1
                deg > 157.5f || deg < -157.5f -> 4
                deg in -157.5f..-112.5f -> 7
                deg in -112.5f..-67.5f -> 8
                deg in -67.5f..-22.5f -> 5
                else -> 0
            }
            if (newDir != currentDir) {
                currentDir = newDir
                listener?.onJoystickDirection(currentDir)
            }
        } else {
            if (currentDir != 0) {
                currentDir = 0
                listener?.onJoystickRelease()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawJoystick(canvas)
        drawButtons(canvas)
    }

    private fun drawJoystick(c: Canvas) {
        val d = density
        paint.color = Color.parseColor("#1A0A2A")
        paint.alpha = 180
        paint.style = Paint.Style.FILL
        c.drawCircle(joystickCx, joystickCy, joystickBaseRadius * d / density, paint)

        paint.color = Color.parseColor("#3A2860")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        c.drawCircle(joystickCx, joystickCy, joystickBaseRadius, paint)

        val dirColor = if (currentDir != 0) Color.parseColor("#AA66FF") else Color.parseColor("#5A4080")
        paint.color = dirColor
        paint.style = Paint.Style.FILL
        paint.alpha = if (currentDir != 0) 100 else 40
        c.drawCircle(joystickCx, joystickCy, joystickBaseRadius - 4f, paint)
        paint.alpha = 255

        paint.color = if (currentDir != 0) Color.parseColor("#CC88FF") else Color.parseColor("#6A50A0")
        paint.style = Paint.Style.FILL
        c.drawCircle(thumbX, thumbY, joystickThumbRadius, paint)

        paint.color = Color.WHITE
        paint.alpha = 80
        paint.style = Paint.Style.FILL
        c.drawCircle(thumbX - 3f, thumbY - 3f, joystickThumbRadius * 0.35f, paint)
        paint.alpha = 255

        drawDirArrows(c, joystickCx, joystickCy, joystickBaseRadius * 0.7f, d)
    }

    private fun drawDirArrows(c: Canvas, cx: Float, cy: Float, r: Float, d: Float) {
        paint.color = Color.parseColor("#6050A0")
        paint.textSize = 10f * d
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        val arrows = listOf(
            Triple("▲", cx, cy - r),
            Triple("▼", cx, cy + r + 10f * d),
            Triple("◄", cx - r, cy + 4f * d),
            Triple("►", cx + r, cy + 4f * d)
        )
        arrows.forEach { (text, ax, ay) ->
            c.drawText(text, ax, ay, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawButtons(c: Canvas) {
        for (btn in buttons) {
            val r = buttonRadius

            paint.color = Color.parseColor("#0D0820")
            paint.alpha = 200
            paint.style = Paint.Style.FILL
            c.drawCircle(btn.cx, btn.cy, r + 3f, paint)
            paint.alpha = 255

            val bgColor = if (btn.pressed) {
                brighten(btn.color, 1.5f)
            } else {
                btn.color
            }
            paint.color = bgColor
            paint.alpha = if (btn.pressed) 255 else 180
            paint.style = Paint.Style.FILL
            c.drawCircle(btn.cx, btn.cy, r, paint)
            paint.alpha = 255

            paint.color = Color.parseColor("#50FFFFFF")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            c.drawCircle(btn.cx, btn.cy, r, paint)

            if (btn.pressed) {
                paint.color = Color.WHITE
                paint.alpha = 40
                c.drawCircle(btn.cx, btn.cy, r * 0.6f, paint)
                paint.alpha = 255
            }

            paint.color = Color.WHITE
            paint.textSize = if (btn.label.length > 1) 11f else 15f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            c.drawText(btn.label, btn.cx, btn.cy + 5f, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun brighten(color: Int, factor: Float): Int {
        val r = ((Color.red(color) * factor).toInt()).coerceIn(0, 255)
        val g = ((Color.green(color) * factor).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(color) * factor).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}

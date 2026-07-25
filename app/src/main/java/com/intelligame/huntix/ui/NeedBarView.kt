package com.intelligame.huntix.ui

import android.animation.ValueAnimator
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.sin

/**
 * NeedBarView — Barra bisogno stile Brookhaven.
 *
 * Disegna: icona cerchio con glow + barra gradiente animata + etichetta + percentuale.
 * Pulse animation quando il valore è basso (< 30%).
 */
class NeedBarView(context: android.content.Context) : View(context) {

    private var label = ""
    private var icon = ""
    private var targetValue = 0f
    private var displayValue = 0f
    private var baseColor = Color.WHITE
    private var lightColor = Color.WHITE
    private var darkColor = Color.WHITE
    private var pulsePhase = 0f

    // Paints
    private val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    // Dimensions
    private val iconSize = 40f
    private val barHeight = 18f
    private val barRadius = 9f
    private val cornerRadius = 16f
    private val cardPadding = 14f

    // Animator
    private var valueAnimator: ValueAnimator? = null

    fun setData(icon: String, label: String, value: Float, colorHex: String) {
        this.icon = icon
        this.label = label
        this.targetValue = value.coerceIn(0f, 100f)
        this.baseColor = Color.parseColor(colorHex)
        this.lightColor = lighten(baseColor, 0.4f)
        this.darkColor = darken(baseColor, 0.3f)

        // Animate to new value
        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofFloat(displayValue, targetValue).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                displayValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Icon paints
        iconBgPaint.apply {
            color = baseColor
            style = Paint.Style.FILL
        }
        iconGlowPaint.apply {
            color = baseColor
            alpha = 40
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }
        iconTextPaint.apply {
            textSize = 22f
            color = Color.WHITE
        }

        // Track paint
        trackPaint.apply {
            color = darken(baseColor, 0.85f)
            style = Paint.Style.FILL
        }

        // Fill paint
        fillPaint.apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 1f, 0f,
                intArrayOf(lightColor, baseColor, darkColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        // Label paint
        labelPaint.apply {
            textSize = 13f
            color = Color.parseColor("#C0B0D8")
        }

        // Percent paint
        percentPaint.apply {
            textSize = 12f
            color = baseColor
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = resources.displayMetrics.density

        val pad = cardPadding * s
        val iconS = iconSize * s
        val barH = barHeight * s
        val barR = barRadius * s
        val cornerR = cornerRadius * s

        // Background card
        val cardRect = RectF(0f, 0f, w, h)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14102A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, cardPaint)

        // Subtle border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darken(baseColor, 0.7f)
            alpha = 80
            style = Paint.Style.STROKE
            strokeWidth = 1f * s
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        // Icon glow (pulsing when low)
        val isLow = targetValue < 30f
        val glowAlpha = if (isLow) {
            pulsePhase += 0.08f
            (60 + 40 * sin(pulsePhase).toFloat()).toInt().coerceIn(0, 255)
        } else {
            30
        }
        iconGlowPaint.alpha = glowAlpha
        val iconCx = pad + iconS / 2f
        val iconCy = h / 2f
        canvas.drawCircle(iconCx, iconCy, iconS * 0.7f, iconGlowPaint)

        // Icon circle background
        canvas.drawCircle(iconCx, iconCy, iconS / 2f, iconBgPaint.apply { alpha = 230 })

        // Icon border ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 50
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * s
        }
        canvas.drawCircle(iconCx, iconCy, iconS / 2f, ringPaint)

        // Icon emoji
        iconTextPaint.textSize = 22f * s
        canvas.drawText(icon, iconCx, iconCy + 8f * s, iconTextPaint)

        // Bar area
        val barLeft = pad + iconS + 12f * s
        val barRight = w - pad - 50f * s
        val barTop = h / 2f - barH / 2f - 2f * s
        val barBottom = barTop + barH

        // Label above bar
        labelPaint.textSize = 12f * s
        canvas.drawText(label, barLeft, barTop - 3f * s, labelPaint)

        // Track background
        val trackRect = RectF(barLeft, barTop, barRight, barBottom)
        canvas.drawRoundRect(trackRect, barR, barR, trackPaint)

        // Fill gradient
        val fillWidth = ((barRight - barLeft) * (displayValue / 100f)).coerceAtLeast(barR * 2)
        val fillRect = RectF(barLeft, barTop, barLeft + fillWidth, barBottom)
        fillPaint.shader = LinearGradient(
            barLeft, 0f, barLeft + fillWidth, 0f,
            intArrayOf(lightColor, baseColor, darkColor),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(fillRect, barR, barR, fillPaint)

        // Shine effect on fill
        if (fillWidth > barR * 2) {
            val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, barTop, 0f, barTop + barH / 2f,
                    intArrayOf(0x44FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(fillRect, barR, barR, shinePaint)
        }

        // Percentage text
        percentPaint.textSize = 13f * s
        val pctText = "${targetValue.toInt()}%"
        canvas.drawText(pctText, w - pad, h / 2f + 5f * s, percentPaint)

        // Continue pulse animation
        if (isLow) {
            postInvalidateDelayed(33)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (56 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(w, h)
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r * (1f - factor)).toInt().coerceIn(0, 255),
            (g * (1f - factor)).toInt().coerceIn(0, 255),
            (b * (1f - factor)).toInt().coerceIn(0, 255)
        )
    }
}

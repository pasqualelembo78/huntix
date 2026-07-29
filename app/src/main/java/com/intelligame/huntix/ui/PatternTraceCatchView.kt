package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class PatternTraceCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CaptureMiniGame {

    companion object {
        const val MAX_ATTEMPTS = 3
    }

    private var miniGameListener: CaptureMiniGame.Listener? = null

    private val templatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#444466")
        alpha = 180
    }
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#00FF88")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val attemptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var currentAttempt = 0
    private var isPlaying = false
    private var isResult = false
    private var resultLabel = ""
    private var resultColor = Color.WHITE
    private var quality = 0f

    private var templatePoints = mutableListOf<Pair<Float, Float>>()
    private var drawnPoints = mutableListOf<Pair<Float, Float>>()
    private var cx = 0f
    private var cy = 0f
    private var patternSize = 0f

    override fun getView(): View = this

    override fun setEggColor(color: Int) {
        eggColor = color
        drawPaint.color = color
        invalidate()
    }

    override fun reset() {
        currentAttempt = 0
        isPlaying = false
        isResult = false
        resultLabel = ""
        drawnPoints.clear()
        invalidate()
    }

    override fun setListener(listener: CaptureMiniGame.Listener) {
        miniGameListener = listener
    }

    override fun release() {
        miniGameListener = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        patternSize = minOf(w, h).toFloat() * 0.25f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (!isPlaying && !isResult && currentAttempt == 0) {
            canvas.drawText("✏️ Disegna il Simbolo", w / 2f, h * 0.3f, textPaint)
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca per iniziare", w / 2f, h * 0.45f, hintPaint)
            return
        }
        if (isResult) {
            textPaint.color = resultColor
            canvas.drawText(resultLabel, w / 2f, h * 0.35f, textPaint)
            if (currentAttempt <= MAX_ATTEMPTS && resultLabel != "CATTURATO!") {
                hintPaint.color = Color.parseColor("#888899")
                canvas.drawText("Tocca per riprovare", w / 2f, h * 0.48f, hintPaint)
            } else if (currentAttempt > MAX_ATTEMPTS) {
                hintPaint.color = Color.parseColor("#888899")
                canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h * 0.48f, hintPaint)
            }
            return
        }
        if (currentAttempt > MAX_ATTEMPTS) {
            textPaint.color = Color.parseColor("#FF3366")
            canvas.drawText("L'uovo è fuggito...", w / 2f, h * 0.35f, textPaint)
            return
        }

        val attemptsLeft = MAX_ATTEMPTS - currentAttempt + 1
        val attemptsText = "Tentativi: ${"●".repeat(attemptsLeft)}${"○".repeat(currentAttempt - 1)}"
        canvas.drawText(attemptsText, w / 2f, h - 20f, attemptPaint)

        glowPaint.color = eggColor
        glowPaint.alpha = 30
        canvas.drawCircle(cx, cy, patternSize * 1.4f, glowPaint)

        drawTemplate(canvas)
        drawUserPath(canvas)

        if (drawnPoints.isEmpty()) {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Traccia il simbolo col dito", w / 2f, h * 0.15f, hintPaint)
        }
    }

    private fun drawTemplate(canvas: Canvas) {
        if (templatePoints.size < 2) return
        val path = Path()
        path.moveTo(templatePoints[0].first, templatePoints[0].second)
        for (i in 1 until templatePoints.size) {
            path.lineTo(templatePoints[i].first, templatePoints[i].second)
        }
        canvas.drawPath(path, templatePaint)

        val last = templatePoints.last()
        hintPaint.color = Color.parseColor("#66FFCC")
        hintPaint.textSize = 28f
        canvas.drawText("●", last.first, last.second + 8f, hintPaint)
    }

    private fun drawUserPath(canvas: Canvas) {
        if (drawnPoints.size < 2) return
        val path = Path()
        path.moveTo(drawnPoints[0].first, drawnPoints[0].second)
        for (i in 1 until drawnPoints.size) {
            path.lineTo(drawnPoints[i].first, drawnPoints[i].second)
        }
        canvas.drawPath(path, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isResult) {
                    if (currentAttempt > MAX_ATTEMPTS) return true
                    if (resultLabel == "CATTURATO!") return true
                    isResult = false
                    startRound()
                    return true
                }
                if (!isPlaying && currentAttempt == 0) {
                    startRound()
                    return true
                }
                if (isPlaying) {
                    drawnPoints.clear()
                    drawnPoints.add(event.x to event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPlaying) {
                    drawnPoints.add(event.x to event.y)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPlaying && drawnPoints.size >= 5) {
                    evaluateTrace()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startRound() {
        currentAttempt++
        isPlaying = true
        isResult = false
        drawnPoints.clear()
        generatePattern()
        invalidate()
    }

    private fun generatePattern() {
        templatePoints.clear()
        val shapes = listOf(
            { circlePattern() },
            { trianglePattern() },
            { starPattern() },
            { wavePattern() }
        )
        shapes[Random.nextInt(shapes.size)]()
    }

    private fun circlePattern() {
        val steps = 24
        for (i in 0..steps) {
            val a = Math.toRadians((i * 360.0 / steps)).toFloat()
            val x = cx + patternSize * cos(a)
            val y = cy + patternSize * sin(a)
            templatePoints.add(x to y)
        }
    }

    private fun trianglePattern() {
        for (i in 0..3) {
            val a = Math.toRadians((i * 120.0 - 90.0)).toFloat()
            val x = cx + patternSize * cos(a)
            val y = cy + patternSize * sin(a)
            templatePoints.add(x to y)
        }
    }

    private fun starPattern() {
        val points = 5
        for (i in 0..points * 2) {
            val a = Math.toRadians((i * 360.0 / (points * 2) - 90.0)).toFloat()
            val r = if (i % 2 == 0) patternSize else patternSize * 0.45f
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            templatePoints.add(x to y)
        }
    }

    private fun wavePattern() {
        val steps = 30
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = cx - patternSize + t * patternSize * 2f
            val y = cy + sin(t * Math.PI * 4).toFloat() * patternSize * 0.4f
            templatePoints.add(x to y)
        }
    }

    private fun evaluateTrace() {
        isPlaying = false
        isResult = true

        if (drawnPoints.size < 5) {
            quality = 0f
        } else {
            val templateLength = pathLength(templatePoints)
            val drawnLength = pathLength(drawnPoints)
            val lengthRatio = if (templateLength > 0) (drawnLength / templateLength).coerceIn(0f, 1.5f) else 0f
            val lengthScore = (1f - abs(lengthRatio - 1f)).coerceIn(0f, 1f)

            val sampledTemplate = resamplePath(templatePoints, 20)
            val sampledDrawn = resamplePath(drawnPoints, 20)
            var totalDist = 0f
            for (i in sampledTemplate.indices) {
                val dx = sampledTemplate[i].first - sampledDrawn[i].first
                val dy = sampledTemplate[i].second - sampledDrawn[i].second
                totalDist += sqrt(dx * dx + dy * dy)
            }
            val distScore = (1f - totalDist / (20f * patternSize * 0.5f)).coerceIn(0f, 1f)
            quality = (lengthScore * 0.3f + distScore * 0.7f).coerceIn(0f, 1f)
        }

        miniGameListener?.onThrowAttempt(currentAttempt, quality)

        val catchChance = when {
            quality >= 0.8f -> 0.90f
            quality >= 0.5f -> 0.65f
            quality >= 0.3f -> 0.35f
            else -> 0.10f
        }
        val caught = Math.random() < catchChance

        if (caught) {
            resultLabel = "CATTURATO!"
            resultColor = Color.parseColor("#00FF88")
            miniGameListener?.onCaptured(currentAttempt)
        } else if (currentAttempt >= MAX_ATTEMPTS) {
            resultLabel = "L'uovo è fuggito..."
            resultColor = Color.parseColor("#FF3366")
            miniGameListener?.onEscaped(currentAttempt)
        } else {
            resultLabel = "Mancato! Tenta ancora"
            resultColor = Color.parseColor("#FF8800")
        }
        invalidate()
    }

    private fun pathLength(points: List<Pair<Float, Float>>): Float {
        if (points.size < 2) return 0f
        var len = 0f
        for (i in 1 until points.size) {
            val dx = points[i].first - points[i - 1].first
            val dy = points[i].second - points[i - 1].second
            len += sqrt(dx * dx + dy * dy)
        }
        return len
    }

    private fun resamplePath(points: List<Pair<Float, Float>>, count: Int): List<Pair<Float, Float>> {
        if (points.size < 2) return points
        val totalLen = pathLength(points)
        if (totalLen < 1f) return points
        val step = totalLen / count
        val result = mutableListOf<Pair<Float, Float>>()
        result.add(points[0])
        var accum = 0f
        var segIdx = 1
        for (i in 1 until count) {
            val target = i * step
            while (segIdx < points.size) {
                val dx = points[segIdx].first - points[segIdx - 1].first
                val dy = points[segIdx].second - points[segIdx - 1].second
                val segLen = sqrt(dx * dx + dy * dy)
                if (accum + segLen >= target) {
                    val t = if (segLen > 0) (target - accum) / segLen else 0f
                    val x = points[segIdx - 1].first + dx * t
                    val y = points[segIdx - 1].second + dy * t
                    result.add(x to y)
                    break
                }
                accum += segLen
                segIdx++
            }
        }
        while (result.size < count) result.add(points.last())
        return result
    }
}

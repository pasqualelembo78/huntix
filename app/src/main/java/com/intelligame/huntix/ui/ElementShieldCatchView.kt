package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.intelligame.huntix.EggElement
import kotlin.math.abs
import kotlin.math.sqrt

class ElementShieldCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CaptureMiniGame, SensorEventListener {

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val GAUGE_FIRE_RATE = 0.025f
        private const val GAUGE_WATER_RATE = 0.02f
        private const val GAUGE_EARTH_RATE = 0.08f
        private const val GAUGE_AIR_RATE = 0.015f
        private const val GAUGE_NORMAL_RATE = 0.25f
    }

    private var miniGameListener: CaptureMiniGame.Listener? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1030")
    }
    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#FF3366")
    }
    private val shieldGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF3366")
        alpha = 30
    }
    private val gaugeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#333355")
    }
    private val gaugeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF88")
    }
    private val gaugeFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF88")
        alpha = 60
    }
    private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFCC00")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }
    private val attemptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var element: EggElement = EggElement.NORMAL
    private var currentAttempt = 0
    private var isPlaying = false
    private var isResult = false
    private var resultLabel = ""
    private var resultColor = Color.WHITE
    private var quality = 0f
    private var gauge = 0f
    private var gaugeFlashing = false
    private var shieldBroken = false
    private var cx = 0f
    private var cy = 0f
    private var h = 0f
    private var w = 0f

    private var sensorManager: SensorManager? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isListening = false
    private var sensorsFailed = false
    private var tapCount = 0
    private var lastTapTime = 0L
    private var gaugeAnimator: android.animation.ValueAnimator? = null

    fun setElement(el: EggElement) {
        element = el
        val shieldColor = when (el) {
            EggElement.FIRE -> Color.parseColor("#FF6B35")
            EggElement.WATER -> Color.parseColor("#00B4FF")
            EggElement.EARTH -> Color.parseColor("#8B4513")
            EggElement.AIR -> Color.parseColor("#A855F7")
            EggElement.NORMAL -> Color.parseColor("#888899")
        }
        shieldPaint.color = shieldColor
        shieldGlowPaint.color = shieldColor
        invalidate()
    }

    override fun getView(): View = this

    override fun setEggColor(color: Int) {
        eggColor = color
        eggPaint.color = color
        invalidate()
    }

    override fun reset() {
        currentAttempt = 0
        isPlaying = false
        isResult = false
        resultLabel = ""
        gauge = 0f
        shieldBroken = false
        stopSensors()
        invalidate()
    }

    override fun setListener(listener: CaptureMiniGame.Listener) {
        miniGameListener = listener
    }

    override fun release() {
        miniGameListener = null
        stopSensors()
        gaugeAnimator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w.toFloat()
        this.h = h.toFloat()
        cx = w / 2f
        cy = h * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPlaying && !isResult && currentAttempt == 0) {
            canvas.drawText("🛡️ Scudo Elementale", w / 2f, h * 0.28f, textPaint)
            val elName = when (element) {
                EggElement.FIRE -> "Fuoco"
                EggElement.WATER -> "Acqua"
                EggElement.EARTH -> "Terra"
                EggElement.AIR -> "Aria"
                EggElement.NORMAL -> "Normale"
            }
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Uovo ${element.emoji()} $elName", w / 2f, h * 0.38f, hintPaint)
            canvas.drawText("Tocca per iniziare", w / 2f, h * 0.55f, hintPaint)
            return
        }
        if (isResult) {
            drawResult(canvas)
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

        val shieldR = minOf(w, h) * 0.18f
        if (!shieldBroken) {
            val crackPhase = (1f - gauge)
            shieldGlowPaint.alpha = (30 + (crackPhase * 40).toInt()).coerceIn(0, 255)
            canvas.drawCircle(cx, cy, shieldR * 1.4f, shieldGlowPaint)

            shieldPaint.strokeWidth = 6f + crackPhase * 6f
            canvas.drawCircle(cx, cy, shieldR, shieldPaint)

            val hexPath = hexagon(cx, cy, shieldR * 0.85f)
            shieldPaint.strokeWidth = 2f
            canvas.drawPath(hexPath, shieldPaint)

            if (gaugeFlashing) {
                gaugeFlashPaint.alpha = (60 + (Math.random() * 40).toInt()).coerceIn(0, 255)
                canvas.drawCircle(cx, cy, shieldR * 1.3f, gaugeFlashPaint)
            }
        }

        canvas.drawCircle(cx, cy, shieldR * 0.35f, eggPaint)

        val gaugeW = w * 0.7f
        val gaugeH = 20f
        val gaugeY = cy + shieldR * 1.6f
        canvas.drawRoundRect(cx - gaugeW / 2, gaugeY, cx + gaugeW / 2, gaugeY + gaugeH, 10f, 10f, gaugeBgPaint)

        if (gauge > 0f) {
            val fillW = gaugeW * gauge.coerceIn(0f, 1f)
            val gaugeColor = when {
                gauge < 0.3f -> Color.parseColor("#FF3366")
                gauge < 0.7f -> Color.parseColor("#FFCC00")
                else -> Color.parseColor("#00FF88")
            }
            gaugeFillPaint.color = gaugeColor
            canvas.drawRoundRect(cx - gaugeW / 2, gaugeY, cx - gaugeW / 2 + fillW, gaugeY + gaugeH, 10f, 10f, gaugeFillPaint)
            val pct = (gauge * 100).toInt().coerceIn(0, 100)
            hintPaint.color = Color.WHITE
            hintPaint.textSize = 18f
            canvas.drawText("$pct%", cx, gaugeY + gaugeH + 28f, hintPaint)
        }

        val (icon, actionText) = actionHint()
        iconPaint.textSize = 42f
        canvas.drawText(icon, cx, gaugeY + 80f, iconPaint)
        hintPaint.color = Color.parseColor("#888899")
        hintPaint.textSize = 22f
        canvas.drawText(actionText, cx, gaugeY + 115f, hintPaint)
    }

    private fun drawResult(canvas: Canvas) {
        textPaint.color = resultColor
        canvas.drawText(resultLabel, w / 2f, h * 0.35f, textPaint)
        if (currentAttempt <= MAX_ATTEMPTS && resultLabel != "CATTURATO!") {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca per riprovare", w / 2f, h * 0.48f, hintPaint)
        } else if (currentAttempt > MAX_ATTEMPTS) {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h * 0.48f, hintPaint)
        }
    }

    private fun hexagon(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        for (i in 0..6) {
            val a = Math.toRadians((i * 60.0 - 30.0)).toFloat()
            val x = cx + r * kotlin.math.cos(a)
            val y = cy + r * kotlin.math.sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun actionHint(): Pair<String, String> {
        return when (element) {
            EggElement.FIRE -> "🔥" to "Soffia nel microfono!"
            EggElement.WATER -> "🌊" to "Inclina il telefono a destra!"
            EggElement.EARTH -> "🪨" to "Tocca rapidamente!"
            EggElement.AIR -> "💨" to "Ruota il telefono!"
            EggElement.NORMAL -> "⚪" to "Tocca l'uovo!"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
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
            if (isPlaying && !shieldBroken) {
                when (element) {
                    EggElement.EARTH -> registerEarthTap()
                    EggElement.NORMAL -> {
                        gauge += GAUGE_NORMAL_RATE
                        checkGauge()
                    }
                    EggElement.WATER, EggElement.FIRE, EggElement.AIR -> {
                        if (sensorsFailed) {
                            gauge += GAUGE_NORMAL_RATE
                            checkGauge()
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startRound() {
        currentAttempt++
        isPlaying = true
        isResult = false
        gauge = 0f
        shieldBroken = false
        tapCount = 0
        sensorsFailed = false
        gaugeFlashing = false

        when (element) {
            EggElement.FIRE -> startFireSensor()
            EggElement.WATER -> startWaterSensor()
            EggElement.AIR -> startAirSensor()
            EggElement.EARTH -> {}
            EggElement.NORMAL -> {}
        }
        invalidate()
    }

    private fun registerEarthTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 600) tapCount = 0
        lastTapTime = now
        tapCount++
        gauge = (tapCount * GAUGE_EARTH_RATE).coerceIn(0f, 1f)
        checkGauge()
        invalidate()
    }

    private fun startFireSensor() {
        try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
                prepare()
                start()
            }
            isListening = true
            startGaugeAnimator(GAUGE_FIRE_RATE) {
                val amp = mediaRecorder?.maxAmplitude ?: 0
                amp > 20000
            }
        } catch (_: Exception) {
            sensorsFailed = true
            isListening = true
            startGaugeAnimator(0.005f) { true }
        }
    }

    private fun startWaterSensor() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
            startGaugeAnimator(GAUGE_WATER_RATE) { true }
        } else {
            sensorsFailed = true
            isListening = true
            startGaugeAnimator(0.005f) { true }
        }
    }

    private fun startAirSensor() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro != null) {
            sensorManager?.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
            startGaugeAnimator(GAUGE_AIR_RATE) { true }
        } else {
            sensorsFailed = true
            isListening = true
            startGaugeAnimator(0.005f) { true }
        }
    }

    private var lastSensorTime = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isPlaying || shieldBroken) return
        event ?: return
        val now = System.currentTimeMillis()
        if (now - lastSensorTime < 100) return
        lastSensorTime = now

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val tilt = abs(event.values[0])
                if (tilt > 6f) {
                    gauge += GAUGE_WATER_RATE * (tilt / 10f)
                    checkGauge()
                    invalidate()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val rot = abs(event.values[2])
                if (rot > 1.5f) {
                    gauge += GAUGE_AIR_RATE * (rot / 5f)
                    checkGauge()
                    invalidate()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startGaugeAnimator(rate: Float, check: () -> Boolean) {
        gaugeAnimator?.cancel()
        gaugeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            repeatCount = android.animation.ValueAnimator.INFINITE
            duration = 200L
            addUpdateListener {
                if (isPlaying && !shieldBroken && isListening) {
                    if (check()) {
                        gauge = (gauge + rate).coerceIn(0f, 1f)
                        checkGauge()
                        invalidate()
                    }
                }
            }
            start()
        }
    }

    private fun checkGauge() {
        gauge = gauge.coerceIn(0f, 1f)
        gaugeFlashing = gauge >= 0.85f
        if (gauge >= 1f) {
            shieldBroken = true
            stopSensors()
            evaluateCatch()
        }
    }

    private fun evaluateCatch() {
        isPlaying = false
        stopSensors()
        gaugeAnimator?.cancel()

        val attemptQuality: Float
        if (shieldBroken) {
            val timeScore = (1f - (currentAttempt - 1) * 0.2f).coerceIn(0.3f, 1f)
            attemptQuality = (0.5f + timeScore * 0.5f).coerceIn(0f, 1f)
            quality = attemptQuality
        } else {
            quality = 0f
        }

        miniGameListener?.onThrowAttempt(currentAttempt, quality)

        if (shieldBroken) {
            val catchChance = when {
                quality >= 0.8f -> 0.90f
                quality >= 0.5f -> 0.65f
                quality >= 0.3f -> 0.40f
                else -> 0.15f
            }
            val caught = Math.random() < catchChance

            if (caught) {
                resultLabel = "CATTURATO!"
                resultColor = Color.parseColor("#00FF88")
                miniGameListener?.onCaptured(currentAttempt)
            } else {
                if (currentAttempt >= MAX_ATTEMPTS) {
                    resultLabel = "L'uovo è fuggito..."
                    resultColor = Color.parseColor("#FF3366")
                    miniGameListener?.onEscaped(currentAttempt)
                } else {
                    resultLabel = "Scudo rotto! Tenta ancora"
                    resultColor = Color.parseColor("#FF8800")
                }
            }
        } else {
            if (currentAttempt >= MAX_ATTEMPTS) {
                resultLabel = "L'uovo è fuggito..."
                resultColor = Color.parseColor("#FF3366")
                miniGameListener?.onEscaped(currentAttempt)
            } else {
                resultLabel = "Mancato! Tenta ancora"
                resultColor = Color.parseColor("#FF8800")
            }
        }
        isResult = true
        invalidate()
    }

    private fun stopSensors() {
        isListening = false
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun EggElement.emoji(): String = when (this) {
        EggElement.FIRE -> "🔥"
        EggElement.WATER -> "💧"
        EggElement.EARTH -> "🌍"
        EggElement.AIR -> "💨"
        EggElement.NORMAL -> "⚪"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopSensors()
    }
}

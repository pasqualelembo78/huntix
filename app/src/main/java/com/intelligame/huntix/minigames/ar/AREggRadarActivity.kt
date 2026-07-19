package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.minigames.MiniGameBase
import com.intelligame.huntix.managers.MiniGameManager

class AREggRadarActivity : MiniGameBase() {

    private data class HiddenEgg(val x: Float, val y: Float, var found: Boolean = false)
    private data class Probe(val x: Float, val y: Float, val distance: String, val color: Int)

    private val totalEggs = 5
    private val maxTaps = 8
    private var eggsFound = 0
    private var tapsUsed = 0
    private var sweepAngle = 0f
    private val eggs = mutableListOf<HiddenEgg>()
    private val probes = mutableListOf<Probe>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var radarView: RadarCanvasView
    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private var isRunning = false
    private var sweepRunnable: Runnable? = null

    override fun onGameCreate() {
        setupUI()
        startGame()
    }

    private fun setupUI() {
        val c = this
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 4), UiKit.dp(c, 16), UiKit.dp(c, 8))
        }

        val arLabel = TextView(c).apply {
            text = "📱 AR Mode — Radar Egg Finder"
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(arLabel)

        infoText = TextView(c).apply {
            text = "Uova: 0/$totalEggs | Tiri: $tapsUsed/$maxTaps"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(infoText)

        statusText = TextView(c).apply {
            text = "Tocca il radar per cercare le uova!"
            textSize = 14f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        container.addView(statusText)

        radarView = RadarCanvasView(c)
        val radarSize = UiKit.dp(c, 310)
        radarView.layoutParams = LinearLayout.LayoutParams(radarSize, radarSize)
        container.addView(radarView)

        val legend = TextView(c).apply {
            text = "Trova 5 uova in 8 tiri! +50 MVC per uovo"
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 6), 0, 0)
        }
        container.addView(legend)

        build("AR Egg Radar", "📡",
            "Un radar mostra la distanza delle uova nascoste. Trovane 5 in 8 tiri!",
            container)
    }

    private fun startGame() {
        eggs.clear(); probes.clear(); eggsFound = 0; tapsUsed = 0; isRunning = true
        repeat(totalEggs) {
            eggs.add(HiddenEgg(
                (Math.random().toFloat() * 0.8f + 0.1f),
                (Math.random().toFloat() * 0.8f + 0.1f)
            ))
        }
        updateInfo()
        statusText.text = "Tocca il radar per cercare le uova!"
        statusText.setTextColor(Color.parseColor(UiKit.ACCENT))

        sweepRunnable?.let { handler.removeCallbacks(it) }
        sweepRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                sweepAngle = (sweepAngle + 3f) % 360f
                radarView.invalidate()
                handler.postDelayed(this, 30)
            }
        }
        handler.postDelayed(sweepRunnable!!, 30)
    }

    private fun updateInfo() {
        infoText.text = "Uova: $eggsFound/$totalEggs | Tiri: $tapsUsed/$maxTaps"
    }

    private fun getDistanceLabel(dist: Float): String = when {
        dist < 0.08f -> "MOLTO VICINO!"
        dist < 0.15f -> "Vicino"
        dist < 0.25f -> "Medio"
        else -> "Lontano"
    }

    private fun getDistanceColor(dist: Float): Int = when {
        dist < 0.08f -> Color.parseColor("#00FF88")
        dist < 0.15f -> Color.parseColor("#AAFF00")
        dist < 0.25f -> Color.parseColor("#FFAA00")
        else -> Color.parseColor("#FF4444")
    }

    inner class RadarCanvasView(ctx: android.content.Context) : View(ctx) {
        private val bgPaint = Paint().apply { color = Color.parseColor("#0A0418") }
        private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A3D20"); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        private val radarFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D1A10"); style = Paint.Style.FILL
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A2A1A"); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF88"); strokeWidth = 2f; alpha = 180
        }
        private val sweepGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF88"); maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.OUTER); alpha = 80
        }
        private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF88"); textAlign = Paint.Align.CENTER
        }
        private val probePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val crosshair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA"); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 24f
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bgPaint.color)
            val cx = width / 2f; val cy = height / 2f; val r = cx * 0.85f

            canvas.drawCircle(cx, cy, r, radarFill)
            canvas.drawCircle(cx, cy, r, radarPaint)
            canvas.drawCircle(cx, cy, r * 0.33f, gridPaint)
            canvas.drawCircle(cx, cy, r * 0.66f, gridPaint)
            canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
            canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)

            val rad = Math.toRadians(sweepAngle.toDouble())
            val sx = cx + (r * Math.cos(rad)).toFloat()
            val sy = cy + (r * Math.sin(rad)).toFloat()
            canvas.drawLine(cx, cy, sx, sy, sweepGlow)
            canvas.drawLine(cx, cy, sx, sy, sweepPaint)

            for (probe in probes) {
                val px = probe.x * width; val py = probe.y * height
                probePaint.color = probe.color; probePaint.alpha = 180
                canvas.drawCircle(px, py, 10f, probePaint)
                textP.textSize = 18f; textP.color = probe.color
                textP.alpha = 220
                canvas.drawText(probe.distance, px, py - 16f, textP)
                textP.color = Color.WHITE
            }

            for (egg in eggs) {
                if (egg.found) {
                    val ex = egg.x * width; val ey = egg.y * height
                    textP.textSize = 36f
                    canvas.drawText("🥚", ex, ey + 12f, textP)
                    canvas.drawCircle(ex, ey, 22f, crosshair)
                }
            }

            for (egg in eggs) {
                if (!egg.found) {
                    val ex = egg.x * width; val ey = egg.y * height
                    textP.textSize = 20f
                    textP.color = Color.parseColor("#6B5B95")
                    canvas.drawText("?", ex, ey + 7f, textP)
                    textP.color = Color.WHITE
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || !isRunning) return true
            tapsUsed++

            val tapX = event.x / width; val tapY = event.y / height
            var closestDist = 1f
            var foundEgg: HiddenEgg? = null

            for (egg in eggs) {
                if (egg.found) continue
                val dx = tapX - egg.x; val dy = tapY - egg.y
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist < closestDist) closestDist = dist
                if (dist < 0.06f) { foundEgg = egg }
            }

            if (foundEgg != null) {
                foundEgg.found = true
                eggsFound++
                probes.add(Probe(tapX, tapY, "🥚 TROVATA!", Color.parseColor(UiKit.GREEN)))
                statusText.text = "🎉 Uovo trovato! ($eggsFound/$totalEggs)"
                statusText.setTextColor(Color.parseColor(UiKit.GREEN))
                MiniGameManager.applyReward(this@AREggRadarActivity, MiniGameManager.GameReward(
                    mvcCoins = 50, label = "AR Radar Uovo $eggsFound", isWin = true
                ), MiniGameManager.GAME_AR_RADAR)
            } else {
                val label = getDistanceLabel(closestDist)
                val color = getDistanceColor(closestDist)
                probes.add(Probe(tapX, tapY, label, color))
                statusText.text = "📡 $label"
                statusText.setTextColor(color)
            }

            updateInfo()
            radarView.invalidate()

            if (eggsFound >= totalEggs) {
                isRunning = false
                sweepRunnable?.let { handler.removeCallbacks(it) }
                statusText.text = "🏆 Tutte trovate! $tapsUsed/$maxTaps tiri"
                statusText.setTextColor(Color.parseColor(UiKit.GREEN))
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this@AREggRadarActivity, "🔄 Gioca Ancora", UiKit.ACCENT) { startGame() })
            } else if (tapsUsed >= maxTaps) {
                isRunning = false
                sweepRunnable?.let { handler.removeCallbacks(it) }
                statusText.text = "⏱ Tiri esauriti! $eggsFound/$totalEggs trovate"
                statusText.setTextColor(Color.parseColor("#FF4444"))
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this@AREggRadarActivity, "🔄 Gioca Ancora", UiKit.ACCENT) { startGame() })
            }

            return true
        }
    }
}

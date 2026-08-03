package com.intelligame.huntix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.intelligame.huntix.managers.MiniGameManager

/**
 * GamePreviewView — anteprima grafica a tema per ogni minigioco.
 * Disegna una "fotografia" stilizzata del gioco; se è una variante AR
 * aggiunge il mirino di scansione e gli angoli della fotocamera.
 */
class GamePreviewView(context: Context) : View(context) {

    private var gameId = ""
    private var arMode = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val ACCENT = Color.parseColor("#A78BFA")
    private val GREEN = Color.parseColor("#00FF88")
    private val GOLD = Color.parseColor("#FFC53D")

    fun setGame(id: String, ar: Boolean) {
        gameId = id
        arMode = ar
        invalidate()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        bgPaint.color = if (arMode) Color.parseColor("#161040") else Color.parseColor("#191B34")
        canvas.drawRoundRect(RectF(0f, 0f, w, h), dp(14f), dp(14f), bgPaint)

        strokePaint.color = if (arMode) Color.parseColor("#3D3170") else Color.parseColor("#2A2C55")
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(RectF(1f, 1f, w - 1, h - 1), dp(14f), dp(14f), strokePaint)

        if (arMode) drawReticle(canvas, w, h)

        drawIcon(canvas, w, h)
    }

    private fun drawReticle(canvas: Canvas, w: Float, h: Float) {
        thinPaint.color = Color.argb(90, 167, 139, 250)
        thinPaint.strokeWidth = dp(1f)
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawCircle(cx, cy, dp(20f), thinPaint)
        val len = dp(6f)
        val s = dp(6f)
        for ((px, py, dx, dy) in arrayOf(
            arrayOf(s, s, 1f, 1f), arrayOf(w - s, s, -1f, 1f),
            arrayOf(s, h - s, 1f, -1f), arrayOf(w - s, h - s, -1f, -1f)
        )) {
            canvas.drawLine(px, py, px + dx * len, py, thinPaint)
            canvas.drawLine(px + dx * len, py, px + dx * len, py + dy * len, thinPaint)
            canvas.drawLine(px, py, px, py + dy * len, thinPaint)
            canvas.drawLine(px, py + dy * len, px + dx * len, py + dy * len, thinPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        when (gameId) {
            MiniGameManager.GAME_2048 -> draw2048(canvas, cx, cy)
            MiniGameManager.GAME_SNAKE -> drawSnake(canvas, cx, cy)
            MiniGameManager.GAME_MINESWEEPER -> drawMinesweeper(canvas, cx, cy)
            MiniGameManager.GAME_FLAPPY -> drawFlappy(canvas, cx, cy)
            MiniGameManager.GAME_CONNECT4 -> drawConnect4(canvas, cx, cy)
            MiniGameManager.GAME_HANGMAN -> drawHangman(canvas, cx, cy)
            MiniGameManager.GAME_TIC_TAC_TOE -> drawTris(canvas, cx, cy)
            MiniGameManager.GAME_SIMON -> drawSimon(canvas, cx, cy)
            MiniGameManager.GAME_DINO -> drawDino(canvas, cx, cy)
            MiniGameManager.GAME_MEMORY -> drawMemory(canvas, cx, cy)
            MiniGameManager.GAME_CATCH_EGG -> drawCatchEgg(canvas, cx, cy)
            MiniGameManager.GAME_MATCH3 -> drawMatch3(canvas, cx, cy)
            MiniGameManager.GAME_PONG -> drawPong(canvas, cx, cy)
            MiniGameManager.GAME_MOTO_X3M -> drawMotoX3M(canvas, cx, cy)
            MiniGameManager.GAME_SOLITAIRE -> drawSolitaire(canvas, cx, cy)
            MiniGameManager.GAME_BRISCOLA -> drawBriscola(canvas, cx, cy)
            MiniGameManager.GAME_SCOPA -> drawScopa(canvas, cx, cy)
            MiniGameManager.GAME_AR_SHOOTER -> drawShooter(canvas, cx, cy)
            MiniGameManager.GAME_AR_BOMB -> drawBomb(canvas, cx, cy)
            MiniGameManager.GAME_AR_RADAR -> drawRadar(canvas, cx, cy)
            MiniGameManager.GAME_SLINGSHOT -> drawSlingshot(canvas, cx, cy)
            else -> drawSwords(canvas, cx, cy)
        }
    }

    // ── singole anteprime ────────────────────────────────────────

    private fun egg(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillPaint.color = color
        val rf = RectF(cx - r, cy - r * 1.25f, cx + r, cy + r * 1.25f)
        canvas.drawOval(rf, fillPaint)
    }

    private fun draw2048(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(15f)
        val tile = dp(16f)
        val gap = dp(3f)
        val values = listOf(2, 4, 8, 16)
        val colors = listOf(0xFFEAD7A1.toInt(), 0xFFE4B978.toInt(), 0xFFE88E5A.toInt(), 0xFFE95D5D.toInt())
        var idx = 0
        for (row in 0..1) for (col in 0..1) {
            fillPaint.color = colors[idx]
            canvas.drawRoundRect(RectF(cx - s + col * (tile + gap), cy - s + row * (tile + gap), cx - s + col * (tile + gap) + tile, cy - s + row * (tile + gap) + tile), dp(3f), dp(3f), fillPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = dp(10f)
            canvas.drawText(values[idx].toString(), cx - s + col * (tile + gap) + tile / 2f, cy - s + row * (tile + gap) + tile / 2f + dp(3.5f), textPaint)
            idx++
        }
    }

    private fun drawSnake(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = GREEN
        strokePaint.strokeWidth = dp(4f)
        val path = Path()
        path.moveTo(cx - dp(20f), cy + dp(6f))
        path.lineTo(cx - dp(6f), cy + dp(6f))
        path.lineTo(cx - dp(6f), cy - dp(8f))
        path.lineTo(cx + dp(8f), cy - dp(8f))
        path.lineTo(cx + dp(8f), cy + dp(4f))
        canvas.drawPath(path, strokePaint)
        egg(canvas, cx + dp(8f), cy + dp(4f), dp(4.5f), Color.WHITE)
        egg(canvas, cx + dp(20f), cy - dp(12f), dp(3.5f), GOLD)
    }

    private fun drawMinesweeper(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(12f)
        strokePaint.color = Color.parseColor("#8E7CE8")
        strokePaint.strokeWidth = dp(1.2f)
        for (i in 0..3) {
            val x = cx - s + i * s * 2f / 3f
            canvas.drawLine(x, cy - s, x, cy + s, strokePaint)
        }
        for (i in 0..3) {
            val y = cy - s + i * s * 2f / 3f
            canvas.drawLine(cx - s, y, cx + s, y, strokePaint)
        }
        fillPaint.color = Color.parseColor("#FF5252")
        canvas.drawCircle(cx, cy, dp(4f), fillPaint)
        strokePaint.color = Color.parseColor("#FF5252")
        strokePaint.strokeWidth = dp(1f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            canvas.drawLine(
                cx + Math.cos(a).toFloat() * dp(5.5f), cy + Math.sin(a).toFloat() * dp(5.5f),
                cx + Math.cos(a).toFloat() * dp(8f), cy + Math.sin(a).toFloat() * dp(8f), strokePaint
            )
        }
    }

    private fun drawFlappy(canvas: Canvas, cx: Float, cy: Float) {
        fillPaint.color = Color.parseColor("#3DDC84")
        canvas.drawRect(RectF(cx - dp(15f), cy - dp(22f), cx - dp(9f), cy + dp(4f)), fillPaint)
        canvas.drawRect(RectF(cx - dp(16f), cy - dp(22f), cx - dp(8f), cy - dp(18f)), fillPaint)
        canvas.drawRect(RectF(cx + dp(5f), cy - dp(8f), cx + dp(11f), cy + dp(22f)), fillPaint)
        canvas.drawRect(RectF(cx + dp(4f), cy - dp(4f), cx + dp(12f), cy), fillPaint)
        egg(canvas, cx, cy, dp(6f), Color.WHITE)
        fillPaint.color = GOLD
        canvas.drawCircle(cx - dp(2f), cy + dp(1f), dp(1.2f), fillPaint)
    }

    private fun drawConnect4(canvas: Canvas, cx: Float, cy: Float) {
        val colX = listOf(-dp(16f), -dp(5f), dp(6f), dp(17f)).map { cx + it }
        for ((j, x) in colX.withIndex()) {
            for (r in 0..2) {
                val y = cy + dp(10f) - r * dp(7f)
                val isFilled = (j == 1 && r == 0) || (j == 2 && r == 1) || (j == 0 && r == 2)
                val color = when {
                    isFilled && j == 0 -> Color.parseColor("#FF5A5A")
                    isFilled && j == 1 -> Color.parseColor("#FFD54F")
                    isFilled -> Color.parseColor("#A78BFA")
                    else -> Color.parseColor("#334A3A7A")
                }
                fillPaint.color = color
                canvas.drawCircle(x, y, dp(4.5f), fillPaint)
            }
        }
    }

    private fun drawHangman(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = Color.parseColor("#8D6E63")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(16f), cy + dp(16f), cx + dp(16f), cy + dp(16f), strokePaint)
        canvas.drawLine(cx - dp(8f), cy + dp(16f), cx - dp(8f), cy - dp(18f), strokePaint)
        canvas.drawLine(cx - dp(8f), cy - dp(18f), cx + dp(8f), cy - dp(18f), strokePaint)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawLine(cx + dp(8f), cy - dp(18f), cx + dp(8f), cy - dp(12f), strokePaint)
        egg(canvas, cx + dp(8f), cy - dp(8f), dp(4.5f), Color.WHITE)
    }

    private fun drawTris(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(11f)
        strokePaint.color = ACCENT
        strokePaint.strokeWidth = dp(1.2f)
        for (i in 1..2) {
            canvas.drawLine(cx - s + i * s * 2f / 3f, cy - s, cx - s + i * s * 2f / 3f, cy + s, strokePaint)
            canvas.drawLine(cx - s, cy - s + i * s * 2f / 3f, cx + s, cy - s + i * s * 2f / 3f, strokePaint)
        }
        val cell = s * 2f / 3f
        // X
        strokePaint.color = GREEN
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - s, cy - s, cx - s + cell, cy - s + cell, strokePaint)
        canvas.drawLine(cx - s + cell, cy - s, cx - s, cy - s + cell, strokePaint)
        // O
        strokePaint.color = ACCENT
        canvas.drawCircle(cx + cell / 2f, cy + cell / 2f, cell / 2f - dp(2f), strokePaint)
    }

    private fun drawSimon(canvas: Canvas, cx: Float, cy: Float) {
        val off = dp(7f)
        val r = dp(6f)
        fillPaint.color = 0xFFEF5350.toInt()
        canvas.drawCircle(cx - off, cy - off, r, fillPaint)
        fillPaint.color = 0xFF42A5F5.toInt()
        canvas.drawCircle(cx + off, cy - off, r, fillPaint)
        fillPaint.color = 0xFF66BB6A.toInt()
        canvas.drawCircle(cx - off, cy + off, r, fillPaint)
        fillPaint.color = 0xFFFFCA28.toInt()
        canvas.drawCircle(cx + off, cy + off, r, fillPaint)
    }

    private fun drawDino(canvas: Canvas, cx: Float, cy: Float) {
        // Ground line
        strokePaint.color = GREEN
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawLine(cx - dp(18f), cy + dp(8f), cx + dp(18f), cy + dp(8f), strokePaint)
         // Dino body
        fillPaint.color = GREEN
        canvas.drawRect(RectF(cx - dp(10f), cy - dp(2f), cx - dp(2f), cy + dp(6f)), fillPaint)
        // Dino head
        canvas.drawCircle(cx - dp(4f), cy - dp(6f), dp(4f), fillPaint)
        // Eye
        fillPaint.color = Color.BLACK
        canvas.drawCircle(cx - dp(5.5f), cy - dp(7f), dp(1f), fillPaint)
        // Legs
        strokePaint.color = GREEN
        canvas.drawLine(cx - dp(9f), cy + dp(4f), cx - dp(9f), cy + dp(10f), strokePaint)
        canvas.drawLine(cx - dp(5f), cy + dp(4f), cx - dp(5f), cy + dp(10f), strokePaint)
        // Neck
        strokePaint.color = GREEN
        canvas.drawLine(cx - dp(4f), cy - dp(6f), cx - dp(2f), cy - dp(12f), strokePaint)
        // Cacti
        fillPaint.color = 0xFF00C86A.toInt()
        canvas.drawRect(cx - dp(6f), cy + dp(8f), cx - dp(3f), cy + dp(2f), fillPaint)
        canvas.drawRect(cx + dp(8f), cy + dp(8f), cx + dp(11f), cy + dp(4f), fillPaint)
        canvas.drawRect(cx + dp(15f), cy + dp(8f), cx + dp(18f), cy + dp(2f), fillPaint)
    }

    private fun drawMemory(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(11f)
        val card = dp(10f)
        for (i in 0..1) for (j in 0..1) {
            fillPaint.color = if ((i + j) % 2 == 0) ACCENT else Color.parseColor("#3A3B66")
            val l = cx - s + j * (card + dp(3f))
            val t = cy - s + i * (card + dp(3f))
            canvas.drawRoundRect(RectF(l, t, l + card, t + card), dp(2f), dp(2f), fillPaint)
        }
    }

    private fun drawCatchEgg(canvas: Canvas, cx: Float, cy: Float) {
        egg(canvas, cx, cy - dp(2f), dp(7f), Color.WHITE)
        strokePaint.color = Color.parseColor("#A78BFA")
        strokePaint.strokeWidth = dp(1.6f)
        val basket = RectF(cx - dp(10f), cy + dp(4f), cx + dp(10f), cy + dp(10f))
        canvas.drawArc(basket, 0f, 180f, false, strokePaint)
    }

    private fun drawMatch3(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(6f)
        fillPaint.color = Color.parseColor("#00FF88")
        canvas.drawCircle(cx - dp(9f), cy + dp(9f), r, fillPaint)
        fillPaint.color = Color.parseColor("#FF5A5A")
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.color = Color.parseColor("#42A5F5")
        canvas.drawCircle(cx + dp(9f), cy - dp(9f), r, fillPaint)
    }

    private fun drawPong(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = ACCENT
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(16f), cy - dp(2f), cx + dp(16f), cy - dp(2f), strokePaint)

        fillPaint.color = GREEN
        val paddleW = dp(14f)
        val paddleH = dp(4f)
        canvas.drawRoundRect(
            RectF(cx - paddleW / 2f, cy + dp(12f), cx + paddleW / 2f, cy + dp(12f) + paddleH),
            dp(2f), dp(2f), fillPaint
        )

        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy - dp(12f), dp(3f), fillPaint)
    }

    private fun drawMotoX3M(canvas: Canvas, cx: Float, cy: Float) {
        // Ground line
        strokePaint.color = GREEN
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(18f), cy + dp(10f), cx + dp(18f), cy + dp(10f), strokePaint)

        // Motorcycle body
        fillPaint.color = Color.parseColor("#FF6B35")
        canvas.drawRoundRect(RectF(cx - dp(12f), cy - dp(8f), cx + dp(12f), cy + dp(2f)), dp(4f), dp(4f), fillPaint)
        // Wheels
        fillPaint.color = Color.BLACK
        canvas.drawCircle(cx - dp(8f), cy + dp(4f), dp(5f), fillPaint)
        canvas.drawCircle(cx + dp(8f), cy + dp(4f), dp(5f), fillPaint)
        // Helmet
        fillPaint.color = Color.parseColor("#4FC3F7")
        canvas.drawCircle(cx, cy - dp(10f), dp(4f), fillPaint)

        // Obstacle rocks
        fillPaint.color = Color.parseColor("#8D6E63")
        canvas.drawRoundRect(RectF(cx - dp(5f), cy + dp(6f), cx + dp(5f), cy + dp(12f)), dp(2f), dp(2f), fillPaint)
    }

    private fun drawSolitaire(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(10f)
        val cardW = dp(8f)
        val cardH = dp(12f)
        // Draw a few cards stacked
        for (i in 0 until 4) {
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(RectF(cx - s + i * dp(2f), cy - cardH + i * dp(1.5f), cx - s + i * dp(2f) + cardW, cy + i * dp(1.5f)), dp(1f), dp(1f), fillPaint)
            strokePaint.color = Color.parseColor("#CCCCCC")
            strokePaint.strokeWidth = dp(0.5f)
            canvas.drawRoundRect(RectF(cx - s + i * dp(2f), cy - cardH + i * dp(1.5f), cx - s + i * dp(2f) + cardW, cy + i * dp(1.5f)), dp(1f), dp(1f), strokePaint)
        }
        // Ace of hearts on top
        fillPaint.color = Color.parseColor("#FF4444")
        textPaint.textSize = dp(8f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("A", cx - s + dp(4f), cy - cardH + dp(9f), textPaint)
        textPaint.color = Color.parseColor("#FF4444")
        canvas.drawText("♥", cx - s + dp(4f), cy + dp(2f), textPaint)
    }

    private fun drawBriscola(canvas: Canvas, cx: Float, cy: Float) {
        // Draw 3 cards in a fan
        val s = dp(10f)
        val cardW = dp(8f)
        val cardH = dp(12f)
        for (i in 0 until 3) {
            val angle = (i - 1) * dp(3f)
            val x = cx + angle
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(RectF(x - cardW / 2f, cy - cardH, x + cardW / 2f, cy), dp(1f), dp(1f), fillPaint)
            strokePaint.color = Color.parseColor("#CCCCCC")
            strokePaint.strokeWidth = dp(0.5f)
            canvas.drawRoundRect(RectF(x - cardW / 2f, cy - cardH, x + cardW / 2f, cy), dp(1f), dp(1f), strokePaint)
        }
        textPaint.textSize = dp(8f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#FF4444")
        canvas.drawText("♥", cx, cy - dp(2f), textPaint)
    }

    private fun drawScopa(canvas: Canvas, cx: Float, cy: Float) {
        // Draw table cards + hand card
        val s = dp(10f)
        val cardW = dp(8f)
        val cardH = dp(12f)
        // Table cards
        for (i in 0 until 3) {
            fillPaint.color = Color.parseColor("#FFF8E1")
            canvas.drawRoundRect(RectF(cx - s + i * dp(10f), cy - cardH, cx - s + i * dp(10f) + cardW, cy), dp(1f), dp(1f), fillPaint)
            strokePaint.color = Color.parseColor("#CCCCCC")
            strokePaint.strokeWidth = dp(0.5f)
            canvas.drawRoundRect(RectF(cx - s + i * dp(10f), cy - cardH, cx - s + i * dp(10f) + cardW, cy), dp(1f), dp(1f), strokePaint)
        }
        // Hand card (selected)
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(RectF(cx - s + dp(10f), cy + dp(4f), cx - s + dp(10f) + cardW, cy + dp(4f) + cardH), dp(1f), dp(1f), fillPaint)
        strokePaint.color = Color.parseColor("#A78BFA")
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(RectF(cx - s + dp(10f), cy + dp(4f), cx - s + dp(10f) + cardW, cy + dp(4f) + cardH), dp(1f), dp(1f), strokePaint)
        // 7 of coins (settebello)
        textPaint.textSize = dp(7f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#FFC53D")
        canvas.drawText("7", cx - s + dp(14f), cy + dp(10f), textPaint)
        textPaint.color = Color.parseColor("#FFD54F")
        canvas.drawText("♦", cx - s + dp(14f), cy + dp(18f), textPaint)
    }

    private fun drawSwords(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = ACCENT
        strokePaint.strokeWidth = dp(2.4f)
        canvas.drawLine(cx - dp(14f), cy + dp(14f), cx + dp(14f), cy - dp(14f), strokePaint)
        canvas.drawLine(cx + dp(14f), cy + dp(14f), cx - dp(14f), cy - dp(14f), strokePaint)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawLine(cx - dp(14f), cy + dp(14f), cx - dp(10f), cy + dp(18f), strokePaint)
        canvas.drawLine(cx + dp(14f), cy + dp(14f), cx + dp(10f), cy + dp(18f), strokePaint)
    }

    private fun drawShooter(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = ACCENT
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawRoundRect(RectF(cx - dp(14f), cy - dp(10f), cx + dp(14f), cy + dp(10f)), dp(3f), dp(3f), strokePaint)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, dp(5f), strokePaint)
        egg(canvas, cx, cy, dp(4f), GOLD)
    }

    private fun drawBomb(canvas: Canvas, cx: Float, cy: Float) {
        fillPaint.color = Color.parseColor("#FF5252")
        canvas.drawCircle(cx, cy, dp(8f), fillPaint)
        strokePaint.color = Color.parseColor("#FFD54F")
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawLine(cx, cy - dp(8f), cx, cy - dp(12f), strokePaint)
        canvas.drawLine(cx, cy - dp(12f), cx + dp(3f), cy - dp(10f), strokePaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx - dp(3f), cy - dp(3f), dp(1.6f), fillPaint)
    }

    private fun drawRadar(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = Color.parseColor("#00FF88")
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawArc(RectF(cx - dp(16f), cy - dp(16f), cx + dp(16f), cy + dp(16f)), -90f, 120f, false, strokePaint)
        canvas.drawArc(RectF(cx - dp(10f), cy - dp(10f), cx + dp(10f), cy + dp(10f)), -90f, 120f, false, strokePaint)
        canvas.drawArc(RectF(cx - dp(4f), cy - dp(4f), cx + dp(4f), cy + dp(4f)), -90f, 120f, false, strokePaint)
        fillPaint.color = GOLD
        canvas.drawCircle(cx + dp(7f), cy + dp(4f), dp(2.5f), fillPaint)
    }

    private fun drawSlingshot(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = Color.parseColor("#8D6E63")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - dp(14f), cy + dp(10f), cx - dp(14f), cy - dp(12f), strokePaint)
        canvas.drawLine(cx + dp(14f), cy + dp(10f), cx + dp(14f), cy - dp(12f), strokePaint)
        canvas.drawLine(cx - dp(14f), cy - dp(12f), cx + dp(14f), cy - dp(16f), strokePaint)
        canvas.drawLine(cx + dp(14f), cy - dp(12f), cx + dp(14f), cy - dp(16f), strokePaint)
        val band = RectF(cx - dp(14f), cy - dp(10f), cx + dp(14f), cy - dp(8f))
        canvas.drawArc(band, 0f, 180f, false, strokePaint)
        egg(canvas, cx, cy + dp(8f), dp(4.5f), Color.WHITE)
    }
}

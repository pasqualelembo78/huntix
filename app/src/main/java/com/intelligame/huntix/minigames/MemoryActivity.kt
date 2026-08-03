package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🎮 Memory — adattato da open-source.
 *
 * Originale: 142 giochi browser (memory incluso)
 * Licenza: 
 * Tipo: 
 *
 * Adattamento: logica di gioco preservata,
 * rendering convertito al pattern Canvas di Huntix.
 */
class MemoryActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private var gameRunning = false
    private var score = 0
    private var scoreText: TextView? = null
    private var gameView: GameView? = null
    private var overlayContainer: FrameLayout? = null

    // ── Stato del gioco ──────────────────────────────────────────
    // TODO: Aggiungere le variabili di stato dal codice originale

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Memory", "🎮"))
        root.addView(TextView(ctx).apply {
            text = "Gioco open-source adattato a Huntix"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_MEMORY))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = GameView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        score = 0
        gameRunning = true
        scoreText?.text = "Punti: 0"
        // TODO: Inizializzare lo stato del gioco originale
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, 16L)
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            // TODO: Aggiornare la logica di gioco originale
            // TODO: Gestire il game over
            gameView?.invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    // ── Rendering ─────────────────────────────────────────────────

    inner class GameView(context: android.content.Context) : View(context) {
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            // TODO: Disegnare il gioco originale usando Canvas
            c.drawColor(Color.parseColor("#0D0620"))
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            // TODO: Gestire il touch dal codice originale
            return true
        }
    }

    // ── Fine gioco ────────────────────────────────────────────────

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = if (won) (score * 0.2f).toInt().coerceAtLeast(10) else kotlin.math.max(score / 5, 3)
        val xp = if (won) (score * 0.1f).toInt().coerceAtLeast(5) else kotlin.math.max(score / 8, 2)

        try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_MEMORY, score,
                mvc = mvc, xp = xp,
                label = if (won) "Memory: vittoria!" else "Memory: sconfitta",
                isWin = won
            )
        } catch (e: Exception) { Sentry.captureException(e) }

        // TODO: Mostrare overlay di fine gioco (vedi SnakeActivity.kt per il pattern)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}

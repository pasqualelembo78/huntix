package com.intelligame.huntix.minigames.card

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry

/**
 * CardGameBase — base astratta per tutti i giochi di carte.
 *
 * Fornisce il design coerente Huntix:
 * - Schermata iniziale con titolo, emoji, istruzioni e regole
 * - Area di gioco Canvas personalizzabile
 * - HUD con punteggio, vite, livello
 * - Overlay di fine gioco con ricompense (MVC/XP/uova)
 * - Integrazione con il sistema di progressione MiniGameManager
 *
 * Sottoclassi devono implementare:
 * - [onSetupGame] — inizializzazione tavola/carte
 * - [onDrawGame] — rendering della partita
 * - [onPlayerTap] — input del giocatore
 * - [checkGameOver] — condizioni di vittoria/sconfitta
 */
abstract class CardGameBase : AppCompatActivity() {

    protected val handler = Handler(Looper.getMainLooper())
    protected var gameRunning = false

    protected lateinit var gameView: CardGameView
    protected lateinit var scoreText: TextView
    protected lateinit var livesText: TextView
    protected lateinit var statusText: TextView
    protected lateinit var overlayContainer: FrameLayout

    protected var score = 0
    protected var lives = 3
    protected var currentLevel = 1

    abstract val config: CardGameConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }

        root.addView(UiKit.title(ctx, config.title, config.emoji))

        val rulesBtn = TextView(ctx).apply {
            text = "📖 Regole"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
            isClickable = true
            isFocusable = true
            setOnClickListener { showRules() }
        }
        root.addView(rulesBtn)

        root.addView(UiKit.section(ctx, "🎯 Obiettivo"))
        root.addView(TextView(ctx).apply {
            text = config.rules
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })

        root.addView(UiKit.section(ctx, "🎯 Obiettivo"))
        val hudRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, UiKit.dp(ctx, 12), 0)
        }
        livesText = TextView(ctx).apply {
            text = "❤️".repeat(lives); textSize = 16f; setTextColor(Color.parseColor(UiKit.GREEN))
        }
        hudRow.addView(scoreText!!)
        hudRow.addView(livesText!!)
        root.addView(hudRow)

        statusText = TextView(ctx).apply {
            text = "Pronto?"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(ctx, 6))
        }
        root.addView(statusText!!)

        gameView = CardGameView(ctx)
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

    protected fun startGame() {
        score = 0
        lives = 3
        currentLevel = MiniGameManager.getLevel(this, config.toGameId())
        gameRunning = true
        scoreText.text = "Punti: 0"
        livesText.text = "❤️".repeat(lives)
        statusText.text = "Inizia!"
        onSetupGame()
        gameView.invalidate()
    }

    protected fun updateHUD() {
        scoreText.text = "Punti: $score"
        livesText.text = "❤️".repeat(lives.coerceAtLeast(0))
    }

    protected fun showRules() {
        val ctx = this
        val dialog = android.app.Dialog(ctx)
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(ctx, 24), UiKit.dp(ctx, 24), UiKit.dp(ctx, 24), UiKit.dp(ctx, 20))
        }
        box.addView(TextView(ctx).apply {
            text = "📖 ${config.title} — Regole"
            textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        box.addView(TextView(ctx).apply {
            text = config.rules
            textSize = 14f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
        })
        box.addView(UiKit.button(ctx, "✕ Chiudi", UiKit.TEXT_DIM) { dialog.dismiss() })
        scroll.addView(box)
        dialog.setContentView(scroll)
        if (dialog.window != null) {
            dialog.window!!.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.parseColor("#120D26")))
            dialog.window!!.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)
        dialog.show()
    }

    protected fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false

        val mvc = if (won) (score * 0.2f * config.rewardMultiplier).toInt().coerceAtLeast(15) else kotlin.math.max(score / 5, 5)
        val xp = if (won) (score * 0.1f * config.rewardMultiplier).toInt().coerceAtLeast(8) else kotlin.math.max(score / 8, 3)
        val result = try {
            MiniGameManager.completePlay(
                this, config.toGameId(), score,
                mvc = mvc, xp = xp,
                label = "${config.title}: ${if (won) "vittoria!" else "sconfitta"}",
                isWin = won,
                giftEggRarityId = if (won) "common" else null
            )
        } catch (e: Exception) { Sentry.captureException(e); null }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "🏆" else "😔"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Vittoria!" else "Sconfitta"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punti: $score"; textSize = 18f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+${result?.mvc ?: mvc} MVC  •  +${result?.xp ?: xp} XP"
            textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * CardGameView — Canvas di gioco per i giochi di carte.
     */
    inner class CardGameView(context: android.content.Context) : View(context) {

        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val tablePaint = Paint().apply { color = Color.parseColor("#1A3A1A") }

        var viewW = 0f
        var viewH = 0f

        fun setSizes(w: Float, h: Float) { viewW = w; viewH = h }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            viewW = w; viewH = h
            c.drawColor(bgPaint.color)
            c.drawRoundRect(RectF(0f, 0f, w, h), 14f, 14f, tablePaint)
            onDrawGame(c, w, h)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    onPlayerTap(ev.x, ev.y)
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    // ── Metodi da implementare nelle sottoclassi ──────────

    /** Inizializza tavola, carte, stato di gioco. */
    protected abstract fun onSetupGame()

    /** Disegna la partita corrente sul canvas. */
    protected abstract fun onDrawGame(canvas: Canvas, w: Float, h: Float)

    /** Gestisce il tocco del giocatore. */
    protected abstract fun onPlayerTap(x: Float, y: Float)

    /** Controlla se la partita è finita (vittoria/sconfitta). */
    protected abstract fun checkGameOver()
}

/** Estensione per ottenere il gameId da un CardGameConfig. */
fun CardGameConfig.toGameId(): String = gameId
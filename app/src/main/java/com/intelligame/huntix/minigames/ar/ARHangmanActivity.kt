package com.intelligame.huntix.minigames.ar

import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import java.util.Locale

/**
 * 🙈 AR Impiccato — una forca sospesa nella stanza REALE con un'uovo appeso
 * che precipita ad ogni errore. Le lettere della parola sono uova fluttuanti
 * che diventano dorate quando le indovini. Lettere tramite tastiera in HUD.
 */
class ARHangmanActivity : ARGameActivity() {

    companion object {
        private val WORDS = listOf(
            "UOVO", "NIDO", "CODA", "PULCINO", "DORATO", "CANESTRO",
            "CORTILE", "COLORE", "TANA", "STELLA", "FESTA", "NASCOSTO",
            "VERDE", "MAGIA", "CIELO", "TANA", "FOGLIA", "SPRING"
        )
        private val C_DIM = 0x99FFFFFF.toInt()
        private val C_GOLD = 0xFFFFD700.toInt()
    }

    private var arena: AnchorNode? = null
    private val letterEggs = mutableListOf<SphereNode>()
    private val gallowsEgg = arrayOfNulls<SphereNode>(1)
    private var gallowsPole: CubeNode? = null
    private var gallowsArm: CubeNode? = null
    private var gallowsBase: CubeNode? = null
    private var gallowsRope: CubeNode? = null

    private var word = ""
    private val guessed = HashSet<Char>()
    private var wrong = 0
    private var gameOver = false
    private var letterGrid: LinearLayout? = null

    override fun onGameCreate() {
        word = WORDS.random().uppercase(Locale.ITALIAN)
        guessed.clear()
        wrong = 0
        gameOver = false
        letterEggs.clear()
        statusText.text = ""
        statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
        livesText.text = "🙈 Impiccato"
        timerText.text = "Errori: 0/6"
        scoreText.text = word.length.toString() + " lettere"
        startGame()
        whenReady { build() }
    }

    private fun build() {
        val a = spawnAnchor(0.95f, 0f, -0.1f)
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        arena = a

        gallowsBase = cubeNode(0xFF8D6E63.toInt(), Float3(0.6f, 0.08f, 0.4f)).apply {
            position = Position(0f, 0.04f, -0.6f)
        }
        gallowsPole = cubeNode(0xFF8D6E63.toInt(), Float3(0.06f, 1.3f, 0.06f)).apply {
            position = Position(0f, 0.75f, -0.6f)
        }
        gallowsArm = cubeNode(0xFF8D6E63.toInt(), Float3(0.55f, 0.07f, 0.07f)).apply {
            position = Position(0.25f, 1.36f, -0.6f)
        }
        gallowsRope = cubeNode(0xFFD9C9A3.toInt(), Float3(0.03f, 0.35f, 0.03f)).apply {
            position = Position(0.42f, 1.15f, -0.6f)
        }
        val hanging = eggNode(0xFFFFFFFF.toInt(), 0.1f).apply {
            position = Position(0.42f, 0.88f, -0.6f)
        }
        gallowsEgg[0] = hanging
        a.addChildNode(gallowsBase!!)
        a.addChildNode(gallowsPole!!)
        a.addChildNode(gallowsArm!!)
        a.addChildNode(gallowsRope!!)
        a.addChildNode(hanging)

        val cell = 0.26f
        val startX = -((word.length - 1) / 2f) * cell
        for (i in word.indices) {
            val egg = eggNode(C_DIM, 0.09f)
            egg.position = Position(startX + i * cell, 0.35f, 0f)
            a.addChildNode(egg)
            letterEggs.add(egg)
        }
        buildLetterGrid()
        updateProgress()
    }

    private fun buildLetterGrid() {
        val ctx = this
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, UiKit.dp(ctx, 6))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }
        val rows = listOf('A'..'M', 'N'..'Z')
        for (row in rows) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            for (ch in row) {
                val btn = Button(ctx).apply {
                    text = ch.toString()
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setBackgroundColor(0x99141B3D.toInt())
                    isAllCaps = false
                    setPadding(UiKit.dp(ctx, 2), 0, UiKit.dp(ctx, 2), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        UiKit.dp(ctx, 26), UiKit.dp(ctx, 34)
                    ).apply { marginEnd = UiKit.dp(ctx, 3) }
                }
                btn.setOnClickListener {
                    if (gameOver) return@setOnClickListener
                    guess(ch)
                    btn.isEnabled = false
                    btn.setTextColor(0x66888888.toInt())
                }
                rowLayout.addView(btn)
            }
            grid.addView(rowLayout)
        }
        letterGrid = grid
        hud.addView(grid)
    }

    private fun guess(ch: Char) {
        if (guessed.contains(ch)) return
        guessed.add(ch)
        if (word.contains(ch)) {
            for (i in word.indices) {
                if (word[i] == ch) {
                    val egg = letterEggs.getOrNull(i) ?: continue
                    val parent = egg.parent
                    removeNode(egg)
                    val gold = eggNode(C_GOLD, 0.09f)
                    gold.position = Position(egg.position.x, egg.position.y, egg.position.z)
                    parent?.addChildNode(gold)
                    letterEggs[i] = gold
                }
            }
            updateProgress()
            if (word.all { guessed.contains(it) }) {
                endGame(true)
            }
        } else {
            wrong++
            timerText.text = "Errori: $wrong/6"
            val t = wrong / 6f
            val color = Color.rgb(255, (255 * (1 - t)).toInt(), (255 * (1 - t)).toInt())
            gallowsEgg[0]?.let { egg ->
                val oldParent = egg.parent
                removeNode(egg)
                val red = eggNode(color, 0.1f)
                red.position = Position(0.42f, 0.88f - wrong * 0.015f, -0.6f)
                oldParent?.addChildNode(red)
                gallowsEgg[0] = red
            }
            if (wrong >= 6) {
                endGame(false)
            }
        }
    }

    private fun updateProgress() {
        val sb = StringBuilder()
        for (ch in word) {
            sb.append(if (guessed.contains(ch)) " $ch " else " _ ")
        }
        statusText.text = sb.toString().trim()
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
    }

    private fun endGame(won: Boolean) {
        if (gameOver) return
        gameOver = true
        stopGame()
        letterGrid?.let { hud.removeView(it) }
        letterGrid = null
        if (!won) {
            gallowsEgg[0]?.let { removeNode(it) }
            gallowsEgg[0] = null
            statusText.text = "La parola era: $word"
            statusText.setTextColor(Color.parseColor("#FF4444"))
        }
        val reward = if (won) 60 else 8
        val label = if (won) "AR Impiccato vinto!" else "AR Impiccato ($word)"
        try {
            finishGame(reward, "$label ($wrong errori)", won, MiniGameManager.GAME_HANGMAN)
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

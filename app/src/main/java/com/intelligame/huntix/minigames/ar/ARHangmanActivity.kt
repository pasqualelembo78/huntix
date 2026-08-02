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
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import java.util.Locale
import kotlin.math.sin

/**
 * 🙈 AR Impiccato — una forca sospesa nella stanza REALE con un'uovo appeso
 * che precipita ad ogni errore. Le lettere della parola sono uova fluttuanti
 * che diventano dorate quando le indovini. Lettere tramite tastiera in HUD.
 *
 * Ad ogni errore: l'uovo cade con animazione (scossa della forca inclusa),
 * esplosione di particelle e suono d'impatto. Quando l'impiccato è completo
 * (6 errori) parte un effetto pirotecnico con fuochi d'artificio colorati.
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
        private val FIRE_COLORS = intArrayOf(
            0xFFFFD700.toInt(), 0xFFFF5252.toInt(), 0xFF40C4FF.toInt(),
            0xFF69F0AE.toInt(), 0xFFFFB300.toInt(), 0xFFE040FB.toInt(),
            0xFFFFFFFF.toInt()
        )
    }

    init {
        showsModeDialog = true
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
        val a = tryArenaByMode()
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        arena = a
        persistArena(a)

        gallowsBase = cubeNode(0xFF8D6E63.toInt(), Float3(0.25f, 0.04f, 0.18f)).apply {
            position = Position(0f, 0.03f, -0.3f)
        }
        gallowsPole = cubeNode(0xFF8D6E63.toInt(), Float3(0.03f, 0.6f, 0.03f)).apply {
            position = Position(0f, 0.35f, -0.3f)
        }
        gallowsArm = cubeNode(0xFF8D6E63.toInt(), Float3(0.22f, 0.03f, 0.03f)).apply {
            position = Position(0.1f, 0.66f, -0.3f)
        }
        gallowsRope = cubeNode(0xFFD9C9A3.toInt(), Float3(0.015f, 0.15f, 0.015f)).apply {
            position = Position(0.2f, 0.55f, -0.3f)
        }
        val hanging = eggNode(0xFFFFFFFF.toInt(), 0.04f).apply {
            position = Position(0.2f, 0.42f, -0.3f)
        }
        gallowsEgg[0] = hanging
        a.addChildNode(gallowsBase!!)
        a.addChildNode(gallowsPole!!)
        a.addChildNode(gallowsArm!!)
        a.addChildNode(gallowsRope!!)
        a.addChildNode(hanging)

        val cell = 0.2f
        val startX = -((word.length - 1) / 2f) * cell
        for (i in word.indices) {
            val egg = eggNode(C_DIM, 0.055f)
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
        if (gameOver || guessed.contains(ch)) return
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
                    burst(gold.worldPosition, C_GOLD, 6)
                }
            }
            updateProgress()
            if (word.all { guessed.contains(it) }) {
                winBurst()
                postDelayed(600) { endGame(true) }
            }
        } else {
            if (wrong >= 6) return
            wrong++
            timerText.text = "Errori: $wrong/6"
            dropEgg()
            if (wrong >= 6) postDelayed(420) { endGame(false) }
        }
    }

    /** Errore: rimpiazza l'uovo con uno rosso, lo fa precipitare con animazione
     *  (ease-in + oscillazione), scuote la forca e fa esplodere particelle. */
    private fun dropEgg() {
        val old = gallowsEgg[0]
        val parent = old?.parent
        if (old != null) removeNode(old)
        val t = wrong / 6f
        val color = Color.rgb(255, (255 * (1 - t)).toInt(), (255 * (1 - t)).toInt())
        val egg = eggNode(color, 0.08f)
        val startY = 0.42f
        val targetY = startY - 0.02f - wrong * 0.045f
        egg.position = Position(0.2f, startY, -0.3f)
        parent?.addChildNode(egg)
        gallowsEgg[0] = egg
        val steps = 8
        for (s in 1..steps) {
            postDelayed(s * 30L) {
                if (gallowsEgg[0] !== egg) return@postDelayed
                val f = s.toFloat() / steps
                val eased = f * f
                egg.position = Position(
                    0.2f + sin(f * 9f) * 0.02f * (1f - f),
                    startY + (targetY - startY) * eased,
                    -0.3f
                )
            }
        }
        postDelayed(steps * 30L) {
            if (gallowsEgg[0] !== egg) return@postDelayed
            val wp = egg.worldPosition
            burst(wp, 0xFFFF5252.toInt(), 14)
            burst(wp, 0xFFFFB300.toInt(), 7)
            spatialAudio.oneShot(140f, 160, decay = true, gain = 0.5f)
            spatialAudio.oneShot(90f, 260, decay = true, gain = 0.45f)
        }
        shakeGallows()
    }

    /** Scuote la forca (paletto + braccio) per dare impatto all'errore. */
    private fun shakeGallows() {
        val pole = gallowsPole ?: return
        val arm = gallowsArm
        val baseX = pole.position.x
        val armBaseX = arm?.position?.x ?: 0f
        val steps = 6
        for (s in 1..steps) {
            postDelayed(s * 40L) {
                if (gallowsPole !== pole) return@postDelayed
                val amp = 0.012f * (1f - s.toFloat() / (steps + 1f))
                val off = sin(s * 2.6f) * amp
                pole.position = Position(baseX + off, pole.position.y, pole.position.z)
                arm?.let { a ->
                    if (gallowsArm === a) {
                        a.position = Position(armBaseX + off * 0.6f, a.position.y, a.position.z)
                    }
                }
            }
        }
        postDelayed(steps * 40L + 20L) {
            if (gallowsPole === pole) pole.position = Position(baseX, pole.position.y, pole.position.z)
            arm?.let { a ->
                if (gallowsArm === a) a.position = Position(armBaseX, a.position.y, a.position.z)
            }
        }
    }

    /** Impiccato completo: fuochi d'artificio colorati attorno all'uovo appeso. */
    private fun playFireworks() {
        val base = gallowsEgg[0]?.worldPosition ?: arena?.worldPosition ?: return
        var delay = 380L
        repeat(8) { i ->
            val color = FIRE_COLORS[i % FIRE_COLORS.size]
            val offset = Float3(
                (Math.random().toFloat() - 0.5f) * 0.9f,
                0.2f + Math.random().toFloat() * 0.9f,
                (Math.random().toFloat() - 0.5f) * 0.6f
            )
            val pos = Float3(base.x + offset.x, base.y + offset.y, base.z + offset.z)
            val d = delay
            postDelayed(d) {
                burst(pos, color, 20)
                burst(pos, Color.WHITE, 6)
                spatialAudio.oneShot(320f + Math.random().toFloat() * 520f, 200, decay = true, gain = 0.4f)
            }
            delay += 300L
        }
        postDelayed(delay) { spatialAudio.oneShot(660f, 320, decay = true, gain = 0.35f) }
    }

    /** Vittoria: scoppio dorato + scintille colorate sulle lettere indovinate. */
    private fun winBurst() {
        val base = gallowsEgg[0]?.worldPosition ?: arena?.worldPosition ?: return
        burst(base, C_GOLD, 16)
        burst(Float3(base.x, base.y + 0.3f, base.z), 0xFF69F0AE.toInt(), 12)
        spatialAudio.oneShot(520f, 300, decay = true, gain = 0.4f)
        postDelayed(300) {
            burst(Float3(base.x, base.y + 0.5f, base.z), 0xFF40C4FF.toInt(), 12)
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
            // L'uovo resta appeso ("impiccato") mentre partono i fuochi d'artificio.
            statusText.text = "La parola era: $word"
            statusText.setTextColor(Color.parseColor("#FF4444"))
            playFireworks()
            postDelayed(3200) {
                if (!gameOver || isDestroyed) return@postDelayed
                finishGame(8, "AR Impiccato ($word) ($wrong errori)", false, MiniGameManager.GAME_HANGMAN, celebrate = false)
            }
        } else {
            try {
                finishGame(60, "AR Impiccato vinto! ($wrong errori)", true, MiniGameManager.GAME_HANGMAN, celebrate = false)
            } catch (e: Exception) { Sentry.captureException(e) }
        }
    }
}

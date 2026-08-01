package com.intelligame.huntix.minigames.ar

import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🎨 AR Simon — 4 grandi uova colorate fluttuano nella stanza REALE:
 * l'arena le lampeggia in sequenza e tu devi ripeterla toccandole.
 * Ogni uovo è un oggetto 3D ancorato nello spazio (ARCore).
 */
class ARSimonActivity : ARGameActivity() {

    companion object {
        private val COLORS = intArrayOf(
            0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFFCA28.toInt()
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val eggs = arrayOfNulls<SphereNode>(4)
    private val nodeMap = HashMap<Node, Int>()
    private val sequence = mutableListOf<Int>()
    private var inputIndex = 0
    private var score = 0
    private var playing = false
    private var gameOver = false

    override fun onGameCreate() {
        sequence.clear()
        sequence.add(Random.nextInt(4))
        inputIndex = 0
        score = 0
        playing = false
        gameOver = false
        nodeMap.clear()
        statusText.text = "Inquadra lo spazio davanti a te… 🎨"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Uova luminose"
        timerText.text = ""
        scoreText.text = "Punti: 0"
        handler.removeCallbacksAndMessages(null)
        startGame()
        whenReady { build() }
    }

    private fun build() {
        val a = spawnAnchor(0.95f, 0f, 0f)
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        val layout = listOf(-0.38f to 0.38f, 0.38f to 0.38f, -0.38f to -0.38f, 0.38f to -0.38f)
        for (i in 0 until 4) {
            val (x, y) = layout[i]
            val node = eggNode(COLORS[i], 0.14f)
            node.position = Position(x, y, 0f)
            a.addChildNode(node)
            eggs[i] = node
            nodeMap[node] = i
        }
        playSequence()
    }

    private fun pulse(i: Int) {
        val n = eggs[i] ?: return
        n.scale = Scale(1.9f, 2.3f, 1.9f)
        handler.postDelayed({ n.scale = Scale(1f, 1.35f, 1f) }, 300)
    }

    private fun playSequence() {
        playing = true
        statusText.text = "Memorizza…"
        val seq = sequence.toList()
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                if (gameOver) return
                if (i >= seq.size) {
                    playing = false
                    inputIndex = 0
                    statusText.text = "Ripeti toccando le uova!"
                    return
                }
                pulse(seq[i])
                i++
                handler.postDelayed(this, 620)
            }
        }
        handler.postDelayed(runnable, 500)
    }

    override fun onNodeTapped(node: Node) {
        if (playing || gameOver || !running) return
        val idx = nodeMap[node] ?: return
        pulse(idx)
        if (idx == sequence[inputIndex]) {
            inputIndex++
            if (inputIndex == sequence.size) {
                score += 10
                scoreText.text = "Punti: $score"
                sequence.add(Random.nextInt(4))
                handler.postDelayed({ playSequence() }, 650)
            }
        } else {
            endGame()
        }
    }

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        stopGame()
        handler.removeCallbacksAndMessages(null)
        val reward = (score / 2).coerceAtLeast(8).coerceAtMost(300)
        try {
            finishGame(reward, "AR Simon ($score pt)", score >= 40, MiniGameManager.GAME_SIMON)
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

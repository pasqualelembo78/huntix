package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🔢 AR Scegli il Numero — 10 uova numerate sospese nella stanza: una contiene
 * il numero segreto. Toccane una per indovinare; ogni errore rompe un uovo e
 * restringe il campo (indizi alto/basso). Hai 3 vite.
 */
class ARNumberPickActivity : ARGameActivity() {

    companion object {
        private const val MAX = 10
        private const val LIVES = 3
        private val C_NEUTRAL = 0xFFA78BFA.toInt()
        private val C_WIN = 0xFF00FF88.toInt()
        private val C_HINT = 0xFFFFD166.toInt()
    }

    private val cells = arrayOfNulls<SphereNode>(MAX)
    private val nodeMap = HashMap<Node, Int>()
    private val values = IntArray(MAX) { it + 1 }
    private var secret = 0
    private var lives = LIVES
    private var gameOver = false

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        secret = Random.nextInt(1, MAX + 1)
        lives = LIVES
        gameOver = false
        nodeMap.clear()
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Uova: $lives"
        timerText.text = "1–$MAX"
        scoreText.text = "Tocca un'uovo"
        updateLevelHud(MiniGameManager.GAME_NUMBER_PICK)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        val step = 0.34f
        val start = -(MAX - 1) * step / 2f
        for (i in 0 until MAX) {
            val node = eggNode(C_NEUTRAL, 0.11f)
            node.position = Position(start + i * step, 0.13f, 0f)
            a.addChildNode(node)
            cells[i] = node
            nodeMap[node] = i
        }
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || !running) return
        val i = nodeMap[node] ?: return
        val v = values[i]
        when {
            v == secret -> {
                gameOver = true
                stopGame()
                statusText.text = "🎉 Indovinato! Era il $secret."
                statusText.setTextColor(android.graphics.Color.parseColor(UiKit.GREEN))
                recolor(node, C_WIN)
                try {
                    finishGame(
                        50, "AR Numero indovinato ($secret)!", true,
                        MiniGameManager.GAME_NUMBER_PICK,
                        accentColors = intArrayOf(C_WIN, 0xFFFFFFFF.toInt(), 0xFFFFD700.toInt())
                    )
                } catch (e: Exception) { Sentry.captureException(e) }
            }
            else -> {
                lives--
                if (v < secret) {
                    statusText.text = "⬇️ $v è troppo basso… il segreto è più ALTO (vite: $lives)"
                } else {
                    statusText.text = "⬆️ $v è troppo alto… il segreto è più BASSO (vite: $lives)"
                }
                livesText.text = "🥚 Uova: $lives"
                burstEgg(node)
                if (lives <= 0) {
                    gameOver = true
                    stopGame()
                    statusText.text = "💀 Niente vite: era il $secret."
                    try {
                        finishGame(10, "AR Numero perso ($secret)", false, MiniGameManager.GAME_NUMBER_PICK)
                    } catch (e: Exception) { Sentry.captureException(e) }
                }
            }
        }
    }

    private fun burstEgg(node: SphereNode) {
        val an = node.parent
        val local = node.position
        val world = node.worldPosition
        eggBreak(an, local, world, radius = 0.09f, big = true)
        an?.removeChildNode(node)
        node.destroy()
    }

    private fun recolor(node: SphereNode, color: Int) {
        val an = node.parent ?: return
        val i = nodeMap[node] ?: return
        val pos = node.position
        nodeMap.remove(node)
        an.removeChildNode(node)
        node.destroy()
        val nn = eggNode(color, 0.11f)
        nn.position = pos
        an.addChildNode(nn)
        cells[i] = nn
        nodeMap[nn] = i
    }
}

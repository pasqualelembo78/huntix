package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🌊 AR Flood — una griglia 14×14 di uova colorate galleggia nella stanza REALE.
 * Tocca un colore per inondare la griglia da un angolo: un solo colore vince,
 * prima che le mosse finiscano.
 */
class ARFloodActivity : ARGameActivity() {

    companion object {
        private const val BOARD = 14
        private const val NUM_COLORS = 6
        private const val CELL = 0.08f
        private val MAX_STEPS = 30 * (BOARD * NUM_COLORS) / (17 * 6)

        private val PALETTE = listOf(
            0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(),
            0xFFFFCA28.toInt(), 0xFFAB47BC.toInt(), 0xFF26C6DA.toInt()
        )
        private const val C_EDGE = 0x55202233.toInt()
    }

    private val board = Array(BOARD) { IntArray(BOARD) }
    private val cellNodes = Array(BOARD) { arrayOfNulls<SphereNode>(BOARD) }
    private val cellColor = Array(BOARD) { IntArray(BOARD) }
    private val nodeCell = HashMap<Node, Int>()
    private val paletteNodes = HashMap<SphereNode, Int>()

    private var arena: AnchorNode? = null
    private var steps = 0
    private var lastColor = 0
    private var score = 0
    private var gameOver = false

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        val r = Random(System.currentTimeMillis())
        for (y in 0 until BOARD) for (x in 0 until BOARD) board[y][x] = r.nextInt(NUM_COLORS)
        steps = 0
        lastColor = board[0][0]
        score = 0
        gameOver = false
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "Mosse: 0 / $MAX_STEPS"
        timerText.text = "Tocca un colore!"
        scoreText.text = "Punti: 0"
        updateLevelHud(MiniGameManager.GAME_FLOOD)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        val hx = (BOARD - 1) / 2f * CELL
        val hy = (BOARD - 1) / 2f * CELL
        val edges = listOf(-hx to -hy, hx to -hy, -hx to hy, hx to hy)
        for ((ex, ey) in edges) {
            val m = eggNode(C_EDGE, 0.02f)
            m.position = Position(ex, 0.05f, ey)
            a.addChildNode(m)
        }
        for (i in 0 until NUM_COLORS) {
            val x = (i - (NUM_COLORS - 1) / 2f) * (CELL * 1.6f)
            val e = eggNode(PALETTE[i], 0.06f).apply {
                position = Position(x, 0.22f, hy + 0.1f)
            }
            a.addChildNode(e)
            paletteNodes[e] = i
        }
        renderBoard()
        statusText.text = "🌊 Tocca un colore per inondare la griglia"
    }

    private fun cellPos(x: Int, y: Int): Pair<Float, Float> {
        val px = (x - (BOARD - 1) / 2f) * CELL
        val py = (y - (BOARD - 1) / 2f) * CELL
        return px to py
    }

    private fun renderBoard() {
        val arenaRef = arena ?: return
        for (y in 0 until BOARD) {
            for (x in 0 until BOARD) {
                val color = PALETTE[board[y][x].coerceIn(0, NUM_COLORS - 1)]
                val old = cellNodes[y][x]
                if (old != null && cellColor[y][x] == color) continue
                old?.let { removeNode(it) }
                val (px, py) = cellPos(x, y)
                val node = eggNode(color, 0.036f).apply {
                    position = Position(px, 0.055f, py)
                    scale = Scale(1f, 0.35f, 1f)
                }
                arenaRef.addChildNode(node)
                cellNodes[y][x] = node
                cellColor[y][x] = color
                nodeCell[node] = y * BOARD + x
            }
        }
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || !running) return
        val cell = nodeCell[node]
        if (cell != null) {
            val y = cell / BOARD
            val x = cell % BOARD
            doColor(board[y][x])
            return
        }
        val pal = paletteNodes[node] ?: return
        doColor(pal)
    }

    private fun doColor(color: Int) {
        if (color == lastColor) {
            timerText.text = "⚠️ Stesso colore!"
            return
        }
        flood(color)
        lastColor = color
        score = (MAX_STEPS - steps) * 10 + if (checkWin()) 100 else 0
        livesText.text = "Mosse: $steps / $MAX_STEPS"
        scoreText.text = "Punti: $score"
        timerText.text = "Ultimo: ${colorName(color)}"
        renderBoard()
        if (checkWin() || steps >= MAX_STEPS) {
            val won = checkWin()
            endGame(won)
        }
    }

    private fun colorName(c: Int): String = when (c) {
        0 -> "rosso"; 1 -> "blu"; 2 -> "verde"; 3 -> "giallo"; 4 -> "viola"; else -> "ciano"
    }

    private fun flood(replacement: Int) {
        val target = board[0][0]
        if (target == replacement) return
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(0 to 0)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (board[y][x] == target) {
                board[y][x] = replacement
                if (x != 0) queue.addLast(x - 1 to y)
                if (x != BOARD - 1) queue.addLast(x + 1 to y)
                if (y != 0) queue.addLast(x to y - 1)
                if (y != BOARD - 1) queue.addLast(x to y + 1)
            }
        }
        steps++
    }

    private fun checkWin(): Boolean {
        val c = board[0][0]
        for (y in 0 until BOARD) for (x in 0 until BOARD) if (board[y][x] != c) return false
        return true
    }

    private fun endGame(won: Boolean) {
        if (gameOver) return
        gameOver = true
        stopGame()
        val reward = (score / 2).coerceAtLeast(10).coerceAtMost(400)
        try {
            finishGame(
                reward,
                if (won) "AR Flood: tutto un colore!" else "AR Flood ($score pt)",
                won,
                MiniGameManager.GAME_FLOOD,
                score = score
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

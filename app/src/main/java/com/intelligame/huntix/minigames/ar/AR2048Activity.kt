package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.log2
import kotlin.random.Random

/**
 * 🧩 AR 2048 — una griglia 4×4 di uova galleggia nella stanza REALE.
 * Scorri per farle fondere: le uova uguali si uniscono in un'uovo più
 * grande e dorato (colore e dimensione crescono col valore).
 */
class AR2048Activity : ARGameActivity() {

    companion object {
        private const val SIZE = 4
        private const val CELL = 0.30f
        private val TILE_COLORS = mapOf(
            2 to 0xFFEAD7A1.toInt(),
            4 to 0xFFE4B978.toInt(),
            8 to 0xFFE88E5A.toInt(),
            16 to 0xFFE95D5D.toInt(),
            32 to 0xFFCE5FA8.toInt(),
            64 to 0xFF8E7CE8.toInt(),
            128 to 0xFF5F9EE9.toInt(),
            256 to 0xFF57D6D9.toInt(),
            512 to 0xFF66E07A.toInt(),
            1024 to 0xFFE6C84D.toInt(),
            2048 to 0xFFFFD700.toInt(),
        )
        private val C_EMPTY = 0x55222233.toInt()
    }

    private val board = IntArray(16)
    private val tiles = HashMap<Int, SphereNode>()
    private val tileColor = HashMap<Int, Int>()
    private val tileRadius = HashMap<Int, Float>()
    private val emptyCells = HashMap<Int, SphereNode>()
    private var score = 0
    private var gameOver = false
    private var arena: AnchorNode? = null

    init {
        // Posizionamento dell'arena (piano/mesh/libero): mostra il dialogo di scelta.
        showsModeDialog = true
    }

    override fun onGameCreate() {
        android.util.Log.d("AR2048Activity", "onGameCreate called")
        board.fill(0)
        tiles.clear()
        tileColor.clear()
        tileRadius.clear()
        emptyCells.clear()
        score = 0
        gameOver = false
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Uova 2048"
        timerText.text = "Max: 2"
        scoreText.text = "Punti: 0"
        updateLevelHud(MiniGameManager.GAME_2048)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        android.util.Log.d("AR2048Activity", "build() called")
        arena = a
        android.util.Log.d("AR2048Activity", "Arena anchor created, spawning cells")
        for (i in 0 until 16) {
            val (x, y) = cellPos(i)
            val e = eggNode(C_EMPTY, 0.13f)
            e.position = Position(x, 0.13f, -y)
            a.addChildNode(e)
            emptyCells[i] = e
        }
        board[Random.nextInt(16)] = if (Random.nextFloat() < 0.9f) 2 else 4
        addRandomTile()
        renderTiles()
        android.util.Log.d("AR2048Activity", "Setup complete, installing input capture")
        installInputCapture(
            onEnd = { dx, dy, _ ->
                if (gameOver || !running) return@installInputCapture
                val d = when {
                    abs(dx) > abs(dy) -> if (dx > 0) 1 else 0
                    else -> if (dy > 0) 3 else 2
                }
                if (move(d)) renderTiles()
            }
        )
    }

    private fun cellPos(i: Int): Pair<Float, Float> {
        val x = (i % SIZE - (SIZE - 1) / 2f) * CELL
        val y = ((SIZE - 1) / 2f - i / SIZE) * CELL
        return x to y
    }

    private fun addRandomTile() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        board[empty[Random.nextInt(empty.size)]] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    private fun mergeLeft(line: IntArray): Pair<IntArray, Int> {
        val nz = line.filter { it != 0 }
        val out = mutableListOf<Int>()
        var pts = 0
        var i = 0
        while (i < nz.size) {
            if (i + 1 < nz.size && nz[i] == nz[i + 1]) {
                val v = nz[i] * 2
                out.add(v); pts += v; i += 2
            } else {
                out.add(nz[i]); i += 1
            }
        }
        while (out.size < SIZE) out.add(0)
        return out.toIntArray() to pts
    }

    private fun move(direction: Int): Boolean {
        val before = board.copyOf()
        for (lineIdx in 0 until SIZE) {
            val line = IntArray(SIZE) { k ->
                when (direction) {
                    0 -> board[lineIdx * SIZE + k]
                    1 -> board[lineIdx * SIZE + (SIZE - 1 - k)]
                    2 -> board[k * SIZE + lineIdx]
                    else -> board[(SIZE - 1 - k) * SIZE + lineIdx]
                }
            }
            val (merged, pts) = mergeLeft(line)
            for (k in 0 until SIZE) {
                when (direction) {
                    0 -> board[lineIdx * SIZE + k] = merged[k]
                    1 -> board[lineIdx * SIZE + k] = merged[SIZE - 1 - k]
                    2 -> board[k * SIZE + lineIdx] = merged[k]
                    else -> board[k * SIZE + lineIdx] = merged[SIZE - 1 - k]
                }
            }
            score += pts
        }
        if (!board.contentEquals(before)) {
            addRandomTile()
            scoreText.text = "Punti: $score"
            timerText.text = "Max: ${board.maxOrNull() ?: 2}"
            if (!hasMoves()) endGame()
            return true
        }
        return false
    }

    private fun hasMoves(): Boolean {
        if (board.any { it == 0 }) return true
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val v = board[r * SIZE + c]
            if (c < SIZE - 1 && board[r * SIZE + c + 1] == v) return true
            if (r < SIZE - 1 && board[(r + 1) * SIZE + c] == v) return true
        }
        return false
    }

    private fun renderTiles() {
        val arenaRef = arena ?: return
        val removed = tiles.keys.filter { board[it] == 0 }
        for (i in removed) {
            tiles[i]?.let { removeNode(it) }
            tiles.remove(i)
            tileColor.remove(i)
            tileRadius.remove(i)
        }
        for (i in 0 until 16) {
            val v = board[i]
            if (v == 0) continue
            val wantColor = TILE_COLORS[v] ?: 0xFFFFD700.toInt()
            val wantRadius = (0.10 + log2(v.toDouble()) * 0.014).toFloat()
            if (tileColor[i] == wantColor && tileRadius[i] == wantRadius) continue
            tiles[i]?.let { removeNode(it) }
            tiles.remove(i)
            val (x, y) = cellPos(i)
            val node = eggNode(wantColor, wantRadius).apply {
                position = Position(x, wantRadius + 0.04f, -y)
                scale = io.github.sceneview.math.Scale(1f, 0.75f, 1f)
            }
            arenaRef.addChildNode(node)
            tiles[i] = node
            tileColor[i] = wantColor
            tileRadius[i] = wantRadius
        }
    }

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        stopGame()
        val reached2048 = board.any { it >= 2048 }
        val reward = (score / 2).coerceAtLeast(10).coerceAtMost(500)
        try {
            finishGame(
                reward,
                if (reached2048) "AR 2048: uovo d'oro!" else "AR 2048 ($score pt)",
                reached2048,
                MiniGameManager.GAME_2048,
                score = score
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

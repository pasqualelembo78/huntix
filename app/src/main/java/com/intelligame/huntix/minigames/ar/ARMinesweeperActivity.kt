package com.intelligame.huntix.minigames.ar

import android.widget.FrameLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 💣 AR Campo Minato — una griglia 9×9 di uova fluttua nella stanza REALE.
 * Tocca per rivelare; le uova colorate indicano le uova adiacenti alla bomba
 * (colori classici). Attiva la bandiera per segnare le mine.
 */
class ARMinesweeperActivity : ARGameActivity() {

    companion object {
        private const val GRID = 9
        private const val MINES = 10
        private const val CELL = 0.20f
        private val C_EMPTY = 0xFF8E7CE8.toInt()
        private val C_FLAG = 0xFFFFD700.toInt()
        private val C_MINE = 0xFFFF5252.toInt()
        private val C_CLEAR = 0xFF6B7280.toInt()
        private val COUNT_COLORS = intArrayOf(
            0xFF6B7280.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFEF5350.toInt(),
            0xFF303F9F.toInt(), 0xFF8D6E63.toInt(), 0xFF00BCD4.toInt(), 0xFF212121.toInt()
        )
    }

    private class Cell(
        var node: SphereNode? = null,
        var revealed: Boolean = false,
        var flagged: Boolean = false
    )

    private val cells = HashMap<Int, Cell>()
    private val nodeMap = HashMap<Node, Int>()
    private val mines = HashSet<Int>()
    private var arena: AnchorNode? = null
    private var gameOver = false
    private var flagMode = false
    private var minesPlaced = false
    private var revealedCount = 0
    private var flagToggle: TextView? = null

    override fun onGameCreate() {
        cells.clear()
        nodeMap.clear()
        mines.clear()
        gameOver = false
        flagMode = false
        minesPlaced = false
        revealedCount = 0
        statusText.text = "Tocca un'uovo per rivelare 💣"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Campo Minato"
        timerText.text = "💣 $MINES"
        scoreText.text = "9×9"
        startGame()
        whenReady { build() }
    }

    private fun build() {
        val a = spawnAnchor(0.95f, 0f, -0.15f)
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        arena = a
        for (i in 0 until GRID * GRID) {
            val (x, y) = cellPos(i)
            val node = eggNode(C_EMPTY, 0.085f)
            node.position = Position(x, y, 0f)
            a.addChildNode(node)
            val cell = Cell(node)
            cells[i] = cell
            nodeMap[node] = i
        }
        buildFlagToggle()
    }

    private fun cellPos(i: Int): Pair<Float, Float> {
        val x = (i % GRID - (GRID - 1) / 2f) * CELL
        val y = ((GRID - 1) / 2f - i / GRID) * CELL
        return x to y
    }

    private fun buildFlagToggle() {
        val ctx = this
        val tv = TextView(ctx).apply {
            text = "🚩 Bandiera: OFF"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
            setBackgroundColor(0x660D0620.toInt())
            setPadding(UiKit.dp(ctx, 12), UiKit.dp(ctx, 6), UiKit.dp(ctx, 12), UiKit.dp(ctx, 6))
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = UiKit.dp(ctx, 64)
            }
            setOnClickListener {
                flagMode = !flagMode
                text = if (flagMode) "🚩 Bandiera: ON" else "🚩 Bandiera: OFF"
            }
        }
        flagToggle = tv
        hud.addView(tv)
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || !running) return
        val idx = nodeMap[node] ?: return
        val cell = cells[idx] ?: return
        if (cell.revealed) return
        if (flagMode) {
            cell.flagged = !cell.flagged
            replaceCellNode(idx, if (cell.flagged) C_FLAG else C_EMPTY, 0.085f, 1.35f)
            return
        }
        if (cell.flagged) return
        if (!minesPlaced) {
            placeMines(idx)
            minesPlaced = true
        }
        if (idx in mines) {
            revealMines()
            endGame(false)
            return
        }
        reveal(idx)
        if (revealedCount >= GRID * GRID - MINES) endGame(true)
    }

    private fun placeMines(exclude: Int) {
        val candidates = (0 until GRID * GRID).filter { it != exclude }
        val chosen = candidates.shuffled(Random).take(MINES)
        mines.addAll(chosen)
    }

    private fun neighbors(i: Int): List<Int> {
        val c = i % GRID
        val r = i / GRID
        return listOf(
            c - 1 to r - 1, c to r - 1, c + 1 to r - 1,
            c - 1 to r, c + 1 to r,
            c - 1 to r + 1, c to r + 1, c + 1 to r + 1
        ).mapNotNull { (x, y) ->
            if (x in 0 until GRID && y in 0 until GRID) y * GRID + x else null
        }
    }

    private fun countMines(i: Int): Int = neighbors(i).count { it in mines }

    private fun reveal(i: Int) {
        val cell = cells[i] ?: return
        if (cell.revealed || cell.flagged) return
        cell.revealed = true
        revealedCount++
        val cnt = countMines(i)
        if (cnt > 0) {
            replaceCellNode(i, COUNT_COLORS[cnt], 0.075f, 1.1f)
        } else {
            replaceCellNode(i, C_CLEAR, 0.07f, 0.4f)
            neighbors(i).forEach { n ->
                if (n !in mines && !(cells[n]?.revealed ?: true)) reveal(n)
            }
        }
    }

    private fun revealMines() {
        mines.forEach { i ->
            val cell = cells[i] ?: return@forEach
            if (cell.flagged) return@forEach
            replaceCellNode(i, C_MINE, 0.085f, 1.35f)
        }
    }

    private fun replaceCellNode(i: Int, color: Int, radius: Float, ry: Float) {
        if (arena == null) return
        val cell = cells[i] ?: return
        val old = cell.node
        val parent = old?.parent
        if (old != null) removeNode(old)
        val (x, y) = cellPos(i)
        val node = eggNode(color, radius)
        node.position = Position(x, y, 0.02f)
        node.scale = io.github.sceneview.math.Scale(1f, ry, 1f)
        parent?.addChildNode(node)
        cell.node = node
        old?.let { nodeMap.remove(it) }
        nodeMap[node] = i
    }

    private fun endGame(won: Boolean) {
        if (gameOver) return
        gameOver = true
        stopGame()
        val reward = if (won) 120 else 8
        val label = if (won) "AR Campo Minato vinto!" else "Boom! 💥"
        try {
            finishGame(reward, "$label ($revealedCount/71)", won, MiniGameManager.GAME_MINESWEEPER)
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

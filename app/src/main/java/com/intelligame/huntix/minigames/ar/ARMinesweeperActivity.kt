package com.intelligame.huntix.minigames.ar

import android.widget.FrameLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 💣 AR Campo Minato — una griglia 9×9 di uova fluttua nella stanza REALE.
 * Tocca per rivelare; le uova colorate indicano le uova adiacenti alla bomba
 * (colori classici). Attiva la bandiera per segnare le mine.
 *
 * Se tocchi una mina: tutte le uovo-mina diventano rosse ed esplodono in una
 * reazione a catena (particelle + suoni), la griglia trema e termina la partita.
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
    private var exploding = false
    private var flagToggle: TextView? = null

    init {
        // Posizionamento dell'arena (piano/mesh/libero): mostra il dialogo di scelta.
        showsModeDialog = true
    }

    override fun onGameCreate() {
        cells.clear()
        nodeMap.clear()
        mines.clear()
        gameOver = false
        flagMode = false
        minesPlaced = false
        revealedCount = 0
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Campo Minato"
        timerText.text = "💣 $MINES"
        scoreText.text = "9×9"
        exploding = false
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        for (i in 0 until GRID * GRID) {
            val (x, y) = cellPos(i)
            val node = eggNode(C_EMPTY, 0.085f)
            node.position = Position(x, 0.085f, -y)
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
        if (gameOver || exploding || !running) return
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
            boom(idx)
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

    /**
     * Mina toccata: reazione a catena. La mina colpita esplode per prima, poi
     * tutte le altre una alla volta (particelle + suoni); gran finale con una
     * grossa esplosione centrale e scossa della griglia, poi fine partita.
     */
    private fun boom(tapped: Int) {
        exploding = true
        val order = listOf(tapped) + mines.filter { it != tapped }.shuffled()
        var delay = 0L
        order.forEach { i ->
            val d = delay
            postDelayed(d) {
                if (!exploding || isDestroyed) return@postDelayed
                explodeCell(i)
            }
            delay += 150L
        }
        postDelayed(delay) {
            if (!exploding || isDestroyed) return@postDelayed
            val center = arena?.worldPosition ?: return@postDelayed
            burst(center, 0xFFFFB300.toInt(), 22)
            burst(Float3(center.x, center.y + 0.3f, center.z), 0xFFFF5252.toInt(), 16)
            spatialAudio.oneShot(90f, 420, decay = true, gain = 0.55f)
            shakeGrid()
            postDelayed(520) {
                if (!exploding || isDestroyed) return@postDelayed
                exploding = false
                endGame(false)
            }
        }
    }

    /** Fa esplodere una singola cella-mina in particelle colorate. */
    private fun explodeCell(i: Int) {
        val cell = cells[i] ?: return
        val node = cell.node
        val wp = node?.worldPosition
        if (node != null) removeNode(node)
        cell.node = null
        nodeMap.remove(node)
        if (wp != null) {
            burst(wp, 0xFFFF5252.toInt(), 14)
            burst(wp, 0xFFFFB300.toInt(), 7)
            spatialAudio.oneShot(140f + Random.nextInt(60).toFloat(), 180, decay = true, gain = 0.45f)
        }
    }

    /** Scuote tutte le celle ancora presenti, poi le riporta in posizione. */
    private fun shakeGrid() {
        val base = HashMap<Int, Position>()
        cells.forEach { (i, c) -> c.node?.let { base[i] = it.position } }
        val steps = 6
        for (s in 1..steps) {
            postDelayed(s * 45L) {
                if (!exploding || isDestroyed) return@postDelayed
                val amp = 0.012f * (1f - s.toFloat() / (steps + 1f))
                base.forEach { (i, p) ->
                    cells[i]?.node?.let { n ->
                        n.position = Position(
                            p.x + sin(s * 2.6f + i * 1.7f) * amp,
                            p.y + cos(s * 2.6f + i * 1.7f) * amp * 0.6f,
                            p.z
                        )
                    }
                }
            }
        }
        postDelayed(steps * 45L + 30L) {
            if (!exploding || isDestroyed) return@postDelayed
            base.forEach { (i, p) -> cells[i]?.node?.let { n -> n.position = p } }
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
        node.position = Position(x, radius + 0.03f, -y)
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
            finishGame(reward, "$label ($revealedCount/71)", won, MiniGameManager.GAME_MINESWEEPER, celebrate = won)
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

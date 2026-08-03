package com.intelligame.huntix.minigames.ar

import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * ⭕ AR Tris — una griglia 3×3 di uova sospesa nella stanza REALE.
 * Tocca un'uovo per giocare (verde), la CPU risponde (viola).
 * Ogni cella è un oggetto 3D ancorato nello spazio via ARCore.
 */
class ARTicTacToeActivity : ARGameActivity() {

    companion object {
        private val C_EMPTY = 0x88999BBB.toInt()
        private val C_X = 0xFF00FF88.toInt()
        private val C_O = 0xFFA78BFA.toInt()
        private const val EMPTY = 0
        private const val PLAYER = 1
        private const val CPU = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private val cells = arrayOfNulls<SphereNode>(9)
    private val nodeMap = HashMap<Node, Int>()
    private val board = IntArray(9)
    private val LINES = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
    )
    private var gameOver = false
    private var cpuThinking = false
    private var cpuCb: Runnable? = null

    init {
        // Posizionamento dell'arena (piano/mesh/libero): mostra il dialogo di scelta.
        showsModeDialog = true
    }

    override fun onGameCreate() {
        board.fill(EMPTY)
        gameOver = false
        cpuThinking = false
        nodeMap.clear()
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Tu = verde"
        timerText.text = "🤖 CPU = viola"
        scoreText.text = "Tris"
        updateLevelHud(MiniGameManager.GAME_TIC_TAC_TOE)
        handler.removeCallbacksAndMessages(null)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        val cell = 0.30f
        for (i in 0 until 9) {
            val x = (i % 3 - 1) * cell
            val y = (1 - i / 3) * cell
            val node = eggNode(C_EMPTY, 0.13f)
            node.position = Position(x, 0.13f, -y)
            a.addChildNode(node)
            cells[i] = node
            nodeMap[node] = i
        }
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || cpuThinking || !running) return
        val i = nodeMap[node] ?: return
        if (board[i] != EMPTY) return
        place(i, PLAYER)
        val w = winner()
        if (w != EMPTY) { endGame(w); return }
        if (board.indices.all { board[it] != EMPTY }) { endGame(EMPTY); return }
        cpuThinking = true
        statusText.text = "🤖 La CPU sta pensando…"
        removeCallback(cpuCb)
        cpuCb = postDelayed(600) {
            if (gameOver) return@postDelayed
            val m = bestCpuMove()
            if (m == -1) { endGame(EMPTY); return@postDelayed }
            place(m, CPU)
            cpuThinking = false
            val w2 = winner()
            when {
                w2 != EMPTY -> endGame(w2)
                board.indices.all { board[it] != EMPTY } -> endGame(EMPTY)
                else -> statusText.text = "Tocca un'uovo per giocare… ⭕"
            }
        }
    }

    private fun place(i: Int, who: Int) {
        board[i] = who
        val node = cells[i] ?: return
        node.scale = io.github.sceneview.math.Scale(1.25f, 1.5f, 1.25f)
        val color = if (who == PLAYER) C_X else C_O
        val newNode = eggNode(color, 0.13f)
        newNode.position = Position(node.position.x, node.position.y + 0.03f, node.position.z)
        nodeMap.remove(node)
        val parent = node.parent
        node.destroy()
        parent?.addChildNode(newNode)
        cells[i] = newNode
        nodeMap[newNode] = i
    }

    private fun winner(): Int {
        for (line in LINES) {
            val v = board[line[0]]
            if (v != EMPTY && board[line[1]] == v && board[line[2]] == v) return v
        }
        return EMPTY
    }

    /** Linea vincente (le 3 uova che hanno chiuso il tris). */
    private fun winningLine(): IntArray? {
        for (line in LINES) {
            val v = board[line[0]]
            if (v != EMPTY && board[line[1]] == v && board[line[2]] == v) return line
        }
        return null
    }

    /** Le 3 uova della linea vincente saltano su e giù (esultanza da stadio). */
    private fun pulseWinningLine(line: IntArray) {
        repeat(4) { p ->
            postDelayed(p * 260L) {
                if (!gameOver) return@postDelayed
                val s = if (p % 2 == 0) 1.5f else 1.15f
                line.forEach { i ->
                    cells[i]?.let {
                        it.scale = io.github.sceneview.math.Scale(s, s * 1.2f, s)
                    }
                }
            }
        }
    }

    private fun findWinningMove(who: Int): Int {
        for (i in 0 until 9) {
            if (board[i] != EMPTY) continue
            board[i] = who
            val w = winner() == who
            board[i] = EMPTY
            if (w) return i
        }
        return -1
    }

    private fun bestCpuMove(): Int {
        val winning = findWinningMove(CPU)
        if (winning != -1) return winning
        val blocking = findWinningMove(PLAYER)
        if (blocking != -1) return blocking
        val empty = board.indices.filter { board[it] == EMPTY }
        if (board[4] == EMPTY) return 4
        val corners = intArrayOf(0, 2, 6, 8).filter { board[it] == EMPTY }
        val cornerChance = 0.7f + 0.3f * MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_TIC_TAC_TOE)
        if (corners.isNotEmpty() && Random.nextFloat() < cornerChance) return corners[Random.nextInt(corners.size)]
        return if (empty.isEmpty()) -1 else empty[Random.nextInt(empty.size)]
    }

    private fun endGame(result: Int) {
        if (gameOver) return
        gameOver = true
        stopGame()
        removeCallback(cpuCb)
        val won = result == PLAYER
        val draw = result == EMPTY
        val reward = when {
            won -> 50
            draw -> 20
            else -> 10
        }
        val label = when {
            won -> "AR Tris vinto!"
            draw -> "AR Tris pari"
            else -> "AR Tris perso"
        }
        if (won) {
            winningLine()?.let { pulseWinningLine(it) }
        }
        try {
            finishGame(
                reward, "$label ($score)", won, MiniGameManager.GAME_TIC_TAC_TOE,
                isDraw = draw,
                accentColors = intArrayOf(C_X, 0xFFFFFFFF.toInt(), 0xFFFFD700.toInt()),
                score = if (won) 1 else 0
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    private val score: String
        get() = if (board.indices.all { board[it] != EMPTY }) "pari" else "${board.count { it == PLAYER }}-${board.count { it == CPU }}"
}

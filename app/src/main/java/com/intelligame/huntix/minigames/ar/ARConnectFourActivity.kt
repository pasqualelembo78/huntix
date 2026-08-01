package com.intelligame.huntix.minigames.ar

import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🔵 AR Forza 4 — 7 colonne sospese nella stanza REALE. Tocca la base di
 * una colonna per far cadere la tua uovo (rossa), la CPU risponde (gialla).
 * Allinea 4 uova per vincere.
 */
class ARConnectFourActivity : ARGameActivity() {

    companion object {
        private const val COLS = 7
        private const val ROWS = 6
        private const val CELL = 0.26f
        private val C_PLAYER = 0xFFFF5A5A.toInt()
        private val C_CPU = 0xFFFFD54F.toInt()
        private val C_BASE = 0x66A78BFA.toInt()
        private val C_SLOT = 0x44FFFFFF.toInt()
        private const val EMPTY = 0
        private const val PLAYER = 1
        private const val CPU = 2
    }

    private val board = Array(COLS) { IntArray(ROWS) }
    private val slotNodes = HashMap<Int, Array<SphereNode?>>()
    private val baseNodes = HashMap<Int, SphereNode>()
    private val baseMap = HashMap<Node, Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var gameOver = false
    private var cpuThinking = false
    private var playerDiscs = 0
    private var cpuDiscs = 0

    override fun onGameCreate() {
        board.forEach { it.fill(EMPTY) }
        gameOver = false
        cpuThinking = false
        playerDiscs = 0
        cpuDiscs = 0
        statusText.text = "Tocca una colonna per far cadere l'uovo 🔵"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Tu = rosso"
        timerText.text = "🤖 CPU = giallo"
        scoreText.text = "0-0"
        startGame()
        whenReady { build() }
    }

    private fun build() {
        val a = spawnAnchor(1.0f, 0f, -0.25f)
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        slotNodes.clear()
        baseNodes.clear()
        baseMap.clear()
        for (col in 0 until COLS) {
            val x = (col - (COLS - 1) / 2f) * CELL
            val slots = Array<SphereNode?>(ROWS) { row ->
                val s = eggNode(C_SLOT, 0.08f)
                s.position = Position(x, -1.35f / 2f + row * CELL, 0f)
                a.addChildNode(s)
                s
            }
            slotNodes[col] = slots
            val base = eggNode(C_BASE, 0.11f).apply {
                scale = io.github.sceneview.math.Scale(1f, 0.35f, 1f)
                position = Position(x, -1.35f / 2f - 0.13f, 0f)
            }
            a.addChildNode(base)
            baseNodes[col] = base
            baseMap[base] = col
        }
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || cpuThinking || !running) return
        val col = baseMap[node] ?: return
        drop(col, PLAYER)
        val winner = checkWin()
        if (winner != EMPTY) { endGame(winner); return }
        if (board.all { c -> c.all { it != EMPTY } }) { endGame(EMPTY); return }

        cpuThinking = true
        statusText.text = "🤖 La CPU sta pensando…"
        handler.postDelayed({
            if (gameOver) return@postDelayed
            val c = bestCpuCol()
            if (c == -1) { endGame(EMPTY); return@postDelayed }
            drop(c, CPU)
            cpuThinking = false
            val w2 = checkWin()
            when {
                w2 != EMPTY -> endGame(w2)
                board.all { col -> col.all { it != EMPTY } } -> endGame(EMPTY)
                else -> statusText.text = "Tocca una colonna 🔵"
            }
        }, 650)
    }

    private fun drop(col: Int, who: Int): Boolean {
        if (col !in 0 until COLS) return false
        val column = board[col]
        val row = column.indexOfLast { it == EMPTY }
        if (row < 0) return false
        column[row] = who
        val color = if (who == PLAYER) C_PLAYER else C_CPU
        val slots = slotNodes[col] ?: return true
        val old = slots[row]
        val parent = old?.parent
        if (old != null) removeNode(old)
        val disc = eggNode(color, 0.1f)
        val x = (col - (COLS - 1) / 2f) * CELL
        disc.position = Position(x, -1.35f / 2f + row * CELL, 0.04f)
        parent?.addChildNode(disc)
        slots[row] = disc
        if (who == PLAYER) playerDiscs++ else cpuDiscs++
        scoreText.text = "$playerDiscs-$cpuDiscs"
        return true
    }

    private fun bestCpuCol(): Int {
        for (c in 0 until COLS) {
            if (hasSpace(c) && winsWith(c, CPU)) return c
        }
        for (c in 0 until COLS) {
            if (hasSpace(c) && winsWith(c, PLAYER)) return c
        }
        val candidates = (0 until COLS).filter { hasSpace(it) }
        return if (candidates.isEmpty()) -1 else candidates[Random.nextInt(candidates.size)]
    }

    private fun hasSpace(col: Int): Boolean {
        return board[col].any { it == EMPTY }
    }

    private fun winsWith(col: Int, who: Int): Boolean {
        val column = board[col]
        val row = column.indexOfLast { it == EMPTY }
        column[row] = who
        val win = checkWin() == who
        column[row] = EMPTY
        return win
    }

    private fun checkWin(): Int {
        for (c in 0 until COLS) {
            for (r in 0 until ROWS) {
                val v = board[c][r]
                if (v == EMPTY) continue
                if (c + 3 < COLS && board[c + 1][r] == v && board[c + 2][r] == v && board[c + 3][r] == v) return v
                if (r + 3 < ROWS && board[c][r + 1] == v && board[c][r + 2] == v && board[c][r + 3] == v) return v
                if (c + 3 < COLS && r + 3 < ROWS && board[c + 1][r + 1] == v && board[c + 2][r + 2] == v && board[c + 3][r + 3] == v) return v
                if (c - 3 >= 0 && r + 3 < ROWS && board[c - 1][r + 1] == v && board[c - 2][r + 2] == v && board[c - 3][r + 3] == v) return v
            }
        }
        return EMPTY
    }

    private fun endGame(result: Int) {
        if (gameOver) return
        gameOver = true
        stopGame()
        handler.removeCallbacksAndMessages(null)
        val won = result == PLAYER
        val draw = result == EMPTY
        val reward = when { won -> 60; draw -> 25; else -> 12 }
        val label = when { won -> "AR Forza 4 vinto!"; draw -> "AR Forza 4 pari"; else -> "AR Forza 4 perso" }
        try {
            finishGame(reward, "$label ($playerDiscs-$cpuDiscs)", won, MiniGameManager.GAME_CONNECT4)
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

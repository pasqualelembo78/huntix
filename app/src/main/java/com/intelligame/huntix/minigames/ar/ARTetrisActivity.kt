package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.sentry.Sentry
import kotlin.math.abs

/**
 * 🧱 AR Tetris — un vero Tetris sospeso nella stanza REALE.
 * I tetramini sono fatti di blocchi 3D che scendono su una lavagna
 * verticale ancorata alla superficie: scorri a destra/sinistra per
 * muovere, su per ruotare, giù per far scendere, tappa per caduta secca.
 */
class ARTetrisActivity : ARGameActivity() {

    companion object {
        private const val COLS = 10
        private const val ROWS = 16
        private const val CELL = 0.14f

        private const val C_BG    = 0x33202233.toInt()
        private const val C_EMPTY = 0x88222344.toInt()

        private class Piece(val color: Int, val cells: Array<IntArray>)

        private fun piece(color: Int, vararg c: IntArray) = Piece(color, arrayOf(*c))

        private val PIECES = listOf(
            piece(0xFF6AD7FF.toInt(), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(2, 1), intArrayOf(3, 1)), // I
            piece(0xFFFFD166.toInt(), intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(1, 1), intArrayOf(2, 1)), // O
            piece(0xFFA78BFA.toInt(), intArrayOf(1, 2), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(2, 1)), // T
            piece(0xFF00FF88.toInt(), intArrayOf(1, 2), intArrayOf(2, 2), intArrayOf(0, 1), intArrayOf(1, 1)), // S
            piece(0xFFFF6B6B.toInt(), intArrayOf(0, 2), intArrayOf(1, 2), intArrayOf(1, 1), intArrayOf(2, 1)), // Z
            piece(0xFF5F9EE9.toInt(), intArrayOf(0, 2), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(2, 1)), // J
            piece(0xFFFFA726.toInt(), intArrayOf(2, 2), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(2, 1))  // L
        )
    }

    private var arena: AnchorNode? = null
    private var yawCos = 1f
    private var yawSin = 0f

    private val grid = Array(ROWS) { IntArray(COLS) }
    private val locked = Array(ROWS) { arrayOfNulls<CubeNode>(COLS) }
    private val pieceNodes = mutableListOf<CubeNode>()
    private val nextNodes = mutableListOf<CubeNode>()
    private val baseNodes = mutableListOf<CubeNode>()

    private var piece = 0
    private var rot = 0
    private var px = 3
    private var py = ROWS - 3
    private var nextPiece = 0
    private var score = 0
    private var lines = 0
    private var level = 1
    private var gameOver = false
    private var acc = 0f
    private var lastNow = 0L

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        for (r in 0 until ROWS) { grid[r].fill(0); locked[r].fill(null) }
        pieceNodes.clear(); nextNodes.clear(); baseNodes.clear()
        score = 0; lines = 0; level = 1
        gameOver = false; acc = 0f
        lastNow = SystemClock.elapsedRealtime()
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "Livello 1"
        timerText.text = "Linee: 0"
        scoreText.text = "Punti: 0"
        updateLevelHud(MiniGameManager.GAME_TETRIS)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        val yaw = yawToFaceCamera(a)
        yawCos = kotlin.math.cos(yaw)
        yawSin = kotlin.math.sin(yaw)

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val (x, y, z) = cellWorld(c, r)
                val base = cubeNode(C_EMPTY, Float3(CELL * 0.96f, CELL * 0.96f, CELL * 0.96f)).apply {
                    scale = io.github.sceneview.math.Scale(1f, 1f, 0.25f)
                    position = Position(x, y, z)
                }
                a.addChildNode(base)
                baseNodes.add(base)
            }
        }

        nextPiece = (Math.random() * PIECES.size).toInt()
        spawnPiece()
        statusText.text = "🧱 Scorri per muovere il tetramino"
        installInputCapture(
            onEnd = { dx, dy, isTap ->
                if (gameOver || !running) return@installInputCapture
                when {
                    isTap -> hardDrop()
                    abs(dx) > abs(dy) -> {
                        if (dx > 0) move(1) else move(-1)
                    }
                    dy < 0 -> rotate()
                    else -> softDrop()
                }
            }
        )
    }

    // ── geometria ────────────────────────────────────────────────

    private fun cellWorld(c: Int, r: Int): Triple<Float, Float, Float> {
        val x = (c - (COLS - 1) / 2f) * CELL
        val y = CELL / 2f + r * CELL
        val (rx, rz) = rotOffset(x, 0f)
        return Triple(rx, y, rz)
    }

    private fun rotOffset(x: Float, z: Float): Pair<Float, Float> =
        (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)

    private fun pieceCells(): List<Pair<Int, Int>> {
        val p = PIECES[piece]
        return p.cells.map { (x, y) ->
            var cx = x; var cy = y
            repeat(rot % 4) {
                val nx = cy
                val ny = 3 - cx
                cx = nx; cy = ny
            }
            (px + cx) to (py + cy)
        }
    }

    private fun fits(cells: List<Pair<Int, Int>>): Boolean =
        cells.all { (c, r) ->
            c in 0 until COLS && r >= 0 && (r >= ROWS || grid[r][c] == 0)
        }

    private fun spawnPiece() {
        piece = nextPiece
        rot = 0
        px = 3
        py = ROWS - 3
        if (!fits(pieceCells())) { endGame(); return }
        placePieceNodes()
        nextPiece = (Math.random() * PIECES.size).toInt()
        renderNext()
    }

    // ── rendering ────────────────────────────────────────────────

    private fun placePieceNodes() {
        for (n in pieceNodes) { removeNode(n) }
        pieceNodes.clear()
        val arenaRef = arena ?: return
        val color = PIECES[piece].color
        for ((c, r) in pieceCells()) {
            val (x, y, z) = cellWorld(c, r)
            val cube = cubeNode(color, Float3(CELL * 0.96f, CELL * 0.96f, CELL * 0.96f)).apply {
                position = Position(x, y, z)
            }
            arenaRef.addChildNode(cube)
            pieceNodes.add(cube)
        }
    }

    private fun renderNext() {
        for (n in nextNodes) { removeNode(n) }
        nextNodes.clear()
        val arenaRef = arena ?: return
        val p = PIECES[nextPiece]
        val (ox, oz) = rotOffset((COLS / 2f + 1f) * CELL, 0f)
        val oy = 3.2f * CELL
        val minX = p.cells.minOf { it[0] }
        val maxX = p.cells.maxOf { it[0] }
        val minY = p.cells.minOf { it[1] }
        val maxY = p.cells.maxOf { it[1] }
        for ((x, y) in p.cells) {
            val cx = (x - (minX + maxX) / 2f)
            val cy = (y - (minY + maxY) / 2f)
            val (rx, rz) = rotOffset(cx * CELL, 0f)
            val cube = cubeNode(p.color, Float3(CELL * 0.8f, CELL * 0.8f, CELL * 0.8f)).apply {
                position = Position(ox + rx, oy + cy * CELL, oz + rz)
            }
            arenaRef.addChildNode(cube)
            nextNodes.add(cube)
        }
    }

    // ── loop ─────────────────────────────────────────────────────

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running || arena == null || gameOver) return
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastNow).coerceAtLeast(0L)
        lastNow = now
        if (dt <= 0) return
        val tickMs = (800L - 70L * (level - 1)).coerceAtLeast(150L)
        acc += dt
        while (acc >= tickMs) {
            acc -= tickMs
            if (!gravityStep()) return
        }
    }

    private fun gravityStep(): Boolean {
        if (move(0, -1)) return true
        lockPiece()
        return !gameOver
    }

    // ── mosse ────────────────────────────────────────────────────

    private fun move(dc: Int): Boolean = move(dc, 0)

    private fun move(dc: Int, dr: Int): Boolean {
        px += dc; py += dr
        if (fits(pieceCells())) {
            placePieceNodes()
            return true
        }
        px -= dc; py -= dr
        return false
    }

    private fun rotate() {
        val old = rot
        rot = (rot + 1) % 4
        if (fits(pieceCells())) { placePieceNodes(); return }
        // Kick: prova a slittare di 1 verso l'interno per non far girare sul muro.
        for (k in intArrayOf(1, -1, 2, -2)) {
            px += k
            if (fits(pieceCells())) { placePieceNodes(); return }
            px -= k
        }
        rot = old
    }

    private fun softDrop() {
        if (!running || gameOver) return
        if (!move(0, -1)) lockPiece()
    }

    private fun hardDrop() {
        if (!running || gameOver) return
        while (move(0, -1)) { score += 2 }
        lockPiece()
    }

    private fun lockPiece() {
        val color = PIECES[piece].color
        val arenaRef = arena ?: return
        for ((c, r) in pieceCells()) {
            if (r !in 0 until ROWS || c !in 0 until COLS) { endGame(); return }
            grid[r][c] = color
            val (x, y, z) = cellWorld(c, r)
            val cube = cubeNode(color, Float3(CELL * 0.96f, CELL * 0.96f, CELL * 0.96f)).apply {
                position = Position(x, y, z)
            }
            arenaRef.addChildNode(cube)
            locked[r][c] = cube
        }
        for (n in pieceNodes) { removeNode(n) }
        pieceNodes.clear()
        clearLines()
        if (gameOver) return
        spawnPiece()
    }

    private fun clearLines() {
        val arenaRef = arena ?: return
        var cleared = 0
        var r = 0
        while (r < ROWS) {
            if (grid[r].all { it != 0 }) {
                cleared++
                val (_, cy, _) = cellWorld(0, r)
                val wp = arenaRef.worldPosition
                val (wrx, wrz) = rotOffset(0f, 0f)
                burst(Float3(wp.x + wrx, wp.y + cy, wp.z + wrz), PIECES[piece].color, 16)
                for (c in 0 until COLS) {
                    locked[r][c]?.let { removeNode(it) }
                    locked[r][c] = null
                }
                for (rr in r + 1 until ROWS) {
                    grid[rr - 1] = grid[rr]
                    locked[rr - 1] = locked[rr]
                    for (c in 0 until COLS) {
                        locked[rr - 1][c]?.let { it.position = cellWorld(c, rr - 1).let { (x, y, z) -> Position(x, y, z) } }
                    }
                }
                grid[ROWS - 1] = IntArray(COLS)
                locked[ROWS - 1] = arrayOfNulls(COLS)
            } else {
                r++
            }
        }
        if (cleared > 0) {
            val points = when (cleared) {
                1 -> 40; 2 -> 100; 3 -> 300; else -> 1200
            } * level
            score += points
            lines += cleared
            level = 1 + lines / 10
            scoreText.text = "Punti: $score"
            livesText.text = "Livello $level"
            timerText.text = "Linee: $lines"
            haptic(true)
        }
    }

    // ── fine ─────────────────────────────────────────────────────

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        stopGame()
        val target = MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_TETRIS)
        val won = score >= target
        val reward = (score / 5).coerceAtLeast(10).coerceAtMost(400)
        try {
            finishGame(
                reward,
                if (won) "AR Tetris: punteggio record!" else "AR Tetris ($score pt)",
                won,
                MiniGameManager.GAME_TETRIS,
                score = score
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

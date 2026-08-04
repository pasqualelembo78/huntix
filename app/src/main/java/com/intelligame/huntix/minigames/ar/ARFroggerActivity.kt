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
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs

/**
 * 🐸 AR Frogger — porta la rana dall'altra parte del fiume in AR.
 * Tronchi che scorrono, camion che passano e una meta dorata in cima:
 * scorri per muovere la rana (in alto = su, in basso = giù).
 */
class ARFroggerActivity : ARGameActivity() {

    companion object {
        private const val H = 12
        private const val COLS = 7
        private const val CELL = 0.17f
        private const val HALF_W = COLS / 2f * CELL

        private const val C_GOAL   = 0xFFFFCA28.toInt()
        private const val C_WATER  = 0xFF0A3D5C.toInt()
        private const val C_ROAD   = 0xFF1A1030.toInt()
        private const val C_GRASS  = 0xFF0E2E18.toInt()
        private const val C_LOG    = 0xFF8D6E63.toInt()
        private const val C_TRUCK  = 0xFFEF5350.toInt()
        private const val C_FROG   = 0xFF66BB6A.toInt()
    }

    private class Entity(
        var x: Float,
        val row: Int,
        val dir: Int,
        var speed: Float,
        val width: Float,
        var node: CubeNode
    )

    private var arena: AnchorNode? = null
    private var yawCos = 1f
    private var yawSin = 0f

    private val logs = mutableListOf<Entity>()
    private val trucks = mutableListOf<Entity>()

    private var frogX = 0f
    private var frogRow = H - 1
    private var frogNode: SphereNode? = null
    private var won = false
    private var dead = false
    private var gameOver = false
    private var time = 0f
    private var lastNow = 0L
    private var logSpeed = 0f
    private var truckSpeed = 0f

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        logs.clear(); trucks.clear()
        frogX = 0f
        frogRow = H - 1
        won = false; dead = false; gameOver = false
        time = 0f
        lastNow = SystemClock.elapsedRealtime()
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_FROGGER)
        logSpeed = 0.13f + 0.11f * diff
        truckSpeed = 0.18f + 0.12f * diff
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🐸 Rana"
        timerText.text = "Tempo: 0.0s"
        scoreText.text = "🏁 Raggiungi la meta"
        updateLevelHud(MiniGameManager.GAME_FROGGER)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        val yaw = yawToFaceCamera(a)
        yawCos = kotlin.math.cos(yaw)
        yawSin = kotlin.math.sin(yaw)

        // Sfondo per ogni riga
        for (row in 0 until H) {
            val laneColor = when (row) {
                0 -> C_GOAL
                in 1..4 -> C_WATER
                in 5..8 -> C_ROAD
                else -> C_GRASS
            }
            for (col in 0 until COLS) {
                val (x, y, z) = cellWorld(col, row)
                val tile = cubeNode(laneColor, Float3(CELL * 0.98f, CELL * 0.5f, CELL * 0.98f)).apply {
                    scale = io.github.sceneview.math.Scale(1f, 1f, 0.3f)
                    position = Position(x, y, z)
                }
                a.addChildNode(tile)
            }
        }

        // Tronchi: 4 righe (1..4)
        var toLeft = true
        for (row in 1..4) {
            var startX = if (toLeft) HALF_W + 0.1f else -HALF_W - 0.1f
            val widths = if (row % 2 == 0) floatArrayOf(3f, 2f, 3f) else floatArrayOf(2f, 3f, 2f)
            for (w in widths) {
                val width = w * CELL
                val n = cubeNode(C_LOG, Float3(width, CELL * 0.5f, CELL * 0.6f)).apply {
                    position = Position(0f, rowY(row), 0f)
                }
                a.addChildNode(n)
                logs.add(Entity(startX, row, if (toLeft) -1 else 1, logSpeed, width, n))
                startX += (if (toLeft) -1 else 1) * (width + CELL * 1.2f)
            }
            toLeft = !toLeft
        }
        // Camion: 4 righe (5..8)
        toLeft = true
        for (row in 5..8) {
            var startX = if (toLeft) HALF_W + 0.1f else -HALF_W - 0.1f
            val widths = if (row % 2 == 0) floatArrayOf(2f, 2f, 2f) else floatArrayOf(3f, 2f)
            for (w in widths) {
                val width = w * CELL
                val n = cubeNode(C_TRUCK, Float3(width, CELL * 0.5f, CELL * 0.55f)).apply {
                    position = Position(0f, rowY(row), 0f)
                }
                a.addChildNode(n)
                trucks.add(Entity(startX, row, if (toLeft) -1 else 1, truckSpeed, width, n))
                startX += (if (toLeft) -1 else 1) * (width + CELL * 1.2f)
            }
            toLeft = !toLeft
        }

        val frog = eggNode(C_FROG, 0.075f)
        a.addChildNode(frog)
        frogNode = frog
        placeFrog()
        statusText.text = "🐸 Scorri per muovere la rana"
        installInputCapture(
            onEnd = { dx, dy, _ ->
                if (gameOver || !running) return@installInputCapture
                if (abs(dx) > abs(dy)) {
                    if (dx > 0) moveFrog(1f, 0) else moveFrog(-1f, 0)
                } else {
                    if (dy < 0) moveFrog(0f, -1) else moveFrog(0f, 1)
                }
            }
        )
    }

    // ── geometria ────────────────────────────────────────────────

    private fun rowY(row: Int): Float = (H - row) * CELL - CELL / 2f

    private fun cellWorld(col: Int, row: Int): Triple<Float, Float, Float> {
        val x = (col - (COLS - 1) / 2f) * CELL
        val y = rowY(row)
        val (rx, rz) = rotOffset(x, 0f)
        return Triple(rx, y, rz)
    }

    private fun worldPos(x: Float, row: Int): Triple<Float, Float, Float> {
        val y = rowY(row)
        val (rx, rz) = rotOffset(x, 0f)
        return Triple(rx, y, rz)
    }

    private fun rotOffset(x: Float, z: Float): Pair<Float, Float> =
        (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)

    // ── mosse ────────────────────────────────────────────────────

    private fun moveFrog(dx: Float, drow: Int) {
        val nx = (frogX + dx * CELL).coerceIn(-HALF_W + 0.08f, HALF_W - 0.08f)
        val nrow = (frogRow + drow).coerceIn(0, H - 1)
        frogX = nx
        frogRow = nrow
        placeFrog()
    }

    private fun placeFrog() {
        val (x, y, z) = worldPos(frogX, frogRow)
        frogNode?.position = Position(x, y, z)
    }

    // ── loop ─────────────────────────────────────────────────────

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running || arena == null || gameOver) return
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastNow).coerceAtLeast(0L) / 1000f).coerceAtMost(0.05f)
        lastNow = now
        if (dt <= 0f) return

        time += dt
        timerText.text = "Tempo: ${"%.1f".format(time)}s"

        stepEntities(logs, dt)
        stepEntities(trucks, dt)

        if (frogRow in 1..4) {
            var onLog = false
            for (l in logs) {
                if (l.row != frogRow) continue
                if (frogX >= l.x && frogX <= l.x + l.width) {
                    onLog = true
                    frogX += l.dir * l.speed * dt
                    if (frogX < -HALF_W) frogX = HALF_W
                    if (frogX > HALF_W) frogX = -HALF_W
                    placeFrog()
                }
            }
            if (!onLog) dead = true
        } else if (frogRow in 5..8) {
            for (t in trucks) {
                if (t.row != frogRow) continue
                if (frogX >= t.x - CELL * 0.15f && frogX <= t.x + t.width + CELL * 0.15f) {
                    dead = true
                }
            }
        } else if (frogRow == 0) {
            won = true
        }

        if (dead || won) endGame(won)
    }

    private fun stepEntities(list: List<Entity>, dt: Float) {
        for (e in list) {
            e.x += e.dir * e.speed * dt
            if (e.dir < 0 && e.x + e.width < -HALF_W - 0.15f) e.x = HALF_W + 0.15f
            if (e.dir > 0 && e.x > HALF_W + 0.15f) e.x = -HALF_W - e.width - 0.15f
            val (x, y, z) = worldPos(e.x, e.row)
            e.node.position = Position(x, y, z)
        }
    }

    // ── fine ─────────────────────────────────────────────────────

    private fun endGame(win: Boolean) {
        if (gameOver) return
        gameOver = true
        stopGame()
        val finalScore = if (win) (60f / time * 100).toInt().coerceAtLeast(100)
        else (time * 10).toInt().coerceAtLeast(1)
        val reward = finalScore.coerceIn(10, 400)
        try {
            finishGame(
                reward,
                if (win) "AR Frogger: rana salva!" else "AR Frogger ($finalScore pt)",
                win,
                MiniGameManager.GAME_FROGGER,
                score = finalScore
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

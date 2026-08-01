package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.sin

/**
 * 🐍 AR Snake — "Caccia alle Uova in Realtà Aumentata".
 *
 * Un serpente fatto di uova luminose striscia dentro la STANZA REALE: ogni
 * segmento è una sfera 3D ancorata nello spazio tramite ARCore e l'intero
 * campo di gioco è un'arena invisibile sospesa davanti alla fotocamera.
 * Guidalo con gli swipe catturando le uova dorate che fluttuano nell'aria:
 * ogni uovo mangiato allunga il serpente e accelera il ritmo.
 */
class ARSnakeActivity : ARGameActivity() {

    companion object {
        private const val COLS = 12
        private const val ROWS = 9
        private const val CELL = 0.24f
        private const val TICK_START = 280L
        private const val MIN_TICK = 150L

        private const val C_GOLD   = 0xFFFFC53D.toInt()
        private const val C_HEAD   = 0xFFF8F8FF.toInt()
        private const val C_GREEN  = 0xFF00FF88.toInt()
        private const val C_PURPLE = 0xFFA78BFA.toInt()
        private const val C_BLUE   = 0xFF6AD7FF.toInt()
        private const val C_MARKER = 0xAA7B5BFF.toInt()
    }

    private var arena: AnchorNode? = null
    private val segments = mutableListOf<SphereNode>()
    private val body = ArrayDeque<Pair<Int, Int>>()
    private var food: SphereNode? = null
    private var foodCell = 0 to 0

    private var dir = 1 to 0
    private var nextDir = 1 to 0
    private var score = 0
    private var tickMs = TICK_START
    private var acc = 0f
    private var lastNow = 0L
    private var phase = 0f

    override fun onGameCreate() {
        score = 0
        tickMs = TICK_START
        acc = 0f
        dir = 1 to 0
        nextDir = 1 to 0
        body.clear()
        segments.clear()
        lastNow = SystemClock.elapsedRealtime()
        statusText.text = "Scorri per guidare il serpente… 🐍"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Caccia alle Uova"
        timerText.text = "Lunghezza: 3"
        scoreText.text = "0 pt"
        buildSwipeLayer()
        startGame()
        whenReady { buildArena() }
    }

    // ── arena e nodi ─────────────────────────────────────────────

    private fun buildArena() {
        val anchor = spawnAnchor(1.1f, 0f, -0.15f) ?: run {
            if (running) postDelayed(400) { buildArena() }
            return
        }
        arena = anchor

        val hx = COLS / 2
        val hy = ROWS / 2
        body.addLast((hx - 2) to hy)
        body.addLast((hx - 1) to hy)
        body.addLast(hx to hy)

        for (i in 0 until 3) {
            val node = eggNode(bodyColor(i), 0.075f)
            anchor.addChildNode(node)
            segments.add(node)
        }

        buildMarkers(anchor)
        placeFood(anchor)
        layoutBody()
    }

    private fun bodyColor(index: Int): Int {
        val n = segments.size.coerceAtLeast(1)
        val t = index.toFloat() / n.toFloat()
        return when {
            index == 0 -> C_HEAD
            t < 0.45f -> C_GREEN
            t < 0.8f -> C_PURPLE
            else -> C_BLUE
        }
    }

    /** Piccole sfere di delimitazione per rendere visibile il campo di gioco. */
    private fun buildMarkers(anchor: AnchorNode) {
        val hx = COLS / 2f * CELL
        val hy = ROWS / 2f * CELL
        val corners = listOf(
            -hx to -hy, hx to -hy, -hx to hy, hx to hy
        )
        for ((x, y) in corners) {
            val m = eggNode(C_MARKER, 0.03f)
            m.position = Position(x, y, 0f)
            anchor.addChildNode(m)
        }
    }

    private fun placeFood(anchor: AnchorNode) {
        val occupied = body.toSet()
        val free = (0 until COLS).flatMap { x -> (0 until ROWS).map { y -> x to y } }
            .filter { it !in occupied }
        if (free.isEmpty()) return
        foodCell = free.random()
        val f = eggNode(C_GOLD, 0.09f)
        val (cx, cy) = cellPos(foodCell)
        f.position = Position(cx, cy, 0f)
        anchor.addChildNode(f)
        food = f
    }

    private fun cellPos(c: Pair<Int, Int>): Pair<Float, Float> {
        val x = (c.first - COLS / 2f) * CELL
        val y = (ROWS / 2f - c.second) * CELL
        return x to y
    }

    // ── loop di gioco ────────────────────────────────────────────

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running || arena == null) return
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastNow).coerceAtLeast(0L)
        lastNow = now
        if (dt > 0) {
            acc += dt
            phase += dt / 140f
            while (acc >= tickMs) {
                acc -= tickMs
                step()
                if (!running) return
            }
        }
        animateNodes()
    }

    private fun step() {
        dir = nextDir
        val head = body.last()
        val nh = (head.first + dir.first) to (head.second + dir.second)
        if (nh.first < 0 || nh.first >= COLS || nh.second < 0 || nh.second >= ROWS) {
            endGame()
            return
        }
        if (nh in body) { endGame(); return }

        body.addLast(nh)
        if (nh == foodCell) {
            score += 10
            scoreText.text = "$score pt"
            timerText.text = "Lunghezza: ${body.size}"
            tickMs = (tickMs - 4).coerceAtLeast(MIN_TICK)
            food?.let { arena?.removeChildNode(it); it.destroy() }
            food = null
            arena?.let { placeFood(it) }
        } else {
            body.removeFirst()
        }

        while (segments.size < body.size) {
            val node = eggNode(bodyColor(segments.size), 0.075f)
            arena?.addChildNode(node)
            segments.add(node)
        }
        layoutBody()
    }

    private fun layoutBody() {
        if (arena == null) return
        for (i in body.indices) {
            if (i >= segments.size) break
            val (x, y) = cellPos(body.elementAt(i))
            val bob = sin(phase + i * 0.6f) * 0.015f
            segments[i].position = Position(x, y, bob)
        }
    }

    private fun animateNodes() {
        val f = food ?: return
        val (fx, fy) = cellPos(foodCell)
        f.position = Position(fx, fy, sin(phase * 1.6f) * 0.04f)
    }

    // ── controlli ────────────────────────────────────────────────

    private fun buildSwipeLayer() {
        installInputCapture(
            onEnd = { dx, dy, _ ->
                val nd = when {
                    abs(dx) > abs(dy) -> if (dx > 0) 1 to 0 else -1 to 0
                    else -> if (dy > 0) 0 to 1 else 0 to -1
                }
                if (nd.first != -dir.first || nd.second != -dir.second) nextDir = nd
            }
        )
    }

    // ── fine ─────────────────────────────────────────────────────

    private fun endGame() {
        if (!running) return
        stopGame()
        val reward = (score * 0.7).toInt().coerceAtLeast(10).coerceAtMost(400)
        try {
            finishGame(reward, "AR Snake ($score pt)", score >= 60, MiniGameManager.GAME_SNAKE)
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

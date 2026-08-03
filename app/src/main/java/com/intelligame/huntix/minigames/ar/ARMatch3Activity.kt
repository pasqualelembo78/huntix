package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Pose
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Scale

class ARMatch3Activity : ARGameActivity() {

    private val N = 9
    private val nodes = arrayOfNulls<AREgg>(N)
    private val grid = IntArray(N) { (0..5).random() }
    private var selected = -1
    private var score = 0
    private var lock = false
    private var round = 0

    /** Secondi a disposizione: meno a livelli alti. */
    private val timeLimit: Int
        get() = (60 - 15 * MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_MATCH3)).toInt().coerceAtLeast(30)

    init {
        // Posizionamento dell'arena (piano/mesh/libero): mostra il dialogo di scelta.
        showsModeDialog = true
    }

    override fun onGameCreate() {
        score = 0; selected = -1; lock = false; round = 0
        nodes.forEach { it?.let { e -> removeEgg(e) } }
        statusText.text = "🔍 Inquadra una superficie piana…"
        updateLevelHud(MiniGameManager.GAME_MATCH3)
        startGame(); startTimer()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        for (i in 0 until N) {
            val col = i % 3; val row = i / 3
            val local = Pose(
                floatArrayOf((col - 1) * 0.3f, 0.095f, -(1 - row) * 0.28f),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val egg = spawnEggAt(a.anchor.pose.compose(local), grid[i], radius = 0.095f)
            egg?.phase = i.toFloat()
            nodes[i] = egg
        }
    }

    private var timerCb: Runnable? = null
    private fun startTimer() {
        removeCallback(timerCb)
        timerCb = postDelayed(1000) {
            if (!running) return@postDelayed
            round++
            timerText.text = "⏱ ${timeLimit - round}s"
            if (round >= timeLimit) endGame() else startTimer()
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || lock || !egg.alive) return
        val i = egg.phase.toInt()
        if (selected == -1) { select(i); return }
        if (selected == i) { deselect(); return }
        val a = selected
        if (isAdjacent(a, i)) trySwap(a, i) else { deselect(); select(i) }
    }

    private fun select(i: Int) {
        selected = i
        nodes[i]?.let { it.node.scale = Scale(1.35f, 1.9f, 1.35f) }
    }
    private fun deselect() {
        nodes[selected]?.let { it.node.scale = Scale(1f, 1.4f, 1f) }
        selected = -1
    }

    private fun isAdjacent(a: Int, b: Int): Boolean {
        val ra = a / 3; val ca = a % 3; val rb = b / 3; val cb = b % 3
        return kotlin.math.abs(ra - rb) + kotlin.math.abs(ca - cb) == 1
    }

    private fun trySwap(a: Int, b: Int) {
        val t = grid[a]; grid[a] = grid[b]; grid[b] = t
        nodes[a]?.let { recolorEgg(it, grid[a]) }
        nodes[b]?.let { recolorEgg(it, grid[b]) }
        deselect()
        val matches = findMatches()
        if (matches.isEmpty()) {
            lock = true
            postDelayed(550) {
                if (!running) return@postDelayed
                val t2 = grid[a]; grid[a] = grid[b]; grid[b] = t2
                nodes[a]?.let { recolorEgg(it, grid[a]) }
                nodes[b]?.let { recolorEgg(it, grid[b]) }
                lock = false
            }
        } else {
            score += matches.size * 20
            updateHud()
            postDelayed(400) { clearAndRefill(matches); cascadeCheck() }
        }
    }

    private fun findMatches(): Set<Int> {
        val m = mutableSetOf<Int>()
        for (r in 0..2) for (c in 0..1) {
            val i = r * 3 + c
            if (grid[i] == grid[i + 1] && grid[i] == grid[i + 2]) { m += i; m += i + 1; m += i + 2 }
        }
        for (c in 0..2) for (r in 0..1) {
            val i = r * 3 + c
            if (grid[i] == grid[i + 3] && grid[i] == grid[i + 6]) { m += i; m += i + 3; m += i + 6 }
        }
        return m
    }

    private fun clearAndRefill(matched: Set<Int>) {
        for (i in matched) {
            grid[i] = (0..5).random()
            nodes[i]?.let { recolorEgg(it, grid[i]) }
        }
    }

    private fun cascadeCheck() {
        val m = findMatches()
        if (m.isNotEmpty()) {
            score += m.size * 20
            updateHud()
            postDelayed(400) { clearAndRefill(m); cascadeCheck() }
        }
    }

    private fun updateHud() {
        livesText.text = "💎 $score"
        scoreText.text = ""
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLimit}s"
    }

    private fun endGame() {
        stopGame(); removeCallback(timerCb)
        finishGame((score * 0.6).toInt().coerceAtMost(400),
            "AR Match 3 ($score pt)", score > 80, MiniGameManager.GAME_MATCH3, score = score)
    }
}

package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🃏 AR Carta Alta — 10 uova nascondono una carta (1–10). Scegline una: è la
 * tua carta. La CPU ne pesca un'altra a caso e vince la più alta.
 */
class ARHighCardActivity : ARGameActivity() {

    companion object {
        private const val MAX = 10
        private val C_HIDDEN = 0xFFA78BFA.toInt()
        private val C_PLAYER = 0xFF00FF88.toInt()
        private val C_CPU = 0xFFEF5350.toInt()
    }

    private val cells = arrayOfNulls<SphereNode>(MAX)
    private val nodeMap = HashMap<Node, Int>()
    private val values = IntArray(MAX) { it + 1 }
    private var playerValue = 0
    private var cpuValue = 0
    private var gameOver = false

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        playerValue = 0
        cpuValue = 0
        gameOver = false
        nodeMap.clear()
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Scegli la tua carta"
        timerText.text = "1–$MAX"
        scoreText.text = "Tocca un'uovo"
        updateLevelHud(MiniGameManager.GAME_HIGH_CARD)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        val step = 0.34f
        val start = -(MAX - 1) * step / 2f
        for (i in 0 until MAX) {
            val node = eggNode(C_HIDDEN, 0.11f)
            node.position = Position(start + i * step, 0.13f, 0f)
            a.addChildNode(node)
            cells[i] = node
            nodeMap[node] = i
        }
    }

    override fun onNodeTapped(node: Node) {
        if (gameOver || !running) return
        val playerIndex = nodeMap[node] ?: return
        gameOver = true
        stopGame()
        playerValue = values[playerIndex]

        val cpuIndex = Random.nextInt(0, MAX - 1).let { if (it >= playerIndex) it + 1 else it }
        cpuValue = values[cpuIndex]

        recolor(node, C_PLAYER, playerIndex)
        cells[cpuIndex]?.let { recolor(it, C_CPU, cpuIndex) }
        cells.indices.forEach { i ->
            if (i != playerIndex && i != cpuIndex) {
                cells[i]?.let { it.parent?.removeChildNode(it); it.destroy() }
                cells[i] = null
            }
        }

        val won = playerValue > cpuValue
        val draw = playerValue == cpuValue
        val label = when {
            won -> "AR Carta Alta vinta! $playerValue vs $cpuValue"
            draw -> "AR Carta Alta pari ($playerValue=$cpuValue)"
            else -> "AR Carta Alta persa ($playerValue vs $cpuValue)"
        }
        statusText.text = label
        statusText.setTextColor(android.graphics.Color.parseColor(if (won) UiKit.GREEN else UiKit.TEXT_DIM))
        try {
            finishGame(
                if (won) 50 else if (draw) 20 else 10,
                label, won, MiniGameManager.GAME_HIGH_CARD,
                isDraw = draw,
                accentColors = intArrayOf(C_PLAYER, 0xFFFFFFFF.toInt(), 0xFFFFD700.toInt())
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    private fun recolor(node: Node, color: Int, index: Int) {
        val an = node.parent ?: return
        val pos = node.position
        nodeMap.remove(node)
        an.removeChildNode(node)
        node.destroy()
        val nn = eggNode(color, 0.11f)
        nn.position = pos
        an.addChildNode(nn)
        cells[index] = nn
        nodeMap[nn] = index
    }
}

package com.intelligame.huntix.minigames.ar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.sudoku.Generator
import com.intelligame.huntix.minigames.sudoku.Grid
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.sentry.Sentry
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🔢 AR Sudoku — una lavagna verticale 9×9 sospesa nella stanza REALE.
 * Tocca una cella per selezionarla, poi scegli il numero dal tastierino 2D
 * in basso. I numeri dati dal puzzle sono viola, i tuoi verdi; i conflitti
 * diventano rossi. Logica di generazione/soluzione: libreria MIT di
 * André Diermann (a11n/sudoku), vedi package minigames.sudoku.
 */
class ARSudokuActivity : ARGameActivity() {

    companion object {
        private const val SIZE = 9
        private const val CELL = 0.12f

        private val C_EMPTY         = 0x88222344.toInt()
        private val C_GIVEN         = 0xFFE0C3FF.toInt()
        private val C_GIVEN_SEL     = 0xFFFFE066.toInt()
        private val C_PLAYER        = 0xFF00FF88.toInt()
        private val C_CONFLICT      = 0xFFEF5350.toInt()
        private val C_SELECT        = 0xFFFFC93C.toInt()
        private val C_SELECT_EMPTY  = 0x66FFC93C.toInt()
    }

    private var arena: AnchorNode? = null
    private var yawCos = 1f
    private var yawSin = 0f

    private val board = Array(SIZE) { IntArray(SIZE) }
    private val given = Array(SIZE) { BooleanArray(SIZE) }
    private val valueNodes = Array(SIZE) { arrayOfNulls<CubeNode>(SIZE) }
    private val baseNodes = mutableListOf<CubeNode>()
    private val nodeMap = HashMap<Node, Int>()

    private var selectedR = -1
    private var selectedC = -1
    private var finished = false
    private var padBar: LinearLayout? = null

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        arena = null
        for (n in baseNodes) { removeNode(n) }
        baseNodes.clear()
        for (r in 0 until SIZE) { board[r].fill(0); given[r].fill(false); valueNodes[r].fill(null) }
        nodeMap.clear()
        selectedR = -1; selectedC = -1
        finished = false
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
        livesText.text = "🔢 Sudoku"
        timerText.text = "9×9"
        scoreText.text = "Dati"
        updateLevelHud(MiniGameManager.GAME_SUDOKU)
        startGame()
        ensurePad()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        val yaw = yawToFaceCamera(a)
        yawCos = cos(yaw)
        yawSin = sin(yaw)

        val difficulty = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_SUDOKU)
        val emptyCount = 28 + (25 * difficulty).toInt()
        val grid = Generator().generate(emptyCount)
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val v = grid.getCell(r, c).value
                board[r][c] = v
                given[r][c] = v != 0
            }
        }

        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val (x, y, z) = cellWorld(c, r)
                val base = cubeNode(C_EMPTY, Float3(CELL * 0.96f, CELL * 0.96f, CELL * 0.96f)).apply {
                    scale = Scale(1f, 1f, 0.25f)
                    position = Position(x, y, z)
                }
                a.addChildNode(base)
                baseNodes.add(base)
                nodeMap[base] = r * SIZE + c
            }
        }
        for (r in 0 until SIZE) for (c in 0 until SIZE) renderCell(r, c)

        val diff = if (difficulty < 0.34f) "Facile" else if (difficulty < 0.67f) "Media" else "Difficile"
        statusText.text = "🧠 $diff • ${81 - emptyCount} numeri dati — tocca una cella, poi un numero"
        haptic(true)
    }

    // ── geometria ────────────────────────────────────────────────

    /** Lavagna verticale centrata davanti all'utente (r=0 in alto). */
    private fun cellWorld(c: Int, r: Int): Triple<Float, Float, Float> {
        val x = (c - (SIZE - 1) / 2f) * CELL
        val y = ((SIZE - 1) / 2f - r) * CELL + 0.5f
        val (rx, rz) = rotOffset(x, 0f)
        return Triple(rx, y, rz)
    }

    private fun rotOffset(x: Float, z: Float): Pair<Float, Float> =
        (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)

    // ── rendering ────────────────────────────────────────────────

    /** Ricrea il cubo di valore della cella in base allo stato corrente. */
    private fun renderCell(r: Int, c: Int) {
        val a = arena ?: return
        val old = valueNodes[r][c]
        if (old != null) { removeNode(old); nodeMap.remove(old) }
        valueNodes[r][c] = null

        val sel = r == selectedR && c == selectedC
        val v = board[r][c]
        val state = when {
            v != 0 && given[r][c] -> (if (sel) C_GIVEN_SEL else C_GIVEN) to 1f
            v != 0 && sel -> C_SELECT to 1.12f
            v != 0 && conflictAt(r, c) -> C_CONFLICT to 1f
            v != 0 -> C_PLAYER to 1f
            sel -> C_SELECT_EMPTY to 1.1f
            else -> null
        }
        val (color, bump) = state ?: return

        val (x, y, z) = cellWorld(c, r)
        val cube = cubeNode(color, Float3(CELL * 0.96f, CELL * 0.96f, CELL * 0.96f)).apply {
            position = Position(x, y, z)
            scale = Scale(bump, bump, bump)
        }
        a.addChildNode(cube)
        valueNodes[r][c] = cube
        nodeMap[cube] = r * SIZE + c
    }

    // ── input ────────────────────────────────────────────────────

    override fun onNodeTapped(node: Node) {
        if (!running || finished) return
        val idx = nodeMap[node] ?: return
        select(idx / SIZE, idx % SIZE)
    }

    private fun select(r: Int, c: Int) {
        selectedR = r; selectedC = c
        if (given[r][c]) {
            statusText.text = "🔒 Cella bloccata dal puzzle"
        } else {
            statusText.text = "Scegli un numero 1-9"
        }
        for (rr in 0 until SIZE) for (cc in 0 until SIZE) renderCell(rr, cc)
    }

    private fun placeNumber(n: Int) {
        if (!running || finished) return
        if (selectedR < 0 || selectedC < 0) {
            statusText.text = "Prima tocca una cella sulla lavagna"
            return
        }
        if (given[selectedR][selectedC]) {
            statusText.text = "🔒 Cella già compilata dal puzzle — scegli una casella vuota"
            return
        }

        board[selectedR][selectedC] = n
        renderCell(selectedR, selectedC)
        if (n == 0) {
            statusText.text = "✕ Cella svuotata"
            haptic(false)
            return
        }

        val grid = Grid.of(board)
        val conflict = !grid.isValidValueForCell(grid.getCell(selectedR, selectedC), n)
        if (conflict) {
            statusText.text = "⚠️ $n è già presente in riga, colonna o riquadro"
            haptic(true)
            return
        }
        if (isCompleteAndValid()) {
            endGame()
        } else {
            statusText.text = "👍 continua così"
            haptic(false)
        }
    }

    private fun conflictAt(r: Int, c: Int): Boolean {
        val v = board[r][c]
        if (v == 0) return false
        for (i in 0 until SIZE) {
            if (i != c && board[r][i] == v) return true
            if (i != r && board[i][c] == v) return true
        }
        val br = r / 3 * 3
        val bc = c / 3 * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            val rr = br + i
            val cc = bc + j
            if ((rr != r || cc != c) && board[rr][cc] == v) return true
        }
        return false
    }

    // ── tastierino 2D ────────────────────────────────────────────

    private fun ensurePad() {
        val existing = padBar
        if (existing != null) {
            existing.visibility = View.VISIBLE
            return
        }
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 8), UiKit.dp(ctx, 10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }
        for (n in 1..9) row.addView(padButton(ctx, n.toString()) { placeNumber(n) })
        row.addView(padButton(ctx, "✕") { placeNumber(0) })
        hud.addView(row)
        padBar = row
    }

    private fun padButton(ctx: Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setBackgroundColor(0xDD0D0620.toInt())
            setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 9), UiKit.dp(ctx, 4), UiKit.dp(ctx, 9))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    // ── fine ─────────────────────────────────────────────────────

    private fun isCompleteAndValid(): Boolean {
        val seen = java.util.HashSet<Int>()
        for (i in 0 until SIZE) {
            seen.clear()
            for (c in 0 until SIZE) if (board[i][c] != 0) { if (!seen.add(board[i][c])) return false }
            seen.clear()
            for (r in 0 until SIZE) if (board[r][i] != 0) { if (!seen.add(board[r][i])) return false }
        }
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                seen.clear()
                for (r in 0 until 3) for (c in 0 until 3) {
                    val v = board[br * 3 + r][bc * 3 + c]
                    if (v != 0) { if (!seen.add(v)) return false }
                }
                if (seen.size != SIZE) return false
            }
        }
        for (r in 0 until SIZE) for (c in 0 until SIZE) if (board[r][c] == 0) return false
        return true
    }

    private fun endGame() {
        if (finished) return
        finished = true
        stopGame()
        padBar?.visibility = View.GONE
        statusText.text = "🎉 AR Sudoku completato!"
        try {
            finishGame(
                120,
                "AR Sudoku: schema completato!",
                true,
                MiniGameManager.GAME_SUDOKU,
                accentColors = intArrayOf(C_GIVEN, C_PLAYER, 0xFFFFD700.toInt()),
                score = 1
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}

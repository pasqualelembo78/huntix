package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.sudoku.Generator
import com.intelligame.huntix.minigames.sudoku.Grid
import com.intelligame.huntix.minigames.sudoku.Solver
import io.sentry.Sentry

/**
 * 🔢 Sudoku — riempi la griglia 9x9 senza ripetizioni in riga, colonna e riquadro.
 * Logica di generazione/soluzione: libreria MIT di André Diermann (a11n/sudoku),
 * vedi package minigames.sudoku.
 */
class SudokuActivity : MiniGameBase() {

    private val SIZE = 9
    private val BOX = 3

    private val board = Array(SIZE) { IntArray(SIZE) }
    private val given = Array(SIZE) { BooleanArray(SIZE) }
    private val cells = Array(SIZE) { arrayOfNulls<TextView>(SIZE) }
    private val moveHistory = mutableListOf<Triple<Int, Int, Int>>()

    private var selectedRow = -1
    private var selectedCol = -1
    private var finished = false
    private var statusText: TextView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Sudoku", "🔢"))
        root.addView(TextView(ctx).apply {
            text = "Riempi 1-9 senza ripetizioni in riga, colonna e riquadro"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 6))
        })
        root.addView(levelBanner(MiniGameManager.GAME_SUDOKU))
        statusText = TextView(ctx).apply {
            text = "Tocca una casella, poi un numero"; textSize = 14f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(statusText!!)

        val grid = GridLayout(ctx).apply {
            columnCount = SIZE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val cell = TextView(ctx).apply {
                    textSize = 18f
                    gravity = android.view.Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    val boxTone = if ((r / BOX + c / BOX) % 2 == 0) "#241642" else "#1A1030"
                    background = GradientDrawable().apply {
                        cornerRadius = 4f
                        setColor(Color.parseColor(boxTone))
                    }
                    val row = r; val col = c
                    setOnClickListener { onCellTap(row, col) }
                }
                cells[r][c] = cell
                grid.addView(
                    cell,
                    GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply {
                        width = 0
                        height = UiKit.dp(ctx, 52)
                        setMargins(UiKit.dp(ctx, 1), UiKit.dp(ctx, 1), UiKit.dp(ctx, 1), UiKit.dp(ctx, 1))
                    }
                )
            }
        }
        root.addView(grid)

        val pad = GridLayout(ctx).apply {
            columnCount = 5
            setPadding(0, UiKit.dp(ctx, 10), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (n in 1..9) {
            val btn = TextView(ctx).apply {
                text = n.toString()
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(UiKit.ACCENT))
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(ctx, 8).toFloat()
                    setColor(Color.parseColor("#2A1B4D"))
                }
                val value = n
                setOnClickListener { onPadTap(value) }
            }
            pad.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = UiKit.dp(ctx, 46)
                    setMargins(UiKit.dp(ctx, 2), UiKit.dp(ctx, 2), UiKit.dp(ctx, 2), UiKit.dp(ctx, 2))
                }
            )
        }
        val erase = TextView(ctx).apply {
            text = "✕"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#EF5350"))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(ctx, 8).toFloat()
                setColor(Color.parseColor("#3A1B2E"))
            }
            setOnClickListener { onPadTap(0) }
        }
        pad.addView(
            erase,
            GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = UiKit.dp(ctx, 46)
                setMargins(UiKit.dp(ctx, 2), UiKit.dp(ctx, 2), UiKit.dp(ctx, 2), UiKit.dp(ctx, 2))
            }
        )
        root.addView(pad)

        root.addView(UiKit.button(ctx, "↩  Annulla", UiKit.TEXT_DIM) {
            undoMove()
        })
        root.addView(UiKit.button(ctx, "🔄  Nuovo Schema", UiKit.ACCENT) {
            overlayContainer?.removeAllViews()
            startGame()
        })

        val scroll = androidx.core.widget.NestedScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor(UiKit.BG))
            addView(root)
            // Safe-area: il contenuto è scrollabile, quindi nulla (pad numerico o
            // pulsante "Nuovo Schema") resta nascosto sotto la barra di navigazione.
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sb.bottom + UiKit.dp(ctx, 8))
                insets
            }
        }

        val wrapper = FrameLayout(ctx)
        wrapper.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)

        startGame()
    }

    private fun startGame() {
        finished = false
        selectedRow = -1; selectedCol = -1

        val difficulty = levelDifficulty(MiniGameManager.GAME_SUDOKU)
        val emptyCount = 28 + (25 * difficulty).toInt()
        val grid = Generator().generate(emptyCount)

        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val v = grid.getCell(r, c).value
                board[r][c] = v
                given[r][c] = v != 0
            }
        }

        statusText?.text = "Schema: ${if (difficulty < 0.34f) "Facile" else if (difficulty < 0.67f) "Media" else "Difficile"} • ${81 - emptyCount} numeri dati"
        refreshBoard()
    }

    private fun onCellTap(row: Int, col: Int) {
        if (finished) return
        selectedRow = row; selectedCol = col
        statusText?.text = if (given[row][col]) "🔒 Cella bloccata dal puzzle" else "Scegli un numero 1-9"
        highlightSelection()
    }

    private fun onPadTap(value: Int) {
        if (finished || selectedRow < 0) {
            statusText?.text = "Prima tocca una casella"
            return
        }
        if (given[selectedRow][selectedCol]) {
            statusText?.text = "🔒 Cella già compilata dal puzzle — scegli una casella vuota"
            return
        }

        board[selectedRow][selectedCol] = value
        val cellView = cells[selectedRow][selectedCol]!!
        if (value == 0) {
            cellView.setTextColor(Color.WHITE)
            refreshBoard()
            return
        }
        moveHistory.add(Triple(selectedRow, selectedCol, board[selectedRow][selectedCol]))

        val grid = Grid.of(board)
        val conflict = !grid.isValidValueForCell(grid.getCell(selectedRow, selectedCol), value)
        cellView.text = value.toString()
        cellView.setTextColor(if (conflict) Color.parseColor("#EF5350") else Color.WHITE)
        if (conflict) {
            statusText?.text = "⚠️ ${value} è già presente in riga, colonna o riquadro"
            return
        }

        if (isCompleteAndValid()) {
            finishGame()
        } else {
            statusText?.text = "👍 continua così"
            highlightSelection()
        }
    }

    private fun refreshBoard() {
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val cell = cells[r][c]!!
                val v = board[r][c]
                cell.text = if (v == 0) "" else v.toString()
                cell.setTextColor(if (given[r][c]) Color.parseColor("#E0C3FF") else Color.WHITE)
                cell.typeface = if (given[r][c]) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
        highlightSelection()
    }

    private fun highlightSelection() {
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                val cell = cells[r][c]!!
                val selected = r == selectedRow && c == selectedCol
                val sameGroup = selectedRow >= 0 && (
                    r == selectedRow || c == selectedCol ||
                        (r / BOX == selectedRow / BOX && c / BOX == selectedCol / BOX)
                    )
                val boxTone = if ((r / BOX + c / BOX) % 2 == 0) "#241642" else "#1A1030"
                cell.background = GradientDrawable().apply {
                    cornerRadius = 4f
                    setColor(
                        when {
                            selected -> Color.parseColor(UiKit.ACCENT)
                            sameGroup -> Color.parseColor("#2E2150")
                            else -> Color.parseColor(boxTone)
                        }
                    )
                }
            }
        }
    }

    private fun undoMove() {
        if (moveHistory.isEmpty() || finished) return
        val (r, c, prev) = moveHistory.removeAt(moveHistory.size - 1)
        board[r][c] = prev
        refreshBoard()
        statusText?.text = "Cella annullata"
    }

    private fun isCompleteAndValid(): Boolean {
        val seen = java.util.HashSet<Int>()
        for (i in 0 until SIZE) {
            seen.clear()
            for (c in 0 until SIZE) if (board[i][c] != 0) { if (!seen.add(board[i][c])) return false }
            seen.clear()
            for (r in 0 until SIZE) if (board[r][i] != 0) { if (!seen.add(board[r][i])) return false }
        }
        for (br in 0 until BOX) {
            for (bc in 0 until BOX) {
                seen.clear()
                for (r in 0 until BOX) for (c in 0 until BOX) {
                    val v = board[br * BOX + r][bc * BOX + c]
                    if (v != 0) { if (!seen.add(v)) return false }
                }
                if (seen.size != SIZE) return false
            }
        }
        for (r in 0 until SIZE) for (c in 0 until SIZE) if (board[r][c] == 0) return false
        return true
    }

    private fun finishGame() {
        finished = true
        statusText?.text = "🎉 Sudoku completato!"
        val lr = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_SUDOKU,
                score = 1,
                mvc = 60, xp = 20,
                label = "Sudoku: schema completato!",
                isWin = true
            )
        } catch (e: Exception) { Sentry.captureException(e); null }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply {
            text = "🏆"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Schema completato!"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        lr?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${lr?.mvc ?: 60} MVC  •  +${lr?.xp ?: 20} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Nuovo Schema", UiKit.ACCENT) {
            overlayContainer?.removeAllViews()
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }
}

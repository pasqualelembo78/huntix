package com.example.minesweeper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast

class MinesweeperActivity : AppCompatActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private lateinit var grid: GridLayout
    private lateinit var statusText: TextView

    private val rows = 9
    private val cols = 9
    private val mines = 10
    private lateinit var board: Array<Array<Cell>>
    private var cellsLeft = rows * cols - mines
    private var isGameOver = false
    private var score = 0
    private var gameId = "game_minesweeper"

    data class Cell(var row: Int, var col: Int, var isMine: Boolean = false, var isRevealed: Boolean = false, var isFlagged: Boolean = false, var adjacentMines: Int = 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_minesweeper")
        }

        val container = android.widget.LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        statusText = TextView(this)
        statusText.text = "Clicca per iniziare"
        container.addView(statusText)

        grid = GridLayout(this)
        grid.columnCount = cols
        grid.rowCount = rows

        container.addView(grid)
        setContentView(container)

        initBoard()
    }

    private fun initBoard() {
        board = Array(rows) { Array(cols) { Cell(0, 0) } }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                board[r][c] = Cell(r, c)
            }
        }

        for (i in 0 until mines) {
            var r: Int
            var c: Int
            do {
                r = (0 until rows).random()
                c = (0 until cols).random()
            } while (board[r][c].isMine)
            board[r][c].isMine = true
        }

        countAdjacentMines()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cellView = MineCellView(this, board[r][c]) { cell, isLongPress ->
                    onCellClick(cell, isLongPress)
                }
                cellView.layoutParams = GridLayout.LayoutParams().apply {
                    width = 40
                    height = 40
                    setMargins(2, 2, 2, 2)
                }
                grid.addView(cellView)
            }
        }
    }

    private fun countAdjacentMines() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols && board[nr][nc].isMine) {
                            count++
                        }
                    }
                }
                board[r][c].adjacentMines = count
            }
        }
    }

    private fun onCellClick(cell: Cell, isLongPress: Boolean) {
        if (isGameOver) return

        if (isLongPress) {
            cell.isFlagged = !cell.isFlagged
            val idx = cell.row * cols + cell.col
            (grid.getChildAt(idx) as MineCellView).invalidate()
        } else {
            if (cell.isFlagged) return
            revealCell(cell)
        }
    }

    private fun revealCell(cell: Cell) {
        if (cell.isRevealed || cell.isFlagged) return

        cell.isRevealed = true
        val idx = cell.row * cols + cell.col
        (grid.getChildAt(idx) as MineCellView).invalidate()

        if (cell.isMine) {
            gameOver(false)
            return
        }

        cellsLeft--
        if (cellsLeft == 0) {
            gameOver(true)
        } else if (cell.adjacentMines == 0) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val nr = cell.row + dr
                    val nc = cell.col + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        revealCell(board[nr][nc])
                    }
                }
            }
        }
    }

    private fun gameOver(won: Boolean) {
        isGameOver = true
        if (won) {
            score = cellsLeft * 10 + mines * 5
            val reward = gameManager.applyReward(gameId, score)
            Toast.makeText(this, "VINTO! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
        } else {
            score = 0
            Toast.makeText(this, "Mina colpita! Hai perso.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

class MineCellView(context: android.content.Context, private val cell: com.example.minesweeper.MinesweeperActivity.Cell, private val listener: (com.example.minesweeper.MinesweeperActivity.Cell, Boolean) -> Unit) :
    androidx.appcompat.widget.AppCompatButton(context) {

    init {
        setOnClickListener {
            listener(cell, false)
        }
        setOnLongClickListener {
            listener(cell, true)
            true
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas?) {
        super.onDraw(canvas)
        text = when {
            cell.isFlagged -> "🚩"
            !cell.isRevealed -> ""
            cell.isMine -> "💣"
            cell.adjacentMines > 0 -> cell.adjacentMines.toString()
            else -> ""
        }

        setTextColor(android.graphics.Color.BLACK)
        val numberColors = mapOf(
            1 to android.graphics.Color.BLUE,
            2 to android.graphics.Color.GREEN,
            3 to android.graphics.Color.RED,
            4 to android.graphics.Color.CYAN,
            5 to android.graphics.Color.MAGENTA
        )
        numberColors[cell.adjacentMines]?.let { setTextColor(it) }
    }
}
package com.example.connectfour

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class ConnectFourActivity : AppCompatActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private lateinit var grid: GridLayout
    private lateinit var statusText: TextView

    private val rows = 6
    private val cols = 7
    private val board = Array(rows) { Array(cols) { 0 } }
    private var currentPlayer = 1 // 1=utente, 2=CPU
    private var isGameActive = true
    private var score = 0
    private var gameId = "game_connect4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_connect4")
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        statusText = TextView(this)
        statusText.text = "Clicca una colonna per iniziare"
        container.addView(statusText)

        grid = GridLayout(this)
        grid.columnCount = cols
        grid.rowCount = rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = CellView(this, r, c, ::onCellClick)
                cell.layoutParams = GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    setMargins(4, 4, 4, 4)
                }
                grid.addView(cell)
            }
        }

        container.addView(grid)
        setContentView(container)
    }

    private fun onCellClick(row: Int, col: Int) {
        if (!isGameActive) return

        val placedRow = dropInColumn(col, currentPlayer)
        if (placedRow == -1) return

        if (checkWin(placedRow, col, currentPlayer)) {
            endGame(currentPlayer)
            return
        }

        currentPlayer = 3 - currentPlayer // Switch 1->2, 2->1
        statusText.text = if (currentPlayer == 1) "Tocca una colonna" else "CPU sta giocando..."

        if (currentPlayer == 2) {
            grid.postDelayed({
                val bestMove = findBestMove()
                dropInColumn(bestMove, 2)
                val newRow = getTopEmptyRow(bestMove)
                if (checkWin(newRow, bestMove, 2)) {
                    endGame(2)
                } else {
                    currentPlayer = 1
                    statusText.text = "Tocca una colonna"
                }
            }, 500)
        }
    }

    private fun dropInColumn(col: Int, player: Int): Int {
        for (r in rows - 1 downTo 0) {
            if (board[r][col] == 0) {
                board[r][col] = player
                updateCell(r, col)
                return r
            }
        }
        return -1
    }

    private fun getTopEmptyRow(col: Int): Int {
        for (r in rows - 1 downTo 0) {
            if (board[r][col] == 0) return r
        }
        return -1
    }

    private fun updateCell(row: Int, col: Int) {
        val idx = row * cols + col
        val cellView = grid.getChildAt(idx) as CellView
        cellView.setPlayer(board[row][col])
        cellView.invalidate()
    }

    private fun checkWin(row: Int, col: Int, player: Int): Boolean {
        return checkDirection(row, col, 0, 1, player, 4) ||  // Orizzontale
               checkDirection(row, col, 1, 0, player, 4) ||  // Verticale
               checkDirection(row, col, 1, 1, player, 4) ||  // Diagonale /
               checkDirection(row, col, 1, -1, player, 4)   // Diagonale \
    }

    private fun checkDirection(row: Int, col: Int, dr: Int, dc: Int, player: Int, length: Int): Boolean {
        var count = 1

        var r = row + dr
        var c = col + dc
        while (r in 0 until rows && c in 0 until cols && board[r][c] == player) {
            count++
            r += dr
            c += dc
        }

        r = row - dr
        c = col - dc
        while (r in 0 until rows && c in 0 until cols && board[r][c] == player) {
            count++
            r -= dr
            c -= dc
        }

        return count >= length
    }

    private fun findBestMove(): Int {
        val emptyCols = (0 until cols).filter { col ->
            board[0][col] == 0
        }
        return emptyCols.random()
    }

    private fun endGame(winner: Int) {
        isGameActive = false
        score = winner * 50
        val reward = gameManager.applyReward(gameId, score)
        val msg = if (winner == 1) "Hai vinto! XP: ${reward.xpEarned}" else "CPU ha vinto! XP: ${reward.xpEarned}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}

class CellView(
    context: android.content.Context,
    private val row: Int,
    private val col: Int,
    private val listener: (Int, Int) -> Unit
) : androidx.appcompat.widget.AppCompatButton(context) {

    private var player = 0

    init {
        setOnClickListener { listener(row, col) }
    }

    fun setPlayer(p: Int) {
        player = p
    }

    override fun onDraw(canvas: android.graphics.Canvas?) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint()
        when (player) {
            1 -> paint.color = android.graphics.Color.RED
            2 -> paint.color = android.graphics.Color.YELLOW
            else -> return
        }
        canvas?.drawColor(paint.color)
    }
}
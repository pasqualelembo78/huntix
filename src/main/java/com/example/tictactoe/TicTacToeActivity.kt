package com.example.tictactoe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class TicTacToeActivity : AppCompatActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private lateinit var boardView: GridLayout
    private lateinit var statusText: TextView

    private val board = Array(3) { Array(3) { 0 } } // 0=empty, 1=X, 2=O
    private var currentPlayer = 1 // 1=utente, 2=CPU
    private var isGameActive = true
    private var score = 0
    private var gameId = "game_tic_tac_toe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_tic_tac_toe")
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        statusText = TextView(this)
        statusText.textSize = 20f
        statusText.text = "Tocca una cella per iniziare"
        container.addView(statusText)

        boardView = GridLayout(this)
        boardView.columnCount = 3
        boardView.rowCount = 3

        for (i in 0..2) {
            for (j in 0..2) {
                val cell = TicTacToeCell(this, i, j, ::onCellClick)
                cell.layoutParams = GridLayout.LayoutParams().apply {
                    width = 120
                    height = 120
                    setMargins(4, 4, 4, 4)
                }
                boardView.addView(cell)
            }
        }

        container.addView(boardView)
        setContentView(container)
    }

    private fun onCellClick(row: Int, col: Int) {
        if (!isGameActive || board[row][col] != 0) return

        makeMove(row, col, 1)

        if (checkWin(1)) {
            endGame(1)
            return
        }

        if (isBoardFull()) {
            endGame(0) // Pareggio
            return
        }

        // CPU move
        currentPlayer = 2
        statusText.text = "CPU sta pensando..."

        // Piccolo delay per simulare CPU
        boardView.postDelayed({
            val bestMove = minimax(2)
            if (bestMove.first != -1) {
                makeMove(bestMove.second, bestMove.third, 2)
            }

            if (checkWin(2)) {
                endGame(2)
            } else if (isBoardFull()) {
                endGame(0)
            } else {
                currentPlayer = 1
                statusText.text = "Tocca una cella"
            }
        }, 300)
    }

    private fun makeMove(row: Int, col: Int, player: Int) {
        board[row][col] = player
        (boardView.getChildAt(row * 3 + col) as TicTacToeCell).setValue(player)

        if (player == 1) {
            spatialAudioPlaceholder("X")
        }
    }

    private fun checkWin(player: Int): Boolean {
        for (i in 0..2) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) return true
            if (board[0][i] == player && board[1][i] == player && board[2][i] == player) return true
        }
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) return true
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) return true
        return false
    }

    private fun isBoardFull(): Boolean {
        return (0..2).all { i -> (0..2).all { j -> board[i][j] != 0 } }
    }

    private fun minimax(depth: Int): Triple<Int, Int, Int> {
        // Implementazione semplificata: random se nulla disponibile
        val empty = mutableListOf<Pair<Int, Int>>()
        for (i in 0..2) {
            for (j in 0..2) {
                if (board[i][j] == 0) empty.add(Pair(i, j))
            }
        }
        if (empty.isNotEmpty()) {
            val move = empty.random()
            return Triple(depth, move.first, move.second)
        }
        return Triple(-1, -1, -1)
    }

    private fun endGame(winner: Int) {
        isGameActive = false
        val reward = gameManager.applyReward(gameId, if (winner == 1) 100 else if (winner == 0) 50 else 0)

        when (winner) {
            1 -> {
                score = 100 - countMoves() * 10
                Toast.makeText(this, "Hai vinto! XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            }
            2 -> {
                score = 0
                Toast.makeText(this, "Hai perso! XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            }
            0 -> {
                score = 50
                Toast.makeText(this, "Pareggio! XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun countMoves(): Int {
        var count = 0
        for (i in 0..2) for (j in 0..2) if (board[i][j] != 0) count++
        return count
    }

    private fun spatialAudioPlaceholder(symbol: String) {
        // Placeholder audio per similitudine AR
    }
}

class TicTacToeCell(
    context: android.content.Context,
    private val row: Int,
    private val col: Int,
    private val listener: (Int, Int) -> Unit
) : androidx.appcompat.widget.AppCompatButton(context) {

    init {
        textSize = 40f
        setOnClickListener { listener(row, col) }
    }

    fun setValue(player: Int) {
        text = when (player) {
            1 -> "X"
            2 -> "O"
            else -> ""
        }
        isEnabled = false
    }
}
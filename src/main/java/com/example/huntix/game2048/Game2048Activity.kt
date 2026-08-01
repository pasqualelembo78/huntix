package com.example.huntix.game2048

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import kotlin.math.min

class Game2048Activity : AppCompatActivity() {

    private lateinit var grid: GridLayout
    private lateinit var scoreText: TextView
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private lateinit var gestureDetector: GestureDetector

    private val size = 4
    private val board = Array(size) { Array(size) { 0 } }
    private var score = 0
    private var gameId = "game_2048"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_2048")
        }

        grid = GridLayout(this).apply {
            columnCount = size
            rowCount = size
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        scoreText = TextView(this)
        scoreText.text = "Score: 0"
        scoreText.textSize = 20f

        val container = FrameLayout(this).apply {
            addView(scoreText)
            addView(grid)
        }

        setContentView(container)

        gestureDetector = GestureDetector(this, SwipeGestureDetector())

        initBoard()
        startGame()
    }

    private fun initBoard() {
        for (i in 0 until size) {
            for (j in 0 until size) {
                val cell = TileView(this)
                cell.value = 0
                grid.addView(cell)
            }
        }
    }

    private fun startGame() {
        score = 0
        for (i in 0 until size) {
            for (j in 0 until size) {
                board[i][j] = 0
            }
        }
        addRandomTile()
        addRandomTile()
        updateUI()
    }

    private fun addRandomTile(): Boolean {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (board[i][j] == 0) emptyCells.add(Pair(i, j))
            }
        }
        if (emptyCells.isEmpty()) return false

        val randomCell = emptyCells.random()
        board[randomCell.first][randomCell.second] = if (Math.random() < 0.9) 2 else 4
        return true
    }

    private fun move(direction: Int): Boolean {
        val originalBoard = board.map { it.copyOf() }

        when (direction) {
            0 -> moveLeft()    // Left
            1 -> moveUp()      // Up
            2 -> moveRight()   // Right
            3 -> moveDown()    // Down
        }

        if (board.any { it.any { cell -> cell > 0 } }) {
            val moved = !originalBoard.contentDeepEquals(board)
            if (moved) {
                addRandomTile()
                updateUI()
                checkGameOver()
            }
            return moved
        }
        return false
    }

    private fun moveLeft() {
        for (i in 0 until size) {
            val newRow = arrayOf(0, 0, 0, 0)
            var pos = 0
            var prev = 0

            for (j in 0 until size) {
                val v = board[i][j]
                if (v != 0) {
                    if (prev == v) {
                        newRow[pos - 1] *= 2
                        score += newRow[pos - 1]
                        prev = 0
                    } else {
                        newRow[pos] = v
                        prev = v
                        pos++
                    }
                }
            }
            board[i] = newRow
        }
    }

    private fun moveRight() {
        flipHorizontal()
        moveLeft()
        flipHorizontal()
    }

    private fun moveUp() {
        transpose()
        moveLeft()
        transpose()
    }

    private fun moveDown() {
        transpose()
        moveRight()
        transpose()
    }

    private fun transpose() {
        for (i in 0 until size) {
            for (j in i + 1 until size) {
                val temp = board[i][j]
                board[i][j] = board[j][i]
                board[j][i] = temp
            }
        }
    }

    private fun flipHorizontal() {
        for (i in 0 until size) {
            for j in 0 until size / 2 {
                val temp = board[i][j]
                board[i][j] = board[i][size - 1 - j]
                board[i][size - 1 - j] = temp
            }
        }
    }

    private fun updateUI() {
        scoreText.text = "Score: $score"
        var idx = 0
        for (i in 0 until size) {
            for (j in 0 until size) {
                val tile = grid.getChildAt(idx++) as TileView
                tile.value = board[i][j]
                tile.setText(tile.value.toString())
            }
        }
    }

    private fun checkGameOver() {
        var hasEmpty = false
        var canMerge = false

        for (i in 0 until size) {
            for (j in 0 until size) {
                if (board[i][j] == 0) hasEmpty = true
                if (!canMerge) {
                    val v = board[i][j]
                    if (j + 1 < size && board[i][j + 1] == v) canMerge = true
                    if (i + 1 < size && board[i + 1][j] == v) canMerge = true
                }
            }
        }

        if (!hasEmpty && !canMerge) {
            gameOver()
        }
    }

    private fun gameOver() {
        val reward = gameManager.applyReward(gameId, score)
        Toast.makeText(this, "Game Over! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()

        val resultIntent = intent
        resultIntent.putExtra("final_score", score)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    inner class SwipeGestureDetector : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y

            if (min(kotlin.math.abs(dx), kotlin.math.abs(dy)) > 100) {
                val direction = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (dx > 0) 2 else 0  // Right/Left
                } else {
                    if (dy > 0) 3 else 1  // Down/Up
                }
                if (move(direction)) {
                    return true
                }
            }
            return false
        }
    }
}

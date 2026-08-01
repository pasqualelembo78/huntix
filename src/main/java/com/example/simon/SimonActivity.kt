package com.example.simon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class SimonActivity : AppCompatActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private lateinit var statusText: TextView
    private lateinit var btnRed: Button
    private lateinit var btnGreen: Button
    private lateinit var btnYellow: Button
    private lateinit var btnBlue: Button

    private val colors = listOf(0, 1, 2, 3) // 0=red,1=green,2=yellow,3=blue
    private var sequence = mutableListOf<Int>()
    private var playerIndex = 0
    private var isPlaying = false
    private var score = 0
    private var gameId = "game_simon"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_simon")
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        statusText = TextView(this)
        statusText.textSize = 24f
        statusText.text = "Clicca per iniziare"
        container.addView(statusText)

        val grid = GridLayout(this)
        grid.columnCount = 2
        grid.rowCount = 2
        grid.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        btnRed = createButton(0xFFE53935.toInt(), 0, grid)
        btnGreen = createButton(0xFF4CAF50.toInt(), 1, grid)
        btnYellow = createButton(0xFFFFEB3B.toInt(), 2, grid)
        btnBlue = createButton(0xFF2196F3.toInt(), 3, grid)

        container.addView(grid)
        setContentView(container)

        container.setOnClickListener {
            if (!isPlaying) startGame()
        }

        btnRed.setOnClickListener { onColorClick(0) }
        btnGreen.setOnClickListener { onColorClick(1) }
        btnYellow.setOnClickListener { onColorClick(2) }
        btnBlue.setOnClickListener { onColorClick(3) }
    }

    private fun createButton(color: Int, id: Int, grid: GridLayout): Button {
        val btn = Button(this)
        btn.setBackgroundColor(color)
        btn.layoutParams = GridLayout.LayoutParams().apply {
            width = 200
            height = 200
            setMargins(16, 16, 16, 16)
        }
        return btn
    }

    private fun startGame() {
        sequence.clear()
        playerIndex = 0
        score = 0
        statusText.text = "Memorizza!"
        addColorToSequence()
        playSequence()
    }

    private fun addColorToSequence() {
        sequence.add(colors.random())
    }

    private fun playSequence() {
        isPlaying = false
        playerIndex = 0
        val delay = 500L

        btnRed.isEnabled = false
        btnGreen.isEnabled = false
        btnYellow.isEnabled = false
        btnBlue.isEnabled = false

        Thread {
            sequence.forEachIndexed { index, color ->
                runOnUiThread {
                    highlightButton(color, true)
                }
                Thread.sleep(delay - index * 50)
                runOnUiThread {
                    highlightButton(color, false)
                }
                Thread.sleep(200)
            }

            runOnUiThread {
                statusText.text = "Adesso tocca!"
                btnRed.isEnabled = true
                btnGreen.isEnabled = true
                btnYellow.isEnabled = true
                btnBlue.isEnabled = true
                isPlaying = true
            }
        }.start()
    }

    private fun highlightButton(color: Int, active: Boolean) {
        val alpha = if (active) 255 else 100
        when (color) {
            0 -> btnRed.alpha = alpha.toFloat()
            1 -> btnGreen.alpha = alpha.toFloat()
            2 -> btnYellow.alpha = alpha.toFloat()
            3 -> btnBlue.alpha = alpha.toFloat()
        }
    }

    private fun onColorClick(color: Int) {
        if (!isPlaying) return

        if (color == sequence[playerIndex]) {
            playerIndex++
            if (playerIndex >= sequence.size) {
                score += 10
                statusText.text = "Corretto! Score: $score"

                Thread {
                    Thread.sleep(800)
                    runOnUiThread {
                        addColorToSequence()
                        playSequence()
                    }
                }.start()
            }
        } else {
            statusText.text = "SBAGLIATO! Score finale: $score"
            val reward = gameManager.applyReward(gameId, score)
            Toast.makeText(this, "Game Over! XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
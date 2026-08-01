package com.example.huntix.snake

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.FrameLayout
import android.widget.TextView
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class SnakeActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var scoreText: TextView
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private var gameId = "game_snake"

    private val handler = Handler(Looper.getMainLooper())
    private var gameThread: Thread? = null
    private var playing = false

    private val gridSize = 20
    private val cellSize = 40
    private lateinit var snake: MutableList<Point>
    private lateinit var food: Point
    private var direction = Point(1, 0)
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_snake")
        }

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        surfaceView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        scoreText = TextView(this)
        scoreText.text = "Score: 0"
        scoreText.setTextColor(Color.WHITE)
        scoreText.setBackgroundResource(android.R.color.black)

        val container = FrameLayout(this)
        container.addView(surfaceView)
        val scoreParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setPadding(16, 16, 16, 16)
        }
        container.addView(scoreText, scoreParams)
        setContentView(container)

        surfaceView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val x = event.x
                    val y = event.y
                    val centerX = surfaceView.width / 2f
                    val centerY = surfaceView.height / 2f
                    if (x < centerX && y < centerY) direction = Point(-1, 0)      // Left
                    else if (x > centerX && y < centerY) direction = Point(1, 0) // Right
                    else if (x < centerX && y > centerY) direction = Point(0, -1)// Up
                    else if (x > centerX && y > centerY) direction = Point(0, 1) // Down
                    true
                }
            }
            false
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        playing = false
        gameThread?.join()
    }

    private fun startGame() {
        snake = mutableListOf(Point(gridSize / 2, gridSize / 2))
        direction = Point(1, 0)
        score = 0
        spawnFood()
        playing = true

        gameThread = Thread {
            while (playing) {
                try {
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    break
                }
                update()
                draw()
            }
        }
        gameThread?.start()
    }

    private fun update() {
        val head = Point(snake[0])
        head.x += direction.x
        head.y += direction.y

        // Borderi
        if (head.x < 0 || head.x >= gridSize || head.y < 0 || head.y >= gridSize) {
            gameOver()
            return
        }

        // Se colpisce sé stessa
        if (snake.contains(head)) {
            gameOver()
            return
        }

        snake.add(0, head)

        if (head == food) {
            score++
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun spawnFood() {
        food = Point(Random.nextInt(gridSize), Random.nextInt(gridSize))
        while (snake.contains(food)) {
            food = Point(Random.nextInt(gridSize), Random.nextInt(gridSize))
        }
    }

    private fun draw() {
        val holder = surfaceView.holder
        val canvas = holder.lockCanvas()

        canvas?.let {
            val paint = Paint()
            paint.color = Color.BLACK
            it.drawColor(Color.BLACK)
            paint.color = Color.GREEN
            paint.style = Paint.Style.FILL

            snake.forEach { point ->
                it.drawRect(
                    point.x * cellSize.toFloat(),
                    point.y * cellSize.toFloat(),
                    (point.x + 1) * cellSize.toFloat(),
                    (point.y + 1) * cellSize.toFloat(),
                    paint
                )
            }

            paint.color = Color.RED
            it.drawRect(
                food.x * cellSize.toFloat(),
                food.y * cellSize.toFloat(),
                (food.x + 1) * cellSize.toFloat(),
                (food.y + 1) * cellSize.toFloat(),
                paint
            )

            holder.unlockCanvasAndPost(it)
        }

        handler.post {
            scoreText.text = "Score: $score"
        }
    }

    private fun gameOver() {
        playing = false
        val reward = gameManager.applyReward(gameId, score)
        runOnUiThread {
            android.widget.Toast.makeText(
                this,
                "Game Over! Score: $score, XP: ${reward.xpEarned}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }
}
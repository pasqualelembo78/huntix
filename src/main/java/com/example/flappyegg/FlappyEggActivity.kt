package com.example.flappyegg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.random.Random

class FlappyEggActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var gameView: GameSurfaceView
    private lateinit var scoreText: TextView
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager

    private var score = 0
    private var gameId = "game_flappy_egg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameManager = com.example.huntix.data.MiniGameManager(this)
        Bundle().apply {
            gameId = getString("game_id", "game_flappy_egg")
        }

        val container = FrameLayout(this)
        gameView = GameSurfaceView(this)
        scoreText = TextView(this)
        scoreText.text = "Score: 0"
        scoreText.textSize = 20f

        container.addView(gameView)
        container.addView(scoreText)
        setContentView(container)
    }

    inner class GameSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
        private var eggY = 0f
        private var eggVelocity = 0f
        private var gravity = 0.5f
        private var jumpStrength = -8f
        private var pipeX = 0f
        private var pipeGap = 0f
        private var pipeWidth = 80f
        private var gameWidth = 0
        private var gameHeight = 0
        private var gameThread: Thread? = null
        private var playing = false

        override fun surfaceCreated(holder: SurfaceHolder) {
            gameWidth = width
            gameHeight = height
            eggY = gameHeight / 2f
            pipeX = gameWidth.toFloat()
            pipeGap = gameHeight / 3f

            playing = true
            gameThread = Thread {
                while (playing) {
                    try { Thread.sleep(30) } catch (e: InterruptedException) { break }

                    eggVelocity += gravity
                    eggY += eggVelocity

                    pipeX -= 4f
                    if (pipeX < -pipeWidth) {
                        pipeX = gameWidth.toFloat()
                        pipeGap = (gameHeight / 4f + Random.nextFloat() * gameHeight / 2f)
                        score++
                    }

                    if (eggY < 0) eggY = 0f
                    if (eggY > gameHeight) eggY = gameHeight.toFloat()

                    val tapZone = gameWidth / 2
                    if (eggY > gameHeight - 100) { // Colpimento conterra
                        gameOver()
                        continue
                    }

                    val eggCenterX = 50f
                    if (pipeX < eggCenterX + 30 && pipeX + pipeWidth > eggCenterX - 30) {
                        if (eggY < pipeGap || eggY > pipeGap + 150) {
                            gameOver()
                            continue
                        }
                    }

                    draw()
                }
            }
            gameThread?.start()

            setOnTouchListener { _, event ->
                eggVelocity = jumpStrength
                true
            }
        }

        private fun draw() {
            val canvas = holder.lockCanvas()
            canvas?.let {
                it.drawColor(android.graphics.Color.CYAN)
                val paint = android.graphics.Paint()
                paint.color = android.graphics.Color.YELLOW
                it.drawCircle(50f, eggY, 20f, paint)

                paint.color = android.graphics.Color.GREEN
                it.drawRect(pipeX, 0f, pipeX + pipeWidth, pipeGap, paint)
                it.drawRect(pipeX, pipeGap + 150f, pipeX + pipeWidth, gameHeight.toFloat(), paint)

                holder.unlockCanvasAndPost(it)
            }

            runOnUiThread {
                scoreText.text = "Score: $score"
            }
        }

        private fun gameOver() {
            playing = false
            val reward = gameManager.applyReward(gameId, score)
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@FlappyEggActivity,
                    "Game Over! Score: $score, XP: ${reward.xpEarned}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            playing = false
        }
    }
}
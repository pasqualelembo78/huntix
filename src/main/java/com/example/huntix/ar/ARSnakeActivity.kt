package com.example.huntix.ar

import android.os.Bundle
import android.widget.Toast
import com.example.huntix.R
import com.example.huntix.ar.base.ARGameActivity
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlin.random.Random

class ARSnakeActivity : ARGameActivity() {

    private val gridSize = 10
    private val arenaSize = 2.0f
    private lateinit var snake: MutableList<SnakeSegment>
    private lateinit var foodNode: Node
    private var direction = Pair(1, 0)
    private var score = 0
    private var gameSpeed = 800L
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private var gameThread: Thread? = null
    private var playing = false
    private lateinit var arenaAnchor: AnchorNode

    data class SnakeSegment(var x: Float, var z: Float)

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        setupGesture()
        createArena()
    }

    private fun setupGesture() {
        arSceneView.setOnTouchListener { _, event ->
            val centerX = arSceneView.width / 2f
            val centerY = arSceneView.height / 2f

            if (event.x < centerX && event.y < centerY) direction = Pair(-1, 0)
            else if (event.x > centerX && event.y < centerY) direction = Pair(1, 0)
            else if (event.x < centerX && event.y > centerY) direction = Pair(0, -1)
            else if (event.x > centerX && event.y > centerY) direction = Pair(0, 1)
            true
        }
    }

    private fun createArena() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose

        val anchor = Anchor.Builder()
            .setPose(com.google.ar.core.Pose(
                floatArrayOf(pose.tx(), pose.ty() - 1f, pose.tz() - 2f),
                floatArrayOf(1f, 0f, 0f, 0f)
            ))
            .build()

        arenaAnchor = AnchorNode(anchor).also {
            it.isEnabled = true
            scene.addChild(it)
        }

        // Disegna bordi arena
        for (i in 0..4) {
            val edge = ShapeFactory.makeCube(
                com.google.ar.sceneform.math.Vector3(arenaSize, 0.05f, 0.05f),
                com.google.ar.sceneform.math.Vector3(0f, -0.5f, 0f),
                getMaterial(Color(android.graphics.Color.GRAY))
            )
            Node().apply {
                setParent(arenaAnchor)
                localPosition = com.google.ar.sceneform.math.Vector3(
                    -arenaSize / 2 + i * (arenaSize / 4), 0f, -arenaSize
                )
                localRotation = com.google.ar.sceneform.math.Vector3(0f, 0f, 0f)
                renderable = edge
            }
        }
    }

    private fun startSnakeGame() {
        snake = mutableListOf(SnakeSegment(gridSize / 2f, gridSize / 2f))
        direction = Pair(1, 0)
        score = 0
        spawnFood()

        playing = true
        gameThread = Thread {
            while (playing) {
                try { Thread.sleep(gameSpeed) } catch (e: InterruptedException) { break }
                updateSnake()
                drawSnake()
            }
        }
        gameThread?.start()
    }

    private fun updateSnake() {
        val head = snake[0]
        val newHead = SnakeSegment(
            head.x + direction.first,
            head.z + direction.second
        )

        // Collisioni bordi
        if (newHead.x < 0 || newHead.x >= gridSize || newHead.z < 0 || newHead.z >= gridSize) {
            gameOver()
            return
        }

        // Collisione con sé stessa
        snake.forEach { segment ->
            if (segment.x == newHead.x && segment.z == newHead.z) {
                gameOver()
                return
            }
        }

        snake.add(0, newHead)

        if (newHead.x == foodNode.localPosition.x / (arenaSize / gridSize) &&
            newHead.z == foodNode.localPosition.z / (arenaSize / gridSize)) {
            score++
            spawnFood()
            if (gameSpeed > 300) gameSpeed -= 50
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun spawnFood() {
        val fx = Random.nextInt(gridSize).toFloat()
        val fz = Random.nextInt(gridSize).toFloat()

        MaterialFactory.makeOpaqueWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(R.drawable.ic_golden_egg)
                .build()
                .apply {
                    build(this@ARSnakeActivity)
                }) { _, texture ->
            val material = getMaterial(Color(android.graphics.Color.YELLOW))
            val sphere = ShapeFactory.makeSphere(0.08f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)
            foodNode = Node().apply {
                setParent(arenaAnchor)
                localPosition = gridToLocal(fx, fz)
                renderable = sphere
            }

            // Suono cattura uovo
            spatialAudio.playSoundAtPosition("egg_collect", fx, 0f, fz)
        }
    }

    private fun drawSnake() {
        // Rimuove vecchi nodi e disegna nuovi
        snake.forEachIndexed { index, segment ->
            val pos = gridToLocal(segment.x, segment.z)
            val color = if (index == 0) Color(android.graphics.Color.GREEN) else Color(android.graphics.Color.BLUE)
            val sphere = ShapeFactory.makeSphere(0.07f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), getMaterial(color))

            Node().apply {
                setParent(arenaAnchor)
                localPosition = pos
                renderable = sphere
            }
        }
    }

    private fun gridToLocal(x: Float, z: Float): com.google.ar.sceneform.math.Vector3 {
        return com.google.ar.sceneform.math.Vector3(
            (x / gridSize - 0.5f) * arenaSize,
            0f,
            (z / gridSize - 0.5f) * arenaSize
        )
    }

    private fun getMaterial(color: Color): com.google.ar.sceneform.rendering.Material {
        return android.widget.Toast.makeText(this, "", android.widget.Toast.LENGTH_SHORT)
        return MaterialFactory.makeOpaqueWithColor(this, color).join()
    }

    private fun gameOver() {
        playing = false
        val reward = gameManager.applyReward("ar_snake", score)
        Toast.makeText(this, "Game Over! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
        val resultIntent = intent
        resultIntent.putExtra("final_score", score)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun handleFrame(frame: Frame) {
        // Aggiornamenti AR
    }

    override fun onGameOver(score: Int) {
        gameOver()
    }
}
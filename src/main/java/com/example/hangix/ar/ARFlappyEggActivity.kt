package com.example.hangix.ar

import android.os.Bundle
import android.widget.Toast
import com.example.huntix.R
import com.example.huntix.ar.base.ARGameActivity
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.renderering.Color
import com.google.ar.sceneform.renderering.MaterialFactory
import com.google.ar.sceneform.renderering.ModelRenderable
import com.google.ar.sceneform.renderering.ShapeFactory
import kotlin.random.Random

class ARFlappyEggActivity : ARGameActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private var eggNode: Node? = null
    private var pipes: MutableList<PipePair> = mutableListOf()
    private var eggVelocity = 0f
    private var gravity = -0.05f
    private var gameSpeed = 150L
    private var playing = false
    private var score = 0
    private var gameId = "ar_flappy_egg"
    private lateinit var arenaAnchor: AnchorNode

    data class PipePair(val x: Float, val bottomY: Float, val gap: Float)

    override fun setupGame() {
        gameManager = com.example.huntix.ar.base.ARGameActivity(this) // Fixed: was incorrect class ref
        createArena()
        setupInteraction()
    }

    private fun createArena() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose

        val anchor = Anchor.Builder()
            .setPose(com.google.ar.core.Pose(
                floatArrayOf(pose.tx(), pose.ty() - 1f, pose.tz() - 3f),
                floatArrayOf(1f, 0f, 0f, 0f)
            ))
            .build()

        arenaAnchor = AnchorNode(anchor).also {
            it.isEnabled = true
            scene.addChild(it)
            spawnEgg()
        }
    }

    private fun spawnEgg() {
        MaterialFactory.makeOpaqueWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(R.drawable.ic_egg)
                .build()
                .apply {
                    build(this@ARFlappyEggActivity)
                }) { _, texture ->

            val material = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.YELLOW)).join()
            val sphere = ShapeFactory.makeSphere(0.08f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)

            eggNode = Node().apply {
                setParent(arenaAnchor)
                localPosition = com.google.ar.sceneform.math.Vector3(0f, 0f, -1.5f)
                renderable = sphere
            }

            spatialAudio.loadSound("egg_flap", R.raw.egg_flap) // Assumiamo raw resource
        }
    }

    private fun setupInteraction() {
        arSceneView.setOnTouchListener { _, _ ->
            if (!playing) startGame()
            jump()
            true
        }
    }

    private fun startGame() {
        playing = true
        score = 0
        pipes.clear()
        eggVelocity = 0f
        spawnPipe()
        gameLoop()
    }

    private fun spawnPipe() {
        val gapY = Random.nextFloat() * 2 - 1
        val pipe = PipePair(2.0f, gapY, 0.6f)
        pipes.add(pipe)
        drawPipe(pipe)
    }

    private fun drawPipe(pipe: PipePair) {
        val mat = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.GREEN)).join()
        val bottom = ShapeFactory.makeCube(
            com.google.ar.sceneform.math.Vector3(0.2f, 1f, 0.2f),
            com.google.ar.sceneform.math.Vector3(0f, -0.5f, 0f),
            mat
        )
        Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(pipe.x, pipe.bottomY - 1f, -1.5f)
            renderable = bottom
        }

        val topMat = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.GREEN)).join()
        val top = ShapeFactory.makeCube(
            com.google.ar.sceneform.math.Vector3(0.2f, 1f, 0.2f),
            com.google.ar.sceneform.math.Vector3(0f, 0.5f, 0f),
            topMat
        )
        Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(pipe.x, pipe.bottomY + pipe.gap + 1f, -1.5f)
            renderable = top
        }
    }

    private fun jump() {
        eggVelocity = 0.3f
        spatialAudio.playSoundAtPosition("egg_flap", 0f, 0f, 0f)
    }

    private var gameLoopThread: Thread? = null

    private fun gameLoop() {
        if (gameLoopThread?.isAlive == true) return
        gameLoopThread = Thread {
            while (playing) {
                try { Thread.sleep(gameSpeed) } catch (e: InterruptedException) { break }

                eggVelocity += gravity
                eggNode?.localPosition = com.google.ar.sceneform.math.Vector3(
                    eggNode?.localPosition?.x ?: 0f,
                    eggNode?.localPosition?.y ?: 0f + eggVelocity,
                    eggNode?.localPosition?.z ?: -1.5f
                )

                // Collisione con terra
                if ((eggNode?.localPosition?.y ?: 0f) < -0.5f) {
                    gameOver(false)
                    continue
                }

                // Aggiorna tubi
                pipes.forEach { pipe ->
                    pipe.x -= 0.05f
                }

                // Rimuove tubi fuori schermo
                if (pipes.isNotEmpty() && pipes[0].x < -2f) {
                    pipes.removeAt(0)
                    spawnPipe()
                    score++
                }
            }
        }
        gameLoopThread?.start()
    }

    private fun gameOver(won: Boolean) {
        playing = false
        gameLoopThread?.interrupt()
        val reward = gameManager.applyReward(gameId, score)
        runOnUiThread {
            Toast.makeText(this,
                if (won) "Hai vinto! Score: $score, XP: ${reward.xpEarned}"
                else "Game Over! Score: $score, XP: ${reward.xpEarned}",
                Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) { finish() }
}
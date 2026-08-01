package com.example.hangix.ar

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
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlin.random.Random

class ARSimonActivity : ARGameActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private var sequence = mutableListOf<Int>()
    private var playerIndex = 0
    private var isPlaying = false
    private var score = 0
    private lateinit var arenaAnchor: AnchorNode
    private val buttonNodes: Array<Node?> = arrayOfNulls(4)
    private val colorNames = listOf("rosso", "verde", "giallo", "blu")
    private val colors = listOf(
        Color(android.graphics.Color.RED),
        Color(android.graphics.Color.GREEN),
        Color(android.graphics.Color.YELLOW),
        Color(android.graphics.Color.BLUE)
    )

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        createArena()
        setupInteraction()
    }

    private fun createArena() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose

        val anchor = Anchor.Builder()
            .setPose(com.google.ar.core.Pose(
                floatArrayOf(pose.tx(), pose.ty() - 1f, pose.tz() - 2.5f),
                floatArrayOf(1f, 0f, 0f, 0f)
            ))
            .build()

        arenaAnchor = AnchorNode(anchor).also {
            it.isEnabled = true
            scene.addChild(it)
            createSimonButtons()
        }
    }

    private fun createSimonButtons() {
        val radius = 0.4f
        for (i in 0..3) {
            val angle = Math.toRadians((i * 90).toDouble()).toFloat()
            val x = radius * kotlin.math.cos(angle)
            val z = radius * kotlin.math.sin(angle)

            MaterialFactory.makeOpaqueWithColor(this, colors[i]) { _, material ->
                val sphere = ShapeFactory.makeSphere(0.15f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)

                val node = Node().apply {
                    setParent(arenaAnchor)
                    localPosition = com.google.ar.sceneform.math.Vector3(x, 0f, z)
                    renderable = sphere
                    setOnTapListener { _, _ -> onColorClick(i) }
                }
                buttonNodes[i] = node
            }
        }
    }

    private fun setupInteraction() {
        arSceneView.setOnTouchListener { _, _ ->
            if (!isPlaying) startGame()
            true
        }
    }

    private fun startGame() {
        sequence.clear()
        playerIndex = 0
        score = 0
        runOnUiThread {
            Toast.makeText(this, "Memorizza la sequenza!", Toast.LENGTH_SHORT).show()
        }
        addColorToSequence()
        playSequence()
    }

    private fun addColorToSequence() {
        sequence.add(Random.nextInt(4))
    }

    private fun playSequence() {
        isPlaying = false
        playerIndex = 0

        Thread {
            sequence.forEachIndexed { index, color ->
                highlightButton(color, true)
                Thread.sleep(600 - index * 30)
                highlightButton(color, false)
                Thread.sleep(200)
            }

            runOnUiThread {
                Toast.makeText(this, "Adesso tocca!", Toast.LENGTH_SHORT).show()
                isPlaying = true
                spatialAudio.playSoundAtPosition("simon_start", 0f, 0f, 0f)
            }
        }.start()
    }

    private fun highlightButton(index: Int, active: Boolean) {
        buttonNodes[index]?.let { node ->
            node.renderable?.let { renderable ->
                if (renderable is com.google.ar.sceneform.rendering.Color) {
                    val mat = renderable.material
                    mat?.let {
                        val intensity = if (active) 1f else 0.3f
                        it.baseColor = Color(colors[index].r * intensity, colors[index].g * intensity, colors[index].b * intensity, 1f)
                    }
                }
            }
        }

        // Effetto luminoso
        if (active) {
            spatialAudio.playSoundAtPosition("button_press_${colorNames[index]}", 0f, 0f, 0f)
        }
    }

    private fun onColorClick(color: Int) {
        if (!isPlaying) return

        if (color == sequence[playerIndex]) {
            playerIndex++
            if (playerIndex >= sequence.size) {
                score += 10
                runOnUiThread {
                    Toast.makeText(this, "Corretto! Score: $score", Toast.LENGTH_SHORT).show()
                }

                Thread {
                    Thread.sleep(800)
                    addColorToSequence()
                    playSequence()
                }.start()
            }
        } else {
            runOnUiThread {
                val reward = gameManager.applyReward("ar_simon", score)
                Toast.makeText(this, "SBAGLIATO! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) { finish() }
}
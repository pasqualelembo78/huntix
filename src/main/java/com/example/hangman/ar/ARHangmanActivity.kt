package com.example.hangman.ar

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
import com.google.ar.sceneform.rendering.ViewRenderable
import kotlin.random.Random

class ARHangmanActivity : ARGameActivity() {

    private lateinit var gameManager: com.example.hangix.data.MiniGameManager
    private val words = listOf(
        "UOVA", "GALLINA", "FATTORIA", "LATTE", "FORMAGGIO", "MASCARPONE"
    )
    private var secretWord = ""
    private var guessedLetters = mutableSetOf<Char>()
    private var wrongGuesses = 0
    private val maxWrong = 7
    private var score = 0
    private var gameNodes: MutableList<Node> = mutableListOf()
    private lateinit var arenaAnchor: AnchorNode
    private var letterButtons: Map<Char, Node> = emptyMap()

    data class EggLetter(val letter: Char, val node: Node, val x: Float, val z: Float)

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        createArena()
        startGame()
    }

    private fun createArena() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose

        val anchor = Anchor.Builder()
            .setPose(com.google.ar.core.Pose(
                floatArrayOf(pose.tx(), pose.ty() - 0.5f, pose.tz() - 2f),
                floatArrayOf(1f, 0f, 0f, 0f)
            ))
            .build()

        arenaAnchor = AnchorNode(anchor).also {
            it.isEnabled = true
            scene.addChild(it)
        }

        // Forca 3D
        val beamMaterial = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.DKGRAY)).join()
        val beam = ShapeFactory.makeCylinder(0.02f, 0.5f, com.google.ar.sceneform.math.Vector3(0f, 0.25f, 0f), beamMaterial)

        val crossbarMaterial = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.DKGRAY)).join()
        val crossbar = ShapeFactory.makeCube(com.google.ar.sceneform.math.Vector3(0.5f, 0.02f, 0.02f), com.google.ar.sceneform.math.Vector3(0f, 0.5f, 0f), crossbarMaterial)

        Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(0f, 0f, 0f)
            renderable = beam
        }
        Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(0f, 0.5f, 0f)
            renderable = crossbar
        }
    }

    private fun startGame() {
        secretWord = words.random()
        guessedLetters.clear()
        wrongGuesses = 0
        clearAllNodes()

        // Crea lettere come uova interattive
        setupLetterEggs()
    }

    private fun setupLetterEggs() {
        val alphabet = ('A'..'Z').toList()
        val spacing = 0.2f

        alphabet.forEachIndexed { index, letter ->
            val col = index % 7
            val row = index / 7
            val x = (col - 3) * spacing
            val z = (row - 1) * spacing

            MaterialFactory.makeOpaqueWithTexture(this,
                com.google.ar.sceneform.rendering.Texture.builder()
                    .setSource(R.drawable.ic_egg)
                    .build()
                    .apply {
                        build(this@ARHangmanActivity)
                    }) { _, texture ->
                val material = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.WHITE)).join()
                val egg = ShapeFactory.makeSphere(0.06f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)

                val node = Node().apply {
                    setParent(arenaAnchor)
                    localPosition = com.google.ar.sceneform.math.Vector3(x, 0.5f, z)
                    localRotation = com.google.ar.sceneform.math.Vector3(90f, 0f, 0f)
                    renderable = egg
                    setOnTapListener { hitTestResult, plane ->
                        onLetterClick(letter)
                    }
                }
                letterButtons[letter] = node
                gameNodes.add(node)
            }
        }
    }

    private fun onLetterClick(letter: Char) {
        if (guessedLetters.contains(letter)) return
        guessedLetters.add(letter)

        // Rimuovi uovo lettera (diventa rossa)
        letterButtons[letter]?.let { node ->
            val material = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED)).join()
            val egg = ShapeFactory.makeSphere(0.06f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)
            node.renderable = egg
        }

        if (!secretWord.contains(letter)) {
            wrongGuesses++
            drawHangman()
        }

        if (isWordGuessed()) {
            score = secretWord.length * 10 - wrongGuesses * 5
            val reward = gameManager.applyReward("ar_hangman", maxOf(0, score))
            Toast.makeText(this, "Hai vinto! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
            finish()
        } else if (wrongGuesses >= maxWrong) {
            score = 0
            Toast.makeText(this, "Hai perso! Parola: $secretWord", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun drawHangman() {
        // Uovo che cade: crea una palla rossa checade
        val eggMaterial = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED)).join()
        val egg = ShapeFactory.makeSphere(0.08f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), eggMaterial)

        val dropNode = Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(0f, 0.5f, 0f)
            renderable = egg
        }
        gameNodes.add(dropNode)

        // Effetto suono caduta
        spatialAudio.playSoundAtPosition("egg_fall", 0f, 0.5f, 0f)
    }

    private fun clearAllNodes() {
        gameNodes.forEach { it.setParent(null) }
        gameNodes.clear()
    }

    private fun isWordGuessed(): Boolean {
        return secretWord.all { guessedLetters.contains(it) }
    }

    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) { finish() }
}
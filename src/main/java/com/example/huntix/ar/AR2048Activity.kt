package com.example.huntix.ar

import android.os.Bundle
import android.widget.Toast
import com.example.huntix.R
import com.example.huntix.ar.base.ARGameActivity
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import kotlin.random.Random

class AR2048Activity : ARGameActivity() {

    private val size = 4
    private val board = Array(size) { Array(size) { 0 } }
    private var score = 0
    private var gameBoardNodes: Array<Array<Node?>> = Array(size) { arrayOfNulls(size) }
    private lateinit var boardNode: AnchorNode
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        createBoardPlane()
        startGame()
    }

    private fun createBoardPlane() {
        val anchor = arSession?.currentFrame?.let { frame ->
            // Crea un piano centrale per il tabellone
            val x = frame.camera.pose.tx()
            val y = frame.camera.pose.ty() - 0.5f
            val z = frame.camera.pose.tz() - 1.5f

            Anchor.Builder()
                .setPose(com.google.ar.core.Pose(Array(3) { 0f }, Array(4) { 0f }))
                .build()
        }

        anchor?.let {
            boardNode = AnchorNode(it).also { node ->
                node.isEnabled = true
                scene.addChild(node)
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

        placeTileAt(randomCell.first, randomCell.second, board[randomCell.first][randomCell.second])
        return true
    }

    private fun placeTileAt(row: Int, col: Int, value: Int) {
        val x = col.toFloat() * 0.25f
        val z = row.toFloat() * 0.25f

        MaterialFactory.makeTransparentWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(R.drawable.ic_egg)
                .build()
                .apply {
                    build(this@AR2048Activity)
                }) { _, texture ->
            val color = tileColor(value)

            MaterialFactory.makeOpaqueWithTexture(this, texture) { _, material ->
                val sphere = ShapeFactory.makeSphere(0.1f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)
                val node = Node().apply {
                    setParent(boardNode)
                    localPosition = com.google.ar.sceneform.math.Vector3(x, 0f, z)
                    renderable = sphere
                }
                gameBoardNodes[row][col] = node

                // Effetto audio alla creazione
                spatialAudio.playSoundAtPosition("tile_spawn", x, 0f, z)
            }
        }
    }

    private fun tileColor(value: Int): Color {
        return when (value) {
            2 -> Color(android.graphics.Color.rgb(238, 228, 218))
            4 -> Color(android.graphics.Color.rgb(237, 224, 200))
            8 -> Color(android.graphics.Color.rgb(242, 177, 121))
            16 -> Color(android.graphics.Color.rgb(245, 149, 99))
            32 -> Color(android.graphics.Color.rgb(246, 124, 95))
            64 -> Color(android.graphics.Color.rgb(246, 94, 59))
            128 -> Color(android.graphics.Color.rgb(237, 207, 114))
            256 -> Color(android.graphics.Color.rgb(237, 204, 94))
            512 -> Color(android.graphics.Color.rgb(237, 201, 79))
            1024 -> Color(android.graphics.Color.rgb(237, 197, 63))
            2048 -> Color(android.graphics.Color.rgb(237, 194, 46))
            else -> Color(android.graphics.Color.WHITE)
        }
    }

    private fun updateUI() {
        // Aggiorna visualizzazione punteggio
    }

    private fun move(direction: Int): Boolean {
        val originalBoard = board.map { it.copyOf() }

        when (direction) {
            0 -> moveLeft()
            1 -> moveUp()
            2 -> moveRight()
            3 -> moveDown()
        }

        val moved = !originalBoard.contentDeepEquals(board)
        if (moved) {
            addRandomTile()
            updateUI()
            checkGameOver()
        }
        return moved
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
                        animateMerge(i, pos - 1, newRow[pos - 1])
                    } else {
                        newRow[pos] = v
                        prev = v
                        pos++
                        animateMove(i, j, pos - 1)
                    }
                }
            }
            board[i] = newRow
        }
    }

    private fun animateMove(row: Int, fromCol: Int, toCol: Int) {
        gameBoardNodes[row][fromCol]?.localPosition =
            com.google.ar.sceneform.math.Vector3(toCol * 0.25f, 0f, row * 0.25f)
    }

    private fun animateMerge(row: Int, col: Int, newValue: Int) {
        val x = col.toFloat() * 0.25f
        val z = row.toFloat() * 0.25f

        // Effetto audio di fusione
        spatialAudio.playSoundAtPosition("tile_merge", x, 0f, z)

        MaterialFactory.makeOpaqueWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(R.drawable.ic_egg)
                .build()
                .apply {
                    build(this@AR2048Activity)
                }) { _, texture ->
            val color = tileColor(newValue)

            MaterialFactory.makeOpaqueWithTexture(this, texture) { _, material ->
                val sphere = ShapeFactory.makeSphere(0.12f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), material)
                val node = Node().apply {
                    setParent(boardNode)
                    localPosition = com.google.ar.sceneform.math.Vector3(x, 0f, z)
                    renderable = sphere
                }
                gameBoardNodes[row][col] = node
            }
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
            for (j in 0 until size / 2) {
                val temp = board[i][j]
                board[i][j] = board[i][size - 1 - j]
                board[i][size - 1 - j] = temp
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
        val reward = gameManager.applyReward("ar_2048", score)
        Toast.makeText(this, "Game Over! Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()

        val resultIntent = intent
        resultIntent.putExtra("final_score", score)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun handleFrame(frame: Frame) {
        // Aggiornamento logica frame per animazioni AR
    }

    override fun onGameOver(score: Int) {
        gameOver()
    }
}

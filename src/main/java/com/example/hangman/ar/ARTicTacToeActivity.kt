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
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory

class ARTicTacToeActivity : ARGameActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private val board = Array(3) { Array(3) { 0 } } // 0=empty, 1=TU, 2=CPU
    private var currentPlayer = 1
    private var isGameActive = true
    private var score = 0
    private var arenaAnchor: AnchorNode? = null
    private val cellNodes: Array<Array<Node?>> = Array(3) { arrayOfNulls(3) }

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
            drawGridBoard()
        }
    }

    private fun drawGridBoard() {
        val lineMat = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.WHITE)).join()

        for (i in 0..3) {
            // Verticale
            val vLine = ShapeFactory.makeCube(
                com.google.ar.sceneform.math.Vector3(0.02f, 0.02f, 1.5f),
                com.google.ar.sceneform.math.Vector3(0f, 0f, 0f),
                lineMat
            )
            Node().apply {
                setParent(arenaAnchor)
                localPosition = com.google.ar.sceneform.math.Vector3(
                    -0.75f + i * 0.75f, 0f, 0f
                )
                renderable = vLine
            }

            // Orizzontale
            val hLine = ShapeFactory.makeCube(
                com.google.ar.sceneform.math.Vector3(1.5f, 0.02f, 0.02f),
                com.google.ar.sceneform.math.Vector3(0f, 0f, 0f),
                lineMat
            )
            Node().apply {
                setParent(arenaAnchor)
                localPosition = com.google.ar.sceneform.math.Vector3(
                    0f, 0f, -0.75f + i * 0.75f
                )
                renderable = hLine
            }
        }
    }

    private fun startGame() {
        board.forEachIndexed { i, row ->
            row.forEachIndexed { j, _ -> board[i][j] = 0 }
        }
        currentPlayer = 1
        isGameActive = true

        setupCells()
    }

    private fun setupCells() {
        val spacing = 0.75f
        for (i in 0..2) {
            for (j in 0..2) {
                val pos = com.google.ar.sceneform.math.Vector3(
                    -0.75f + j * spacing, 0f, -0.75f + i * spacing
                )

                val cellMat = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.TRANSPARENT)).join()
                val cell = ShapeFactory.makeCube(
                    com.google.ar.sceneform.math.Vector3(0.7f, 0.01f, 0.7f),
                    com.google.ar.sceneform.math.Vector3(0f, -0.01f, 0f),
                    cellMat
                )

                val node = Node().apply {
                    setParent(arenaAnchor)
                    localPosition = pos
                    renderable = cell
                    setOnTapListener { _, _ -> onCellClick(i, j) }
                }
                cellNodes[i][j] = node
            }
        }
    }

    private fun onCellClick(row: Int, col: Int) {
        if (!isGameActive || board[row][col] != 0) return
        if (currentPlayer != 1) return // Aspetta il turno

        makeMove(row, col, 1)

        if (checkWin(1)) endGame(1)
        else if (isBoardFull()) endGame(0)
        else {
            currentPlayer = 2
            aiMove()
        }
    }

    private fun makeMove(row: Int, col: Int, player: Int) {
        board[row][col] = player
        val pos = com.google.ar.sceneform.math.Vector3(
            -0.75f + col * 0.75f, 0f, -0.75f + row * 0.75f
        )

        val color = if (player == 1) Color(android.graphics.Color.GREEN) else Color(android.graphics.Color.MAGENTA)
        val eggMat = MaterialFactory.makeOpaqueWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(R.drawable.ic_egg)
                .build()
                .apply {
                    build(this@ARTicTacToeActivity)
                }).join()

        val sphere = ShapeFactory.makeSphere(0.25f, com.google.ar.sceneform.math.Vector3(0f, 0.1f, 0f), eggMat)

        Node().apply {
            setParent(arenaAnchor)
            localPosition = pos
            renderable = sphere
        }
        cellNodes[row][col] = this
    }

    private fun aiMove() {
        // AI perfetta: cerca vittoria/diffesa, altrimenti random
        val empty = mutableListOf<Pair<Int, Int>>()
        for (i in 0..2) for (j in 0..2) if (board[i][j] == 0) empty.add(Pair(i, j))

        var bestMove: Pair<Int, Int>? = null

        // Proviamo a vincere
        empty.forEach { (r, c) ->
            board[r][c] = 2
            if (checkWin(2)) bestMove = Pair(r, c)
            board[r][c] = 0
        }

        // Blocchiamo avversario se potesse vincere
        if (bestMove == null) {
            empty.forEach { (r, c) ->
                board[r][c] = 1
                if (checkWin(1)) bestMove = Pair(r, c)
                board[r][c] = 0
            }
        }

        // Altrimenti casuale
        if (bestMove == null) bestMove = empty.random()

        // Piccolo delay simulazione "pensiero"
        handler.postDelayed({
            board[bestMove!!.first][bestMove.second] = 2
            makeMove(bestMove.first, bestMove.second, 2)
            spatialAudio.playSoundAtPosition("cpu_move", 0f, 0f, 0f)

            if (checkWin(2)) endGame(2)
            else if (isBoardFull()) endGame(0)
            else currentPlayer = 1
        }, 500)
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

    private fun isBoardFull(): Boolean = (0..2).all { i -> (0..2).all { j -> board[i][j] != 0 } }

    private fun endGame(winner: Int) {
        isGameActive = false
        score = when (winner) {
            1 -> 100
            2 -> 0
            else -> 50
        }
        val reward = gameManager.applyReward("ar_tic_tac_toe", score)
        Toast.makeText(this, "Fine gioco! XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) { finish() }
}
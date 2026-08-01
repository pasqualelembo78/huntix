package com.example.hangix.ar

import android.os.Bundle
import android.widget.Toast
import com.example.huntix.R
import com.example.hangix.ar.base.ARGameActivity
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.renderering.Color
import com.google.ar.sceneform.renderering.MaterialFactory
import com.google.ar.sceneform.renderering.ShapeFactory

class ARConnectFourActivity : ARGameActivity() {

    private lateinit var gameManager: com.example.huntix.data.MiniGameManager
    private val rows = 6
    private val cols = 7
    private val board = Array(rows) { Array(cols) { 0 } } // 0=empty,1=TU,2=CPU
    private var currentPlayer = 1
    private var isGameActive = true
    private var score = 0
    private lateinit var arenaAnchor: AnchorNode
    private val columnNodes: MutableList<ColumnNode> = mutableListOf()
    private var gameId = "ar_connect4"

    data class ColumnNode(val node: Node, val colIndex: Int)

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        createArena()
        setupColumns()
    }

    private fun createArena() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose

        val anchor = Anchor.Builder()
            .setPose(com.google.ar.core.Pose(
                floatArrayOf(pose.tx(), pose.ty() - 0.5f, pose.tz() - 3f),
                floatArrayOf(1f, 0f, 0f, 0f)
            ))
            .build()

        arenaAnchor = AnchorNode(anchor).also {
            it.isEnabled = true
            scene.addChild(it)
        }
    }

    private fun setupColumns() {
        val width = 0.3f
        val spacing = 0.35f

        for (col in 0 until cols) {
            val x = (col - 3) * spacing
            val columnNode = createColumn(x)
            columnNodes.add(ColumnNode(columnNode, col))
        }
    }

    private fun createColumn(x: Float): Node {
        val mat = MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.DKGRAY)).join()
        val column = ShapeFactory.makeCylinder(0.3f, 2.0f, com.google.ar.sceneform.math.Vector3(0f, 1f, 0f), mat)

        return Node().apply {
            setParent(arenaAnchor)
            localPosition = com.google.ar.sceneform.math.Vector3(x, 0f, 0f)
            localRotation = com.google.ar.sceneform.math.Vector3(-90f, 0f, 0f)
            renderable = column
            setOnTapListener { _, _ -> onColumnClick(col) }
        }
    }

    private fun onColumnClick(col: Int) {
        if (!isGameActive || currentPlayer != 1) return

        val row = dropInColumn(col, 1)
        if (row == -1) return

        spawnEgg(row, col, 1)

        if (checkWin(row, col, 1)) {
            endGame(1)
            return
        }

        currentPlayer = 2
        spatialAudio.playSoundAtPosition("cpu_turn", 0f, 0f, 0f)

        // AI move
        arHandler.postDelayed({
            val bestCol = findBestMove()
            val newRow = dropInColumn(bestCol, 2)
            if (newRow != -1) spawnEgg(newRow, bestCol, 2)

            if (newRow != -1 && checkWin(newRow, bestCol, 2)) {
                endGame(2)
            } else {
                currentPlayer = 1
            }
        }, 500)
    }

    private fun dropInColumn(col: Int, player: Int): Int {
        for (r in rows - 1 downTo 0) {
            if (board[r][col] == 0) {
                board[r][col] = player
                return r
            }
        }
        return -1
    }

    private fun spawnEgg(row: Int, col: Int, player: Int) {
        val z = colToZ(col)
        val y = rowToY(row)

        val colorRes = if (player == 1) R.drawable.ic_green_egg else R.drawable.ic_purple_egg

        MaterialFactory.makeOpaqueWithTexture(this,
            com.google.ar.sceneform.rendering.Texture.builder()
                .setSource(colorRes)
                .build()
                .apply {
                    build(this@ARConnectFourActivity)
                }) { _, texture ->

            val mat = MaterialFactory.makeOpaqueWithTexture(this, texture).join()
            val sphere = ShapeFactory.makeSphere(0.12f, com.google.ar.sceneform.math.Vector3(0f, 0f, 0f), mat)

            Node().apply {
                setParent(arenaAnchor)
                localPosition = com.google.ar.sceneform.math.Vector3(0f, y, z)
                renderable = sphere
            }

            spatialAudio.playSoundAtPosition("egg_drop", 0f, y, z)
        }
    }

    private fun colToZ(col: Int): Float = (col - 3) * 0.35f
    private fun rowToY(row: Int): Float = 2.0f - row * 0.35f

    private fun checkWin(row: Int, col: Int, player: Int): Boolean {
        fun checkDr(dr: Int, dc: Int): Boolean {
            var count = 1

            var r = row + dr
            var c = col + dc
            while (r in 0 until rows && c in 0 until cols && board[r][c] == player) {
                count++
                r += dr
                c += dc
            }

            r = row - dr
            c = col - dc
            while (r in 0 until rows && c in 0 until cols && board[r][c] == player) {
                count++
                r -= dr
                c -= dc
            }

            return count >= 4
        }

        return checkDr(0, 1) || checkDr(1, 0) || checkDr(1, 1) || checkDr(1, -1)
    }

    private fun findBestMove(): Int {
        val emptyCols = (0 until cols).filter { col ->
            board[0][col] == 0
        }
        return if (emptyCols.isNotEmpty()) emptyCols.random() else 0
    }

    private fun endGame(winner: Int) {
        isGameActive = false
        score = winner * 50
        val reward = gameManager.applyReward(gameId, score)
        Toast.makeText(this,
            if (winner == 1) "Hai vinto! XP: ${reward.xpEarned}"
            else "CPU ha vinto! XP: ${reward.xpEarned}",
            Toast.LENGTH_LONG).show()
        finish()
    }

    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) { finish() }
}
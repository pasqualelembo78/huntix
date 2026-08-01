package com.example.hangix.ar.exclusive

import android.os.Bundle
import com.example.hangix.R
import com.example.hangix.ar.base.ARGameActivity
import com.google.ar.core.Frame

class AREggShooterActivity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}

class ARColorBombActivity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}

class AREggRadarActivity : ARGameActivity() {
    override fun setupGame() {}
    override fun handleFrame(frame: Frame) {}
    override fun onGameOver(score: Int) {}
}

// Showcase esclusivo: catapulta AR con traiettoria balistica + audio 3D + esplosioni
class AREggSlingshotActivity : ARGameActivity() {

    private var projectileNode: Node? = null
    private var isAiming = false
    private var launchVelocity = Vector3(0f, 0f, 0f)
    private var gravity = -9.8f
    private var timeStep = 0.016f
    private lateinit var gameManager: com.example.huntix.data.MiniGameManager

    override fun setupGame() {
        gameManager = com.example.huntix.data.MiniGameManager(this)
        setupSlingshot()
    }

    private fun setupSlingshot() {
        val frame = arSession?.currentFrame ?: return
        val pose = frame.camera.pose
        val anchor = Anchor.Builder()
            .setPose(Pose(floatArrayOf(pose.tx(), pose.ty() - 1f, pose.tz() - 2f), floatArrayOf(1f, 0f, 0f, 0f)))
            .build()
        val anchorNode = AnchorNode(anchor).also { scene.addChild(it) }

        // Base catapulta (cilindro)
        MaterialFactory.makeOpaqueWithColor(this, Color(Color.rgb(139, 69, 19))) { _, material ->
            val base = ShapeFactory.makeCylinder(0.1f, 0.5f, Vector3(0f, 0.25f, 0f), material)
            Node().apply { setParent(anchorNode); localPosition = Vector3(0f, 0f, -1f); renderable = base }
        }
    }

    override fun handleFrame(frame: Frame) {
        projectileNode?.let { node ->
            val pos = node.localPosition
            node.localPosition = Vector3(pos.x + launchVelocity.x * timeStep,
                pos.y + launchVelocity.y * timeStep,
                pos.z + launchVelocity.z * timeStep)
            launchVelocity = Vector3(launchVelocity.x, launchVelocity.y + gravity * timeStep, launchVelocity.z)

            if (pos.y < -1f) {
                node.setParent(null)
                projectileNode = null
            }
        }
    }

    private fun launchProjectile(direction: Vector3) {
        MaterialFactory.makeOpaqueWithTexture(this,
            Texture.builder().setSource(R.drawable.ic_egg).build().apply { build(this@AREggSlingshotActivity) }) { _, texture ->
            val mat = MaterialFactory.makeOpaqueWithTexture(this, texture).join()
            val sphere = ShapeFactory.makeSphere(0.1f, Vector3(0f, 0f, 0f), mat)
            projectileNode = Node().apply {
                setParent(scene.find<AnchorNode> { it.parent == scene } ?: return@apply)
                localPosition = Vector3(0f, 0f, -1f)
                launchVelocity = direction * 3f
            }

            spatialAudio.playSoundAtPosition("launch", 0f, 0f, -1f)
            spawnExplosion(Vector3(0f, -0.5f, -1f))
        }
    }

    private fun spawnExplosion(position: Vector3) {
        // Effetto particelle semplice (cubo rossastruolo)
        MaterialFactory.makeOpaqueWithColor(this, Color(Color.RED)) { _, mat ->
            val cube = ShapeFactory.makeCube(Vector3(0.1f, 0.1f, 0.1f), Vector3(0f, 0f, 0f), mat)
            repeat(10) {
                Node().apply {
                    setParent(scene)
                    localPosition = position + Vector3((0..20).random().toFloat()-10f, (0..20).random().toFloat()-10f, (0..20).random().toFloat()-10f) * 0.05f
                    renderable = cube
                }
            }
        }
        spatialAudio.playSoundAtPosition("explosion", position.x, position.y, position.z)
        vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onGameOver(score: Int) {
        val reward = gameManager.applyReward("ar_egg_slingshot", score)
        Toast.makeText(this, "Score: $score, XP: ${reward.xpEarned}", Toast.LENGTH_LONG).show()
        finish()
    }
}

// Importazioni ausiliarie
typealias Node = com.google.ar.sceneform.Node
typealias Anchor = com.google.ar.core.Anchor
typealias AnchorNode = com.google.ar.sceneform.AnchorNode
typealias Pose = com.google.ar.core.Pose
typealias Vector3 = com.google.ar.sceneform.math.Vector3
typealias Color = com.google.ar.sceneform.rendering.Color
typealias Texture = com.google.ar.sceneform.rendering.Texture
typealias MaterialFactory = com.google.ar.sceneform.rendering.MaterialFactory
typealias ShapeFactory = com.google.ar.sceneform.rendering.ShapeFactory
typealias VibrationEffect = android.os.VibrationEffect
val Context.vibrator get() = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator?
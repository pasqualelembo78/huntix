package com.intelligame.huntix.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import com.intelligame.huntix.battle3d.BattleEngine3D.AnimEvent

class Fighter3DNode(
    private val modelLoader: ModelLoader
) {
    var modelNode: ModelNode? = null
        private set
    private var loaded = false
    private var currentAnim: String? = null

    private val animMap = mapOf(
        AnimEvent.IDLE to listOf("Idle", "idle", "Standing_Idle"),
        AnimEvent.WALK to listOf("Walk", "walk", "Walking", "Run", "run"),
        AnimEvent.PUNCH to listOf("Punch", "punch", "Punching"),
        AnimEvent.KICK to listOf("Kick", "kick", "Roundhouse_Kick", "Punch"),
        AnimEvent.SPECIAL to listOf("SwordSlash", "Shoot_OneHanded", "Special", "Throw"),
        AnimEvent.HIT to listOf("RecieveHit", "Hit_Reaction", "hit", "GettingHit"),
        AnimEvent.BLOCK to listOf("Block", "block", "Guarding", "Defense", "Idle"),
        AnimEvent.KO to listOf("Death", "death", "Defeat", "KO", "ko", "Falling"),
        AnimEvent.VICTORY to listOf("Victory", "victory", "Cheering", "Taunt", "Idle")
    )

    fun load(glbPath: String, onLoaded: () -> Unit = {}) {
        modelLoader.loadModelInstanceAsync(
            fileLocation = glbPath,
            onResult = { instance ->
                if (instance != null) {
                    modelNode = ModelNode(instance)
                    loaded = true
                }
                onLoaded()
            }
        )
    }

    fun isLoaded(): Boolean = loaded

    fun playAnimation(anim: AnimEvent) {
        val node = modelNode ?: return
        val name = resolveAnimName(node, anim) ?: return
        if (name == currentAnim) return
        currentAnim = name
        val loop = anim == AnimEvent.IDLE || anim == AnimEvent.WALK
        node.playAnimation(name, speed = 1f, loop = loop)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        modelNode?.position = Position(x = x, y = y, z = z)
    }

    fun setFacing(right: Boolean) {
        modelNode?.rotation = Rotation(y = if (right) 0f else 180f)
    }

    private fun resolveAnimName(node: ModelNode, anim: AnimEvent): String? {
        val candidates = animMap[anim] ?: return null
        val count = node.animationCount
        if (count == 0) return null
        val names = (0 until count).mapNotNull { i ->
            try { node.animator.getAnimationName(i) } catch (_: Exception) { null }
        }
        for (key in candidates) {
            val match = names.firstOrNull {
                it.equals(key, ignoreCase = true) || it.contains(key, ignoreCase = true)
            }
            if (match != null) return match
        }
        return names.firstOrNull()
    }

    fun destroy() {
        modelNode?.destroy()
    }
}

package com.intelligame.huntix.ui

import com.google.android.filament.EntityManager
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import com.intelligame.huntix.reallife.PetDef

/**
 * PetNode — node 3D del pet che segue il giocatore nella città.
 * Composto da: corpo (cubo), testa (sfera), coda (cubo), 2 occhi (sfere), 4 zampe (cubi).
 * IA: segue il player se distanza > followDist, si siede se vicino.
 */
class PetNode(def: PetDef) : Node() {

    private val followDist = 1.8f
    private val stopDist = 0.8f
    private val bodyW = 0.35f * def.size
    private val bodyH = 0.28f * def.size
    private val bodyD = 0.55f * def.size
    private val headR = 0.2f * def.size
    private val limbW = 0.1f * def.size
    private val limbH = 0.22f * def.size
    private val tailW = 0.08f * def.size
    private val tailH = 0.18f * def.size

    private val bodyNode: CubeNode
    private val headNode: SphereNode
    private val tailNode: CubeNode
    private val eyeL: SphereNode
    private val eyeR: SphereNode
    private val legFL: CubeNode
    private val legFR: CubeNode
    private val legBL: CubeNode
    private val legBR: CubeNode
    private val loveHeart: SphereNode

    private var targetX = 0f
    private var targetZ = 0f
    private var idleTimer = 0f
    private var isSitting = false
    private var isShowingHeart = false
    private var heartTimer = 0f
    private var bobPhase = 0f

    init {
        // Body
        bodyNode = CubeNode(
            size = io.github.sceneview.math.Size(bodyW, bodyH, bodyD),
            center = Position(0f, bodyH / 2 + limbH, 0f),
            colorInstance = ColorInstance(def.bodyColor),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode }

        // Head
        headNode = SphereNode(
            radius = headR,
            center = Position(0f, bodyH / 2 + limbH + bodyH / 2 + headR * 0.6f, bodyD / 2 + headR * 0.4f),
            colorInstance = ColorInstance(def.headColor),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode }

        // Eyes
        val eyeOff = headR * 0.35f
        val eyeR2 = headR * 0.18f
        val eyeY = bodyH / 2 + limbH + bodyH / 2 + headR * 0.6f + headR * 0.15f
        val eyeZ = bodyD / 2 + headR * 0.4f + headR * 0.7f
        eyeL = SphereNode(radius = eyeR2,
            center = Position(-eyeOff, eyeY, eyeZ),
            colorInstance = ColorInstance(0xFF1A1A1A.toInt()),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode }
        eyeR = SphereNode(radius = eyeR2,
            center = Position(eyeOff, eyeY, eyeZ),
            colorInstance = ColorInstance(0xFF1A1A1A.toInt()),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode }

        // Tail
        tailNode = CubeNode(
            size = io.github.sceneview.math.Size(tailW, tailH, tailW),
            center = Position(0f, bodyH / 2 + limbH + bodyH / 2, -bodyD / 2 - tailW),
            colorInstance = ColorInstance(def.headColor),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode }

        // Legs
        val legData = listOf(
            Pair("FL", -bodyW / 2 + limbW / 2, bodyD / 2 - limbW),
            Pair("FR", bodyW / 2 - limbW / 2, bodyD / 2 - limbW),
            Pair("BL", -bodyW / 2 + limbW / 2, -bodyD / 2 + limbW),
            Pair("BR", bodyW / 2 - limbW / 2, -bodyD / 2 + limbW)
        )
        val legNodes = legData.map { (name, lx, lz) ->
            CubeNode(
                size = io.github.sceneview.math.Size(limbW, limbH, limbW),
                center = Position(lx, limbH / 2, lz),
                colorInstance = ColorInstance(def.bodyColor),
                engine = null, entity = EntityManager.get().create()
            ).apply { parent = this@PetNode }
        }
        legFL = legNodes[0]; legFR = legNodes[1]; legBL = legNodes[2]; legBR = legNodes[3]

        // Love heart (hidden by default)
        loveHeart = SphereNode(
            radius = headR * 0.5f,
            center = Position(0f, bodyH / 2 + limbH + bodyH / 2 + headR * 2f, bodyD / 2),
            colorInstance = ColorInstance(0xFFE91E63.toInt()),
            engine = null, entity = EntityManager.get().create()
        ).apply { parent = this@PetNode; isVisible = false }
    }

    /**
     * Chiamato ogni frame per aggiornare la posizione del pet.
     * @param playerX, playerZ posizione del giocatore
     * @param dt delta time in secondi
     * @param playerStopped true se il giocatore è fermo
     */
    fun updatePet(playerX: Float, playerZ: Float, dt: Float, playerStopped: Boolean) {
        val dx = playerX - worldPosition.x
        val dz = playerZ - worldPosition.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)

        if (dist > followDist) {
            // Move towards player
            val speed = 3.5f
            val step = speed * dt
            if (dist > 0.01f) {
                worldPosition = Position(
                    worldPosition.x + (dx / dist) * step,
                    worldPosition.y,
                    worldPosition.z + (dz / dist) * step
                )
            }
            isSitting = false
            bobPhase += dt * 8f
        } else if (dist < stopDist && playerStopped) {
            isSitting = true
            bobPhase = 0f
        } else {
            // In between, slowly approach
            val speed = 1.5f
            val step = speed * dt
            if (dist > 0.01f) {
                worldPosition = Position(
                    worldPosition.x + (dx / dist) * step,
                    worldPosition.y,
                    worldPosition.z + (dz / dist) * step
                )
            }
            bobPhase += dt * 6f
        }

        // Face player
        val angle = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
        worldRotation = Rotation(0f, angle, 0f)

        // Walking bob
        if (!isSitting) {
            val bob = kotlin.math.sin(bobPhase.toDouble()).toFloat() * 0.03f
            bodyNode.center = Position(0f, bodyH / 2 + limbH + bob, 0f)
            headNode.center = Position(0f, bodyH / 2 + limbH + bodyH / 2 + headR * 0.6f + bob, bodyD / 2 + headR * 0.4f)
        } else {
            // Sitting pose — lower body
            bodyNode.center = Position(0f, bodyH / 2 + limbH * 0.5f, 0f)
            headNode.center = Position(0f, bodyH / 2 + limbH * 0.5f + bodyH / 2 + headR * 0.6f, bodyD / 2 + headR * 0.4f)
        }

        // Tail wag
        val wag = kotlin.math.sin((System.currentTimeMillis() / 200.0).toFloat()).toFloat() * 15f
        tailNode.worldRotation = Rotation(wag, 0f, 0f)

        // Show love heart when player stopped and pet is close
        if (playerStopped && dist < 1.2f) {
            heartTimer += dt
            if (heartTimer > 1.5f) {
                loveHeart.isVisible = true
                isShowingHeart = true
            }
        } else {
            heartTimer = 0f
            if (isShowingHeart) {
                loveHeart.isVisible = false
                isShowingHeart = false
            }
        }
    }

    /**
     * ColorInstance helper — crea un color instance per i node.
     */
    class ColorInstance(color: Int) : io.github.sceneview.material.ColorInstance(
        color = io.github.sceneview.math.Color(
            android.graphics.Color.red(color) / 255f,
            android.graphics.Color.green(color) / 255f,
            android.graphics.Color.blue(color) / 255f
        )
    )
}

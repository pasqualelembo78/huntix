package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.Skybox
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.MapNode
import com.intelligame.huntix.reallife.RealLifeClient
import com.intelligame.huntix.reallife.WorldState
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.collision.Box
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class CityActivity : AppCompatActivity() {

    private lateinit var sceneView: SceneView
    private lateinit var cameraNode: CameraNode
    private lateinit var joystickView: JoystickView
    private lateinit var npcNameLabel: TextView
    private lateinit var speechBubble: TextView
    private lateinit var minimap: MinimapView
    private var playerNode: SphereNode? = null
    private var playerX = 0f
    private var playerZ = 0f
    private var lastFrameNs = 0L
    private var speechBubbleNpc: NpcData? = null
    private var speechBubbleTimer = 0f

    private val engine get() = sceneView.engine
    private val ml get() = sceneView.materialLoader

    private data class AABB(val minX: Float, val maxX: Float, val minZ: Float, val maxZ: Float)
    private val buildingAABBs = mutableListOf<AABB>()
    private val roadCenters = mutableListOf<Float>()

    private data class NpcData(
        val rootNode: Node,
        val mapNode: MapNode,
        var x: Float,
        var z: Float,
        var targetX: Float,
        var targetZ: Float,
        var waitTime: Float = 0f
    )
    private val npcs = mutableListOf<NpcData>()

    private val NPC_PHRASES = listOf(
        "Ciao! Che bella giornata!",
        " Sai dove si trova la biblioteca?",
        "Mi piace passeggiare qui.",
        "Che tempo fa oggi?",
        "Abiti in quartiere?",
        "Ci vediamo dopo!",
        "Devo fare una commissione.",
        "Che bel posto!",
        "Sei nuovo qui?",
        "Il caffè qui è ottimo.",
        "Stasera esco con gli amici.",
        "Devo ancora fare la spesa.",
        "Hai visto i nuovi negozi?",
        "Che ore sono?",
        "Mi manca il mare...",
        "Oggi ho tanto da fare.",
        "Passa a trovarmi!",
        "Che fortuna incontro te!",
        "Il vento è fresco oggi.",
        "A presto!"
    )

    companion object {
        private const val CITY = 80f
        private const val BLOCK = 10f
        private const val ROAD = 2f
        private const val HALF = CITY / 2f
        private const val P_Y = 0.35f
        private const val CAM_H = 35f
        private const val CAM_D = 25f
        private const val SPEED = 4f
        private const val PLAYER_R = 0.3f
        private const val NPC_SPEED = 2.5f
        private const val NPC_BODY_R = 0.2f
        private const val NPC_HEAD_R = 0.15f
        private const val NPC_HEIGHT = 0.8f
        private const val NPC_INTERACT_DIST = 4f
        private const val SPEECH_DURATION = 4f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sceneView = SceneView(this).apply { cameraManipulator = null }
        sceneView.lifecycle = lifecycle

        cameraNode = CameraNode(engine).apply { far = 500f; near = 0.1f }
        sceneView.setCameraNode(cameraNode)

        buildCity()
        buildDetails()
        placePlayer()

        joystickView = JoystickView(this)

        npcNameLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; alpha = 0f
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }

        speechBubble = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; alpha = 0f
            setPadding(UiKit.dp(this@CityActivity, 12), UiKit.dp(this@CityActivity, 6),
                UiKit.dp(this@CityActivity, 12), UiKit.dp(this@CityActivity, 6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xCC333333.toInt())
                cornerRadius = UiKit.dp(this@CityActivity, 8).toFloat()
            }
        }

        minimap = MinimapView(this)

        val hud = TextView(this).apply {
            text = "  Joystick · avvicinati a un personaggio per parlarci"
            setTextColor(Color.WHITE); textSize = 11f; alpha = 0.5f
        }

        val root = FrameLayout(this).apply {
            addView(sceneView)
            addView(npcNameLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 48) })
            addView(speechBubble, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 72) })
            addView(hud, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = UiKit.dp(this@CityActivity, 8) })
            addView(minimap, FrameLayout.LayoutParams(
                UiKit.dp(this@CityActivity, 110), UiKit.dp(this@CityActivity, 110)
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = UiKit.dp(this@CityActivity, 12); marginEnd = UiKit.dp(this@CityActivity, 12) })
            addView(joystickView, FrameLayout.LayoutParams(
                UiKit.dp(this@CityActivity, 160), UiKit.dp(this@CityActivity, 160)
            ).apply { gravity = Gravity.BOTTOM or Gravity.START; marginStart = UiKit.dp(this@CityActivity, 24); bottomMargin = UiKit.dp(this@CityActivity, 32) })
        }
        sceneView.onTouchEvent = { event, _ ->
            if (event.action == MotionEvent.ACTION_UP) {
                findNearestNpc()?.let { openChat(it.mapNode) }
            }
            true
        }

        setContentView(root)
        joystickView.bringToFront()
        syncCamera()
        minimap.setRoads(roadCenters, HALF)
        loadNpcs()
        loadWorldState()
    }

    override fun onResume() {
        super.onResume()
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(frameCb)
    }

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            try {
                if (lastFrameNs != 0L) {
                    val dt = ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    updatePlayer(dt)
                    updateNpcs(dt)
                    updateNpcLabel()
                    updateSpeechBubble(dt)
                    updateMinimap()
                }
            } catch (_: Exception) {}
            lastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updatePlayer(dt: Float) {
        val jdx = joystickView.dx
        val jdy = joystickView.dy
        if (jdx == 0f && jdy == 0f) return

        val mag = sqrt(jdx * jdx + jdy * jdy)
        val nx = jdx / mag
        val ny = jdy / mag
        val step = SPEED * dt * mag

        val newX = playerX + nx * step
        val newZ = playerZ + ny * step

        if (!collides(newX, newZ)) {
            playerX = newX; playerZ = newZ
        } else if (!collides(newX, playerZ)) {
            playerX = newX
        } else if (!collides(playerX, newZ)) {
            playerZ = newZ
        }

        playerX = playerX.coerceIn(-HALF + 1f, HALF - 1f)
        playerZ = playerZ.coerceIn(-HALF + 1f, HALF - 1f)
        playerNode?.position = Position(playerX, P_Y, playerZ)
        syncCamera()
    }

    private fun updateNpcs(dt: Float) {
        for (npc in npcs) {
            if (npc.waitTime > 0f) {
                npc.waitTime -= dt
                continue
            }

            val dx = npc.targetX - npc.x
            val dz = npc.targetZ - npc.z
            val dist = sqrt(dx * dx + dz * dz)

            if (dist < 0.3f) {
                pickNextTarget(npc)
                continue
            }

            val nx = dx / dist
            val nz = dz / dist
            val step = NPC_SPEED * dt
            npc.x += nx * step.coerceAtMost(dist)
            npc.z += nz * step.coerceAtMost(dist)
            npc.rootNode.position = Position(npc.x, 0f, npc.z)
        }
    }

    private fun pickNextTarget(npc: NpcData) {
        val nearX = roadCenters.minByOrNull { abs(it - npc.x) } ?: npc.x
        val nearZ = roadCenters.minByOrNull { abs(it - npc.z) } ?: npc.z

        val candidates = mutableListOf<Pair<Float, Float>>()
        for (rc in roadCenters) {
            if (abs(rc - nearX) > 0.5f) candidates.add(Pair(rc, nearZ))
            if (abs(rc - nearZ) > 0.5f) candidates.add(Pair(nearX, rc))
        }
        if (candidates.isEmpty()) {
            candidates.add(Pair(nearX, nearZ))
        }

        val (tx, tz) = candidates.random()
        npc.targetX = tx
        npc.targetZ = tz
        npc.waitTime = 1f + (Math.random() * 3f).toFloat()
    }

    private fun updateNpcLabel() {
        var closestDist = Float.MAX_VALUE
        var closestName = ""
        var closestAvatar = ""

        for (npc in npcs) {
            val dx = npc.x - playerX
            val dz = npc.z - playerZ
            val d = sqrt(dx * dx + dz * dz)
            if (d < closestDist) {
                closestDist = d
                closestName = npc.mapNode.name
                closestAvatar = npc.mapNode.avatar
            }
        }

        if (closestDist < NPC_INTERACT_DIST && closestName.isNotEmpty()) {
            npcNameLabel.text = "$closestAvatar $closestName"
            npcNameLabel.alpha = 1f - (closestDist / NPC_INTERACT_DIST).coerceIn(0f, 1f)

            val nearNpc = npcs.firstOrNull {
                val dx = it.x - playerX; val dz = it.z - playerZ
                sqrt(dx * dx + dz * dz) < NPC_INTERACT_DIST
            }
            if (nearNpc != null && speechBubbleNpc != nearNpc && speechBubbleTimer <= 0f) {
                speechBubbleNpc = nearNpc
                speechBubble.text = NPC_PHRASES.random()
                speechBubbleTimer = SPEECH_DURATION
            }
        } else {
            npcNameLabel.alpha = 0f
        }
    }

    private fun updateSpeechBubble(dt: Float) {
        if (speechBubbleTimer > 0f) {
            speechBubbleTimer -= dt
            val fadeIn = ((SPEECH_DURATION - speechBubbleTimer) / 0.3f).coerceAtMost(1f)
            val fadeOut = (speechBubbleTimer / 0.5f).coerceAtMost(1f)
            speechBubble.alpha = fadeIn.coerceAtMost(fadeOut)
        } else {
            speechBubble.alpha = 0f
            speechBubbleNpc = null
        }
    }

    private fun updateMinimap() {
        val npcDots = npcs.map {
            val cat = it.mapNode.category
            val color = CATEGORY_COLORS[cat] ?: 0xFF9090A0.toInt()
            Triple(it.x, it.z, color)
        }
        minimap.update(playerX, playerZ, npcDots)
    }

    private fun collides(x: Float, z: Float): Boolean {
        for (b in buildingAABBs) {
            if (x + PLAYER_R > b.minX && x - PLAYER_R < b.maxX &&
                z + PLAYER_R > b.minZ && z - PLAYER_R < b.maxZ) return true
        }
        return false
    }

    private fun loadNpcs() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { RealLifeClient.getMap() }.getOrNull() ?: return@launch
            val grid = state.width.toFloat()

            for (mn in state.nodes) {
                val cx = ((mn.x / grid) * CITY - HALF).toFloat()
                val cz = ((mn.y / grid) * CITY - HALF).toFloat()
                val snapX = roadCenters.minByOrNull { abs(it - cx) } ?: cx
                val snapZ = roadCenters.minByOrNull { abs(it - cz) } ?: cz
                val col = CATEGORY_COLORS[mn.category] ?: 0xFF9090A0.toInt()

                val bodyMat = ml.createColorInstance(color = col)
                val headMat = ml.createColorInstance(color = col)

                val body = CubeNode(engine, Size(NPC_BODY_R * 2f, NPC_HEIGHT, NPC_BODY_R * 2f), materialInstance = bodyMat)
                    .apply { position = Position(0f, NPC_HEIGHT / 2f, 0f) }
                val head = SphereNode(engine, NPC_HEAD_R, materialInstance = headMat)
                    .apply { position = Position(0f, NPC_HEIGHT + NPC_HEAD_R + 0.05f, 0f) }

                val root = Node(engine).apply { position = Position(snapX, 0f, snapZ) }
                root.addChildNode(body)
                root.addChildNode(head)
                sceneView.addChildNode(root)

                val npc = NpcData(root, mn, snapX, snapZ, snapX, snapZ)
                pickNextTarget(npc)
                npcs.add(npc)
            }
        }
    }

    private fun loadWorldState() {
        lifecycleScope.launch {
            val ws = withContext(Dispatchers.IO) { RealLifeClient.getWorldState() }.getOrNull() ?: return@launch
            applyTimeOfDay(ws)
        }
    }

    private fun applyTimeOfDay(ws: WorldState) {
        try {
            val parts = ws.time.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: 12
            val minute = parts[1].toIntOrNull() ?: 0
            val t = hour + minute / 60f

            val (skyR, skyG, skyB, lightR, lightG, lightB, intensity) = when {
                t in 5f..7f -> TimeColors(0.6f, 0.5f, 0.6f, 1.0f, 0.7f, 0.5f, 40_000f)
                t in 7f..10f -> TimeColors(0.5f, 0.7f, 1.0f, 1.0f, 0.95f, 0.9f, 80_000f)
                t in 10f..16f -> TimeColors(0.5f, 0.7f, 1.0f, 1.0f, 1.0f, 0.95f, 100_000f)
                t in 16f..19f -> TimeColors(0.9f, 0.5f, 0.3f, 1.0f, 0.7f, 0.4f, 60_000f)
                t in 19f..21f -> TimeColors(0.2f, 0.2f, 0.4f, 0.5f, 0.4f, 0.6f, 20_000f)
                else -> TimeColors(0.05f, 0.05f, 0.15f, 0.2f, 0.2f, 0.4f, 5_000f)
            }

            try {
                val skybox = Skybox.Builder().color(floatArrayOf(skyR, skyG, skyB, 1f)).build(engine)
                sceneView.skybox = skybox
            } catch (_: Exception) {}

            try {
                sceneView.mainLightNode?.let { light ->
                    light.intensity = intensity
                }
            } catch (_: Exception) {}

            when (ws.weather) {
                "Pioggia", "Temporale" -> try { sceneView.mainLightNode?.let { it.intensity *= 0.6f } } catch (_: Exception) {}
                "Nuvoloso" -> try { sceneView.mainLightNode?.let { it.intensity *= 0.75f } } catch (_: Exception) {}
                "Nebbia" -> try { sceneView.mainLightNode?.let { it.intensity *= 0.5f } } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private data class TimeColors(
        val skyR: Float, val skyG: Float, val skyB: Float,
        val lightR: Float, val lightG: Float, val lightB: Float,
        val intensity: Float
    )

    private fun buildDetails() {
        val poleMat = ml.createColorInstance(color = Color.rgb(0x55, 0x55, 0x55))
        val lightMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xEE, 0xAA))
        val benchMat = ml.createColorInstance(color = Color.rgb(0x8B, 0x5E, 0x3C))
        val trunkMat = ml.createColorInstance(color = Color.rgb(0x6B, 0x42, 0x26))
        val leafMat = ml.createColorInstance(color = Color.rgb(0x2E, 0x7D, 0x32))

        for (i in roadCenters.indices) {
            for (j in roadCenters.indices) {
                val rx = roadCenters[i]
                val rz = roadCenters[j]
                val sd = ((rx * 173 + rz * 311).toInt().let { if (it < 0) -it else it }) % 1000

                if (sd % 3 == 0) {
                    val lx = rx + ROAD / 2f + 0.8f
                    sceneView.addChildNode(CubeNode(engine, Size(0.08f, 2.5f, 0.08f), materialInstance = poleMat).apply {
                        position = Position(lx, 1.25f, rz)
                    })
                    sceneView.addChildNode(SphereNode(engine, 0.15f, materialInstance = lightMat).apply {
                        position = Position(lx, 2.6f, rz)
                    })
                }

                if (sd % 4 == 1) {
                    val bx = rx - ROAD / 2f - 0.6f
                    sceneView.addChildNode(CubeNode(engine, Size(0.8f, 0.35f, 0.35f), materialInstance = benchMat).apply {
                        position = Position(bx, 0.18f, rz)
                    })
                }

                if (sd % 5 == 2) {
                    val tx = rx - ROAD / 2f - 1.2f
                    sceneView.addChildNode(CubeNode(engine, Size(0.2f, 1.8f, 0.2f), materialInstance = trunkMat).apply {
                        position = Position(tx, 0.9f, rz + 2f)
                    })
                    sceneView.addChildNode(SphereNode(engine, 0.8f, materialInstance = leafMat).apply {
                        position = Position(tx, 2.2f, rz + 2f)
                    })
                }
            }
        }
    }

    private fun buildCity() {
        sceneView.addChildNode(
            CubeNode(engine, Size(CITY, 0.3f, CITY), materialInstance = ml.createColorInstance(color = Color.rgb(0x4A, 0x8C, 0x3F))).apply {
                position = Position(0f, -0.15f, 0f)
                collisionShape = Box(Vector3(CITY, 0.3f, CITY))
            }
        )

        var rp = -HALF + BLOCK / 2f
        while (rp <= HALF) { roadCenters.add(rp); rp += BLOCK }

        val roadMat = ml.createColorInstance(color = Color.rgb(0x55, 0x55, 0x65))
        for (rc in roadCenters) {
            sceneView.addChildNode(CubeNode(engine, Size(ROAD, 0.06f, CITY), materialInstance = roadMat).apply { position = Position(rc, 0.02f, 0f) })
            sceneView.addChildNode(CubeNode(engine, Size(CITY, 0.06f, ROAD), materialInstance = roadMat).apply { position = Position(0f, 0.02f, rc) })
        }

        val swMat = ml.createColorInstance(color = Color.rgb(0xAA, 0xAA, 0xBB))
        val s = 0.4f; val o = ROAD / 2f + s / 2f
        for (rc in roadCenters) {
            sceneView.addChildNode(CubeNode(engine, Size(s, 0.08f, CITY), materialInstance = swMat).apply { position = Position(rc - o, 0.04f, 0f) })
            sceneView.addChildNode(CubeNode(engine, Size(s, 0.08f, CITY), materialInstance = swMat).apply { position = Position(rc + o, 0.04f, 0f) })
            sceneView.addChildNode(CubeNode(engine, Size(CITY, 0.08f, s), materialInstance = swMat).apply { position = Position(0f, 0.04f, rc - o) })
            sceneView.addChildNode(CubeNode(engine, Size(CITY, 0.08f, s), materialInstance = swMat).apply { position = Position(0f, 0.04f, rc + o) })
        }

        val colors = intArrayOf(
            Color.rgb(0x8B, 0x8B, 0x8B), Color.rgb(0xA0, 0x90, 0x70),
            Color.rgb(0x70, 0x80, 0x90), Color.rgb(0x90, 0x60, 0x50),
            Color.rgb(0x60, 0x70, 0x80), Color.rgb(0x80, 0x70, 0x60),
            Color.rgb(0x75, 0x75, 0x95), Color.rgb(0x8A, 0x7A, 0x6A)
        )

        for (i in 0 until roadCenters.size - 1) {
            for (j in 0 until roadCenters.size - 1) {
                val x1 = roadCenters[i] + ROAD / 2f + s + 0.2f
                val x2 = roadCenters[i + 1] - ROAD / 2f - s - 0.2f
                val z1 = roadCenters[j] + ROAD / 2f + s + 0.2f
                val z2 = roadCenters[j + 1] - ROAD / 2f - s - 0.2f
                val bw = x2 - x1; val bh = z2 - z1
                if (bw < 0.8f || bh < 0.8f) continue

                val cx = (x1 + x2) / 2f; val cz = (z1 + z2) / 2f
                val seed = ((cx * 137f + cz * 251f).toInt().let { if (it < 0) -it else it }) % 10000
                val n = 1 + (seed % 3)

                for (k in 0 until n) {
                    val sd = seed * 7 + k * 31
                    val h = 1.5f + (sd % 70).toFloat() / 10f
                    val w = 1.2f + (sd % 30).toFloat() / 15f
                    val d = 1.2f + ((sd / 7) % 30).toFloat() / 15f
                    val ci = sd % colors.size
                    val ox = ((sd % 100).toFloat() / 100f) * (bw - w).coerceAtLeast(0f)
                    val oz = (((sd / 10) % 100).toFloat() / 100f) * (bh - d).coerceAtLeast(0f)
                    val bcx = x1 + ox + w / 2f
                    val bcz = z1 + oz + d / 2f

                    sceneView.addChildNode(
                        CubeNode(engine, Size(w, h, d), materialInstance = ml.createColorInstance(color = colors[ci])).apply {
                            position = Position(bcx, h / 2f, bcz)
                        }
                    )
                    buildingAABBs.add(AABB(bcx - w / 2f, bcx + w / 2f, bcz - d / 2f, bcz + d / 2f))
                }
            }
        }
    }

    private fun placePlayer() {
        playerNode = SphereNode(engine, PLAYER_R, materialInstance = ml.createColorInstance(color = Color.rgb(0xFF, 0x6D, 0x00))).apply {
            position = Position(0f, P_Y, 0f)
        }
        sceneView.addChildNode(playerNode!!)
    }

    private fun findNearestNpc(): NpcData? {
        var best: NpcData? = null
        var bestDist = Float.MAX_VALUE
        for (npc in npcs) {
            val dx = npc.x - playerX
            val dz = npc.z - playerZ
            val d = sqrt(dx * dx + dz * dz)
            if (d < NPC_INTERACT_DIST && d < bestDist) {
                bestDist = d
                best = npc
            }
        }
        return best
    }

    private fun openChat(mn: MapNode) {
        startActivity(Intent(this, RealLifeChatActivity::class.java).apply {
            putExtra("CHAR_ID", mn.id)
            putExtra("CHAR_NAME", mn.name)
            putExtra("CHAR_AVATAR", mn.avatar.takeIf { it.length <= 2 } ?: "\uD83D\uDE42")
        })
    }

    private fun syncCamera() {
        cameraNode.position = Position(playerX + CAM_D, CAM_H, playerZ + CAM_D)
        cameraNode.lookAt(Position(playerX, 0f, playerZ))
    }

    override fun onDestroy() {
        sceneView.destroy()
        super.onDestroy()
    }
}

private val CATEGORY_COLORS = mapOf(
    "famiglia" to Color.rgb(0xE0, 0x50, 0x50),
    "amici" to Color.rgb(0x40, 0xA0, 0x40),
    "colleghi" to Color.rgb(0x50, 0x80, 0xE0),
    "partner" to Color.rgb(0xE0, 0x50, 0xB0),
    "vicini" to Color.rgb(0xE0, 0xA0, 0x30),
    "generici" to Color.rgb(0x90, 0x90, 0xA0)
)

package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.Skybox
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.AvatarConfig
import com.intelligame.huntix.reallife.BuildingDefs
import com.intelligame.huntix.reallife.BuildingType
import com.intelligame.huntix.reallife.DayNightManager
import com.intelligame.huntix.reallife.MapNode
import com.intelligame.huntix.reallife.Pets
import com.intelligame.huntix.reallife.RealLifeClient
import com.intelligame.huntix.reallife.WorldState
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.collision.Box
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
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
    private lateinit var buildingLabel: TextView
    private lateinit var enterBtn: LinearLayout
    private lateinit var minimap: MinimapView
    private var playerNode: SphereNode? = null
    private var playerBody: CubeNode? = null
    private var playerHead: SphereNode? = null
    private var playerLegL: CubeNode? = null
    private var playerLegR: CubeNode? = null
    private var playerArmL: CubeNode? = null
    private var playerArmR: CubeNode? = null
    private var playerRoot: Node? = null
    private var playerX = 0f
    private var playerZ = 0f
    private lateinit var avatarConfig: AvatarConfig
    private var lastFrameNs = 0L
    private var speechBubbleNpc: NpcData? = null
    private var speechBubbleTimer = 0f

    // Weather
    private lateinit var weatherOverlay: WeatherOverlay
    private var currentWeather = "Soleggiato"
    private var weatherCycleTimer = 0f
    private val WEATHER_CYCLE_INTERVAL = 300f // 5 minuti virtuali

    // Emote
    private lateinit var emoteBtn: TextView
    private lateinit var emoteBubble: TextView
    private var emoteTimer = 0f
    private var emotePlayerScale = 1f
    private var emoteAnimating = false

    // Pet
    private var petNode: PetNode? = null

    // Day/Night cycle
    private lateinit var dayNightManager: DayNightManager
    private lateinit var dayNightOverlay: DayNightOverlay
    private var skyboxUpdateTimer = 0f
    private var windowUpdateTimer = 0f
    private var windowMaterial: com.google.android.filament.MaterialInstance? = null
    private val windowMaterials = mutableListOf<com.google.android.filament.MaterialInstance>()
    private var lampLightMaterial: com.google.android.filament.MaterialInstance? = null
    private var timeLabel: TextView? = null

    private val engine get() = sceneView.engine
    private val ml get() = sceneView.materialLoader
    private val modelLoader get() = sceneView.modelLoader

    private val buildingAABBs = mutableListOf<com.intelligame.huntix.reallife.AABB>()
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

        avatarConfig = AvatarConfig.load(this)

        sceneView = SceneView(this).apply { cameraManipulator = null }
        sceneView.lifecycle = lifecycle

        cameraNode = CameraNode(engine).apply { far = 500f; near = 0.1f }
        sceneView.setCameraNode(cameraNode)

        buildCity()
        buildDetails()
        placePlayer()

        // Day/Night cycle
        dayNightManager = DayNightManager()
        dayNightOverlay = DayNightOverlay(this)

        // Weather overlay
        weatherOverlay = WeatherOverlay(this)

        // Emote bubble
        emoteBubble = TextView(this).apply {
            textSize = 24f; alpha = 0f
            setPadding(UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 4),
                UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 4))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 12).toFloat()
                setColor(0xDD1A1030.toInt())
                setStroke(1, 0x44FFFFFF)
            }
        }

        // Emote button (next to joystick)
        emoteBtn = TextView(this).apply {
            text = "🎭"; textSize = 22f; gravity = Gravity.CENTER
            isClickable = true; isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 24).toFloat()
                setColor(0xDD1A1030.toInt())
                setStroke(1, 0x44FFFFFF)
            }
            setPadding(UiKit.dp(this@CityActivity, 12), UiKit.dp(this@CityActivity, 10),
                UiKit.dp(this@CityActivity, 12), UiKit.dp(this@CityActivity, 10))
            setOnClickListener { showEmotePopup() }
        }

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

        buildingLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f; alpha = 0f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }

        enterBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 12).toFloat()
                setColor(Color.parseColor(UiKit.ACCENT))
            }
            setPadding(UiKit.dp(this@CityActivity, 16), UiKit.dp(this@CityActivity, 8),
                UiKit.dp(this@CityActivity, 16), UiKit.dp(this@CityActivity, 8))
            isClickable = true; isFocusable = true
            alpha = 0f
            addView(TextView(this@CityActivity).apply {
                text = "Entra"; textSize = 14f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
        }

        minimap = MinimapView(this)
        minimap.setPlayerColor(avatarConfig.shirtColor)

        val hud = TextView(this).apply {
            text = "  Joystick · avvicinati a un NPC o edificio"
            setTextColor(Color.WHITE); textSize = 11f; alpha = 0.5f
        }

        val backBtn = TextView(this).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
            setPadding(UiKit.dp(this@CityActivity, 12), UiKit.dp(this@CityActivity, 8), 0, 0)
        }

        val mapBtn = TextView(this).apply {
            text = "\uD83D\uDDFA\uFE0F"; textSize = 20f; setTextColor(Color.WHITE)
            isClickable = true; isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 20).toFloat()
                setColor(0xDD1A1030.toInt()); setStroke(1, 0x44FFFFFF)
            }
            setPadding(UiKit.dp(this@CityActivity, 10), UiKit.dp(this@CityActivity, 8),
                UiKit.dp(this@CityActivity, 10), UiKit.dp(this@CityActivity, 8))
            setOnClickListener {
                val intent = Intent(this@CityActivity, CityMapActivity::class.java)
                intent.putExtra("PLAYER_X", playerX)
                intent.putExtra("PLAYER_Z", playerZ)
                startActivity(intent)
            }
        }

        timeLabel = TextView(this).apply {
            textSize = 11f; setTextColor(Color.parseColor("#FFD86B"))
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setPadding(UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 4),
                UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 4))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 6).toFloat()
                setColor(0x55000000)
            }
        }

        val root = FrameLayout(this).apply {
            addView(sceneView)
            addView(dayNightOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(backBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = UiKit.dp(this@CityActivity, 8); marginStart = UiKit.dp(this@CityActivity, 4) })
            addView(mapBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = UiKit.dp(this@CityActivity, 8); marginStart = UiKit.dp(this@CityActivity, 52) })
            addView(npcNameLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 48) })
            addView(speechBubble, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 72) })
            addView(hud, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = UiKit.dp(this@CityActivity, 38) })
            addView(minimap, FrameLayout.LayoutParams(
                UiKit.dp(this@CityActivity, 110), UiKit.dp(this@CityActivity, 110)
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = UiKit.dp(this@CityActivity, 12); marginEnd = UiKit.dp(this@CityActivity, 12) })
            addView(timeLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = UiKit.dp(this@CityActivity, 128); marginEnd = UiKit.dp(this@CityActivity, 12) })
            addView(joystickView, FrameLayout.LayoutParams(
                UiKit.dp(this@CityActivity, 160), UiKit.dp(this@CityActivity, 160)
            ).apply { gravity = Gravity.BOTTOM or Gravity.START; marginStart = UiKit.dp(this@CityActivity, 24); bottomMargin = UiKit.dp(this@CityActivity, 32) })
            addView(buildingLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 96) })
            addView(enterBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER or Gravity.BOTTOM; bottomMargin = UiKit.dp(this@CityActivity, 200) })
            addView(weatherOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(emoteBubble, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = UiKit.dp(this@CityActivity, 116) })
            addView(emoteBtn, FrameLayout.LayoutParams(
                UiKit.dp(this@CityActivity, 48), UiKit.dp(this@CityActivity, 48)
            ).apply { gravity = Gravity.BOTTOM or Gravity.END; marginEnd = UiKit.dp(this@CityActivity, 20); bottomMargin = UiKit.dp(this@CityActivity, 40) })
        }
        sceneView.onTouchEvent = { event, _ ->
            if (event.action == MotionEvent.ACTION_UP) {
                val nearNpc = findNearestNpc()
                if (nearNpc != null) {
                    openChat(nearNpc.mapNode)
                } else {
                    val nearB = BuildingDefs.findNearest(playerX, playerZ)
                    if (nearB != null) openBuilding(nearB.first)
                }
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
                    updateBuildingLabel()
                    updateSpeechBubble(dt)
                    updateMinimap()
                    updateDayNight(dt)
                    updateWeather(dt)
                    updateEmote(dt)
                    updatePet(dt)
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
        playerRoot?.position = Position(playerX, 0f, playerZ)
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

    private fun updateBuildingLabel() {
        val nearB = BuildingDefs.findNearest(playerX, playerZ)
        if (nearB != null) {
            val (b, dist) = nearB
            buildingLabel.text = "${b.emoji}  ${b.name}"
            buildingLabel.alpha = (1f - (dist / BuildingDefs.NEAR_DISTANCE).coerceIn(0f, 1f))
            enterBtn.alpha = 1f
            enterBtn.setOnClickListener { openBuilding(b) }
        } else {
            buildingLabel.alpha = 0f
            enterBtn.alpha = 0f
        }
    }

    private fun openBuilding(b: com.intelligame.huntix.reallife.BuildingDef) {
        val intent = Intent(this, BuildingInteriorActivity::class.java)
        intent.putExtra(BuildingInteriorActivity.EXTRA_BUILDING_TYPE, b.type.ordinal)
        startActivity(intent)
    }

    private fun updateMinimap() {
        val npcDots = npcs.map {
            val cat = it.mapNode.category
            val color = CATEGORY_COLORS[cat] ?: 0xFF9090A0.toInt()
            Triple(it.x, it.z, color)
        }
        minimap.update(playerX, playerZ, npcDots)
    }

    private fun updateDayNight(dt: Float) {
        dayNightManager.advance(dt)
        dayNightOverlay.update(dayNightManager)

        // Update time label
        timeLabel?.text = "🕐 ${dayNightManager.getTimeString()} · ${dayNightManager.getPeriodLabel()}"

        // Rebuild skybox every 2 seconds (expensive operation)
        skyboxUpdateTimer -= dt
        if (skyboxUpdateTimer <= 0f) {
            skyboxUpdateTimer = 2f
            try {
                val sc = dayNightManager.getSkyColors()
                val skyR = Color.red(sc.topColor) / 255f
                val skyG = Color.green(sc.topColor) / 255f
                val skyB = Color.blue(sc.topColor) / 255f
                sceneView.skybox = Skybox.Builder().color(floatArrayOf(skyR, skyG, skyB, 1f)).build(engine)
                sceneView.mainLightNode?.intensity = dayNightManager.getLightIntensity()
            } catch (_: Exception) {}
        }

        // Update window/lamp colors every 5 seconds
        windowUpdateTimer -= dt
        if (windowUpdateTimer <= 0f) {
            windowUpdateTimer = 5f
            val winColor = dayNightManager.getWindowColor()
            for (wm in windowMaterials) {
                try {
                    val r = Color.red(winColor) / 255f
                    val g = Color.green(winColor) / 255f
                    val b = Color.blue(winColor) / 255f
                    wm.setParameter("color", com.google.android.filament.Color4f(r, g, b, 1f))
                } catch (_: Exception) {}
            }
            // Update lamp color
            val lampColor = dayNightManager.getLampColor()
            try {
                val r = Color.red(lampColor) / 255f
                val g = Color.green(lampColor) / 255f
                val b = Color.blue(lampColor) / 255f
                lampLightMaterial?.setParameter("color", com.google.android.filament.Color4f(r, g, b, 1f))
            } catch (_: Exception) {}
        }
    }

    private fun collides(x: Float, z: Float): Boolean {
        for (b in buildingAABBs) {
            if (x + PLAYER_R > b.minX && x - PLAYER_R < b.maxX &&
                z + PLAYER_R > b.minZ && z - PLAYER_R < b.maxZ) return true
        }
        return false
    }

    private fun updateWeather(dt: Float) {
        weatherCycleTimer -= dt
        if (weatherCycleTimer <= 0f) {
            weatherCycleTimer = WEATHER_CYCLE_INTERVAL
            val weathers = arrayOf("Soleggiato", "Soleggiato", "Soleggiato", "Nuvoloso", "Pioggia", "Temporale", "Nebbia")
            currentWeather = weathers.random()
        }
        weatherOverlay.setWeather(currentWeather)
        weatherOverlay.invalidate()

        // Temporale: flash periodico
        if (currentWeather == "Temporale") {
            if (Math.random() < 0.002f) {
                weatherOverlay.triggerFlash()
            }
        }

        // Regola luminosità in base al meteo
        val weatherBrightness = when (currentWeather) {
            "Nuvoloso" -> 0.7f
            "Pioggia" -> 0.6f
            "Temporale" -> 0.4f
            "Nebbia" -> 0.5f
            else -> 1f
        }
        sceneView.mainLightNode?.intensity = dayNightManager.getLightIntensity() * weatherBrightness
    }

    private fun showEmotePopup() {
        val popup = EmotePopup(this) { emoji, name ->
            emoteBubble.text = "$emoji $name"
            emoteBubble.alpha = 1f
            emoteTimer = 3f
            emoteAnimating = true
        }
        popup.show(emoteBtn)
    }

    private fun updateEmote(dt: Float) {
        if (emoteTimer > 0f) {
            emoteTimer -= dt
            val fadeIn = ((3f - emoteTimer) / 0.3f).coerceAtMost(1f)
            val fadeOut = (emoteTimer / 0.5f).coerceAtMost(1f)
            emoteBubble.alpha = fadeIn.coerceAtMost(fadeOut)

            // Player bounce animation
            if (emoteAnimating && playerRoot != null) {
                val t = 3f - emoteTimer
                val bounce = kotlin.math.sin(t * 8.0).toFloat() * 0.15f
                val scaleY = 1f + bounce.coerceIn(-0.15f, 0.15f)
                playerRoot?.scale = Position(1f, scaleY, 1f)
            }
        } else {
            emoteBubble.alpha = 0f
            if (emoteAnimating) {
                emoteAnimating = false
                playerRoot?.scale = Position(1f, 1f, 1f)
            }
        }
    }

    private fun updatePet(dt: Float) {
        val pet = petNode ?: return
        val stopped = joystickView.dx == 0f && joystickView.dy == 0f
        pet.updatePet(playerX, playerZ, dt, stopped)
    }

    private fun spawnPet() {
        val def = Pets.AVAILABLE.random()
        val bodyMat = ml.createColorInstance(color = def.bodyColor)
        val headMat = ml.createColorInstance(color = def.headColor)
        val eyeMat = ml.createColorInstance(color = 0xFF1A1A1A.toInt())
        val tailMat = ml.createColorInstance(color = def.headColor)
        val heartMat = ml.createColorInstance(color = 0xFFE91E63.toInt())
        val pet = PetNode(engine, def, bodyMat, headMat, eyeMat, tailMat, heartMat)
        pet.worldPosition = Position(playerX + 2f, 0f, playerZ)
        sceneView.addChildNode(pet)
        petNode = pet
    }

    private fun spawnProceduralTree(tx: Float, tz: Float, seed: Int,
        trunkMat: com.google.android.filament.MaterialInstance,
        leafMat: com.google.android.filament.MaterialInstance,
        leafLightMat: com.google.android.filament.MaterialInstance,
        leafDarkMat: com.google.android.filament.MaterialInstance
    ) {
        val treeMat = when (seed % 3) { 0 -> leafMat; 1 -> leafLightMat; else -> leafDarkMat }
        val treeH = 1.6f + (seed % 5).toFloat() * 0.15f
        sceneView.addChildNode(CubeNode(engine, Size(0.18f, treeH, 0.18f), materialInstance = trunkMat).apply {
            position = Position(tx, treeH / 2f, tz)
        })
        val canopyBase = treeH + 0.1f
        sceneView.addChildNode(SphereNode(engine, 0.55f + (seed % 3).toFloat() * 0.08f, materialInstance = treeMat).apply {
            position = Position(tx, canopyBase + 0.3f, tz)
        })
        sceneView.addChildNode(SphereNode(engine, 0.4f + (seed % 2).toFloat() * 0.1f, materialInstance = treeMat).apply {
            position = Position(tx + 0.2f, canopyBase + 0.15f, tz + 0.15f)
        })
        sceneView.addChildNode(SphereNode(engine, 0.35f + (seed % 2).toFloat() * 0.05f, materialInstance = leafLightMat).apply {
            position = Position(tx - 0.15f, canopyBase + 0.4f, tz - 0.1f)
        })
        if (seed % 3 == 0) {
            sceneView.addChildNode(CubeNode(engine, Size(0.3f, 0.06f, 0.06f), materialInstance = trunkMat).apply {
                position = Position(tx + 0.2f, treeH * 0.65f, tz)
            })
        }
    }

    private fun spawnProceduralCar(cx: Float, cz: Float, seed: Int,
        carColors: IntArray,
        carMat: com.google.android.filament.MaterialInstance?,
        windshieldMat: com.google.android.filament.MaterialInstance,
        wheelMat: com.google.android.filament.MaterialInstance,
        headlightMat: com.google.android.filament.MaterialInstance,
        tailLightMat: com.google.android.filament.MaterialInstance
    ) {
        val mat = carMat ?: ml.createColorInstance(color = carColors[seed % carColors.size])
        sceneView.addChildNode(CubeNode(engine, Size(1.4f, 0.3f, 0.7f), materialInstance = mat).apply {
            position = Position(cx, 0.22f, cz)
        })
        sceneView.addChildNode(CubeNode(engine, Size(0.4f, 0.15f, 0.65f), materialInstance = mat).apply {
            position = Position(cx + 0.5f, 0.38f, cz)
        })
        sceneView.addChildNode(CubeNode(engine, Size(0.35f, 0.2f, 0.65f), materialInstance = mat).apply {
            position = Position(cx - 0.5f, 0.4f, cz)
        })
        sceneView.addChildNode(CubeNode(engine, Size(0.6f, 0.25f, 0.6f), materialInstance = windshieldMat).apply {
            position = Position(cx, 0.5f, cz)
        })
        sceneView.addChildNode(CubeNode(engine, Size(0.08f, 0.12f, 0.68f), materialInstance = wheelMat).apply {
            position = Position(cx + 0.72f, 0.18f, cz)
        })
        sceneView.addChildNode(CubeNode(engine, Size(0.08f, 0.12f, 0.68f), materialInstance = wheelMat).apply {
            position = Position(cx - 0.72f, 0.18f, cz)
        })
        sceneView.addChildNode(SphereNode(engine, 0.06f, materialInstance = headlightMat).apply {
            position = Position(cx + 0.72f, 0.22f, cz - 0.25f)
        })
        sceneView.addChildNode(SphereNode(engine, 0.06f, materialInstance = headlightMat).apply {
            position = Position(cx + 0.72f, 0.22f, cz + 0.25f)
        })
        sceneView.addChildNode(SphereNode(engine, 0.05f, materialInstance = tailLightMat).apply {
            position = Position(cx - 0.72f, 0.22f, cz - 0.25f)
        })
        sceneView.addChildNode(SphereNode(engine, 0.05f, materialInstance = tailLightMat).apply {
            position = Position(cx - 0.72f, 0.22f, cz + 0.25f)
        })
        for (wx in floatArrayOf(-0.45f, 0.45f)) {
            for (wz in floatArrayOf(-0.3f, 0.3f)) {
                sceneView.addChildNode(SphereNode(engine, 0.1f, materialInstance = wheelMat).apply {
                    position = Position(cx + wx, 0.1f, cz + wz)
                })
                val hubMat = ml.createColorInstance(color = Color.rgb(0xBD, 0xBD, 0xBD))
                sceneView.addChildNode(SphereNode(engine, 0.05f, materialInstance = hubMat).apply {
                    position = Position(cx + wx, 0.1f, cz + wz + (if (wz > 0) 0.06f else -0.06f))
                })
            }
        }
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
            // Sync day/night manager with server time
            try {
                val parts = ws.time.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: 10
                    val minute = parts[1].toIntOrNull() ?: 0
                    dayNightManager.setHour(hour + minute / 60f)
                }
            } catch (_: Exception) {}

            // Set weather from server
            currentWeather = ws.weather
            weatherOverlay.setWeather(ws.weather)

            // Spawn pet after world is loaded
            withContext(Dispatchers.Main) { spawnPet() }
        }
    }

    private fun buildDetails() {
        val poleMat = ml.createColorInstance(color = Color.rgb(0x55, 0x55, 0x55))
        val lightMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xEE, 0xAA))
        lampLightMaterial = lightMat  // capture for day/night
        val benchMat = ml.createColorInstance(color = Color.rgb(0x8B, 0x5E, 0x3C))
        val benchDarkMat = ml.createColorInstance(color = Color.rgb(0x6D, 0x4C, 0x30))
        val trunkMat = ml.createColorInstance(color = Color.rgb(0x6B, 0x42, 0x26))
        val leafMat = ml.createColorInstance(color = Color.rgb(0x2E, 0x7D, 0x32))
        val leafLightMat = ml.createColorInstance(color = Color.rgb(0x43, 0xA0, 0x47))
        val leafDarkMat = ml.createColorInstance(color = Color.rgb(0x1B, 0x5E, 0x20))
        val bushMat = ml.createColorInstance(color = Color.rgb(0x38, 0x8E, 0x3C))
        val flowerColors = intArrayOf(
            0xFFE91E63.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(),
            0xFF9C27B0.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt()
        )
        val carColors = intArrayOf(
            Color.rgb(0xE5, 0x39, 0x35), // rosso
            Color.rgb(0x1E, 0x88, 0xE5), // blu
            Color.rgb(0xEC, 0xEF, 0xF1), // bianco
            Color.rgb(0xFF, 0xCA, 0x28), // giallo
            Color.rgb(0x21, 0x21, 0x21), // nero
            Color.rgb(0x4C, 0xAF, 0x50)  // verde
        )
        val wheelMat = ml.createColorInstance(color = Color.rgb(0x21, 0x21, 0x21))
        val windshieldMat = ml.createColorInstance(color = Color.rgb(0x90, 0xCA, 0xF9))
        val headlightMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xFF, 0xE0))
        val tailLightMat = ml.createColorInstance(color = Color.rgb(0xEF, 0x53, 0x50))
        val shadeMat = ml.createColorInstance(color = Color.rgb(0x75, 0x75, 0x75))
        val grassDetailMat = ml.createColorInstance(color = Color.rgb(0x66, 0xBB, 0x6A))

        // ── LAMPIONI MIGLIORATI + BANCHE MIGLIORATE + AUTO DETTAGLIATE ──
        for (i in roadCenters.indices) {
            for (j in roadCenters.indices) {
                val rx = roadCenters[i]
                val rz = roadCenters[j]
                val sd = ((rx * 173 + rz * 311).toInt().let { if (it < 0) -it else it }) % 1000

                // Lampione migliorato: palo + braccio + ombra + luce
                if (sd % 3 == 0) {
                    val lx = rx + ROAD / 2f + 0.8f
                    // Palo principale
                    sceneView.addChildNode(CubeNode(engine, Size(0.08f, 2.8f, 0.08f), materialInstance = poleMat).apply {
                        position = Position(lx, 1.4f, rz)
                    })
                    // Braccio orizzontale
                    sceneView.addChildNode(CubeNode(engine, Size(0.5f, 0.06f, 0.06f), materialInstance = poleMat).apply {
                        position = Position(lx - 0.25f, 2.8f, rz)
                    })
                    // Ombra lampione (disco piatto)
                    sceneView.addChildNode(CubeNode(engine, Size(0.35f, 0.04f, 0.35f), materialInstance = shadeMat).apply {
                        position = Position(lx - 0.25f, 2.75f, rz)
                    })
                    // Luce
                    sceneView.addChildNode(SphereNode(engine, 0.12f, materialInstance = lightMat).apply {
                        position = Position(lx - 0.25f, 2.65f, rz)
                    })
                    // Base
                    sceneView.addChildNode(CubeNode(engine, Size(0.2f, 0.15f, 0.2f), materialInstance = poleMat).apply {
                        position = Position(lx, 0.08f, rz)
                    })
                }

                // Panchina migliorata: seduta + schienale + 4 gambe
                if (sd % 4 == 1) {
                    val bx = rx - ROAD / 2f - 0.6f
                    // Seduta
                    sceneView.addChildNode(CubeNode(engine, Size(0.9f, 0.06f, 0.35f), materialInstance = benchMat).apply {
                        position = Position(bx, 0.35f, rz)
                    })
                    // Schienale
                    sceneView.addChildNode(CubeNode(engine, Size(0.9f, 0.3f, 0.06f), materialInstance = benchMat).apply {
                        position = Position(bx, 0.55f, rz - 0.15f)
                    })
                    // Gambe
                    for (gx in floatArrayOf(-0.35f, 0.35f)) {
                        sceneView.addChildNode(CubeNode(engine, Size(0.06f, 0.35f, 0.06f), materialInstance = benchDarkMat).apply {
                            position = Position(bx + gx, 0.18f, rz - 0.12f)
                        })
                        sceneView.addChildNode(CubeNode(engine, Size(0.06f, 0.35f, 0.06f), materialInstance = benchDarkMat).apply {
                            position = Position(bx + gx, 0.18f, rz + 0.12f)
                        })
                    }
                }

                // Auto — GLB con fallback procedurale
                if (sd % 6 == 3) {
                    val cx = rx + ROAD / 2f + 1.5f
                    val cz = rz + (if (sd % 2 == 0) 1.5f else -1.5f)
                    val carRot = if (sd % 2 == 0) 0f else 90f
                    val useCarGlb = try { assets.open("city_models/car.glb").close(); true } catch (_: Exception) { false }
                    if (useCarGlb) {
                        try {
                            modelLoader.loadModelInstanceAsync("city_models/car.glb") { instance ->
                                if (instance != null) {
                                    val node = ModelNode(instance).apply {
                                        position = Position(cx, 0f, cz)
                                        rotation = Rotation(y = carRot)
                                        scale = Position(0.005f, 0.005f, 0.005f)
                                    }
                                    sceneView.addChildNode(node)
                                }
                            }
                        } catch (_: Exception) {
                            spawnProceduralCar(cx, cz, sd, carColors, carMat = null, windshieldMat, wheelMat, headlightMat, tailLightMat)
                        }
                    } else {
                        spawnProceduralCar(cx, cz, sd, carColors, null, windshieldMat, wheelMat, headlightMat, tailLightMat)
                    }
                }
            }
        }

        // ── ALBERI MIGLIORATI (3 sfere sovrapposte per chioma) + CESPUGLI + FIORI ──
        val occupied = BuildingDefs.occupiedBlocks().toSet()
        val s = 0.4f
        for (i in 0 until roadCenters.size - 1) {
            for (j in 0 until roadCenters.size - 1) {
                val x1 = roadCenters[i] + ROAD / 2f + s + 0.3f
                val x2 = roadCenters[i + 1] - ROAD / 2f - s - 0.3f
                val z1 = roadCenters[j] + ROAD / 2f + s + 0.3f
                val z2 = roadCenters[j + 1] - ROAD / 2f - s - 0.3f
                val bw = x2 - x1; val bh = z2 - z1
                if (bw < 1f || bh < 1f) continue

                val cx = (x1 + x2) / 2f; val cz = (z1 + z2) / 2f
                val bx = Math.round(cx / 10f) * 10f; val bz = Math.round(cz / 10f) * 10f
                if (occupied.contains(Pair(bx, bz))) continue

                val seed = ((cx * 197 + cz * 337).toInt().let { if (it < 0) -it else it }) % 10000

                // 2-4 alberi — GLB con fallback procedurale
                val treeCount = 2 + seed % 3
                val useTreeGlb = try { assets.open("city_models/tree.glb").close(); true } catch (_: Exception) { false }
                for (t in 0 until treeCount) {
                    val ts = seed * 7 + t * 41
                    val tx = x1 + ((ts % 100).toFloat() / 100f) * bw
                    val tz = z1 + (((ts / 10) % 100).toFloat() / 100f) * bh
                    if (useTreeGlb) {
                        // GLB tree
                        try {
                            modelLoader.loadModelInstanceAsync("city_models/tree.glb") { instance ->
                                if (instance != null) {
                                    val scale = 0.008f + (ts % 3).toFloat() * 0.002f
                                    val node = ModelNode(instance).apply {
                                        position = Position(tx, 0f, tz)
                                        this.scale = Position(scale, scale, scale)
                                    }
                                    sceneView.addChildNode(node)
                                }
                            }
                        } catch (_: Exception) {
                            // Fallback to procedural
                            spawnProceduralTree(tx, tz, ts, trunkMat, leafMat, leafLightMat, leafDarkMat)
                        }
                    } else {
                        spawnProceduralTree(tx, tz, ts, trunkMat, leafMat, leafLightMat, leafDarkMat)
                    }
                }

                // 3-5 cespugli (2 sfere sovrapposte)
                val bushCount = 3 + (seed % 3)
                for (b in 0 until bushCount) {
                    val bs = seed * 13 + b * 29
                    val bx2 = x1 + ((bs % 100).toFloat() / 100f) * bw
                    val bz2 = z1 + (((bs / 10) % 100).toFloat() / 100f) * bh
                    val bushSize = 0.22f + (bs % 20).toFloat() / 100f
                    sceneView.addChildNode(SphereNode(engine, bushSize, materialInstance = bushMat).apply {
                        position = Position(bx2, bushSize, bz2)
                    })
                    sceneView.addChildNode(SphereNode(engine, bushSize * 0.7f, materialInstance = leafLightMat).apply {
                        position = Position(bx2 + bushSize * 0.4f, bushSize * 0.8f, bz2 + bushSize * 0.3f)
                    })
                }

                // 5-8 fiori (con gambo)
                val flowerCount = 5 + (seed % 4)
                for (f in 0 until flowerCount) {
                    val fs = seed * 11 + f * 37
                    val fx = x1 + ((fs % 100).toFloat() / 100f) * bw
                    val fz = z1 + (((fs / 10) % 100).toFloat() / 100f) * bh
                    val fMat = ml.createColorInstance(color = flowerColors[fs % flowerColors.size])
                    // Gambo
                    sceneView.addChildNode(CubeNode(engine, Size(0.02f, 0.15f, 0.02f), materialInstance = grassDetailMat).apply {
                        position = Position(fx, 0.08f, fz)
                    })
                    // Testa fiore
                    sceneView.addChildNode(SphereNode(engine, 0.06f + (fs % 5).toFloat() / 100f, materialInstance = fMat).apply {
                        position = Position(fx, 0.18f, fz)
                    })
                }
            }
        }

        // ── PISCINA (blocco [3][4] ≈ x=5, z=15) ──
        val poolX = 5f; val poolZ = 15f
        val waterMat = ml.createColorInstance(color = Color.rgb(0x42, 0xA5, 0xF5))
        val poolEdgeMat = ml.createColorInstance(color = Color.rgb(0xEC, 0xEF, 0xF1))
        val loungeMat = ml.createColorInstance(color = Color.rgb(0xFF, 0x98, 0x00))
        val loungeSeatMat = ml.createColorInstance(color = Color.rgb(0x8D, 0x6E, 0x63))

        // Acqua
        sceneView.addChildNode(CubeNode(engine, Size(3.5f, 0.1f, 2.5f), materialInstance = waterMat).apply {
            position = Position(poolX, 0.05f, poolZ)
        })
        // Muretto
        for (side in 0..3) {
            val (sx, sz, sw, sd2) = when (side) {
                0 -> floatArrayOf(poolX - 1.85f, poolZ, 0.15f, 2.6f)
                1 -> floatArrayOf(poolX + 1.85f, poolZ, 0.15f, 2.6f)
                2 -> floatArrayOf(poolX, poolZ - 1.35f, 3.7f, 0.15f)
                else -> floatArrayOf(poolX, poolZ + 1.35f, 3.7f, 0.15f)
            }
            sceneView.addChildNode(CubeNode(engine, Size(sw, 0.35f, sd2), materialInstance = poolEdgeMat).apply {
                position = Position(sx, 0.18f, sz)
            })
        }
        // Sdraio
        for (l in 0..2) {
            val lx = poolX + 2.5f + l * 1.2f
            sceneView.addChildNode(CubeNode(engine, Size(0.6f, 0.1f, 0.35f), materialInstance = loungeMat).apply {
                position = Position(lx, 0.15f, poolZ)
            })
            sceneView.addChildNode(CubeNode(engine, Size(0.6f, 0.35f, 0.06f), materialInstance = loungeSeatMat).apply {
                position = Position(lx, 0.32f, poolZ - 0.15f)
            })
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

        // ── Named buildings (edifici speciali) — GLB con fallback procedurale ──
        val doorMat = ml.createColorInstance(color = Color.rgb(0x3E, 0x27, 0x23))
        val awningColors = intArrayOf(
            Color.rgb(0xE5, 0x39, 0x35), Color.rgb(0x1E, 0x88, 0xE5),
            Color.rgb(0xFF, 0xCA, 0x28), Color.rgb(0x4C, 0xAF, 0x50)
        )
        val chimneyMat = ml.createColorInstance(color = Color.rgb(0x79, 0x55, 0x48))
        val balconyMat = ml.createColorInstance(color = Color.rgb(0xBD, 0xBD, 0xBD))
        val poleMat = ml.createColorInstance(color = Color.rgb(0x55, 0x55, 0x55))

        for (bd in BuildingDefs.BUILDINGS) {
            // Try loading GLB model first
            val glbPath = bd.glbModel
            val glbLoaded = if (glbPath != null) {
                try {
                    modelLoader.loadModelInstanceAsync(glbPath) { instance ->
                        if (instance != null) {
                            val modelNode = ModelNode(instance).apply {
                                position = Position(bd.x, 0f, bd.z)
                                scale = Position(bd.modelScale, bd.modelScale, bd.modelScale)
                            }
                            sceneView.addChildNode(modelNode)
                        }
                    }
                    true
                } catch (_: Exception) { false }
            } else false

            if (!glbLoaded) {
                // Fallback: enhanced procedural building
                val bMat = ml.createColorInstance(color = bd.color3D)
                val rMat = ml.createColorInstance(color = bd.roofColor)

                sceneView.addChildNode(
                    CubeNode(engine, Size(bd.width, bd.height, bd.depth), materialInstance = bMat).apply {
                        position = Position(bd.x, bd.height / 2f, bd.z)
                    }
                )
                sceneView.addChildNode(
                    CubeNode(engine, Size(bd.width + 0.4f, 0.35f, bd.depth + 0.4f), materialInstance = rMat).apply {
                        position = Position(bd.x, bd.height + 0.18f, bd.z)
                    }
                )
                sceneView.addChildNode(
                    CubeNode(engine, Size(bd.width * 0.6f, 0.2f, bd.depth * 0.6f), materialInstance = rMat).apply {
                        position = Position(bd.x, bd.height + 0.45f, bd.z)
                    }
                )
                sceneView.addChildNode(
                    CubeNode(engine, Size(0.7f, 1.2f, 0.1f), materialInstance = doorMat).apply {
                        position = Position(bd.x, 0.6f, bd.z + bd.depth / 2f + 0.05f)
                    }
                )
                val handleMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xD5, 0x4F))
                sceneView.addChildNode(SphereNode(engine, 0.04f, materialInstance = handleMat).apply {
                    position = Position(bd.x + 0.2f, 0.6f, bd.z + bd.depth / 2f + 0.12f)
                })
                val winMat = ml.createColorInstance(color = Color.rgb(0x90, 0xCA, 0xF9))
                windowMaterials.add(winMat)
                val winFrameMat = ml.createColorInstance(color = Color.rgb(0xE0, 0xE0, 0xE0))
                val wxOff = bd.width * 0.28f
                val wyBase = bd.height * 0.55f
                for (wy in floatArrayOf(wyBase, wyBase + 0.6f)) {
                    for (wx in floatArrayOf(-wxOff, wxOff)) {
                        sceneView.addChildNode(
                            CubeNode(engine, Size(0.35f, 0.35f, 0.08f), materialInstance = winMat).apply {
                                position = Position(bd.x + wx, wy, bd.z + bd.depth / 2f + 0.04f)
                            }
                        )
                        sceneView.addChildNode(
                            CubeNode(engine, Size(0.42f, 0.42f, 0.04f), materialInstance = winFrameMat).apply {
                                position = Position(bd.x + wx, wy, bd.z + bd.depth / 2f + 0.02f)
                            }
                        )
                    }
                }
                for (bx in floatArrayOf(-wxOff, wxOff)) {
                    sceneView.addChildNode(
                        CubeNode(engine, Size(0.6f, 0.06f, 0.3f), materialInstance = balconyMat).apply {
                            position = Position(bd.x + bx, bd.height * 0.55f - 0.22f, bd.z + bd.depth / 2f + 0.2f)
                        }
                    )
                    sceneView.addChildNode(
                        CubeNode(engine, Size(0.6f, 0.2f, 0.03f), materialInstance = balconyMat).apply {
                            position = Position(bd.x + bx, bd.height * 0.55f - 0.12f, bd.z + bd.depth / 2f + 0.35f)
                        }
                    )
                }
                if (bd.height > 2f) {
                    sceneView.addChildNode(
                        CubeNode(engine, Size(0.3f, 0.6f, 0.3f), materialInstance = chimneyMat).apply {
                            position = Position(bd.x + bd.width * 0.3f, bd.height + 0.65f, bd.z - bd.depth * 0.2f)
                        }
                    )
                }
                val awningColor = awningColors[BuildingDefs.BUILDINGS.indexOf(bd) % awningColors.size]
                val awningMat = ml.createColorInstance(color = awningColor)
                sceneView.addChildNode(
                    CubeNode(engine, Size(bd.width * 0.8f, 0.06f, 0.5f), materialInstance = awningMat).apply {
                        position = Position(bd.x, 1.5f, bd.z + bd.depth / 2f + 0.3f)
                    }
                )
                for (px in floatArrayOf(-bd.width * 0.35f, bd.width * 0.35f)) {
                    sceneView.addChildNode(
                        CubeNode(engine, Size(0.04f, 1.5f, 0.04f), materialInstance = poleMat).apply {
                            position = Position(bd.x + px, 0.75f, bd.z + bd.depth / 2f + 0.5f)
                        }
                    )
                }
            }

            buildingAABBs.add(bd.aabb())
        }

        // ── Procedural buildings (fill remaining blocks) DETTAGLIATI ──
        val colors = intArrayOf(
            0xFFB3D9FF.toInt(), // azzurro chiaro
            0xFFFFCDD2.toInt(), // rosa chiaro
            0xFFC8E6C9.toInt(), // verde mint
            0xFFFFF9C4.toInt(), // giallo pastello
            0xFFD1C4E9.toInt(), // lavanda
            0xFFFFE0B2.toInt(), // pesca
            0xFFB2DFDB.toInt(), // turchese
            0xFFF0F4C3.toInt()  // lime
        )
        val roofColors = intArrayOf(
            0xFF8D6E63.toInt(), 0xFF78909C.toInt(), 0xFFA1887F.toInt(),
            0xFF90A4AE.toInt(), 0xFFBCAAA4.toInt(), 0xFFB0BEC5.toInt(),
            0xFF80CBC4.toInt(), 0xFFC5E1A5.toInt()
        )
        val occupied = BuildingDefs.occupiedBlocks().toSet()
        val proceduralWindowMat = ml.createColorInstance(color = Color.rgb(0xBB, 0xDE, 0xFB))
        windowMaterials.add(proceduralWindowMat)
        val proceduralDoorMat = ml.createColorInstance(color = Color.rgb(0x5D, 0x40, 0x37))
        val proceduralRoofMat = ml.createColorInstance(color = Color.rgb(0x79, 0x55, 0x48))

        for (i in 0 until roadCenters.size - 1) {
            for (j in 0 until roadCenters.size - 1) {
                val x1 = roadCenters[i] + ROAD / 2f + s + 0.2f
                val x2 = roadCenters[i + 1] - ROAD / 2f - s - 0.2f
                val z1 = roadCenters[j] + ROAD / 2f + s + 0.2f
                val z2 = roadCenters[j + 1] - ROAD / 2f - s - 0.2f
                val bw = x2 - x1; val bh = z2 - z1
                if (bw < 0.8f || bh < 0.8f) continue

                val cx = (x1 + x2) / 2f; val cz = (z1 + z2) / 2f
                val bx = Math.round(cx / 10f) * 10f; val bz = Math.round(cz / 10f) * 10f
                if (occupied.contains(Pair(bx, bz))) continue

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

                    // Main body
                    sceneView.addChildNode(
                        CubeNode(engine, Size(w, h, d), materialInstance = ml.createColorInstance(color = colors[ci])).apply {
                            position = Position(bcx, h / 2f, bcz)
                        }
                    )
                    // Roof
                    sceneView.addChildNode(
                        CubeNode(engine, Size(w + 0.2f, 0.2f, d + 0.2f), materialInstance = ml.createColorInstance(color = roofColors[ci])).apply {
                            position = Position(bcx, h + 0.1f, bcz)
                        }
                    )
                    // Door (front)
                    sceneView.addChildNode(
                        CubeNode(engine, Size(0.4f, 0.8f, 0.08f), materialInstance = proceduralDoorMat).apply {
                            position = Position(bcx, 0.4f, bcz + d / 2f + 0.04f)
                        }
                    )
                    // Windows (1-2 per lato frontale)
                    val pwxOff = w * 0.25f
                    val pWinCount = if (w > 1.5f) 2 else 1
                    for (pw in 0 until pWinCount) {
                        val pwx = if (pWinCount == 1) 0f else (pw * 2 - 1) * pwxOff
                        sceneView.addChildNode(
                            CubeNode(engine, Size(0.25f, 0.25f, 0.06f), materialInstance = proceduralWindowMat).apply {
                                position = Position(bcx + pwx, h * 0.55f, bcz + d / 2f + 0.03f)
                            }
                        )
                    }
                    // Balcony (per edifici alti)
                    if (h > 2.5f && sd % 3 == 0) {
                        sceneView.addChildNode(
                            CubeNode(engine, Size(w * 0.5f, 0.05f, 0.25f), materialInstance = balconyMat).apply {
                                position = Position(bcx, h * 0.45f, bcz + d / 2f + 0.15f)
                            }
                        )
                    }
                    buildingAABBs.add(com.intelligame.huntix.reallife.AABB(bcx - w / 2f, bcx + w / 2f, bcz - d / 2f, bcz + d / 2f))
                }
            }
        }
    }

    private fun placePlayer() {
        val root = Node(engine).apply { position = Position(0f, 0f, 0f) }

        val skinMat = ml.createColorInstance(color = avatarConfig.skinColor)
        val shirtMat = ml.createColorInstance(color = avatarConfig.shirtColor)
        val pantsMat = ml.createColorInstance(color = avatarConfig.pantsColor)
        val shoeMat = ml.createColorInstance(color = avatarConfig.shoeColor)
        val hairMat = ml.createColorInstance(color = avatarConfig.hairColor)

        // Corpo (maglia)
        playerBody = CubeNode(engine, Size(0.35f, 0.4f, 0.2f), materialInstance = shirtMat).apply {
            position = Position(0f, 0.55f, 0f)
        }
        root.addChildNode(playerBody!!)

        // Testa
        playerHead = SphereNode(engine, 0.17f, materialInstance = skinMat).apply {
            position = Position(0f, 0.95f, 0f)
        }
        root.addChildNode(playerHead!!)

        // Capelli (sopra la testa)
        val hairNode = CubeNode(engine, Size(0.32f, 0.08f, 0.22f), materialInstance = hairMat).apply {
            position = Position(0f, 1.12f, 0f)
        }
        root.addChildNode(hairNode)

        // Braccia
        playerArmL = CubeNode(engine, Size(0.1f, 0.35f, 0.1f), materialInstance = skinMat).apply {
            position = Position(-0.28f, 0.52f, 0f)
        }
        root.addChildNode(playerArmL!!)
        playerArmR = CubeNode(engine, Size(0.1f, 0.35f, 0.1f), materialInstance = skinMat).apply {
            position = Position(0.28f, 0.52f, 0f)
        }
        root.addChildNode(playerArmR!!)

        // Gambe
        playerLegL = CubeNode(engine, Size(0.12f, 0.35f, 0.12f), materialInstance = pantsMat).apply {
            position = Position(-0.1f, 0.18f, 0f)
        }
        root.addChildNode(playerLegL!!)
        playerLegR = CubeNode(engine, Size(0.12f, 0.35f, 0.12f), materialInstance = pantsMat).apply {
            position = Position(0.1f, 0.18f, 0f)
        }
        root.addChildNode(playerLegR!!)

        // Scarpe
        val shoeL = CubeNode(engine, Size(0.14f, 0.06f, 0.18f), materialInstance = shoeMat).apply {
            position = Position(-0.1f, 0.0f, 0.02f)
        }
        root.addChildNode(shoeL)
        val shoeR = CubeNode(engine, Size(0.14f, 0.06f, 0.18f), materialInstance = shoeMat).apply {
            position = Position(0.1f, 0.0f, 0.02f)
        }
        root.addChildNode(shoeR)

        playerRoot = root
        sceneView.addChildNode(root)
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
        petNode?.destroy()
        playerRoot?.destroy()
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

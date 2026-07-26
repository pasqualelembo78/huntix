package com.intelligame.huntix.ui

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.Skybox
import com.google.android.filament.IndirectLight
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.R
import com.intelligame.huntix.SentryDebugManager
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.AvatarConfig
import com.intelligame.huntix.reallife.BuildingDefs
import com.intelligame.huntix.reallife.BuildingType
import com.intelligame.huntix.reallife.CoordinateConverter
import com.intelligame.huntix.reallife.DayNightManager
import com.intelligame.huntix.reallife.MapNode
import com.intelligame.huntix.reallife.OsmCityBuilder
import com.intelligame.huntix.reallife.OsmClient
import com.intelligame.huntix.reallife.OsmData
import com.intelligame.huntix.reallife.Pets
import com.intelligame.huntix.reallife.RealLifeClient
import com.intelligame.huntix.reallife.WorldState
import io.github.sceneview.SceneView
import io.github.sceneview.safeDestroySkybox
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.collision.Box
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
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
    private var cameraAngle = CAM_ANGLE_DEFAULT
    private var rotationStartAngle = 0f
    private var rotationStartCamAngle = 0f
    private var isRotating = false

    // Zoom (pinch to zoom)
    private var cameraDistance = CAM_D
    private var pinchStartDistance = 0f
    private var pinchStartCamDist = 0f

    // Single-finger drag to rotate camera
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartCamAngle = 0f
    private var isDragging = false
    private lateinit var avatarConfig: AvatarConfig
    private var lastFrameNs = 0L
    @Volatile private var destroyed = false
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
    private var memoryCheckTimer = 5f // Wait 5s before first memory check
    private val MEMORY_CHECK_INTERVAL = 2f
    private var windowMaterial: com.google.android.filament.MaterialInstance? = null
    private val windowMaterials = mutableListOf<com.google.android.filament.MaterialInstance>()
    private var lampLightMaterial: com.google.android.filament.MaterialInstance? = null
    private var currentSkybox: Skybox? = null
    private var timeLabel: TextView? = null
    private var osmStatusLabel: TextView? = null

    private val engine get() = sceneView.engine
    private val ml get() = sceneView.materialLoader

    private val buildingAABBs = mutableListOf<com.intelligame.huntix.reallife.AABB>()
    private val roadCenters = mutableListOf<Float>()

    // OSM data
    private var osmData: OsmData? = null
    private var osmCityBuilder: OsmCityBuilder? = null
    private var osmLoading = false
    private var osmLoaded = false
    private var osmPhase = 0
    private var buildGeneration = 0

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
        private const val TAG = "City3D"
        private const val CITY = 8000f
        private const val BLOCK = 80f
        private const val ROAD = 6f
        private const val HALF = CITY / 2f
        private const val P_Y = 0.35f
        private const val CAM_H = 1.7f
        private const val CAM_D = 2.5f
        private const val CAM_ANGLE_DEFAULT = (kotlin.math.PI / 4f).toFloat()
        private const val SPEED = 25f
        private const val PLAYER_R = 0.3f
        private const val NPC_SPEED = 2.5f
        private const val NPC_BODY_R = 0.2f
        private const val NPC_HEAD_R = 0.15f
        private const val NPC_HEIGHT = 0.8f
        private const val NPC_INTERACT_DIST = 4f
        private const val SPEECH_DURATION = 4f
        private const val MAX_NPCS = 30

        // Foggia — Centro
        private const val OSM_CENTER_LAT = 41.4649
        private const val OSM_CENTER_LON = 15.5387
        private const val OSM_RADIUS_METERS = 2000
        private const val CAM_D_MIN = 1.5f
        private const val CAM_D_MAX = 8f
    }

    private var sceneReady = false
    private var loadingOverlay: FrameLayout? = null
    private var loadingLabel: TextView? = null

    // Target city coordinates (from intent or default Foggia)
    private var targetLat = OSM_CENTER_LAT
    private var targetLon = OSM_CENTER_LON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(TAG, "onCreate START")

        avatarConfig = AvatarConfig.load(this)
        AppLog.d(TAG, "AvatarConfig loaded")

        OsmClient.init(this)
        AppLog.d(TAG, "OsmClient initialized")

        try {
            AppLog.d(TAG, "Creating SceneView...")
            sceneView = SceneView(this).apply { cameraManipulator = null }
            AppLog.d(TAG, "SceneView created, engine=${sceneView.engine}")
            sceneView.lifecycle = lifecycle

            cameraNode = CameraNode(engine).apply { far = 500f; near = 0.1f }
            sceneView.setCameraNode(cameraNode)
            AppLog.d(TAG, "CameraNode created and set")

            // Graphics quality: improve lighting
            sceneView.mainLightNode?.apply {
                intensity = 1.5f
            }
            AppLog.d(TAG, "Main light configured")
        } catch (e: Exception) {
            AppLog.e(TAG, "SceneView creation FAILED", e)
            Sentry.captureException(e)
            SentryDebugManager.breadcrumb("city3d", "SceneView creation failed", mapOf("error" to e.message))
            finish()
            return
        }

        // Read target city from intent (for city search/teleport)
        targetLat = intent.getDoubleExtra("TARGET_LAT", OSM_CENTER_LAT)
        targetLon = intent.getDoubleExtra("TARGET_LON", OSM_CENTER_LON)
        AppLog.d(TAG, "Target city: lat=$targetLat, lon=$targetLon")

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
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        val searchBtn = TextView(this).apply {
            text = "🔍"; textSize = 20f; setTextColor(Color.WHITE)
            isClickable = true; isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 20).toFloat()
                setColor(0xDD1A1030.toInt()); setStroke(1, 0x44FFFFFF)
            }
            setPadding(UiKit.dp(this@CityActivity, 10), UiKit.dp(this@CityActivity, 8),
                UiKit.dp(this@CityActivity, 10), UiKit.dp(this@CityActivity, 8))
            setOnClickListener {
                val intent = Intent(this@CityActivity, CitySearchActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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

        osmStatusLabel = TextView(this).apply {
            textSize = 10f; setTextColor(Color.parseColor("#88CCFF"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setPadding(UiKit.dp(this@CityActivity, 6), UiKit.dp(this@CityActivity, 2),
                UiKit.dp(this@CityActivity, 6), UiKit.dp(this@CityActivity, 2))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@CityActivity, 4).toFloat()
                setColor(0x44000000)
            }
            text = "📥 Caricamento mappa..."
        }

        val root = FrameLayout(this).apply {
            // Sky-blue background ensures visibility even if SceneView fails to render
            setBackgroundColor(Color.rgb(135, 193, 233))
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
            addView(searchBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; topMargin = UiKit.dp(this@CityActivity, 8); marginStart = UiKit.dp(this@CityActivity, 100) })
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
            addView(osmStatusLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = UiKit.dp(this@CityActivity, 152); marginEnd = UiKit.dp(this@CityActivity, 12) })
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

            // Debug log button (small, bottom-right above emote)
            val debugLogBtn = TextView(this@CityActivity).apply {
                text = "\uD83D\uDD27"; textSize = 16f; setTextColor(Color.parseColor("#888888"))
                isClickable = true; isFocusable = true
                setPadding(UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 6),
                    UiKit.dp(this@CityActivity, 8), UiKit.dp(this@CityActivity, 6))
                setOnClickListener {
                    startActivity(Intent(this@CityActivity, CityDebugLogActivity::class.java))
                }
            }
            addView(debugLogBtn, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.END; marginEnd = UiKit.dp(this@CityActivity, 20); bottomMargin = UiKit.dp(this@CityActivity, 92) })

            // Loading overlay (shown while city builds, dismissed when done)
            loadingLabel = TextView(this@CityActivity).apply {
                text = "Caricamento citta 3D..."
                setTextColor(Color.WHITE); textSize = 16f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
                gravity = Gravity.CENTER
            }
            loadingOverlay = FrameLayout(this@CityActivity).apply {
                setBackgroundColor(Color.parseColor("#CC0D0620"))
                addView(loadingLabel, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER })
            }
            addView(loadingOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        sceneView.onTouchEvent = { event, _ ->
            // Two-finger gestures: rotation + pinch zoom
            if (event.pointerCount == 2) {
                val dx = event.getX(1) - event.getX(0)
                val dy = event.getY(1) - event.getY(0)
                val currentDistance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        rotationStartAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        rotationStartCamAngle = cameraAngle
                        pinchStartDistance = currentDistance
                        pinchStartCamDist = cameraDistance
                        isRotating = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isRotating) {
                            val currentAngle: Float = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                            val deltaAngle: Float = currentAngle - rotationStartAngle
                            cameraAngle = rotationStartCamAngle + deltaAngle
                        }
                        if (pinchStartDistance > 0f) {
                            val scale = currentDistance / pinchStartDistance
                            cameraDistance = (pinchStartCamDist / scale).coerceIn(CAM_D_MIN, CAM_D_MAX)
                        }
                        syncCamera()
                    }
                    MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                        isRotating = false
                        pinchStartDistance = 0f
                    }
                }
                true
            }
            // Single-finger drag: rotate camera around player
            else if (event.pointerCount == 1) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.getX()
                        dragStartY = event.getY()
                        dragStartCamAngle = cameraAngle
                        isDragging = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            val deltaX = event.getX() - dragStartX
                            cameraAngle = dragStartCamAngle - deltaX * 0.005f
                            syncCamera()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        val deltaX = event.getX() - dragStartX
                        val deltaY = event.getY() - dragStartY
                        if (kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat() < 20f) {
                            val nearNpc = findNearestNpc()
                            if (nearNpc != null) openChat(nearNpc.mapNode) else {
                                val nearB = BuildingDefs.findNearest(playerX, playerZ)
                                if (nearB != null) openBuilding(nearB.first)
                            }
                        }
                        true
                    }
                    else -> true
                }
            } else if (event.action == MotionEvent.ACTION_UP) {
                val nearNpc = findNearestNpc()
                if (nearNpc != null) {
                    openChat(nearNpc.mapNode)
                } else {
                    val nearB = BuildingDefs.findNearest(playerX, playerZ)
                    if (nearB != null) openBuilding(nearB.first)
                }
                true
            } else {
                true
            }
        }

        setContentView(root)
        joystickView.bringToFront()
    }

    override fun onResume() {
        super.onResume()
        AppLog.d(TAG, "onResume, destroyed=$destroyed")
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

override fun onPause() {
        super.onPause()
        AppLog.d(TAG, "onPause")
        Choreographer.getInstance().removeFrameCallback(frameCb)
        // DON'T cancel rebuildJob — let build continue in background
        // If user returns, build will be done or still running
    }

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return

            if (!sceneReady) {
                sceneReady = true
                AppLog.d(TAG, "=== FIRST FRAME === engine ready, loading OSM data")
                SentryDebugManager.breadcrumb("city3d", "First frame rendered, starting OSM load")
                // Load OSM data in background, then build city
                loadOsmData()
                // Create skybox on first frame (engine GL context now ready)
                createSkybox()
            }

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
                    checkMemoryPressure(dt)
                }
            } catch (e: Exception) { Sentry.captureException(e) }
            lastFrameNs = frameTimeNanos
            if (!destroyed) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updatePlayer(dt: Float) {
        val jdx = joystickView.dx
        val jdy = joystickView.dy
        if (jdx == 0f && jdy == 0f) return

        val mag = sqrt(jdx * jdx + jdy * jdy)
        var nx = jdx / mag
        var ny = jdy / mag

        // Rotate joystick input by camera angle so "forward" = camera direction
        val cosA = kotlin.math.cos(cameraAngle.toDouble()).toFloat()
        val sinA = kotlin.math.sin(cameraAngle.toDouble()).toFloat()
        val worldNx = nx * cosA - ny * sinA
        val worldNy = nx * sinA + ny * cosA

        val step = SPEED * dt * mag * 2.5f  // increased speed

        val newX = playerX + worldNx * step
        val newZ = playerZ + worldNy * step

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
        overridePendingTransition(R.anim.scale_in, R.anim.scale_out)
    }

    private fun updateMinimap() {
        val npcDots = npcs.map {
            val cat = it.mapNode.category
            val color = CATEGORY_COLORS[cat] ?: 0xFF9090A0.toInt()
            Triple(it.x, it.z, color)
        }
        minimap.update(playerX, playerZ, npcDots)
    }

    private fun createSkybox() {
        if (destroyed) return
        try {
            AppLog.d(TAG, "Creating skybox...")
            val sc = dayNightManager.getSkyColors()
            val skyR = Color.red(sc.topColor) / 255f
            val skyG = Color.green(sc.topColor) / 255f
            val skyB = Color.blue(sc.topColor) / 255f
            currentSkybox?.let { engine.safeDestroySkybox(it) }
            val newSkybox = Skybox.Builder()
                .color(floatArrayOf(skyR, skyG, skyB, 1f))
                .build(engine)
            sceneView.skybox = newSkybox
            currentSkybox = newSkybox
            sceneView.mainLightNode?.intensity = dayNightManager.getLightIntensity()
            skyboxUpdateTimer = 10f
            AppLog.d(TAG, "Skybox created successfully")
        } catch (e: Exception) {
            AppLog.e(TAG, "Skybox creation FAILED", e)
            Sentry.captureException(e)
            skyboxUpdateTimer = 0.5f  // Retry fast if engine wasn't ready
        }
    }

    private fun updateDayNight(dt: Float) {
        dayNightManager.advance(dt)
        dayNightOverlay.update(dayNightManager)

        // Update time label
        timeLabel?.text = "🕐 ${dayNightManager.getTimeString()} · ${dayNightManager.getPeriodLabel()}"

        skyboxUpdateTimer -= dt
        if (skyboxUpdateTimer <= 0f && !destroyed) {
            try {
                val sc = dayNightManager.getSkyColors()
                val skyR = Color.red(sc.topColor) / 255f
                val skyG = Color.green(sc.topColor) / 255f
                val skyB = Color.blue(sc.topColor) / 255f
                currentSkybox?.let { engine.safeDestroySkybox(it) }
                
                val newSkybox = Skybox.Builder()
                    .color(floatArrayOf(skyR, skyG, skyB, 1f))
                    .build(engine)
                sceneView.skybox = newSkybox
                currentSkybox = newSkybox
                
                sceneView.mainLightNode?.intensity = dayNightManager.getLightIntensity()
                skyboxUpdateTimer = 10f  // Success: retry in 10s
            } catch (e: Exception) {
                Sentry.captureException(e)
                skyboxUpdateTimer = 0.5f  // Failure: retry fast next frame
            }
        }

        // Update window/lamp colors every 5 seconds
        windowUpdateTimer -= dt
        if (windowUpdateTimer <= 0f) {
            windowUpdateTimer = 5f
            // Window/lamp color updates deferred to day/night overlay
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
            }
        }
    }

    private fun loadNpcs() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { RealLifeClient.getMap() }.getOrNull() ?: return@launch
            if (destroyed) return@launch
            val grid = state.width.toFloat()

            val maxNpcs = MAX_NPCS
            var loaded = 0
            for (mn in state.nodes) {
                if (destroyed || loaded >= maxNpcs) return@launch
                try {
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
                    loaded++
                } catch (e: Exception) { Sentry.captureException(e) }
            }
        }
    }

    private fun loadWorldState() {
        lifecycleScope.launch {
            if (destroyed) return@launch
            val ws = withContext(Dispatchers.IO) { RealLifeClient.getWorldState() }.getOrNull() ?: return@launch
            if (destroyed) return@launch
            // Sync day/night manager with server time
            try {
                val parts = ws.time.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: 10
                    val minute = parts[1].toIntOrNull() ?: 0
                    dayNightManager.setHour(hour + minute / 60f)
                }
            } catch (e: Exception) { Sentry.captureException(e) }

            // Set weather from server
            currentWeather = ws.weather
            weatherOverlay.setWeather(ws.weather)

            // Spawn pet after world is loaded
            if (!destroyed) withContext(Dispatchers.Main) { spawnPet() }
        }
    }

    private fun buildDetailsStreetFurniture() {
        val poleMat = ml.createColorInstance(color = Color.rgb(0x55, 0x55, 0x55))
        val lightMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xEE, 0xAA))
        lampLightMaterial = lightMat
        val benchMat = ml.createColorInstance(color = Color.rgb(0x8B, 0x5E, 0x3C))
        val carColors = intArrayOf(
            Color.rgb(0xE5, 0x39, 0x35),
            Color.rgb(0x1E, 0x88, 0xE5),
            Color.rgb(0xEC, 0xEF, 0xF1),
            Color.rgb(0xFF, 0xCA, 0x28),
            Color.rgb(0x21, 0x21, 0x21),
            Color.rgb(0x4C, 0xAF, 0x50)
        )
        val wheelMat = ml.createColorInstance(color = Color.rgb(0x21, 0x21, 0x21))
        val windshieldMat = ml.createColorInstance(color = Color.rgb(0x90, 0xCA, 0xF9))
        val headlightMat = ml.createColorInstance(color = Color.rgb(0xFF, 0xFF, 0xE0))
        val tailLightMat = ml.createColorInstance(color = Color.rgb(0xEF, 0x53, 0x50))

        for (i in roadCenters.indices) {
            for (j in roadCenters.indices) {
                val rx = roadCenters[i]
                val rz = roadCenters[j]
                val sd = ((rx * 173 + rz * 311).toInt().let { if (it < 0) -it else it }) % 1000

                if (sd % 5 == 0) {
                    val lx = rx + ROAD / 2f + 0.8f
                    sceneView.addChildNode(CubeNode(engine, Size(0.08f, 2.8f, 0.08f), materialInstance = poleMat).apply {
                        position = Position(lx, 1.4f, rz)
                    })
                    sceneView.addChildNode(SphereNode(engine, 0.12f, materialInstance = lightMat).apply {
                        position = Position(lx - 0.25f, 2.65f, rz)
                    })
                }

                if (sd % 6 == 1) {
                    val bx = rx - ROAD / 2f - 0.6f
                    sceneView.addChildNode(CubeNode(engine, Size(0.9f, 0.06f, 0.35f), materialInstance = benchMat).apply {
                        position = Position(bx, 0.35f, rz)
                    })
                    sceneView.addChildNode(CubeNode(engine, Size(0.9f, 0.3f, 0.06f), materialInstance = benchMat).apply {
                        position = Position(bx, 0.55f, rz - 0.15f)
                    })
                }

                if (sd % 10 == 3) {
                    val cx = rx + ROAD / 2f + 1.5f
                    val cz = rz + (if (sd % 2 == 0) 1.5f else -1.5f)
                    spawnProceduralCar(cx, cz, sd, carColors, null, windshieldMat, wheelMat, headlightMat, tailLightMat)
                }
            }
        }
    }

    private fun buildDetailsVegetation() {
        val trunkMat = ml.createColorInstance(color = Color.rgb(0x6B, 0x42, 0x26))
        val leafMat = ml.createColorInstance(color = Color.rgb(0x2E, 0x7D, 0x32))
        val leafLightMat = ml.createColorInstance(color = Color.rgb(0x43, 0xA0, 0x47))
        val leafDarkMat = ml.createColorInstance(color = Color.rgb(0x1B, 0x5E, 0x20))
        val bushMat = ml.createColorInstance(color = Color.rgb(0x38, 0x8E, 0x3C))
        val flowerColors = intArrayOf(
            0xFFE91E63.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(),
            0xFF9C27B0.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt()
        )
        val grassDetailMat = ml.createColorInstance(color = Color.rgb(0x66, 0xBB, 0x6A))

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

                val treeCount = 1
                for (t in 0 until treeCount) {
                    val ts = seed * 7 + t * 41
                    val tx = x1 + ((ts % 100).toFloat() / 100f) * bw
                    val tz = z1 + (((ts / 10) % 100).toFloat() / 100f) * bh
                    spawnProceduralTree(tx, tz, ts, trunkMat, leafMat, leafLightMat, leafDarkMat)
                }

                val bushCount = 1
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

                val flowerCount = 2
                for (f in 0 until flowerCount) {
                    val fs = seed * 11 + f * 37
                    val fx = x1 + ((fs % 100).toFloat() / 100f) * bw
                    val fz = z1 + (((fs / 10) % 100).toFloat() / 100f) * bh
                    val fMat = ml.createColorInstance(color = flowerColors[fs % flowerColors.size])
                    sceneView.addChildNode(CubeNode(engine, Size(0.02f, 0.15f, 0.02f), materialInstance = grassDetailMat).apply {
                        position = Position(fx, 0.08f, fz)
                    })
                    sceneView.addChildNode(SphereNode(engine, 0.06f + (fs % 5).toFloat() / 100f, materialInstance = fMat).apply {
                        position = Position(fx, 0.18f, fz)
                    })
                }
            }
        }
    }

    private fun buildDetailsPool() {
        val poolX = 5f; val poolZ = 15f
        val waterMat = ml.createColorInstance(color = Color.rgb(0x42, 0xA5, 0xF5))
        val poolEdgeMat = ml.createColorInstance(color = Color.rgb(0xEC, 0xEF, 0xF1))
        val loungeMat = ml.createColorInstance(color = Color.rgb(0xFF, 0x98, 0x00))
        val loungeSeatMat = ml.createColorInstance(color = Color.rgb(0x8D, 0x6E, 0x63))

        sceneView.addChildNode(CubeNode(engine, Size(3.5f, 0.1f, 2.5f), materialInstance = waterMat).apply {
            position = Position(poolX, 0.05f, poolZ)
        })
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

    private fun buildDetails() {
        buildDetailsStreetFurniture()
        buildDetailsVegetation()
        buildDetailsPool()
    }

    private fun loadOsmData() {
        if (osmLoading || osmLoaded) {
            AppLog.d(TAG, "loadOsmData: skipped (loading=$osmLoading, loaded=$osmLoaded)")
            return
        }
        osmLoading = true
        AppLog.d(TAG, "loadOsmData: START (lat=$targetLat, lon=$targetLon)")
        updateLoadingText("Preparazione mappa...")

        // Move ALL heavy work to a coroutine so main thread stays free
        lifecycleScope.launch {
            // Initialize coordinate converter for target city
            CoordinateConverter.init(targetLat, targetLon)

            // 1. Load mini-chunk from assets (IO thread - file + JSON parsing)
            updateLoadingText("Caricamento mappa da assets...")
            AppLog.d(TAG, "loadOsmData: loading mini-chunk from assets (IO)...")
            val miniData = withContext(Dispatchers.IO) {
                OsmClient.loadMiniChunk()
            }
            val hasEnoughOsmData = miniData?.let { it.roads.size >= 5 && it.buildings.size >= 3 } == true
            AppLog.d(TAG, "loadOsmData: mini-chunk roads=${miniData?.roads?.size ?: 0}, buildings=${miniData?.buildings?.size ?: 0}, hasEnough=$hasEnoughOsmData")
            SentryDebugManager.breadcrumb("city3d", "OSM mini-chunk loaded", mapOf(
                "roads" to (miniData?.roads?.size ?: 0),
                "buildings" to (miniData?.buildings?.size ?: 0),
                "hasEnough" to hasEnoughOsmData
            ))

            if (destroyed) return@launch

            if (hasEnoughOsmData) {
                // Build immediately with mini-chunk on GL thread
                updateLoadingText("Costruzione citta (mini OSM)...")
                AppLog.d(TAG, "loadOsmData: rebuilding city with mini OSM data...")
                withContext(Dispatchers.Main) {
                    if (!destroyed) rebuildCityWithOsm(miniData!!)
                }
                // Dismiss loading screen — user sees mini-city while full build runs in background
                withContext(Dispatchers.Main) {
                    if (!destroyed) {
                        osmStatusLabel?.text = "Mappa OSM (mini) — aggiornamento in corso..."
                        dismissLoading()
                    }
                }
            } else {
                // Fallback: build grid city across multiple frames to prevent ANR
                updateLoadingText("Costruzione citta griglia...")
                AppLog.d(TAG, "loadOsmData: mini-chunk insufficient, building grid city (phased)")
                withContext(Dispatchers.Main) {
                    if (!destroyed) {
                        buildCityGridPhased()
                    }
                }
            }

            // 2. Download full km² data in background (works for both mini and grid paths)
            AppLog.d(TAG, "loadOsmData: starting background download of full OSM data...")
            try {
                AppLog.d(TAG, "OSM download: START (IO thread)")
                val data = withContext(Dispatchers.IO) {
                    OsmClient.fetchAreaCached(targetLat, targetLon, OSM_RADIUS_METERS)
                }
                if (destroyed) {
                    AppLog.w(TAG, "OSM download complete but activity destroyed, skipping")
                    return@launch
                }
                AppLog.d(TAG, "OSM download: COMPLETE (roads=${data.roads.size}, buildings=${data.buildings.size}, trees=${data.trees.size})")
                SentryDebugManager.breadcrumb("city3d", "OSM full data downloaded", mapOf(
                    "roads" to data.roads.size,
                    "buildings" to data.buildings.size,
                    "trees" to data.trees.size
                ))
                osmData = data
                osmLoaded = true
                osmLoading = false

                // Rebuild city with full OSM data on GL thread
                withContext(Dispatchers.Main) {
                    if (!destroyed) {
                        AppLog.d(TAG, "Rebuilding city with FULL OSM data on GL thread...")
                        rebuildCityWithOsm(data)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "OSM download FAILED: ${e.message}", e)
                Sentry.captureException(e)
                SentryDebugManager.breadcrumb("city3d", "OSM download failed", mapOf("error" to e.message))
                osmLoading = false
                if (!hasEnoughOsmData) {
                    withContext(Dispatchers.Main) {
                        if (!destroyed) {
                            AppLog.d(TAG, "Rebuilding with grid city after OSM failure (phased)")
                            buildCityGridPhased()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!destroyed) {
                            osmStatusLabel?.text = "Aggiornamento OSM fallito (usa mini)"
                        }
                    }
                }
            }
        }
    }

    private fun clearCityNodes() {
        // Remove all child nodes except camera and light
        val toRemove = sceneView.childNodes.filter {
            it != cameraNode && it.javaClass.simpleName != "LightNode"
        }
        for (node in toRemove) {
            sceneView.removeChildNode(node)
        }
        buildingAABBs.clear()
        windowMaterials.clear()
        roadCenters.clear()
        playerRoot = null
    }

    private var rebuildJob: kotlinx.coroutines.Job? = null

    private fun rebuildCityWithOsm(data: OsmData) {
        try {
            AppLog.d(TAG, "rebuildCityWithOsm: START (roads=${data.roads.size}, buildings=${data.buildings.size})")
            SentryDebugManager.breadcrumb("city3d", "Rebuilding city with OSM", mapOf(
                "roads" to data.roads.size,
                "buildings" to data.buildings.size
            ))

            // Cancel any previous rebuild in progress
            rebuildJob?.cancel()

            // Remove old grid city nodes to prevent handle arena overflow
            clearCityNodes()
            AppLog.d(TAG, "Old nodes cleared")

            osmCityBuilder = OsmCityBuilder(sceneView)
            osmPhase = 0

// ALL phases run as coroutine with yield between each phase
                    // This prevents ANR by never blocking the main thread for more than ~1s per frame
                    rebuildJob = lifecycleScope.launch {
                        try {
                            // Phase 1: terrain + roads
                            AppLog.d(TAG, "Phase 1: Terrain and roads...")
                            osmCityBuilder!!.buildPhase1_TerrainAndRoads(data, CITY)
                            osmPhase = 1
                            AppLog.d(TAG, "Phase 1 complete (nodes=${osmCityBuilder!!.getCurrentNodeCount()})")
                            osmStatusLabel?.text = "Strade completata · Edifici..."
                            kotlinx.coroutines.yield()

                    if (destroyed) return@launch

                    // Populate roadCenters from OSM roads (for NPC navigation + minimap)
                    val rcSet = mutableSetOf<Float>()
                    for (way in data.roads) {
                        for (node in way.nodes) {
                            rcSet.add(node.localX)
                            rcSet.add(node.localZ)
                        }
                    }
                    roadCenters.addAll(rcSet)
                    // Re-place player and sync camera after clearing old nodes
                    try { placePlayer() } catch (e: Exception) { Sentry.captureException(e) }
                    syncCamera()
                    minimap.setRoads(roadCenters, HALF)

                    if (destroyed) return@launch

                    // Phase 2: colosseum + buildings
                    AppLog.d(TAG, "Phase 2: Colosseum and buildings...")
                    osmCityBuilder!!.buildPhase2_ColosseumAndBuildings(data)
                    osmPhase = 2
                    AppLog.d(TAG, "Phase 2 complete (nodes=${osmCityBuilder!!.getCurrentNodeCount()})")
                    osmStatusLabel?.text = "Edifici completati · Alberi..."
                    kotlinx.coroutines.yield()

                    if (destroyed) return@launch

                    // Phase 3: trees + vegetation
                    AppLog.d(TAG, "Phase 3: Trees and vegetation...")
                    osmCityBuilder!!.buildPhase3_TreesAndVegetation(data, CITY)
                    osmPhase = 3
                    AppLog.d(TAG, "Phase 3 complete (nodes=${osmCityBuilder!!.getCurrentNodeCount()})")
                    osmStatusLabel?.text = "Alberi completati · Arredo..."
                    kotlinx.coroutines.yield()

                    if (destroyed) return@launch

                    // Phase 4: furniture + cars
                    AppLog.d(TAG, "Phase 4: Furniture and cars...")
                    osmCityBuilder!!.buildPhase4_FurnitureAndCars(data)
                    osmPhase = 4
                    AppLog.d(TAG, "Phase 4 complete (nodes=${osmCityBuilder!!.getCurrentNodeCount()})")
                    osmStatusLabel?.text = "Arredo completato · Dettagli..."
                    kotlinx.coroutines.yield()

                    if (destroyed) return@launch

                    // Phase 5: POI details + signs + restaurants + shops
                    AppLog.d(TAG, "Phase 5: Details, POI, signs...")
                    osmCityBuilder!!.buildPhase5_Details(data)
                    osmPhase = 5

                    // Copy collision data
                    buildingAABBs.clear()
                    buildingAABBs.addAll(osmCityBuilder!!.buildingAABBs)
                    windowMaterials.clear()
                    windowMaterials.addAll(osmCityBuilder!!.windowMaterials)
                    lampLightMaterial = osmCityBuilder!!.lampLightMaterial
                    minimap.invalidate()
                    osmStatusLabel?.text = "Mappa OSM completa (1km²)"
                    AppLog.d(TAG, "Phase 5 complete — CITY BUILD FINISHED (total nodes=${osmCityBuilder!!.getCurrentNodeCount()}, AABBs=${buildingAABBs.size})")
                    SentryDebugManager.breadcrumb("city3d", "City build finished", mapOf(
                        "nodes" to osmCityBuilder!!.getCurrentNodeCount(),
                        "aabbs" to buildingAABBs.size
                    ))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    AppLog.d(TAG, "Rebuild cancelled (likely onPause or new rebuild triggered)")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Rebuild FAILED", e)
                    Sentry.captureException(e)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private fun buildCityGridPhased() {
        AppLog.d(TAG, "buildCityGridPhased: START")
        // Phase G1: Ground + roads + sidewalks
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (destroyed) return
                try {
                    AppLog.d(TAG, "Grid Phase G1: Ground, roads, sidewalks...")
                    buildCityGroundAndRoads()
                    AppLog.d(TAG, "Grid Phase G1 complete")
                } catch (e: Exception) { AppLog.e(TAG, "Grid Phase G1 FAILED", e); Sentry.captureException(e); return }

                // Phase G2: Named buildings
                Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (destroyed) return
                        try {
                            AppLog.d(TAG, "Grid Phase G2: Named buildings...")
                            buildCityNamedBuildings()
                            AppLog.d(TAG, "Grid Phase G2 complete")
                        } catch (e: Exception) { AppLog.e(TAG, "Grid Phase G2 FAILED", e); Sentry.captureException(e); return }

                        // Phase G3: Procedural buildings
                        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                            override fun doFrame(frameTimeNanos: Long) {
                                if (destroyed) return
                                try {
                                    AppLog.d(TAG, "Grid Phase G3: Procedural buildings (batched)...")
                                    buildCityProceduralBuildings {
                                        if (destroyed) return@buildCityProceduralBuildings
                                        AppLog.d(TAG, "Grid Phase G3 complete")

                                        // Phase D1: Street furniture (lamps, benches, cars)
                                        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                                            override fun doFrame(frameTimeNanos: Long) {
                                                if (destroyed) return
                                                try {
                                                    AppLog.d(TAG, "Grid Phase D1: Street furniture...")
                                                    buildDetailsStreetFurniture()
                                                    AppLog.d(TAG, "Grid Phase D1 complete")
                                                } catch (e: Exception) { AppLog.e(TAG, "Grid Phase D1 FAILED", e); Sentry.captureException(e); return }

                                                // Phase D2: Vegetation + pool
                                                Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                                                    override fun doFrame(frameTimeNanos: Long) {
                                                        if (destroyed) return
                                                        try {
                                                            AppLog.d(TAG, "Grid Phase D2: Vegetation + pool...")
                                                            buildDetailsVegetation()
                                                            buildDetailsPool()
                                                            AppLog.d(TAG, "Grid Phase D2 complete")
                                                        } catch (e: Exception) { AppLog.e(TAG, "Grid Phase D2 FAILED", e); Sentry.captureException(e); return }

                                                        // Final: place player, camera, minimap, dismiss loading
                                                        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                                                            override fun doFrame(frameTimeNanos: Long) {
                                                                if (destroyed) return
                                                                try {
                                                                    AppLog.d(TAG, "Grid Final: Player, camera, minimap...")
                                                                    placePlayer()
                                                                    syncCamera()
                                                                    minimap.setRoads(roadCenters, HALF)
                                                                    osmStatusLabel?.text = "Citta griglia (download OSM...)"
                                                                    dismissLoading()
                                                                    loadNpcs()
                                                                    loadWorldState()
                                                                    AppLog.d(TAG, "Grid city build COMPLETE (total nodes=${sceneView.childNodes.size})")
                                                                } catch (e: Exception) { AppLog.e(TAG, "Grid Final FAILED", e); Sentry.captureException(e) }
                                                            }
                                                        })
                                                    }
                                                })
                                            }
                                        })
                                    }
                                } catch (e: Exception) { AppLog.e(TAG, "Grid Phase G3 FAILED", e); Sentry.captureException(e) }
                            }
                        })
                    }
                })
            }
        })
    }

    private fun buildCityGroundAndRoads() {
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
    }

    private fun buildCityNamedBuildings() {
        // ── Named buildings (edifici speciali) — GLB con fallback procedurale ──
        val doorMat = ml.createColorInstance(color = Color.rgb(0x3E, 0x27, 0x23))
        val awningColors = intArrayOf(
            Color.rgb(0xE5, 0x39, 0x35), Color.rgb(0x1E, 0x88, 0xE5),
            Color.rgb(0xFF, 0xCA, 0x28), Color.rgb(0x4C, 0xAF, 0x50)
        )

        for (bd in BuildingDefs.BUILDINGS) {
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
                CubeNode(engine, Size(0.7f, 1.2f, 0.1f), materialInstance = doorMat).apply {
                    position = Position(bd.x, 0.6f, bd.z + bd.depth / 2f + 0.05f)
                }
            )
            val winMat = ml.createColorInstance(color = Color.rgb(0x90, 0xCA, 0xF9))
            windowMaterials.add(winMat)
            val wxOff = bd.width * 0.28f
            val wyBase = bd.height * 0.55f
            sceneView.addChildNode(
                CubeNode(engine, Size(0.35f, 0.35f, 0.08f), materialInstance = winMat).apply {
                    position = Position(bd.x - wxOff, wyBase, bd.z + bd.depth / 2f + 0.04f)
                }
            )
            sceneView.addChildNode(
                CubeNode(engine, Size(0.35f, 0.35f, 0.08f), materialInstance = winMat).apply {
                    position = Position(bd.x + wxOff, wyBase, bd.z + bd.depth / 2f + 0.04f)
                }
            )
            val awningColor = awningColors[BuildingDefs.BUILDINGS.indexOf(bd) % awningColors.size]
            val awningMat = ml.createColorInstance(color = awningColor)
            sceneView.addChildNode(
                CubeNode(engine, Size(bd.width * 0.8f, 0.06f, 0.5f), materialInstance = awningMat).apply {
                    position = Position(bd.x, 1.5f, bd.z + bd.depth / 2f + 0.3f)
                }
            )

            buildingAABBs.add(bd.aabb())
        }
    }

    private fun buildCityProceduralBuildings(onDone: () -> Unit) {
        // ── Procedural buildings (fill remaining blocks) — BATCHED ──
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
        val s = 0.4f
        val proceduralWindowMat = ml.createColorInstance(color = Color.rgb(0xBB, 0xDE, 0xFB))
        windowMaterials.add(proceduralWindowMat)
        val proceduralDoorMat = ml.createColorInstance(color = Color.rgb(0x5D, 0x40, 0x37))

        // Pre-compute all block data to iterate in small batches
        data class BlockData(val bcx: Float, val bcz: Float, val w: Float, val h: Float, val d: Float, val ci: Int, val roofCi: Int, val aabb: com.intelligame.huntix.reallife.AABB)
        val blocks = mutableListOf<BlockData>()
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
                val n = 1 + (seed % 2)
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
                    blocks.add(BlockData(bcx, bcz, w, h, d, ci, ci, com.intelligame.huntix.reallife.AABB(bcx - w / 2f, bcx + w / 2f, bcz - d / 2f, bcz + d / 2f)))
                }
            }
        }

        // Process in batches of 5 blocks per frame
        val BATCH = 5
        var idx = 0

        fun processBatch() {
            if (destroyed || idx >= blocks.size) {
                onDone()
                return
            }
            val end = (idx + BATCH).coerceAtMost(blocks.size)
            for (k in idx until end) {
                val b = blocks[k]
                sceneView.addChildNode(
                    CubeNode(engine, Size(b.w, b.h, b.d), materialInstance = ml.createColorInstance(color = colors[b.ci])).apply {
                        position = Position(b.bcx, b.h / 2f, b.bcz)
                    }
                )
                sceneView.addChildNode(
                    CubeNode(engine, Size(b.w + 0.2f, 0.2f, b.d + 0.2f), materialInstance = ml.createColorInstance(color = roofColors[b.roofCi])).apply {
                        position = Position(b.bcx, b.h + 0.1f, b.bcz)
                    }
                )
                sceneView.addChildNode(
                    CubeNode(engine, Size(0.4f, 0.8f, 0.08f), materialInstance = proceduralDoorMat).apply {
                        position = Position(b.bcx, 0.4f, b.bcz + b.d / 2f + 0.04f)
                    }
                )
                buildingAABBs.add(b.aabb)
            }
            idx = end
            Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) { processBatch() }
            })
        }
        processBatch()
    }

    private fun placePlayer() {
        AppLog.d(TAG, "placePlayer: creating player node at (0, 0, 0)")
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
        val camX = playerX + cameraDistance * kotlin.math.cos(cameraAngle.toDouble()).toFloat()
        val camZ = playerZ + cameraDistance * kotlin.math.sin(cameraAngle.toDouble()).toFloat()
        cameraNode.position = Position(camX, CAM_H, camZ)
        cameraNode.lookAt(Position(playerX, 0f, playerZ))
    }

    private fun updateLoadingText(msg: String) {
        AppLog.d(TAG, "LOADING: $msg")
        loadingLabel?.text = msg
        // Update OSM status label too
        osmStatusLabel?.text = msg
    }

    private fun dismissLoading() {
        AppLog.d(TAG, "=== LOADING COMPLETE ===")
        SentryDebugManager.breadcrumb("city3d", "Loading complete")
        loadingOverlay?.let { overlay ->
            overlay.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction { overlay.alpha = 0f }
                .start()
        }
    }

    private fun checkMemoryPressure(dt: Float) {
        memoryCheckTimer -= dt
        if (memoryCheckTimer > 0f) return
        memoryCheckTimer = MEMORY_CHECK_INTERVAL

        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val thresholdMb = memInfo.threshold / (1024 * 1024)

        AppLog.d(TAG, "Memory check: ${availMb}MB available, threshold=${thresholdMb}MB")
        if (availMb < thresholdMb + 80L) {
            AppLog.w(TAG, "LOW MEMORY! ${availMb}MB available, finishing activity")
            Sentry.captureMessage("CityActivity: low memory (${availMb}MB avail, threshold ${thresholdMb}MB) — finishing")
            finish()
        }
    }

    override fun onDestroy() {
        AppLog.d(TAG, "onDestroy: cleaning up (destroyed was $destroyed)")
        destroyed = true
        Choreographer.getInstance().removeFrameCallback(frameCb)
        rebuildJob?.cancel()
        rebuildJob = null
        currentSkybox = null
        
        // Explicitly destroy Filament engine to free handle arena
        try {
            sceneView.engine.destroy()
        } catch (e: Exception) {
            // Ignore
        }
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

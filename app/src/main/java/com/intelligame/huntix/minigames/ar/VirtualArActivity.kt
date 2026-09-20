package com.intelligame.huntix.minigames.ar

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.MaterialInstance
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.R
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.bridge.CityTilePreloader
import com.intelligame.huntix.ui.JoystickView
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import java.util.HashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HUNTIX VIRTUAL AR.
 *
 * Scena 3D COMPLETAMENTE virtuale: nessun uso della fotocamera reale, nessun
 * piano/anchors ARCore. Il dispositivo e' solo una "finestra" sul mondo
 * generato da Huntix a partire dalla tile OSM attorno alla posizione virtuale
 * (che e' distinta dal GPS reale). Il personaggio viene mosso col joystick e
 * la camera lo segue in terza persona; trascinando il dito si ruota la vista.
 */
class VirtualArActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "VAR_LAT"
        const val EXTRA_LNG = "VAR_LNG"
        const val EXTRA_POI_LAT = "VAR_POI_LAT"
        const val EXTRA_POI_LNG = "VAR_POI_LNG"
        const val EXTRA_POI_NAME = "VAR_POI_NAME"
        const val EXTRA_POI_TYPE = "VAR_POI_TYPE"

        private const val TAG = "VirtualAr"

        // Raggio (m) del mondo OSM importato attorno alla posizione virtuale.
        private const val WORLD_RADIUS_M = 220f
        // Velocita' di camminata del personaggio (m/s).
        private const val WALK_SPEED = 4.5f
        // Limite di sicurezza: il personaggio resta dentro il mondo importato.
        private const val WALK_LIMIT_M = WORLD_RADIUS_M * 0.9f
        // Camera terza persona.
        private const val CAM_DIST = 6.5f
        private const val CAM_TARGET_H = 1.35f
        private const val CAM_DEFAULT_PITCH = 0.42f
        private const val CAM_MIN_PITCH = 0.06f
        private const val CAM_MAX_PITCH = 1.25f
        private const val LOOK_SENSITIVITY = 0.006f

        private const val SKY_R = 0.53f
        private const val SKY_G = 0.65f
        private const val SKY_B = 0.77f
    }

    private lateinit var sceneView: SceneView
    private lateinit var hud: FrameLayout
    private lateinit var statusText: TextView
    private var joystick: JoystickView? = null

    private var character: Node? = null
    private val materials = HashMap<Int, MaterialInstance>()

    private var poiLat = 0.0
    private var poiLng = 0.0
    private var poiName = "Punto virtuale"

    // Stato del personaggio (metri, origine sul POI virtuale).
    private var charX = 0f
    private var charZ = 4f
    // Camera orbitale in terza persona.
    private var camYaw = 0f
    private var camPitch = CAM_DEFAULT_PITCH
    private var lastFrameNanos = 0L
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_virtual_ar)

        sceneView = findViewById(R.id.sceneView)
        hud = findViewById(R.id.arOverlay)

        poiLat = intent.getDoubleExtra(EXTRA_POI_LAT, intent.getDoubleExtra(EXTRA_LAT, Double.NaN))
        poiLng = intent.getDoubleExtra(EXTRA_POI_LNG, intent.getDoubleExtra(EXTRA_LNG, Double.NaN))
        poiName = intent.getStringExtra(EXTRA_POI_NAME) ?: "Punto virtuale"
        if (poiLat.isNaN() || poiLng.isNaN()) {
            AppLog.w(TAG, "Posizione virtuale assente: uso piazza di default")
            poiLat = 41.46290
            poiLng = 15.54320
        }
        AppLog.i(TAG, "Virtual AR avviata attorno a $poiLat,$poiLng ($poiName)")

        setupScene()
        buildHud()
        initInput()
        loadWorldAsync()
    }

    private fun setupScene() {
        runCatching {
            sceneView.environment = sceneView.environmentLoader.createKTX1Environment(
                iblAssetFile = "environments/neutral/neutral_ibl.ktx",
                skyboxAssetFile = null
            )
        }.onFailure { AppLog.w(TAG, "IBL non caricata: ${it.message}") }

        runCatching {
            val opts = sceneView.renderer.clearOptions
            opts.clearColor = floatArrayOf(SKY_R, SKY_G, SKY_B, 1f)
            opts.clear = true
            sceneView.renderer.clearOptions = opts
        }.onFailure { AppLog.w(TAG, "clearOptions non applicabili: ${it.message}") }

        runCatching {
            sceneView.mainLightNode?.let { light ->
                light.setIntensity(1.1f, 1.0f)
                light.lightDirection = Float3(0.45f, -1f, 0.35f)
            }
        }.onFailure { AppLog.w(TAG, "luce principale non configurabile: ${it.message}") }

        sceneView.cameraNode.near = 0.1f
        sceneView.cameraNode.far = 1500f
    }

    private fun initInput() {
        sceneView.onTouchEvent = { event, _ ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.x
                    touchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - touchX
                    val dy = event.y - touchY
                    camYaw -= dx * LOOK_SENSITIVITY
                    camPitch = (camPitch + dy * LOOK_SENSITIVITY)
                        .coerceIn(CAM_MIN_PITCH, CAM_MAX_PITCH)
                    touchX = event.x
                    touchY = event.y
                }
            }
            true
        }

        sceneView.onFrame = { frameTimeNanos -> onRenderFrame(frameTimeNanos) }
    }

    private fun onRenderFrame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f
        else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos
        if (dt > 0f) updateCharacter(dt)
        updateCamera()
    }

    /** Muove il personaggio in XZ rispetto all'orientamento della camera. */
    private fun updateCharacter(dt: Float) {
        val j = joystick ?: return
        val ch = character ?: return
        val ix = j.dx
        val iy = j.dy
        if (kotlin.math.abs(ix) < 0.06f && kotlin.math.abs(iy) < 0.06f) return

        val forward = -iy
        val strafe = ix
        val fx = sin(camYaw)
        val fz = cos(camYaw)
        // Verso "destra schermo" = (-cos yaw, 0, sin yaw).
        val rx = -fz
        val rz = fx
        var mx = fx * forward + rx * strafe
        var mz = fz * forward + rz * strafe
        val len = hypot(mx, mz)
        if (len < 1e-4f) return
        mx /= len
        mz /= len

        charX += mx * WALK_SPEED * dt
        charZ += mz * WALK_SPEED * dt
        val dist = hypot(charX, charZ)
        if (dist > WALK_LIMIT_M) {
            charX = charX / dist * WALK_LIMIT_M
            charZ = charZ / dist * WALK_LIMIT_M
        }

        ch.position = Position(charX, 0f, charZ)
        ch.rotation = Rotation(0f, Math.toDegrees(atan2(mx.toDouble(), mz.toDouble())).toFloat(), 0f)
    }

    private fun updateCamera() {
        val cp = cos(camPitch)
        val sp = sin(camPitch)
        val eye = Position(
            charX - sin(camYaw) * cp * CAM_DIST,
            CAM_TARGET_H + sp * CAM_DIST,
            charZ - cos(camYaw) * cp * CAM_DIST
        )
        val center = Position(charX, CAM_TARGET_H, charZ)
        sceneView.cameraNode.lookAt(eye, center, Float3(0f, 1f, 0f))
    }

    // ---------------------------------------------------------------- mondo

    private fun loadWorldAsync() {
        statusText.text = "Carico il mondo Huntix…"
        Thread {
            val json = runCatching { CityTilePreloader.ensureGeoJsonSync(this, poiLat, poiLng) }
                .onFailure { AppLog.w(TAG, "tile non scaricata: ${it.message}") }
                .getOrNull()
            val model = VirtualArScene.parse(json, poiLat, poiLng, WORLD_RADIUS_M)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                buildWorld(model)
            }
        }.start()
    }

    private fun buildWorld(model: VirtualArScene.Model?) {
        val root = Node(sceneView.engine)
        sceneView.addChildNode(root)

        buildGround(root)
        if (model == null) {
            statusText.text = "⚠️ Mappa non disponibile: mondo vuoto"
        } else {
            buildRoads(root, model)
            buildBuildings(root, model)
            buildPoiMarkers(root, model)
            buildPoiBeacon(root)
            statusText.text = "🌍 $poiName — ${model.buildings.size} edifici · ${model.roads.size} strade"
        }

        createCharacter(root)
        spawnNpcs(root, model)

        AppLog.i(TAG, "Mondo costruito: ${model?.buildings?.size ?: 0} edifici")
    }

    private fun buildGround(root: Node) {
        val size = WORLD_RADIUS_M * 2.6f
        val ground = CubeNode(
            sceneView.engine,
            Float3(size, 0.4f, size),
            Float3(0f, 0f, 0f),
            material(0xFF3E5B32.toInt())
        )
        ground.position = Position(0f, -0.2f, 0f)
        root.addChildNode(ground)
    }

    private fun buildRoads(root: Node, model: VirtualArScene.Model) {
        val mat = material(0xFF2B2F36.toInt())
        for (road in model.roads) {
            val n = minOf(road.xs.size, road.zs.size)
            if (n < 2) continue
            val w = road.width.coerceIn(1.2f, 14f)
            for (i in 0 until n - 1) {
                val x0 = road.xs[i]
                val z0 = road.zs[i]
                val x1 = road.xs[i + 1]
                val z1 = road.zs[i + 1]
                val dx = x1 - x0
                val dz = z1 - z0
                val len = sqrt(dx * dx + dz * dz)
                if (len < 0.05f) continue
                val strip = CubeNode(sceneView.engine, Float3(w, 0.06f, len), Float3(0f, 0f, 0f), mat)
                strip.position = Position((x0 + x1) / 2f, 0.03f, (z0 + z1) / 2f)
                strip.rotation = Rotation(0f, Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat(), 0f)
                root.addChildNode(strip)
            }
        }
    }

    private fun buildBuildings(root: Node, model: VirtualArScene.Model) {
        for (b in model.buildings) {
            val h = b.height.coerceIn(3f, 60f)
            val color = buildingColor(b.type)
            val body = CubeNode(
                sceneView.engine,
                Float3(b.w, h, b.d),
                Float3(0f, 0f, 0f),
                material(color)
            )
            body.position = Position(b.x, h / 2f, b.z)
            body.rotation = Rotation(0f, b.rot, 0f)
            root.addChildNode(body)

            val roof = CubeNode(
                sceneView.engine,
                Float3(b.w * 1.06f, 0.25f, b.d * 1.06f),
                Float3(0f, 0f, 0f),
                material(darken(color, 0.72f))
            )
            roof.position = Position(b.x, h + 0.12f, b.z)
            roof.rotation = Rotation(0f, b.rot, 0f)
            root.addChildNode(roof)
        }
    }

    private fun buildPoiMarkers(root: Node, model: VirtualArScene.Model) {
        val mat = material(0xFFFFC107.toInt())
        for (m in model.markers) {
            val post = CylinderNode(
                sceneView.engine,
                radius = 0.12f,
                height = 1.6f,
                center = Float3(0f, 0f, 0f),
                sideCount = 10,
                materialInstance = mat
            )
            post.position = Position(m.x, 0.8f, m.z)
            root.addChildNode(post)

            val top = SphereNode(sceneView.engine, 0.28f, materialInstance = mat)
            top.position = Position(m.x, 1.8f, m.z)
            root.addChildNode(top)
        }
    }

    private fun buildPoiBeacon(root: Node) {
        val pillar = CylinderNode(
            sceneView.engine,
            radius = 0.35f,
            height = 7f,
            center = Float3(0f, 0f, 0f),
            sideCount = 16,
            materialInstance = material(0xFF00E5FF.toInt())
        )
        pillar.position = Position(0f, 3.5f, 0f)
        root.addChildNode(pillar)
    }

    private fun createCharacter(root: Node) {
        val ch = buildHumanoid(0xFF00BCD4.toInt(), 1.05f)
        ch.position = Position(charX, 0f, charZ)
        root.addChildNode(ch)
        character = ch
    }

    private fun spawnNpcs(root: Node, model: VirtualArScene.Model?) {
        if (model == null) return
        val colors = intArrayOf(
            0xFFFF7043.toInt(), 0xFFAB47BC.toInt(), 0xFFFFCA28.toInt(),
            0xFFEF5350.toInt(), 0xFF66BB6A.toInt()
        )
        var placed = 0
        for (road in model.roads) {
            if (placed >= 10) break
            val n = minOf(road.xs.size, road.zs.size)
            if (n < 2) continue
            val i = n / 2
            val x = road.xs[i]
            val z = road.zs[i]
            if (hypot(x, z) > 100f) continue
            if (hypot(x - charX, z - charZ) < 3f) continue
            val npc = buildHumanoid(colors[placed % colors.size], 0.98f)
            npc.position = Position(x, 0f, z)
            npc.rotation = Rotation(0f, (placed * 47f) % 360f, 0f)
            root.addChildNode(npc)
            placed++
        }
    }

    private fun buildHumanoid(color: Int, scale: Float): Node {
        val eng = sceneView.engine
        val root = Node(eng)
        root.scale = Scale(scale, scale, scale)

        val skin = material(0xFFFFE0B2.toInt())
        val pants = material(darken(color, 0.55f))
        val body = material(color)

        for (sx in intArrayOf(-1, 1)) {
            val leg = CylinderNode(
                eng, radius = 0.11f, height = 0.85f,
                center = Float3(0f, 0f, 0f), sideCount = 10, materialInstance = pants
            )
            leg.position = Position(sx * 0.13f, 0.42f, 0f)
            root.addChildNode(leg)
        }

        val torso = CylinderNode(
            eng, radius = 0.24f, height = 0.75f,
            center = Float3(0f, 0f, 0f), sideCount = 12, materialInstance = body
        )
        torso.position = Position(0f, 1.2f, 0f)
        root.addChildNode(torso)

        for (sx in intArrayOf(-1, 1)) {
            val arm = CylinderNode(
                eng, radius = 0.075f, height = 0.7f,
                center = Float3(0f, 0f, 0f), sideCount = 8, materialInstance = body
            )
            arm.position = Position(sx * 0.32f, 1.2f, 0f)
            root.addChildNode(arm)
        }

        val head = SphereNode(eng, 0.17f, materialInstance = skin)
        head.position = Position(0f, 1.78f, 0f)
        root.addChildNode(head)

        // Indicatore di direzione (+Z) per capire dove guarda il personaggio.
        val nose = CubeNode(eng, Float3(0.08f, 0.08f, 0.14f), Float3(0f, 0f, 0f), material(0xFFE53935.toInt()))
        nose.position = Position(0f, 1.78f, 0.17f)
        root.addChildNode(nose)

        return root
    }

    // ------------------------------------------------------------------ UI

    private fun buildHud() {
        val pad = UiKit.dp(this, 12)

        statusText = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            setPadding(pad, pad, pad, pad)
            text = "Carico il mondo Huntix…"
        }
        hud.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = pad
                topMargin = pad
            }
        )

        val hint = TextView(this).apply {
            textSize = 12f
            setTextColor(0xE6FFFFFF.toInt())
            setBackgroundColor(0x99000000.toInt())
            setPadding(pad, UiKit.dp(this@VirtualArActivity, 6), pad, UiKit.dp(this@VirtualArActivity, 6))
            text = "Joystick = muovi · trascina = ruota camera"
        }
        hud.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = UiKit.dp(this@VirtualArActivity, 12)
            }
        )

        val exit = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCCB00020.toInt())
            setPadding(pad, pad, pad, pad)
            text = "CHIUDI"
            setOnClickListener {
                AppLog.i(TAG, "chiusura richiesta dall'utente")
                finish()
            }
        }
        hud.addView(
            exit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = pad
                topMargin = pad
            }
        )

        val j = JoystickView(this)
        val size = UiKit.dp(this, 150)
        hud.addView(
            j,
            FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = UiKit.dp(this@VirtualArActivity, 18)
                bottomMargin = UiKit.dp(this@VirtualArActivity, 24)
            }
        )
        joystick = j
    }

    // --------------------------------------------------------------- colori

    private fun material(color: Int): MaterialInstance =
        materials.getOrPut(color) { sceneView.materialLoader.createColorInstance(color = color) }

    private fun buildingColor(type: String): Int = when (type.lowercase()) {
        "house", "residential", "detached", "terrace", "bungalow", "villa" -> 0xFFC7B299.toInt()
        "apartments", "dormitory" -> 0xFFB08D6A.toInt()
        "commercial", "retail", "shop", "supermarket", "kiosk" -> 0xFFB58A7A.toInt()
        "industrial", "warehouse", "factory" -> 0xFF8E9AA3.toInt()
        "school", "university", "hospital", "hotel" -> 0xFFD8B98A.toInt()
        "church", "cathedral", "chapel", "mosque", "synagogue" -> 0xFFE0D6C2.toInt()
        "garage", "garages", "hut", "shed", "carport", "roof" -> 0xFF9A9A8E.toInt()
        "train_station", "transportation" -> 0xFFA9B7C0.toInt()
        else -> 0xFFBFB4A4.toInt()
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    override fun onDestroy() {
        character = null
        joystick = null
        materials.clear()
        runCatching { sceneView.destroy() }
        super.onDestroy()
    }
}

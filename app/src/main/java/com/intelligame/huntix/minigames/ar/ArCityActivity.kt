package com.intelligame.huntix.minigames.ar

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.UiKit
import com.google.android.filament.MaterialInstance
import com.intelligame.huntix.bridge.CityTilePreloader
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import dev.romainguy.kotlin.math.Float3
import java.util.HashMap
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * HUNTIX REALTA' AUMENTATA (Mia citta').
 *
 * La fotocamera del telefono è REALE: scegli una superficie piana (tavolo,
 * pavimento), ARCore la rileva, e sopra la vita reale viene costruita la
 * mini-città OpenStreetMap del punto virtuale in cui ti trovi nella città
 * Unity: edifici, strade, un uovo luminoso e il personaggio. Il tutto è
 * ancorato al piano inquadrato.
 *
 * Non è realtà virtuale: è visualizzazione AR vera con la camera del
 * dispositivo (ARCore). Accesso: pulsante "PASSA A AR" nel gioco Unity.
 */
class ArCityActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "ARC_LAT"
        const val EXTRA_LNG = "ARC_LNG"
        const val EXTRA_POI_LAT = "ARC_POI_LAT"
        const val EXTRA_POI_LNG = "ARC_POI_LNG"
        const val EXTRA_POI_NAME = "ARC_POI_NAME"
        const val EXTRA_POI_TYPE = "ARC_POI_TYPE"
        const val EXTRA_EGGS = "ARC_EGGS"

        // Stesse regole di VirtualArScene per convertire lat/lng in metri locali.
        private const val M_PER_DEG_LAT = 111320.0

        private const val TAG = "ArCity"

        // Raggio (m) del mondo OSM importato attorno al punto virtuale.
        private const val WORLD_RADIUS_M = 220f
        // Scala del mondo rispetto alla realtà (1:100 = 1 cm per metro).
        private const val WORLD_SCALE = 0.01f
        // Soglia minima per considerare valido un piano di appoggio.
        private const val MIN_PLANE_SIZE = 0.25f
    }

    private lateinit var arSceneView: ARSceneView
    private lateinit var overlay: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView

    private val materials = HashMap<Int, MaterialInstance>()
    private var anchored = false
    private var worldRoot: Node? = null
    private var eggNode: Node? = null
    private var eggPulse: ValueAnimator? = null

    private var poiLat = 0.0
    private var poiLng = 0.0
    private var poiName = "Punto virtuale"
    private var poiType = "luogo"

    // Uova presenti in citta' (inviate da Unity): da piazzare in AR nella
    // stessa posizione virtuale, mostrate come indicatori colorati.
    private class EggSpawn(val lat: Double, val lng: Double, val rarity: Int, val type: Int)
    private val eggSpawns = ArrayList<EggSpawn>()

    private val CAMERA_PERMISSION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        poiLat = intent.getDoubleExtra(EXTRA_POI_LAT, intent.getDoubleExtra(EXTRA_LAT, Double.NaN))
        poiLng = intent.getDoubleExtra(EXTRA_POI_LNG, intent.getDoubleExtra(EXTRA_LNG, Double.NaN))
        poiName = intent.getStringExtra(EXTRA_POI_NAME) ?: "Punto virtuale"
        poiType = intent.getStringExtra(EXTRA_POI_TYPE) ?: "luogo"
        parseEggExtras()
        if (poiLat.isNaN() || poiLng.isNaN()) {
            AppLog.w(TAG, "posizione assente: uso la piazza di default")
            poiLat = 41.46290
            poiLng = 15.54320
        }
        AppLog.i(TAG, "Mia citta' AR attorno a $poiLat,$poiLng ($poiName)")

        val root = FrameLayout(this)
        arSceneView = ARSceneView(this)
        root.addView(arSceneView)

        overlay = FrameLayout(this)
        root.addView(overlay)
        setContentView(root)

        buildHud()
        setupAR()
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startArSession()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startArSession()
            } else {
                statusText.text = "Serve la fotocamera per la realta' aumentata"
                runOnUiThread { finish() }
            }
        }
    }

    // ARSceneView (SceneView) avvia da solo camera e sessione ARCore.
    private fun startArSession() { }

    override fun onResume() {
        super.onResume()
        eggPulse?.cancel()
    }

    override fun onPause() {
        super.onPause()
        eggPulse?.cancel()
    }

    override fun onDestroy() {
        eggPulse?.cancel()
        materials.clear()
        runCatching { arSceneView.destroy() }
        super.onDestroy()
    }

    // ------------------------------------------------------------- ARCore

    private fun setupAR() {
        try {
            arSceneView.planeRenderer.isEnabled = true
            arSceneView.configureSession { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                if (!session.isSupported(config)) {
                    config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                }
                config.focusMode = Config.FocusMode.AUTO
            }
            arSceneView.onSessionUpdated = { session, frame ->
                if (!anchored &&
                    frame.camera.trackingState == TrackingState.TRACKING
                ) {
                    // Uso il piano orizzontale più ampio e ancora la città.
                    val plane = session.getAllTrackables(Plane::class.java)
                        .filter {
                            it.trackingState == TrackingState.TRACKING &&
                            it.subsumedBy == null &&
                            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            it.extentX >= MIN_PLANE_SIZE && it.extentZ >= MIN_PLANE_SIZE
                        }
                        .maxByOrNull { it.extentX * it.extentZ }
                    if (plane != null) {
                        anchored = true
                        runOnUiThread {
                            statusText.text = "Piano trovato! Costruisco la citta'..."
                            hintText.text = "Sopra la superficie compaiono edifici, uovo e personaggio"
                        }
                        anchorWorld(plane.centerPose)
                        loadWorldAsync()
                    }
                }
            }
        } catch (e: Exception) {
            showArError(e.message ?: "ARCore non disponibile")
        }
    }

    private fun showArError(msg: String) {
        runOnUiThread {
            statusText.text = "AR non disponibile: $msg"
            hintText.text = "Aggiorna ARCore dal Play Store e riprova"
        }
        AppLog.e(TAG, "setup AR fallito: $msg")
    }

    // --------------------------------------------------------------- mondo

    private fun anchorWorld(pose: Pose) {
        val session = arSceneView.session ?: return
        val anchor = runCatching {
            session.createAnchor(pose)
        }.onFailure { e ->
            AppLog.w(TAG, "anchor fallita: ${e.message}")
        }.getOrNull() ?: return

        val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)

        // Radice del mondo: tutti gli edifici/strazi con scala da tavolo.
        val world = Node(arSceneView.engine)
        world.scale = Scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE)
        worldRoot = world
        buildGround(world)
        anchorNode.addChildNode(world)

        // Uovo caccia e personaggio a grandezza reale, accanto alla citta'.
        buildEgg(anchorNode)
        buildCharacter(anchorNode)

        arSceneView.addChildNode(anchorNode)
        AppLog.i(TAG, "mondo ancorato al piano reale")
    }

    private fun loadWorldAsync() {
        Thread {
            val json = runCatching { CityTilePreloader.ensureGeoJsonSync(this, poiLat, poiLng) }
                .onFailure { AppLog.w(TAG, "tile non scaricata: ${it.message}") }
                .getOrNull()
            val model = VirtualArScene.parse(json, poiLat, poiLng, WORLD_RADIUS_M)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                buildModel(model)
            }
        }.start()
    }

    private fun buildModel(model: VirtualArScene.Model?) {
        val world = worldRoot ?: return
        if (model == null) {
            statusText.text = "\u26A0\uFE0F $poiName — mappa non disponibile, mondo vuoto"
            return
        }
        buildRoads(world, model)
        buildBuildings(world, model)
        buildPoiMarkers(world, model)
        buildPoiBeacon(world)
        buildEggMarkers(world)
        statusText.text = "\uD83C\uDF0D $poiName — ${model.buildings.size} edifici · ${model.roads.size} strade · ${eggSpawns.size} uova"
        AppLog.i(TAG, "citta' AR costruita: ${model.buildings.size} edifici")
    }

    // Uova ricevute da Unity: {lat,lng,rarity,type}. Nessuna costruzione
    // eseguita qui, servono solo per il piazzamento in buildEggMarkers.
    private fun parseEggExtras() {
        eggSpawns.clear()
        val raw = intent.getStringExtra(EXTRA_EGGS) ?: return
        if (raw.isBlank() || raw == "[]") return
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lat = o.optDouble("lat")
                val lng = o.optDouble("lng")
                if (lat.isNaN() || lng.isNaN()) continue
                eggSpawns.add(
                    EggSpawn(lat, lng,
                        o.optInt("rarity", 0),
                        o.optInt("type", 0))
                )
            }
            AppLog.i(TAG, "uova da Unity: " + eggSpawns.size)
        } catch (e: Exception) {
            AppLog.w(TAG, "eggs extra non valido: " + (e.message ?: "err"))
        }
    }

    /** Piazzamento stile città: stesse coordinate locali (stessa conversione
     *  di VirtualArScene), scala mondo 1:100. Indicatore a sfera colorata +
     *  anello rarità, spendibile a occhio sul tavolo. */
    private fun buildEggMarkers(root: Node) {
        if (eggSpawns.isEmpty()) return
        val mPerDegLng = M_PER_DEG_LAT * kotlin.math.cos(Math.toRadians(poiLat))
        val grid = HashMap<Pair<Int, Int>, EggSpawn>()
        for (e in eggSpawns) {
            val x = ((e.lng - poiLng) * mPerDegLng).toFloat()
            val z = (-(e.lat - poiLat) * M_PER_DEG_LAT).toFloat()
            if (kotlin.math.hypot(x.toDouble(), z.toDouble()) > WORLD_RADIUS_M) continue
            // evita grumi: stessa cella 8m -> tiene la piu' rara
            val key = Pair((x / 8f).toInt(), (z / 8f).toInt())
            val prev = grid[key]
            if (prev == null || e.rarity > prev.rarity) grid[key] = e
        }
        for ((_, e) in grid) {
            try {
                val x = ((e.lng - poiLng) * mPerDegLng).toFloat()
                val z = (-(e.lat - poiLat) * M_PER_DEG_LAT).toFloat()
                val col = eggRarityColor(e.rarity)
                val sphere = SphereNode(
                    arSceneView.engine, radius = 1.1f,
                    materialInstance = material(col)
                )
                sphere.position = Position(x, 2.0f, z)
                root.addChildNode(sphere)

                // anello rarità a terra, come il ring di EggController
                val ring = CylinderNode(
                    arSceneView.engine, radius = 2.0f, height = 0.4f,
                    center = Float3(0f, 0f, 0f), sideCount = 16,
                    materialInstance = material(col)
                )
                ring.position = Position(x, 0.35f, z)
                root.addChildNode(ring)
            } catch (e: Exception) {
                AppLog.w(TAG, "uovo AR non costruito: " + (e.message ?: "err"))
            }
        }
    }

    // Palette rarità identica a EggController.cs (Common/Uncommon/Rare/Legendary).
    private fun eggRarityColor(rarity: Int): Int = when (rarity) {
        1 -> 0xFF00B5FF.toInt()
        2 -> 0xFFA855F7.toInt()
        3 -> 0xFFFFD700.toInt()
        else -> 0xFF00CC87.toInt()
    }

    private fun buildGround(root: Node) {
        val size = WORLD_RADIUS_M * 2.6f
        val ground = CubeNode(
            arSceneView.engine,
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
                val x0 = road.xs[i]; val z0 = road.zs[i]
                val x1 = road.xs[i + 1]; val z1 = road.zs[i + 1]
                val dx = x1 - x0; val dz = z1 - z0
                val len = sqrt(dx * dx + dz * dz)
                if (len < 0.05f) continue
                val strip = CubeNode(arSceneView.engine, Float3(w, 0.06f, len), Float3(0f, 0f, 0f), mat)
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
                arSceneView.engine,
                Float3(b.w, h, b.d),
                Float3(0f, 0f, 0f),
                material(color)
            )
            body.position = Position(b.x, h / 2f, b.z)
            body.rotation = Rotation(0f, b.rot, 0f)
            root.addChildNode(body)

            val roof = CubeNode(
                arSceneView.engine,
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
                arSceneView.engine, radius = 0.12f, height = 1.6f,
                center = Float3(0f, 0f, 0f), sideCount = 10, materialInstance = mat
            )
            post.position = Position(m.x, 0.8f, m.z)
            root.addChildNode(post)

            val top = SphereNode(arSceneView.engine, 0.28f, materialInstance = mat)
            top.position = Position(m.x, 1.8f, m.z)
            root.addChildNode(top)
        }
    }

    private fun buildPoiBeacon(root: Node) {
        val pillar = CylinderNode(
            arSceneView.engine, radius = 0.35f, height = 7f,
            center = Float3(0f, 0f, 0f), sideCount = 16,
            materialInstance = material(0xFF00E5FF.toInt())
        )
        pillar.position = Position(0f, 3.5f, 0f)
        root.addChildNode(pillar)
    }

    // ---------------------------------------------- uovo + personaggio

    private fun buildEgg(root: Node) {
        val eggGroup = Node(arSceneView.engine)
        val gold = SphereNode(arSceneView.engine, 0.28f, materialInstance = material(0xFFFFD700.toInt()))
        gold.scale = Scale(1f, 1.35f, 1f)
        eggGroup.addChildNode(gold)

        val base = CylinderNode(
            arSceneView.engine, radius = 0.34f, height = 0.06f,
            center = Float3(0f, 0f, 0f), sideCount = 12,
            materialInstance = material(0xFF8D6E63.toInt())
        )
        base.position = Position(0f, 0.03f, 0f)
        eggGroup.addChildNode(base)

        eggGroup.position = Position(0f, 0.28f, 0.9f)
        root.addChildNode(eggGroup)
        eggNode = eggGroup

        val pulse = ValueAnimator.ofFloat(1f, 1.12f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val s = (anim.animatedValue as? Float) ?: return@addUpdateListener
                eggGroup.scale = Scale(s, s, s)
            }
            start()
        }
        eggPulse = pulse
    }

    private fun buildCharacter(root: Node) {
        val ch = buildHumanoid(0xFF00BCD4.toInt(), 1.05f)
        ch.position = Position(0.55f, 0f, 1.15f)
        ch.rotation = Rotation(0f, -45f, 0f)
        root.addChildNode(ch)
    }

    private fun buildHumanoid(color: Int, scale: Float): Node {
        val eng = arSceneView.engine
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
            text = "Muovi il telefono per trovare un piano..."
        }
        overlay.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = pad; topMargin = pad
            }
        )

        hintText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xE6FFFFFF.toInt())
            setBackgroundColor(0x99000000.toInt())
            setPadding(pad, UiKit.dp(this@ArCityActivity, 6), pad, UiKit.dp(this@ArCityActivity, 6))
            text = "Inquadra una superficie piana (tavolo/pavimento)"
        }
        overlay.addView(
            hintText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = UiKit.dp(this@ArCityActivity, 12)
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
                AppLog.i(TAG, "chiusura richiesta")
                finish()
            }
        }
        overlay.addView(
            exit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = pad; topMargin = pad
            }
        )
    }

    // --------------------------------------------------------------- colori

    @Suppress("UNCHECKED_CAST")
    private fun material(color: Int): MaterialInstance =
        materials.getOrPut(color) {
            arSceneView.materialLoader.createColorInstance(color = color)
        }

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
}
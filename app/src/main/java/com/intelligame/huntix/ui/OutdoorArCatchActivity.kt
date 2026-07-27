package com.intelligame.huntix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.intelligame.huntix.R
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.WorldEgg
import com.intelligame.huntix.manager.BuildingObstacleManager
import com.intelligame.huntix.manager.GeospatialAnchorManager
import com.intelligame.huntix.manager.OutdoorManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

class OutdoorArCatchActivity : AppCompatActivity() {

    private val mgr by lazy { OutdoorManager.get() }
    private val geoMgr = GeospatialAnchorManager()
    private val buildingMgr = BuildingObstacleManager()

    private lateinit var sceneView: ARSceneView
    private lateinit var overlay: FrameLayout

    private val eggNodes = mutableMapOf<String, Node>()
    private var arrowNode: Node? = null
    @Volatile private var currentEggId: String? = null
    private var trackingReady = false

    private lateinit var compassArrow: CompassArrowView
    private lateinit var distText: TextView
    private lateinit var hintText: TextView
    private lateinit var obstacleHint: TextView
    private lateinit var catchBtn: Button
    private lateinit var dashBtn: Button
    private lateinit var mapBtn: Button

    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudRunnable = object : Runnable {
        override fun run() {
            updateHud()
            refreshDashButton()
            hudHandler.postDelayed(this, 500)
        }
    }

    private val requestLoc = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            mgr.start(this)
        }
    }
    private val requestCam = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    companion object {
        private const val MAX_DISPLAY_RANGE_M = 80f
        private const val SCENE_SCALE = 0.06f
        private const val MAX_SCENE_DIST_M = 5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        sceneView = findViewById(R.id.sceneView)
        overlay = findViewById(R.id.arOverlay)
        sceneView.lifecycle = lifecycle
        buildHud()
        configureSession()

        sceneView.onSessionUpdated = { session, frame ->
            if (!trackingReady) {
                if (frame.camera.trackingState == TrackingState.TRACKING) {
                    trackingReady = true
                    onTrackingReady()
                }
            }
            val wasTracking = geoMgr.isTracking()
            geoMgr.updateEarthState(session)

            if (geoMgr.isTracking()) {
                updateGeospatialAnchors()
                cleanCompassEggs()
            } else if (wasTracking) {
                geoMgr.removeAll()
                clearAllEggNodes()
            } else {
                updateArrow3D()
                updateCompassEggs()
            }
        }

        val needsLoc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
        val needsCam = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED

        if (needsLoc) {
            requestLoc.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            mgr.start(this)
        }
        if (needsCam) requestCam.launch(arrayOf(Manifest.permission.CAMERA))

        mgr.currentLocation?.let { loc ->
            buildingMgr.fetchBuildingsIfNeeded(loc.latitude, loc.longitude)
        }

        hudHandler.post(hudRunnable)
    }

    override fun onResume() {
        super.onResume()
        hudHandler.post(hudRunnable)
        refreshDashButton()
    }

    override fun onPause() {
        hudHandler.removeCallbacks(hudRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        hudHandler.removeCallbacks(hudRunnable)
        geoMgr.removeAll()
        buildingMgr.destroy()
        clearAllEggNodes()
        mgr.huntingEggId = null
        mgr.stop()
        super.onDestroy()
    }

    private fun refreshDashButton() {
        val now = System.currentTimeMillis()
        val lastLeverTime = mgr.lastLeverTime
        val remaining = ((OutdoorManager.LEVER_COOLDOWN_MS - (now - lastLeverTime)) / 1000f).coerceAtLeast(1f)
        dashBtn.isEnabled = now - lastLeverTime >= OutdoorManager.LEVER_COOLDOWN_MS
        dashBtn.text = if (now - lastLeverTime < OutdoorManager.LEVER_COOLDOWN_MS) {
            "🔜 Pesta! (${remaining.toInt()}s)"
        } else "🗩 Pesta!"
    }

    private fun configureSession() {
        sceneView.configureSession { session, config ->
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            geoMgr.configureSession(session, config)
        }
    }

    private fun onTrackingReady() {
        if (arrowNode != null) return
        val mat = sceneView.materialLoader.createColorInstance(0xFFFDD835.toInt())
        val arrow = Node(sceneView.engine)
        val shaft = SphereNode(sceneView.engine, 0.15f, materialInstance = mat).apply {
            scale = io.github.sceneview.math.Scale(0.35f, 0.35f, 1.5f)
            position = Position(0f, 0f, -0.3f)
        }
        val head = SphereNode(sceneView.engine, 0.2f, materialInstance = mat).apply {
            position = Position(0f, 0f, -0.7f)
        }
        arrow.addChildNode(shaft)
        arrow.addChildNode(head)
        arrow.position = Position(0f, -0.05f, -0.6f)
        sceneView.cameraNode.addChildNode(arrow)
        arrowNode = arrow
    }

    private fun updateGeospatialAnchors() {
        if (!geoMgr.isTracking()) return
        val eggs = mgr.getEggs().filter { !it.found && mgr.distanceMeters(it) <= MAX_DISPLAY_RANGE_M }
        if (eggs.isEmpty()) return

        val nearest = eggs.minByOrNull { mgr.distanceMeters(it) } ?: return
        updateGeospatialAnchorFor(nearest)
    }

    private fun updateGeospatialAnchorFor(egg: WorldEgg) {
        if (egg.found) return

        val prevEggId = currentEggId
        prevEggId?.let { geoMgr.removeAnchor(it) }
        eggNodes[prevEggId]?.let { node ->
            node.parent?.removeChildNode(node)
            eggNodes.remove(prevEggId)
        }
        currentEggId = null

        val anchor = geoMgr.createAnchorForEgg(egg) ?: return
        val anchorNode = AnchorNode(sceneView.engine, anchor).apply { isVisible = true }
        sceneView.addChildNode(anchorNode)
        geoMgr.registerAnchor(egg.id, anchor)
        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val body = SphereNode(sceneView.engine, 0.18f, materialInstance = mat)
        anchorNode.addChildNode(body)
        val glowColor = android.graphics.Color.argb(
            (0.3f * 255).toInt(),
            android.graphics.Color.red(egg.rarity.glowColor),
            android.graphics.Color.green(egg.rarity.glowColor),
            android.graphics.Color.blue(egg.rarity.glowColor)
        )
        val glow = SphereNode(sceneView.engine, 0.28f, materialInstance = sceneView.materialLoader.createColorInstance(glowColor))
        anchorNode.addChildNode(glow)
        eggNodes[egg.id] = anchorNode
        currentEggId = egg.id
        geoMgr.trackNode(egg.id, anchorNode)
    }

    private fun updateCompassEggs() {
        val eggs = mgr.getEggs().filter { !it.found && mgr.distanceMeters(it) <= MAX_DISPLAY_RANGE_M }
        val currentIds = eggNodes.keys.toSet()
        val targetIds = eggs.map { it.id }.toSet()

        for (id in currentIds) {
            if (id !in targetIds) {
                eggNodes[id]?.let { it.parent?.removeChildNode(it) }
                eggNodes.remove(id)
            }
        }

        val heading = mgr.getDeviceHeadingDeg()
        for (egg in eggs) {
            if (eggNodes.containsKey(egg.id)) continue
            val node = buildEggNode(egg, heading)
            sceneView.cameraNode.addChildNode(node)
            eggNodes[egg.id] = node
        }

        for ((id, node) in eggNodes) {
            val egg = eggs.firstOrNull { it.id == id } ?: continue
            updateCameraRelativeNode(node, egg, heading)
        }
    }

    private fun buildEggNode(egg: WorldEgg, heading: Float): Node {
        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val body = SphereNode(sceneView.engine, 0.15f, materialInstance = mat).apply {
            position = Position(0f, 0f, 0f)
        }
        val glowAlpha = (0.3f * 255).toInt()
        val glowColor = android.graphics.Color.argb(
            glowAlpha,
            android.graphics.Color.red(egg.rarity.glowColor),
            android.graphics.Color.green(egg.rarity.glowColor),
            android.graphics.Color.blue(egg.rarity.glowColor)
        )
        val glow = SphereNode(sceneView.engine, 0.22f, materialInstance = sceneView.materialLoader.createColorInstance(glowColor)).apply {
            position = Position(0f, 0f, 0f)
        }
        val root = Node(sceneView.engine).apply {
            position = Position(0f, 0.05f, 0f)
            addChildNode(glow)
            addChildNode(body)
        }
        return root
    }

    private fun updateCameraRelativeNode(node: Node, egg: WorldEgg, heading: Float) {
        var relative = mgr.bearingTo(egg) - heading
        if (relative > 180f) relative -= 360f
        else if (relative < -180f) relative += 360f
        val dist = mgr.distanceMeters(egg)
        val scaledDist = (dist * SCENE_SCALE).coerceIn(0.5f, MAX_SCENE_DIST_M)
        val rad = Math.toRadians(relative.toDouble())
        val x = (Math.sin(rad) * scaledDist).toFloat()
        val z = (-Math.cos(rad) * scaledDist).toFloat()
        node.position = Position(x, 0.05f, z)
    }

    private fun cleanCompassEggs() {
        val eggs = mgr.getEggs().filter { !it.found }
        val targetIds = eggs.map { it.id }.toSet()
        for ((id, node) in eggNodes) {
            if (id !in targetIds) {
                node.parent?.removeChildNode(node)
            }
        }
        eggNodes.entries.removeAll { it.key !in targetIds }
    }

    private fun clearAllEggNodes() {
        for ((_, node) in eggNodes) {
            node.parent?.removeChildNode(node)
        }
        eggNodes.clear()
    }

    private fun updateArrow3D() {
        val arrow = arrowNode ?: return
        val egg = mgr.nearestUnfoundEgg() ?: return
        var relative = mgr.bearingTo(egg) - mgr.getDeviceHeadingDeg()
        if (relative > 180f) relative -= 360f
        else if (relative < -180f) relative += 360f
        arrow.rotation = io.github.sceneview.math.Rotation(0f, -relative, 0f)
    }

    private fun updateHud() {
        val eggs = mgr.getEggs().filter { !it.found && mgr.distanceMeters(it) <= MAX_DISPLAY_RANGE_M }
        if (eggs.isEmpty()) {
            distText.text = "Nessuna uova nelle vicinanze"
            hintText.text = "Spostati per trovarne"
            catchBtn.visibility = View.GONE
            compassArrow.visibility = View.GONE
            obstacleHint.visibility = View.GONE
            clearAllEggNodes()
            return
        }
        val nearest = eggs.minByOrNull { mgr.distanceMeters(it) }!!
        val dist = mgr.distanceMeters(nearest)
        val loc = mgr.currentLocation

        compassArrow.headingDeg = mgr.getDeviceHeadingDeg()
        compassArrow.targetBearingDeg = mgr.bearingTo(nearest)
        compassArrow.invalidate()

        if (loc != null) {
            buildingMgr.fetchBuildingsIfNeeded(loc.latitude, loc.longitude)
            val obs = buildingMgr.checkObstacle(
                loc.latitude, loc.longitude,
                nearest.lat, nearest.lng,
                mgr.getDeviceHeadingDeg()
            )
            if (obs.blocked && obs.suggestion != null) {
                obstacleHint.text = "\u26A0 ${obs.suggestion}"
                obstacleHint.visibility = View.VISIBLE
            } else {
                obstacleHint.visibility = View.GONE
            }
        } else {
            obstacleHint.visibility = View.GONE
        }

        if (geoMgr.isTracking() && dist <= 20f) {
            distText.text = "${nearest.rarity.emoji} ${nearest.rarity.displayName} [VPS]"
            hintText.text = "Guarda attorno: l'uovo e' vicino!"
            compassArrow.visibility = View.GONE
            clearAllEggNodes()
        } else {
            distText.text = "${nearest.rarity.emoji} ${nearest.rarity.displayName}: ${dist.toInt()} m"
            hintText.text = when {
                dist <= mgr.getCatchRadiusM(nearest) -> "Tocca l'uovo per catturarlo!"
                dist <= 60f -> "Avvicinati ancora..."
                geoMgr.isTracking() -> "VPS attivo — segui la freccia"
                else -> "Segui la direzione sulla mappa"
            }
            compassArrow.visibility = View.VISIBLE
        }
        catchBtn.visibility = if (dist <= mgr.getCatchRadiusM(nearest)) View.VISIBLE else View.GONE
    }

    private fun onCatch() {
        val eggs = mgr.getEggs().filter { !it.found }
        val nearest = eggs.minByOrNull { mgr.distanceMeters(it) } ?: return
        if (mgr.distanceMeters(nearest) > mgr.getCatchRadiusM(nearest)) {
            Toast.makeText(this, "Avvicinati per catturare", Toast.LENGTH_SHORT).show()
            return
        }
        CatchDialogHelper.showFoodSelection(this, nearest, object : CatchDialogHelper.OnCatchReady {
            override fun onCatchReady(foodBonus: Float, xpMultiplier: Float) {
                val effectiveBonus = if (foodBonus > 0f) foodBonus else 1f
                val res = mgr.tryCatch(this@OutdoorArCatchActivity, nearest.id, effectiveBonus)
                Toast.makeText(this@OutdoorArCatchActivity, res.message, Toast.LENGTH_LONG).show()
                if (res.success) {
                    geoMgr.removeAnchor(nearest.id)
                    eggNodes[nearest.id]?.let { it.parent?.removeChildNode(it) }
                    eggNodes.remove(nearest.id)
                    updateHud()
                    EggOpeningAnimationActivity.start(this@OutdoorArCatchActivity, res.egg!!.rarity, res.egg.name, res.egg.rarity.xpReward)
                }
            }
        })
    }

    private fun onDash() {
        val result = mgr.simulateApproach()
        Toast.makeText(this@OutdoorArCatchActivity, result, Toast.LENGTH_SHORT).show()
        refreshDashButton()
    }

    private fun buildHud() {
        compassArrow = CompassArrowView(this).apply {
            alpha = 0.92f
        }
        val arrowSize = UiKit.dp(this, 180)
        val arrowP = FrameLayout.LayoutParams(arrowSize, arrowSize).apply {
            gravity = Gravity.CENTER
            bottomMargin = UiKit.dp(this@OutdoorArCatchActivity, 60)
        }

        distText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 20f
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        hintText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        obstacleHint = TextView(this).apply {
            setTextColor(0xFFFF9800.toInt()); textSize = 16f
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            visibility = View.GONE
        }
        catchBtn = Button(this).apply {
            text = "Cattura!"
            setBackgroundColor(0xFF2E7D32.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener { onCatch() }
        }
        dashBtn = Button(this).apply {
            text = "🗩 Pesta!"
            setBackgroundColor(0xFFE53935.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener { onDash() }
        }
        mapBtn = Button(this).apply {
            text = "Mappa"
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@OutdoorArCatchActivity, OutdoorWorldActivity::class.java))
                finish()
            }
        }

        val topP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setMargins(0, 48, 0, 0) }
        val hintP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setMargins(0, 88, 0, 0) }
        val obstacleP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setMargins(0, 120, 0, 0) }
        val catchP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, 180) }
        val dashP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; setMargins(0, 0, 40, 160) }
        val mapP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; setMargins(30, 0, 0, 40) }

        overlay.addView(compassArrow, arrowP)
        overlay.addView(distText, topP)
        overlay.addView(hintText, hintP)
        overlay.addView(obstacleHint, obstacleP)
        overlay.addView(catchBtn, catchP)
        overlay.addView(dashBtn, dashP)
        overlay.addView(mapBtn, mapP)
        catchBtn.visibility = View.GONE
    }
}
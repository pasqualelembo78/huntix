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
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

class ArNavigationActivity : AppCompatActivity() {

    companion object {
        private const val REVEAL_RADIUS_M = 60f
        private const val ARROW_COLOR = 0xFFFDD835.toInt()
        private const val MAX_DISPLAY_RANGE_M = 80f
        private const val SCENE_SCALE = 0.06f
        private const val MAX_SCENE_DIST_M = 5f
    }

    private val mgr by lazy { OutdoorManager.get() }
    private val geoMgr = GeospatialAnchorManager()
    private val buildingMgr = BuildingObstacleManager()

    private lateinit var sceneView: ARSceneView
    private lateinit var overlay: FrameLayout

    private var arrowNode: Node? = null
    private var eggNode: SphereNode? = null
    private var eggAnchorNode: Node? = null
    @Volatile private var currentEggId: String? = null
    private var trackingReady = false

    private lateinit var compassArrow: CompassArrowView
    private lateinit var targetText: TextView
    private lateinit var hintText: TextView
    private lateinit var obstacleHint: TextView
    private lateinit var catchBtn: Button
    private lateinit var mapBtn: Button

    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudRunnable = object : Runnable {
        override fun run() {
            updateHud()
            hudHandler.postDelayed(this, 400)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        sceneView = findViewById(R.id.sceneView)
        overlay = findViewById(R.id.arOverlay)
        val intentEggId = intent.getStringExtra("eggId")
        mgr.huntingEggId = intentEggId ?: mgr.nearestUnfoundEgg()?.id
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
                eggNode?.let { it.parent?.removeChildNode(it) }
                eggNode = null
                eggAnchorNode?.let { it.parent?.removeChildNode(it) }
                eggAnchorNode = null
            } else if (wasTracking) {
                geoMgr.removeAll()
                currentEggId = null
                eggNode?.let { it.parent?.removeChildNode(it) }
                eggNode = null
                eggAnchorNode?.let { it.parent?.removeChildNode(it) }
                eggAnchorNode = null
            } else {
                updateArrow3D()
                val target = mgr.targetEgg()
                if (target != null && mgr.distanceMeters(target) <= MAX_DISPLAY_RANGE_M) {
                    updateCompassEgg(target)
                } else {
                    eggNode?.let { it.parent?.removeChildNode(it) }
                    eggNode = null
                    eggAnchorNode?.let { it.parent?.removeChildNode(it) }
                    eggAnchorNode = null
                    currentEggId = null
                }
            }
        }

        // SceneView.onAttachedToWindow() auto-detects the lifecycle. Only set
        // it manually as a fallback if auto-detection failed.
        if (sceneView.lifecycle == null) {
            sceneView.lifecycle = lifecycle
        }

        val needsLoc = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
        val needsCam = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED

        if (needsLoc) {
            requestLoc.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
    }

    override fun onPause() {
        hudHandler.removeCallbacks(hudRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        hudHandler.removeCallbacks(hudRunnable)
        geoMgr.removeAll()
        buildingMgr.destroy()
        eggNode?.let { it.parent?.removeChildNode(it) }
        eggAnchorNode?.let { it.parent?.removeChildNode(it) }
        mgr.huntingEggId = null
        mgr.stop()
        super.onDestroy()
    }

    private fun configureSession() {
        val configBlock: (Config) -> Unit = { config ->
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.focusMode = Config.FocusMode.AUTO
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            geoMgr.configureSession(session, config)
        }
        sceneView.configureSession { session, config -> configBlock(config) }
        // Fallback: if session already created by onAttachedToWindow lifecycle auto-detection,
        // configureSession's callback was missed. Apply config immediately.
        sceneView.session?.configure(configBlock)
    }

    private fun onTrackingReady() {
        if (arrowNode != null) return
        val mat = sceneView.materialLoader.createColorInstance(ARROW_COLOR)

        val arrow = Node(sceneView.engine)
        val shaft = SphereNode(sceneView.engine, 0.15f, materialInstance = mat).apply {
            scale = Scale(0.35f, 0.35f, 1.5f)
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
        val egg = mgr.targetEgg() ?: return
        if (egg.found) return
        if (currentEggId == egg.id) return

        currentEggId?.let { geoMgr.removeAnchor(it) }
        eggNode?.let { it.parent?.removeChildNode(it) }
        eggAnchorNode?.let { it.parent?.removeChildNode(it) }
        eggNode = null
        eggAnchorNode = null

        val anchor = geoMgr.createAnchorForEgg(egg) ?: return
        val anchorNode = AnchorNode(sceneView.engine, anchor).apply { isVisible = true }
        sceneView.addChildNode(anchorNode)
        geoMgr.registerAnchor(egg.id, anchor)
        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val sphere = SphereNode(sceneView.engine, 0.25f, materialInstance = mat)
        anchorNode.addChildNode(sphere)
        val glowColor = android.graphics.Color.argb(
            (0.3f * 255).toInt(),
            android.graphics.Color.red(egg.rarity.glowColor),
            android.graphics.Color.green(egg.rarity.glowColor),
            android.graphics.Color.blue(egg.rarity.glowColor)
        )
        val glowMat = sceneView.materialLoader.createColorInstance(glowColor)
        val glow = SphereNode(sceneView.engine, 0.35f, materialInstance = glowMat)
        anchorNode.addChildNode(glow)
        eggAnchorNode = anchorNode
        eggNode = sphere
        currentEggId = egg.id
        geoMgr.trackNode(egg.id, anchorNode)
    }

    private fun updateCompassEgg(egg: WorldEgg) {
        if (currentEggId == egg.id && eggNode != null) return

        eggNode?.let { it.parent?.removeChildNode(it) }
        eggAnchorNode?.let { it.parent?.removeChildNode(it) }
        eggNode = null
        eggAnchorNode = null
        currentEggId = null

        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val node = SphereNode(sceneView.engine, 0.18f, materialInstance = mat).apply {
            position = Position(0f, 0.05f, 0f)
        }
        sceneView.cameraNode.addChildNode(node)
        eggNode = node
        currentEggId = egg.id

        val heading = mgr.getDeviceHeadingDeg()
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

    private fun updateArrow3D() {
        val arrow = arrowNode ?: return
        val egg = mgr.targetEgg() ?: return
        var relative = mgr.bearingTo(egg) - mgr.getDeviceHeadingDeg()
        if (relative > 180f) relative -= 360f
        else if (relative < -180f) relative += 360f
        arrow.rotation = Rotation(0f, -relative, 0f)
    }

    private fun updateHud() {
        val egg = mgr.targetEgg()
        if (egg == null) {
            targetText.text = "Nessuna uova nelle vicinanze"
            hintText.text = "Spostati per trovarne"
            catchBtn.visibility = View.GONE
            compassArrow.visibility = View.GONE
            obstacleHint.visibility = View.GONE
            eggNode?.let { it.parent?.removeChildNode(it) }
            eggNode = null
            eggAnchorNode?.let { it.parent?.removeChildNode(it) }
            eggAnchorNode = null
            currentEggId = null
            return
        }
        val dist = mgr.distanceMeters(egg)
        val loc = mgr.currentLocation

        compassArrow.headingDeg = mgr.getDeviceHeadingDeg()
        compassArrow.targetBearingDeg = mgr.bearingTo(egg)
        compassArrow.invalidate()

        if (loc != null) {
            buildingMgr.fetchBuildingsIfNeeded(loc.latitude, loc.longitude)
            val obs = buildingMgr.checkObstacle(
                loc.latitude, loc.longitude,
                egg.lat, egg.lng,
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
            targetText.text = "${egg.rarity.displayName} [VPS]"
            hintText.text = "Guarda attorno: l'uovo e' vicino!"
            compassArrow.visibility = View.GONE
            eggNode?.let { it.parent?.removeChildNode(it) }
            eggNode = null
            eggAnchorNode?.let { it.parent?.removeChildNode(it) }
            eggAnchorNode = null
            currentEggId = null
        } else {
            targetText.text = "${egg.rarity.displayName}: ${dist.toInt()} m"
            hintText.text = if (geoMgr.isTracking()) "VPS attivo — segui la freccia" else "Segui la freccia gialla"
            compassArrow.visibility = View.VISIBLE
            if (dist <= REVEAL_RADIUS_M) {
                if (geoMgr.isTracking()) {
                    // geospatial anchor handles rendering
                } else updateCompassEgg(egg)
            } else {
                eggNode?.let { it.parent?.removeChildNode(it) }
                eggNode = null
                eggAnchorNode?.let { it.parent?.removeChildNode(it) }
                eggAnchorNode = null
                currentEggId = null
            }
        }
        catchBtn.visibility = if (dist <= mgr.getCatchRadiusM(egg)) View.VISIBLE else View.GONE
    }

    private fun onCatch() {
        val egg = mgr.targetEgg() ?: return
        if (mgr.distanceMeters(egg) > mgr.getCatchRadiusM(egg)) {
            Toast.makeText(this, "Avvicinati per catturare", Toast.LENGTH_SHORT).show()
            return
        }
        CatchDialogHelper.showFoodSelection(this, egg, object : CatchDialogHelper.OnCatchReady {
            override fun onCatchReady(foodBonus: Float, xpMultiplier: Float) {
                val effectiveBonus = if (foodBonus > 0f) foodBonus else 1f
                val res = mgr.tryCatch(this@ArNavigationActivity, egg.id, effectiveBonus)
                Toast.makeText(this@ArNavigationActivity, res.message, Toast.LENGTH_LONG).show()
                if (res.success) {
                    geoMgr.removeAnchor(egg.id)
                    eggNode?.let { it.parent?.removeChildNode(it) }
                    eggNode = null
                    eggAnchorNode?.let { it.parent?.removeChildNode(it) }
                    eggAnchorNode = null
                    currentEggId = null
                    updateHud()
                    EggOpeningAnimationActivity.start(this@ArNavigationActivity, res.egg!!.rarity, res.egg.name, res.egg.rarity.xpReward)
                }
            }
        })
    }

    private fun buildHud() {
        compassArrow = CompassArrowView(this).apply {
            alpha = 0.92f
        }
        val arrowSize = UiKit.dp(this, 180)
        val arrowP = FrameLayout.LayoutParams(arrowSize, arrowSize).apply {
            gravity = Gravity.CENTER
            bottomMargin = UiKit.dp(this@ArNavigationActivity, 60)
        }

        targetText = TextView(this).apply {
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
            text = "Cattura"
            setBackgroundColor(0xFF2E7D32.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener { onCatch() }
        }
        mapBtn = Button(this).apply {
            text = "Mappa"
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@ArNavigationActivity, OutdoorWorldActivity::class.java))
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
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, 130) }
        val mapP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; setMargins(30, 0, 0, 40) }

        overlay.addView(compassArrow, arrowP)
        overlay.addView(targetText, topP)
        overlay.addView(hintText, hintP)
        overlay.addView(obstacleHint, obstacleP)
        overlay.addView(catchBtn, catchP)
        overlay.addView(mapBtn, mapP)
        catchBtn.visibility = View.GONE
    }
}
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
        private val ARROW_COLOR = 0xFFFDD835.toInt()
    }

    private val mgr by lazy { OutdoorManager.get() }
    private val geoMgr = GeospatialAnchorManager()

    private lateinit var sceneView: ARSceneView
    private lateinit var overlay: FrameLayout

    private var arrowNode: Node? = null
    @Volatile private var eggNode: SphereNode? = null
    @Volatile private var currentEggId: String? = null
    private var trackingReady = false
    private var bobT = 0.0

    private lateinit var compassArrow: CompassArrowView
    private lateinit var targetText: TextView
    private lateinit var hintText: TextView
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
        sceneView.lifecycle = lifecycle
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
            } else if (wasTracking) {
                geoMgr.removeAll()
                currentEggId = null
                eggNode = null
            } else {
                updateArrow3D()
                showEggNode(mgr.targetEgg()?.takeIf { mgr.distanceMeters(it) <= 80f })
                eggNode?.let { node ->
                    bobT += 0.06
                    val y = (-0.3f + 0.06f * Math.sin(bobT).toFloat())
                    node.position = Position(0f, y, -1.3f)
                }
            }
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

        hudHandler.post(hudRunnable)
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
        eggNode = null

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
        eggNode = sphere
        currentEggId = egg.id
        geoMgr.trackNode(egg.id, anchorNode)
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
            showEggNode(null)
            return
        }
        val dist = mgr.distanceMeters(egg)

        compassArrow.headingDeg = mgr.getDeviceHeadingDeg()
        compassArrow.targetBearingDeg = mgr.bearingTo(egg)
        compassArrow.invalidate()

        if (geoMgr.isTracking() && dist <= 20f) {
            targetText.text = "${egg.rarity.displayName} [VPS]"
            hintText.text = "Guarda attorno: l'uovo e' vicino!"
            compassArrow.visibility = View.GONE
            showEggNode(null)
        } else {
            targetText.text = "${egg.rarity.displayName}: ${dist.toInt()} m"
            hintText.text = if (geoMgr.isTracking()) "VPS attivo — segui la freccia" else "Segui la freccia gialla"
            compassArrow.visibility = View.VISIBLE
            showEggNode(if (dist <= REVEAL_RADIUS_M) egg else null)
        }
        catchBtn.visibility = if (dist <= mgr.getCatchRadiusM(egg)) View.VISIBLE else View.GONE
    }

    private fun showEggNode(egg: WorldEgg?) {
        if (egg == null) {
            eggNode?.let { it.parent?.removeChildNode(it) }
            eggNode = null
            currentEggId = null
            return
        }
        if (currentEggId == egg.id) return
        eggNode?.let { it.parent?.removeChildNode(it) }
        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val node = SphereNode(sceneView.engine, 0.18f, materialInstance = mat).apply {
            position = Position(0f, -0.3f, -1.3f)
        }
        sceneView.cameraNode.addChildNode(node)
        eggNode = node
        currentEggId = egg.id
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
        val catchP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, 130) }
        val mapP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; setMargins(30, 0, 0, 40) }

        overlay.addView(compassArrow, arrowP)
        overlay.addView(targetText, topP)
        overlay.addView(hintText, hintP)
        overlay.addView(catchBtn, catchP)
        overlay.addView(mapBtn, mapP)
        catchBtn.visibility = View.GONE
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
                    showEggNode(null)
                    updateHud()
                    EggOpeningAnimationActivity.start(this@ArNavigationActivity, res.egg!!.rarity, res.egg.name, res.egg.rarity.xpReward)
                }
            }
        })
    }

    override fun onDestroy() {
        hudHandler.removeCallbacks(hudRunnable)
        geoMgr.removeAll()
        mgr.huntingEggId = null
        mgr.stop()
        super.onDestroy()
    }
}

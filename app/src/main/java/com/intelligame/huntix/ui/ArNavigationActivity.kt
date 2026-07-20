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
import com.intelligame.huntix.WorldEgg
import com.intelligame.huntix.manager.OutdoorManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode

/**
 * ArNavigationActivity — navigazione AR del mondo outdoor.
 *
 * Mostra il feed camera in realtà aumentata (ARSceneView, già in dipendenze → 0 MB extra)
 * con una freccia 3D fluttuante ancorata alla camera che indica la direzione verso
 * l'uovo più vicino (calcolata da bussola + bearing GPS), e fa "fluttuare" un uovo
 * davanti al giocatore quando è abbastanza vicino da poterlo catturare.
 */
class ArNavigationActivity : AppCompatActivity() {

    companion object {
        private const val REVEAL_RADIUS_M = 60f   // entro cui compare l'uovo fluttuante
        private val ARROW_COLOR = 0xFFFDD835.toInt() // giallo
    }

    private val mgr by lazy { OutdoorManager.get() }

    private lateinit var sceneView: ARSceneView
    private lateinit var overlay: FrameLayout

    private var arrowNode: Node? = null
    private var eggNode: SphereNode? = null
    private var currentEggId: String? = null
    private var trackingReady = false
    private var bobT = 0.0

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
    ) { /* ARCore gestisce il sessione; senza camera non parte il tracking */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        sceneView = findViewById(R.id.sceneView)
        overlay = findViewById(R.id.arOverlay)
        buildHud()
        configureSession()

        sceneView.onSessionUpdated = { _, frame ->
            if (!trackingReady) {
                if (frame.camera.trackingState == TrackingState.TRACKING) {
                    trackingReady = true
                    onTrackingReady()
                }
            }
            // freccia sempre aggiornata ad ogni frame (fluida col movimento)
            updateArrow()
            // bob dell'uovo fluttuante
            eggNode?.let { node ->
                bobT += 0.06
                val y = (-0.3f + 0.06f * Math.sin(bobT).toFloat())
                node.position = Position(0f, y, -1.3f)
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
        }
    }

    private fun onTrackingReady() {
        if (arrowNode != null) return
        val mat = sceneView.materialLoader.createColorInstance(ARROW_COLOR)

        val arrow = Node(sceneView.engine)
        val shaft = SphereNode(sceneView.engine, 0.1f, materialInstance = mat).apply {
            scale = Scale(0.3f, 0.3f, 1.2f)
            position = Position(0f, 0f, -0.2f)
        }
        val head = SphereNode(sceneView.engine, 0.13f, materialInstance = mat).apply {
            position = Position(0f, 0f, -0.5f)
        }
        arrow.addChildNode(shaft)
        arrow.addChildNode(head)
        arrow.position = Position(0f, -0.1f, -0.8f)

        sceneView.cameraNode.addChildNode(arrow)
        arrowNode = arrow
    }

    private fun updateArrow() {
        val arrow = arrowNode ?: return
        val egg = mgr.nearestUnfoundEgg() ?: return
        val relative = mgr.bearingTo(egg) - mgr.getDeviceHeadingDeg()
        // L'arrow di default punta lungo -Z (avanti). Ruotando di -relative attorno
        // a Y, -Z si orienta verso l'uovo (relative>0 = a destra del giocatore).
        arrow.rotation = Rotation(0f, Math.toRadians(-relative.toDouble()).toFloat(), 0f)
    }

    private fun updateHud() {
        val egg = mgr.nearestUnfoundEgg()
        if (egg == null) {
            targetText.text = "Nessuna uova nelle vicinanze"
            hintText.text = "Spostati per trovarne"
            catchBtn.visibility = View.GONE
            showEggNode(null)
            return
        }
        val dist = mgr.distanceMeters(egg)
        targetText.text = "${egg.rarity.displayName}: ${dist.toInt()} m"
        hintText.text = "Segui la freccia gialla"
        showEggNode(if (dist <= REVEAL_RADIUS_M) egg else null)
        catchBtn.visibility = if (dist <= OutdoorManager.CATCH_RADIUS_M) View.VISIBLE else View.GONE
    }

    private fun showEggNode(egg: WorldEgg?) {
        if (egg == null) {
            eggNode?.let { sceneView.cameraNode.removeChildNode(it) }
            eggNode = null
            currentEggId = null
            return
        }
        if (currentEggId == egg.id) return
        eggNode?.let { sceneView.cameraNode.removeChildNode(it) }
        val mat = sceneView.materialLoader.createColorInstance(egg.rarity.color)
        val node = SphereNode(sceneView.engine, 0.18f, materialInstance = mat).apply {
            position = Position(0f, -0.3f, -1.3f)
        }
        sceneView.cameraNode.addChildNode(node)
        eggNode = node
        currentEggId = egg.id
    }

    private fun buildHud() {
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

        overlay.addView(targetText, topP)
        overlay.addView(hintText, hintP)
        overlay.addView(catchBtn, catchP)
        overlay.addView(mapBtn, mapP)
        catchBtn.visibility = View.GONE
    }

    private fun onCatch() {
        val egg = mgr.nearestUnfoundEgg() ?: return
        if (mgr.distanceMeters(egg) > OutdoorManager.CATCH_RADIUS_M) {
            Toast.makeText(this, "Avvicinati per catturare", Toast.LENGTH_SHORT).show()
            return
        }
        val res = mgr.tryCatch(this, egg.id)
        Toast.makeText(this, res?.message ?: "Uova non trovata", Toast.LENGTH_LONG).show()
        if (res?.success == true) {
            showEggNode(null)
            updateHud()
        }
    }

    override fun onDestroy() {
        hudHandler.removeCallbacks(hudRunnable)
        super.onDestroy()
    }
}

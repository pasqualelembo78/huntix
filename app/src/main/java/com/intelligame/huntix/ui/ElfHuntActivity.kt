package com.intelligame.huntix.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.gamification.ElfHuntManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.hypot

/**
 * ElfHuntActivity — Caccia AR semplificata al regalo natalizio.
 *
 * Fasi: SETUP → FINDING → NEAR_GIFT → OPENING → REVEALED
 *
 * Semplificazioni rispetto al gioco Indoor principale:
 * no safe, no bucket, no keys, no multiplayer, no trap eggs.
 */
class ElfHuntActivity : AppCompatActivity() {

    private enum class HuntPhase { SETUP, FINDING, NEAR_GIFT, OPENING, REVEALED }

    private var currentPhase = HuntPhase.SETUP
    private var huntDay: Int = 1
    private lateinit var arSceneView: ARSceneView
    private var giftAnchorNode: AnchorNode? = null
    private var giftNode: Node? = null
    private var lastArFrame: Frame? = null
    private var planeDetected = false

    private var swipeStartY = 0f
    private var swipeStartTime = 0L

    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var rewardPopup: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val proximityRunnable = object : Runnable {
        override fun run() {
            if (currentPhase == HuntPhase.FINDING) {
                checkProximity()
                handler.postDelayed(this, 200)
            }
        }
    }
    private var pulseAnim: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        huntDay = intent.getIntExtra("day", 1).coerceIn(1, 25)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        arSceneView = ARSceneView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(arSceneView)

        overlayContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayContainer)

        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D0620"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }
        statusText = TextView(this).apply {
            text = "\uD83C\uDF81 Caccia al Regalo \u2014 Giorno $huntDay"
            textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        statusBar.addView(statusText)
        overlayContainer.addView(statusBar)

        instructionText = TextView(this).apply {
            text = "Muovi il telefono per trovare una superficie..."
            textSize = 13f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#CC0D0620"))
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(40)
            }
        }
        overlayContainer.addView(instructionText)

        setupRewardPopup()
        overlayContainer.addView(rewardPopup)

        setContentView(root)
        setupAR()
        checkCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(proximityRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(proximityRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startArSession()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startArSession()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Fotocamera necessaria")
                    .setMessage("La caccia al regalo richiede accesso alla fotocamera per la realta' aumentata.")
                    .setPositiveButton("Esci") { _, _ -> finish() }
                    .setCancelable(false).show()
            }
        }
    }

    private fun setupAR() {
        try {
            arSceneView.apply {
                planeRenderer.isEnabled = true
                configureSession { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    if (!session.isSupported(config)) {
                        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    }
                    config.focusMode = Config.FocusMode.AUTO
                }
                onSessionUpdated = { session, frame ->
                    lastArFrame = frame

                    if (!planeDetected && frame.camera.trackingState == TrackingState.TRACKING) {
                        val hasPlane = session.getAllTrackables(Plane::class.java)
                            .any { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
                        if (hasPlane) {
                            planeDetected = true
                            runOnUiThread { onFirstPlaneDetected() }
                        }
                    }

                    if (currentPhase == HuntPhase.FINDING &&
                        frame.camera.trackingState == TrackingState.TRACKING) {
                        checkProximity()
                    }
                }
                onTouchEvent = { event, _ ->
                    handleTouch(event)
                    true
                }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("AR non disponibile")
                .setMessage("Il tuo dispositivo non supporta ARCore.\n\nAggiorna ARCore dal Play Store e riprova.")
                .setPositiveButton("Esci") { _, _ -> finish() }
                .setCancelable(false).show()
        }
    }

    private fun startArSession() { }

    private fun onFirstPlaneDetected() {
        instructionText.text = "Superficie trovata! Piazzamento regalo..."
        statusText.text = "\uD83C\uDF81 Piazzamento..."
        placeGift()
    }

    private fun placeGift() {
        val session = arSceneView.session ?: return
        val planes = session.getAllTrackables(Plane::class.java).filter {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.extentX >= 0.25f && it.extentZ >= 0.25f
        }
        if (planes.isEmpty()) {
            runOnUiThread {
                instructionText.text = "Muovi il telefono verso il pavimento..."
            }
            return
        }

        val plane = planes.random()
        val rx = (kotlin.random.Random.nextFloat() - 0.5f) * plane.extentX * 0.6f
        val rz = (kotlin.random.Random.nextFloat() - 0.5f) * plane.extentZ * 0.6f
        val cx = plane.centerPose.tx() + rx
        val cy = plane.centerPose.ty()
        val cz = plane.centerPose.tz() + rz

        try {
            val anchor = session.createAnchor(
                Pose(floatArrayOf(cx, cy, cz), floatArrayOf(0f, 0f, 0f, 1f))
            )

            val sv = arSceneView
            val an = AnchorNode(engine = sv.engine, anchor = anchor)

            val redMat = sv.materialLoader.createColorInstance(
                color = Color.parseColor("#C62828")
            )
            val goldMat = sv.materialLoader.createColorInstance(
                color = Color.parseColor("#FFD700")
            )

            val giftBox = CubeNode(
                sv.engine,
                Size(GIFT_SIZE, GIFT_SIZE * 0.8f, GIFT_SIZE),
                materialInstance = redMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(giftBox)

            val ribbonH = CubeNode(
                sv.engine,
                Size(GIFT_SIZE * 1.02f, GIFT_SIZE * 0.1f, GIFT_SIZE * 0.12f),
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(ribbonH)

            val ribbonV = CubeNode(
                sv.engine,
                Size(GIFT_SIZE * 0.12f, GIFT_SIZE * 0.1f, GIFT_SIZE * 1.02f),
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.4f, 0f) }
            an.addChildNode(ribbonV)

            val bow = SphereNode(
                sv.engine,
                0.03f,
                materialInstance = goldMat
            ).apply { position = Position(0f, GIFT_SIZE * 0.85f, 0f) }
            an.addChildNode(bow)

            an.isVisible = false
            sv.addChildNode(an)

            giftAnchorNode = an
            giftNode = giftBox

            currentPhase = HuntPhase.FINDING
            runOnUiThread {
                statusText.text = "\uD83C\uDF81 Cercando regalo..."
                instructionText.text = "Cammina nella stanza per trovare il regalo nascosto..."
            }

            handler.postDelayed(proximityRunnable, 500)

        } catch (e: Exception) {
            runOnUiThread {
                instructionText.text = "Errore nel piazzamento. Riprova."
            }
        }
    }

    private fun checkProximity() {
        if (currentPhase != HuntPhase.FINDING) return
        val frame = lastArFrame ?: return
        val gift = giftAnchorNode ?: return

        try {
            val cam = frame.camera.pose.translation
            val giftPos = gift.anchor.pose.translation
            val dist = dist3(cam, giftPos)

            if (dist < GIFT_REVEAL_DISTANCE) {
                currentPhase = HuntPhase.NEAR_GIFT
                gift.isVisible = true
                startGiftPulse()
                handler.removeCallbacks(proximityRunnable)

                runOnUiThread {
                    statusText.text = "\uD83C\uDF81 Regalo trovato!"
                    instructionText.text = "Scorri verso l'alto per aprire il regalo! \uD83D\uDC46"
                }
            }
        } catch (_: Exception) {}
    }

    private fun dist3(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return hypot(hypot(dx, dy), dz)
    }

    private fun startGiftPulse() {
        pulseAnim?.cancel()
        val gift = giftNode ?: return
        pulseAnim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = 1f + 0.15f * kotlin.math.sin((anim.animatedValue as Float).toDouble()).toFloat()
                gift.scale = Scale(p, p, p)
            }
            start()
        }
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = event.y
                swipeStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (currentPhase != HuntPhase.NEAR_GIFT) return
                val dy = event.y - swipeStartY
                val dt = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                val validSwipe = dy < -90f && (dy / dt) * 1000f < -250f
                if (validSwipe) {
                    onGiftOpened()
                }
            }
        }
    }

    private fun onGiftOpened() {
        currentPhase = HuntPhase.OPENING
        pulseAnim?.cancel()
        statusText.text = "\uD83C\uDF81 Apertura..."
        instructionText.text = "Il regalo si sta aprendo..."

        val gift = giftNode ?: return
        val expandAnim = ValueAnimator.ofFloat(1f, 1.5f).apply {
            duration = 600
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                gift.scale = Scale(s, s, s)
            }
        }
        val fadeAnim = ObjectAnimator.ofFloat(giftAnchorNode, "alpha", 1f, 0f).apply {
            duration = 600
        }

        expandAnim.start()
        fadeAnim.start()

        handler.postDelayed({
            giftAnchorNode?.isVisible = false
            showReward()
        }, 700)
    }

    private fun setupRewardPopup() {
        rewardPopup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E60D0620"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
    }

    private fun showReward() {
        val result = ElfHuntManager.claimDay(this, huntDay)
        val (claimed, mvcReward) = result

        if (!claimed) {
            runOnUiThread {
                Toast.makeText(this, "Errore nel riscatto", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        val item = ElfHuntManager.itemForDay(huntDay)

        runOnUiThread {
            currentPhase = HuntPhase.REVEALED
            rewardPopup.removeAllViews()
            rewardPopup.visibility = View.VISIBLE

            rewardPopup.addView(TextView(this).apply {
                text = "\uD83C\uDF89 Regalo Aperto!"
                textSize = 24f; setTextColor(Color.parseColor("#FFD700"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })

            rewardPopup.addView(TextView(this).apply {
                text = "Giorno $huntDay di 25"
                textSize = 14f; setTextColor(Color.parseColor("#A78BFA"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })

            rewardPopup.addView(TextView(this).apply {
                text = "\uD83D\uDCB0 +$mvcReward MVC"
                textSize = 28f; setTextColor(Color.parseColor("#00FF88"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })

            if (item != null) {
                rewardPopup.addView(TextView(this).apply {
                    text = "\u2B50 $item"
                    textSize = 18f; setTextColor(Color.parseColor("#FFD700"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(4))
                })
                rewardPopup.addView(TextView(this).apply {
                    text = ElfHuntManager.itemDescriptionForDay(huntDay) ?: ""
                    textSize = 12f; setTextColor(Color.parseColor("#A78BFA"))
                    gravity = Gravity.CENTER
                    setPadding(dp(16), 0, dp(16), dp(16))
                })
            }

            if (huntDay == 25) {
                val stats = ElfHuntManager.getBabboCacciatoreStats()
                if (stats != null) {
                    rewardPopup.addView(TextView(this).apply {
                        text = "\uD83E\uDDBF Creature leggendaria!"
                        textSize = 14f; setTextColor(Color.parseColor("#FF6EC7"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, dp(4))
                    })
                    rewardPopup.addView(TextView(this).apply {
                        text = "ATK: ${stats["atk"]}  DEF: ${stats["def"]}  HP: ${stats["hp"]}"
                        textSize = 12f; setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    rewardPopup.addView(TextView(this).apply {
                        text = stats["special"] as String
                        textSize = 11f; setTextColor(Color.parseColor("#A78BFA"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(4), 0, dp(8))
                    })
                }
            }

            val backBtn = LinearLayout(this@ElfHuntActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#A78BFA"))
                }
                setPadding(dp(24), dp(14), dp(24), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(24) }
                isClickable = true; isFocusable = true
                setOnClickListener { finish() }
            }
            backBtn.addView(TextView(this@ElfHuntActivity).apply {
                text = "Torna al calendario \uD83D\uDCC5"
                textSize = 14f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
            rewardPopup.addView(backBtn)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val RC_CAMERA = 1001
        private const val GIFT_REVEAL_DISTANCE = 1.5f
        private const val GIFT_SIZE = 0.15f
    }
}

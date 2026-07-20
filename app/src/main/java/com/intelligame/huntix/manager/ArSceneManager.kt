package com.intelligame.huntix.manager

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.intelligame.huntix.MainActivity
import com.intelligame.huntix.launchBasket
import com.intelligame.huntix.updateUI
import com.intelligame.huntix.R
import com.intelligame.huntix.IndoorArSync
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import io.github.sceneview.node.Node
import kotlin.math.abs

class ArSceneManager(internal val activity: MainActivity) {

    internal val binding get() = activity.binding
    internal val viewModel get() = activity.viewModel

    // Room scan state
    internal var lastKnownPlaneCount = 0
    internal var scanLiveBadgeAnim: ValueAnimator? = null
    internal var newSurfaceHideHandler: Handler? = null

    fun setupAR() {
        try {
            binding.sceneView.apply {
                planeRenderer.isEnabled = true
                configureSession { session, config ->
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    // ENVIRONMENTAL_HDR richiede supporto dispositivo: proviamolo,
                    // altrimenti fallback ad AMBIENT_INTENSITY (senza fallback le
                    // uova resterebbero nere su molti device).
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    if (!session.isSupported(config)) {
                        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    }
                    config.focusMode = Config.FocusMode.AUTO
                    config.depthMode = when {
                        viewModel.arMode == "standard" -> Config.DepthMode.DISABLED
                        session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> Config.DepthMode.AUTOMATIC
                        else -> Config.DepthMode.DISABLED
                    }
                    config.cloudAnchorMode = if (viewModel.isIndoorMp)
                        Config.CloudAnchorMode.ENABLED
                    else
                        Config.CloudAnchorMode.DISABLED
                }
                onSessionUpdated = handler@{ session, frame ->
                    if (!activity.isActive) return@handler
                    activity.lastArFrame = frame

                    if (activity.gamePhase == GamePhase.SCAN_ROOM && activity.roomScanManager.isScanning) {
                        val prog = activity.roomScanManager.getProgressPercent(session)
                        val secs = activity.roomScanManager.getRemainingSeconds()
                        val surfs = activity.roomScanManager.getSurfaceDescriptions(session)
                        val count = activity.roomScanManager.getTrackedPlaneCount(session)
                        activity.runOnUiThread {
                            updateScanProgress(prog, secs, surfs, count)
                        }
                        if (activity.roomScanManager.isScanComplete(session)) {
                            activity.runOnUiThread { onScanComplete() }
                        }
                    }

                    val camTracking = frame.camera.trackingState
                    if (camTracking == TrackingState.TRACKING) {
                        if (activity.trackingLostFrames > 30) {
                            activity.runOnUiThread {
                                binding.statusDot.setBackgroundResource(R.drawable.circle_green)
                                binding.tvStatus.text = if (activity.planeDetected) "Piano trovato" else "Cerco superfici..."
                            }
                        }
                        activity.trackingLostFrames = 0
                    } else {
                        val nowMs = System.currentTimeMillis()
                        if (++activity.trackingLostFrames > 20 && nowMs - activity.lastTrackingHintMs > 3000L) {
                            activity.lastTrackingHintMs = nowMs
                            activity.runOnUiThread { handleTrackingLost(frame.camera.trackingFailureReason) }
                        }
                    }

                    if (!activity.planeDetected && camTracking == TrackingState.TRACKING) {
                        val hasPlane = session.getAllTrackables(Plane::class.java)
                            .any { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
                        if (hasPlane) {
                            activity.planeDetected = true
                            activity.runOnUiThread { onFirstPlaneDetected() }
                        }
                    }
                    if (activity.gamePhase == GamePhase.PLAYING &&
                        activity.playState != PlayState.THROWING && activity.playState != PlayState.TICKET_SHOWN &&
                        ++activity.frameCount % 6 == 0 &&
                        camTracking == TrackingState.TRACKING) checkProximity(frame)
                }
                onTouchEvent = { event, svHit ->
                    handleTouch(event, svHit?.node)
                    true
                }
            }

            if (viewModel.arMode == "room_scan") {
                activity.uiHandler.postDelayed({
                    if (activity.isActive && activity.gamePhase == GamePhase.SCAN_ROOM) startRoomScan()
                }, 1000L)
            }

            activity.uiHandler.postDelayed({
                if (!activity.planeDetected && activity.isActive &&
                    (activity.gamePhase == GamePhase.SETUP_SAFE || activity.gamePhase == GamePhase.SCAN_ROOM)) {
                    activity.runOnUiThread {
                        showTemporaryOverlay(
                            "Difficolta' con la superficie?\n" +
                            "Muovi lentamente il telefono\npuntando verso il pavimento.\n" +
                            "Evita superfici molto uniformi\no con scarsa illuminazione.", 5000L)
                    }
                }
            }, 10_000L)
        } catch (e: Exception) {
            android.util.Log.e("EggHunt", "ARCore init failed: ${e.message}", e)
            activity.runOnUiThread {
                android.app.AlertDialog.Builder(activity)
                    .setTitle("AR non disponibile")
                    .setMessage("Il tuo dispositivo non supporta ARCore o la versione installata e' troppo vecchia.\n\nAggiorna ARCore dal Play Store e riprova.")
                    .setPositiveButton("Apri Play Store") { _, _ ->
                        try {
                            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=com.google.ar.core")))
                        } catch (_: Exception) {
                            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")))
                        }
                    }
                    .setNegativeButton("Esci") { _, _ -> activity.finish() }
                    .setCancelable(false).show()
            }
        }
    }

    private fun checkProximity(frame: Frame) {
        if (!activity.isActive || activity.isFinishing || activity.isDestroyed) return
        val target = activity.eggs.getOrNull(activity.currentEggIdx) ?: return
        try {
            val cam = frame.camera.pose.translation
            val eggDist = dist3(cam, target.anchorNode.anchor.pose.translation)
            val safeDist = activity.safeObject?.let { dist3(cam, it.anchorNode.anchor.pose.translation) } ?: Float.MAX_VALUE
            val revealD = activity.dm.getRevealDistMeters()
            val catchD = activity.dm.getCatchDistMeters()

            val shouldShow = !activity.keyInPocket && eggDist < revealD
            if (target.anchorNode.isVisible != shouldShow) {
                target.anchorNode.isVisible = shouldShow
                if (shouldShow) activity.runOnUiThread { activity.eggPlacementManager.startEggPulse(target) }
                else target.pulseAnim?.cancel()
            }

            val newState: PlayState? = when {
                activity.playState == PlayState.SEARCHING && !activity.keyInPocket && eggDist < catchD -> PlayState.NEAR_EGG
                activity.playState == PlayState.NEAR_EGG && eggDist >= catchD -> PlayState.SEARCHING
                activity.playState == PlayState.KEY_OBTAINED && safeDist < 1.5f -> PlayState.NEAR_SAFE
                activity.playState == PlayState.NEAR_SAFE && safeDist >= 1.5f -> PlayState.KEY_OBTAINED
                else -> null
            }
            if (newState != null) activity.runOnUiThread {
                if (activity.isActive && !activity.isFinishing) {
                    activity.playState = newState
                    activity.updateUI()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ArScene", "checkProximity failed: ${e.message}")
        }
    }

    private fun handleTouch(event: MotionEvent, hitNode: Node?) {
        if (activity.isMenuOpen) return
        when (activity.gamePhase) {
            GamePhase.SCAN_ROOM -> {}
            GamePhase.SETUP_SAFE -> if (event.actionMasked == MotionEvent.ACTION_UP)
                arSurfaceHitTest(event)?.let { activity.safeManager.placeSafe(it) }
            GamePhase.SETUP_EGGS -> handleSetupTouch(event, hitNode)
            GamePhase.PLAYING -> handlePlayingTouch(event)
            else -> Unit
        }
    }

    private fun handleSetupTouch(event: MotionEvent, hitNode: Node?) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        if (viewModel.eggSetupMode == "auto") return
        val arHit = arSurfaceHitTest(event)
        if (arHit != null) {
            val hp = arHit.hitPose.translation
            val nearby = activity.eggs.minByOrNull { egg ->
                val p = egg.anchorNode.anchor.pose.translation
                val dx = hp[0] - p[0]
                val dz = hp[2] - p[2]
                dx * dx + dz * dz
            }?.takeIf { egg ->
                val p = egg.anchorNode.anchor.pose.translation
                val dx = hp[0] - p[0]
                val dz = hp[2] - p[2]
                kotlin.math.sqrt(dx * dx + dz * dz) < 0.22f
            }
            if (nearby != null) {
                if (activity.selectedEgg == nearby) activity.eggPlacementManager.deselectEgg() else activity.eggPlacementManager.selectEgg(nearby)
            } else {
                activity.eggPlacementManager.deselectEgg()
                activity.eggPlacementManager.placeEgg(arHit, activity.nextEggIsTrap, activity.nextEggShape)
            }
        } else {
            val tappedEgg = activity.eggs.find { it.eggNode == hitNode || it.trapMarkerNode == hitNode }
            if (tappedEgg != null) {
                if (activity.selectedEgg == tappedEgg) activity.eggPlacementManager.deselectEgg() else activity.eggPlacementManager.selectEgg(tappedEgg)
            } else {
                activity.eggPlacementManager.deselectEgg()
            }
        }
    }

    private fun handlePlayingTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activity.swipeStartX = event.x
                activity.swipeStartY = event.y
                activity.swipeStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (activity.playState != PlayState.NEAR_EGG || activity.throwInProgress) return
                val dy = event.y - activity.swipeStartY
                val dt = (System.currentTimeMillis() - activity.swipeStartTime).coerceAtLeast(1L)
                val valid = dy < -90f && (dy / dt) * 1000f < -250f && activity.swipeStartY > binding.root.height * 0.4f
                if (valid) {
                    SoundManager.playThrow()
                    activity.launchBasket(activity.swipeStartX, activity.swipeStartY,
                        abs(event.x - activity.swipeStartX) < abs(dy) * 1.8f)
                }
            }
        }
    }

    fun arSurfaceHitTest(event: MotionEvent): HitResult? {
        val frame = activity.lastArFrame ?: return null
        val hits = frame.hitTest(event)
        return when (viewModel.arMode) {
            "standard" -> hits.firstOrNull { hr ->
                val t = hr.trackable
                t is Plane && t.isPoseInPolygon(hr.hitPose) && t.trackingState == TrackingState.TRACKING
            }
            else -> {
                hits.firstOrNull { hr ->
                    when (val t = hr.trackable) {
                        is Plane -> t.isPoseInPolygon(hr.hitPose) && t.trackingState == TrackingState.TRACKING
                        is DepthPoint -> true
                        else -> false
                    }
                } ?: hits.firstOrNull { hr ->
                    hr.trackable.trackingState == TrackingState.TRACKING
                }
            }
        }
    }

    private fun onFirstPlaneDetected() {
        binding.statusDot.setBackgroundResource(R.drawable.circle_green)
        binding.tvStatus.text = "Piano trovato"
        binding.reticle.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(binding.reticle, "alpha", 0.3f, 1.0f).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
        }.start()
        when {
            viewModel.isRestoreMode -> {
                binding.tvInstruction.text = "Piazza la cassaforte NELLO STESSO PUNTO di prima"
                binding.tvInstruction.visibility = View.VISIBLE
            }
            activity.gamePhase == GamePhase.SCAN_ROOM -> {
                binding.tvScanInstruction.text = "Prima superficie rilevata!\nContinua a muovere il telefono per mappare piu' aree"
            }
            activity.gamePhase == GamePhase.SETUP_SAFE -> {
                if (viewModel.isIndoorMp && !viewModel.indoorIsHost && activity.pendingCloudRestore) {
                    activity.safeManager.attemptAutoSafeRestore()
                } else {
                    activity.safeManager.showSafeTypePicker()
                }
            }
        }
    }

    private fun handleTrackingLost(reason: TrackingFailureReason) {
        val hint = when (reason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT ->
                "Troppo buio!\nSposta il telefono verso una zona piu' illuminata."
            TrackingFailureReason.EXCESSIVE_MOTION ->
                "Movimenti troppo veloci!\nMuovi il telefono piu' lentamente."
            TrackingFailureReason.INSUFFICIENT_FEATURES ->
                "Superficie troppo uniforme!\nPunta verso oggetti, bordi o pavimento con piu' texture.\nEvita muri bianchi o pavimenti lisci monocolore."
            TrackingFailureReason.CAMERA_UNAVAILABLE ->
                "Fotocamera non disponibile.\nRiavvia l'app se il problema persiste."
            else ->
                "AR persa!\nMuovi lentamente il telefono verso il pavimento."
        }
        binding.statusDot.setBackgroundResource(R.drawable.circle_red)
        binding.tvStatus.text = "Ricalibro..."
        showTemporaryOverlay(hint, 3000L)
    }

    fun cleanup() {
        scanLiveBadgeAnim?.cancel()
        newSurfaceHideHandler?.removeCallbacksAndMessages(null)
    }
}

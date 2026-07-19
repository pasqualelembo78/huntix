package com.intelligame.huntix.manager

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import com.intelligame.huntix.updateUI
import com.intelligame.huntix.model.GamePhase

internal fun ArSceneManager.startRoomScan() {
    val session = binding.sceneView.session
    if (session != null) activity.roomScanManager.startScan(session)
    lastKnownPlaneCount = 0
    binding.sceneView.planeRenderer.isEnabled = true
    activity.runOnUiThread {
        binding.scanOverlay.visibility = View.VISIBLE
        binding.scanGuideCenter.visibility = View.VISIBLE
        binding.tvScanTitle.text = "Scansione stanza"
        binding.tvScanInstruction.text = "Punta la fotocamera e cammina - pavimento, pareti, mobili"
        binding.scanProgressBar.progress = 0
        binding.tvScanPercent.text = "0%"
        binding.tvScanSurfaces.text = "In attesa di superfici..."
        binding.tvNewSurface.visibility = View.INVISIBLE
        startLiveBadgePulse()
        binding.btnSkipScan.setOnClickListener {
            activity.roomScanManager.forceComplete()
            onScanComplete()
        }
    }
}

internal fun ArSceneManager.startLiveBadgePulse() {
    scanLiveBadgeAnim?.cancel()
    scanLiveBadgeAnim = ObjectAnimator.ofFloat(binding.tvScanLiveBadge, "alpha", 1f, 0.2f).apply {
        duration = 600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        start()
    }
}

internal fun ArSceneManager.updateScanProgress(percent: Int, remainingSecs: Int, surfaces: List<String>, planeCount: Int) {
    if (!activity.isActive) return
    binding.scanProgressBar.progress = percent
    binding.tvScanPercent.text = "$percent%"
    val barColor = when {
        percent >= 70 -> 0xFF00FF88.toInt()
        percent >= 40 -> 0xFFFF9800.toInt()
        else -> 0xFFFFD700.toInt()
    }
    binding.scanProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(barColor)
    val timeStr = when {
        remainingSecs <= 0 -> "Quasi fatto..."
        remainingSecs < 5 -> "${remainingSecs}s"
        else -> "~${remainingSecs}s"
    }
    binding.tvScanInstruction.text = when {
        percent < 20 -> "Punta verso il pavimento e cammina - $timeStr"
        percent < 50 -> "Ora punta verso pareti e mobili - $timeStr"
        percent < 80 -> "Ottimo! Cerca angoli e sotto i mobili - $timeStr"
        else -> "Copertura eccellente! $timeStr"
    }
    binding.tvScanSurfaces.text = surfaces.joinToString(", ")
    if (planeCount > 0) binding.scanGuideCenter.visibility = View.GONE
    if (planeCount > lastKnownPlaneCount) {
        lastKnownPlaneCount = planeCount
        flashNewSurface()
    }
}

internal fun ArSceneManager.flashNewSurface() {
    binding.tvNewSurface.visibility = View.VISIBLE
    binding.tvNewSurface.alpha = 1f
    newSurfaceHideHandler?.removeCallbacksAndMessages(null)
    newSurfaceHideHandler = Handler(Looper.getMainLooper()).also {
        it.postDelayed({
            ObjectAnimator.ofFloat(binding.tvNewSurface, "alpha", 1f, 0f).apply {
                duration = 600
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(anim: android.animation.Animator) {
                        binding.tvNewSurface.visibility = View.INVISIBLE
                    }
                })
                start()
            }
        }, 900L)
    }
}

internal fun ArSceneManager.onScanComplete() {
    if (!activity.isActive || activity.gamePhase != GamePhase.SCAN_ROOM) return
    activity.roomScanManager.stopScan()
    scanLiveBadgeAnim?.cancel()
    newSurfaceHideHandler?.removeCallbacksAndMessages(null)
    activity.gamePhase = GamePhase.SETUP_SAFE
    binding.scanOverlay.visibility = View.GONE
    binding.sceneView.planeRenderer.isEnabled = true
    showTemporaryOverlay("Stanza mappata!\n\nOra tocca qualsiasi\nsuperficie colorata per\npiazzare la cassaforte.", 4000L)
    activity.updateUI()
}

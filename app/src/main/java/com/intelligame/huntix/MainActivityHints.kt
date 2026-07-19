package com.intelligame.huntix

import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.OnUserEarnedRewardListener
import androidx.constraintlayout.widget.ConstraintLayout
import android.view.animation.OvershootInterpolator
import com.intelligame.huntix.model.GamePhase
import com.intelligame.huntix.model.PlayState
import kotlin.math.hypot

// ── Indizio (Rewarded Ad) ──────────────────────────────────────
internal fun MainActivity.onHintRequested() {
    if (gamePhase != GamePhase.PLAYING) return
    if (playState != PlayState.SEARCHING && playState != PlayState.NEAR_EGG) return
    val now = SystemClock.elapsedRealtime()
    if (now < hintCooldownUntilMs) return
    hintCooldownUntilMs = now + 3_000L

    val ad = rewardedAd
    if (ad != null) {
        ad.show(this, OnUserEarnedRewardListener { _ ->
            runOnUiThread { showHint() }
        })
    } else {
        Toast.makeText(this, "Pubblicita' non pronta, riprova tra poco!", Toast.LENGTH_LONG).show()
        loadRewardedAd()
    }
}

internal fun MainActivity.showHint() {
    val frame = lastArFrame ?: run {
        Toast.makeText(this, "AR non attiva, muovi il telefono", Toast.LENGTH_SHORT).show()
        return
    }
    val target = eggs.getOrNull(currentEggIdx) ?: return
    try {
        val cam = frame.camera.pose
        val egg = target.anchorNode.anchor.pose.translation
        val dx  = egg[0] - cam.tx()
        val dz  = egg[2] - cam.tz()
        val distM = hypot(dx, dz)

        val distLbl = when {
            distM < 0.5f  -> "meno di 50 cm"
            distM < 1.5f  -> "circa ${(distM * 100).toInt()} cm"
            distM < 5f    -> "circa ${"%.1f".format(distM)} m"
            else          -> "piu' di ${distM.toInt()} m"
        }

        val fwd   = cam.rotateVector(floatArrayOf(0f, 0f, -1f))
        val right = cam.rotateVector(floatArrayOf(1f, 0f,  0f))
        fun dot2D(ax: Float, az: Float, bx: Float, bz: Float) = ax * bx + az * bz
        val fwdDot   = dot2D(fwd[0],   fwd[2],   dx, dz)
        val rightDot = dot2D(right[0], right[2], dx, dz)

        val vertic = when {
            fwdDot >  0.3f * distM -> "davanti a te"
            fwdDot < -0.3f * distM -> "dietro di te"
            else -> ""
        }
        val horiz = when {
            rightDot >  0.3f * distM -> "a destra"
            rightDot < -0.3f * distM -> "a sinistra"
            else -> ""
        }
        val dir = listOf(vertic, horiz).filter { it.isNotEmpty() }.joinToString(" e ").ifEmpty { "intorno a te" }

        showHintOverlay("Indizio:\nL'uovo e' $distLbl\n$dir")
    } catch (_: Exception) {
        Toast.makeText(this, "Indizio non disponibile", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.showHintOverlay(message: String) {
    val root = binding.arContainer
    val tv = TextView(this).apply {
        text = message
        textSize = 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setBackgroundColor(Color.parseColor("#DD1B5E20"))
        setPadding(dp(28), dp(20), dp(28), dp(20))
        alpha = 0f; scaleX = 0.85f; scaleY = 0.85f
        layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).also { lp ->
            lp.topToTop      = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart  = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd      = ConstraintLayout.LayoutParams.PARENT_ID
            lp.verticalBias  = 0.35f
        }
    }
    root.addView(tv)
    tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
        .setInterpolator(OvershootInterpolator(1.3f))
        .withEndAction {
            uiHandler.postDelayed({
                if (!isActive) { try { root.removeView(tv) } catch (_: Exception) {}; return@postDelayed }
                tv.animate().alpha(0f).translationYBy(-40f).setDuration(400)
                    .withEndAction { runOnUiThread { try { root.removeView(tv) } catch (_: Exception) {} } }
                    .start()
            }, 3_500L)
        }.start()
}

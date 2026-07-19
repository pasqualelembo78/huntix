package com.intelligame.huntix.manager

import android.view.animation.AccelerateInterpolator
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.random.Random

internal fun SafeManager.launchGoldenBurst() {
    if (!activity.isActive || activity.isFinishing) return
    val goldenEmojis = listOf("sparkles", "star", "heart", "star2", "sparkles", "heart2", "cherry_blossom", "sparkles", "confetti_ball", "ring", "sparkles", "cherry_blossom")
    val root = binding.arContainer
    val (w, h) = activity.windowSizePx()
    goldenEmojis.forEachIndexed { i, emoji ->
        val tv = TextView(activity).apply {
            text = emoji
            textSize = 30f
            alpha = 0f
            x = w * 0.5f + (Random.nextFloat() - 0.5f) * w * 0.75f
            y = h * 0.38f + (Random.nextFloat() - 0.5f) * h * 0.38f
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(tv)
        activity.uiHandler.postDelayed({
            if (!activity.isActive) {
                try { root.removeView(tv) } catch (_: Exception) {}
                return@postDelayed
            }
            tv.animate().alpha(1f).scaleX(2.4f).scaleY(2.4f).setDuration(190)
                .withEndAction {
                    activity.uiHandler.postDelayed({
                        if (!activity.isActive) {
                            try { root.removeView(tv) } catch (_: Exception) {}
                            return@postDelayed
                        }
                        tv.animate().alpha(0f).scaleX(0.1f).scaleY(0.1f)
                            .translationYBy(-160f + Random.nextFloat() * -100f)
                            .translationXBy((Random.nextFloat() - 0.5f) * 260f)
                            .setDuration(560).setInterpolator(AccelerateInterpolator())
                            .withEndAction { try { root.removeView(tv) } catch (_: Exception) {} }
                            .start()
                    }, 180)
                }.start()
        }, i * 65L)
    }
}

internal fun SafeManager.launchSparkles() {
    if (!activity.isActive || activity.isFinishing) return
    val sparkles = listOf("sparkles", "star", "star2", "sparkles", "star", "sparkles", "star2", "star")
    val root = binding.arContainer
    val (w, h) = activity.windowSizePx()
    sparkles.forEachIndexed { i, emoji ->
        val tv = TextView(activity).apply {
            text = emoji
            textSize = 26f
            alpha = 0f
            x = (50f + Random.nextFloat() * (w - 100f))
            y = (h * 0.05f + Random.nextFloat() * h * 0.75f)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(tv)
        activity.uiHandler.postDelayed({
            if (!activity.isActive) {
                try { root.removeView(tv) } catch (_: Exception) {}
                return@postDelayed
            }
            tv.animate().alpha(1f).scaleX(1.6f).scaleY(1.6f).setDuration(300)
                .withEndAction {
                    activity.uiHandler.postDelayed({
                        if (!activity.isActive) {
                            try { root.removeView(tv) } catch (_: Exception) {}
                            return@postDelayed
                        }
                        tv.animate().alpha(0f).scaleX(0f).scaleY(0f).translationYBy(-60f)
                            .setDuration(500)
                            .withEndAction { try { root.removeView(tv) } catch (_: Exception) {} }
                            .start()
                    }, 500)
                }.start()
        }, i * 100L)
    }
}

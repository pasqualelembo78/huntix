package com.intelligame.huntix.manager

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.hypot

internal fun ArSceneManager.showTemporaryOverlay(message: String, durationMs: Long) {
    val root = binding.arContainer
    root.findViewWithTag<TextView>("trackingHint")?.let {
        try { root.removeView(it) } catch (_: Exception) {}
    }
    val tv = TextView(activity).apply {
        tag = "trackingHint"
        text = message
        textSize = 14f
        setTextColor(AndroidColor.WHITE)
        gravity = android.view.Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setBackgroundColor(AndroidColor.parseColor("#DD8B0000"))
        setPadding(activity.dp(22), activity.dp(14), activity.dp(22), activity.dp(14))
        alpha = 0f
        layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).also { lp ->
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.verticalBias = 0.72f
        }
    }
    root.addView(tv)
    tv.animate().alpha(1f).setDuration(250).withEndAction {
        activity.uiHandler.postDelayed({
            if (!activity.isActive) {
                try { root.removeView(tv) } catch (_: Exception) {}
                return@postDelayed
            }
            tv.animate().alpha(0f).setDuration(350)
                .withEndAction { activity.runOnUiThread { try { root.removeView(tv) } catch (_: Exception) {} } }
                .start()
        }, durationMs)
    }.start()
}

internal fun ArSceneManager.dist3(a: FloatArray, b: FloatArray) = hypot(hypot(a[0] - b[0], a[1] - b[1]), a[2] - b[2])

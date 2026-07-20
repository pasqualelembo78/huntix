package com.intelligame.huntix.manager

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.intelligame.huntix.SoundManager
import com.intelligame.huntix.finishGame
import com.intelligame.huntix.updateUI
import com.intelligame.huntix.model.PlayState

internal fun SafeManager.onTicketClosed() {
    closeSafeDoor {
        // Secchiello: mostra i biglietti rimanenti prima di passare all'uovo successivo
        if (activity.pendingTickets > 1) {
            activity.pendingTickets -= 1
            showDepositTicket()
            return@closeSafeDoor
        }
        activity.pendingTickets = 0
        activity.advanceAfterDeposit()
    }
}

internal fun SafeManager.showTurnSwitchOverlay(playerName: String) {
    SoundManager.playTurnSwitch()
    val root = binding.arContainer
    val tv = TextView(activity).apply {
        text = "  Turno di\n$playerName!"
        textSize = 26f
        setTextColor(AndroidColor.WHITE)
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        setBackgroundColor(AndroidColor.parseColor("#DD1A237E"))
        setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24))
        alpha = 0f
        scaleX = 0.6f
        scaleY = 0.6f
        x = root.width / 2f - 200f
        y = root.height / 3f
        layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT)
    }
    root.addView(tv)
    tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400)
        .setInterpolator(OvershootInterpolator(1.5f)).withEndAction {
            activity.uiHandler.postDelayed({
                tv.animate().alpha(0f).translationYBy(-100f).setDuration(400)
                    .withEndAction {
                        root.removeView(tv)
                        activity.playState = PlayState.SEARCHING
                        activity.updateUI()
                    }.start()
            }, 1800)
        }.start()
}

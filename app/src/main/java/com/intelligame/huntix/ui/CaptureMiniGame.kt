package com.intelligame.huntix.ui

import android.graphics.Color
import android.view.View

interface CaptureMiniGame {

    fun getView(): View

    fun setEggColor(color: Int)

    fun reset()

    fun setListener(listener: Listener)

    fun release()

    interface Listener {
        fun onCaptured(totalAttempts: Int)
        fun onEscaped(totalAttempts: Int)
        fun onThrowAttempt(attempt: Int, quality: Float)
    }

    companion object {
        fun parseColor(hex: String): Int {
            val h = hex.removePrefix("#")
            if (h.length != 6) return Color.parseColor("#FFCC00")
            val r = h.substring(0, 2).toInt(16)
            val g = h.substring(2, 4).toInt(16)
            val b = h.substring(4, 6).toInt(16)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

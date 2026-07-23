package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.intelligame.huntix.UiKit

/**
 * EmotePopup — popup con griglia di emote selezionabili.
 * Mostra 6 emote in griglia 3x2. Quando una viene toccata, chiama onEmote.
 */
class EmotePopup(private val context: Context, private val onEmote: (String, String) -> Unit) {

    data class Emote(val emoji: String, val name: String)

    private val emotes = listOf(
        Emote("\uD83D\uDC4B", "Saluta"),
        Emote("\uD83D\uDD7A", "Balla"),
        Emote("\uD83D\uDE02", "Ridi"),
        Emote("\uD83D\uDE22", "Piangi"),
        Emote("\uD83E\uDE91", "Seduti"),
        Emote("\uD83D\uDD7A", "Danza")
    )

    fun show(anchor: View) {
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 8), UiKit.dp(context, 8),
                UiKit.dp(context, 8), UiKit.dp(context, 8))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 14).toFloat()
                setColor(0xDD1A1030.toInt())
                setStroke(1, 0x44FFFFFF)
            }
        }

        for (row in 0..1) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (col in 0..2) {
                val idx = row * 3 + col
                if (idx < emotes.size) {
                    val emote = emotes[idx]
                    val btn = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        val size = UiKit.dp(context, 56)
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginStart = UiKit.dp(context, 2)
                            marginEnd = UiKit.dp(context, 2)
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            onEmote(emote.emoji, emote.name)
                        }
                    }
                    btn.addView(TextView(context).apply {
                        text = emote.emoji; textSize = 24f; gravity = Gravity.CENTER
                    })
                    btn.addView(TextView(context).apply {
                        text = emote.name; textSize = 8f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                        gravity = Gravity.CENTER; maxLines = 1
                    })
                    rowLayout.addView(btn)
                }
            }
            grid.addView(rowLayout)
        }

        val popup = PopupWindow(grid,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true)
        popup.elevation = 16f
        popup.showAsDropDown(anchor, 0, -anchor.height)
    }
}

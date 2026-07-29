package com.intelligame.huntix.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.R
import com.intelligame.huntix.UiKit

object CaptureMethodPickerDialog {

    fun show(context: Context, onDismiss: (() -> Unit)? = null) {
        val currentMethod = CapturePreferences.getPreferredMethod(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiKit.dp(context, 16),
                UiKit.dp(context, 8),
                UiKit.dp(context, 16),
                UiKit.dp(context, 8)
            )
        }

        val title = TextView(context).apply {
            text = "Scegli metodo di cattura"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(context, 12))
        }
        container.addView(title)

        val subtitle = TextView(context).apply {
            text = "Il metodo selezionato verrà usato per tutte le catture. Puoi cambiarlo quando vuoi."
            textSize = 13f
            setTextColor(Color.parseColor("#888899"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(context, 16))
        }
        container.addView(subtitle)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setCancelable(true)
            .setNegativeButton("Chiudi", null)
            .create()

        CaptureMethod.values().forEach { method ->
            val isSelected = method == currentMethod
            val card = createMethodCard(context, method, isSelected) {
                CapturePreferences.setPreferredMethod(context, method)
                dialog.dismiss()
                onDismiss?.invoke()
            }
            container.addView(card)
        }

        dialog.show()
    }

    private fun createMethodCard(
        context: Context,
        method: CaptureMethod,
        isSelected: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val dp8 = UiKit.dp(context, 8)
        val dp12 = UiKit.dp(context, 12)
        val dp16 = UiKit.dp(context, 16)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp12, dp16, dp12)

            setBackgroundResource(if (isSelected) R.drawable.btn_accent else R.drawable.btn_purple)
            val bgAlpha = if (isSelected) 255 else 120
            setAlpha(bgAlpha / 255f)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp8)
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val emojiText = TextView(context).apply {
                text = "${method.emoji}  ${method.displayName}"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            topRow.addView(emojiText)

            val diffBadge = TextView(context).apply {
                text = method.difficultyLabel
                textSize = 11f
                setTextColor(Color.parseColor("#888899"))
                gravity = Gravity.CENTER
                setPadding(dp12, 4, dp12, 4)
            }
            topRow.addView(diffBadge)

            addView(topRow)

            val descText = TextView(context).apply {
                text = method.description
                textSize = 13f
                setTextColor(Color.parseColor("#CCCCDD"))
                setPadding(0, dp8, 0, 0)
            }
            addView(descText)

            if (isSelected) {
                val selectedLabel = TextView(context).apply {
                    text = "✓  ATTIVO"
                    textSize = 11f
                    setTextColor(Color.parseColor("#00FF88"))
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.END
                }
                addView(selectedLabel)
            }

            setOnClickListener {
                CapturePreferences.setPreferredMethod(context, method)
                onClick()
            }
        }
    }
}

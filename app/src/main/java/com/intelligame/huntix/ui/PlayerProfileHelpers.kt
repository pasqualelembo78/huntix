package com.intelligame.huntix.ui

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

internal fun PlayerProfileActivity.sectionCard(bgHex: String, borderHex: String, block: LinearLayout.() -> Unit): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor(bgHex))
            setStroke(dp(1), Color.parseColor(borderHex))
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(12) }
        block()
    }
}

internal fun PlayerProfileActivity.rowText(label: String, value: String, labelColorHex: String, valueColorHex: String): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(3), 0, dp(3))
        addView(tv(label, 13f, Color.parseColor(labelColorHex), Gravity.START).also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(tv(value, 14f, Color.parseColor(valueColorHex), Gravity.END, true).also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }
}

internal fun PlayerProfileActivity.buildProgressBar(progressPercent: Int, colorHex: String): ProgressBar {
    return ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progress = progressPercent
        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
    }
}

internal fun PlayerProfileActivity.tabBg(selected: Boolean): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
        if (selected) {
            setColor(Color.parseColor("#3D1A8A"))
            setStroke(dp(1), Color.parseColor("#8B5CF6"))
        } else {
            setColor(Color.parseColor("#1A1A2E"))
            setStroke(dp(1), Color.parseColor("#2A2A4A"))
        }
    }
}

internal fun PlayerProfileActivity.spacer(h: Int) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
}

internal fun PlayerProfileActivity.tv(t: String, s: Float, c: Int, g: Int, b: Boolean = false) =
    TextView(this).apply {
        text = t; textSize = s; setTextColor(c); gravity = g
        if (b) typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }

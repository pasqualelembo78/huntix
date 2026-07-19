package com.intelligame.huntix

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.widget.NestedScrollView

/**
 * UiKit — helper condivisi per costruire schermate programmatiche
 * (nessun XML layout), coerenti con lo stile Brawl-Stars di Huntix.
 *
 * Usato da tutte le Activity "contenuto" (Settings, Stats, Shop, ecc.)
 * per ridurre il boilerplate e mantenere consistenza visiva.
 */
object UiKit {

    const val BG        = "#0D0620"
    const val BG_CARD   = "#1A1030"
    const val ACCENT    = "#A78BFA"
    const val GREEN     = "#00FF88"
    const val TEXT_DIM  = "#6B5B95"
    const val PURPLE    = "#6A1B9A"

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun scroll(c: Context, vararg children: View): NestedScrollView {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 80))
        }
        children.forEach { root.addView(it) }
        return NestedScrollView(c).apply {
            setBackgroundColor(Color.parseColor(BG))
            addView(root)
        }
    }

    fun title(c: Context, text: String, emoji: String = "\uD83C\uDF08"): TextView =
        TextView(c).apply {
            this.text = "$emoji  $text"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(c, 12))
        }

    fun subtitle(c: Context, text: String): TextView =
        TextView(c).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor(TEXT_DIM))
            setPadding(0, 0, 0, dp(c, 12))
        }

    fun card(c: Context, vararg children: View): LinearLayout {
        val inner = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                setColor(Color.parseColor(BG_CARD))
            }
            setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
        }
        children.forEach { inner.addView(it) }
        return inner.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(c, 12)
        }
    }

    fun section(c: Context, text: String): TextView =
        TextView(c).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor(ACCENT))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(c, 6), 0, dp(c, 6))
        }

    fun row(c: Context, label: String, value: String = ""): LinearLayout {
        val ll = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 8) }
        }
        ll.addView(TextView(c).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (value.isNotBlank()) {
            ll.addView(TextView(c).apply {
                text = value; textSize = 13f; setTextColor(Color.parseColor(ACCENT))
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        return ll
    }

    fun button(c: Context, label: String, color: String = ACCENT, onClick: () -> Unit): LinearLayout {
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 12).toFloat(); setColor(Color.parseColor(color))
            }
            setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 10) }
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(c).apply {
                text = label; textSize = 14f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
        }
    }

    /** Schermata "in arrivo" per feature con logica non ancora cablata. */
    fun comingSoon(c: Context, feature: String, desc: String): LinearLayout {
        val ll = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 28))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat(); setColor(Color.parseColor(BG_CARD))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 12) }
        }
        ll.addView(TextView(c).apply { text = "\uD83C\uDFAE"; textSize = 40f; gravity = Gravity.CENTER })
        ll.addView(TextView(c).apply {
            text = feature; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(c, 8), 0, 0)
        })
        ll.addView(TextView(c).apply {
            text = desc; textSize = 12f; setTextColor(Color.parseColor(TEXT_DIM))
            gravity = Gravity.CENTER; setPadding(dp(c, 8), dp(c, 6), dp(c, 8), 0)
        })
        ll.addView(TextView(c).apply {
            text = "⚙️ In sviluppo"; textSize = 11f; setTextColor(Color.parseColor(ACCENT))
            gravity = Gravity.CENTER; setPadding(0, dp(c, 10), 0, 0)
        })
        return ll
    }
}

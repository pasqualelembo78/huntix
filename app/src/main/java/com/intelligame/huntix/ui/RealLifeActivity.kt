package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit

/**
 * RealLifeActivity — sotto-menu "Real Life".
 * Layer sopra il gioco principale in cui il giocatore interagisce con
 * personaggi reali guidati da LLM (es. Groq / Ollama). Vedi narrazione.txt.
 *
 * NOTA: pagina segnaposto. La logica di conversazione/AI non è implementata.
 */
class RealLifeActivity : BaseNavActivity() {
    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        box.addView(body(c, "Real Life è un mondo vivo sopra il gioco: incontri persone vere e conversi con loro tramite intelligenza artificiale (LLM gratuiti come Groq o Ollama). Vivi la città, socializza e costruisci relazioni che nascono in modo organico dall'esperienza."))
        box.addView(spacer(c))
        box.addView(menuItem(c, "\uD83C\uDFE0  Città", "Esplora luoghi e attività"))
        box.addView(menuItem(c, "\uD83D\uDC64  Personaggi", "Conosci e parla con NPC reali"))
        box.addView(menuItem(c, "\uD83D\uDCAC  Chat AI", "Conversazioni guidate da linguaggio"))

        setContentView(UiKit.scroll(c, UiKit.title(c, "Real Life", "\uD83D\uDC65"), UiKit.section(c, "Sotto-menu"), box))
    }

    private fun body(c: android.content.Context, text: String) = TextView(c).apply {
        this.text = text; textSize = 13f; setTextColor(Color.parseColor("#C9B8E8"))
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun spacer(c: android.content.Context) = android.view.View(c).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
    }

    private fun menuItem(c: android.content.Context, title: String, sub: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1A1030")) }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        isClickable = true; isFocusable = true
        val t = TextView(c).apply { text = title; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        val s = TextView(c).apply {
            text = sub; textSize = 10f; setTextColor(Color.parseColor("#8A7AB0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(10) }
        }
        addView(t); addView(s)
        setOnClickListener { Toast.makeText(c, "Prossimamente", Toast.LENGTH_SHORT).show() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

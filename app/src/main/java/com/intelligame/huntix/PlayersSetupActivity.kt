package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * PlayersSetupActivity — configura i giocatori di una sessione locale/multiplayer.
 * Salva i nomi in SharedPreferences e procede alla selezione modalità.
 */
class PlayersSetupActivity : BaseNavActivity() {

    override fun activeTab() = ""
    private val MAX = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val prefs = getSharedPreferences("players_setup", MODE_PRIVATE)

        val nameBoxes = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun addBox(i: Int, hint: String) {
            nameBoxes.addView(EditText(c).apply {
                this.hint = hint; setHintTextColor(android.graphics.Color.parseColor("#555577"))
                setTextColor(android.graphics.Color.WHITE); textSize = 15f; maxLines = 1
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 10).toFloat()
                    setColor(android.graphics.Color.parseColor(UiKit.BG_CARD))
                    setStroke(1, android.graphics.Color.parseColor("#334466"))
                }
                setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiKit.dp(c, 10) }
                tag = "player_$i"
                setText(prefs.getString("player_$i", ""))
            })
        }
        for (i in 1..MAX) addBox(i, "Giocatore $i")

        val content = UiKit.scroll(c,
            UiKit.title(c, "Giocatori", "🧑‍🤝‍🧑"),
            UiKit.subtitle(c, "Inserisci i nomi dei partecipanti alla sessione (fino a $MAX)."),
            nameBoxes,
            UiKit.button(c, "▶️  Continua", UiKit.ACCENT) {
                val names = (1..MAX).mapNotNull { i ->
                    (nameBoxes.findViewWithTag<EditText>("player_$i"))
                        ?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                }
                if (names.isEmpty()) {
                    Toast.makeText(c, "Inserisci almeno un giocatore", Toast.LENGTH_SHORT).show(); return@button
                }
                prefs.edit().apply {
                    names.forEachIndexed { idx, n -> putString("player_${idx + 1}", n) }
                    apply()
                }
                startActivity(Intent(c, IndoorModeSelectionActivity::class.java))
            }
        )
        setContentView(content)
    }
}

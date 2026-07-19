package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * TutorialActivity — breve onboarding sulle modalità di gioco.
 */
class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val steps = listOf(
            "🏠  INDOOR" to "Nascondi uova in AR nella tua stanza e sfida i tuoi amici a trovarle.",
            "🌍  OUTDOOR" to "Esplora la mappa, cattura uova GPS e completa le palestre vicino a te.",
            "🎮  MINIGIOCHI" to "9 giochi classici + 13 in Realtà Aumentata per guadagnare ricompense.",
            "⚔️  BATTAGLIA" to "Sfida altri giocatori 1v1 in duelli stile Street Fighter.",
            "👥  SOCIAL" to "Amici, chat, squadre e scambi per crescere insieme."
        )

        val children = mutableListOf<android.view.View>(
            UiKit.title(c, "Come si gioca", "📖"),
            UiKit.subtitle(c, "Huntix unisce AR, GPS e social in un'unica caccia alle uova.")
        )
        steps.forEachIndexed { i, (t, d) ->
            children.add(UiKit.card(c,
                TextView(c).apply {
                    text = "${i + 1}. $t"; textSize = 15f; setTextColor(android.graphics.Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = d; textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, 0)
                }
            ))
        }
        children.add(UiKit.button(c, "▶️  Inizia a giocare", UiKit.ACCENT) {
            startActivity(Intent(c, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        })

        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }
}

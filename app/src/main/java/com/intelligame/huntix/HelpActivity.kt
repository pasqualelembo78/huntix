package com.intelligame.huntix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * HelpActivity — FAQ e risoluzione problemi comuni.
 */
class HelpActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val faq = listOf(
            "L'app non trova superfici AR" to "Illumina bene l'ambiente e muovi lentamente il telefono. I piani riflettenti (vetro) possono dare problemi.",
            "ARCore non è installato" to "Aggiorna 'Google Play Services for AR' dal Play Store.",
            "Le uova non appaiono in Outdoor" to "Verifica di aver concesso i permessi di localizzazione e di avere campo GPS.",
            "Le notifiche non arrivano" to "Abilita le notifiche da Impostazioni e controlla che non siano in silenzioso di sistema.",
            "Voglio cancellare il profilo" to "Vai su Profilo → opzioni account → elimina profilo."
        )
        val children = mutableListOf<android.view.View>(UiKit.title(c, "Aiuto", "❓"))
        faq.forEach { (q, a) ->
            children.add(UiKit.card(c,
                android.widget.TextView(c).apply {
                    text = q; textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                android.widget.TextView(c).apply {
                    text = a; textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, 0)
                }
            ))
        }
        children.add(UiKit.button(c, "✉️  Contatta il supporto", UiKit.PURPLE) {
            startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:support@huntix.app")
            })
        })
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }
}

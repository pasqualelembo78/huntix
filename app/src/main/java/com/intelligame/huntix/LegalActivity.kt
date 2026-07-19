package com.intelligame.huntix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * LegalActivity — Informativa Privacy & Termini di servizio.
 */
class LegalActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val content = UiKit.scroll(c,
            UiKit.title(c, "Privacy & Termini", "🔒"),
            UiKit.section(c, "Informativa sul trattamento dei dati"),
            UiKit.card(c,
                paragraph("Huntix raccoglie solo i dati necessari al funzionamento del gioco: " +
                    "nome scelto, progressi, posizione GPS (solo in modalità Outdoor) e, " +
                    "se autorizzi, dati di accesso tramite Google o Facebook."),
                paragraph("I dati sono salvati su Firebase (Google) e mai condivisi con terze parti " +
                    "a scopo commerciale. Puoi richiedere la cancellazione del profilo in ogni momento " +
                    "dalla schermata del profilo."),
                paragraph("L'app è destinata anche a minori: non vengono mostrati annunci con " +
                    "targeting comportamentale e le chat sono moderate automaticamente.")
            ),
            UiKit.section(c, "Termini"),
            UiKit.card(c,
                paragraph("Utilizzando Huntix accetti di non abusare del servizio, non utilizzare " +
                    "software di cheating e rispettare le altre persone nelle modalità multiplayer.")
            ),
            UiKit.button(c, "✉️  Contatta il supporto", UiKit.PURPLE) {
                startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:support@huntix.app")
                })
            }
        )
        setContentView(content)
    }

    private fun paragraph(text: String) = android.widget.TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
        setPadding(0, 0, 0, UiKit.dp(this@LegalActivity, 8))
    }
}

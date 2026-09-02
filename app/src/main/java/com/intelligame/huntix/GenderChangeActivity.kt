package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * GenderChangeActivity — il sesso viene scelto una sola volta in registrazione
 * e per ora NON può essere modificato (read-only). Questa schermata lo mostra
 * come informazione bloccata.
 */
class GenderChangeActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val profile = PlayerProfileManager.myProfile
        val gender = profile?.playerGender.orEmpty()
        val label = when (gender) {
            "female" -> "♀️  Femmina"
            "male" -> "♂️  Maschio"
            else -> "⚧  Non definito"
        }
        val emoji = when (gender) {
            "female" -> "\uD83D\uDC69"
            "male" -> "\uD83D\uDC68"
            else -> "\uD83E\uDDD1"
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Sesso", emoji),
            UiKit.subtitle(c, "Il sesso determina il modello 3D del tuo personaggio."),
            UiKit.section(c, "Il tuo sesso"),
            UiKit.button(c, "$label  ·  scelto in registrazione", UiKit.GREEN) {
                Toast.makeText(c, "Il sesso non è modificabile per ora.", Toast.LENGTH_SHORT).show()
            },
            UiKit.section(c, "Bloccato"),
            UiKit.button(c, "Il sesso è stato scelto all'iscrizione e al momento non può essere cambiato.", UiKit.PURPLE) { }
        )
        setContentView(content)
    }
}

package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * GenderChangeActivity — cambio genere del personaggio.
 * Aggiorna PlayerProfile e persiste su Firestore/locale.
 */
class GenderChangeActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val profile = PlayerProfileManager.myProfile

        val content = UiKit.scroll(c,
            UiKit.title(c, "Cambia Genere", "\uD83E\uDDD1"),
            UiKit.subtitle(c, "Il genere determina il modello 3D del tuo personaggio."),
            UiKit.section(c, "Seleziona"),
            genderOption("♂️  Maschio", "male"),
            genderOption("♀️  Femmina", "female"),
            genderOption("⚧  Non binario", "nonbinary"),
            UiKit.section(c, "Cambi effettuati: ${profile?.genderChangesCount ?: 0}")
        )
        setContentView(content)
    }

    private fun genderOption(label: String, value: String): LinearLayout {
        val c = this
        val current = PlayerProfileManager.myProfile?.playerGender == value
        return UiKit.button(c, if (current) "✓ $label" else label,
            if (current) UiKit.GREEN else UiKit.PURPLE) {
            val profile = PlayerProfileManager.myProfile ?: run {
                Toast.makeText(c, "Profilo non disponibile", Toast.LENGTH_SHORT).show(); return@button
            }
            profile.playerGender = value
            profile.genderChangesCount += 1
            profile.genderChosenAt = System.currentTimeMillis()
            PlayerProfileManager.persistMyProfile {
                Toast.makeText(c, "Genere aggiornato!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

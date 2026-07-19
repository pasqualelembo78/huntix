package com.intelligame.huntix

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.ui.PlayerProfileActivity

/**
 * SettingsActivity — impostazioni dell'app (audio, notifiche, privacy, account).
 */
class SettingsActivity : BaseNavActivity() {

    override fun activeTab() = ""
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        prefs = getSharedPreferences("huntix_settings", MODE_PRIVATE)

        val content = UiKit.scroll(c,
            UiKit.title(c, "Impostazioni", "⚙️"),

            UiKit.section(c, "Audio & Notifiche"),
            toggleRow("🔊  Effetti sonori", "sound", true),
            toggleRow("🎵  Musica", "music", true),
            toggleRow("🔔  Notifiche push", "notifications", true),

            UiKit.section(c, "Account & Privacy"),
            UiKit.button(c, "👤  Profilo", UiKit.PURPLE) {
                startActivity(Intent(c, PlayerProfileActivity::class.java))
            },
            UiKit.button(c, "📖  Tutorial", UiKit.PURPLE) {
                startActivity(Intent(c, TutorialActivity::class.java))
            },
            UiKit.button(c, "🔒  Privacy & Termini", UiKit.PURPLE) {
                startActivity(Intent(c, LegalActivity::class.java))
            },
            UiKit.button(c, "ℹ️  Info & Legale", UiKit.PURPLE) {
                startActivity(Intent(c, InfoLegalActivity::class.java))
            },
            UiKit.button(c, "❓  Aiuto", UiKit.PURPLE) {
                startActivity(Intent(c, HelpActivity::class.java))
            },

            UiKit.section(c, "Sessione"),
            UiKit.button(c, "🚪  Esci (Ospite)", "#C62828") {
                prefs.edit().clear().apply()
                startActivity(Intent(c, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        )
        setContentView(content)
    }

    private fun toggleRow(label: String, key: String, default: Boolean): LinearLayout {
        val c = this
        val ll = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 8) }
        }
        ll.addView(android.widget.TextView(c).apply {
            text = label; textSize = 14f; setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = Switch(c).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, isOn -> prefs.edit().putBoolean(key, isOn).apply() }
        }
        ll.addView(sw)
        return ll
    }
}

package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * IndoorModeSelectionActivity — scegli la modalità Indoor (solo / locale / online).
 */
class IndoorModeSelectionActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val content = UiKit.scroll(c,
            UiKit.title(c, "Indoor AR", "🏠"),
            UiKit.subtitle(c, "Nascondi e cerca uova in Realtà Aumentata."),
            UiKit.button(c, "🎯  Solo (contro il tempo)", UiKit.ACCENT) {
                com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(c, "play_indoor")
                startActivity(Intent(c, MainActivity::class.java))
            },
            UiKit.button(c, "👥  Multigiocatore Locale", UiKit.PURPLE) {
                startActivity(Intent(c, IndoorSetupActivity::class.java).putExtra("mode", "local"))
            },
            UiKit.button(c, "🌐  Multigiocatore Online", UiKit.PURPLE) {
                startActivity(Intent(c, IndoorMultiplayerLobbyActivity::class.java).putExtra("mode", "online"))
            }
        )
        setContentView(content)
    }
}

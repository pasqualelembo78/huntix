package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * MultiplayerLobbyActivity — sala d'attesa prima dell'inizio della caccia.
 */
class MultiplayerLobbyActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val prefs = getSharedPreferences("mp_room", MODE_PRIVATE)
        val roomCode = prefs.getString("room_code", "—") ?: "—"
        val players = prefs.getStringSet("room_players", emptySet()) ?: emptySet()

        val playersBox = androidx.appcompat.widget.LinearLayoutCompat(c).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
        }
        players.forEach { p -> playersBox.addView(UiKit.row(c, "🧑  $p")) }

        val content = UiKit.scroll(c,
            UiKit.title(c, "In attesa…", "⏳"),
            UiKit.card(c, UiKit.row(c, "Stanza", roomCode)),
            UiKit.section(c, "Giocatori (${players.size})"),
            playersBox,
            UiKit.button(c, "▶️  Avvia caccia", "#00FF88") {
                startActivity(Intent(c, MainActivity::class.java)
                    .putExtra("mp_mode", intent.getStringExtra("mode") ?: "local")
                    .putExtra("room_code", roomCode))
            }
        )
        setContentView(content)
    }
}

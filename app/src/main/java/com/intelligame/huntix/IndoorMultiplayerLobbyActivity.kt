package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * IndoorMultiplayerLobbyActivity — crea o unisciti a una stanza locale/online.
 * Lo stato dei giocatori è salvato in locale (MultiplayerManager è ancora uno stub).
 */
class IndoorMultiplayerLobbyActivity : BaseNavActivity() {

    override fun activeTab() = ""
    private var mode = "local"

    override fun onCreate(savedInstanceState: Bundle?) {
        val c = this
        mode = intent.getStringExtra("mode") ?: "local"
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("mp_room", MODE_PRIVATE)
        var roomCode = prefs.getString("room_code", "") ?: ""

        val codeView = TextView(c).apply {
            text = if (roomCode.isBlank()) "Stanza: —" else "Stanza: $roomCode"
            textSize = 16f; setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }

        val playersBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        fun renderPlayers() {
            playersBox.removeAllViews()
            val names = prefs.getStringSet("room_players", emptySet()) ?: emptySet()
            if (names.isEmpty()) playersBox.addView(UiKit.comingSoon(c, "Nessun giocatore", "Crea o unisciti per popolare la stanza."))
            names.forEach { n -> playersBox.addView(UiKit.row(c, "🧑  $n")) }
        }
        renderPlayers()

        val nameInput = EditText(c).apply {
            hint = "Tuo nome"; setHintTextColor(android.graphics.Color.parseColor("#555577"))
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
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Lobby ${if (mode == "online") "Online" else "Locale"}", "👥"),
            codeView,
            UiKit.section(c, "Il tuo nome"),
            nameInput,
            UiKit.button(c, "➕  Crea stanza", UiKit.ACCENT) {
                val me = nameInput.text.toString().trim().ifBlank { "Host" }
                roomCode = (1000..9999).random().toString()
                prefs.edit()
                    .putString("room_code", roomCode)
                    .putStringSet("room_players", linkedSetOf(me))
                    .apply()
                codeView.text = "Stanza: $roomCode"
                renderPlayers()
                Toast.makeText(c, "Stanza creata: $roomCode", Toast.LENGTH_SHORT).show()
            },
            UiKit.button(c, "🚪  Unisciti", UiKit.PURPLE) {
                if (roomCode.isBlank()) { Toast.makeText(c, "Crea prima una stanza", Toast.LENGTH_SHORT).show(); return@button }
                val me = nameInput.text.toString().trim().ifBlank { "Giocatore" }
                val set = (prefs.getStringSet("room_players", emptySet()) ?: emptySet()).toMutableSet()
                set.add(me); prefs.edit().putStringSet("room_players", set).apply()
                renderPlayers()
            },
            UiKit.section(c, "Giocatori"), playersBox,
            UiKit.button(c, "▶️  Inizia partita", "#00FF88") {
                if ((prefs.getStringSet("room_players", emptySet()) ?: emptySet()).isEmpty()) {
                    Toast.makeText(c, "Aggiungi almeno un giocatore", Toast.LENGTH_SHORT).show(); return@button
                }
                startActivity(Intent(c, MainActivity::class.java)
                    .putExtra("mp_mode", mode)
                    .putExtra("room_code", roomCode))
            }
        )
        setContentView(content)
    }
}

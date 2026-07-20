package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.intelligame.huntix.EggSetupModeActivity
import com.intelligame.huntix.GameModeActivity

/**
 * IndoorMultiplayerLobbyActivity — crea o unisciti a una stanza ONLINE (Firebase).
 * Gli altri giocatori compaiono in tempo reale e, una volta avviata la partita,
 * i punteggi si sincronizzano tramite IndoorSessionManager (indoor_rooms).
 */
class IndoorMultiplayerLobbyActivity : BaseNavActivity() {

    override fun activeTab() = ""
    private var mode = "local"
    private var roomCode = ""
    private var isHost = false
    private var playerUid = ""
    private var playerName = "Giocatore"

    private val roomsRef get() = FirebaseDatabase.getInstance().getReference("indoor_rooms")
    private var playersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val c = this
        mode = intent.getStringExtra("mode") ?: "local"
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("mp_room", MODE_PRIVATE)
        val auth = FirebaseAuth.getInstance()
        playerUid = auth.currentUser?.uid
            ?: PlayerProfileManager.myProfile?.playerId
            ?: "p_${System.currentTimeMillis()}"
        playerName = PlayerProfileManager.myProfile?.name?.takeIf { it.isNotBlank() } ?: "Giocatore"
        roomCode = prefs.getString("room_code", "") ?: ""

        val codeView = TextView(c).apply {
            text = if (roomCode.isBlank()) "Stanza: —" else "Stanza: $roomCode"
            textSize = 16f; setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }

        val playersBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        fun renderPlayers(map: Map<String, String>) {
            playersBox.removeAllViews()
            if (map.isEmpty()) {
                playersBox.addView(UiKit.comingSoon(c, "Nessun giocatore", "Crea o unisciti per popolare la stanza."))
            } else {
                map.forEach { (uid, name) ->
                    val you = if (uid == playerUid) "  (tu)" else ""
                    playersBox.addView(UiKit.row(c, "🧑  $name$you"))
                }
            }
        }

        fun attachPlayersListener(code: String) {
            playersListener?.let { roomsRef.child(code).child("players").removeEventListener(it) }
            playersListener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val map = snap.children.mapNotNull { ds ->
                        val name = ds.getValue(String::class.java)
                        val key = ds.key
                        if (name != null && key != null) key to name else null
                    }.toMap()
                    renderPlayers(map)
                }
                override fun onCancelled(e: DatabaseError) {
                    Toast.makeText(c, "Errore stanza: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            roomsRef.child(code).child("players").addValueEventListener(playersListener!!)
        }

        val nameInput = EditText(c).apply {
            hint = "Tuo nome"; setHintTextColor(android.graphics.Color.parseColor("#555577"))
            setTextColor(android.graphics.Color.WHITE); textSize = 15f; maxLines = 1
            setText(playerName)
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

        val codeInput = EditText(c).apply {
            hint = "Codice stanza (per unirsi)"; setHintTextColor(android.graphics.Color.parseColor("#555577"))
            setTextColor(android.graphics.Color.WHITE); textSize = 15f; maxLines = 1
            setText(roomCode)
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

        fun joinRoom(code: String, asHost: Boolean) {
            if (code.length < 3) { Toast.makeText(c, "Codice stanza non valido", Toast.LENGTH_SHORT).show(); return }
            val me = nameInput.text.toString().trim().ifBlank { playerName }
            playerName = me
            roomCode = code; isHost = asHost
            prefs.edit().putString("room_code", roomCode).apply()
            codeView.text = "Stanza: $roomCode"
            roomsRef.child(roomCode).child("players").child(playerUid).setValue(me)
                .addOnSuccessListener { attachPlayersListener(roomCode) }
                .addOnFailureListener { e ->
                    Toast.makeText(c, "Impossibile entrare: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Lobby ${if (mode == "online") "Online" else "Locale"}", "👥"),
            codeView,
            UiKit.section(c, "Il tuo nome"),
            nameInput,
            UiKit.button(c, "➕  Crea stanza online", UiKit.ACCENT) {
                val code = (1000..9999).random().toString()
                joinRoom(code, asHost = true)
                Toast.makeText(c, "Stanza creata: $code", Toast.LENGTH_SHORT).show()
            },
            UiKit.section(c, "Unisciti a una stanza"),
            codeInput,
            UiKit.button(c, "🚪  Unisciti", UiKit.PURPLE) {
                joinRoom(codeInput.text.toString().trim(), asHost = false)
            },
            UiKit.section(c, "Giocatori"),
            playersBox,
            UiKit.button(c, "▶️  Inizia partita", "#00FF88") {
                if (roomCode.isBlank()) {
                    Toast.makeText(c, "Crea o unisciti a una stanza prima", Toast.LENGTH_SHORT).show(); return@button
                }
                startActivity(Intent(c, MainActivity::class.java).apply {
                    putExtra("indoor_mp", true)
                    putExtra("room_code", roomCode)
                    putExtra("room_is_host", isHost)
                    putExtra("current_player", playerName)
                    putExtra("room_uid", playerUid)
                    putExtra(EggSetupModeActivity.EXTRA_SETUP_MODE, "auto")
                    putExtra(EggSetupModeActivity.EXTRA_AUTO_EGG_COUNT, 4)
                    putExtra(EggSetupModeActivity.EXTRA_TRAP_EGG_COUNT, 0)
                    putExtra(EggSetupModeActivity.EXTRA_PENALTY_SECS, 30)
                    putExtra(GameModeActivity.EXTRA_TURN_MODE, "sequential")
                })
            }
        )
        setContentView(content)

        // Se già dentro a una stanza (es. ritorno dalla partita) riattacca il listener
        if (roomCode.isNotBlank()) attachPlayersListener(roomCode)
    }

    override fun onDestroy() {
        playersListener?.let { roomsRef.child(roomCode).child("players").removeEventListener(it) }
        super.onDestroy()
    }
}

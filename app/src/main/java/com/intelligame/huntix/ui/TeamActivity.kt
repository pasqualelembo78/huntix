package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.gamification.TeamManager
import com.intelligame.huntix.gamification.TeamManager.Team
import com.intelligame.huntix.BaseNavActivity

class TeamActivity : BaseNavActivity() {

    private var chatListener: ListenerRegistration? = null
    private var currentTeam: Team? = null
    private lateinit var mainContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(40))
        }
        scroll.addView(mainContainer)
        setContentView(scroll)
        loadTeamState()
    }

    private fun loadTeamState() {
        mainContainer.removeAllViews()
        mainContainer.addView(buildBackBtn())
        mainContainer.addView(titleTv("👥 Squadra", "#E0E0FF"))
        mainContainer.addView(loadingTv("Caricamento..."))

        TeamManager.getMyTeam { team ->
            runOnUiThread {
                mainContainer.removeAllViews()
                mainContainer.addView(buildBackBtn())
                mainContainer.addView(titleTv("👥 Squadra", "#E0E0FF"))
                if (team == null) showNoTeamUI()
                else showTeamUI(team)
            }
        }
    }

    private fun showNoTeamUI() {
        currentTeam = null
        mainContainer.addView(tv("Non sei in nessuna squadra.", 14f, "#9999CC").also { it.setPadding(0, dp(16), 0, dp(24)) })

        // Create team button
        val createBtn = buildColorBtn("➕ Crea Nuova Squadra", "#00FF88")
        createBtn.setOnClickListener { showCreateTeamDialog() }
        mainContainer.addView(createBtn)

        // Search teams
        mainContainer.addView(sectionTv("🔍 Cerca Squadra", "#00B4FF"))
        val searchField = EditText(this).apply {
            hint = "Nome o tag squadra..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1A2A4A"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        mainContainer.addView(searchField)

        val searchBtn = buildColorBtn("🔍 Cerca", "#00B4FF")
        val resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainContainer.addView(searchBtn)
        mainContainer.addView(resultsContainer)

        searchBtn.setOnClickListener {
            val q = searchField.text.toString()
            TeamManager.searchTeams(q) { teams ->
                runOnUiThread {
                    resultsContainer.removeAllViews()
                    if (teams.isEmpty()) {
                        resultsContainer.addView(tv("Nessuna squadra trovata.", 13f, "#888888"))
                    } else {
                        teams.forEach { t -> resultsContainer.addView(buildTeamResultCard(t)) }
                    }
                }
            }
        }
    }

    private fun showTeamUI(team: Team) {
        currentTeam = team
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val isLeader = team.leaderId == myUid

        // Team header card
        val headerCard = CardView(this).apply {
            radius = dp(16).toFloat(); cardElevation = dp(6).toFloat()
            setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(16) }
        }
        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        headerCol.addView(tv("[${team.tag}] ${team.name}", 22f, "#E0E0FF", bold = true))
        headerCol.addView(tv(team.description, 13f, "#9999CC").also { it.setPadding(0, dp(4), 0, dp(8)) })
        headerCol.addView(tv("👑 Leader: ${team.leaderName}", 12f, "#9999CC"))
        headerCol.addView(tv("👥 ${team.memberCount} membri   ⚡ ${team.totalXp} XP   🥚 ${team.totalEggs} uova", 12f, "#9999CC").also { it.setPadding(0, dp(4), 0, 0) })
        headerCard.addView(headerCol)
        mainContainer.addView(headerCard)

        // Chat
        mainContainer.addView(sectionTv("💬 Chat Squadra", "#00B4FF"))
        val chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A1428"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220))
                .also { it.bottomMargin = dp(8) }
        }
        mainContainer.addView(chatContainer)

        // Chat scroll view
        val chatScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        }
        val chatMessages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll.addView(chatMessages)
        chatContainer.addView(chatScroll)

        // Message input
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val msgInput = EditText(this).apply {
            hint = "Scrivi..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1A2A4A"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        }
        val sendBtn = Button(this).apply {
            text = "→"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00B4FF"))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(40)).also { it.marginStart = dp(6) }
        }
        inputRow.addView(msgInput)
        inputRow.addView(sendBtn)
        mainContainer.addView(inputRow)

        val playerName = PlayerProfileManager.myProfile?.name ?: "Giocatore"
        sendBtn.setOnClickListener {
            val text = msgInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            TeamManager.sendMessage(team.teamId, playerName, text, "") { runOnUiThread { msgInput.setText("") } }
        }

        // Listen to chat
        chatListener = TeamManager.listenToChat(team.teamId) { messages ->
            runOnUiThread {
                chatMessages.removeAllViews()
                messages.takeLast(30).forEach { msg ->
                    chatMessages.addView(tv(
                        "[${msg.senderName}] ${msg.text}",
                        12f, if (msg.senderId == myUid) "#90CAF9" else "#9999CC"
                    ).also { it.setPadding(0, dp(2), 0, dp(2)) })
                }
                chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }

        // Leave button
        val leaveBtn = buildColorBtn("🚪 Esci dalla Squadra", "#F44336")
        leaveBtn.setOnClickListener {
            TeamManager.leaveTeam(team.teamId, onSuccess = { runOnUiThread { loadTeamState() } }, onError = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } })
        }
        mainContainer.addView(leaveBtn.also { it.setPadding(0, dp(16), 0, 0) })
    }

    private fun buildTeamResultCard(team: Team): CardView {
        val card = CardView(this).apply {
            radius = dp(10).toFloat(); cardElevation = dp(3).toFloat()
            setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(8) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(tv("[${team.tag}] ${team.name}", 15f, "#E0E0FF", bold = true))
        col.addView(tv("👥 ${team.memberCount}  ⚡ ${team.totalXp} XP", 11f, "#9999CC"))
        row.addView(col)
        val joinBtn = Button(this).apply {
            text = "UNISCITI"; textSize = 11f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00FF88"))
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(36))
            setOnClickListener {
                val playerName = PlayerProfileManager.myProfile?.name ?: "Giocatore"
                TeamManager.joinTeam(team.teamId, playerName,
                    onSuccess = { runOnUiThread { loadTeamState() } },
                    onError = { msg -> runOnUiThread { Toast.makeText(this@TeamActivity, msg, Toast.LENGTH_SHORT).show() } }
                )
            }
        }
        row.addView(joinBtn)
        card.addView(row)
        return card
    }

    private fun showCreateTeamDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        val nameField = EditText(this).apply { hint = "Nome squadra (min 3 caratteri)"; setTextColor(Color.parseColor("#333333")) }
        val tagField = EditText(this).apply { hint = "Tag (2-5 lettere, es. EGGS)"; setTextColor(Color.parseColor("#333333")) }
        val descField = EditText(this).apply { hint = "Descrizione (opzionale)"; setTextColor(Color.parseColor("#333333")) }
        layout.addView(nameField); layout.addView(tagField); layout.addView(descField)
        dialog.setView(layout)
        dialog.setTitle("Crea Squadra")
        dialog.setPositiveButton("Crea") { _, _ ->
            val playerName = PlayerProfileManager.myProfile?.name ?: "Capo"
            TeamManager.createTeam(
                nameField.text.toString(), tagField.text.toString(), descField.text.toString(), playerName,
                onSuccess = { runOnUiThread { loadTeamState() } },
                onError = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
            )
        }
        dialog.setNegativeButton("Annulla", null)
        dialog.show()
    }

    override fun onDestroy() { super.onDestroy(); chatListener?.remove() }

    private fun buildBackBtn() = TextView(this).apply {
        text = "← Torna"; textSize = 14f; setTextColor(Color.parseColor("#666699"))
        setPadding(0, 0, 0, dp(8))
        setOnClickListener { finish() }
    }

    private fun buildColorBtn(text: String, colorHex: String) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor(colorHex))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
            .also { it.bottomMargin = dp(10) }
    }

    private fun titleTv(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 24f; setTextColor(Color.parseColor(colorHex))
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(20))
    }

    private fun sectionTv(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.parseColor(colorHex))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun tv(text: String, size: Float, colorHex: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(colorHex))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private fun loadingTv(text: String) = tv(text, 13f, "#888888").also { it.gravity = Gravity.CENTER }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

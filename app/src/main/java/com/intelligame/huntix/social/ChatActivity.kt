package com.intelligame.huntix.social

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.intelligame.huntix.AgeGateManager
import com.intelligame.huntix.ParentalGateManager
import com.intelligame.huntix.PlayerProfileManager
import java.text.SimpleDateFormat
import java.util.*
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.social.ChatProfanityFilter

class ChatActivity : BaseNavActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var scrollView: ScrollView
    private var chatRef: Query? = null
    private var listener: ChildEventListener? = null
    private val myUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val myName get() = PlayerProfileManager.myProfile?.name ?: "Giocatore"
    private var friendUid: String? = null
    private var friendName: String? = null
    private val tf = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(s: Bundle?) { super.onCreate(s)
        // ✅ FIX v7.2.1: Under 18 bloccati, under 13 bloccati con COPPA
        if (!AgeGateManager.checkAdultAccess(this, "Chat")) { finish(); return }
        friendUid = intent.getStringExtra("friendUid"); friendName = intent.getStringExtra("friendName")
        // ✅ FIX v7.2.1: Parental gate extra per sicurezza (se age gate bypassato)
        ParentalGateManager.requireIfChild(this, "Chat") {
            buildUI(); startListening()
        }
    }
    override fun onDestroy() { super.onDestroy(); listener?.let { chatRef?.removeEventListener(it) } }

    private fun buildUI() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // WhatsApp header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.parseColor("#075E54")); setPadding(dp(8),dp(36),dp(16),dp(10)) }
        header.addView(Button(this).apply { text = "<"; textSize = 20f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { finish() }; layoutParams = LinearLayout.LayoutParams(dp(44),dp(44)) })
        header.addView(TextView(this).apply { text = if (friendName!=null) friendName!!.first().uppercase() else "G"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#128C7E")) }; layoutParams = LinearLayout.LayoutParams(dp(36),dp(36)).also { it.marginStart = dp(4) } })
        val hi = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) } }
        hi.addView(TextView(this).apply { text = friendName ?: "Chat Globale"; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        hi.addView(TextView(this).apply { text = if (friendName!=null) "online" else "tutti i giocatori"; textSize = 12f; setTextColor(Color.parseColor("#A5D6A7")) })
        header.addView(hi); root.addView(header)
        // Messages
        scrollView = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f); setBackgroundColor(Color.parseColor("#ECE5DD")) }
        messagesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)) }
        scrollView.addView(messagesContainer); root.addView(scrollView)
        // Input bar
        val ib = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(dp(6),dp(4),dp(6),dp(4)) }
        inputField = EditText(this).apply { hint = "Messaggio"; setHintTextColor(Color.parseColor("#999999")); setTextColor(Color.BLACK); textSize = 15f; maxLines = 4; background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.WHITE) }; setPadding(dp(16),dp(10),dp(16),dp(10)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        ib.addView(inputField)
        ib.addView(Button(this).apply { text = ">"; textSize = 20f; setTextColor(Color.WHITE); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#25D366")) }; layoutParams = LinearLayout.LayoutParams(dp(48),dp(48)).also { it.marginStart = dp(6) }; setOnClickListener { sendMessage() } })
        root.addView(ib); setContentView(root)
    }

    private fun getChatRef(): DatabaseReference { val db = FirebaseDatabase.getInstance(); return if (friendUid != null) { val key = if (myUid < friendUid!!) myUid + "_" + friendUid else friendUid + "_" + myUid; db.getReference("chat_private").child(key) } else db.getReference("chat_global") }

    private fun startListening() {
        chatRef = getChatRef().limitToLast(100)
        listener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) { val name = snap.child("name").getValue(String::class.java) ?: "?"; val text = snap.child("text").getValue(String::class.java) ?: ""; val uid = snap.child("uid").getValue(String::class.java) ?: ""; val ts = snap.child("ts").getValue(Long::class.java) ?: 0L; runOnUiThread { addBubble(name, text, uid == myUid, ts, uid) } }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}; override fun onChildRemoved(s: DataSnapshot) {}; override fun onChildMoved(s: DataSnapshot, p: String?) {}; override fun onCancelled(e: DatabaseError) {}
        }
        chatRef?.addChildEventListener(listener!!)
    }

    private fun sendMessage() {
        // ✅ FIX v7.2.1: Filtro contenuti automatico (Play Store compliance)
        val rawText = inputField.text.toString()
        val filterResult = ChatProfanityFilter.check(rawText)
        if (filterResult.isBlocked) {
            android.widget.Toast.makeText(this, filterResult.reason, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val raw = inputField.text.toString().trim()
        if (raw.isBlank() || myUid.isBlank()) return
        // ✅ FIX v7.2: Filtra messaggi con ChatModerationManager (COPPA + Play Store UGC)
        val text = ChatModerationManager.filterMessage(raw)
        // ✅ FIX v7.2: Usa "ts" per allineamento con Firebase Security Rules
        getChatRef().push().setValue(mapOf("uid" to myUid, "name" to myName, "text" to text, "ts" to ServerValue.TIMESTAMP))
        inputField.text.clear()
    }

    private fun addBubble(name: String, text: String, isMine: Boolean, ts: Long, senderId: String = "") {
        // ✅ FIX v7.2: Non mostrare messaggi da utenti bloccati
        if (senderId.isNotBlank() && isBlocked(senderId)) return
        val bubble = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor(if (isMine) "#DCF8C6" else "#FFFFFF")) }; setPadding(dp(10),dp(6),dp(10),dp(4)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = if (isMine) Gravity.END else Gravity.START; it.bottomMargin = dp(4); it.marginStart = if (isMine) dp(60) else 0; it.marginEnd = if (isMine) 0 else dp(60) } }
        if (!isMine) bubble.addView(TextView(this).apply { this.text = name; textSize = 12f; setTextColor(Color.parseColor("#075E54")); typeface = Typeface.DEFAULT_BOLD })
        bubble.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.parseColor("#303030")) })
        val timeStr = if (ts > 0) tf.format(Date(ts)) else ""
        bubble.addView(TextView(this).apply { this.text = timeStr + if (isMine) " vv" else ""; textSize = 10f; setTextColor(Color.parseColor(if (isMine) "#7CB342" else "#999999")); gravity = Gravity.END })
        // ✅ FIX v7.2: Long-press per Report/Block (Play Store UGC requirement)
        if (!isMine && senderId.isNotBlank()) {
            bubble.setOnLongClickListener {
                showMessageOptions(senderId, name, text)
                true
            }
        }
        messagesContainer.addView(bubble); scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    // ── REPORT / BLOCK (Play Store UGC requirement) ─────────────

    private fun showMessageOptions(senderId: String, senderName: String, messageText: String) {
        val items = arrayOf("\uD83D\uDEA9 Segnala messaggio", "\uD83D\uDEAB Blocca utente")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opzioni")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> reportMessage(senderId, senderName, messageText)
                    1 -> blockUser(senderId, senderName)
                }
            }.show()
    }

    private fun reportMessage(senderId: String, senderName: String, messageText: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val report = mapOf(
            "reporterId" to uid,
            "reportedUserId" to senderId,
            "reportedUserName" to senderName,
            "messageText" to messageText,
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending"
        )
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reports").add(report)
        android.widget.Toast.makeText(this, "\uD83D\uDEA9 Messaggio segnalato. Grazie!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun blockUser(userId: String, userName: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("blocked_users", 0)
        val blocked = prefs.getStringSet("blocked", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        blocked.add(userId)
        prefs.edit().putStringSet("blocked", blocked).apply()
        android.widget.Toast.makeText(this, "\uD83D\uDEAB $userName bloccato", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun isBlocked(userId: String): Boolean {
        val prefs = getSharedPreferences("blocked_users", 0)
        return prefs.getStringSet("blocked", emptySet())?.contains(userId) == true
    }

}

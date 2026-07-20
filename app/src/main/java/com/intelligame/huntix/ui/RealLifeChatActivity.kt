package com.intelligame.huntix.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.RealLifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RealLifeChatActivity — schermata chat (Fase A).
 * Invia messaggi al backend (POST /chat) e mostra le risposte dello NPC.
 * La cronologia è mantenuta in memoria per la sessione.
 */
class RealLifeChatActivity : AppCompatActivity() {
    private lateinit var charId: String
    private lateinit var charName: String
    private lateinit var charAvatar: String

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: LinearLayout
    private lateinit var statusText: TextView

    private val messages = mutableListOf<Pair<Boolean, String>>() // true = utente
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        charId = intent.getStringExtra("CHAR_ID") ?: ""
        charName = intent.getStringExtra("CHAR_NAME") ?: "Personaggio"
        charAvatar = intent.getStringExtra("CHAR_AVATAR") ?: "🙂"

        val c = this
        messagesContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 12), UiKit.dp(c, 12), UiKit.dp(c, 12))
        }
        scrollView = ScrollView(c).apply {
            setBackgroundColor(Color.parseColor(UiKit.BG))
            addView(messagesContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        input = EditText(c).apply {
            hint = "Scrivi un messaggio…"
            inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor(UiKit.TEXT_DIM))
            background = null
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sendBtn = UiKit.button(c, "➤", UiKit.ACCENT) { sendMessage() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                UiKit.dp(c, 52), UiKit.dp(c, 44)
            ).apply { leftMargin = UiKit.dp(c, 8) }
        }
        val inputBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 10), UiKit.dp(c, 8), UiKit.dp(c, 10), UiKit.dp(c, 8))
            addView(input)
            addView(sendBtn)
        }

        statusText = TextView(c).apply {
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 2), UiKit.dp(c, 12), UiKit.dp(c, 4))
        }

        // ── Top bar ──────────────────────────────────────────
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 10), UiKit.dp(c, 12), UiKit.dp(c, 10))
        }
        topBar.addView(TextView(c).apply {
            text = "←"
            textSize = 22f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, UiKit.dp(c, 10), 0)
            isClickable = true
            setOnClickListener { finish() }
        })
        topBar.addView(TextView(c).apply {
            text = "$charAvatar  $charName"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(topBar)
        root.addView(scrollView)
        root.addView(statusText)
        root.addView(inputBar)

        window.statusBarColor = Color.parseColor("#0A0618")
        setContentView(root)

        addAiMessage("Ciao, sono $charName! Come posso aiutarti oggi?")
    }

    private fun sendMessage() {
        if (busy) return
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.setText("")
        addUserMessage(text)
        busy = true
        setStatus("… sta scrivendo")
        sendBtn.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RealLifeClient.sendMessage(this@RealLifeChatActivity, charId, text, "Utente")
            }
            busy = false
            sendBtn.isEnabled = true
            setStatus("")
            result.onSuccess { resp ->
                val txt = resp.response.ifBlank { "(nessuna risposta)" }
                addAiMessage(txt)
            }.onFailure {
                addAiMessage("⚠️ ${it.message}")
            }
        }
    }

    private fun setStatus(s: String) {
        statusText.text = s
        statusText.visibility = if (s.isBlank()) View.GONE else View.VISIBLE
    }

    private fun addUserMessage(text: String) {
        messages.add(true to text)
        renderBubble(text, true)
    }

    private fun addAiMessage(text: String) {
        messages.add(false to text)
        renderBubble(text, false)
    }

    private fun renderBubble(text: String, isUser: Boolean) {
        val c = this
        val bubble = TextView(c).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (isUser) Color.WHITE else Color.parseColor("#E8DEFF"))
            val pad = UiKit.dp(c, 12)
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 14).toFloat()
                setColor(Color.parseColor(if (isUser) UiKit.ACCENT else UiKit.BG_CARD))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                bottomMargin = UiKit.dp(c, 8)
                topMargin = UiKit.dp(c, 2)
                if (isUser) leftMargin = UiKit.dp(c, 48) else rightMargin = UiKit.dp(c, 48)
            }
        }
        runOnUiThread {
            messagesContainer.addView(bubble)
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}

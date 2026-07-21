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
import com.intelligame.huntix.reallife.InteractResponse
import com.intelligame.huntix.reallife.Needs
import com.intelligame.huntix.reallife.RealLifeAuth
import com.intelligame.huntix.reallife.RealLifeClient
import com.intelligame.huntix.reallife.Skill
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
    private lateinit var needsContainer: LinearLayout
    private lateinit var skillsContainer: LinearLayout

    private var charTags: List<String> = emptyList()
    private var userId: String = ""

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

        // ── Fase B: bisogni (barre) + skill (chip) ──
        needsContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 8), UiKit.dp(c, 12), UiKit.dp(c, 8))
        }
        skillsContainer = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 6), UiKit.dp(c, 12), UiKit.dp(c, 6))
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
        root.addView(needsContainer)
        root.addView(skillsContainer)
        root.addView(scrollView)
        root.addView(statusText)
        root.addView(inputBar)

        window.statusBarColor = Color.parseColor("#0A0618")
        setContentView(root)

        charTags = intent.getStringArrayListExtra("CHAR_TAGS") ?: emptyList()
        userId = RealLifeAuth.getUsername(this)

        loadNeedsAndSkills()
        addAiMessage("Ciao, sono $charName! Come posso aiutarti oggi?")
    }

    private fun loadNeedsAndSkills() {
        if (userId.isBlank()) return
        lifecycleScope.launch {
            val needs = withContext(Dispatchers.IO) { RealLifeClient.getNeeds(charId, userId) }.getOrNull()
            needs?.let { renderNeeds(it) }
            val skills = withContext(Dispatchers.IO) { RealLifeClient.getSkills(userId) }.getOrNull()
            skills?.let { renderSkills(it.skills) }
        }
    }

    private fun renderNeeds(needs: Needs) {
        val items = listOf(
            "🍔 Fame" to needs.hunger.toFloat(),
            "😴 Sonno" to needs.sleep.toFloat(),
            "🚿 Igiene" to needs.hygiene.toFloat(),
            "💬 Socialità" to needs.social.toFloat(),
            "🎉 Divertimento" to needs.funLevel.toFloat()
        )
        val colors = listOf("#FF8A3D", "#4FA3FF", "#3DE0E0", "#3DFF8A", "#B96BFF")
        runOnUiThread {
            needsContainer.removeAllViews()
            items.forEachIndexed { i, (label, value) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiKit.dp(this@RealLifeChatActivity, 4) }
                }
                val lab = TextView(this).apply {
                    text = label; textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@RealLifeChatActivity, 96), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val track = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = UiKit.dp(this@RealLifeChatActivity, 6).toFloat()
                        setColor(Color.parseColor("#221838"))
                    }
                    layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@RealLifeChatActivity, 12), 1f)
                }
                val fill = View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = UiKit.dp(this@RealLifeChatActivity, 6).toFloat()
                        setColor(Color.parseColor(colors[i]))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        (UiKit.dp(this@RealLifeChatActivity, 200) * (value / 100f)).toInt().coerceAtLeast(2),
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                }
                track.addView(fill)
                val valTxt = TextView(this).apply {
                    text = "${value.toInt()}"; textSize = 11f; setTextColor(Color.WHITE)
                    setPadding(UiKit.dp(this@RealLifeChatActivity, 6), 0, 0, 0)
                }
                row.addView(lab); row.addView(track); row.addView(valTxt)
                needsContainer.addView(row)
            }
        }
    }

    private fun renderSkills(skills: List<Skill>) {
        runOnUiThread {
            skillsContainer.removeAllViews()
            skillsContainer.addView(TextView(this).apply {
                text = "⚡ "; textSize = 14f
            })
            skills.forEach { sk ->
                val lvl = if (sk.level > 0) " L${sk.level}" else ""
                val chip = TextView(this).apply {
                    text = "${sk.name}$lvl"
                    textSize = 11f
                    setTextColor(if (sk.level > 0) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = UiKit.dp(this@RealLifeChatActivity, 12).toFloat()
                        setColor(Color.parseColor(if (sk.level > 0) "#2A1B4A" else "#161029"))
                        setStroke(1, Color.parseColor(if (sk.level > 0) "#5A3FA0" else "#2A2240"))
                    }
                    setPadding(UiKit.dp(this@RealLifeChatActivity, 10), UiKit.dp(this@RealLifeChatActivity, 5),
                        UiKit.dp(this@RealLifeChatActivity, 10), UiKit.dp(this@RealLifeChatActivity, 5))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = UiKit.dp(this@RealLifeChatActivity, 6) }
                }
                skillsContainer.addView(chip)
            }
        }
    }

    private fun onInteractDone(resp: InteractResponse?) {
        resp?.needs?.let { renderNeeds(it) }
        resp?.skills?.skills?.let { renderSkills(it) }
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
                // Fase B: ricarica bisogni + XP skill dopo la chat
                if (userId.isNotBlank()) {
                    val inter = withContext(Dispatchers.IO) {
                        RealLifeClient.interact(charId, userId, charTags)
                    }.getOrNull()
                    onInteractDone(inter)
                }
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

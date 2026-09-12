@file:Suppress("DEPRECATION")

package com.intelligame.huntix.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.RealLifeAuth
import com.intelligame.huntix.reallife.RealLifeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * PlayerChatActivity — chat tra giocatori nella citt\u00e0 (relay P2P via backend).
 * Aperta dal tap su un RemotePlayer (Unity ->"> PlayerProfileRequest -> Bridge ->
 * qui). Mostra il profilo (nome + livello + skin) dell'altro giocatore e una
 * chat in stile messaggistica, con il testo inoltrato dal server (il server
 * \u00e8 il relay: da qui nasce il pu\u00f2-vedersi-e-parlarsi tra due telefoni).
 *
 * Endpoint:
 *   POST /api/city/chat/send   { token, to_user, text }
 *   GET  /api/city/chat/inbox  ?token=
 */
class PlayerChatActivity : AppCompatActivity() {
    private lateinit var toUserId: String
    private lateinit var otherName: String
    private var otherLevel: Int = 1
    private lateinit var otherSkin: String

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: LinearLayout

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toUserId = intent.getStringExtra("TO_USER_ID") ?: ""
        otherName = intent.getStringExtra("NAME") ?: "Giocatore"
        otherLevel = intent.getIntExtra("LEVEL", 1)
        otherSkin = intent.getStringExtra("SKIN") ?: "humanMaleA"

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
            hint = "Scrivi a $otherName…"
            inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor(UiKit.TEXT_DIM))
            background = null
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sendBtn = UiKit.button(c, "\u27a4", UiKit.ACCENT) { sendMessage() }.apply {
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

        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 10), UiKit.dp(c, 12), UiKit.dp(c, 10))
        }
        topBar.addView(TextView(c).apply {
            text = "\u2190"
            textSize = 22f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, UiKit.dp(c, 10), 0)
            isClickable = true
            setOnClickListener { finish() }
        })
        // Profilo: nome + livello + skin (avatar)
        topBar.addView(TextView(c).apply {
            text = "\uD83D\uDCB9  $otherName  \u00b7  Lv.$otherLevel"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        topBar.addView(TextView(c).apply {
            text = "  " + otherSkin
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
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
        root.addView(inputBar)
        window.statusBarColor = Color.parseColor("#0A0618")
        setContentView(root)

        pollInbox()
    }

    private fun sendMessage() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || toUserId.isEmpty()) return
        val token = RealLifeAuth.getAccessToken(this)
        if (token.isEmpty()) {
            addLine(false, "Autenticazione mancante: fai login.")
            return
        }
        input.text?.clear()
        addLine(true, text) // ottimistica
        lifecycleScope.launch {
            val ok = postSend(token, text)
            if (!ok) addLine(false, "Invio non riuscito. Riprova.")
        }
    }

    private suspend fun postSend(token: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("token" to token, "to_user" to toUserId, "text" to text))
        val req = Request.Builder()
            .url("${RealLifeConfig.BASE_URL}/api/city/chat/send")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful && (resp.body?.string()?.contains("\"ok\":true") == true)
            }
        }.getOrDefault(false)
    }

    private fun pollInbox() {
        val token = RealLifeAuth.getAccessToken(this)
        if (token.isEmpty() || toUserId.isEmpty()) return
        lifecycleScope.launch {
            while (isActive) {
                val msgs = fetchInbox(token)
                for (m in msgs) addLine(false, m) // messaggi dell'altro giocatore
                delay(2200)
            }
        }
    }

    private suspend fun fetchInbox(token: String): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${RealLifeConfig.BASE_URL}/api/city/chat/inbox?token=$token")
            .get()
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val text = resp.body?.string()
                val j = org.json.JSONObject(text)
                val arr = j.optJSONArray("messages") ?: return@use emptyList()
                (0 until arr.length()).map { i ->
                    val m = arr.optJSONObject(i)
                    m?.optString("text", "") ?: ""
                }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    // ── UI bubble ─────────────────────────────────────────────
    private fun addLine(isMine: Boolean, text: String) {
        if (text.isEmpty()) return
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(UiKit.dp(this@PlayerChatActivity, 10),
                       UiKit.dp(this@PlayerChatActivity, 8),
                       UiKit.dp(this@PlayerChatActivity, 10),
                       UiKit.dp(this@PlayerChatActivity, 8))
            setBackgroundColor(if (isMine)
                Color.parseColor(UiKit.ACCENT) else Color.parseColor("#26203A"))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isMine) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(this@PlayerChatActivity, 6) }
            addView(bubble, LinearLayout.LayoutParams(
                (UiKit.dp(this@PlayerChatActivity, 260)),
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        runOnUiThread {
            messagesContainer.addView(row)
            scrollView.post { scrollView.fullScroll(ViewGroup.FOCUS_DOWN) }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
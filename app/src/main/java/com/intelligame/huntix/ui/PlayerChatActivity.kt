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
import android.content.ClipboardManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.bridge.WorldPosCloud
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
        val locBtn = UiKit.button(c, "\uD83D\uDCCD", "#3A2E66") { shareLocation() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                UiKit.dp(c, 44), UiKit.dp(c, 44)
            ).apply { rightMargin = UiKit.dp(c, 8) }
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
            addView(locBtn)
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

        loadHistory()
    }

    /** Carica la conversazione precedente (anche i messaggi gia' letti) e
     *  solo dopo avvia il polling dei nuovi. Svuota una volta la inbox cosi'
     *  i messaggi gia' mostrati dalla storia non vengono duplicati. */
    private fun loadHistory() {
        lifecycleScope.launch {
            val items = fetchHistory()
            for ((mine, text) in items) addLine(mine, text) // addLine è thread-safe
            if (items.isNotEmpty()) addSeparator("— conversazione precedente —")
            drainInbox()
            pollInbox()
        }
    }

    private suspend fun fetchHistory(): List<Pair<Boolean, String>> {
        val token = RealLifeAuth.getAccessToken(this)
        if (token.isEmpty() || toUserId.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${RealLifeConfig.BASE_URL}/api/city/chat/history?token=$token&with_id=$toUserId")
                .get()
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val j = org.json.JSONObject(resp.body?.string().orEmpty())
                    val arr = j.optJSONArray("messages") ?: return@use emptyList()
                    (0 until arr.length()).map { i ->
                        val m = arr.optJSONObject(i)
                        val mine = m?.optBoolean("mine", false) ?: false
                        val t = m?.optString("text", "") ?: ""
                        Pair(mine, t)
                    }.filter { it.second.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }
    }

    /** Consuma la inbox una sola volta (pull-and-clear) senza mostrarla:
     *  evita la duplicazione con la storia appena caricata. */
    private suspend fun drainInbox() {
        val token = RealLifeAuth.getAccessToken(this)
        if (token.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${RealLifeConfig.BASE_URL}/api/city/chat/inbox?token=$token")
                    .get()
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    private fun addSeparator(label: String) {
        val tv = TextView(this).apply {
            setText(label)
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(this@PlayerChatActivity, 10), 0, UiKit.dp(this@PlayerChatActivity, 4))
        }
        runOnUiThread {
            messagesContainer.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            scrollView.post { scrollView.fullScroll(ViewGroup.FOCUS_DOWN) }
        }
    }

    private fun sendMessage() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || toUserId.isEmpty()) return
        input.text?.clear()
        postText(text)
    }

    /** Condivisione posizione: invia le coordinate del mio ultimo punto in
     *  citta' (arrivate da Unity via "PlayerPosition", vedi WorldPosCloud). */
    private fun shareLocation() {
        if (toUserId.isEmpty()) return
        if (RealLifeAuth.getAccessToken(this).isEmpty()) {
            addLine(false, "Autenticazione mancante: fai login.")
            return
        }
        val pos = WorldPosCloud.lastPosition(this)
        if (pos == null) {
            addLine(false, "Posizione non disponibile: entra prima in citt\u00e0.")
            return
        }
        postText(String.format(
            java.util.Locale.ITALY, "\uD83D\uDCCD %.6f,%.6f", pos.first, pos.second))
    }

    private fun postText(text: String) {
        if (text.isEmpty()) return
        val token = RealLifeAuth.getAccessToken(this)
        if (token.isEmpty()) {
            addLine(false, "Autenticazione mancante: fai login.")
            return
        }
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
        val chip = if (!isMine && text.startsWith("\uD83D\uDCCD"))
            TextView(this@PlayerChatActivity).apply {
                setText("\uD83D\uDCCF Copia")
                textSize = 11f
                setTextColor(Color.parseColor(UiKit.ACCENT))
                setPadding(UiKit.dp(this@PlayerChatActivity, 8),
                    UiKit.dp(this@PlayerChatActivity, 4),
                    UiKit.dp(this@PlayerChatActivity, 8),
                    UiKit.dp(this@PlayerChatActivity, 4))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { copyLocation(text) }
            } else null
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(this@PlayerChatActivity, 6) }
            addView(bubble, LinearLayout.LayoutParams(
                (UiKit.dp(this@PlayerChatActivity, 250)),
                ViewGroup.LayoutParams.WRAP_CONTENT))
            if (chip != null) addView(chip)
        }
        runOnUiThread {
            messagesContainer.addView(row)
            scrollView.post { scrollView.fullScroll(ViewGroup.FOCUS_DOWN) }
        }
    }

    /** Copia la coppia lat,lon contenuta in un messaggio 📌 ricevuto. */
    private fun copyLocation(text: String) {
        val nums = Regex("(-?\\d+\\.\\d+)").findAll(text).map { it.value }.toList()
        if (nums.size < 2) return
        val out = "Lat: ${nums[0]}, Lon: ${nums[1]}"
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("coordinate", out))
            addLine(false, out + " \u2713") // conferma nella chat
        } catch (_: Exception) {}
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
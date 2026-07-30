package com.intelligame.huntix.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.BuildingDefs
import com.intelligame.huntix.reallife.BuildingType
import com.intelligame.huntix.reallife.LocalNeeds
import com.intelligame.huntix.reallife.RealLifeAuth
import com.intelligame.huntix.reallife.RealLifeClient
import com.intelligame.huntix.reallife.VenueCharacterResponse
import com.intelligame.huntix.reallife.OrderItem
import com.intelligame.huntix.reallife.BalanceResponse
import com.intelligame.huntix.reallife.WorkResponse
import com.intelligame.huntix.reallife.SkillsResponse
import com.intelligame.huntix.reallife.UserSkillsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuildingInteriorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BUILDING_TYPE = "building_type"
        const val EXTRA_POI_NAME = "poi_name"
        const val EXTRA_POI_URL = "poi_url"
        const val EXTRA_VENUE_ID = "venue_id"
        const val EXTRA_VENUE_LAT = "venue_lat"
        const val EXTRA_VENUE_LNG = "venue_lng"
        const val EXTRA_BUILDING_TYPE_STR = "building_type_str"

        fun darken(color: Int, factor: Float): Int {
            val r = (Color.red(color) * factor).toInt()
            val g = (Color.green(color) * factor).toInt()
            val b = (Color.blue(color) * factor).toInt()
            return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
    }

    private lateinit var building: com.intelligame.huntix.reallife.BuildingDef
    private lateinit var needs: MutableMap<String, Float>
    private val needsLabels = mapOf(
        "hunger" to Pair("Fame", "\uD83C\uDF5C"),
        "sleep" to Pair("Sonno", "\uD83D\uDCA4"),
        "hygiene" to Pair("Igiene", "\uD83E\uDDF4"),
        "fun" to Pair("Divertimento", "\uD83C\uDFAE"),
        "thirst" to Pair("Sete", "\uD83E\uDDC3")
    )
    private val needsBars = mutableMapOf<String, ProgressBar>()
    private val needsTexts = mutableMapOf<String, TextView>()

    // Chat state
    private var venueCharacter: VenueCharacterResponse? = null
    private var venueId: String = ""
    private var venueLat: Double = 0.0
    private var venueLng: Double = 0.0
    private var buildingTypeStr: String = ""
    private var chatExpanded = false
    private val messages = mutableListOf<Pair<Boolean, String>>()
    private var chatBusy = false
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var chatHeader: LinearLayout
    private lateinit var chatInputBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeOrdinal = intent.getIntExtra(EXTRA_BUILDING_TYPE, 0)
        val safeIdx = typeOrdinal.coerceIn(0, BuildingDefs.BUILDINGS.lastIndex)
        building = BuildingDefs.BUILDINGS[safeIdx]
        val poiName = intent.getStringExtra(EXTRA_POI_NAME)
        needs = LocalNeeds.load(this).toMutableMap()

        venueId = intent.getStringExtra(EXTRA_VENUE_ID) ?: ""
        venueLat = intent.getDoubleExtra(EXTRA_VENUE_LAT, 0.0)
        venueLng = intent.getDoubleExtra(EXTRA_VENUE_LNG, 0.0)
        buildingTypeStr = intent.getStringExtra(EXTRA_BUILDING_TYPE_STR) ?: building.type.name

        val bgColor = darken(building.color3D, 0.15f)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // ── Top bar ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 12),
                UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 12))
            setBackgroundColor(darken(bgColor, 0.3f))
        }
        topBar.addView(TextView(this).apply {
            text = "← "; textSize = 20f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; contentDescription = "Indietro"; setOnClickListener { finish() }
            minimumWidth = UiKit.dp(this@BuildingInteriorActivity, 48)
            minimumHeight = UiKit.dp(this@BuildingInteriorActivity, 48)
            gravity = Gravity.CENTER
        })
        topBar.addView(TextView(this).apply {
            text = "${building.emoji}  ${poiName ?: building.name}"
            textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(topBar)

        // ── Interior scene (custom View) ──
        val scene = InteriorSceneView(this, building)
        root.addView(scene, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ── Actions ──
        val actionsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 10),
                UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 6))
        }
        actionsCard.addView(TextView(this).apply {
            text = "Azioni"; textSize = 13f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, UiKit.dp(this@BuildingInteriorActivity, 6))
        })

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (action in building.actions) {
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@BuildingInteriorActivity, 10).toFloat()
                    setColor(0x33FFFFFF)
                    setStroke(UiKit.dp(this@BuildingInteriorActivity, 1), 0x44FFFFFF)
                }
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@BuildingInteriorActivity, 80), 1f).apply {
                    marginStart = UiKit.dp(this@BuildingInteriorActivity, 4)
                    marginEnd = UiKit.dp(this@BuildingInteriorActivity, 4)
                }
                isClickable = true; isFocusable = true
                setOnClickListener { performAction(action) }
            }
            btn.addView(TextView(this).apply {
                text = action.emoji; textSize = 28f; gravity = Gravity.CENTER
            })
            btn.addView(TextView(this).apply {
                text = action.label; textSize = 10f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1; setPadding(0, UiKit.dp(this@BuildingInteriorActivity, 2), 0, 0)
            })
            actionsRow.addView(btn)
        }
        actionsCard.addView(actionsRow)
        root.addView(actionsCard)

        // ── Web link button (if POI has URL) ──
        val poiUrl = intent.getStringExtra(EXTRA_POI_URL)
        if (!poiUrl.isNullOrBlank()) {
            val linkBtn = Button(this).apply {
                text = "\uD83C\uDF10  Apri Sito"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(0x3300BCD4.toInt())
                setPadding(
                    UiKit.dp(this@BuildingInteriorActivity, 14),
                    UiKit.dp(this@BuildingInteriorActivity, 10),
                    UiKit.dp(this@BuildingInteriorActivity, 14),
                    UiKit.dp(this@BuildingInteriorActivity, 10)
                )
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(poiUrl)))
                }
            }
            root.addView(linkBtn)
        }

        // ── Needs bars ──
        val needsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 6),
                UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 14))
        }
        for ((key, pair) in needsLabels) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiKit.dp(this@BuildingInteriorActivity, 4) }
            }
            row.addView(TextView(this).apply {
                text = "${pair.second} ${pair.first}"; textSize = 11f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val pct = TextView(this).apply {
                textSize = 11f; setTextColor(Color.parseColor(UiKit.ACCENT))
                text = "${needs[key]?.toInt() ?: 0}%"
            }
            needsTexts[key] = pct
            row.addView(pct)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = needs[key]?.toInt() ?: 0
                layoutParams = LinearLayout.LayoutParams(
                    UiKit.dp(this@BuildingInteriorActivity, 100),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = UiKit.dp(this@BuildingInteriorActivity, 6) }
                progressDrawable = LayerDrawable(arrayOf(
                    ColorDrawable(0x33FFFFFF),
                    ClipDrawable(ColorDrawable(needsColor(key)), Gravity.START, ClipDrawable.HORIZONTAL)
                )).apply {
                    setId(0, android.R.id.background)
                    setId(1, android.R.id.progress)
                }
            }
            needsBars[key] = bar
            row.addView(bar)
            needsCard.addView(row)
        }
        root.addView(needsCard)

        // ── Chat panel (expandable) ──
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(darken(bgColor, 0.35f))
        }

        chatHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 10),
                UiKit.dp(this@BuildingInteriorActivity, 14),
                UiKit.dp(this@BuildingInteriorActivity, 10))
            isClickable = true
            setOnClickListener { toggleChat() }
        }
        chatHeader.addView(TextView(this).apply {
            id = View.generateViewId()
            text = "\uD83D\uDC4B  Caricamento personaggio..."
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        chatHeader.addView(TextView(this).apply {
            text = "\u25BC"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
        })
        chatContainer.addView(chatHeader)

        // ── Price list card ──
        val priceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 12),
                UiKit.dp(this@BuildingInteriorActivity, 8),
                UiKit.dp(this@BuildingInteriorActivity, 12),
                UiKit.dp(this@BuildingInteriorActivity, 8))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@BuildingInteriorActivity, 8).toFloat()
                setColor(0x22FFD54F.toInt())
                setStroke(1, 0x44FFD54F.toInt())
            }
        }
        priceCard.addView(TextView(this).apply {
            text = "💰  Listino: ${orderCostForType(buildingTypeStr)} MVC"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD54F"))
            typeface = Typeface.DEFAULT_BOLD
        })
        priceCard.addView(TextView(this).apply {
            text = "✍️  /ordina [prodotti] — es: /ordina margherita, acqua"
            textSize = 10f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(this@BuildingInteriorActivity, 2), 0, 0)
        })
        chatContainer.addView(priceCard)

        // Chat messages area (hidden initially)
        chatMessagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 12), 0,
                UiKit.dp(this@BuildingInteriorActivity, 12), 0)
        }
        chatScrollView = ScrollView(this).apply {
            addView(chatMessagesContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(this@BuildingInteriorActivity, 160)
            )
            visibility = View.GONE
        }
        chatContainer.addView(chatScrollView)

        // Chat input bar (hidden initially)
        chatInputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 10),
                UiKit.dp(this@BuildingInteriorActivity, 6),
                UiKit.dp(this@BuildingInteriorActivity, 10),
                UiKit.dp(this@BuildingInteriorActivity, 6))
            visibility = View.GONE
        }
        chatInput = EditText(this).apply {
            hint = "Chiedi qualcosa..."
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor(UiKit.TEXT_DIM))
            background = null
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        chatInputBar.addView(chatInput)
        chatInputBar.addView(UiKit.button(this, "\u2191", UiKit.ACCENT) { sendChatMessage() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                UiKit.dp(this@BuildingInteriorActivity, 44),
                UiKit.dp(this@BuildingInteriorActivity, 40)
            ).apply { leftMargin = UiKit.dp(this@BuildingInteriorActivity, 6) }
        })
        chatContainer.addView(chatInputBar)

        root.addView(chatContainer)

        setContentView(root)

        // Fetch venue character
        if (venueId.isNotBlank()) {
            loadVenueCharacter()
        }
    }

    private fun loadVenueCharacter() {
        lifecycleScope.launch {
            val char = withContext(Dispatchers.IO) {
                RealLifeClient.getVenueCharacter(
                    venueId = venueId,
                    venueName = intent.getStringExtra(EXTRA_POI_NAME) ?: building.name,
                    buildingType = buildingTypeStr,
                    lat = venueLat,
                    lng = venueLng,
                )
            }.getOrNull()
            venueCharacter = char
            val name = char?.name ?: building.name
            val avatar = char?.avatar ?: "\uD83D\uDC64"
            updateChatHeader("\uD83D\uDC4B  $avatar $name")
        }
    }

    private fun updateChatHeader(text: String) {
        val tv = chatHeader.getChildAt(0) as? TextView ?: return
        tv.text = text
    }

    private fun orderCostForType(buildingType: String): Int {
        return when (buildingType) {
            "RESTAURANT" -> 50
            "SUPERMARKET" -> 30
            "HOSPITAL" -> 80
            "GYM" -> 40
            "HOUSE" -> 20
            "MONUMENT" -> 10
            "MUSEUM" -> 15
            else -> 25
        }
    }

    private fun toggleChat() {
        chatExpanded = !chatExpanded
        chatScrollView.visibility = if (chatExpanded) View.VISIBLE else View.GONE
        chatInputBar.visibility = if (chatExpanded) View.VISIBLE else View.GONE
        val arrow = chatHeader.getChildAt(1) as? TextView
        arrow?.text = if (chatExpanded) "\u25B2" else "\u25BC"
        if (chatExpanded && messages.isEmpty() && venueCharacter != null) {
            val name = venueCharacter?.name ?: building.name
            addChatMessage(false, "Ciao! Sono $name. Per ordinare scrivi: /ordina [prodotti]\n" +
                "Esempio: /ordina margherita, acqua\n" +
                "Oppure premi il pulsante Vai a lavorare per guadagnare MVC!")
        }
    }

    private fun sendChatMessage() {
        if (chatBusy || venueCharacter == null) return
        val text = chatInput.text.toString().trim()
        if (text.isBlank()) return
        chatInput.setText("")

        // Intercetta il comando /ordina
        if (text.startsWith("/ordina ", ignoreCase = true) || text == "/ordina") {
            handleOrderCommand(text)
            return
        }

        addChatMessage(true, text)
        chatBusy = true

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RealLifeClient.sendMessage(
                    context = this@BuildingInteriorActivity,
                    characterId = venueCharacter!!.id,
                    text = text,
                    username = "Utente",
                    venueId = venueId,
                )
            }
            chatBusy = false
            result.onSuccess { resp ->
                addChatMessage(false, resp.response.ifBlank { "(nessuna risposta)" })
            }.onFailure {
                addChatMessage(false, "\u26A0\uFE0F ${it.message}")
            }
        }
    }

    private fun handleOrderCommand(text: String) {
        val itemsStr = text.removePrefix("/ordina").trim()
        if (itemsStr.isBlank()) {
            addChatMessage(false, "Scrivi gli articoli da ordinare: `/ordina margherita, acqua`")
            return
        }
        val items = itemsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (items.isEmpty()) {
            addChatMessage(false, "Nessun articolo specificato.")
            return
        }

        addChatMessage(true, "/ordina $itemsStr")
        chatBusy = true

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RealLifeClient.createOrder(
                    venueId = venueId,
                    venueName = intent.getStringExtra(EXTRA_POI_NAME) ?: building.name,
                    buildingType = buildingTypeStr,
                    lat = venueLat,
                    lng = venueLng,
                    items = items,
                )
            }
            chatBusy = false
            result.onSuccess { resp ->
                if (resp.status == "error" && resp.error == "insufficient_funds") {
                    addChatMessage(false, "Non ho abbastanza MVC! 💰 Hai ${resp.balance} su ${resp.cost} necessari. Vai a lavorare prima!")
                    addWorkButton()
                } else {
                    val name = resp.characterName.ifBlank { "il commesso" }
                    addChatMessage(false, "👋 $name: Ordine preso! Costo: ${resp.cost} MVC. ${items.joinToString(", ")} pronto tra 2 minuti.")
                    showOrderNotification(resp.orderId, items)
                }
            }.onFailure {
                addChatMessage(false, "⚠️ Errore ordine: ${it.message}")
            }
        }
    }

    private fun showOrderNotification(orderId: Int, items: List<String>) {
        val notification = TextView(this).apply {
            text = "📥  Ordine #$orderId in preparazione: ${items.joinToString(", ")}"
            textSize = 11f
            setTextColor(Color.parseColor("#FFD54F"))
            setPadding(UiKit.dp(this@BuildingInteriorActivity, 8),
                UiKit.dp(this@BuildingInteriorActivity, 4),
                UiKit.dp(this@BuildingInteriorActivity, 8),
                UiKit.dp(this@BuildingInteriorActivity, 4))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@BuildingInteriorActivity, 6).toFloat()
                setColor(0x33FFD54F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(this@BuildingInteriorActivity, 4) }
            isClickable = true
            setOnClickListener {
                lifecycleScope.launch {
                    val orders = withContext(Dispatchers.IO) {
                        RealLifeClient.getOrders(venueId).getOrNull()
                    }
                    orders?.let { list ->
                        val order = list.find { it.id == orderId }
                        if (order != null && order.status != "completed") {
                            completeOrder(order)
                        }
                    }
                }
            }
        }
        chatMessagesContainer.addView(notification, 0)
    }

    private fun addWorkButton() {
        var workBtnRef: LinearLayout? = null
        val workBtn = UiKit.button(this, "💼  Vai a lavorare (+$buildingTypeStr)", UiKit.ACCENT) {
            workBtnRef?.isEnabled = false
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    RealLifeClient.work(buildingType = buildingTypeStr)
                }
                workBtnRef?.isEnabled = true
                result.onSuccess { resp ->
                    addChatMessage(false, "💼 Lavora ottenuto: ${resp.earned} MVC!")
                }.onFailure {
                    addChatMessage(false, "⚠️ Errore: ${it.message}")
                }
            }
        }
        workBtnRef = workBtn
        workBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = UiKit.dp(this@BuildingInteriorActivity, 6) }
        chatMessagesContainer.addView(workBtn, 0)
    }

    private fun completeOrder(order: OrderItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RealLifeClient.completeOrder(order.orderId, order.characterId)
            }
            result.onSuccess { resp ->
                val gainStr = resp.gains.entries.joinToString(", ") { "${it.key}: +${it.value}" }
                addChatMessage(false, "\uD83C\uDF69 Ordine #${order.orderId} completato! $gainStr")
            }.onFailure {
                addChatMessage(false, "\u26A0\uFE0F Errore completamento: ${it.message}")
            }
        }
    }

    private fun addChatMessage(isUser: Boolean, text: String) {
        messages.add(isUser to text)
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (isUser) Color.WHITE else Color.parseColor("#E8DEFF"))
            val pad = UiKit.dp(this@BuildingInteriorActivity, 10)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@BuildingInteriorActivity, 12).toFloat()
                setColor(Color.parseColor(if (isUser) UiKit.ACCENT else "#221838"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                bottomMargin = UiKit.dp(this@BuildingInteriorActivity, 6)
                topMargin = UiKit.dp(this@BuildingInteriorActivity, 2)
                if (isUser) leftMargin = UiKit.dp(this@BuildingInteriorActivity, 48)
                else rightMargin = UiKit.dp(this@BuildingInteriorActivity, 48)
            }
        }
        runOnUiThread {
            chatMessagesContainer.addView(bubble)
            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onResume() {
        super.onResume()
        needs = LocalNeeds.load(this).toMutableMap()
        refreshNeedsUI()
    }

    private fun performAction(action: com.intelligame.huntix.reallife.BuildingAction) {
        needs = LocalNeeds.applyAction(this, action.needKey, action.gain).mapValues { it.value.coerceAtMost(100f) }.toMutableMap()
        refreshNeedsUI()
        val (label, _) = needsLabels[action.needKey] ?: return
        Toast.makeText(this, "${action.emoji} ${action.label}: +${action.gain.toInt()} $label",
            Toast.LENGTH_SHORT).show()
    }

    private fun refreshNeedsUI() {
        for ((key, bar) in needsBars) {
            bar.progress = needs[key]?.toInt() ?: 0
        }
        for ((key, txt) in needsTexts) {
            txt.text = "${needs[key]?.toInt() ?: 0}%"
        }
    }

    private fun needsColor(key: String): Int = when (key) {
        "hunger" -> 0xFFFF7043.toInt()
        "sleep" -> 0xFF5C6BC0.toInt()
        "hygiene" -> 0xFF26A69A.toInt()
        "fun" -> 0xFFFFCA28.toInt()
        "thirst" -> 0xFF42A5F5.toInt()
        else -> 0xFF888888.toInt()
    }

    private class InteriorSceneView(ctx: Context, private val building: com.intelligame.huntix.reallife.BuildingDef) : View(ctx) {
        private fun dk(color: Int, factor: Float) = BuildingInteriorActivity.darken(color, factor)

        private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            isFakeBoldText = true; setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            wallPaint.color = dk(building.color3D, 0.45f)
            canvas.drawRect(0f, h * 0.1f, w, h * 0.6f, wallPaint)

            val wainscotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dk(building.color3D, 0.35f); style = Paint.Style.FILL
            }
            canvas.drawRect(0f, h * 0.42f, w, h * 0.6f, wainscotPaint)
            val wainscotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dk(building.color3D, 0.5f); style = Paint.Style.STROKE; strokeWidth = 2f
            }
            canvas.drawLine(0f, h * 0.42f, w, h * 0.42f, wainscotLinePaint)

            objectPaint.color = dk(building.color3D, 0.55f)
            canvas.drawRect(0f, h * 0.1f, w, h * 0.14f, objectPaint)

            val winLeft = w * 0.38f
            val winRight = w * 0.62f
            val winTop = h * 0.18f
            val winBottom = h * 0.38f
            val windowFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8D6E63.toInt(); style = Paint.Style.FILL }
            canvas.drawRoundRect(winLeft - 4, winTop - 4, winRight + 4, winBottom + 4, 3f, 3f, windowFramePaint)
            val windowGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF81D4FA.toInt(); style = Paint.Style.FILL }
            canvas.drawRect(winLeft, winTop, winRight, winBottom, windowGlassPaint)
            val windowDivPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8D6E63.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
            canvas.drawLine((winLeft + winRight) / 2, winTop, (winLeft + winRight) / 2, winBottom, windowDivPaint)
            canvas.drawLine(winLeft, (winTop + winBottom) / 2, winRight, (winTop + winBottom) / 2, windowDivPaint)
            val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x18FFEB3B.toInt(); style = Paint.Style.FILL
            }
            val rayPath = Path().apply {
                moveTo(winLeft, winBottom)
                lineTo(winLeft - w * 0.1f, h)
                lineTo(winRight + w * 0.1f, h)
                lineTo(winRight, winBottom)
                close()
            }
            canvas.drawPath(rayPath, rayPaint)

            val lightCx = w / 2f
            objectPaint.color = 0xFF9E9E9E.toInt()
            canvas.drawRect(lightCx - 15f, h * 0.02f, lightCx + 15f, h * 0.1f, objectPaint)
            objectPaint.color = 0xFFFFEB3B.toInt()
            canvas.drawCircle(lightCx, h * 0.12f, 8f, objectPaint)
            val lightGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x20FFEB3B.toInt(); style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(lightCx, h * 0.12f, 25f, lightGlowPaint)

            floorPaint.color = dk(building.color3D, 0.25f)
            canvas.drawRect(0f, h * 0.6f, w, h, floorPaint)

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dk(building.color3D, 0.32f); style = Paint.Style.STROKE; strokeWidth = 1f
            }
            val tileSize = w / 8f
            var i = 0f
            while (i <= w) {
                canvas.drawLine(i, h * 0.6f, i, h, gridPaint)
                i += tileSize
            }
            var j = h * 0.6f
            while (j <= h) {
                canvas.drawLine(0f, j, w, j, gridPaint)
                j += tileSize
            }

            objectPaint.color = 0xFF795548.toInt()
            canvas.drawRoundRect(w * 0.04f, h * 0.36f, w * 0.1f, h * 0.42f, 3f, 3f, objectPaint)
            objectPaint.color = 0xFF4CAF50.toInt()
            canvas.drawCircle(w * 0.07f, h * 0.32f, w * 0.035f, objectPaint)
            objectPaint.color = 0xFF66BB6A.toInt()
            canvas.drawCircle(w * 0.05f, h * 0.3f, w * 0.025f, objectPaint)
            canvas.drawCircle(w * 0.09f, h * 0.31f, w * 0.02f, objectPaint)

            objectPaint.color = 0xFF5D4037.toInt()
            canvas.drawRect(w * 0.82f, h * 0.2f, w * 0.96f, h * 0.35f, objectPaint)
            objectPaint.color = dk(building.color3D, 0.6f)
            canvas.drawRect(w * 0.84f, h * 0.22f, w * 0.94f, h * 0.33f, objectPaint)

            val rugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            rugPaint.color = dk(building.color3D, 0.5f).let { c ->
                val r2 = (Color.red(c) + 30).coerceAtMost(255)
                val g2 = (Color.green(c) - 20).coerceAtLeast(0)
                Color.rgb(r2, g2, Color.blue(c))
            }
            canvas.drawRoundRect(w * 0.25f, h * 0.68f, w * 0.75f, h * 0.88f, 8f, 8f, rugPaint)
            val rugBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dk(building.color3D, 0.6f); style = Paint.Style.STROKE; strokeWidth = 2f
            }
            canvas.drawRoundRect(w * 0.25f, h * 0.68f, w * 0.75f, h * 0.88f, 8f, 8f, rugBorderPaint)

            drawObjects(canvas, w, h)
        }

        private fun drawObjects(canvas: Canvas, w: Float, h: Float) {
            textPaint.textSize = w * 0.06f
            val cx = w / 2f
            val baseY = h * 0.58f

            when (building.type) {
                BuildingType.HOUSE -> {
                    objectPaint.color = 0xFF8D6E63.toInt()
                    canvas.drawRoundRect(w * 0.15f, baseY - h * 0.12f, w * 0.45f, baseY, 8f, 8f, objectPaint)
                    objectPaint.color = 0xFFA1887F.toInt()
                    canvas.drawRoundRect(w * 0.17f, baseY - h * 0.1f, w * 0.3f, baseY - h * 0.02f, 6f, 6f, objectPaint)
                    canvas.drawRoundRect(w * 0.31f, baseY - h * 0.1f, w * 0.43f, baseY - h * 0.02f, 6f, 6f, objectPaint)
                    objectPaint.color = 0xFF212121.toInt()
                    canvas.drawRoundRect(w * 0.55f, h * 0.2f, w * 0.85f, h * 0.42f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF4FC3F7.toInt()
                    canvas.drawRoundRect(w * 0.57f, h * 0.22f, w * 0.83f, h * 0.4f, 2f, 2f, objectPaint)
                    objectPaint.color = 0xFF5D4037.toInt()
                    canvas.drawRect(w * 0.6f, baseY - h * 0.05f, w * 0.8f, baseY, objectPaint)
                    objectPaint.color = 0xFFFFEB3B.toInt()
                    canvas.drawCircle(w * 0.12f, h * 0.18f, 8f, objectPaint)
                    objectPaint.color = 0xFF795548.toInt()
                    canvas.drawRect(w * 0.11f, h * 0.2f, w * 0.13f, baseY, objectPaint)
                    objectPaint.color = 0xFF5D4037.toInt()
                    canvas.drawRoundRect(w * 0.2f, baseY + h * 0.05f, w * 0.4f, baseY + h * 0.1f, 4f, 4f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFE0", cx, h * 0.08f, textPaint)
                }
                BuildingType.RESTAURANT -> {
                    for (t in 0..1) {
                        val tx = w * (0.2f + t * 0.4f)
                        objectPaint.color = 0xFF6D4C41.toInt()
                        canvas.drawRect(tx - w * 0.08f, baseY - h * 0.02f, tx + w * 0.08f, baseY, objectPaint)
                        objectPaint.color = 0xFF8D6E63.toInt()
                        canvas.drawRoundRect(tx - w * 0.1f, baseY - h * 0.08f, tx + w * 0.1f, baseY - h * 0.02f, 4f, 4f, objectPaint)
                        objectPaint.color = 0xFFECEFF1.toInt()
                        canvas.drawCircle(tx, baseY - h * 0.05f, w * 0.03f, objectPaint)
                        objectPaint.color = 0xFFE53935.toInt()
                        canvas.drawRect(tx - w * 0.1f, baseY - h * 0.08f, tx + w * 0.1f, baseY - h * 0.06f, objectPaint)
                    }
                    objectPaint.color = 0xFF4E342E.toInt()
                    canvas.drawRect(w * 0.02f, h * 0.15f, w * 0.98f, h * 0.22f, objectPaint)
                    objectPaint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(w * 0.1f, h * 0.1f, w * 0.12f, h * 0.15f, objectPaint)
                    objectPaint.color = 0xFFF44336.toInt()
                    canvas.drawRect(w * 0.14f, h * 0.12f, w * 0.16f, h * 0.15f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDF55", cx, h * 0.08f, textPaint)
                }
                BuildingType.SUPERMARKET -> {
                    for (s in 0..2) {
                        val sx = w * (0.15f + s * 0.3f)
                        objectPaint.color = 0xFFBDBDBD.toInt()
                        canvas.drawRect(sx - w * 0.06f, h * 0.2f, sx + w * 0.06f, baseY, objectPaint)
                        for (r in 0..2) {
                            val ry = h * (0.22f + r * 0.1f)
                            objectPaint.color = when (s * 3 + r) {
                                0 -> 0xFFE53935.toInt(); 1 -> 0xFF43A047.toInt()
                                2 -> 0xFFFF9800.toInt(); 3 -> 0xFF1E88E5.toInt()
                                4 -> 0xFF8E24AA.toInt(); 5 -> 0xFFD81B60.toInt()
                                6 -> 0xFF00897B.toInt(); 7 -> 0xFF546E7A.toInt()
                                else -> 0xFF757575.toInt()
                            }
                            canvas.drawRect(sx - w * 0.04f, ry, sx + w * 0.04f, ry + 6f, objectPaint)
                        }
                    }
                    objectPaint.color = 0xFF9E9E9E.toInt()
                    canvas.drawRoundRect(w * 0.7f, baseY - h * 0.06f, w * 0.85f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF616161.toInt()
                    canvas.drawCircle(w * 0.73f, baseY + 4f, 4f, objectPaint)
                    canvas.drawCircle(w * 0.82f, baseY + 4f, 4f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83D\uDED2", cx, h * 0.08f, textPaint)
                }
                BuildingType.HOSPITAL -> {
                    objectPaint.color = 0xFFECEFF1.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.1f, w * 0.5f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFFBBDEFB.toInt()
                    canvas.drawRoundRect(w * 0.12f, baseY - h * 0.08f, w * 0.3f, baseY - h * 0.02f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFFFFFFFF.toInt()
                    canvas.drawRoundRect(w * 0.12f, baseY - h * 0.08f, w * 0.22f, baseY - h * 0.04f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF263238.toInt()
                    canvas.drawRoundRect(w * 0.6f, h * 0.18f, w * 0.82f, h * 0.35f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(w * 0.62f, h * 0.2f, w * 0.8f, h * 0.33f, objectPaint)
                    val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF00E676.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
                    }
                    val path = Path().apply {
                        moveTo(w * 0.63f, h * 0.27f)
                        lineTo(w * 0.68f, h * 0.27f)
                        lineTo(w * 0.7f, h * 0.22f)
                        lineTo(w * 0.72f, h * 0.32f)
                        lineTo(w * 0.74f, h * 0.25f)
                        lineTo(w * 0.79f, h * 0.25f)
                    }
                    canvas.drawPath(path, heartPaint)
                    objectPaint.color = 0xFFE53935.toInt()
                    canvas.drawRect(cx - 3f, h * 0.12f, cx + 3f, h * 0.2f, objectPaint)
                    canvas.drawRect(cx - 8f, h * 0.15f, cx + 8f, h * 0.17f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFE5", cx, h * 0.08f, textPaint)
                }
                BuildingType.GYM -> {
                    objectPaint.color = 0xFF424242.toInt()
                    canvas.drawRoundRect(w * 0.05f, baseY - h * 0.15f, w * 0.25f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF616161.toInt()
                    canvas.drawRect(w * 0.07f, baseY - h * 0.12f, w * 0.23f, baseY - h * 0.02f, objectPaint)
                    objectPaint.color = 0xFF212121.toInt()
                    canvas.drawRect(w * 0.08f, baseY - h * 0.1f, w * 0.22f, baseY - h * 0.03f, objectPaint)
                    for (d in 0..1) {
                        val dx = w * (0.45f + d * 0.15f)
                        objectPaint.color = 0xFF212121.toInt()
                        canvas.drawRect(dx - 2f, baseY - h * 0.04f, dx + 2f, baseY, objectPaint)
                        objectPaint.color = 0xFF616161.toInt()
                        canvas.drawRect(dx - 8f, baseY - h * 0.03f, dx + 8f, baseY - h * 0.01f, objectPaint)
                    }
                    objectPaint.color = 0xFF37474F.toInt()
                    canvas.drawRect(w * 0.7f, baseY - h * 0.06f, w * 0.9f, baseY - h * 0.04f, objectPaint)
                    objectPaint.color = 0xFF546E7A.toInt()
                    canvas.drawRect(w * 0.72f, baseY - h * 0.02f, w * 0.76f, baseY, objectPaint)
                    canvas.drawRect(w * 0.84f, baseY - h * 0.02f, w * 0.88f, baseY, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83D\uDCAA", cx, h * 0.08f, textPaint)
                }
                BuildingType.MONUMENT -> {
                    objectPaint.color = 0xFF5D4037.toInt()
                    canvas.drawRect(cx - w * 0.12f, baseY - h * 0.08f, cx + w * 0.12f, baseY, objectPaint)
                    objectPaint.color = 0xFF795548.toInt()
                    canvas.drawRect(cx - w * 0.1f, baseY - h * 0.06f, cx + w * 0.1f, baseY - h * 0.02f, objectPaint)
                    objectPaint.color = 0xFFBDBDBD.toInt()
                    canvas.drawRect(cx - w * 0.04f, h * 0.2f, cx + w * 0.04f, baseY - h * 0.08f, objectPaint)
                    objectPaint.color = 0xFFE0E0E0.toInt()
                    canvas.drawCircle(cx, h * 0.18f, w * 0.05f, objectPaint)
                    objectPaint.color = 0xFF8D6E63.toInt()
                    canvas.drawRoundRect(w * 0.6f, h * 0.25f, w * 0.9f, h * 0.38f, 3f, 3f, objectPaint)
                    objectPaint.color = 0xFFFFEB3B.toInt()
                    canvas.drawRect(w * 0.62f, h * 0.27f, w * 0.88f, h * 0.36f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFF0", cx, h * 0.08f, textPaint)
                }
                BuildingType.MUSEUM -> {
                    for (d in 0..1) {
                        val dx = w * (0.2f + d * 0.5f)
                        objectPaint.color = 0xFF37474F.toInt()
                        canvas.drawRect(dx - w * 0.1f, h * 0.2f, dx + w * 0.1f, baseY, objectPaint)
                        objectPaint.color = 0xFF81D4FA.toInt()
                        canvas.drawRect(dx - w * 0.08f, h * 0.22f, dx + w * 0.08f, baseY - h * 0.05f, objectPaint)
                        objectPaint.color = 0xFFFFEB3B.toInt()
                        canvas.drawRect(dx - w * 0.02f, h * 0.3f, dx + w * 0.02f, h * 0.4f, objectPaint)
                    }
                    objectPaint.color = 0xFF5D4037.toInt()
                    canvas.drawRoundRect(w * 0.35f, baseY - h * 0.04f, w * 0.65f, baseY, 4f, 4f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFDB\uFE0F", cx, h * 0.08f, textPaint)
                }
                BuildingType.GOVERNMENT -> {
                    objectPaint.color = 0xFFE8E8E8.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.1f, w * 0.5f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF90A4AE.toInt()
                    canvas.drawRoundRect(w * 0.12f, baseY - h * 0.08f, w * 0.48f, baseY - h * 0.02f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFFF5F5F5.toInt()
                    for (i in 0..3) {
                        val x = w * (0.15f + i * 0.22f)
                        canvas.drawRoundRect(x, baseY - h * 0.05f, x + w * 0.12f, baseY + h * 0.05f, 2f, 2f, objectPaint)
                    }
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFE1", cx, h * 0.08f, textPaint)
                }
                BuildingType.BANK -> {
                    objectPaint.color = 0xFFE8EAF6.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.08f, w * 0.8f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF607D8B.toInt()
                    canvas.drawRect(w * 0.12f, baseY - h * 0.06f, w * 0.78f, baseY - h * 0.02f, objectPaint)
                    canvas.drawLine(w * 0.7f, baseY - h * 0.05f, w * 0.7f, baseY + h * 0.05f, objectPaint)
                    objectPaint.color = 0xFFFAFAFA.toInt()
                    canvas.drawCircle(w * 0.23f, baseY, 4f, objectPaint)
                    canvas.drawCircle(w * 0.77f, baseY, 4f, objectPaint)
                    objectPaint.color = 0xFFFFEB3B.toInt()
                    canvas.drawRoundRect(w * 0.5f, baseY - h * 0.06f, w * 0.65f, baseY, 4f, 4f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFE6", cx, h * 0.08f, textPaint)
                }
                BuildingType.POST_OFFICE -> {
                    objectPaint.color = 0xFFE3F2FD.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.08f, w * 0.8f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF2196F3.toInt()
                    canvas.drawRect(w * 0.15f, baseY - h * 0.05f, w * 0.75f, baseY + h * 0.05f, objectPaint)
                    objectPaint.color = 0xFFFAFAFA.toInt()
                    canvas.drawCircle(w * 0.25f, baseY, 3f, objectPaint)
                    objectPaint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(w * 0.65f, baseY - h * 0.03f, w * 0.77f, baseY + h * 0.03f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83D\uDCEC", cx, h * 0.08f, textPaint)
                }
                BuildingType.LIBRARY -> {
                    objectPaint.color = 0xFFF3E5F5.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.1f, w * 0.9f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF7B1FA2.toInt()
                    canvas.drawRect(w * 0.12f, baseY - h * 0.08f, w * 0.88f, baseY - h * 0.02f, objectPaint)
                    objectPaint.color = 0xFF1565C0.toInt()
                    for (s in 0..3) {
                        val sx = w * (0.15f + s * 0.22f)
                        objectPaint.color = 0xFF1565C0.toInt()
                        canvas.drawRect(sx - w * 0.04f, h * 0.2f, sx + w * 0.04f, baseY, objectPaint)
                        objectPaint.color = 0xFF90CAF9.toInt()
                        canvas.drawRect(sx - w * 0.02f, h * 0.22f, sx + w * 0.02f, baseY - h * 0.05f, objectPaint)
                    }
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83D\uDCDA", cx, h * 0.08f, textPaint)
                }
                BuildingType.SCHOOL -> {
                    objectPaint.color = 0xFFFFF9C4.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.08f, w * 0.7f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFFF9A825.toInt()
                    canvas.drawRect(w * 0.12f, baseY - h * 0.06f, w * 0.68f, baseY - h * 0.02f, objectPaint)
                    objectPaint.color = 0xFF90CAF9.toInt()
                    canvas.drawRect(w * 0.4f, h * 0.2f, w * 0.6f, baseY - h * 0.2f, objectPaint)
                    objectPaint.color = 0xFFE53935.toInt()
                    canvas.drawRect(w * 0.42f, h * 0.22f, w * 0.58f, h * 0.28f, objectPaint)
                    for (d in 0..1) {
                        val dx = w * (0.2f + d * 0.5f)
                        objectPaint.color = 0xFF6D4C41.toInt()
                        canvas.drawRect(dx - 2f, baseY - h * 0.04f, dx + 2f, baseY, objectPaint)
                    }
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("\uD83C\uDFEB", cx, h * 0.08f, textPaint)
                }
            }
        }

    }
}

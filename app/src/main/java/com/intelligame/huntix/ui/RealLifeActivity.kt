package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.AvatarConfig
import com.intelligame.huntix.reallife.CharacterItem
import com.intelligame.huntix.reallife.MoneyManager
import com.intelligame.huntix.reallife.Needs
import com.intelligame.huntix.reallife.RealLifeAuth
import com.intelligame.huntix.reallife.RealLifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RealLifeActivity — Hub del mondo "Real Life" di Huntix.
 *
 * Mostra: avatar giocatore, stato del mondo (data/ora/stagione/meteo),
 * bisogni (Sims), azioni rapide (Città 3D, Chat, Negozio, Profilo),
 * e lista NPC con relazione.
 *
 * Stile Brookhaven: il giocatore È un cittadino di questo mondo virtuale.
 */
class RealLifeActivity : BaseNavActivity() {
    override fun activeTab() = ""

    private lateinit var avatarPreviewView: AvatarPreviewHubView
    private lateinit var nameText: TextView
    private lateinit var worldText: TextView
    private lateinit var needsContainer: LinearLayout
    private lateinit var npcContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var moneyText: TextView
    private var avatarConfig: AvatarConfig = AvatarConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        avatarConfig = AvatarConfig.load(this)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        avatarConfig = AvatarConfig.load(this)
        avatarPreviewView.updateConfig(avatarConfig)
        nameText.text = RealLifeAuth.getUsername(this)
        moneyText.text = "💵 \$${MoneyManager.getCash(this)}"
    }

    private fun buildUI() {
        val c = this

        // ── Top bar ──
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        topBar.addView(TextView(c).apply {
            text = "←"; textSize = 22f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
            setPadding(0, 0, UiKit.dp(c, 12), 0)
        })
        topBar.addView(TextView(c).apply {
            text = "Real Life"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        moneyText = TextView(c).apply {
            text = "💵 \$${MoneyManager.getCash(c)}"; textSize = 14f
            setTextColor(Color.parseColor("#FFD86B")); typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 4), UiKit.dp(c, 8), UiKit.dp(c, 4))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 8).toFloat()
                setColor(0x33FFD86B)
            }
        }
        topBar.addView(moneyText)

        // ── Avatar card ──
        val avatarCard = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 14).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
            }
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiKit.dp(c, 8); marginStart = UiKit.dp(c, 14); marginEnd = UiKit.dp(c, 14)
            }
        }

        avatarPreviewView = AvatarPreviewHubView(c, avatarConfig).apply {
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(c, 72), UiKit.dp(c, 72))
        }
        avatarCard.addView(avatarPreviewView)

        val avatarInfo = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(UiKit.dp(c, 12), 0, 0, 0)
        }
        nameText = TextView(c).apply {
            text = RealLifeAuth.getUsername(c)
            textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        avatarInfo.addView(nameText)
        avatarInfo.addView(TextView(c).apply {
            text = "Cittadino di Huntix"; textSize = 11f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 2), 0, 0)
        })
        avatarCard.addView(avatarInfo)

        val customizeBtn = TextView(c).apply {
            text = "👤"; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; isFocusable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 20).toFloat()
                setColor(Color.parseColor("#1A1030"))
                setStroke(1, Color.parseColor(UiKit.ACCENT))
            }
            setPadding(UiKit.dp(c, 10), UiKit.dp(c, 10), UiKit.dp(c, 10), UiKit.dp(c, 10))
            setOnClickListener {
                startActivity(Intent(c, AvatarCustomizeActivity::class.java))
            }
        }
        avatarCard.addView(customizeBtn)

        // ── World state ──
        worldText = TextView(c).apply {
            text = "🌍 …"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD86B"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 8), UiKit.dp(c, 14), 0)
        }

        // ── Bisogni ──
        val needsHeader = TextView(c).apply {
            text = "📊  Bisogni"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 10), UiKit.dp(c, 14), UiKit.dp(c, 4))
        }
        needsContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), 0)
        }

        // ── Azioni rapide ──
        val actionsHeader = TextView(c).apply {
            text = "⚡  Azioni"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 4))
        }
        val actionsGrid = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 10), 0, UiKit.dp(c, 10), 0)
        }
        actionsGrid.addView(actionButton(c, "🏙️", "Città 3D", "#FF6D00") {
            startActivity(Intent(c, CityActivity::class.java))
        })
        actionsGrid.addView(actionSpacer(c))
        actionsGrid.addView(actionButton(c, "💬", "Chat NPC", "#42A5F5") {
            startActivity(Intent(c, NpcListActivity::class.java))
        })
        actionsGrid.addView(actionSpacer(c))
        actionsGrid.addView(actionButton(c, "💰", "Lavora", "#FFCA28") {
            startActivity(Intent(c, JobsActivity::class.java))
        })
        actionsGrid.addView(actionSpacer(c))
        actionsGrid.addView(actionButton(c, "🛒", "Negozio", "#FF7043") {
            startActivity(Intent(c, com.intelligame.huntix.ui.ShopActivity::class.java))
        })
        actionsGrid.addView(actionSpacer(c))
        actionsGrid.addView(actionButton(c, "📋", "Profilo", "#00FF88") {
            startActivity(Intent(c, PlayerProfileActivity::class.java))
        })

        // ── NPC Section ──
        val npcHeader = TextView(c).apply {
            text = "👥  Personaggi del Mondo"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 4))
        }
        npcContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        statusText = TextView(c).apply {
            text = "Caricamento personaggi…"; textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 4), UiKit.dp(c, 14), 0)
        }

        // ── Assemble ──
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(topBar)
        root.addView(avatarCard)
        root.addView(worldText)
        root.addView(needsHeader)
        root.addView(needsContainer)
        root.addView(actionsHeader)
        root.addView(actionsGrid)
        root.addView(npcHeader)
        root.addView(npcContainer)
        root.addView(statusText)

        val scroll = android.widget.ScrollView(c).apply {
            setBackgroundColor(Color.parseColor(UiKit.BG))
            addView(root)
        }
        setContentView(scroll)

        // Load data
        loadWorldState()
        loadNeeds()
        loadCharacters()
    }

    // ── World State ──────────────────────────────────────────────

    private fun loadWorldState() {
        lifecycleScope.launch {
            val ws = withContext(Dispatchers.IO) { RealLifeClient.getWorldState() }.getOrNull()
            if (ws != null) {
                worldText.text = "🌍  ${formatDate(ws.date)} · ${ws.time}  ·  ${ws.season}  ·  ${ws.weather}"
            } else {
                worldText.text = "🌍  Mondo in caricamento…"
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            val months = arrayOf("", "Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
            "${parts[2].toInt()} ${months[parts[1].toInt()]} ${parts[0]}"
        } catch (e: Exception) { dateStr }
    }

    // ── Needs (Sims) ─────────────────────────────────────────────

    private fun loadNeeds() {
        lifecycleScope.launch {
            val userId = RealLifeAuth.getUsername(this@RealLifeActivity)
            if (userId.isBlank()) return@launch

            // Carica needs del primo personaggio (o default)
            val chars = withContext(Dispatchers.IO) { RealLifeClient.getCharacters(null) }.getOrNull()
            val firstCharId = chars?.firstOrNull()?.id
            if (firstCharId != null) {
                val needs = withContext(Dispatchers.IO) { RealLifeClient.getNeeds(firstCharId, userId) }.getOrNull()
                if (needs != null) {
                    renderNeeds(needs)
                    return@launch
                }
            }
            // Fallback: needs default
            renderDefaultNeeds()
        }
    }

    private fun renderNeeds(needs: Needs) {
        val items = listOf(
            Triple("🍔 Fame", needs.hunger.toFloat(), "#FF8A3D"),
            Triple("😴 Sonno", needs.sleep.toFloat(), "#4FA3FF"),
            Triple("🚿 Igiene", needs.hygiene.toFloat(), "#3DE0E0"),
            Triple("💬 Socialità", needs.social.toFloat(), "#3DFF8A"),
            Triple("🎉 Divertimento", needs.funLevel.toFloat(), "#B96BFF")
        )
        runOnUiThread {
            needsContainer.removeAllViews()
            items.forEach { (label, value, color) ->
                needsContainer.addView(makeNeedBar(label, value, color))
            }
        }
    }

    private fun renderDefaultNeeds() {
        runOnUiThread {
            needsContainer.removeAllViews()
            val items = listOf(
                Triple("🍔 Fame", 70f, "#FF8A3D"),
                Triple("😴 Sonno", 70f, "#4FA3FF"),
                Triple("🚿 Igiene", 70f, "#3DE0E0"),
                Triple("💬 Socialità", 70f, "#3DFF8A"),
                Triple("🎉 Divertimento", 70f, "#B96BFF")
            )
            items.forEach { (label, value, color) ->
                needsContainer.addView(makeNeedBar(label, value, color))
            }
        }
    }

    private fun makeNeedBar(label: String, value: Float, color: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(this@RealLifeActivity, 4) }

            addView(TextView(this@RealLifeActivity).apply {
                text = label; textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@RealLifeActivity, 110), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val track = LinearLayout(this@RealLifeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@RealLifeActivity, 6).toFloat()
                    setColor(Color.parseColor("#221838"))
                }
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@RealLifeActivity, 12), 1f)
            }
            val fill = View(this@RealLifeActivity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@RealLifeActivity, 6).toFloat()
                    setColor(Color.parseColor(color))
                }
                layoutParams = LinearLayout.LayoutParams(
                    (UiKit.dp(this@RealLifeActivity, 200) * (value / 100f)).toInt().coerceAtLeast(2),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            track.addView(fill)
            addView(track)

            addView(TextView(this@RealLifeActivity).apply {
                text = "${value.toInt()}%"; textSize = 11f; setTextColor(Color.WHITE)
                setPadding(UiKit.dp(this@RealLifeActivity, 6), 0, 0, 0)
            })
        }
    }

    // ── NPC List ─────────────────────────────────────────────────

    private fun loadCharacters() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RealLifeClient.getCharacters(null) }
            result.onSuccess { chars ->
                if (chars.isEmpty()) {
                    statusText.text = "Nessun personaggio disponibile."
                } else {
                    statusText.visibility = View.GONE
                    renderNpcList(chars.take(12)) // max 12 in hub
                }
            }.onFailure {
                statusText.text = "Errore: ${it.message}"
            }
        }
    }

    private fun renderNpcList(chars: List<CharacterItem>) {
        runOnUiThread {
            npcContainer.removeAllViews()
            chars.chunked(2).forEach { row ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiKit.dp(this@RealLifeActivity, 6) }
                }
                row.forEach { char ->
                    rowLayout.addView(npcCard(char))
                    if (row.size == 2) {
                        rowLayout.addView(View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@RealLifeActivity, 8), 0)
                        })
                    }
                }
                if (row.size < 2) {
                    rowLayout.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    })
                }
                npcContainer.addView(rowLayout)
            }
        }
    }

    private fun npcCard(char: CharacterItem): LinearLayout {
        val c = this
        val emoji = (char.avatar ?: "🙂").takeIf { it.length <= 2 } ?: "🙂"
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 12).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(1, Color.parseColor("#2A2240"))
            }
            setPadding(UiKit.dp(c, 10), UiKit.dp(c, 10), UiKit.dp(c, 10), UiKit.dp(c, 10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener {
                startActivity(Intent(c, RealLifeChatActivity::class.java).apply {
                    putExtra("CHAR_ID", char.id)
                    putExtra("CHAR_NAME", char.name)
                    putExtra("CHAR_AVATAR", emoji)
                    putExtra("CHAR_TAGS", ArrayList(char.tags ?: emptyList()))
                })
            }

            addView(TextView(c).apply {
                text = emoji; textSize = 28f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            addView(TextView(c).apply {
                text = char.name; textSize = 12f; setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                maxLines = 1; setPadding(0, UiKit.dp(c, 4), 0, 0)
            })
            char.role?.takeIf { it.isNotBlank() }?.let { role ->
                addView(TextView(c).apply {
                    text = role; textSize = 10f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    gravity = Gravity.CENTER; maxLines = 1
                })
            }
            char.intimacyLabel?.takeIf { it.isNotBlank() }?.let { lbl ->
                addView(TextView(c).apply {
                    text = "💞 $lbl"; textSize = 9f; setTextColor(Color.parseColor(UiKit.GREEN))
                    gravity = Gravity.CENTER
                })
            }
        }
    }

    // ── Action Buttons ───────────────────────────────────────────

    private fun actionButton(c: android.content.Context, emoji: String, label: String, color: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 14).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(1, Color.parseColor(color))
            }
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 12), UiKit.dp(c, 8), UiKit.dp(c, 10))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(c).apply {
                text = emoji; textSize = 26f; gravity = Gravity.CENTER
            })
            addView(TextView(c).apply {
                text = label; textSize = 10f; setTextColor(Color.parseColor(color))
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(c, 4), 0, 0)
            })
        }
    }

    private fun actionSpacer(c: android.content.Context): View {
        return View(c).apply {
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(c, 6), 0)
        }
    }

    // ── Avatar Preview Hub (piccola, per la card) ──

    class AvatarPreviewHubView(context: android.content.Context, private var config: AvatarConfig) : View(context) {
        fun updateConfig(newConfig: AvatarConfig) {
            config = newConfig
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val scale = width / 80f

            // Sfondo cerchio
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, cx,
                    intArrayOf(Color.parseColor("#2A1B4A"), Color.parseColor("#0D0620")),
                    floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(cx, cy, cx, bgPaint)

            config.drawPreview(canvas, cx, cy + 4f * scale, scale * 0.55f)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(size, size)
        }
    }
}

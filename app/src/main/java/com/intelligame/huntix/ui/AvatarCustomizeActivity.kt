package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.AvatarConfig

/**
 * AvatarCustomizeActivity — schermata di personalizzazione avatar Real Life.
 *
 * Permette di scegliere genere, colori (pelle, capelli, vestiti, scarpe) e
 * stile capelli. La preview 2D si aggiorna in tempo reale.
 */
class AvatarCustomizeActivity : AppCompatActivity() {

    private lateinit var previewView: AvatarPreviewView
    private lateinit var config: AvatarConfig
    private var selectedCategory = "pelle"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AvatarConfig.load(this)
        buildUI()
    }

    private fun buildUI() {
        val c = this

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
            text = "👤  Personalizza Avatar"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        previewView = AvatarPreviewView(c, config).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 220)
            )
        }

        // ── Genere toggle ──
        val genderRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 8))
        }
        val btnMale = makeToggleBtn("🧑  Maschio", config.gender == "male") {
            config = config.copy(gender = "male"); refreshAll()
        }
        val btnFemale = makeToggleBtn("👩  Femmina", config.gender == "female") {
            config = config.copy(gender = "female"); refreshAll()
        }
        genderRow.addView(btnMale)
        genderRow.addView(makeSpacer())
        genderRow.addView(btnFemale)

        // ── Categorie colori ──
        val catRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 4))
        }
        val categories = listOf(
            "pelle" to "Pelle", "capelli" to "Capelli", "occhi" to "Occhi",
            "maglia" to "Maglia", "pantaloni" to "Pantaloni", "scarpe" to "Scarpe"
        )
        categories.forEachIndexed { idx, (key, label) ->
            val btn = TextView(c).apply {
                text = label; textSize = 11f
                setTextColor(if (selectedCategory == key) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                typeface = if (selectedCategory == key) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 10).toFloat()
                    setColor(Color.parseColor(if (selectedCategory == key) "#2A1B4A" else "#0D0620"))
                    setStroke(1, Color.parseColor(if (selectedCategory == key) UiKit.ACCENT else "#2A2240"))
                }
                setPadding(UiKit.dp(c, 8), UiKit.dp(c, 5), UiKit.dp(c, 8), UiKit.dp(c, 5))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = UiKit.dp(c, 4) }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    selectedCategory = key
                    refreshAll()
                }
            }
            catRow.addView(btn)
        }

        // ── Palette colori ──
        val paletteContainer = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 6), UiKit.dp(c, 14), UiKit.dp(c, 6))
        }

        // ── Stile capelli (solo se capelli selezionato) ──
        val hairStyleRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 4), UiKit.dp(c, 14), UiKit.dp(c, 4))
            visibility = View.GONE
        }

        // ── Bottone salva ──
        val saveBtn = UiKit.button(c, "✓  Salva Avatar", "#00FF88") {
            AvatarConfig.save(this@AvatarCustomizeActivity, config)
            setResult(RESULT_OK)
            finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 48)
            ).apply {
                topMargin = UiKit.dp(c, 12)
                marginStart = UiKit.dp(c, 14); marginEnd = UiKit.dp(c, 14)
            }
        }

        val scroll = android.widget.ScrollView(c).apply {
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(topBar)
        root.addView(previewView)
        root.addView(genderRow)
        root.addView(catRow)
        root.addView(paletteContainer)
        root.addView(hairStyleRow)
        root.addView(saveBtn)
        root.addView(TextView(c).apply {
            text = " "; textSize = 8f
        })
        scroll.addView(root)
        setContentView(scroll)

        // Salva riferimenti per refresh
        paletteRef = paletteContainer
        hairStyleRef = hairStyleRow
        refreshAll()
    }

    private lateinit var paletteRef: LinearLayout
    private lateinit var hairStyleRef: LinearLayout

    private fun refreshAll() {
        previewView.updateConfig(config)
        renderPalette()
        renderHairStyle()
        // Aggiorna toggle genere
        (paletteRef.parent as? LinearLayout)?.let { refreshGenderButtons(it) }
    }

    private fun refreshGenderButtons(root: LinearLayout) {
        // Trova la gender row (secondo figlio del root) e ricostruiscila
        // Per semplicità: rebuild dell'intera UI è troppo costoso,
        // quindi aggiorniamo solo i colori dei bottoni
        val genderRow = root.getChildAt(2) as? LinearLayout ?: return
        val btns = (0 until genderRow.childCount).mapNotNull { genderRow.getChildAt(it) as? TextView }
        if (btns.size == 2) {
            btns[0].apply {
                text = "🧑  Maschio"
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@AvatarCustomizeActivity, 10).toFloat()
                    setColor(Color.parseColor(if (config.gender == "male") "#2A1B4A" else "#1A1030"))
                    setStroke(2, Color.parseColor(if (config.gender == "male") UiKit.ACCENT else "#2A2240"))
                }
            }
            btns[1].apply {
                text = "👩  Femmina"
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@AvatarCustomizeActivity, 10).toFloat()
                    setColor(Color.parseColor(if (config.gender == "female") "#2A1B4A" else "#1A1030"))
                    setStroke(2, Color.parseColor(if (config.gender == "female") UiKit.ACCENT else "#2A2240"))
                }
            }
        }
    }

    private fun renderPalette() {
        paletteRef.removeAllViews()
        val colors = when (selectedCategory) {
            "pelle" -> AvatarConfig.SKIN_COLORS
            "capelli" -> AvatarConfig.HAIR_COLORS
            "occhi" -> intArrayOf(
                0xFF1B5E20.toInt(), 0xFF1565C0.toInt(), 0xFF4E342E.toInt(),
                0xFF6A1B9A.toInt(), 0xFF37474F.toInt()
            )
            "maglia" -> AvatarConfig.SHIRT_COLORS
            "pantaloni" -> AvatarConfig.PANTS_COLORS
            "scarpe" -> AvatarConfig.SHOE_COLORS
            else -> AvatarConfig.SKIN_COLORS
        }
        val currentColor = when (selectedCategory) {
            "pelle" -> config.skinColor
            "capelli" -> config.hairColor
            "occhi" -> config.eyeColor
            "maglia" -> config.shirtColor
            "pantaloni" -> config.pantsColor
            "scarpe" -> config.shoeColor
            else -> config.skinColor
        }

        colors.forEach { color ->
            val swatch = View(this).apply {
                val size = UiKit.dp(this@AvatarCustomizeActivity, 36)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = UiKit.dp(this@AvatarCustomizeActivity, 6)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    setStroke(if (color == currentColor) 3 else 1,
                        Color.WHITE.takeIf { color == currentColor } ?: Color.parseColor("#333333"))
                }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    config = when (selectedCategory) {
                        "pelle" -> config.copy(skinColor = color)
                        "capelli" -> config.copy(hairColor = color)
                        "occhi" -> config.copy(eyeColor = color)
                        "maglia" -> config.copy(shirtColor = color)
                        "pantaloni" -> config.copy(pantsColor = color)
                        "scarpe" -> config.copy(shoeColor = color)
                        else -> config
                    }
                    refreshAll()
                }
            }
            paletteRef.addView(swatch)
        }
    }

    private fun renderHairStyle() {
        hairStyleRef.removeAllViews()
        if (selectedCategory == "capelli") {
            hairStyleRef.visibility = View.VISIBLE
            AvatarConfig.HAIR_STYLES.forEachIndexed { idx, style ->
                val btn = TextView(this).apply {
                    text = AvatarConfig.HAIR_STYLE_LABELS[idx]
                    textSize = 11f
                    setTextColor(if (config.hairStyle == style) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                    typeface = if (config.hairStyle == style) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    gravity = Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = UiKit.dp(this@AvatarCustomizeActivity, 10).toFloat()
                        setColor(Color.parseColor(if (config.hairStyle == style) "#2A1B4A" else "#0D0620"))
                        setStroke(1, Color.parseColor(if (config.hairStyle == style) UiKit.ACCENT else "#2A2240"))
                    }
                    setPadding(UiKit.dp(this@AvatarCustomizeActivity, 10), UiKit.dp(this@AvatarCustomizeActivity, 5),
                        UiKit.dp(this@AvatarCustomizeActivity, 10), UiKit.dp(this@AvatarCustomizeActivity, 5))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = UiKit.dp(this@AvatarCustomizeActivity, 6) }
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        config = config.copy(hairStyle = style)
                        refreshAll()
                    }
                }
                hairStyleRef.addView(btn)
            }
        } else {
            hairStyleRef.visibility = View.GONE
        }
    }

    private fun makeToggleBtn(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@AvatarCustomizeActivity, 12).toFloat()
                setColor(Color.parseColor(if (active) "#2A1B4A" else "#1A1030"))
                setStroke(2, Color.parseColor(if (active) UiKit.ACCENT else "#2A2240"))
            }
            setPadding(UiKit.dp(this@AvatarCustomizeActivity, 20), UiKit.dp(this@AvatarCustomizeActivity, 10),
                UiKit.dp(this@AvatarCustomizeActivity, 20), UiKit.dp(this@AvatarCustomizeActivity, 10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun makeSpacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@AvatarCustomizeActivity, 8), 0)
    }

    // ── View interna che disegna l'avatar con Canvas ──

    class AvatarPreviewView(context: android.content.Context, private var config: AvatarConfig) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f, 300f,
                intArrayOf(Color.parseColor("#2A1B4A"), Color.parseColor("#0D0620")),
                floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP
            )
        }

        fun updateConfig(newConfig: AvatarConfig) {
            config = newConfig
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Sfondo con gradiente
            bgPaint.shader?.let {
                (it as? RadialGradient)?.let { _ ->
                    val shader = RadialGradient(
                        width / 2f, height / 2f, width / 2f,
                        intArrayOf(Color.parseColor("#2A1B4A"), Color.parseColor("#0D0620")),
                        floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP
                    )
                    bgPaint.shader = shader
                }
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val scale = width / 160f
            config.drawPreview(canvas, width / 2f, height / 2f + 10f * scale, scale)

            // Etichetta sotto
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(UiKit.TEXT_DIM)
                textAlign = Paint.Align.CENTER
                textSize = 12f * scale
            }
            canvas.drawText("Anteprima Avatar", width / 2f, height - 12f * scale, labelPaint)
        }
    }
}

package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.BuildingDefs
import com.intelligame.huntix.reallife.BuildingType
import com.intelligame.huntix.reallife.LocalNeeds

/**
 * BuildingInteriorActivity — schermata interni di un edificio della città.
 *
 * Mostra: sfondo colorato, action buttons per ricaricare bisogni, barre bisogni aggiornate.
 * Riceve EXTRA_BUILDING_TYPE (ordinal dell'enum BuildingType).
 */
class BuildingInteriorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BUILDING_TYPE = "building_type"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeOrdinal = intent.getIntExtra(EXTRA_BUILDING_TYPE, 0)
        building = BuildingDefs.BUILDINGS[typeOrdinal]
        needs = LocalNeeds.load(this).toMutableMap()

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
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = "${building.emoji}  ${building.name}"
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
                val size = UiKit.dp(this@BuildingInteriorActivity, 80)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
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
                text = "${needs[key]?.toInt() ?: 60}%"
            }
            needsTexts[key] = pct
            row.addView(pct)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = needs[key]?.toInt() ?: 60
                layoutParams = LinearLayout.LayoutParams(
                    UiKit.dp(this@BuildingInteriorActivity, 100),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = UiKit.dp(this@BuildingInteriorActivity, 6) }
                progressDrawable = android.graphics.drawable.GradientDrawable().let {
                    // Use a layered drawable for colored progress
                    val bg = android.graphics.drawable.ColorDrawable(0x33FFFFFF)
                    val fg = android.graphics.drawable.ColorDrawable(needsColor(key))
                    android.graphics.drawable.LayerDrawable(arrayOf(bg, fg))
                }
            }
            needsBars[key] = bar
            row.addView(bar)
            needsCard.addView(row)
        }
        root.addView(needsCard)

        setContentView(root)
    }

    private fun performAction(action: com.intelligame.huntix.reallife.BuildingAction) {
        needs = LocalNeeds.applyAction(this, action.needKey, action.gain).toMutableUI()
        refreshNeedsUI()
        val (label, _) = needsLabels[action.needKey] ?: return
        Toast.makeText(this, "${action.emoji} ${action.label}: +${action.gain.toInt()} $label",
            Toast.LENGTH_SHORT).show()
    }

    private fun refreshNeedsUI() {
        for ((key, bar) in needsBars) {
            bar.progress = needs[key]?.toInt() ?: 60
        }
        for ((key, txt) in needsTexts) {
            txt.text = "${needs[key]?.toInt() ?: 60}%"
        }
    }

    private fun MutableMap<String, Float>.toMutableUI() = this

    private fun needsColor(key: String): Int = when (key) {
        "hunger" -> 0xFFFF7043.toInt()
        "sleep" -> 0xFF5C6BC0.toInt()
        "hygiene" -> 0xFF26A69A.toInt()
        "fun" -> 0xFFFFCA28.toInt()
        "thirst" -> 0xFF42A5F5.toInt()
        else -> 0xFF888888.toInt()
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * InteriorSceneView — disegna la scena interna dell'edificio con Canvas.
     */
    private class InteriorSceneView(ctx: Context, private val building: com.intelligame.huntix.reallife.BuildingDef) : View(ctx) {

        private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            isFakeBoldText = true; setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Floor
            floorPaint.color = darken(building.color3D, 0.25f)
            canvas.drawRect(0f, h * 0.6f, w, h, floorPaint)

            // Floor grid
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darken(building.color3D, 0.35f); style = Paint.Style.STROKE; strokeWidth = 1f
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

            // Back wall
            wallPaint.color = darken(building.color3D, 0.4f)
            canvas.drawRect(0f, h * 0.1f, w, h * 0.6f, wallPaint)

            // Wall trim
            roofPaint.color = darken(building.color3D, 0.5f)
            canvas.drawRect(0f, h * 0.1f, w, h * 0.15f, roofPaint)

            // Building-specific interior objects
            drawObjects(canvas, w, h)
        }

        private fun drawObjects(canvas: Canvas, w: Float, h: Float) {
            textPaint.textSize = w * 0.06f
            val cx = w / 2f
            val baseY = h * 0.58f

            when (building.type) {
                BuildingType.HOUSE -> {
                    // Couch
                    objectPaint.color = 0xFF8D6E63.toInt()
                    canvas.drawRoundRect(w * 0.15f, baseY - h * 0.12f, w * 0.45f, baseY, 8f, 8f, objectPaint)
                    // Cushions
                    objectPaint.color = 0xFFA1887F.toInt()
                    canvas.drawRoundRect(w * 0.17f, baseY - h * 0.1f, w * 0.3f, baseY - h * 0.02f, 6f, 6f, objectPaint)
                    canvas.drawRoundRect(w * 0.31f, baseY - h * 0.1f, w * 0.43f, baseY - h * 0.02f, 6f, 6f, objectPaint)
                    // TV
                    objectPaint.color = 0xFF212121.toInt()
                    canvas.drawRoundRect(w * 0.55f, h * 0.2f, w * 0.85f, h * 0.42f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF4FC3F7.toInt()
                    canvas.drawRoundRect(w * 0.57f, h * 0.22f, w * 0.83f, h * 0.4f, 2f, 2f, objectPaint)
                    // TV stand
                    objectPaint.color = 0xFF5D4037.toInt()
                    canvas.drawRect(w * 0.6f, baseY - h * 0.05f, w * 0.8f, baseY, objectPaint)
                    // Lamp
                    objectPaint.color = 0xFFFFEB3B.toInt()
                    canvas.drawCircle(w * 0.12f, h * 0.18f, 8f, objectPaint)
                    objectPaint.color = 0xFF795548.toInt()
                    canvas.drawRect(w * 0.11f, h * 0.2f, w * 0.13f, baseY, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("🏠", cx, h * 0.08f, textPaint)
                }
                BuildingType.RESTAURANT -> {
                    // Tables
                    for (t in 0..1) {
                        val tx = w * (0.2f + t * 0.4f)
                        objectPaint.color = 0xFF6D4C41.toInt()
                        canvas.drawRect(tx - w * 0.08f, baseY - h * 0.02f, tx + w * 0.08f, baseY, objectPaint)
                        // Table top
                        objectPaint.color = 0xFF8D6E63.toInt()
                        canvas.drawRoundRect(tx - w * 0.1f, baseY - h * 0.08f, tx + w * 0.1f, baseY - h * 0.02f, 4f, 4f, objectPaint)
                        // Plate
                        objectPaint.color = 0xFFECEFF1.toInt()
                        canvas.drawCircle(tx, baseY - h * 0.05f, w * 0.03f, objectPaint)
                    }
                    // Counter
                    objectPaint.color = 0xFF4E342E.toInt()
                    canvas.drawRect(w * 0.02f, h * 0.15f, w * 0.98f, h * 0.22f, objectPaint)
                    // Bottles on counter
                    objectPaint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(w * 0.1f, h * 0.1f, w * 0.12f, h * 0.15f, objectPaint)
                    objectPaint.color = 0xFFF44336.toInt()
                    canvas.drawRect(w * 0.14f, h * 0.12f, w * 0.16f, h * 0.15f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("🍕", cx, h * 0.08f, textPaint)
                }
                BuildingType.SUPERMARKET -> {
                    // Shelves
                    for (s in 0..2) {
                        val sx = w * (0.15f + s * 0.3f)
                        objectPaint.color = 0xFFBDBDBD.toInt()
                        canvas.drawRect(sx - w * 0.06f, h * 0.2f, sx + w * 0.06f, baseY, objectPaint)
                        // Items on shelves
                        for (r in 0..2) {
                            val ry = h * (0.22f + r * 0.1f)
                            objectPaint.color = when (s * 3 + r) {
                                0 -> 0xFFE53935.toInt()
                                1 -> 0xFF43A047.toInt()
                                2 -> 0xFFFF9800.toInt()
                                3 -> 0xFF1E88E5.toInt()
                                4 -> 0xFF8E24AA.toInt()
                                5 -> 0xFFD81B60.toInt()
                                6 -> 0xFF00897B.toInt()
                                7 -> 0xFF546E7A.toInt()
                                else -> 0xFF757575.toInt()
                            }
                            canvas.drawRect(sx - w * 0.04f, ry, sx + w * 0.04f, ry + 6f, objectPaint)
                        }
                    }
                    // Cart
                    objectPaint.color = 0xFF9E9E9E.toInt()
                    canvas.drawRoundRect(w * 0.7f, baseY - h * 0.06f, w * 0.85f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF616161.toInt()
                    canvas.drawCircle(w * 0.73f, baseY + 4f, 4f, objectPaint)
                    canvas.drawCircle(w * 0.82f, baseY + 4f, 4f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("🛒", cx, h * 0.08f, textPaint)
                }
                BuildingType.HOSPITAL -> {
                    // Bed
                    objectPaint.color = 0xFFECEFF1.toInt()
                    canvas.drawRoundRect(w * 0.1f, baseY - h * 0.1f, w * 0.5f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFFBBDEFB.toInt()
                    canvas.drawRoundRect(w * 0.12f, baseY - h * 0.08f, w * 0.3f, baseY - h * 0.02f, 4f, 4f, objectPaint)
                    // Pillow
                    objectPaint.color = 0xFFFFFFFF.toInt()
                    canvas.drawRoundRect(w * 0.12f, baseY - h * 0.08f, w * 0.22f, baseY - h * 0.04f, 4f, 4f, objectPaint)
                    // Heart monitor
                    objectPaint.color = 0xFF263238.toInt()
                    canvas.drawRoundRect(w * 0.6f, h * 0.18f, w * 0.82f, h * 0.35f, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(w * 0.62f, h * 0.2f, w * 0.8f, h * 0.33f, objectPaint)
                    // Heart line
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
                    // Red cross
                    objectPaint.color = 0xFFE53935.toInt()
                    canvas.drawRect(cx - 3f, h * 0.12f, cx + 3f, h * 0.2f, objectPaint)
                    canvas.drawRect(cx - 8f, h * 0.15f, cx + 8f, h * 0.17f, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("🏥", cx, h * 0.08f, textPaint)
                }
                BuildingType.GYM -> {
                    // Treadmill
                    objectPaint.color = 0xFF424242.toInt()
                    canvas.drawRoundRect(w * 0.05f, baseY - h * 0.15f, w * 0.25f, baseY, 4f, 4f, objectPaint)
                    objectPaint.color = 0xFF616161.toInt()
                    canvas.drawRect(w * 0.07f, baseY - h * 0.12f, w * 0.23f, baseY - h * 0.02f, objectPaint)
                    // Belt
                    objectPaint.color = 0xFF212121.toInt()
                    canvas.drawRect(w * 0.08f, baseY - h * 0.1f, w * 0.22f, baseY - h * 0.03f, objectPaint)
                    // Dumbbells
                    for (d in 0..1) {
                        val dx = w * (0.45f + d * 0.15f)
                        objectPaint.color = 0xFF212121.toInt()
                        canvas.drawRect(dx - 2f, baseY - h * 0.04f, dx + 2f, baseY, objectPaint)
                        objectPaint.color = 0xFF616161.toInt()
                        canvas.drawRect(dx - 8f, baseY - h * 0.03f, dx + 8f, baseY - h * 0.01f, objectPaint)
                    }
                    // Bench
                    objectPaint.color = 0xFF37474F.toInt()
                    canvas.drawRect(w * 0.7f, baseY - h * 0.06f, w * 0.9f, baseY - h * 0.04f, objectPaint)
                    objectPaint.color = 0xFF546E7A.toInt()
                    canvas.drawRect(w * 0.72f, baseY - h * 0.02f, w * 0.76f, baseY, objectPaint)
                    canvas.drawRect(w * 0.84f, baseY - h * 0.02f, w * 0.88f, baseY, objectPaint)
                    textPaint.textSize = w * 0.04f
                    canvas.drawText("💪", cx, h * 0.08f, textPaint)
                }
            }
        }

        private fun darken(color: Int, factor: Float): Int {
            val r = (Color.red(color) * factor).toInt()
            val g = (Color.green(color) * factor).toInt()
            val b = (Color.blue(color) * factor).toInt()
            return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
    }
}

package com.intelligame.huntix

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.ui.OutdoorWorldActivity

class OutdoorSetupActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private var selectedRadius = "250"
    private var selectedEggs = "10"
    private var selectedGyms = "true"
    private var selectedDifficulty = "normale"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("outdoor_setup", MODE_PRIVATE)
        selectedRadius = prefs.getInt("radius", 250).toString()
        selectedEggs = prefs.getInt("eggs", 10).toString()
        selectedGyms = prefs.getBoolean("gyms", true).toString()
        selectedDifficulty = prefs.getString("difficulty", "normale") ?: "normale"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0620.toInt())
            setPadding(UiKit.dp(this@OutdoorSetupActivity, 24), UiKit.dp(this@OutdoorSetupActivity, 24),
                UiKit.dp(this@OutdoorSetupActivity, 24), UiKit.dp(this@OutdoorSetupActivity, 24))
        }

        root.addView(UiKit.title(this, "Imposta Caccia Outdoor", "⚙️"))

        fun addLabel(text: String) {
            root.addView(TextView(this@OutdoorSetupActivity).apply {
                this.text = text; textSize = 16f; setTextColor(0xFFA78BFA.toInt())
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = UiKit.dp(this@OutdoorSetupActivity, 20)
                    bottomMargin = UiKit.dp(this@OutdoorSetupActivity, 8)
                }
            })
        }

        fun addOptionRow(
            options: List<Pair<String, String>>,
            current: String,
            onSelected: (String) -> Unit
        ) {
            val row = LinearLayout(this@OutdoorSetupActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            options.forEach { (display, value) ->
                val btn = UiKit.button(this@OutdoorSetupActivity, display, UiKit.PURPLE) {
                    onSelected(value)
                    refreshHighlights(row, value)
                }
                btn.tag = value
                btn.alpha = if (value == current) 1f else 0.5f
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = UiKit.dp(this@OutdoorSetupActivity, 6)
                btn.layoutParams = lp
                row.addView(btn)
            }
            root.addView(row)
        }

        addLabel("Raggio zona")
        addOptionRow(
            listOf("100m" to "100", "250m" to "250", "500m" to "500", "1 km" to "1000"),
            selectedRadius
        ) { selectedRadius = it }

        addLabel("Numero uova")
        addOptionRow(
            listOf("5" to "5", "10" to "10", "15" to "15", "20" to "20"),
            selectedEggs
        ) { selectedEggs = it }

        addLabel("Includi palestre/POI")
        addOptionRow(
            listOf("Sì" to "true", "No" to "false"),
            selectedGyms
        ) { selectedGyms = it }

        addLabel("Difficoltà")
        addOptionRow(
            listOf("Facile" to "facile", "Normale" to "normale", "Difficile" to "difficile"),
            selectedDifficulty
        ) { selectedDifficulty = it }

        val startBtn = UiKit.button(this, "🚀 Avvia Caccia", UiKit.GREEN) {
            prefs.edit()
                .putInt("radius", selectedRadius.toInt())
                .putInt("eggs", selectedEggs.toInt())
                .putBoolean("gyms", selectedGyms.toBoolean())
                .putString("difficulty", selectedDifficulty)
                .apply()
            startActivity(Intent(this, OutdoorWorldActivity::class.java))
        }
        startBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(this@OutdoorSetupActivity, 24) }
        root.addView(startBtn)

        val previewBtn = UiKit.button(this, "🗺️ Anteprima Mappa", UiKit.PURPLE) {
            startActivity(Intent(this, OutdoorWorldActivity::class.java))
        }
        previewBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(this@OutdoorSetupActivity, 8) }
        root.addView(previewBtn)

        setContentView(UiKit.scroll(this, *root.children.map { it }.toTypedArray()))
    }

    private fun refreshHighlights(row: LinearLayout, selected: String) {
        for (i in 0 until row.childCount) {
            val btn = row.getChildAt(i)
            val btnTag = btn.tag as? String ?: continue
            btn.alpha = if (btnTag == selected) 1f else 0.5f
        }
    }
}
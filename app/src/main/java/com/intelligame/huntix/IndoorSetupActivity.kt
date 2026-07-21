package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class IndoorSetupActivity : AppCompatActivity() {

    private val MAX_PLAYERS = 4
    private var turnMode = "sequential"
    private var setupMode = "manual"
    private var autoEggCount = 4
    private var trapEggCount = 0
    private var penaltySecs = 30
    private lateinit var nameBoxes: LinearLayout
    private lateinit var autoContainer: LinearLayout
    private lateinit var modeManualBtn: View
    private lateinit var modeAutoBtn: View
    private lateinit var lblEggs: TextView
    private lateinit var lblTraps: TextView
    private lateinit var lblPenalty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val prefs = getSharedPreferences("players_setup", MODE_PRIVATE)

        nameBoxes = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        for (i in 1..MAX_PLAYERS) {
            nameBoxes.addView(EditText(c).apply {
                hint = "Giocatore $i"
                setHintTextColor(0xFF555577.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f; maxLines = 1
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 10).toFloat()
                    setColor(0xFF12112A.toInt())
                    setStroke(1, 0xFF334466.toInt())
                }
                setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiKit.dp(c, 10) }
                setText(prefs.getString("player_$i", ""))
                tag = "player_$i"
            })
        }

        val turnRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(c, 8), 0, 0)
        }
        listOf(
            "Sequenziale" to "sequential",
            "Alternato" to "alternated",
            "Casuale" to "random"
        ).forEach { (label, mode) ->
            val btn = UiKit.button(c, label, UiKit.PURPLE) {
                turnMode = mode
                refreshTurnHighlight()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = UiKit.dp(c, 8)
                }
                tag = "turn_$mode"
            }
            turnRow.addView(btn)
        }

        autoContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(c, 12) }
        }

        fun addSlider(label: String, min: Int, max: Int, def: Int, onChange: (Int) -> Unit): TextView {
            val lbl = TextView(c).apply {
                text = "$label: $def"; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
            }
            autoContainer.addView(lbl)
            autoContainer.addView(SeekBar(c).apply {
                this.max = max - min; setProgress(def - min)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = p + min; lbl.text = "$label: $v"; onChange(v)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
            return lbl
        }

        lblEggs = addSlider("Uova", 2, 10, 4) { autoEggCount = it }
        lblTraps = addSlider("Trappole", 0, 5, 0) { trapEggCount = it }
        lblPenalty = addSlider("Penalità (sec)", 10, 60, 30) { penaltySecs = it }

        modeManualBtn = UiKit.button(c, "✋ Manuale", UiKit.ACCENT) { setupMode = "manual"; refreshModeHighlight() }
        modeAutoBtn = UiKit.button(c, "🤖 Automatico", UiKit.ACCENT) { setupMode = "auto"; refreshModeHighlight() }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Configura Partita Indoor", "🎮"),
            UiKit.subtitle(c, "Nomina i giocatori e scegli le impostazioni."),
            nameBoxes,
            UiKit.section(c, "Ordine Turni"), turnRow,
            UiKit.section(c, "Posizionamento Uova"),
            modeManualBtn, modeAutoBtn, autoContainer,
            UiKit.button(c, "✅ Avvia Partita", UiKit.GREEN) {
                val names = (1..MAX_PLAYERS).mapNotNull { i ->
                    (nameBoxes.findViewWithTag<EditText>("player_$i"))
                        ?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                }
                if (names.isEmpty()) {
                    Toast.makeText(c, "Inserisci almeno un giocatore", Toast.LENGTH_SHORT).show()
                    return@button
                }
                prefs.edit().apply {
                    names.forEachIndexed { idx, n -> putString("player_${idx + 1}", n) }
                    apply()
                }
                val intent = Intent(c, MainActivity::class.java).apply {
                    putExtra("turn_mode", turnMode)
                    putExtra("setup_mode", setupMode)
                    putExtra("auto_egg_count", autoEggCount)
                    putExtra("trap_egg_count", trapEggCount)
                    putExtra("penalty_secs", penaltySecs)
                    putStringArrayListExtra("player_names", ArrayList(names))
                }
                startActivity(intent)
            }
        )

        refreshTurnHighlight()
        refreshModeHighlight()
        setContentView(content)
    }

    private fun refreshTurnHighlight() {
        listOf("sequential", "alternated", "random").forEach { mode ->
            nameBoxes.findViewWithTag<TextView>("turn_$mode")?.alpha = if (turnMode == mode) 1f else 0.4f
        }
    }

    private fun refreshModeHighlight() {
        modeManualBtn.alpha = if (setupMode == "manual") 1f else 0.4f
        modeAutoBtn.alpha = if (setupMode == "auto") 1f else 0.4f
        autoContainer.visibility = if (setupMode == "auto") View.VISIBLE else View.GONE
    }
}

package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EggSetupModeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SETUP_MODE = "setup_mode"
        const val EXTRA_AUTO_EGG_COUNT = "auto_egg_count"
        const val EXTRA_TRAP_EGG_COUNT = "trap_egg_count"
        const val EXTRA_PENALTY_SECS = "penalty_secs"
        const val MODE_MANUAL = "manual"
        const val MODE_AUTO = "auto"
    }

    private var selectedMode = MODE_MANUAL
    private var autoEggCount = 4
    private var trapEggCount = 0
    private var penaltySecs = 30
    private lateinit var modeManualBtn: View
    private lateinit var modeAutoBtn: View
    private lateinit var autoContainer: LinearLayout
    private lateinit var lblEggs: TextView
    private lateinit var lblTraps: TextView
    private lateinit var lblPenalty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0D0620.toInt())
            setPadding(UiKit.dp(this@EggSetupModeActivity, 24), UiKit.dp(this@EggSetupModeActivity, 40),
                UiKit.dp(this@EggSetupModeActivity, 24), UiKit.dp(this@EggSetupModeActivity, 24))
        }

        root.addView(TextView(this).apply {
            text = "🥚"; textSize = 48f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Configura Uova"; textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(this@EggSetupModeActivity, 8), 0, UiKit.dp(this@EggSetupModeActivity, 4))
        })
        root.addView(TextView(this).apply {
            text = "Scegli come posizionare le uova sul campo"; textSize = 13f
            setTextColor(0xFF6B5B95.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(this@EggSetupModeActivity, 24))
        })

        modeManualBtn = UiKit.button(this, "✋ Manuale", UiKit.PURPLE) {
            selectedMode = MODE_MANUAL; refreshModeHighlight()
        }
        root.addView(modeManualBtn)

        modeAutoBtn = UiKit.button(this, "🤖 Automatico", UiKit.PURPLE) {
            selectedMode = MODE_AUTO; refreshModeHighlight()
        }
        root.addView(modeAutoBtn)

        autoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(this@EggSetupModeActivity, 16) }
        }

        fun addSlider(label: String, min: Int, max: Int, def: Int, onChange: (Int) -> Unit): TextView {
            val lbl = TextView(this).apply {
                text = "$label: $def"; textSize = 15f; setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(this@EggSetupModeActivity, 8) }
            }
            autoContainer.addView(lbl)

            autoContainer.addView(SeekBar(this).apply {
                this.max = max - min
                setProgress(def - min)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = p + min
                        lbl.text = "$label: $v"
                        onChange(v)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
            autoContainer.addView(TextView(this).apply {
                text = "($min - $max)"; textSize = 12f; setTextColor(0xFF6B5B95.toInt())
            })
            return lbl
        }

        lblEggs = addSlider("Uova automatiche", 2, 10, 4) { autoEggCount = it }
        lblTraps = addSlider("Uova trappola", 0, 5, 0) { trapEggCount = it }
        lblPenalty = addSlider("Penalità (sec)", 10, 60, 30) { penaltySecs = it }

        root.addView(autoContainer)

        val confirmBtn = UiKit.button(this, "✅ Conferma", UiKit.GREEN) {
            val data = Intent().apply {
                putExtra(EXTRA_SETUP_MODE, selectedMode)
                putExtra(EXTRA_AUTO_EGG_COUNT, autoEggCount)
                putExtra(EXTRA_TRAP_EGG_COUNT, trapEggCount)
                putExtra(EXTRA_PENALTY_SECS, penaltySecs)
            }
            setResult(RESULT_OK, data)
            finish()
        }
        confirmBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(this@EggSetupModeActivity, 16) }
        root.addView(confirmBtn)

        setContentView(root)
    }

    private fun refreshModeHighlight() {
        modeManualBtn.alpha = if (selectedMode == MODE_MANUAL) 1f else 0.4f
        modeAutoBtn.alpha = if (selectedMode == MODE_AUTO) 1f else 0.4f
        autoContainer.visibility = if (selectedMode == MODE_AUTO) View.VISIBLE else View.GONE
    }
}
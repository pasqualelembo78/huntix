package com.intelligame.huntix

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameModeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TURN_MODE = "turn_mode"
        const val MODE_SEQUENTIAL = "sequential"
        const val MODE_ALTERNATED = "alternated"
        const val MODE_RANDOM = "random"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0D0620.toInt())
            setPadding(UiKit.dp(this@GameModeActivity, 24), UiKit.dp(this@GameModeActivity, 40),
                UiKit.dp(this@GameModeActivity, 24), UiKit.dp(this@GameModeActivity, 24))
        }

        root.addView(TextView(this).apply {
            text = "⚡"; textSize = 48f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Modalità di Gioco"; textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(this@GameModeActivity, 8), 0, UiKit.dp(this@GameModeActivity, 4))
        })
        root.addView(TextView(this).apply {
            text = "Scegli come si svolgono i turni"; textSize = 13f
            setTextColor(0xFF6B5B95.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(this@GameModeActivity, 24))
        })

        data class ModeOption(
            val label: String, val mode: String, val desc: String, val emoji: String
        )

        listOf(
            ModeOption("Sequenziale", MODE_SEQUENTIAL, "Un giocatore alla volta, in ordine", "⚡"),
            ModeOption("Alternato", MODE_ALTERNATED, "Turni alternati tra i giocatori", "🔄"),
            ModeOption("Casuale", MODE_RANDOM, "Ordine completamente casuale", "🎲")
        ).forEach { opt ->
            val card = UiKit.card(this,
                TextView(this).apply {
                    text = "${opt.emoji}  ${opt.label}"
                    textSize = 18f; setTextColor(0xFFFFFFFF.toInt())
                    paint.isFakeBoldText = true
                },
                TextView(this).apply {
                    text = opt.desc; textSize = 13f
                    setTextColor(0xFFA78BFA.toInt())
                    setPadding(0, UiKit.dp(this@GameModeActivity, 4), 0, 0)
                }
            )
            card.isClickable = true; card.isFocusable = true
            card.setOnClickListener {
                val data = Intent().apply { putExtra(EXTRA_TURN_MODE, opt.mode) }
                setResult(RESULT_OK, data)
                finish()
            }
            root.addView(card)
        }

        setContentView(root)
    }
}
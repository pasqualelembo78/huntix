package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.CharacterItem
import com.intelligame.huntix.reallife.RealLifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RealLifeActivity — Fase A del layer "Real Life".
 * Lista i personaggi dal backend (GET /characters) e apre la chat su tap.
 * Vedi narrazione.txt (Fase A: RealLifeActivity -> /characters + /chat).
 */
class RealLifeActivity : BaseNavActivity() {
    override fun activeTab() = ""

    private lateinit var chipRow: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView

    private var allCharacters: List<CharacterItem> = emptyList()
    private var currentCategory: String = "Tutti"
    private val categoryColors = arrayOf("#A78BFA", "#00FF88", "#FF6F00", "#E91E63", "#42A5F5", "#FFCA28")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        chipRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        (chipRow.layoutParams as? LinearLayout.LayoutParams)?.apply { bottomMargin = UiKit.dp(c, 8) }

        listContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }

        statusText = TextView(c).apply {
            text = "Caricamento personaggi…"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 8), 0, 0)
        }

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        root.addView(UiKit.title(c, "Real Life", "\uD83D\uDC65"))
        root.addView(UiKit.subtitle(c,
            "Un mondo vivo sopra il gioco: parla con persone vere guidate da intelligenza artificiale."))
        root.addView(chipRow)
        root.addView(statusText)
        root.addView(listContainer)

        val scroll = android.widget.ScrollView(c).apply {
            setBackgroundColor(Color.parseColor(UiKit.BG))
            addView(root)
        }
        setContentView(scroll)

        loadCharacters()
    }

    private fun loadCharacters() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RealLifeClient.getCharacters(null) }
            result.onSuccess { chars ->
                allCharacters = chars
                if (chars.isEmpty()) {
                    statusText.text = "Nessun personaggio disponibile al momento."
                } else {
                    statusText.visibility = View.GONE
                    buildChips(chars)
                    renderList()
                }
            }.onFailure {
                statusText.text = "Errore di rete: ${it.message}\nVerifica che il backend sia avviato su :5100."
            }
        }
    }

    private fun buildChips(chars: List<CharacterItem>) {
        chipRow.removeAllViews()
        val cats = mutableListOf("Tutti")
        chars.map { it.category }.distinct().forEach { cats.add(it) }
        cats.forEachIndexed { idx, cat ->
            val color = if (cat == currentCategory) UiKit.ACCENT else categoryColors[idx % categoryColors.size]
            val chip = TextView(this).apply {
                text = cat.replaceFirstChar { it.uppercase() }
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@RealLifeActivity, 14).toFloat()
                    setColor(Color.parseColor(if (cat == currentCategory) "#2A1B4A" else "#1A1030"))
                    setStroke(2, Color.parseColor(color))
                }
                setPadding(UiKit.dp(this@RealLifeActivity, 14), UiKit.dp(this@RealLifeActivity, 8),
                    UiKit.dp(this@RealLifeActivity, 14), UiKit.dp(this@RealLifeActivity, 8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = UiKit.dp(this@RealLifeActivity, 8) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentCategory = cat
                    buildChips(allCharacters)
                    renderList()
                }
            }
            chipRow.addView(chip)
        }
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val filtered = if (currentCategory == "Tutti") {
            allCharacters
        } else {
            allCharacters.filter { it.category == currentCategory }
        }
        if (filtered.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Nessun personaggio in questa categoria."
                textSize = 13f
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                setPadding(0, UiKit.dp(this@RealLifeActivity, 8), 0, 0)
            })
            return
        }
        filtered.forEach { char -> listContainer.addView(characterRow(char)) }
    }

    private fun characterRow(char: CharacterItem): LinearLayout {
        val c = this
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 12).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
            }
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 12), UiKit.dp(c, 12), UiKit.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 8) }
            isClickable = true
            isFocusable = true

            val emoji = (char.avatar ?: "🙂").takeIf { it.length <= 2 } ?: "🙂"
            addView(TextView(c).apply {
                text = emoji
                textSize = 28f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(c, 44), UiKit.dp(c, 44)).apply {
                    rightMargin = UiKit.dp(c, 10)
                }
            })

            val info = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(c).apply {
                text = char.name
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            val sub = buildString {
                char.role?.let { append(it) }
                char.description?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it.take(60))
                }
            }
            if (sub.isNotBlank()) {
                info.addView(TextView(c).apply {
                    text = sub
                    textSize = 11f
                    setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 2), 0, 0)
                })
            }
            char.intimacyLabel?.takeIf { it.isNotBlank() }?.let { lbl ->
                info.addView(TextView(c).apply {
                    text = "💞 $lbl"
                    textSize = 10f
                    setTextColor(Color.parseColor(UiKit.GREEN))
                    setPadding(0, UiKit.dp(c, 2), 0, 0)
                })
            }
            addView(info)

            addView(TextView(c).apply {
                text = "▶"
                textSize = 16f
                setTextColor(Color.parseColor(UiKit.ACCENT))
                setPadding(UiKit.dp(c, 6), 0, 0, 0)
            })

            setOnClickListener {
                startActivity(Intent(c, RealLifeChatActivity::class.java).apply {
                    putExtra("CHAR_ID", char.id)
                    putExtra("CHAR_NAME", char.name)
                    putExtra("CHAR_AVATAR", (char.avatar ?: "🙂").takeIf { it.length <= 2 } ?: "🙂")
                })
            }
        }
    }
}

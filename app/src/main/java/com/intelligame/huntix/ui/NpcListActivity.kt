package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.CharacterItem
import com.intelligame.huntix.reallife.RealLifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NpcListActivity — Lista completa di tutti i personaggi/NPC del mondo Real Life.
 * Accessibile dal hub (RealLifeActivity) → "Chat NPC".
 */
class NpcListActivity : AppCompatActivity() {

    private lateinit var chipRow: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView
    private var allCharacters: List<CharacterItem> = emptyList()
    private var currentCategory: String = "Tutti"
    private val categoryColors = arrayOf("#A78BFA", "#00FF88", "#FF6F00", "#E91E63", "#42A5F5", "#FFCA28")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            text = "👥  Personaggi"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        chipRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 8), UiKit.dp(c, 14), UiKit.dp(c, 8))
        }
        listContainer = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        statusText = TextView(c).apply {
            text = "Caricamento…"; textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 8), UiKit.dp(c, 14), 0)
        }

        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(topBar)
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
                    statusText.text = "Nessun personaggio disponibile."
                } else {
                    statusText.visibility = View.GONE
                    buildChips(chars)
                    renderList()
                }
            }.onFailure {
                statusText.text = "Errore: ${it.message}"
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
                textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@NpcListActivity, 14).toFloat()
                    setColor(Color.parseColor(if (cat == currentCategory) "#2A1B4A" else "#1A1030"))
                    setStroke(2, Color.parseColor(color))
                }
                setPadding(UiKit.dp(this@NpcListActivity, 14), UiKit.dp(this@NpcListActivity, 8),
                    UiKit.dp(this@NpcListActivity, 14), UiKit.dp(this@NpcListActivity, 8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = UiKit.dp(this@NpcListActivity, 8) }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    currentCategory = cat; buildChips(allCharacters); renderList()
                }
            }
            chipRow.addView(chip)
        }
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val filtered = if (currentCategory == "Tutti") allCharacters
        else allCharacters.filter { it.category == currentCategory }

        if (filtered.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Nessun personaggio in questa categoria."
                textSize = 13f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                setPadding(UiKit.dp(this@NpcListActivity, 14), UiKit.dp(this@NpcListActivity, 8), 0, 0)
            })
            return
        }

        filtered.forEach { char ->
            val emoji = (char.avatar ?: "🙂").takeIf { it.length <= 2 } ?: "🙂"
            listContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@NpcListActivity, 12).toFloat()
                    setColor(Color.parseColor(UiKit.BG_CARD))
                }
                setPadding(UiKit.dp(this@NpcListActivity, 12), UiKit.dp(this@NpcListActivity, 12),
                    UiKit.dp(this@NpcListActivity, 12), UiKit.dp(this@NpcListActivity, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = UiKit.dp(this@NpcListActivity, 4)
                    marginStart = UiKit.dp(this@NpcListActivity, 14); marginEnd = UiKit.dp(this@NpcListActivity, 14)
                }
                isClickable = true; isFocusable = true

                addView(TextView(this@NpcListActivity).apply {
                    text = emoji; textSize = 28f; gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@NpcListActivity, 44), UiKit.dp(this@NpcListActivity, 44))
                })

                val info = LinearLayout(this@NpcListActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(UiKit.dp(this@NpcListActivity, 10), 0, 0, 0)
                }
                info.addView(TextView(this@NpcListActivity).apply {
                    text = char.name; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                })
                val sub = buildString {
                    char.role?.let { append(it) }
                    char.description?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · "); append(it.take(60))
                    }
                }
                if (sub.isNotBlank()) {
                    info.addView(TextView(this@NpcListActivity).apply {
                        text = sub; textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                        setPadding(0, UiKit.dp(this@NpcListActivity, 2), 0, 0)
                    })
                }
                char.intimacyLabel?.takeIf { it.isNotBlank() }?.let { lbl ->
                    info.addView(TextView(this@NpcListActivity).apply {
                        text = "💞 $lbl"; textSize = 10f; setTextColor(Color.parseColor(UiKit.GREEN))
                        setPadding(0, UiKit.dp(this@NpcListActivity, 2), 0, 0)
                    })
                }
                addView(info)
                addView(TextView(this@NpcListActivity).apply {
                    text = "▶"; textSize = 16f; setTextColor(Color.parseColor(UiKit.ACCENT))
                    setPadding(UiKit.dp(this@NpcListActivity, 6), 0, 0, 0)
                })

                setOnClickListener {
                    startActivity(Intent(this@NpcListActivity, RealLifeChatActivity::class.java).apply {
                        putExtra("CHAR_ID", char.id)
                        putExtra("CHAR_NAME", char.name)
                        putExtra("CHAR_AVATAR", emoji)
                        putExtra("CHAR_TAGS", ArrayList(char.tags ?: emptyList()))
                    })
                }
            })
        }
    }
}

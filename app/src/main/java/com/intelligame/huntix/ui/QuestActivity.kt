package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.gamification.QuestManager
import com.intelligame.huntix.gamification.QuestManager.Quest
import com.intelligame.huntix.BaseNavActivity

class QuestActivity : BaseNavActivity() {

    private lateinit var dailyContainer: LinearLayout
    private lateinit var weeklyContainer: LinearLayout
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(40))
        }
        scroll.addView(root)

        // Header
        root.addView(buildRow(
            TextView(this).apply { text = "← Torna"; textSize = 14f; setTextColor(Color.parseColor("#666699")); setOnClickListener { finish() } }
        ))
        root.addView(TextView(this).apply {
            text = "🎯 Missioni"; textSize = 24f; setTextColor(Color.parseColor("#E0E0FF"))
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(24))
        })

        // Daily section
        root.addView(sectionHeader("📅 Missioni Giornaliere", "#00B4FF"))
        dailyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(dailyContainer)

        // Weekly section
        root.addView(sectionHeader("📆 Missioni Settimanali", "#FF9800").also { it.setPadding(0, dp(24), 0, dp(8)) })
        weeklyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(weeklyContainer)

        loadingText = TextView(this).apply { text = "Caricamento missioni..."; textSize = 13f; setTextColor(Color.GRAY); gravity = Gravity.CENTER }
        root.addView(loadingText)

        setContentView(scroll)
        loadQuests()
    }

    private fun loadQuests() {
        QuestManager.ensureDailyQuests { daily ->
            QuestManager.ensureWeeklyQuests { weekly ->
                runOnUiThread {
                    loadingText.visibility = android.view.View.GONE
                    dailyContainer.removeAllViews()
                    weeklyContainer.removeAllViews()
                    daily.forEach { dailyContainer.addView(buildQuestCard(it)) }
                    weekly.forEach { weeklyContainer.addView(buildQuestCard(it)) }
                    if (daily.isEmpty()) dailyContainer.addView(emptyText("Nessuna missione disponibile"))
                    if (weekly.isEmpty()) weeklyContainer.addView(emptyText("Nessuna missione disponibile"))
                }
            }
        }
    }

    private fun buildQuestCard(quest: Quest): CardView {
        val bgColor = when {
            quest.claimed    -> "#1A2A1A"
            quest.completed  -> "#1A3A1A"
            quest.isExpired  -> "#1A1A1A"
            else             -> "#FFFFFF"
        }
        val card = CardView(this).apply {
            radius = dp(12).toFloat(); cardElevation = dp(4).toFloat()
            setCardBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Title row
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = quest.emoji; textSize = 22f
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
        }
        titleCol.addView(TextView(this).apply {
            text = if (quest.claimed) "✅ ${quest.title}" else quest.title
            textSize = 15f
            setTextColor(if (quest.claimed) Color.GRAY else Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = quest.description; textSize = 12f; setTextColor(Color.parseColor("#9999CC"))
        })
        titleRow.addView(titleCol)

        // Reward badge
        titleRow.addView(LinearLayout(this).apply {
            val ctx = context
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(TextView(ctx).apply { text = "+${quest.rewardXp}XP"; textSize = 11f; setTextColor(Color.parseColor("#E0E0FF")); gravity = Gravity.END })
            if (quest.rewardGems > 0) addView(TextView(ctx).apply { text = "+${quest.rewardGems}💎"; textSize = 11f; setTextColor(Color.parseColor("#00E5FF")); gravity = Gravity.END })
        })

        col.addView(titleRow)

        // Progress bar
        if (!quest.claimed) {
            col.addView(TextView(this).apply {
                text = "${quest.progress}/${quest.target}"
                textSize = 11f; setTextColor(Color.parseColor("#666699")); setPadding(0, dp(6), 0, dp(4))
            })
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = quest.target; progress = quest.progress
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                progressDrawable = android.graphics.drawable.ClipDrawable(
                    android.graphics.drawable.ColorDrawable(if (quest.completed) Color.parseColor("#00FF88") else Color.parseColor("#00B4FF")),
                    Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL
                )
            }
            col.addView(pb)

            if (quest.canClaim) {
                val claimBtn = Button(this).apply {
                    text = "🎁 RITIRA RICOMPENSA"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#00FF88"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).also { it.topMargin = dp(10) }
                    setOnClickListener {
                        QuestManager.claimReward(quest.id) {
                            runOnUiThread { loadQuests() }
                        }
                    }
                }
                col.addView(claimBtn)
            }
        }

        // Expiry
        if (!quest.claimed) {
            val remainingMs = quest.expiresAt - System.currentTimeMillis()
            val remainingH = remainingMs / 3_600_000
            col.addView(TextView(this).apply {
                text = if (remainingH > 0) "⏱ Scade tra ${remainingH}h" else "⏱ Scade presto"
                textSize = 10f; setTextColor(Color.parseColor("#666699")); setPadding(0, dp(4), 0, 0)
            })
        }

        card.addView(col)
        return card
    }

    private fun sectionHeader(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 16f; setTextColor(Color.parseColor(colorHex))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun emptyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(16))
    }

    private fun buildRow(vararg views: android.view.View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .also { it.bottomMargin = dp(8) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

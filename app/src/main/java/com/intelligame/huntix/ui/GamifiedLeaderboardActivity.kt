package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.gamification.GamifiedLeaderboardManager
import com.intelligame.huntix.gamification.GamifiedLeaderboardManager.LeaderboardType
import com.intelligame.huntix.gamification.GamifiedLeaderboardManager.LeaderboardEntry
import com.intelligame.huntix.gamification.GamifiedLeaderboardManager.TeamLeaderboardEntry
import com.intelligame.huntix.BaseNavActivity

class GamifiedLeaderboardActivity : BaseNavActivity() {

    private lateinit var contentContainer: LinearLayout
    private lateinit var myPositionLabel: TextView
    private var currentType = LeaderboardType.GLOBAL_XP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(56), dp(20), dp(40))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "← Torna"; textSize = 14f; setTextColor(Color.parseColor("#666699"))
            setOnClickListener { finish() }; setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "🏆 Classifiche"; textSize = 24f; setTextColor(Color.parseColor("#E0E0FF"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(16))
        })

        // Tab bar
        val scrollTabs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).also { it.bottomMargin = dp(16) }
        }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        LeaderboardType.values().forEach { type ->
            val tab = Button(this).apply {
                text = "${type.emoji} ${type.label}"; textSize = 11f; setTextColor(Color.WHITE)
                setBackgroundColor(if (type == currentType) Color.parseColor("#00E5FF") else Color.parseColor("#1A1A3A"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                    .also { it.marginEnd = dp(4) }
                setOnClickListener { currentType = type; loadLeaderboard() }
            }
            tabRow.addView(tab)
        }
        scrollTabs.addView(tabRow)
        root.addView(scrollTabs)

        myPositionLabel = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#E0E0FF")); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(16))
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }
        root.addView(myPositionLabel)

        contentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        root.addView(contentContainer)

        setContentView(scroll)
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        contentContainer.removeAllViews()
        myPositionLabel.text = "Caricamento..."
        contentContainer.addView(tv("Caricamento classifica...", 13f, "#888888").also { it.gravity = Gravity.CENTER })

        if (currentType == LeaderboardType.TEAMS) {
            GamifiedLeaderboardManager.loadTeamLeaderboard(50) { entries ->
                runOnUiThread {
                    contentContainer.removeAllViews()
                    myPositionLabel.text = "Top ${entries.size} Squadre"
                    entries.forEach { contentContainer.addView(buildTeamCard(it)) }
                    if (entries.isEmpty()) contentContainer.addView(tv("Nessuna squadra ancora.", 13f, "#888888").also { it.gravity = Gravity.CENTER })
                }
            }
        } else {
            GamifiedLeaderboardManager.loadLeaderboard(currentType) { entries, myEntry ->
                runOnUiThread {
                    contentContainer.removeAllViews()
                    myPositionLabel.text = if (myEntry != null)
                        "La tua posizione: #${myEntry.rank}  ${GamifiedLeaderboardManager.formatValue(currentType, myEntry.value)}"
                    else "Gioca per apparire in classifica!"
                    entries.forEach { contentContainer.addView(buildPlayerCard(it)) }
                    if (entries.isEmpty()) contentContainer.addView(tv("Nessun giocatore in classifica ancora.", 13f, "#888888").also { it.gravity = Gravity.CENTER })
                }
            }
        }
    }

    private fun buildPlayerCard(entry: LeaderboardEntry): CardView {
        val isMe = entry.isCurrentUser
        val bgColor = if (isMe) "#1A1A4A" else when (entry.rank) {
            1 -> "#2A1A00"; 2 -> "#1A1A1A"; 3 -> "#1A0A00"; else -> "#FFFFFF"
        }
        val card = CardView(this).apply {
            radius = dp(10).toFloat(); cardElevation = dp(if (isMe) 6 else 3).toFloat()
            setCardBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
                .also { it.bottomMargin = dp(6) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }

        val medal = when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" }
        row.addView(tv(medal, if (entry.rank <= 3) 22f else 15f, "#FFFFFF", bold = entry.rank <= 3).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
        }
        infoCol.addView(tv(
            if (isMe) "${entry.name} (Tu)" else entry.name,
            14f, if (isMe) "#E0E0FF" else "#FFFFFF", bold = isMe
        ))
        infoCol.addView(tv("Lv.${entry.level}", 11f, "#9999CC"))
        row.addView(infoCol)

        row.addView(tv(GamifiedLeaderboardManager.formatValue(currentType, entry.value), 13f, "#E0E0FF", bold = true))

        card.addView(row)
        return card
    }

    private fun buildTeamCard(entry: TeamLeaderboardEntry): CardView {
        val card = CardView(this).apply {
            radius = dp(10).toFloat(); cardElevation = dp(3).toFloat()
            setCardBackgroundColor(Color.parseColor(if (entry.rank <= 3) "#1A0D2A" else "#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
                .also { it.bottomMargin = dp(6) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        val medal = when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" }
        row.addView(tv(medal, if (entry.rank <= 3) 22f else 15f, "#FFFFFF", bold = entry.rank <= 3).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
        }
        col.addView(tv("[${entry.tag}] ${entry.teamName}", 14f, "#E0E0FF", bold = true))
        col.addView(tv("👑 ${entry.leaderName}  ·  👥 ${entry.memberCount}", 11f, "#9999CC"))
        row.addView(col)
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(tv("⚡ ${GamifiedLeaderboardManager.formatValue(LeaderboardType.TEAMS, entry.totalXp)}", 12f, "#E0E0FF", bold = true))
            addView(tv("🥚 ${entry.totalEggs}", 10f, "#9999CC"))
        })
        card.addView(row)
        return card
    }

    private fun tv(text: String, size: Float, colorHex: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(colorHex))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

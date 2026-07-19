package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.gamification.LiveEventManager
import com.intelligame.huntix.gamification.LiveEventManager.LiveEvent
import com.intelligame.huntix.BaseNavActivity

class LiveEventsActivity : BaseNavActivity() {

    private lateinit var eventsContainer: LinearLayout
    private lateinit var activeMultiplierLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

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
            text = "🎉 Eventi Live"; textSize = 24f; setTextColor(Color.parseColor("#FF9800"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(8))
        })

        activeMultiplierLabel = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#E0E0FF")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(activeMultiplierLabel)

        root.addView(sectionTv("⚡ Attivi Ora", "#00FF88"))
        eventsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(eventsContainer)
        root.addView(sectionTv("⏳ In Arrivo", "#00B4FF").also { it.setPadding(0, dp(20), 0, dp(8)) })

        val upcomingContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(upcomingContainer)

        setContentView(scroll)
        loadEvents(upcomingContainer)

        // Auto-refresh ogni 30 secondi per aggiornare timer
        timerRunnable = object : Runnable {
            override fun run() { loadEvents(upcomingContainer); handler.postDelayed(this, 30_000) }
        }
        handler.postDelayed(timerRunnable, 30_000)
    }

    private fun loadEvents(upcomingContainer: LinearLayout) {
        val events = LiveEventManager.getCurrentAndUpcomingEvents()
        val active = events.filter { it.isActive }
        val upcoming = events.filter { it.isUpcoming }

        runOnUiThread {
            eventsContainer.removeAllViews()
            upcomingContainer.removeAllViews()

            val xpMult = LiveEventManager.getActiveXpMultiplier()
            val spawnMult = LiveEventManager.getActiveSpawnMultiplier()
            val legendBonus = LiveEventManager.getActiveLegendaryBonus()

            activeMultiplierLabel.text = buildString {
                if (xpMult > 1) append("⚡ XP x${xpMult.toInt()}  ")
                if (spawnMult > 1) append("🥚 Spawn x${spawnMult.toInt()}  ")
                if (legendBonus > 0) append("⭐ Legg. +${(legendBonus * 100).toInt()}%  ")
                if (isEmpty()) append("Nessun bonus attivo al momento")
            }

            if (active.isEmpty()) {
                eventsContainer.addView(tv("Nessun evento attivo ora.\nTorna più tardi!", 13f, "#888888").also {
                    it.gravity = Gravity.CENTER; it.setPadding(0, dp(12), 0, dp(12))
                })
            } else {
                active.forEach { eventsContainer.addView(buildEventCard(it, active = true)) }
            }

            if (upcoming.isEmpty()) {
                upcomingContainer.addView(tv("Controlla la prossima settimana!", 13f, "#888888").also {
                    it.gravity = Gravity.CENTER; it.setPadding(0, dp(12), 0, dp(12))
                })
            } else {
                upcoming.forEach { upcomingContainer.addView(buildEventCard(it, active = false)) }
            }
        }
    }

    private fun buildEventCard(event: LiveEvent, active: Boolean): CardView {
        // Normalizza colore: accetta sia #RRGGBB che #AARRGGBB
        val hexBase = if (event.colorHex.length == 9) event.colorHex.substring(3) else event.colorHex.trimStart('#')
        val bgColor = if (active) "#22$hexBase" else "#FFFFFF"
        @Suppress("UNUSED_VARIABLE") val borderColor = if (active) "#FF$hexBase" else "#333366"

        val card = CardView(this).apply {
            radius = dp(14).toFloat(); cardElevation = if (active) dp(6).toFloat() else dp(3).toFloat()
            setCardBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        // Title row
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = event.emoji; textSize = 28f
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
        }
        titleCol.addView(tv(event.title, 17f, if (active) event.colorHex else "#FFFFFF", bold = true))
        titleCol.addView(tv(event.description, 12f, "#9999CC").also { it.setPadding(0, dp(2), 0, 0) })
        titleRow.addView(titleCol)

        // Badge LIVE or PRESTO
        titleRow.addView(TextView(this).apply {
            text = if (active) "● LIVE" else "PRESTO"
            textSize = 11f
            setTextColor(if (active) Color.parseColor("#00FF88") else Color.parseColor("#FF9800"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        })

        col.addView(titleRow)

        // Timer / countdown
        val timerText = if (active) {
            val rem = event.remainingMinutes
            if (rem > 60) "⏱ Finisce tra ${rem / 60}h ${rem % 60}min" else "⏱ Finisce tra ${rem}min"
        } else {
            val startsIn = (event.startsInMs / 60_000).toInt()
            if (startsIn > 60) "🕐 Inizia tra ${startsIn / 60}h ${startsIn % 60}min" else "🕐 Inizia tra ${startsIn}min"
        }
        col.addView(tv(timerText, 12f, "#9999CC").also { it.setPadding(0, dp(8), 0, 0) })

        // Bonus info
        val bonuses = buildList {
            if (event.xpMultiplier > 1) add("⚡ XP x${event.xpMultiplier.toInt()}")
            if (event.eggSpawnMultiplier > 1) add("🥚 Spawn x${event.eggSpawnMultiplier.toInt()}")
            if (event.legendaryChanceBonus > 0) add("⭐ Legg. +${(event.legendaryChanceBonus * 100).toInt()}%")
            if (event.rewardGems > 0) add("+${event.rewardGems} 💎 bonus")
        }
        if (bonuses.isNotEmpty()) {
            col.addView(tv(bonuses.joinToString("  ·  "), 11f, event.colorHex).also { it.setPadding(0, dp(4), 0, 0) })
        }

        // Progress bar per eventi attivi
        if (active) {
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = event.progressPercent
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6))
                    .also { it.topMargin = dp(10) }
            }
            col.addView(pb)
        }

        card.addView(col)
        return card
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(timerRunnable) }

    private fun sectionTv(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.parseColor(colorHex))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun tv(text: String, size: Float, colorHex: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(colorHex))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.JobDefs
import com.intelligame.huntix.reallife.JobType
import com.intelligame.huntix.reallife.Jobs
import com.intelligame.huntix.reallife.MoneyManager

/**
 * JobsActivity — hub per scegliere un lavoro.
 * Mostra le 3 card lavoro con descrizione, difficoltà e pay range.
 */
class JobsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }

        // Top bar
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        topBar.addView(TextView(c).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(c).apply {
            text = "💰  Lavori"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(c).apply {
            text = "💵 ${MoneyManager.getCash(c)}"; textSize = 14f
            setTextColor(Color.parseColor("#FFD86B")); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(topBar)

        // Subtitle
        root.addView(TextView(c).apply {
            text = "Scegli un lavoro e guadagna cash!"; textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 10), UiKit.dp(c, 14), UiKit.dp(c, 6))
        })

        // Job cards
        for (job in Jobs.ALL) {
            root.addView(jobCard(c, job))
        }

        // Stats
        root.addView(TextView(c).apply {
            text = "📊  Lavori completati: ${MoneyManager.getJobsDone(c)}  •  Totale guadagnato: \$${MoneyManager.getTotalEarned(c)}"
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 16), UiKit.dp(c, 14), UiKit.dp(c, 14))
            gravity = Gravity.CENTER
        })

        val scroll = android.widget.ScrollView(c).apply {
            addView(root)
        }
        setContentView(scroll)
    }

    private fun jobCard(c: android.content.Context, job: JobDefs): LinearLayout {
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 14).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(1, Color.parseColor("#2A2240"))
            }
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = UiKit.dp(c, 14); marginEnd = UiKit.dp(c, 14); bottomMargin = UiKit.dp(c, 10)
            }
        }

        // Header row
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(c).apply {
            text = job.emoji; textSize = 28f
        })
        val info = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(UiKit.dp(c, 10), 0, 0, 0)
        }
        info.addView(TextView(c).apply {
            text = job.name; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        info.addView(TextView(c).apply {
            text = "Difficoltà: ${job.difficulty}  •  \$${job.payMin}-\$${job.payMax}"
            textSize = 11f; setTextColor(Color.parseColor("#FFD86B"))
        })
        header.addView(info)
        card.addView(header)

        // Description
        card.addView(TextView(c).apply {
            text = job.desc; textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 8))
        })

        // Play button
        val playBtn = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(Color.parseColor(UiKit.ACCENT))
            }
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 10), UiKit.dp(c, 12), UiKit.dp(c, 10))
            isClickable = true; isFocusable = true
            setOnClickListener { startJob(job.type) }
        }
        playBtn.addView(TextView(c).apply {
            text = "▶  Lavora"; textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        card.addView(playBtn)

        return card
    }

    private fun startJob(type: JobType) {
        val cls = when (type) {
            JobType.DELIVERY -> DeliveryMiniGameActivity::class.java
            JobType.COOK -> CookMiniGameActivity::class.java
            JobType.TAXI -> TaxiMiniGameActivity::class.java
        }
        startActivity(Intent(this, cls))
    }
}

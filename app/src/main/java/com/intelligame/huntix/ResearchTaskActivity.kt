package com.intelligame.huntix

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.managers.ResearchTaskManager
import com.intelligame.huntix.managers.SavedManager

class ResearchTaskActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        ResearchTaskManager.refreshIfNeeded(c)
        val daily = ResearchTaskManager.getDailyTasks(c)
        val weekly = ResearchTaskManager.getWeeklyTasks(c)

        val children = mutableListOf<View>(
            UiKit.title(c, "Research Tasks", "📋"),
            UiKit.subtitle(c, "Completa missioni per guadagnare MVC e XP!")
        )

        // Daily tasks
        children.add(UiKit.section(c, "📆 Missioni Giornaliere (si aggiornano domani)"))
        if (daily.isEmpty()) {
            children.add(UiKit.comingSoon(c, "Nessuna missione", "Torna domani!"))
        } else {
            daily.forEach { task ->
                children.add(renderTask(c, task))
            }
        }

        // Weekly tasks
        children.add(UiKit.section(c, "📅 Missioni Settimanali (si aggiornano lunedì)"))
        if (weekly.isEmpty()) {
            children.add(UiKit.comingSoon(c, "Nessuna missione", "Torna lunedì!"))
        } else {
            weekly.forEach { task ->
                children.add(renderTask(c, task))
            }
        }

        // Stats
        children.add(UiKit.section(c, "Statistiche"))
        children.add(TextView(c).apply {
            text = "Totale completate: ${ResearchTaskManager.getCompletedCount(c)}"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 4), UiKit.dp(c, 16), UiKit.dp(c, 4))
        })

        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })

        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }

    private fun renderTask(c: android.content.Context, task: ResearchTaskManager.ResearchTask): View {
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 12).toFloat()
                setColor(Color.parseColor(
                    when {
                        task.claimed -> "#0D2818"
                        task.isComplete -> "#1A2A0D"
                        else -> "#12112A"
                    }
                ))
                setStroke(1, Color.parseColor(
                    when {
                        task.claimed -> "#00CC88"
                        task.isComplete -> "#FFD700"
                        else -> "#334466"
                    }
                ))
            }
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 8) }
        }

        // Title row
        val titleRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(c).apply {
            text = task.emoji; textSize = 20f
            setPadding(0, 0, UiKit.dp(c, 10), 0)
        })
        titleRow.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(c).apply {
                text = task.title; textSize = 14f; setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT_BOLD)
            })
            addView(TextView(c).apply {
                text = task.description; textSize = 11f
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                setPadding(0, UiKit.dp(c, 2), 0, 0)
            })
        })
        card.addView(titleRow)

        // Progress bar
        val progressBar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = (task.progressPct * 1000).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 6)
            ).apply { topMargin = UiKit.dp(c, 8) }
        }
        card.addView(progressBar)

        // Progress label + rewards
        val infoRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiKit.dp(c, 6), 0, 0)
        }
        infoRow.addView(TextView(c).apply {
            text = "${task.progressLabel}  ·  💰 ${task.rewardMvc} MVC  ·  ⚡ ${task.rewardXp} XP"
            textSize = 11f; setTextColor(Color.parseColor("#A78BFA"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (task.isComplete && !task.claimed) {
            infoRow.addView(UiKit.button(c, "✅ Riscatta", UiKit.GREEN) {
                val rewards = ResearchTaskManager.claimReward(c, task.id)
                if (rewards != null) {
                    SavedManager.addMvc(c, rewards.first.toDouble())
                    Toast.makeText(c, "💰 +${rewards.first} MVC · ⚡ +${rewards.second} XP", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            })
        } else if (task.claimed) {
            infoRow.addView(TextView(c).apply {
                text = "✅ Riscattata"; textSize = 11f
                setTextColor(Color.parseColor("#00CC88"))
            })
        }

        card.addView(infoRow)
        return card
    }
}

package com.intelligame.huntix

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.managers.DailyStreakManager
import com.intelligame.huntix.managers.SavedManager

class DailyStreakActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        DailyStreakManager.resetIfBroken(c)
        val status = DailyStreakManager.getStatus(c)

        val children = mutableListOf<View>(
            UiKit.title(c, "Calendario Streak", "📅"),
            UiKit.subtitle(c, "Accedi ogni giorno per ricompense sempre maggiori!")
        )

        // Current streak banner
        val streakBanner = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 12), UiKit.dp(c, 16), UiKit.dp(c, 12))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 12).toFloat()
                setColor(Color.parseColor("#1A1030"))
                setStroke(1, if (status.currentStreak > 0) Color.parseColor("#FFD700") else Color.parseColor("#333355"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(UiKit.dp(c, 16), 0, UiKit.dp(c, 16), UiKit.dp(c, 12)) }
        }

        streakBanner.addView(TextView(c).apply {
            text = "🔥 Streak: ${status.currentStreak} giorni"
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        streakBanner.addView(TextView(c).apply {
            text = "Record: ${status.longestStreak} giorni · Totale: ${status.totalClaims} riscatti"
            textSize = 11f; setTextColor(Color.parseColor("#A78BFA")); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, 0)
        })
        if (status.shieldsAvailable > 0) {
            streakBanner.addView(TextView(c).apply {
                text = "🛡️ Scudi disponibili: ${status.shieldsAvailable}"
                textSize = 12f; setTextColor(Color.parseColor("#00CC88")); gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(c, 4), 0, 0)
            })
        }
        children.add(streakBanner)

        // Claim button
        if (status.canClaimToday) {
            val currentStreak = status.currentStreak + 1
            val reward = DailyStreakManager.rewardForDay(currentStreak)
            children.add(UiKit.button(c, "✅ Riscatta Giorno $currentStreak — +$reward MVC", UiKit.GREEN) {
                val (claimed, rewardAmount) = DailyStreakManager.claimToday(c)
                if (claimed) {
                    SavedManager.addMvc(c, rewardAmount.toDouble())
                    val milestone = DailyStreakManager.isMilestone(currentStreak)
                    val msg = if (milestone) "🎉 MILESTONE! +$rewardAmount MVC!" else "+$rewardAmount MVC"
                    Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(c, "Già riscattato oggi!", Toast.LENGTH_SHORT).show()
                }
            })
        } else if (status.todayClaimed) {
            children.add(UiKit.button(c, "✅ Già riscattato oggi!", "#444") {})
        }

        // 30-day calendar grid
        val grid = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 16), 0, UiKit.dp(c, 16), 0)
        }

        val currentStreakDay = if (status.todayClaimed) status.currentStreak else status.currentStreak + 1
        var row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 6) }
        }

        for (day in 1..30) {
            if (day > 1 && (day - 1) % 7 == 0) {
                grid.addView(row)
                row = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiKit.dp(c, 6) }
                }
            }

            val claimed = day in status.claimedDays
            val isToday = day == currentStreakDay && !status.todayClaimed
            val isPast = day < currentStreakDay
            val isFuture = day > currentStreakDay
            val milestone = DailyStreakManager.isMilestone(day)
            val reward = DailyStreakManager.rewardForDay(day)

            val bgColor = when {
                claimed -> Color.parseColor("#00CC88")
                isToday -> Color.parseColor("#FFD700")
                milestone -> Color.parseColor("#A855F7")
                else -> Color.parseColor("#1A1030")
            }

            val cell = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(c, 52), 1f).apply {
                    marginEnd = UiKit.dp(c, 4)
                }
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 8).toFloat()
                    setColor(bgColor)
                    if (isToday) setStroke(2, Color.WHITE)
                }
                setPadding(UiKit.dp(c, 2), UiKit.dp(c, 4), UiKit.dp(c, 2), UiKit.dp(c, 4))
            }

            cell.addView(TextView(c).apply {
                text = when {
                    claimed -> "✅"
                    milestone -> "🏆"
                    day == 1 -> "1"
                    else -> "$day"
                }
                textSize = 11f; gravity = Gravity.CENTER
                setTextColor(if (claimed || isToday) Color.WHITE else Color.parseColor("#6B5B95"))
                setTypeface(Typeface.DEFAULT_BOLD)
            })

            cell.addView(TextView(c).apply {
                text = "${reward}"
                textSize = 8f; gravity = Gravity.CENTER
                setTextColor(if (claimed) Color.WHITE else Color.parseColor("#6B5B95"))
            })

            row.addView(cell)
        }
        grid.addView(row)
        children.add(grid)

        // Reward scale info
        children.add(UiKit.section(c, "scala ricompense"))
        for (day in listOf(1, 7, 14, 21, 30)) {
            val reward = DailyStreakManager.rewardForDay(day)
            children.add(TextView(c).apply {
                text = "${DailyStreakManager.dayLabel(day)}: +$reward MVC"
                textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                setPadding(UiKit.dp(c, 16), UiKit.dp(c, 2), UiKit.dp(c, 16), UiKit.dp(c, 2))
            })
        }

        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })

        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }
}

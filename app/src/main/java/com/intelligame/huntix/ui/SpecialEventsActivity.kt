package com.intelligame.huntix.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.cardview.widget.CardView
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.gamification.DailyEventManager
import com.intelligame.huntix.gamification.DailyEventRegistry
import com.intelligame.huntix.gamification.ElfHuntManager
import com.intelligame.huntix.gamification.SpecialEventManager

/**
 * SpecialEventsActivity — Calendario Eventi Speciali.
 *
 * Mostra la griglia dell'avvento (5x5) quando ElfHunt è attivo,
 * oppure un messaggio "nessun evento" altrimenti.
 */
class SpecialEventsActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private lateinit var calendarContainer: LinearLayout
    private lateinit var progressLabel: TextView
    private lateinit var mvcCounterLabel: TextView
    private lateinit var progressBarFill: View
    private lateinit var progressBarBg: View
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val cellAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val scroll = ScrollView(c).apply {
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(80))
        }
        scroll.addView(root)

        root.addView(TextView(c).apply {
            text = "\u2190 Indietro"; textSize = 14f
            setTextColor(Color.parseColor("#666699"))
            setOnClickListener { finish() }
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(UiKit.title(c, "Eventi Speciali", "\uD83C\uDF84"))

        val activeEvent = SpecialEventManager.getActiveSpecialEvent()

        if (activeEvent == null) {
            // No monthly event — show daily event if available
            val dailyEvent = DailyEventRegistry.getTodayEvent()
            if (dailyEvent != null) {
                setupDailyEventUI(root, dailyEvent)
            } else {
                root.addView(UiKit.card(c,
                    TextView(c).apply {
                        text = "Nessun evento speciale attivo al momento."
                        textSize = 14f; setTextColor(Color.parseColor("#6B5B95"))
                        gravity = Gravity.CENTER
                    },
                    TextView(c).apply {
                        text = "Torna durante un evento speciale per vincere ricompense esclusive!"
                        textSize = 12f; setTextColor(Color.parseColor("#4A3870"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, 0)
                    }
                ))
            }
            setContentView(scroll)
            return
        }

        when (activeEvent) {
            is SpecialEventManager.SpecialEvent.ElfHunt -> setupElfHuntUI(root)
        }

        setContentView(scroll)

        timerRunnable = object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed) {
                    refreshCalendar()
                    handler.postDelayed(this, 30_000)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, 30_000)
    }

    private fun setupElfHuntUI(root: LinearLayout) {
        val c = this
        val status = ElfHuntManager.getStatus(c)

        progressLabel = TextView(c).apply {
            text = "\uD83C\uDF81 ${status.totalDaysClaimed}/25 regali trovati"
            textSize = 14f; setTextColor(Color.parseColor("#00FF88"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(progressLabel)

        mvcCounterLabel = TextView(c).apply {
            text = "\uD83D\uDCB0 Totale: ${status.totalMvcEarned} MVC guadagnati"
            textSize = 12f; setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(mvcCounterLabel)

        val progressWrapper = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(LP_MW, dp(8)).also {
                it.bottomMargin = dp(16)
            }
        }
        progressBarBg = View(c).apply {
            layoutParams = FrameLayout.LayoutParams(LP_MW, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1A1030"))
            }
        }
        progressWrapper.addView(progressBarBg)

        progressBarFill = View(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#00FF88"))
            }
        }
        progressWrapper.addView(progressBarFill)
        root.addView(progressWrapper)

        root.addView(UiKit.section(c, "Calendario dell'Avvento"))

        calendarContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(calendarContainer)

        root.addView(TextView(c).apply {
            text = "Riscatta ogni giorno per ricompense crescenti!"
            textSize = 11f; setTextColor(Color.parseColor("#4A3870"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        })

        refreshCalendar()
    }

    private fun refreshCalendar() {
        val c = this
        val status = ElfHuntManager.getStatus(c)
        val todayDay = SpecialEventManager.getElfHuntDay()
        val rewards = ElfHuntManager.getRewardsCalendar()

        runOnUiThread {
            progressLabel.text = "\uD83C\uDF81 ${status.totalDaysClaimed}/25 regali trovati"
            mvcCounterLabel.text = "\uD83D\uDCB0 Totale: ${status.totalMvcEarned} MVC guadagnati"

            val progressPct = (status.totalDaysClaimed * 100 / 25).coerceIn(0, 100)
            progressBarFill.post {
                val parentWidth = (progressBarBg.parent as FrameLayout).width
                if (parentWidth > 0) {
                    progressBarFill.layoutParams = FrameLayout.LayoutParams(
                        parentWidth * progressPct / 100, dp(8)
                    )
                }
            }

            calendarContainer.removeAllViews()
            cellAnimators.forEach { it.cancel() }
            cellAnimators.clear()

            var row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                    it.bottomMargin = dp(6)
                }
            }

            for (day in 1..25) {
                if (day > 1 && (day - 1) % 5 == 0) {
                    calendarContainer.addView(row)
                    row = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                            it.bottomMargin = dp(6)
                        }
                    }
                }

                val reward = rewards[day - 1]
                val isClaimed = day in status.claimedDays
                val isToday = day == todayDay && status.canClaimToday
                val isPast = day < todayDay && !isClaimed
                val isFuture = day > todayDay

                val bgColor = when {
                    isClaimed -> "#1A3322"
                    isToday -> "#1A1030"
                    isPast -> "#2A1010"
                    else -> "#1A1028"
                }

                val cell = CardView(c).apply {
                    radius = dp(8).toFloat()
                    cardElevation = if (isToday) dp(4).toFloat() else dp(1).toFloat()
                    setCardBackgroundColor(Color.parseColor(bgColor))
                    layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).also {
                        it.marginEnd = dp(4)
                    }
                }

                val borderColor = when {
                    isClaimed -> Color.parseColor("#00FF88")
                    isToday -> Color.parseColor("#FFD700")
                    isPast -> Color.parseColor("#553333")
                    else -> Color.parseColor("#332244")
                }
                val borderWidth = if (isToday || isClaimed) dp(2) else dp(1)
                cell.post {
                    cell.foreground = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setStroke(borderWidth, borderColor)
                    }
                }

                val cellInner = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                }

                val iconText = when {
                    isClaimed -> "\u2705"
                    isToday -> "\uD83C\uDF81"
                    isPast -> "\u274C"
                    isFuture -> "\uD83D\uDD12"
                    else -> "$day"
                }
                cellInner.addView(TextView(c).apply {
                    text = iconText; textSize = 18f; gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                })

                cellInner.addView(TextView(c).apply {
                    text = "$day"; textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#6B5B95"))
                })

                if (reward.isMilestone && reward.item != null) {
                    cellInner.addView(TextView(c).apply {
                        text = ElfHuntManager.itemEmojiForDay(day); textSize = 10f; gravity = Gravity.CENTER
                    })
                }

                cell.addView(cellInner)

                if (isToday) {
                    val pulseAnim = ObjectAnimator.ofFloat(cell, "alpha", 0.6f, 1.0f).apply {
                        duration = 900
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                        interpolator = LinearInterpolator()
                    }
                    cellAnimators.add(pulseAnim)
                    pulseAnim.start()

                    cell.isClickable = true
                    cell.isFocusable = true
                    cell.setOnClickListener {
                        val intent = Intent(c, ElfHuntActivity::class.java)
                        intent.putExtra("day", todayDay)
                        startActivity(intent)
                    }
                } else if (isClaimed) {
                    cell.isClickable = true
                    cell.isFocusable = true
                    cell.setOnClickListener {
                        val mvc = ElfHuntManager.rewardMvcForDay(day)
                        val item = ElfHuntManager.itemForDay(day)
                        val msg = buildString {
                            append("Giorno $day: +$mvc MVC")
                            if (item != null) append("\nItem: $item")
                        }
                        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                row.addView(cell)
            }
            calendarContainer.addView(row)

            if (status.itemsReceived.isNotEmpty()) {
                val itemsSection = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(16), 0, 0)
                }
                itemsSection.addView(UiKit.section(c, "\uD83C\uDF81 Item Ottenuti"))
                for (item in status.itemsReceived) {
                    itemsSection.addView(TextView(c).apply {
                        text = "\u2B50 $item"
                        textSize = 13f; setTextColor(Color.parseColor("#FFD700"))
                        setPadding(dp(4), dp(2), 0, dp(2))
                    })
                }
                calendarContainer.addView(itemsSection)
            }
        }
    }

    private fun setupDailyEventUI(root: LinearLayout, event: com.intelligame.huntix.gamification.DailyEvent) {
        val c = this
        val isActive = DailyEventManager.isWithinEventWindow(event)
        val minsUntil = DailyEventManager.minutesUntilEvent()
        val minsLeft = DailyEventManager.minutesLeftInEvent()

        root.addView(TextView(c).apply {
            text = "${event.emoji} ${event.title}"
            textSize = 20f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        root.addView(TextView(c).apply {
            text = event.description
            textSize = 13f; setTextColor(Color.parseColor("#A78BFA"))
            setPadding(0, 0, 0, dp(12))
        })

        val statusCard = CardView(c).apply {
            radius = dp(12).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1030"))
            cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                it.bottomMargin = dp(12)
            }
        }
        val statusInner = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val (statusLabel, statusColor) = when {
            isActive -> "ATTIVO ORA" to "#00FF88"
            minsUntil in 0..30 -> "Inizia tra $minsUntil min" to "#FFD700"
            else -> "Oggi alle ${event.startHour}:${String.format("%02d", event.startMinute)}" to "#6B5B95"
        }

        statusInner.addView(TextView(c).apply {
            text = statusLabel
            textSize = 16f; setTextColor(Color.parseColor(statusColor))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        if (isActive) {
            statusInner.addView(TextView(c).apply {
                text = "Mancano $minsLeft minuti"
                textSize = 12f; setTextColor(Color.parseColor("#A78BFA"))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }

        statusCard.addView(statusInner)
        root.addView(statusCard)

        val dayNames = mapOf(
            java.util.Calendar.MONDAY to "Lunedì",
            java.util.Calendar.TUESDAY to "Martedì",
            java.util.Calendar.WEDNESDAY to "Mercoledì",
            java.util.Calendar.THURSDAY to "Giovedì",
            java.util.Calendar.FRIDAY to "Venerdì",
            java.util.Calendar.SATURDAY to "Sabato",
            java.util.Calendar.SUNDAY to "Domenica"
        )
        root.addView(TextView(c).apply {
            text = "Ogni ${dayNames[event.dayOfWeek] ?: ""} alle ${event.startHour}:${String.format("%02d", event.startMinute)}"
            textSize = 12f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, 0, 0, dp(8))
        })

        if (isActive && event.activityClass != null) {
            val playBtn = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(event.colorInt)
                }
                setPadding(dp(24), dp(14), dp(24), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                isClickable = true; isFocusable = true
                setOnClickListener {
                    startActivity(Intent(c, event.activityClass))
                }
            }
            playBtn.addView(TextView(c).apply {
                text = "${event.emoji} Gioca Ora!"
                textSize = 14f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
            root.addView(playBtn)
        }

        root.addView(UiKit.section(c, "\uD83D\uDCC5 Programma Settimanale"))

        val allEvents = DailyEventRegistry.getAllEvents()
        for (evt in allEvents) {
            val isToday = evt.dayOfWeek == java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            val dayLabel = dayNames[evt.dayOfWeek] ?: ""

            val eventRow = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor(if (isToday) "#1A1030" else "#0D0620"))
                }
                layoutParams = LinearLayout.LayoutParams(LP_MW, LP_WW).also {
                    it.bottomMargin = dp(4)
                }
            }

            eventRow.addView(TextView(c).apply {
                text = evt.emoji; textSize = 18f
                setPadding(0, 0, dp(8), 0)
            })

            val textCol = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f)
            }
            textCol.addView(TextView(c).apply {
                text = "$dayLabel — ${evt.title}"
                textSize = 13f
                setTextColor(if (isToday) Color.WHITE else Color.parseColor("#6B5B95"))
                typeface = if (isToday) Typeface.create("sans-serif-medium", Typeface.BOLD) else null
            })
            textCol.addView(TextView(c).apply {
                text = "${evt.startHour}:${String.format("%02d", evt.startMinute)} • ${evt.durationMinutes} min"
                textSize = 10f; setTextColor(Color.parseColor("#4A3870"))
            })
            eventRow.addView(textCol)

            if (isToday) {
                eventRow.addView(TextView(c).apply {
                    text = "OGGI"
                    textSize = 9f; setTextColor(event.colorInt)
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                })
            }

            root.addView(eventRow)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cellAnimators.forEach { it.cancel() }
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val LP_MW = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WW = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}

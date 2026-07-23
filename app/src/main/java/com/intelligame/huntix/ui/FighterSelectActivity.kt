package com.intelligame.huntix.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.battle3d.FighterDef
import com.intelligame.huntix.battle3d.FighterRegistry
import kotlin.random.Random

class FighterSelectActivity : BaseNavActivity() {
    private var selectedFighter: FighterDef? = null
    private lateinit var btnFight: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        buildUI()
    }

    private fun buildUI() {
        val root = FrameLayout(this)
        root.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#0A0022"), Color.parseColor("#1A1A3E"), Color.parseColor("#0A0022")))
        })
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(48), dp(20), dp(32))
        }
        scroll.addView(content); root.addView(scroll)

        val title = TextView(this).apply {
            text = "Scegli il tuo Lottatore"; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Il computer scegliera a random tra gli altri"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#AABBDD"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4); it.bottomMargin = dp(20) }
        }
        content.addView(subtitle)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        content.addView(grid)

        val fighters = FighterRegistry.ALL
        var row: LinearLayout? = null
        fighters.forEachIndexed { i, f ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
                }
                grid.addView(row)
            }
            row?.addView(fighterCard(f))
        }

        btnFight = Button(this).apply {
            text = "COMBATTI!"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#00E5FF")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).also { it.topMargin = dp(8) }
            setOnClickListener { startFight() }
            isEnabled = false; alpha = 0.4f
        }
        content.addView(btnFight)

        setContentView(root)
    }

    private fun fighterCard(f: FighterDef): FrameLayout {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(160), 1f).also { it.marginEnd = dp(6); it.marginStart = dp(6) }
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1A1A3E")); setStroke(2, Color.parseColor("#334466")) }
            setOnClickListener {
                selectedFighter = f
                btnFight.isEnabled = true; btnFight.alpha = 1f
                highlightSelection()
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        val avatar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(f.primaryColor) }
        }
        inner.addView(avatar)

        inner.addView(TextView(this).apply {
            text = f.name; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) }
        })
        inner.addView(TextView(this).apply {
            text = f.style.name; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#AABBDD"))
        })
        inner.addView(TextView(this).apply {
            text = f.specialName; textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#FFD700"))
        })

        card.addView(inner)
        card.tag = f
        return card
    }

    private fun highlightSelection() {
        val root = findViewById<FrameLayout>(android.R.id.content)
        val scroll = root.getChildAt(0) as? ScrollView ?: return
        val content = scroll.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            if (v is LinearLayout) {
                for (j in 0 until v.childCount) {
                    val card = v.getChildAt(j) as? FrameLayout ?: continue
                    val f = card.tag as? FighterDef ?: continue
                    val bg = card.background as? GradientDrawable ?: continue
                    bg.setStroke(if (f.id == selectedFighter?.id) 3 else 2,
                        if (f.id == selectedFighter?.id) Color.parseColor("#00E5FF") else Color.parseColor("#334466"))
                }
            }
        }
    }

    private fun startFight() {
        val player = selectedFighter ?: return
        val enemies = FighterRegistry.ALL.filter { it.id != player.id }
        val enemy = enemies[Random.nextInt(enemies.size)]
        startActivity(Intent(this, FightingGame3DActivity::class.java).apply {
            putExtra("player_id", player.id); putExtra("enemy_id", enemy.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

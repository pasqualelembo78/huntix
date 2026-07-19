package com.intelligame.huntix.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.gamification.AbilityManager
import com.intelligame.huntix.gamification.AbilityManager.Ability
import com.intelligame.huntix.BaseNavActivity

class AbilityActivity : BaseNavActivity() {

    private lateinit var abilitiesContainer: LinearLayout
    private var playerGems = 0
    private var playerLevel = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0F0F2A")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(56), dp(20), dp(40))
        }
        scroll.addView(root)

        root.addView(backBtn())
        root.addView(titleTv("⚡ Abilità", "#FFFF5722"))
        root.addView(tv("Usa le abilità durante la caccia per trovare più uova!", 13f, "#9999CC").also { it.gravity = Gravity.CENTER; it.setPadding(0, 0, 0, dp(20)) })

        abilitiesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(abilitiesContainer)
        root.addView(tv("Caricamento...", 13f, "#888888").also { it.gravity = Gravity.CENTER })

        setContentView(scroll)

        val profile = PlayerProfileManager.myProfile
        playerLevel = profile?.level ?: 1
        playerGems = profile?.gems ?: 0

        AbilityManager.loadAbilities(playerLevel) { abilities ->
            runOnUiThread {
                root.removeViewAt(root.childCount - 1) // remove loading
                abilities.forEach { ability -> abilitiesContainer.addView(buildAbilityCard(ability)) }
            }
        }
    }

    private fun buildAbilityCard(ability: Ability): CardView {
        val isLocked = !ability.isUnlocked
        val bgColor = when {
            isLocked       -> "#0A0A1A"
            ability.isReady -> "#0D2A1F"
            else            -> "#1A1A0D"
        }
        val card = CardView(this).apply {
            radius = dp(14).toFloat(); cardElevation = dp(5).toFloat()
            setCardBackgroundColor(Color.parseColor(bgColor))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Header
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headerRow.addView(TextView(this).apply {
            text = ability.emoji; textSize = 32f; alpha = if (isLocked) 0.4f else 1f
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) }
        }
        infoCol.addView(tv(
            if (isLocked) "🔒 ${ability.name} (Lv.${ability.unlockLevel})" else ability.name,
            16f, if (isLocked) "#666666" else "#FFFFFF", bold = true
        ))
        infoCol.addView(tv(ability.description, 12f, if (isLocked) "#444444" else "#9999CC").also { it.setPadding(0, dp(2), 0, 0) })
        headerRow.addView(infoCol)

        // Level badge
        if (!isLocked) {
            headerRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#1A2A4A"))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                addView(tv("LV.${ability.abilityLevel}", 12f, "#E0E0FF", bold = true).also { it.gravity = Gravity.CENTER })
                addView(tv("MAX 5", 9f, "#888888").also { it.gravity = Gravity.CENTER })
            })
        }

        col.addView(headerRow)

        if (!isLocked) {
            // Effect description
            col.addView(tv(ability.effectDescription, 11f, "#7CB9E8").also { it.setPadding(0, dp(6), 0, 0) })

            // Cooldown status
            val cdText = if (ability.isReady) "✅ Pronta all'uso" else "⏳ Cooldown: ${ability.cooldownRemainingSeconds}s"
            val cdColor = if (ability.isReady) "#00FF88" else "#FF9800"
            col.addView(tv(cdText, 12f, cdColor).also { it.setPadding(0, dp(6), 0, 0) })

            // Progress bar per cooldown
            if (!ability.isReady) {
                val progress = ((ability.lastUsedMs + ability.actualCooldownMs - System.currentTimeMillis()).toFloat() / ability.actualCooldownMs * 100).toInt()
                val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100; this.progress = (100 - progress).coerceIn(0, 100)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).also { it.topMargin = dp(4) }
                }
                col.addView(pb)
            }

            // Action buttons
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) }
            }

            if (ability.isReady) {
                val useBtn = Button(this).apply {
                    text = "⚡ USA"; setTextColor(Color.WHITE); textSize = 13f
                    setBackgroundColor(Color.parseColor("#00FF88"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).also { it.marginEnd = dp(6) }
                    setOnClickListener {
                        AbilityManager.useAbility(ability,
                            onSuccess = { runOnUiThread { recreate() } },
                            onError = { msg -> runOnUiThread { Toast.makeText(this@AbilityActivity, msg, Toast.LENGTH_SHORT).show() } }
                        )
                    }
                }
                btnRow.addView(useBtn)
            }

            if (ability.canUpgrade) {
                val upBtn = Button(this).apply {
                    text = "⬆️ +1 (${ability.gemsCostUpgrade}💎)"; setTextColor(Color.WHITE); textSize = 12f
                    setBackgroundColor(Color.parseColor("#FF9800"))
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                    setOnClickListener {
                        AbilityManager.upgradeAbility(ability, playerGems,
                            onSuccess = { _, _ -> runOnUiThread { recreate() } },
                            onError = { msg -> runOnUiThread { Toast.makeText(this@AbilityActivity, msg, Toast.LENGTH_SHORT).show() } }
                        )
                    }
                }
                btnRow.addView(upBtn)
            } else {
                btnRow.addView(tv("⭐ LIVELLO MASSIMO", 12f, "#E0E0FF").also { it.gravity = Gravity.CENTER })
            }

            col.addView(btnRow)
        } else {
            col.addView(tv("🔒 Sblocca al Livello ${ability.unlockLevel}", 12f, "#888888").also { it.setPadding(0, dp(8), 0, 0) })
        }

        card.addView(col)
        return card
    }

    private fun backBtn() = TextView(this).apply {
        text = "← Torna"; textSize = 14f; setTextColor(Color.parseColor("#666699"))
        setPadding(0, 0, 0, dp(8)); setOnClickListener { finish() }
    }

    private fun titleTv(text: String, colorHex: String) = TextView(this).apply {
        this.text = text; textSize = 24f; setTextColor(Color.parseColor(colorHex))
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun tv(text: String, size: Float, colorHex: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(colorHex))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

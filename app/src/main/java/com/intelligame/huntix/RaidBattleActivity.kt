package com.intelligame.huntix

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.managers.IncubatorManager
import com.intelligame.huntix.managers.RaidManager
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.SurpriseManager
import com.intelligame.huntix.ui.RaidLootActivity

class RaidBattleActivity : BaseNavActivity() {

    override fun activeTab() = ""
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var raidsBox: LinearLayout
    private lateinit var statsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        raidsBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        statsLabel = TextView(c).apply {
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = Gravity.CENTER
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Raid Battles", "🏟️"),
            UiKit.subtitle(c, "Sfida boss in palestre! Danno = attacco squadra."),
            statsLabel,
            UiKit.button(c, "🔄 Aggiorna", UiKit.ACCENT) { render() },
            raidsBox
        )
        setContentView(content)
        render()
    }

    private fun render() {
        val c = this
        raidsBox.removeAllViews()

        if (!RaidManager.canRaidToday(c)) {
            statsLabel.text = "Limite giornaliero raggiunto (5 raid/giorno)"
            raidsBox.addView(UiKit.comingSoon(c, "Torna domani!", "Hai completato tutti i raid giornalieri."))
            return
        }

        val raids = RaidManager.getActiveRaids(c)
        val completed = RaidManager.getCompletedRaids(c)
        val team = SurpriseManager.getAll(c).filter { it.inBattleTeam }
        val teamPower = team.sumOf { it.creature?.baseAttack ?: 0 }

        statsLabel.text = "Completati: $completed · Squadra: ${team.size} (${teamPower} ATK)"

        if (raids.isEmpty()) {
            raidsBox.addView(UiKit.comingSoon(c, "Nessun raid attivo", "Torna più tardi!"))
            return
        }

        raids.forEach { raid ->
            val boss = raid.boss
            val tierStars = "⭐".repeat(boss.tier)

            val hpBar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = raid.hpPercent
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 10)
                ).apply { topMargin = UiKit.dp(c, 8) }
            }

            val card = UiKit.card(c,
                TextView(c).apply {
                    text = "${boss.emoji} ${boss.name}"
                    textSize = 16f; setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = "$tierStars  Tier ${boss.tier}"
                    textSize = 12f; setTextColor(Color.parseColor("#FFD700"))
                    setPadding(0, UiKit.dp(c, 2), 0, UiKit.dp(c, 4))
                },
                TextView(c).apply {
                    text = "❤️ ${raid.currentHp}/${raid.maxHp} HP  ·  ⏱️ ${raid.remainingSec}s rimasti"
                    textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 2), 0, 0)
                },
                hpBar,
                TextView(c).apply {
                    text = "👥 ${raid.participants} partecipanti"
                    textSize = 11f; setTextColor(Color.parseColor("#6B5B95"))
                    setPadding(0, UiKit.dp(c, 4), 0, 0)
                }
            )

            val canAttack = !raid.isExpired && !raid.defeated && teamPower > 0
            val attackText = if (raid.defeated) "✅ Boss Sconfitto!"
            else if (raid.isExpired) "⏱️ Tempo scaduto"
            else if (teamPower == 0) "❌ Squadra vuota (aggiungi uova alla squadra)"
            else "⚔️ Attacca (${teamPower} ATK)"

            card.addView(UiKit.button(c, attackText,
                when {
                    raid.defeated -> UiKit.GREEN
                    raid.isExpired || teamPower == 0 -> "#444"
                    else -> UiKit.PURPLE
                }
            ) {
                if (canAttack && RaidManager.canRaidToday(c)) {
                    val damage = teamPower + (0..teamPower / 4).random()
                    val result = RaidManager.damageRaid(c, raid.id, damage)
                    RaidManager.incrementRaidCount(c)

                    if (result != null && result.defeated) {
                        // Launch loot screen
                        val rarity = boss.rewardRarity
                        val mvcReward = boss.tier * 100
                        val xpReward = boss.tier * 150
                        val candiesDropped = 3 + boss.tier * 2
                        val itemsList = mutableListOf<String>()
                        itemsList.add("🍬 Caramelle")
                        if (boss.tier >= 3) itemsList.add("🧬 Super Incubatrice")
                        if (boss.tier >= 4) itemsList.add("⭐ Stella Rara")
                        if (boss.tier >= 5) itemsList.add("💎 Gemma Leggendaria")

                        SavedManager.addMvc(c, mvcReward.toDouble())
                        val eggItem = EggInventoryItem(
                            rarityId = rarity.id,
                            fantasyName = rarity.randomName(),
                            power = rarity.basePower
                        )
                        SavedManager.addPendingEgg(c, eggItem)

                        RaidLootActivity.start(
                            c,
                            bossEmoji = boss.emoji,
                            bossName = boss.name,
                            mvcReward = mvcReward,
                            xpReward = xpReward,
                            eggRarityId = rarity.id,
                            candiesDropped = candiesDropped,
                            itemsDropped = itemsList
                        )
                        finish()
                    } else if (result != null) {
                        Toast.makeText(c, "⚔️ -$damage danni! HP: ${result.currentHp}/${result.maxHp}", Toast.LENGTH_SHORT).show()
                    }
                    render()
                } else if (teamPower == 0) {
                    Toast.makeText(c, "Aggiungi uova alla squadra di battaglia!", Toast.LENGTH_SHORT).show()
                }
            })

            raidsBox.addView(card)
        }

        raidsBox.addView(UiKit.button(c, "← Indietro", "#666") { finish() })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

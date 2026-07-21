package com.intelligame.huntix

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.SurpriseManager

class PokedexActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val allCreatures = SurpriseCreature.ALL
        val hatched = SavedManager.getHatchedEggs(c)
        val owned = SurpriseManager.getAll(c).map { it.creatureId }.toSet()
        val discovered = hatched.map { it.creatureId }.toSet() + owned

        val total = allCreatures.size
        val found = allCreatures.count { it.id in discovered }
        val pct = if (total > 0) (found * 100 / total) else 0

        val children = mutableListOf<View>(
            UiKit.title(c, "Pokédex", "📖"),
            UiKit.subtitle(c, "$found / $total creature scoperte ($pct%)")
        )

        // Progress bar
        val progressTrack = LinearLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 12)
            ).apply { setMargins(UiKit.dp(c, 16), UiKit.dp(c, 8), UiKit.dp(c, 16), UiKit.dp(c, 16)) }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 6).toFloat()
                setColor(Color.parseColor("#1A1030"))
            }
        }
        val progressFill = View(c).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, found.toFloat() / total.coerceAtLeast(1))
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 6).toFloat()
                setColor(Color.parseColor(UiKit.ACCENT))
            }
        }
        progressTrack.addView(progressFill)
        children.add(progressTrack)

        // Group by rarity
        EggRarity.values().forEach { rarity ->
            val creatures = allCreatures.filter { it.rarityId == rarity.id }
            if (creatures.isEmpty()) return@forEach

            children.add(UiKit.section(c, "${rarity.emoji} ${rarity.displayName} (${creatures.count { it.id in discovered }}/${creatures.size})"))

            creatures.forEach { creature ->
                val isDiscovered = creature.id in discovered
                val isOwned = creature.id in owned
                val ownedList = SurpriseManager.getAll(c).filter { it.creatureId == creature.id }
                val count = ownedList.size
                val avgLevel = if (count > 0) ownedList.map { it.level }.average() else 0.0

                val card = UiKit.card(c,
                    TextView(c).apply {
                        text = if (isDiscovered) "${creature.emoji} ${creature.name}" else "❓ ???"
                        textSize = 14f
                        setTextColor(if (isDiscovered) Color.WHITE else Color.parseColor("#444466"))
                        setTypeface(Typeface.DEFAULT_BOLD)
                    },
                    TextView(c).apply {
                        text = if (isDiscovered) {
                            "⚔️ ${creature.baseAttack}  🛡️ ${creature.baseDefense}  💨 ${creature.baseSpeed}  ❤️ ${creature.baseHp}"
                        } else "Scoprila catturando uova ${rarity.displayName}!"
                        textSize = 11f
                        setTextColor(if (isDiscovered) Color.parseColor(UiKit.TEXT_DIM) else Color.parseColor("#333355"))
                        setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 4))
                    },
                    TextView(c).apply {
                        text = if (isDiscovered) {
                            "${creature.specialMoveEmoji} ${creature.specialMoveName} (${creature.specialMoveDamage} dmg)"
                        } else ""
                        textSize = 11f
                        setTextColor(Color.parseColor("#A78BFA"))
                        setPadding(0, UiKit.dp(c, 2), 0, 0)
                    }
                )

                if (isOwned) {
                    card.addView(TextView(c).apply {
                        text = "✅ Posseduta × $count  ·  Lv. ${"%.1f".format(avgLevel)}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#00CC88"))
                        setPadding(0, UiKit.dp(c, 6), 0, 0)
                    })
                }

                if (isDiscovered) {
                    card.addView(TextView(c).apply {
                        text = creature.description
                        textSize = 10f
                        setTextColor(Color.parseColor("#6B5B95"))
                        setPadding(0, UiKit.dp(c, 4), 0, 0)
                    })
                }

                children.add(card)
            }
        }

        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })

        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }
}

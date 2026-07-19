package com.intelligame.huntix

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * EggInventoryActivity — inventario uova e gestione squadra battaglia.
 */
class EggInventoryActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val items = EggInventoryManager.getInventory(c)
        val children = mutableListOf<android.view.View>(
            UiKit.title(c, "Inventario Uova", "🎒"),
            UiKit.subtitle(c, "Tocca un'uovo per aggiungerla/rimuoverla dalla squadra di battaglia.")
        )
        if (items.isEmpty()) {
            children.add(UiKit.comingSoon(c, "Inventario vuoto", "Ottieni uova giocando in Indoor o Outdoor!"))
        } else {
            children.add(box)
        }
        setContentView(UiKit.scroll(c, *children.toTypedArray()))

        items.forEach { item ->
            val row = UiKit.card(c,
                TextView(c).apply {
                    text = "${item.rarity.emoji} ${item.rarity.displayName}"
                    textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                },
                TextView(c).apply {
                    text = "${item.fantasyName.ifBlank { "Uovo" }}  ·  ⚔️ ${item.power}"
                    textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM))
                    setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
                },
                UiKit.button(c, if (item.inBattleTeam) "✓ In squadra" else "➕ Aggiungi a squadra",
                    if (item.inBattleTeam) UiKit.GREEN else UiKit.PURPLE) {
                    EggInventoryManager.toggleBattleTeam(c, item.instanceId)
                    renderRow(c, box, item)
                }
            )
            box.addView(row)
        }
    }

    private fun renderRow(c: android.content.Context, box: LinearLayout, item: EggInventoryItem) {
        box.removeAllViews()
        box.addView(TextView(c).apply {
            text = "Squadra aggiornata: ${EggInventoryManager.getBattleTeamCount(c)} uova"
            textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
            setPadding(0, UiKit.dp(c, 8), 0, 0)
        })
    }
}

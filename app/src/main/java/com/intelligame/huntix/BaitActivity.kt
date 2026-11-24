// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.EggBaitManager

/**
 * Fase 2 · voce 13: scelta dell'esca prima della cattura.
 *
 * Non introduce un inventario parallelo: legge le quantita' che
 * EggNutrimentManager possiede gia' e scrive solo lo stato "esca attiva" in
 * EggBaitManager. Nessun premio aggiuntivo, nessun default cambiato.
 */
class BaitActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val c = this
        val foods = EggNutrimentManager.EggFood.values().map {
            it to EggNutrimentManager.getQuantity(c, it)
        }
        val active = EggBaitManager.current(c)
        val wait = EggBaitManager.cooldownRemainingMs(c)

        val children = mutableListOf<View>(
            UiKit.title(c, "Esche per le uova", "🍎"),
            UiKit.subtitle(c, "Scegli un aroma prima di lanciare: aumenta la probabilità di cattura")
        )

        children.add(UiKit.card(c,
            TextView(c).apply {
                text = "Ogni esca dura 5 minuti e vale per un solo lancio. " +
                       "Tra un'esca e l'altra aspetti 30 secondi. " +
                       "Nessun premio nascosto: cambia solo la fortuna del lancio."
                textSize = 12f
                setTextColor(Color.parseColor("#C8E6C9"))
            }
        ))

        if (active != null) {
            children.add(UiKit.section(c, "Esca pronta"))
            children.add(UiKit.card(c,
                TextView(c).apply {
                    text = "${active.food.emoji} ${active.food.displayName} · " +
                           "bonus ×${String.format(java.util.Locale.US, "%.2f", active.bonus)}"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
                UiKit.button(c, "Annulla esca", "#6A1B9A") {
                    EggBaitManager.cancel(c)
                    showToast("Esca annullata")
                    render()
                }.also { it.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(c, 8) } }
            ))
        }

        if (wait > 0L) {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "⏳ Cooldown: ancora ${(wait / 1000L) + 1}s"
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
                gravity = Gravity.CENTER
            }))
        }

        children.add(UiKit.section(c, "Disponibili — ${foods.filter { it.second > 0 }.size} aromi"))

        var selected = false
        foods.forEach { (food, qty) ->
            val card = UiKit.card(c,
                TextView(c).apply {
                    text = "${food.emoji} ${food.displayName}   ×$qty"
                    textSize = 14f
                    setTextColor(if (qty > 0) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                },
                TextView(c).apply {
                    text = "Bonus base ×${String.format(java.util.Locale.US, "%.2f", food.baseCatchBonus)}" +
                           if (food.favoriteElement != null) " · preferito da ${food.favoriteElement}" else " · universale"
                    textSize = 11f
                    setTextColor(Color.parseColor("#A5D6A7"))
                }
            )
            if (active == null && qty > 0 && wait <= 0L) {
                selected = true
                card.addView(UiKit.button(c, "Usa", UiKit.ACCENT) {
                    val msg = EggBaitManager.select(c, food.id)
                    showToast(msg ?: "Esca non disponibile")
                    render()
                }.also { b ->
                    b.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = UiKit.dp(c, 8) }
                })
            }
            children.add(card)
        }

        if (!selected && active == null) {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "Nessun aroma utilizzabile adesso. Puoi acquistare il cibo " +
                       "nel negozio oppure attendere il cooldown."
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
            }))
        }

        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

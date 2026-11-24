// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.EggFusionCraftManager
import com.intelligame.huntix.managers.EggFusionRecipeManager

/**
 * Fase 2 · voce 15: UI italiana della fusione uova già esistente.
 *
 * Non introduce un inventario nuovo: riempie i tre slot storici
 * (`fusion_slots_v2`) con le uova schiuse reali e chiama il wrapper additivo.
 * Nessun costo in valuta, nessun acquisto, nessun premio duplicato.
 */
class CraftActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val c = this
        val mgr = EggFusionCraftManager
        val slots = mgr.slots(c)
        val available = mgr.available(c)
        val reason = mgr.blockReason(c)

        val children = mutableListOf<View>(
            UiKit.title(c, "Fondi le uova", "🥚"),
            UiKit.subtitle(c, "Tre uova schiuse della stessa creatura diventano un uovo più potente"),
            UiKit.card(c, TextView(c).apply {
                text = "La fusione è gratuita e non tocca MVC, gemme o negozio. " +
                       "Ogni fusione lascia un ricordo posizionale nel diario delle uova."
                textSize = 12f
                setTextColor(Color.parseColor("#C8E6C9"))
            })
        )

        children.add(UiKit.section(c, "Slot di fusione — ${slots.count { it != null }}/3"))
        slots.forEachIndexed { index, egg ->
            val card = UiKit.card(c, TextView(c).apply {
                text = if (egg == null) "${index + 1}. Slot vuoto"
                       else "${index + 1}. ${egg.emoji} ${egg.displayName} · ${egg.levelTag} · ${egg.creatureId.ifBlank { "creatura sconosciuta" }}"
                textSize = 14f
                setTextColor(if (egg == null) Color.parseColor(UiKit.TEXT_DIM) else Color.WHITE)
            })
            if (egg != null) {
                card.addView(UiKit.button(c, "Togli dallo slot", "#6A1B9A") {
                    mgr.removeSlot(c, index)
                    render()
                }.also { it.layoutParams = matchParent(c) })
            }
            children.add(card)
        }

        children.add(UiKit.section(c, "Uova schiuse disponibili — ${available.size}"))
        if (available.isEmpty()) {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "Non hai ancora uova schiuse. Schiudine una dalla scheda Uova."
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
            }))
        } else {
            // Si mostrano le prime 12 per non trasformare la lista in una
            // scorrimento infinito: le altre restano selezionabili dagli slot.
            available.take(12).forEach { egg ->
                val inSlot = slots.any { it?.istanzaId == egg.istanzaId }
                val card = UiKit.card(c,
                    TextView(c).apply {
                        text = "${egg.emoji} ${egg.displayName} · ${egg.levelTag}"
                        textSize = 14f
                        setTextColor(if (inSlot) Color.parseColor(UiKit.TEXT_DIM) else Color.WHITE)
                    },
                    TextView(c).apply {
                        text = "Creature: ${egg.creatureId.ifBlank { "sconosciuta" }} · potenza ${HatchedEgg.formatHps(egg.miningPowerHps)}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#A5D6A7"))
                    }
                )
                if (!inSlot) {
                    card.addView(UiKit.button(c, "Metti nello slot", UiKit.ACCENT) {
                        mgr.addSlot(c, egg.istanzaId)
                        render()
                    }.also { it.layoutParams = matchParent(c) })
                }
                children.add(card)
            }
        }

        if (reason == null) {
            children.add(UiKit.button(c, "✨ Fondi le uova", UiKit.GREEN) {
                val msg = mgr.craft(c, "Android→crafting")
                showToast(msg ?: "Fusione non disponibile")
                render()
            })
        } else {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "⏳ $reason"
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
                gravity = Gravity.CENTER
            }))
        }

        // Fase 2 · voce 16: ricettario in sola lettura. Non avvia fusioni:
        // il giocatore continua a riempire gli slot qui sopra. La guida non
        // impone livelli uguali, come non li impone il motore di fusione.
        children.add(UiKit.section(c, "Ricette di fusione — " +
            "${EggFusionRecipeManager.discoveredCount(c)}/${EggFusionRecipeManager.catalog().size} scoperte"))
        EggFusionRecipeManager.views(c).forEach { view ->
            val r = view.recipe
            val mark = if (view.discovered) "📖" else "❔"
            children.add(UiKit.card(c,
                TextView(c).apply {
                    text = "$mark ${r.title}  ·  ${r.targetTag}"
                    textSize = 14f
                    setTextColor(if (view.discovered) Color.WHITE
                                 else Color.parseColor(UiKit.TEXT_DIM))
                },
                TextView(c).apply {
                    text = r.description
                    textSize = 11f
                    setTextColor(Color.parseColor("#A5D6A7"))
                },
                TextView(c).apply {
                    text = if (view.readyGroups > 0)
                        "Pronte: ${view.readyGroups} creature con uova adatte"
                        else "Nessun gruppo pronto al momento"
                    textSize = 11f
                    setTextColor(Color.parseColor(
                        if (view.readyGroups > 0) "#FFD54F" else UiKit.TEXT_DIM))
                }
            ))
        }
        children.add(UiKit.card(c, TextView(c).apply {
            text = "Le ricette sono una guida: il motore prende il livello della " +
                   "prima uovo nello slot, quindi non occorre che i tre livelli siano uguali. " +
                   "Usate ${EggFusionRecipeManager.uses(c)} volte."
            textSize = 12f
            setTextColor(Color.parseColor("#FFCC80"))
        }))

        children.add(UiKit.card(c, TextView(c).apply {
            text = "Fusioni completate: ${mgr.total(c)} · ultimo livello: ${mgr.lastLevel(c)}"
            textSize = 12f
            setTextColor(Color.WHITE)
        }))
        children.add(UiKit.button(c, "📖 Apri il diario", UiKit.ACCENT) {
            startActivity(android.content.Intent(c, ActivityLogActivity::class.java))
        })
        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }

    private fun matchParent(c: android.content.Context) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = UiKit.dp(c, 8) }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.EggInventoryViewManager
import com.intelligame.huntix.managers.GlobalAreas

/**
 * Fase 2 · voce 11: vista dell'inventario uova già esistente.
 *
 * Usa SOLO `EggInventoryManager` Android: nessun secondo inventario, nessun
 * acquisto e nessun premio in valuta. Gli ordinamenti sono una preferenza di
 * vista; la squadra uova resta distinta dalla squadra creature dei raid.
 */
class EggCollectionActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val c = this
        val mgr = EggInventoryViewManager
        val mode = mgr.sortMode(c)
        val eggs = mgr.sorted(c)
        val team = mgr.team(c)

        val children = mutableListOf<View>(
            UiKit.title(c, "Le tue uova", "🥚"),
            UiKit.subtitle(c, "Ordina la collezione e scegli la squadra uova"),
            UiKit.card(c, TextView(c).apply {
                text = "Collezione: ${eggs.size} uova · squadra uova: ${team.size}/3\n" +
                       "La squadra uova è diversa da quella creature usata nei raid: " +
                       "cambiarla non tocca le battaglie."
                textSize = 12f
                setTextColor(Color.parseColor("#C8E6C9"))
            }),
            // Mandato §13: l'inventario è globale e mostra ciò che MiCittà
            // ha fatto nel mondo, non solo le uova della collezione.
            UiKit.card(c, TextView(c).apply {
                text = GlobalAreas.summary(c)
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
            }),
            UiKit.button(c, "⚡ Vai all'Area Abilità", UiKit.ACCENT) {
                GlobalAreas.openSkills(c)
            }
        )

        children.add(UiKit.section(c, "Ordina per (solo vista)"))
        val chips = UiKit.card(c)
        EggInventoryViewManager.SortMode.values().forEach { m ->
            chips.addView(UiKit.button(c, m.label,
                if (m == mode) UiKit.GREEN else "#4F46E5") {
                mgr.setSortMode(c, m.id)
                render()
            }.also { b ->
                b.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = UiKit.dp(c, 8) }
            })
        }
        children.add(chips)

        if (team.isNotEmpty()) {
            children.add(UiKit.section(c, "Squadra uova"))
            children.add(UiKit.card(c, TextView(c).apply {
                text = team.joinToString(" · ") {
                    "${it.rarity.emoji} ${it.fantasyName.ifBlank { it.rarity.displayName }}"
                }
                textSize = 13f
                setTextColor(Color.WHITE)
            }))
        }

        children.add(UiKit.section(c, "Collezione — ${eggs.size}"))
        if (eggs.isEmpty()) {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "Non hai ancora uova: catturane una nel mondo o in Miacittà."
                textSize = 12f
                setTextColor(Color.parseColor("#FFCC80"))
            }))
        } else {
            // Al massimo 20 righe per schermata: l'ordinamento resta completo
            // nei dati, si limita solo la lista mostrata.
            eggs.take(20).forEach { egg ->
                val inTeam = mgr.isInTeam(c, egg.istanzaId)
                val card = UiKit.card(c,
                    TextView(c).apply {
                        text = "${egg.rarity.emoji} ${egg.fantasyName.ifBlank { egg.rarity.displayName }} · ${egg.rarity.displayName}"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                    },
                    TextView(c).apply {
                        text = "Forza ${egg.power} · XP ${egg.xpReward}${if (inTeam) " · in squadra" else ""}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#A5D6A7"))
                    }
                )
                card.addView(UiKit.button(c,
                    if (inTeam) "Togli dalla squadra" else "Metti in squadra",
                    if (inTeam) "#6A1B9A" else UiKit.ACCENT) {
                    val msg = mgr.toggleTeam(c, egg.istanzaId)
                    android.widget.Toast.makeText(c, msg ?: "Squadra uova piena (3)",
                        android.widget.Toast.LENGTH_SHORT).show()
                    render()
                }.also { b ->
                    b.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = UiKit.dp(c, 8) }
                })
                children.add(card)
            }
        }

        children.add(UiKit.card(c, TextView(c).apply {
            text = "Nessun acquisto e nessuna valuta: cambiare vista o squadra " +
                   "lascia un ricordo posizionale nel diario delle uova."
            textSize = 12f
            setTextColor(Color.parseColor("#FFCC80"))
        }))
        children.add(UiKit.button(c, "📖 Apri il diario", UiKit.ACCENT) {
            startActivity(android.content.Intent(c, ActivityLogActivity::class.java))
        })
        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }
}

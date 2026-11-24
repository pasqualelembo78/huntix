// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.EggResourceHarvestManager

/**
 * Fase 2 · voce 14: registro delle risorse raccolte nel mondo.
 *
 * Vista in sola lettura dei contatori già mantenuti da
 * EggResourceHarvestManager. Non compra, non spende e non duplica inventari:
 * serve a mostrare DOVE sono state raccolte le uova della raccolta, nello
 * stesso diario delle uova della Fase 1.
 */
class ResourceHarvestActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val manager = EggResourceHarvestManager
        val children = mutableListOf<View>(
            UiKit.title(c, "Raccolte nel mondo", "🧺"),
            UiKit.subtitle(c, "Ogni prima raccolta nasconde un uovo posizionale, sempre nello stesso diario"),
            UiKit.card(c, TextView(c).apply {
                text = "Totale raccolte: ${manager.total(c)} · tipi diversi: ${manager.kinds(c)}"
                textSize = 14f
                setTextColor(Color.WHITE)
            })
        )

        manager.allCounts(c).forEach { (resource, qty) ->
            children.add(UiKit.card(c,
                TextView(c).apply {
                    text = "${resource.emoji} ${resource.displayName}   ×$qty"
                    textSize = 14f
                    setTextColor(if (qty > 0) Color.WHITE else Color.parseColor(UiKit.TEXT_DIM))
                },
                TextView(c).apply {
                    text = if (qty > 0) "Uovo della Raccolta: già trovato 🥚"
                            else "Raccoglilo in Miacittà per trovare l'uovo 🥚"
                    textSize = 11f
                    setTextColor(Color.parseColor("#A5D6A7"))
                }
            ))
        }

        children.add(UiKit.card(c, TextView(c).apply {
            text = "Le raccolte non danno MVC, gemme o soldi: cambiano solo il diario " +
                   "e, una volta per tipo, l'inventario uova."
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

// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.managers.EggWhereLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro attività del giocatore (Fase 1 · voce 2).
 *
 * Legge l'UNICO storico già prodotto da EggWhereLog: uova con luogo e
 * coordinate, più le voci di missioni, check-in, badge ed eventi Unity.
 * Nessun nuovo file di preferenze e nessun duplicazione del log uova:
 * il diario e' solo la vista consultabile che il Mandato chiedeva
 * ("DOVE HO TROVATO QUESTO UOVO").
 */
class ActivityLogActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private var filter: String = EggWhereLog.KIND_EGG
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val c = this
        val all = EggWhereLog.getLog(c)
        val shown = if (filter == "all") all
                    else all.filter { it.optString("kind", EggWhereLog.KIND_EGG) == filter }
                        .sortedByDescending { it.optLong("ts", 0L) }

        val children = mutableListOf<View>(
            UiKit.title(c, "Diario delle uova", "📔"),
            UiKit.subtitle(c, "Ogni uovo ricordato con luogo, rarità e coordinate · ${all.size} voci")
        )

        // Riepilogo per categoria: numeri letti dallo storico, non duplicati.
        val counts = linkedMapOf(
            EggWhereLog.KIND_EGG to all.count { it.optString("kind", EggWhereLog.KIND_EGG) == EggWhereLog.KIND_EGG },
            EggWhereLog.KIND_QUEST to all.count { it.optString("kind", "") == EggWhereLog.KIND_QUEST },
            EggWhereLog.KIND_LOGIN to all.count { it.optString("kind", "") == EggWhereLog.KIND_LOGIN },
            EggWhereLog.KIND_BADGE to all.count { it.optString("kind", "") == EggWhereLog.KIND_BADGE },
            EggWhereLog.KIND_EVENT to all.count { it.optString("kind", "") == EggWhereLog.KIND_EVENT }
        )
        children.add(UiKit.card(c,
            TextView(c).apply {
                text = counts.entries.joinToString("  ·  ") { (k, n) -> "${kindEmoji(k)} $n" }
                textSize = 12f
                setTextColor(Color.parseColor(UiKit.ACCENT))
                gravity = Gravity.CENTER
            }
        ))

        // Filtri: "Tutte" e le cinque categorie. Cambiare filtro non scrive
        // nulla: e' solo una vista, quindi nessun effetto sui dati salvati.
        val chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val options = listOf("all" to "Tutte") + counts.keys.map { it to kindLabel(it) }
        options.forEach { (key, label) ->
            chips.addView(UiKit.button(c, if (filter == key) "• $label" else label,
                if (filter == key) UiKit.PURPLE else "#2A2A4A") {
                filter = key
                render()
            }.also {
                it.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = UiKit.dp(c, 6) }
            })
        }
        children.add(HorizontalScrollView(c).apply { addView(chips) })

        children.add(UiKit.section(c, "${kindLabel(filter)} — ${shown.size} voci"))

        if (shown.isEmpty()) {
            children.add(UiKit.card(c, TextView(c).apply {
                text = "Nessuna voce in questa categoria. Torna a cacciare uova: " +
                       "ogni ritrovamento finisce qui con il suo luogo!"
                textSize = 12f
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            }))
        } else {
            shown.forEach { entry ->
                val kind = entry.optString("kind", EggWhereLog.KIND_EGG)
                val rarity = entry.optString("rarity", "")
                val rarityEmoji = EggRarity.values()
                    .firstOrNull { it.id == rarity }?.emoji ?: "🥚"
                val place = entry.optString("place", "luogo non registrato")
                val label = entry.optString("label", entry.optString("name", kindLabel(kind)))
                val ts = entry.optLong("ts", 0L)
                val whenText = if (ts > 0L) dateFmt.format(Date(ts)) else "—"
                val lat = entry.optDouble("lat", 0.0)
                val lng = entry.optDouble("lng", 0.0)
                val coords = if (lat != 0.0 || lng != 0.0)
                    "📍 %.6f, %.6f".format(Locale.US, lat, lng) else "📍 coordinate non note"

                children.add(UiKit.card(c,
                    TextView(c).apply {
                        text = "$whenText   ${kindEmoji(kind)} $label"
                        textSize = 12f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                    },
                    TextView(c).apply {
                        text = "$rarityEmoji  $place"
                        textSize = 12f
                        setTextColor(Color.parseColor("#A5D6A7"))
                        setPadding(0, UiKit.dp(c, 4), 0, 0)
                    },
                    TextView(c).apply {
                        text = coords
                        textSize = 11f
                        setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                        setPadding(0, UiKit.dp(c, 3), 0, 0)
                    }
                ))
            }
        }

        children.add(UiKit.button(c, "← Indietro", "#666") { finish() })
        setContentView(UiKit.scroll(c, *children.toTypedArray()))
    }

    private fun kindEmoji(kind: String) = when (kind) {
        EggWhereLog.KIND_EGG -> "🥚"
        EggWhereLog.KIND_QUEST -> "🎯"
        EggWhereLog.KIND_LOGIN -> "📅"
        EggWhereLog.KIND_BADGE -> "📍"
        else -> "✨"
    }

    private fun kindLabel(kind: String) = when (kind) {
        EggWhereLog.KIND_EGG -> "Uova"
        EggWhereLog.KIND_QUEST -> "Missioni"
        EggWhereLog.KIND_LOGIN -> "Check-in"
        EggWhereLog.KIND_BADGE -> "Badge"
        else -> "Eventi"
    }
}

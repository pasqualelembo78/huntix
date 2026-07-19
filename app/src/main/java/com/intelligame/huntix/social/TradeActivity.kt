package com.intelligame.huntix.social

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit

/**
 * TradeActivity — scambio creature con altri giocatori (Firebase).
 */
class TradeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val incoming = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val mine = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        fun load() {
            incoming.removeAllViews(); mine.removeAllViews()
            TradeManager.getIncomingOffers { offers ->
                runOnUiThread {
                    if (offers.isEmpty()) incoming.addView(UiKit.comingSoon(c, "Nessuna offerta", "Le offerte di scambio ricevute appariranno qui."))
                    offers.forEach { o ->
                        incoming.addView(UiKit.card(c,
                            TextView(c).apply { text = "📥 Da ${o.fromName}: ${o.offeredCreatureEmoji} ${o.offeredCreatureName}"; textSize = 13f; setTextColor(android.graphics.Color.WHITE); setTypeface(android.graphics.Typeface.DEFAULT_BOLD) },
                            TextView(c).apply { text = "Costo: ${o.mvcCost} MVC"; textSize = 12f; setTextColor(android.graphics.Color.parseColor(UiKit.TEXT_DIM)); setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8)) },
                            UiKit.button(c, "✅ Accetta", "#00FF88") {
                                TradeManager.acceptOffer(o.id) { ok, msg -> Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); load() }
                            }
                        ))
                    }
                }
            }
            TradeManager.getMyOffers { offers ->
                runOnUiThread {
                    if (offers.isEmpty()) mine.addView(UiKit.comingSoon(c, "Nessuna offerta inviata", "Proponi scambi ai tuoi amici."))
                    offers.forEach { o ->
                        mine.addView(UiKit.row(c, "📤 → ${o.toName}: ${o.offeredCreatureEmoji} ${o.offeredCreatureName}", o.status))
                    }
                }
            }
        }
        load()

        setContentView(UiKit.scroll(c,
            UiKit.title(c, "Scambi", "🔄"),
            UiKit.section(c, "Offerte ricevute"), incoming,
            UiKit.section(c, "Le mie offerte"), mine
        ))
    }
}

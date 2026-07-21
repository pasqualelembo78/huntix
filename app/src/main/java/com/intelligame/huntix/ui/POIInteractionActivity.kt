package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class POIInteractionActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var poiId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        poiId = intent.getStringExtra("poiId") ?: mgr.getPois().firstOrNull()?.id ?: ""

        val nameText = TextView(this).apply { textSize = 22f }
        val descText = TextView(this).apply { textSize = 15f }
        val rewardText = TextView(this).apply { textSize = 16f ; setTextColor(0xFFFFEB3B.toInt()) }

        val poi = mgr.getPois().firstOrNull { it.id == poiId }
        nameText.text = poi?.name ?: "POI non disponibile"
        descText.text = if (poi != null) "Distanza: ${mgr.distanceMeters(poi).toInt()} m\nSpinna per ottenere ricompense." else ""

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Palestra", "🏟️"),
            UiKit.card(this, nameText, descText, rewardText),
            UiKit.button(this, "🌀 Spinna la palestra", UiKit.ACCENT) {
                val msg = mgr.spinPoi(this, poiId)
                rewardText.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            },
            UiKit.button(this, "↩️ Torna alla mappa", UiKit.PURPLE) {
                startActivity(Intent(this, OutdoorWorldActivity::class.java))
                finish()
            }
        )
        setContentView(content)
    }
}

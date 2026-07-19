package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class OutdoorArCatchActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private lateinit var eggView: TextView
    private lateinit var hint: TextView
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { update(); refresh.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        }

        eggView = TextView(this).apply {
            text = "🥚"
            textSize = 120f
            setOnClickListener { attemptCatch() }
        }
        hint = TextView(this).apply { textSize = 16f; setTextColor(android.graphics.Color.WHITE) }

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Caccia AR", "📍"),
            UiKit.subtitle(this, "Inquadra l'uovo e toccalo per catturarlo."),
            UiKit.card(this, eggView, hint),
            UiKit.button(this, "↩️ Mappa", UiKit.PURPLE) {
                startActivity(Intent(this, OutdoorWorldActivity::class.java))
                finish()
            }
        )
        setContentView(content)
        refresh.post(tick)
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun update() {
        val egg = mgr.nearestUnfoundEgg()
        if (egg == null) {
            hint.text = "Nessuna uova da catturare. Torna alla mappa."
            eggView.text = "🔍"
            return
        }
        val d = mgr.distanceMeters(egg)
        eggView.text = egg.rarity.emoji
        hint.text = if (d > OutdoorManager.CATCH_RADIUS_M) {
            "Avvicinati: mancano ${d.toInt()} m (tocca l'uovo quando sei vicino)"
        } else {
            "Sei abbastanza vicino! Tocca l'uovo per catturarlo."
        }
    }

    private fun attemptCatch() {
        val egg = mgr.nearestUnfoundEgg() ?: run {
            Toast.makeText(this, "Nessuna uova vicina", Toast.LENGTH_SHORT).show()
            return
        }
        val res = mgr.tryCatch(this, egg.id)
        Toast.makeText(this, res.message, Toast.LENGTH_LONG).show()
        if (res.success) {
            eggView.text = "✅"
            update()
        }
    }
}

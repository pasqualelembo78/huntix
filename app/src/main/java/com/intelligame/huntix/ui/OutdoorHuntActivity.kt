package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class OutdoorHuntActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var eggId: String = ""
    private lateinit var distText: TextView
    private lateinit var hintText: TextView
    private lateinit var radar: OutdoorRadarView
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { update(); refresh.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eggId = intent.getStringExtra("eggId") ?: mgr.nearestUnfoundEgg()?.id ?: ""
        mgr.huntingEggId = eggId.ifEmpty { null }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 2003
            )
        }

        distText = TextView(this).apply {
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
        }
        hintText = TextView(this).apply { textSize = 16f }
        radar = OutdoorRadarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this@OutdoorHuntActivity, 240)
            )
        }

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Caccia all'uovo", "🥚"),
            radar,
            UiKit.card(
                this,
                distText,
                hintText,
                UiKit.button(this, "🎯 Cattura!", UiKit.ACCENT) { doCatch() }
            ),
            UiKit.button(this, "↩️ Torna alla mappa", UiKit.PURPLE) {
                startActivity(Intent(this, OutdoorWorldActivity::class.java))
                finish()
            }
        )
        setContentView(content)
        refresh.post(tick)
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        mgr.huntingEggId = null
        mgr.stop()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2003 &&
            grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            mgr.start(this)
        }
    }

    private fun update() {
        val egg = mgr.getEgg(eggId)
        if (egg == null) {
            distText.text = "Uovo non disponibile"
            radar.blips = emptyList()
            return
        }
        val d = mgr.distanceMeters(egg)
        distText.text = if (egg.found) "✅ Già catturato" else "${d.toInt()} m"
        hintText.text = when {
            egg.found -> "Torna alla mappa per un'altra uova."
            d > mgr.getCatchRadiusM(egg) -> "Avvicinati all'uovo per catturarlo."
            else -> "Sei abbastanza vicino: premi Cattura!"
        }
        radar.headingDeg = mgr.getDeviceHeadingDeg()
        radar.blips = listOf(
            OutdoorRadarView.Blip(mgr.bearingTo(egg), d, 0xFFFFEB3B.toInt())
        )
        radar.invalidate()
    }

    private fun doCatch() {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Già catturato", Toast.LENGTH_SHORT).show()
            return
        }
        CatchDialogHelper.showFoodSelection(this, egg, object : CatchDialogHelper.OnCatchReady {
            override fun onCatchReady(foodBonus: Float, xpMultiplier: Float) {
                if (foodBonus <= 0f && xpMultiplier <= 0f) {
                    Toast.makeText(this@OutdoorHuntActivity, "L'uovo è fuggito! Riprova.", Toast.LENGTH_LONG).show()
                    update()
                    return
                }
                val effectiveBonus = if (foodBonus > 0f) foodBonus else 1f
                val res = mgr.tryCatch(this@OutdoorHuntActivity, eggId, effectiveBonus)
                Toast.makeText(this@OutdoorHuntActivity, res.message, Toast.LENGTH_LONG).show()
                if (res.success && res.egg != null) {
                    distText.text = "✅ Catturato!"
                    EggOpeningAnimationActivity.start(this@OutdoorHuntActivity, res.egg.rarity, res.egg.name, res.egg.rarity.xpReward)
                }
                update()
            }
        })
    }
}

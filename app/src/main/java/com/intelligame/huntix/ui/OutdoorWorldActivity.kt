package com.intelligame.huntix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager

class OutdoorWorldActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private lateinit var radar: OutdoorRadarView
    private lateinit var eggList: LinearLayout
    private lateinit var poiList: LinearLayout
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshUi(); refresh.postDelayed(this, 2000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101
            )
        } else {
            mgr.start(this)
        }

        radar = OutdoorRadarView(this)
        eggList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        poiList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val radarHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this@OutdoorWorldActivity, 300)
            )
        }
        radar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        radarHolder.addView(radar)

        val content = UiKit.scroll(
            this,
            UiKit.title(this, "Mappa del Mondo", "🗺️"),
            UiKit.subtitle(this, "Cammina per trovare uova e palestre vicino a te."),
            radarHolder,
            UiKit.button(this, "🔄 Rigenera spawn", UiKit.PURPLE) {
                mgr.currentLocation?.let { mgr.regenerate(it) }
                    ?: mgr.regenerate(mgr.defaultLocation())
                Toast.makeText(this, "Nuovi spawn generati", Toast.LENGTH_SHORT).show()
            },
            UiKit.section(this, "Uova vicine"),
            eggList,
            UiKit.section(this, "Palestre / POI"),
            poiList,
            UiKit.button(this, "🧭 Naviga verso l'uovo piu' vicino", UiKit.ACCENT) {
                if (mgr.nearestUnfoundEgg() == null) {
                    Toast.makeText(this, "Nessuna uova da cercare", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, ArNavigationActivity::class.java))
                }
            },
            UiKit.button(this, "📍 Caccia AR", UiKit.PURPLE) {
                startActivity(Intent(this, OutdoorArCatchActivity::class.java))
            }
        )
        setContentView(content)
        refresh.post(tick)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            mgr.start(this)
        } else {
            Toast.makeText(this, "Permesso GPS negato: usato spawn dimostrativo", Toast.LENGTH_LONG).show()
            mgr.start(this)
        }
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        mgr.stop()
        super.onDestroy()
    }

    private fun refreshUi() {
        val blips = mutableListOf<OutdoorRadarView.Blip>()
        eggList.removeAllViews()
        mgr.getEggs().forEach { egg ->
            val d = mgr.distanceMeters(egg)
            blips.add(OutdoorRadarView.Blip(mgr.bearingTo(egg), d, rarityColor(egg.rarity)))
            val row = UiKit.row(
                this,
                "${egg.rarity.emoji} ${egg.displayLabel}",
                if (egg.found) "✅ presa" else "${d.toInt()} m"
            )
            row.setOnClickListener { openHunt(egg.id) }
            eggList.addView(row)
            if (!egg.found) {
                eggList.addView(
                    UiKit.button(this, "Cattura", UiKit.ACCENT) { doCatch(egg.id) }
                )
            }
        }
        mgr.getPois().forEach { poi ->
            val d = mgr.distanceMeters(poi)
            blips.add(OutdoorRadarView.Blip(mgr.bearingTo(poi), d, 0xFF55AAFF.toInt()))
            val row = UiKit.row(this, "🏟️ ${poi.name}", "${d.toInt()} m")
            row.setOnClickListener { openPoi(poi.id) }
            poiList.addView(row)
        }
        radar.blips = blips
        radar.invalidate()
    }

    private fun doCatch(eggId: String) {
        val res = mgr.tryCatch(this, eggId)
        Toast.makeText(this, res.message, Toast.LENGTH_LONG).show()
        refreshUi()
    }

    private fun openHunt(eggId: String) {
        startActivity(Intent(this, OutdoorHuntActivity::class.java).apply {
            putExtra("eggId", eggId)
        })
    }

    private fun openPoi(poiId: String) {
        startActivity(Intent(this, POIInteractionActivity::class.java).apply {
            putExtra("poiId", poiId)
        })
    }

    private fun rarityColor(r: EggRarity): Int = when (r) {
        EggRarity.COMMON -> 0xFF9E9E9E.toInt()
        EggRarity.UNCOMMON -> 0xFF4CAF50.toInt()
        EggRarity.RARE -> 0xFF2196F3.toInt()
        EggRarity.EPIC -> 0xFF9C27B0.toInt()
        EggRarity.LEGENDARY -> 0xFFFFC107.toInt()
    }
}

package com.intelligame.huntix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.managers.WeatherZoneManager

class OutdoorWorldActivity : BaseNavActivity(), OnMapReadyCallback {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var googleMap: GoogleMap? = null
    private lateinit var eggList: LinearLayout
    private lateinit var poiList: LinearLayout
    private lateinit var weatherText: TextView
    private lateinit var statusText: TextView
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshUi(); refresh.postDelayed(this, 3000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.intelligame.huntix.R.layout.activity_outdoor_world)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101
            )
        }

        weatherText = findViewById(com.intelligame.huntix.R.id.tvWeather)
        statusText = findViewById(com.intelligame.huntix.R.id.tvStatus)
        eggList = findViewById(com.intelligame.huntix.R.id.eggList)
        poiList = findViewById(com.intelligame.huntix.R.id.poiList)

        val mapFragment = supportFragmentManager.findFragmentById(com.intelligame.huntix.R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        findViewById<android.view.View>(com.intelligame.huntix.R.id.btnNavigate).setOnClickListener {
            if (mgr.nearestUnfoundEgg() == null) {
                Toast.makeText(this, "Nessuna uova da cercare", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, ArNavigationActivity::class.java))
            }
        }
        findViewById<android.view.View>(com.intelligame.huntix.R.id.btnArCatch).setOnClickListener {
            startActivity(Intent(this, OutdoorArCatchActivity::class.java))
        }

        refresh.post(tick)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.uiSettings.isMapToolbarEnabled = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            updateMapCamera()
        }

        map.setOnMarkerClickListener { marker ->
            val tag = marker.tag as? String ?: return@setOnMarkerClickListener false
            when {
                tag.startsWith("egg_") -> { openHunt(tag.removePrefix("egg_")); true }
                tag.startsWith("poi_") -> { openPoi(tag.removePrefix("poi_")); true }
                else -> false
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            googleMap?.let {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    it.isMyLocationEnabled = true
                    updateMapCamera()
                }
            }
        } else if (requestCode == 101) {
            Toast.makeText(this, "Permesso GPS negato: usato spawn dimostrativo", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mgr.start(this)
        if (!refresh.hasCallbacks(tick)) refresh.post(tick)
        updateMapCamera()
    }

    override fun onPause() {
        super.onPause()
        refresh.removeCallbacks(tick)
        mgr.stop()
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        mgr.stop()
        super.onDestroy()
    }

    private fun updateMapCamera() {
        val loc = mgr.currentLocation ?: return
        val ll = LatLng(loc.latitude, loc.longitude)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16f))
    }

    private fun refreshUi() {
        eggList.removeAllViews()
        poiList.removeAllViews()

        val w = WeatherZoneManager.currentWeather
        val boostDesc = w.rarityBoost.entries.joinToString(", ") { (r, b) -> "$r ×${"%.1f".format(b)}" }
        weatherText.text = "${w.emoji} ${w.displayName} — Bonus rarità: $boostDesc"

        val eggCount = mgr.getEggs().count { !it.found }
        statusText.text = "🥚 $eggCount uova da trovare  ·  🏟️ ${mgr.getPois().size} palestre"

        googleMap?.clear()

        mgr.getEggs().forEach { egg ->
            val d = mgr.distanceMeters(egg)
            val ll = LatLng(egg.lat, egg.lng)

            if (!egg.found) {
                googleMap?.addMarker(MarkerOptions()
                    .position(ll)
                    .title("egg_${egg.id}")
                    .snippet("${egg.rarity.displayName}: ${d.toInt()} m")
                    .icon(BitmapDescriptorFactory.defaultMarker(rarityHue(egg.rarity)))
                )?.tag = "egg_${egg.id}"

                googleMap?.addCircle(CircleOptions()
                    .center(ll)
                    .radius(mgr.getCatchRadiusM(egg).toDouble())
                    .strokeColor(rarityColor(egg.rarity))
                    .strokeWidth(2f)
                    .fillColor(Color.argb(30, Color.red(rarityColor(egg.rarity)),
                        Color.green(rarityColor(egg.rarity)), Color.blue(rarityColor(egg.rarity))))
                )
            }

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
            val ll = LatLng(poi.lat, poi.lng)

            googleMap?.addMarker(MarkerOptions()
                .position(ll)
                .title("poi_${poi.id}")
                .snippet("${poi.name}: ${d.toInt()} m")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )?.tag = "poi_${poi.id}"

            val row = UiKit.row(this, "🏟️ ${poi.name}", "${d.toInt()} m")
            row.setOnClickListener { openPoi(poi.id) }
            poiList.addView(row)
        }
    }

    private fun doCatch(eggId: String) {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Già catturato", Toast.LENGTH_SHORT).show()
            return
        }
        CatchDialogHelper.showFoodSelection(this, egg, object : CatchDialogHelper.OnCatchReady {
            override fun onCatchReady(foodBonus: Float, xpMultiplier: Float) {
                val effectiveBonus = if (foodBonus > 0f) foodBonus else 1f
                val res = mgr.tryCatch(this@OutdoorWorldActivity, eggId, effectiveBonus)
                Toast.makeText(this@OutdoorWorldActivity, res.message, Toast.LENGTH_LONG).show()
                if (res.success && res.egg != null) {
                    EggOpeningAnimationActivity.start(this@OutdoorWorldActivity, res.egg.rarity, res.egg.name, res.egg.rarity.xpReward)
                }
                refreshUi()
            }
        })
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

    private fun rarityHue(r: EggRarity): Float = when (r) {
        EggRarity.COMMON -> BitmapDescriptorFactory.HUE_GREEN
        EggRarity.UNCOMMON -> BitmapDescriptorFactory.HUE_BLUE
        EggRarity.RARE -> BitmapDescriptorFactory.HUE_VIOLET
        EggRarity.EPIC -> BitmapDescriptorFactory.HUE_ORANGE
        EggRarity.LEGENDARY -> BitmapDescriptorFactory.HUE_YELLOW
    }

    private fun rarityColor(r: EggRarity): Int = when (r) {
        EggRarity.COMMON -> 0xFF9E9E9E.toInt()
        EggRarity.UNCOMMON -> 0xFF4CAF50.toInt()
        EggRarity.RARE -> 0xFF2196F3.toInt()
        EggRarity.EPIC -> 0xFF9C27B0.toInt()
        EggRarity.LEGENDARY -> 0xFFFFC107.toInt()
    }
}

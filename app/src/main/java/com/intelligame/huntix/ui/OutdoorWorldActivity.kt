package com.intelligame.huntix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.R
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.managers.WeatherZoneManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class OutdoorWorldActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var map: MapView? = null
    private lateinit var tvWeather: TextView
    private lateinit var tvStatus: TextView
    private lateinit var bottomSheet: LinearLayout
    private lateinit var tvSheetTitle: TextView
    private lateinit var tvSheetInfo: TextView
    private lateinit var tvSheetAction: TextView
    private lateinit var btnSheetAction: LinearLayout
    private var activeEggId: String? = null
    private var activePoiId: String? = null
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshUi(); refresh.postDelayed(this, 3000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_outdoor_world)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }

        tvWeather = findViewById(R.id.tvWeather)
        tvStatus = findViewById(R.id.tvStatus)
        bottomSheet = findViewById(R.id.bottomSheet)
        tvSheetTitle = findViewById(R.id.tvSheetTitle)
        tvSheetInfo = findViewById(R.id.tvSheetInfo)
        tvSheetAction = findViewById(R.id.tvSheetAction)
        btnSheetAction = findViewById(R.id.btnSheetAction)

        map = findViewById(R.id.mapView)
        map?.setTileSource(TileSourceFactory.MAPNIK)
        map?.setMultiTouchControls(true)
        map?.controller?.setZoom(17.0)

        map?.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = true
            override fun onZoom(event: ZoomEvent?): Boolean = true
        })

        map?.setOnClickListener { hideBottomSheet() }

        findViewById<View>(R.id.btnCenter).setOnClickListener { centerOnUser() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, OutdoorSetupActivity::class.java))
        }

        btnSheetAction.setOnClickListener {
            activeEggId?.let { eid -> doCatch(eid) }
            activePoiId?.let { pid -> openPoi(pid) }
        }

        refresh.post(tick)
    }

    override fun onResume() {
        super.onResume()
        mgr.start(this)
        map?.onResume()
        if (!refresh.hasCallbacks(tick)) refresh.post(tick)
        centerOnUser()
    }

    override fun onPause() {
        super.onPause()
        refresh.removeCallbacks(tick)
        map?.onPause()
        mgr.stop()
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        mgr.stop()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            centerOnUser()
        } else if (requestCode == 101) {
            Toast.makeText(this, "Permesso GPS negato: usato spawn dimostrativo", Toast.LENGTH_LONG).show()
        }
    }

    private fun centerOnUser() {
        val loc = mgr.currentLocation ?: return
        val gp = GeoPoint(loc.latitude, loc.longitude)
        map?.controller?.animateTo(gp)
        map?.controller?.setZoom(17.0)
    }

    private fun refreshUi() {
        val w = WeatherZoneManager.currentWeather
        val boostDesc = w.rarityBoost.entries.joinToString(", ") { (r, b) -> "$r x${"%.1f".format(b)}" }
        tvWeather.text = "${w.emoji} ${w.displayName} — Bonus: $boostDesc"

        val eggCount = mgr.getEggs().count { !it.found }
        tvStatus.text = "\uD83E\uDD5A $eggCount uova  ·  \uD83C\uDFDF\uFE0F ${mgr.getPois().size} palestre"

        map?.overlays?.clear()

        mgr.getEggs().forEach { egg ->
            if (!egg.found) {
                val gp = GeoPoint(egg.lat, egg.lng)

                val marker = Marker(map).apply {
                    position = gp
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = makeMarkerBitmap(egg.rarity)
                    title = egg.displayLabel
                    setOnMarkerClickListener { _, _ ->
                        showEggSheet(egg)
                        true
                    }
                }
                map?.overlays?.add(marker)

                val circle = Polygon().apply {
                    points = createCirclePoints(gp, mgr.getCatchRadiusM(egg).toDouble())
                    outlinePaint.color = rarityColor(egg.rarity)
                    outlinePaint.strokeWidth = 4f
                    fillPaint.color = Color.argb(35, Color.red(rarityColor(egg.rarity)),
                        Color.green(rarityColor(egg.rarity)), Color.blue(rarityColor(egg.rarity)))
                }
                map?.overlays?.add(circle)
            }
        }

        mgr.getPois().forEach { poi ->
            val gp = GeoPoint(poi.lat, poi.lng)
            val marker = Marker(map).apply {
                position = gp
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = makePoiBitmap()
                title = poi.name
                setOnMarkerClickListener { _, _ ->
                    showPoiSheet(poi)
                    true
                }
            }
            map?.overlays?.add(marker)
        }

        map?.invalidate()
    }

    private fun showEggSheet(egg: com.intelligame.huntix.WorldEgg) {
        val d = mgr.distanceMeters(egg)
        activeEggId = egg.id
        activePoiId = null
        tvSheetTitle.text = "${egg.rarity.emoji} ${egg.displayLabel}"
        tvSheetInfo.text = "${egg.rarity.displayName} — ${d.toInt()} m — ${egg.element.name}"
        tvSheetAction.text = "Cattura!"
        btnSheetAction.visibility = View.VISIBLE
        bottomSheet.visibility = View.VISIBLE
    }

    private fun showPoiSheet(poi: OutdoorManager.Poi) {
        val d = mgr.distanceMeters(poi)
        activePoiId = poi.id
        activeEggId = null
        tvSheetTitle.text = "\uD83C\uDFDF\uFE0F ${poi.name}"
        tvSheetInfo.text = "${d.toInt()} m"
        tvSheetAction.text = "Spinna"
        btnSheetAction.visibility = View.VISIBLE
        bottomSheet.visibility = View.VISIBLE
    }

    private fun hideBottomSheet() {
        bottomSheet.visibility = View.GONE
        activeEggId = null
        activePoiId = null
    }

    private fun doCatch(eggId: String) {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Gia catturato", Toast.LENGTH_SHORT).show()
            hideBottomSheet()
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
                hideBottomSheet()
                refreshUi()
            }
        })
    }

    private fun openPoi(poiId: String) {
        startActivity(Intent(this, POIInteractionActivity::class.java).apply {
            putExtra("poiId", poiId)
        })
        hideBottomSheet()
    }

    private fun makeMarkerBitmap(rarity: EggRarity): android.graphics.Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = rarityColor(rarity)
        p.style = Paint.Style.FILL
        c.drawCircle(size / 2f, size / 2f, size / 2f - 4f, p)
        p.color = Color.WHITE
        p.textSize = 20f
        p.textAlign = Paint.Align.CENTER
        val emoji = when (rarity) {
            EggRarity.COMMON -> "C"
            EggRarity.UNCOMMON -> "U"
            EggRarity.RARE -> "R"
            EggRarity.EPIC -> "E"
            EggRarity.LEGENDARY -> "L"
        }
        c.drawText(emoji, size / 2f, size / 2f + 7f, p)
        return bmp
    }

    private fun makePoiBitmap(): android.graphics.Bitmap {
        val size = 56
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2196F3.toInt()
        p.style = Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(size / 2f, 0f)
        path.lineTo(size.toFloat(), size * 0.7f)
        path.lineTo(size / 2f, size.toFloat())
        path.lineTo(0f, size * 0.7f)
        path.close()
        c.drawPath(path, p)
        p.color = Color.WHITE
        p.textSize = 18f
        p.textAlign = Paint.Align.CENTER
        c.drawText("\u26FF", size / 2f, size * 0.55f, p)
        return bmp
    }

    private fun createCirclePoints(center: GeoPoint, radiusMeters: Double, numPoints: Int = 64): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val latRad = Math.toRadians(center.latitude)
        val dLat = radiusMeters / 111320.0
        val dLng = radiusMeters / (111320.0 * Math.cos(latRad))
        for (i in 0..numPoints) {
            val angle = 2.0 * Math.PI * i / numPoints
            points.add(GeoPoint(
                center.latitude + dLat * Math.cos(angle),
                center.longitude + dLng * Math.sin(angle)
            ))
        }
        return points
    }

    private fun rarityColor(r: EggRarity): Int = when (r) {
        EggRarity.COMMON -> 0xFF9E9E9E.toInt()
        EggRarity.UNCOMMON -> 0xFF4CAF50.toInt()
        EggRarity.RARE -> 0xFF2196F3.toInt()
        EggRarity.EPIC -> 0xFF9C27B0.toInt()
        EggRarity.LEGENDARY -> 0xFFFFC107.toInt()
    }
}

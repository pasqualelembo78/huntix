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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.OutdoorSetupActivity
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.R
import com.intelligame.huntix.gamification.LiveEventManager
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.WeatherZoneManager
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.camera.CameraPosition.Builder
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.VectorSource
import java.util.Calendar
import java.util.Locale

class OutdoorWorldActivity : BaseNavActivity() {

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var mapView: MapView? = null
    private var mapLibre: MapLibreMap? = null
    private lateinit var tvWeatherEmoji: TextView
    private lateinit var weatherTooltip: LinearLayout
    private lateinit var tvWeatherName: TextView
    private lateinit var tvWeatherBonus: TextView
    private lateinit var badgeWeather: FrameLayout
    private lateinit var tvEggCount: TextView
    private lateinit var tvGymCount: TextView
    private lateinit var weatherOverlay: View
    private lateinit var bottomSheet: LinearLayout
    private lateinit var tvSheetTitle: TextView
    private lateinit var tvSheetInfo: TextView
    private lateinit var btnSheetHunt: LinearLayout
    private lateinit var tvSheetHunt: TextView
    private lateinit var btnSheetAr: LinearLayout
    private lateinit var tvSheetAr: TextView
    private var activeEggId: String? = null
    private var activePoiId: String? = null
    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refreshUi(); refresh.postDelayed(this, 3000) }
    }

    // ── Phase 1: new HUD elements ─────────────────────────────
    private lateinit var tvPlayerLevel: TextView
    private lateinit var expBarFill: View
    private lateinit var tvPlayerXp: TextView
    private lateinit var tvMvcCount: TextView
    private lateinit var badgeEvent: FrameLayout
    private lateinit var tvEventEmoji: TextView
    private lateinit var tvEventTimer: TextView
    private lateinit var radarView: OutdoorRadarView
    private lateinit var btnCatch: FrameLayout
    private lateinit var tvCatchHint: TextView
    private lateinit var btnCompass: TextView
    private lateinit var btnPhoto: TextView
    private lateinit var btnCalendar: TextView
    private lateinit var incubationProgress: LinearLayout
    private lateinit var tvIncubationKm: TextView
    private lateinit var incubationBarFill: View
    private lateinit var skyOverlay: SkyEventOverlay

    // ── Phase 3: sensor + animation state ─────────────────────
    private var currentHeading: Float = 0f
    private var walkTick: Int = 0
    private var sensorManager: android.hardware.SensorManager? = null
    private var hasSensor: Boolean = false
    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            val rotation = FloatArray(9)
            android.hardware.SensorManager.getRotationMatrixFromVector(rotation, event.values)
            val orientation = FloatArray(3)
            android.hardware.SensorManager.getOrientation(rotation, orientation)
            currentHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (currentHeading < 0) currentHeading += 360f
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        setContentView(R.layout.activity_outdoor_world)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }

        tvWeatherEmoji = findViewById(R.id.tvWeatherEmoji)
        weatherTooltip = findViewById(R.id.weatherTooltip)
        tvWeatherName = findViewById(R.id.tvWeatherName)
        tvWeatherBonus = findViewById(R.id.tvWeatherBonus)
        badgeWeather = findViewById(R.id.badgeWeather)
        tvEggCount = findViewById(R.id.tvEggCount)
        tvGymCount = findViewById(R.id.tvGymCount)
        weatherOverlay = findViewById(R.id.weatherOverlay)
        bottomSheet = findViewById(R.id.bottomSheet)
        tvSheetTitle = findViewById(R.id.tvSheetTitle)
        tvSheetInfo = findViewById(R.id.tvSheetInfo)
        btnSheetHunt = findViewById(R.id.btnSheetHunt)
        tvSheetHunt = findViewById(R.id.tvSheetHunt)
        btnSheetAr = findViewById(R.id.btnSheetAr)
        tvSheetAr = findViewById(R.id.tvSheetAr)

        // Phase 1 bindings
        tvPlayerLevel = findViewById(R.id.tvPlayerLevel)
        expBarFill = findViewById(R.id.expBarFill)
        tvPlayerXp = findViewById(R.id.tvPlayerXp)
        tvMvcCount = findViewById(R.id.tvMvcCount)
        badgeEvent = findViewById(R.id.badgeEvent)
        tvEventEmoji = findViewById(R.id.tvEventEmoji)
        tvEventTimer = findViewById(R.id.tvEventTimer)
        radarView = findViewById(R.id.radarView)
        btnCatch = findViewById(R.id.btnCatch)
        tvCatchHint = findViewById(R.id.tvCatchHint)
        btnCompass = findViewById(R.id.btnCompass)
        btnPhoto = findViewById(R.id.btnPhoto)
        btnCalendar = findViewById(R.id.btnCalendar)
        incubationProgress = findViewById(R.id.incubationProgress)
        tvIncubationKm = findViewById(R.id.tvIncubationKm)
        incubationBarFill = findViewById(R.id.incubationBarFill)
        skyOverlay = findViewById(R.id.skyOverlay)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync { map ->
            mapLibre = map

            map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                val loc = mgr.currentLocation
                val initPos = if (loc != null) {
                    LatLng(loc.latitude, loc.longitude)
                } else {
                    LatLng(41.9028, 12.4964)
                }

                map.cameraPosition = Builder()
                    .target(initPos)
                    .zoom(17.0)
                    .tilt(60.0)
                    .bearing(0.0)
                    .build()

                addBuildingLayer(style)
            }

            map.addOnMapClickListener {
                hideBottomSheet()
                true
            }

            map.setOnMarkerClickListener { marker ->
                val title = marker.title ?: return@setOnMarkerClickListener false
                val egg = mgr.getEggs().firstOrNull { it.displayLabel == title && !it.found }
                val poi = mgr.getPois().firstOrNull { it.name == title }
                if (egg != null) showEggSheet(egg)
                else if (poi != null) showPoiSheet(poi)
                true
            }
        }

        // Navigation buttons
        findViewById<View>(R.id.btnCenter).setOnClickListener { centerOnUser() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, OutdoorSetupActivity::class.java))
        }

        // Phase 1: Compass — reset bearing to north
        btnCompass.setOnClickListener {
            val cur = mapLibre?.cameraPosition ?: return@setOnClickListener
            mapLibre?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(cur.target!!, 17.0)
            )
            // Reset bearing to 0 (north)
            mapLibre?.cameraPosition = Builder()
                .target(cur.target)
                .zoom(cur.zoom)
                .tilt(cur.tilt)
                .bearing(0.0)
                .build()
        }

        // Phase 1: Photo — screenshot
        btnPhoto.setOnClickListener { takeScreenshot() }

        // Phase 1: Calendar — open events
        btnCalendar.setOnClickListener {
            startActivity(Intent(this, com.intelligame.huntix.ui.LiveEventsActivity::class.java))
        }

        // Phase 1: Central catch button
        btnCatch.setOnClickListener {
            val nearestEgg = mgr.getEggs()
                .filter { !it.found }
                .minByOrNull { mgr.distanceMeters(it) }
            if (nearestEgg != null && mgr.distanceMeters(nearestEgg) <= mgr.getCatchRadiusM(nearestEgg)) {
                showEggSheet(nearestEgg)
            } else {
                Toast.makeText(this, "Nessuna uova in raggio", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom sheet buttons
        btnSheetHunt.setOnClickListener {
            activeEggId?.let { doHunt(it) }
            activePoiId?.let { openPoi(it) }
        }

        btnSheetAr.setOnClickListener {
            activeEggId?.let { doArNavigation(it) }
        }

        badgeWeather.setOnClickListener {
            weatherTooltip.visibility =
                if (weatherTooltip.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        refresh.post(tick)

        // Phase 3: compass sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as? android.hardware.SensorManager
        val rotationVector = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager?.registerListener(sensorListener, rotationVector, android.hardware.SensorManager.SENSOR_DELAY_UI)
            hasSensor = true
        }
    }

    override fun onResume() {
        super.onResume()
        mgr.start(this)
        mapView?.onResume()
        if (!refresh.hasCallbacks(tick)) refresh.post(tick)
        centerOnUser()
        updateSkyColor()
    }

    override fun onPause() {
        super.onPause()
        refresh.removeCallbacks(tick)
        mapView?.onPause()
        mgr.stop()
        if (hasSensor) sensorManager?.unregisterListener(sensorListener)
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        mapView?.onDestroy()
        mgr.stop()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
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
        val latLng = LatLng(loc.latitude, loc.longitude)
        mapLibre?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.0))
    }

    // ─── Main UI refresh ───────────────────────────────────────

    private fun refreshUi() {
        val w = WeatherZoneManager.currentWeather
        walkTick = (walkTick + 1) % 64

        tvWeatherEmoji.text = w.emoji
        tvWeatherName.text = w.displayName
        val boostDesc = w.rarityBoost.entries.joinToString("\n") { (r, b) -> "$r  x${"%.1f".format(b)}" }
        tvWeatherBonus.text = "Bonus rarita:\n$boostDesc"

        val eggCount = mgr.getEggs().count { !it.found }
        tvEggCount.text = "$eggCount"
        tvGymCount.text = "${mgr.getPois().size}"

        // Phase 1.1: Player level + EXP bar
        refreshPlayerHud()

        // Phase 1.1: MVC currency
        val mvc = SavedManager.getMvcBalance(this)
        tvMvcCount.text = "${mvc.toLong()}"

        // Phase 1.6: Live event badge
        refreshEventBadge()

        // Phase 1.5: Radar view
        refreshRadar()

        // Phase 4.1: Sky event overlay
        refreshSkyOverlay()

        // Phase 1.4: Catch button state
        refreshCatchButton()

        // Phase 5.2: Proximity hint
        checkProximityHint()

        // Incubation progress in bottom sheet
        refreshIncubationProgress()

        updateWeatherParticles(w)

        mapLibre?.let { map ->
            map.clear()

            mgr.getEggs().forEach { egg ->
                if (!egg.found) {
                    val color = rarityColor(egg.rarity)
                    val center = LatLng(egg.lat, egg.lng)
                    val points = createCirclePoints(center, mgr.getCatchRadiusM(egg).toDouble())
                    map.addPolygon(PolygonOptions()
                        .addAll(points)
                        .strokeColor(color)
                        .fillColor(Color.argb(35, Color.red(color), Color.green(color), Color.blue(color)))
                    )
                }
            }

            val iconFactory = IconFactory.getInstance(this)

            mgr.getEggs().forEach { egg ->
                if (!egg.found) {
                    val latLng = LatLng(egg.lat, egg.lng)
                    map.addMarker(MarkerOptions()
                        .position(latLng)
                        .icon(iconFactory.fromBitmap(makeMarkerBitmap(egg.rarity)))
                        .title(egg.displayLabel)
                        .snippet("${egg.rarity.displayName} — ${egg.element.name}")
                    )
                }
            }

            mgr.getPois().forEach { poi ->
                val latLng = LatLng(poi.lat, poi.lng)
                val bitmap = if (mgr.isPoiOnCooldown(poi)) {
                    makePoiBitmapGray(poi.type)
                } else {
                    makePoiBitmap(poi.type)
                }
                map.addMarker(MarkerOptions()
                    .position(latLng)
                    .icon(iconFactory.fromBitmap(bitmap))
                    .title(poi.name)
                    .snippet(poi.type)
                )
            }

        // Phase 3: Player avatar marker + buddy + direction indicator
        val currentLoc = mgr.currentLocation
        if (currentLoc != null) {
            val profile = PlayerProfileManager.myProfile
            val level = profile?.level ?: 1

            // 3.1: Player avatar (RPM or fallback)
            val avatarDrawable = com.intelligame.huntix.avatar.AvatarMapRenderer
                .makeAvatarMarkerDrawable(resources, 104, walkTick, level, currentHeading, this)
            val avatarBitmap = avatarDrawable.bitmap
            map.addMarker(MarkerOptions()
                .position(LatLng(currentLoc.latitude, currentLoc.longitude))
                .icon(iconFactory.fromBitmap(avatarBitmap))
                .title("Io")
                .snippet("Lv.$level")
            )

            // 3.2: Buddy creature marker (offset slightly)
            val buddy = com.intelligame.huntix.managers.SurpriseManager.getAll(this)
                .firstOrNull { it.isBuddy }
            if (buddy != null) {
                val creature = com.intelligame.huntix.SurpriseCreature.ALL
                    .firstOrNull { it.id == buddy.creatureId }
                if (creature != null) {
                    val buddyBitmap = makeBuddyBitmap(creature.emoji)
                    // Offset the buddy marker ~20m in a fixed direction
                    val offsetLat = currentLoc.latitude + 0.00018
                    val offsetLng = currentLoc.longitude + 0.00012
                    map.addMarker(MarkerOptions()
                        .position(LatLng(offsetLat, offsetLng))
                        .icon(iconFactory.fromBitmap(buddyBitmap))
                        .title(creature.name)
                        .snippet("Compagno - ${buddy.candies} caramelle")
                    )
                }
            }

            // 3.3: Direction indicator (triangle pointing where player faces)
            val dirBitmap = makeDirectionBitmap(currentHeading)
            map.addMarker(MarkerOptions()
                .position(LatLng(currentLoc.latitude, currentLoc.longitude))
                .icon(iconFactory.fromBitmap(dirBitmap))
                .title("")
            )
        }
        }
    }

    // ─── Phase 1.1: Player HUD ────────────────────────────────

    private fun refreshPlayerHud() {
        val profile = PlayerProfileManager.myProfile
        if (profile != null) {
            tvPlayerLevel.text = "Lv.${profile.level}"
            val progress = profile.levelProgressPercent
            val lp = expBarFill.layoutParams
            lp.width = (60 * resources.displayMetrics.density * progress / 100).toInt()
            expBarFill.layoutParams = lp
            tvPlayerXp.text = "${profile.xpProgressInLevel}/${profile.xpNeededForNextLevel}"
        } else {
            tvPlayerLevel.text = "Lv.1"
            tvPlayerXp.text = "0/150"
        }
    }

    // ─── Phase 1.5: Radar ─────────────────────────────────────

    private fun refreshRadar() {
        val eggs = mgr.getEggs().filter { !it.found }
        val pois = mgr.getPois()
        val loc = mgr.currentLocation

        if (loc == null || (eggs.isEmpty() && pois.isEmpty())) {
            radarView.visibility = View.GONE
            return
        }

        radarView.visibility = View.VISIBLE

        val blips = mutableListOf<OutdoorRadarView.Blip>()

        for (egg in eggs) {
            val d = mgr.distanceMeters(egg)
            if (d > 500) continue
            val bearing = mgr.bearingTo(egg.lat, egg.lng)
            blips.add(OutdoorRadarView.Blip(
                bearingDeg = bearing,
                distanceM = d,
                color = rarityColor(egg.rarity),
                label = egg.rarity.emoji
            ))
        }

        for (poi in pois) {
            val d = mgr.distanceMeters(poi)
            if (d > 500) continue
            val bearing = mgr.bearingTo(poi.lat, poi.lng)
            blips.add(OutdoorRadarView.Blip(
                bearingDeg = bearing,
                distanceM = d,
                color = 0xFF42A5F5.toInt(),
                label = "\uD83C\uDFDF"
            ))
        }

        radarView.blips = blips.sortedBy { it.distanceM }
        radarView.maxRangeM = 500f
        radarView.headingDeg = currentHeading
        radarView.invalidate()
    }

    // ─── Phase 1.4: Catch button ──────────────────────────────

    private fun refreshCatchButton() {
        if (mgr.currentLocation == null) return
        val nearestEgg = mgr.getEggs()
            .filter { !it.found }
            .minByOrNull { mgr.distanceMeters(it) }

        if (nearestEgg != null && mgr.distanceMeters(nearestEgg) <= mgr.getCatchRadiusM(nearestEgg)) {
            btnCatch.visibility = View.VISIBLE
            tvCatchHint.visibility = View.VISIBLE
            tvCatchHint.text = "${nearestEgg.rarity.emoji} ${nearestEgg.displayLabel} a ${mgr.distanceMeters(nearestEgg).toInt()}m"
        } else if (nearestEgg != null && mgr.distanceMeters(nearestEgg) <= 200) {
            btnCatch.visibility = View.VISIBLE
            tvCatchHint.visibility = View.VISIBLE
            tvCatchHint.text = "Avvicinati per catturare (${mgr.distanceMeters(nearestEgg).toInt()}m)"
        } else {
            btnCatch.visibility = View.GONE
            tvCatchHint.visibility = View.GONE
        }
    }

    // ─── Phase 1.6: Event badge ───────────────────────────────

    private fun refreshEventBadge() {
        val activeEvent = LiveEventManager.getCurrentEvent()
        if (activeEvent != null && activeEvent.isActive) {
            badgeEvent.visibility = View.VISIBLE
            tvEventEmoji.text = activeEvent.emoji
            val mins = activeEvent.remainingMinutes
            tvEventTimer.text = if (mins >= 60) {
                "${mins / 60}h ${mins % 60}m"
            } else {
                "${mins}m"
            }
            btnCalendar.visibility = View.VISIBLE
        } else {
            badgeEvent.visibility = View.GONE
            val upcoming = LiveEventManager.getUpcomingEvent()
            if (upcoming != null) {
                btnCalendar.visibility = View.VISIBLE
            } else {
                btnCalendar.visibility = View.GONE
            }
        }
    }

    // ─── Phase 5.2: Proximity hint ─────────────────────────────

    private var lastProximityEggId: String? = null
    private var lastProximityTime: Long = 0L

    private fun checkProximityHint() {
        val now = System.currentTimeMillis()
        if (now - lastProximityTime < 30_000) return  // max 1 ogni 30s

        val nearestEgg = mgr.getEggs()
            .filter { !it.found }
            .minByOrNull { mgr.distanceMeters(it) } ?: return

        val dist = mgr.distanceMeters(nearestEgg)
        if (dist < 50f && nearestEgg.id != lastProximityEggId) {
            lastProximityEggId = nearestEgg.id
            lastProximityTime = now
            // Vibrate
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            Toast.makeText(this, "${nearestEgg.rarity.emoji} ${nearestEgg.displayLabel} vicino! (${dist.toInt()}m)", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Phase 4.1: Sky events ───────────────────────────────

    private fun refreshSkyOverlay() {
        val activeEvent = LiveEventManager.getCurrentEvent()
        if (activeEvent != null && activeEvent.isActive) {
            val type = when (activeEvent.type) {
                LiveEventManager.EventType.EGG_RUSH -> SkyEventOverlay.SkyEvent.EGG_RUSH
                LiveEventManager.EventType.DOUBLE_XP -> SkyEventOverlay.SkyEvent.DOUBLE_XP
                LiveEventManager.EventType.MYSTERY_EGGS -> SkyEventOverlay.SkyEvent.MYSTERY_EGGS
                LiveEventManager.EventType.GOLDEN_HOUR -> SkyEventOverlay.SkyEvent.GOLDEN_HOUR
                LiveEventManager.EventType.LEGENDARY_WEEK -> SkyEventOverlay.SkyEvent.LEGENDARY_WEEK
                else -> SkyEventOverlay.SkyEvent.NONE
            }
            skyOverlay.setEventType(type)
        } else {
            skyOverlay.setEventType(SkyEventOverlay.SkyEvent.NONE)
        }
    }

    // ─── Incubation progress ───────────────────────────────────

    private fun refreshIncubationProgress() {
        // Check if any egg in the bottom sheet is being incubated
        val currentEggId = activeEggId ?: return
        val egg = mgr.getEgg(currentEggId) ?: return
        refreshIncubationForEgg(egg)
    }

    private fun refreshIncubationForEgg(egg: com.intelligame.huntix.WorldEgg) {
        val activeEggs = com.intelligame.huntix.managers.IncubatorManager.getActiveEggs(this)
        val incubating = activeEggs.firstOrNull {
            it.rarityId == egg.rarity.name.lowercase() && !it.isReady
        }

        if (incubating != null) {
            incubationProgress.visibility = View.VISIBLE
            tvIncubationKm.text = "%.1f / %.0f km".format(
                incubating.distanceWalked, incubating.distanceRequired
            )
            val lp = incubationBarFill.layoutParams
            val progressPercent = (incubating.progress * 100).toInt()
            lp.width = (120 * resources.displayMetrics.density * progressPercent / 100).toInt()
            incubationBarFill.layoutParams = lp
        } else {
            incubationProgress.visibility = View.GONE
        }
    }

    // ─── Phase 1.3: Screenshot ────────────────────────────────

    private fun takeScreenshot() {
        try {
            val rootView = window.decorView.rootView
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)

            val path = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            )
            val file = java.io.File(path, "huntix_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Condividi screenshot"))
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Map helpers ───────────────────────────────────────────

    private fun createCirclePoints(center: LatLng, radiusMeters: Double, numPoints: Int = 64): List<LatLng> {
        val points = mutableListOf<LatLng>()
        val latRad = Math.toRadians(center.latitude)
        val dLat = radiusMeters / 111320.0
        val dLng = radiusMeters / (111320.0 * Math.cos(latRad))
        for (i in 0..numPoints) {
            val angle = 2.0 * Math.PI * i / numPoints
            points.add(LatLng(
                center.latitude + dLat * Math.cos(angle),
                center.longitude + dLng * Math.sin(angle)
            ))
        }
        return points
    }

    private fun addBuildingLayer(style: Style) {
        try {
            val sourceId = "openmaptiles"
            if (style.getSource(sourceId) == null) {
                val source = VectorSource(sourceId, "https://tiles.openfreemap.org/planet")
                style.addSource(source)
            }

            if (style.getLayer("buildings-3d") != null) return

            val buildings = FillExtrusionLayer("buildings-3d", sourceId).apply {
                sourceLayer = "building"
                setProperties(
                    PropertyFactory.fillExtrusionColor(Expression.literal("#1A1040")),
                    PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                    PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                    PropertyFactory.fillExtrusionOpacity(0.6f)
                )
            }
            style.addLayer(buildings)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSkyColor() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val skyColor = when {
            hour in 5..6   -> Color.parseColor("#FF8A65")
            hour in 7..16  -> Color.parseColor("#4A90D9")
            hour in 17..18 -> Color.parseColor("#FF6F00")
            hour in 19..20 -> Color.parseColor("#1A237E")
            else           -> Color.parseColor("#0D0620")
        }
        mapView?.setBackgroundColor(skyColor)
        refresh.postDelayed({
            mapView?.setBackgroundColor(Color.TRANSPARENT)
        }, 100)
    }

    // ─── Bottom sheet ──────────────────────────────────────────

    private fun showEggSheet(egg: com.intelligame.huntix.WorldEgg) {
        val d = mgr.distanceMeters(egg)
        activeEggId = egg.id
        activePoiId = null
        tvSheetTitle.text = "${egg.rarity.emoji} ${egg.displayLabel}"
        tvSheetInfo.text = "${egg.rarity.displayName} — ${d.toInt()} m — ${egg.element.name}"
        tvSheetHunt.text = "\uD83C\uDFAF Cattura"
        btnSheetHunt.visibility = View.VISIBLE
        btnSheetAr.visibility = View.VISIBLE
        bottomSheet.visibility = View.VISIBLE

        // Phase 5.1: Show incubation progress if this egg is being incubated
        refreshIncubationForEgg(egg)
    }

    private fun showPoiSheet(poi: OutdoorManager.Poi) {
        val d = mgr.distanceMeters(poi)
        activePoiId = poi.id
        activeEggId = null
        tvSheetTitle.text = "\uD83C\uDFDF\uFE0F ${poi.name}"
        tvSheetInfo.text = "${d.toInt()} m"
        tvSheetHunt.text = "Spinna"
        btnSheetHunt.visibility = View.VISIBLE
        btnSheetAr.visibility = View.GONE
        bottomSheet.visibility = View.VISIBLE
    }

    private fun hideBottomSheet() {
        bottomSheet.visibility = View.GONE
        activeEggId = null
        activePoiId = null
    }

    // ─── Actions ───────────────────────────────────────────────

    private fun doHunt(eggId: String) {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Gia catturato", Toast.LENGTH_SHORT).show()
            hideBottomSheet()
            return
        }
        startActivity(Intent(this, OutdoorHuntActivity::class.java).apply {
            putExtra("eggId", eggId)
        })
        hideBottomSheet()
    }

    private fun doArNavigation(eggId: String) {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Gia catturato", Toast.LENGTH_SHORT).show()
            hideBottomSheet()
            return
        }
        startActivity(Intent(this, ArNavigationActivity::class.java).apply {
            putExtra("eggId", eggId)
        })
        hideBottomSheet()
    }

    private fun openPoi(poiId: String) {
        startActivity(Intent(this, POIInteractionActivity::class.java).apply {
            putExtra("poiId", poiId)
        })
        hideBottomSheet()
    }

    // ─── Bitmap generators ─────────────────────────────────────

    private fun makeMarkerBitmap(rarity: EggRarity): Bitmap {
        val w = 80; val h = 100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val color = rarityColor(rarity)
        val headR = 28f
        val headY = h - 55f

        val pin = android.graphics.Path()
        pin.moveTo(w / 2f, h.toFloat())
        pin.quadTo(w / 2f - 8f, h - 30f, w / 2f - headR, headY)
        pin.arcTo(w / 2f - headR, headY - headR, w / 2f + headR, headY + headR, 180f, -180f, false)
        pin.quadTo(w / 2f + 8f, h - 30f, w / 2f, h.toFloat())
        pin.close()

        p.color = color
        p.style = Paint.Style.FILL
        c.drawPath(pin, p)

        p.color = Color.argb(40, 255, 255, 255)
        c.drawCircle(w / 2f - 4f, headY - 6f, headR - 8f, p)

        p.color = Color.WHITE
        p.style = Paint.Style.FILL
        val eggW = 10f; val eggH = 14f
        val eggPath = android.graphics.Path()
        eggPath.addOval(w / 2f - eggW, headY - eggH, w / 2f + eggW, headY + eggH, android.graphics.Path.Direction.CW)
        c.drawPath(eggPath, p)

        p.color = color
        val dotR = 3f
        c.drawCircle(w / 2f, headY + 2f, dotR, p)

        return bmp
    }

    private fun makePoiBitmap(type: String = "gym"): Bitmap {
        val w = 80; val h = 100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Color based on type
        val color = when (type) {
            "pokestop" -> 0xFF42A5F5.toInt()  // blue
            "sponsor" -> 0xFFFFD700.toInt()   // gold
            "arena" -> 0xFFFF5722.toInt()     // red-orange
            else -> 0xFF42A5F5.toInt()         // default blue (gym)
        }

        val headR = 26f
        val headY = h - 52f

        val pin = android.graphics.Path()
        pin.moveTo(w / 2f, h.toFloat())
        pin.quadTo(w / 2f - 8f, h - 28f, w / 2f - headR, headY)
        pin.arcTo(w / 2f - headR, headY - headR, w / 2f + headR, headY + headR, 180f, -180f, false)
        pin.quadTo(w / 2f + 8f, h - 28f, w / 2f, h.toFloat())
        pin.close()

        p.color = color
        p.style = Paint.Style.FILL
        c.drawPath(pin, p)

        p.color = Color.WHITE
        p.style = Paint.Style.FILL

        when (type) {
            "gym" -> {
                // Gym: shield shape
                val bx = w / 2f; val by = headY
                val bw = 14f; val bh = 16f
                val bld = android.graphics.Path()
                bld.addRect(bx - bw, by - bh, bx + bw, by + bh, android.graphics.Path.Direction.CW)
                c.drawPath(bld, p)
                p.color = color
                c.drawRect(bx - 4f, by - 5f, bx + 4f, by + 5f, p)
                c.drawRect(bx - bw + 3f, by - bh + 3f, bx - bw + 7f, by - 3f, p)
                c.drawRect(bx + bw - 7f, by - bh + 3f, bx + bw - 3f, by - 3f, p)
            }
            "pokestop" -> {
                // Pokestop: cube/box shape
                val bx = w / 2f; val by = headY
                val bs = 12f
                c.drawRect(bx - bs, by - bs, bx + bs, by + bs, p)
                p.color = color
                c.drawRect(bx - 3f, by - bs, bx + 3f, by - bs + 6f, p)
            }
            "sponsor" -> {
                // Sponsor: star shape
                val cx = w / 2f; val cy = headY
                val outerR = 14f; val innerR = 6f
                val star = android.graphics.Path()
                for (i in 0 until 5) {
                    val angle = Math.toRadians((i * 72 - 90).toDouble())
                    val innerAngle = Math.toRadians((i * 72 + 36 - 90).toDouble())
                    val ox = cx + outerR * Math.cos(angle).toFloat()
                    val oy = cy + outerR * Math.sin(angle).toFloat()
                    val ix = cx + innerR * Math.cos(innerAngle).toFloat()
                    val iy = cy + innerR * Math.sin(innerAngle).toFloat()
                    if (i == 0) star.moveTo(ox, oy) else star.lineTo(ox, oy)
                    star.lineTo(ix, iy)
                }
                star.close()
                c.drawPath(star, p)
            }
            "arena" -> {
                // Arena: circle with border
                val cx = w / 2f; val cy = headY
                c.drawCircle(cx, cy, 14f, p)
                p.color = color
                c.drawCircle(cx, cy, 10f, p)
                p.color = Color.WHITE
                c.drawCircle(cx, cy, 6f, p)
            }
            else -> {
                // Default (same as gym)
                val bx = w / 2f; val by = headY
                val bw = 14f; val bh = 16f
                val bld = android.graphics.Path()
                bld.addRect(bx - bw, by - bh, bx + bw, by + bh, android.graphics.Path.Direction.CW)
                c.drawPath(bld, p)
                p.color = color
                c.drawRect(bx - 4f, by - 5f, bx + 4f, by + 5f, p)
            }
        }

        return bmp
    }

    private fun makePoiBitmapGray(@Suppress("UNUSED_PARAMETER") type: String = "gym"): Bitmap {
        val w = 80; val h = 100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val color = 0xFF616161.toInt()  // gray
        val headR = 26f
        val headY = h - 52f

        val pin = android.graphics.Path()
        pin.moveTo(w / 2f, h.toFloat())
        pin.quadTo(w / 2f - 8f, h - 28f, w / 2f - headR, headY)
        pin.arcTo(w / 2f - headR, headY - headR, w / 2f + headR, headY + headR, 180f, -180f, false)
        pin.quadTo(w / 2f + 8f, h - 28f, w / 2f, h.toFloat())
        pin.close()

        p.color = color
        p.style = Paint.Style.FILL
        c.drawPath(pin, p)

        p.color = Color.parseColor("#9E9E9E")
        p.style = Paint.Style.FILL
        val bx = w / 2f; val by = headY
        val bw = 14f; val bh = 16f
        val bld = android.graphics.Path()
        bld.addRect(bx - bw, by - bh, bx + bw, by + bh, android.graphics.Path.Direction.CW)
        c.drawPath(bld, p)

        return bmp
    }

    private fun makeBuddyBitmap(emoji: String): Bitmap {
        val w = 56; val h = 56
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer circle (light purple)
        p.color = 0xFF9C27B0.toInt()
        p.style = Paint.Style.FILL
        c.drawCircle(w / 2f, h / 2f, 26f, p)

        // Inner circle (white)
        p.color = Color.WHITE
        c.drawCircle(w / 2f, h / 2f, 22f, p)

        // Creature emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(emoji, w / 2f, h / 2f + 10f, textPaint)

        return bmp
    }

    private fun makeDirectionBitmap(heading: Float): Bitmap {
        val w = 40; val h = 40
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD700.toInt()
            style = Paint.Style.FILL
        }

        // Triangle pointing up, rotated by heading
        val path = android.graphics.Path()
        val radius = 16f
        val angleRad = Math.toRadians(heading.toDouble())

        // Tip of triangle (in direction of heading)
        val tipX = cx + (radius * Math.sin(angleRad)).toFloat()
        val tipY = cy - (radius * Math.cos(angleRad)).toFloat()

        // Base points (perpendicular to heading)
        val baseAngle1 = angleRad + Math.PI * 0.75
        val baseAngle2 = angleRad - Math.PI * 0.75
        val baseR = radius * 0.45f

        val base1X = cx + (baseR * Math.sin(baseAngle1)).toFloat()
        val base1Y = cy - (baseR * Math.cos(baseAngle1)).toFloat()
        val base2X = cx + (baseR * Math.sin(baseAngle2)).toFloat()
        val base2Y = cy - (baseR * Math.cos(baseAngle2)).toFloat()

        path.moveTo(tipX, tipY)
        path.lineTo(base1X, base1Y)
        path.lineTo(base2X, base2Y)
        path.close()

        c.drawPath(path, p)

        // Outline
        p.color = 0xFF000000.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        c.drawPath(path, p)

        return bmp
    }

    private fun makePlayerBitmap(): Bitmap {
        val w = 48; val h = 48
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer circle
        p.color = Color.parseColor("#FF00E5FF")
        p.style = Paint.Style.FILL
        c.drawCircle(w / 2f, h / 2f, 22f, p)

        // Inner circle
        p.color = Color.parseColor("#FF1A1030")
        c.drawCircle(w / 2f, h / 2f, 16f, p)

        // Player icon (person silhouette)
        p.color = Color.WHITE
        c.drawCircle(w / 2f, h / 2f - 6f, 5f, p)  // head

        val bodyPath = android.graphics.Path()
        bodyPath.addRoundRect(
            w / 2f - 6f, h / 2f + 0f, w / 2f + 6f, h / 2f + 14f,
            4f, 4f, android.graphics.Path.Direction.CW
        )
        c.drawPath(bodyPath, p)

        return bmp
    }

    private fun rarityColor(r: EggRarity): Int = when (r) {
        EggRarity.COMMON -> 0xFF9E9E9E.toInt()
        EggRarity.UNCOMMON -> 0xFF4CAF50.toInt()
        EggRarity.RARE -> 0xFF2196F3.toInt()
        EggRarity.EPIC -> 0xFF9C27B0.toInt()
        EggRarity.LEGENDARY -> 0xFFFFC107.toInt()
    }

    private var currentWeatherType: com.intelligame.huntix.WeatherType? = null

    private fun updateWeatherParticles(weather: com.intelligame.huntix.WeatherType) {
        if (weather == currentWeatherType) return
        currentWeatherType = weather

        if (weatherOverlay is WeatherParticleOverlay) {
            (weatherOverlay as WeatherParticleOverlay).setWeatherType(weather)
        } else {
            val parent = weatherOverlay.parent as? ViewGroup ?: return
            val idx = parent.indexOfChild(weatherOverlay)
            parent.removeView(weatherOverlay)
            val overlay = WeatherParticleOverlay(this)
            overlay.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            overlay.setWeatherType(weather)
            parent.addView(overlay, idx)
            weatherOverlay = overlay
        }
    }
}

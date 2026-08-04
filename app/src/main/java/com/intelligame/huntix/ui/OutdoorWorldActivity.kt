// Copyright (c) 2026 Huntix. All rights reserved.
// Original code by Pasquale Lembo. Unauthorized redistribution prohibited.

package com.intelligame.huntix.ui

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.intelligame.huntix.UiKit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.intelligame.huntix.BaseNavActivity
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.OutdoorSetupActivity
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.R
import com.intelligame.huntix.gamification.LiveEventManager
import com.intelligame.huntix.manager.OutdoorManager
import com.intelligame.huntix.managers.PoiSearchManager
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.WeatherZoneManager
import com.intelligame.huntix.reallife.BuildingDefs
import com.intelligame.huntix.reallife.BuildingType
import com.intelligame.huntix.ui.BuildingInteriorActivity
import java.util.concurrent.atomic.AtomicLong
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition.Builder
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon as GeoPolygon
import java.util.Calendar
import java.util.Locale

class OutdoorWorldActivity : BaseNavActivity() {

    companion object {
        private const val TAG = "OutdoorWorld"
        private const val MIN_REPORT_DISTANCE_M = 10.0
        private const val REPORT_COOLDOWN_MS = 10_000L
        private const val MAX_REPORTS_PER_SESSION = 10

        const val EXTRA_MODE = "mode"
        const val MODE_OUTDOOR = "outdoor"
        const val MODE_REALLIFE = "reallife"
    }

    override fun activeTab() = ""

    private val mgr = OutdoorManager.get()
    private var mapView: MapView? = null
    private var mapLibre: MapLibreMap? = null
    private var styleReady = false
    private var mapInitialized = false
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
    private lateinit var btnReportPosition: TextView
    private var activeEggId: String? = null
    private var activePoiId: String? = null
    private var lastReportTime = 0L
    private var lastReportLat = 0.0
    private var lastReportLng = 0.0
    private var reportsThisSession = 0

    private val refresh = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try {
                refreshUi()
            } catch (e: OutOfMemoryError) {
                AppLog.e(TAG, "OOM in refreshUi", e)
                try {
                    mapView?.onLowMemory()
                } catch (_: Exception) {
                    // ignore
                }
            } catch (e: Error) {
                AppLog.e(TAG, "Error in refreshUi", e)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error in refreshUi", e)
            }
            refresh.postDelayed(this, 3000)
        }
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
    private lateinit var btnCaptureMethod: TextView
    private lateinit var btnLever: TextView
    private lateinit var btnArToggle: TextView
    private lateinit var schiusaProgress: LinearLayout
    private lateinit var tvSchiusaKm: TextView
    private lateinit var schiusaBarFill: View
    private lateinit var skyOverlay: SkyEventOverlay

    // ── Phase 3: sensor + animation state ─────────────────────
    private var currentHeading: Float = 0f
    private var walkTick: Int = 0
    private var sensorManager: android.hardware.SensorManager? = null
    private var hasSensor: Boolean = false
    private var initDone = false
    private var isExploring = false
    @Volatile private var isCameraMoving = false
    @Volatile private var needsRefreshOnIdle = false
    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (!isExploring || event.values.isEmpty()) return
            val rotation = FloatArray(9)
            android.hardware.SensorManager.getRotationMatrixFromVector(rotation, event.values)
            val orientation = FloatArray(3)
            android.hardware.SensorManager.getOrientation(rotation, orientation)
            currentHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (currentHeading < 0) currentHeading += 360f
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private var currentMode: String = MODE_OUTDOOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate START")

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_OUTDOOR
        mgr.isReallifeMode = currentMode == MODE_REALLIFE
        AppLog.i(TAG, "Mode: $currentMode")

        MapLibre.getInstance(this)

        setContentView(R.layout.activity_outdoor_world)

        // ═══ POI SEARCH OVERLAY (regione + città + ricerca realtime) ═══
        val dp8 = (resources.displayMetrics.density * 8).toInt()
        val poiSearchRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp8, dp8, dp8, 0)
        }

        // ─── Toggle Auto / Manuale (override GPS) ───
        val manualToggleBtn = Button(this).apply {
            text = if (mgr.isManualOverrideActive()) "✋ Manuale" else "📍 Auto"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = (resources.displayMetrics.density * 8).toFloat()
                setColor(
                    if (mgr.isManualOverrideActive()) Color.parseColor("#E65100")
                    else Color.parseColor("#43A047")
                )
            }
            setPadding(
                (resources.displayMetrics.density * 12).toInt(),
                (resources.displayMetrics.density * 6).toInt(),
                (resources.displayMetrics.density * 12).toInt(),
                (resources.displayMetrics.density * 6).toInt()
            )
            isAllCaps = false
        }
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        toggleRow.addView(manualToggleBtn)
        toggleRow.addView(TextView(this).apply {
            text = "  GPS: auto · Manuale: scegli città qui sotto"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        poiSearchRoot.addView(toggleRow)

        val poiSearchPanel = PoiSearchPanel(this).apply {
            onOpenPoi = { r ->
                val url = PoiSearchManager().getJsonPageUrl(r)
                startActivity(Intent(this@OutdoorWorldActivity, POICustomPageActivity::class.java).apply {
                    putExtra(POICustomPageActivity.EXTRA_JSON_URL, url)
                    putExtra(POICustomPageActivity.EXTRA_POI_NAME, r.name)
                })
                mapLibre?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(r.lat, r.lng), 18.0))
            }
            // Se il manuale è attivo, ogni cambio regione/città forzato ricarica la mappa
            onSelectionChanged = { region, city ->
                if (mgr.isManualOverrideActive() && region.isNotBlank()) {
                    mgr.setManualOverride(region, city)
                }
            }
        }
        poiSearchRoot.addView(poiSearchPanel)

        mgr.onLocationOverrideApplied = { centerOnUser() }

        manualToggleBtn.setOnClickListener {
            if (mgr.isManualOverrideActive()) {
                mgr.clearManualOverride()
                manualToggleBtn.text = "📍 Auto"
                manualToggleBtn.background = GradientDrawable().apply {
                    cornerRadius = (resources.displayMetrics.density * 8).toFloat()
                    setColor(Color.parseColor("#43A047"))
                }
            } else {
                val region = poiSearchPanel.currentRegionSlug
                if (region.isBlank()) {
                    Toast.makeText(this, "Scegli prima regione e città nei menu sotto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                mgr.setManualOverride(region, poiSearchPanel.currentCitySlug)
                manualToggleBtn.text = "✋ Manuale"
                manualToggleBtn.background = GradientDrawable().apply {
                    cornerRadius = (resources.displayMetrics.density * 8).toFloat()
                    setColor(Color.parseColor("#E65100"))
                }
            }
        }

        val contentFrame = findViewById<ViewGroup>(android.R.id.content)
        contentFrame.addView(poiSearchRoot)

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
        btnReportPosition = findViewById(R.id.btnReportPosition)

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
        btnCaptureMethod = findViewById(R.id.btnCaptureMethod)
        btnLever = findViewById(R.id.btnLever)
        btnArToggle = findViewById(R.id.btnArToggle)
        schiusaProgress = findViewById(R.id.schiusaProgress)
        tvSchiusaKm = findViewById(R.id.tvSchiusaKm)
        schiusaBarFill = findViewById(R.id.schiusaBarFill)
        skyOverlay = findViewById(R.id.skyOverlay)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        AppLog.d(TAG, "mapView created")

        mapView?.getMapAsync { map ->
            mapLibre = map
            mapInitialized = true
            AppLog.i(TAG, "MapLibre map ready, style loading...")

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

                listOf("eggs-source", "egg-radius-source", "pois-source", "avatar-source", "fidato-source", "direction-source").forEach { id ->
                    style.addSource(GeoJsonSource(id))
                }

                style.addLayer(SymbolLayer("eggs-layer", "eggs-source").withProperties(
                    PropertyFactory.iconImage(Expression.get("icon-id")),
                    PropertyFactory.iconSize(0.7f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                ))
                style.addLayer(FillLayer("egg-radius-layer", "egg-radius-source").withProperties(
                    PropertyFactory.fillColor(Expression.get("fill-color")),
                    PropertyFactory.fillOpacity(Expression.get("fill-opacity"))
                ))
                style.addLayer(SymbolLayer("pois-layer", "pois-source").withProperties(
                    PropertyFactory.iconImage(Expression.get("icon-id")),
                    PropertyFactory.iconSize(0.7f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                ))
                style.addLayer(SymbolLayer("avatar-layer", "avatar-source").withProperties(
                    PropertyFactory.iconImage(Expression.get("icon-id")),
                    PropertyFactory.iconSize(0.8f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                ))
                style.addLayer(SymbolLayer("fidato-layer", "fidato-source").withProperties(
                    PropertyFactory.iconImage(Expression.get("icon-id")),
                    PropertyFactory.iconSize(0.7f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                ))
                style.addLayer(SymbolLayer("direction-layer", "direction-source").withProperties(
                    PropertyFactory.iconImage(Expression.get("icon-id")),
                    PropertyFactory.iconSize(0.5f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                ))

                styleReady = true
                AppLog.d(TAG, "Style loaded, layers added, zoom=17 tilt=60")

                addStaticStyleImages(style)
            }

            map.addOnMapClickListener { point ->
                val correctionPoi = positionCorrectionPoi
                if (correctionPoi != null) {
                    positionCorrectionPoi = null
                    savePositionCorrection(correctionPoi, point.latitude, point.longitude)
                    return@addOnMapClickListener true
                }

                val screenPoint = map.projection.toScreenLocation(point)
                val hit = map.queryRenderedFeatures(screenPoint, "eggs-layer", "pois-layer")
                if (hit.isNotEmpty()) {
                    val feat = hit.first()
                    val type = feat.getStringProperty("type") ?: return@addOnMapClickListener true
                    val id = feat.getStringProperty("id") ?: return@addOnMapClickListener true
                    when (type) {
                        "egg" -> {
                            val egg = mgr.getEggs().firstOrNull { it.id == id && !it.found }
                            if (egg != null) showEggSheet(egg)
                        }
                        "poi" -> {
                            val poi = mgr.getPois().firstOrNull { it.id == id }
                            if (poi != null) {
                                if (currentMode == MODE_REALLIFE) {
                                    val bt = BuildingDefs.resolveBuildingType(poi.buildingType, poi.type)
                                    if (bt != null) openBuilding(poi, bt)
                                    else if (poi.type == "building" && poi.buildingType.isNotEmpty()) openBuilding(poi, null)
                                    else showPoiSheet(poi)
                                } else {
                                    when (poi.pageType) {
                                        "web" -> if (poi.url.isNotBlank()) openWebView(poi) else showPoiSheet(poi)
                                        "custom" -> if (poi.url.isNotBlank()) openCustomPage(poi) else showPoiSheet(poi)
                                        else -> {
                                            val bt = BuildingDefs.resolveBuildingType(poi.buildingType, poi.type)
                                            if (bt != null) openBuilding(poi, bt)
                                            else if (poi.type == "building" && poi.buildingType.isNotEmpty()) openBuilding(poi, null)
                                            else showPoiSheet(poi)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return@addOnMapClickListener true
                }

                hideBottomSheet()
                if (currentMode != MODE_REALLIFE) {
                    showReportPoiDialog(point.latitude, point.longitude)
                }
                true
            }

            map.addOnCameraMoveListener {
                isCameraMoving = true
                AppLog.d(TAG, "Camera MOVE start")
            }

            map.addOnCameraIdleListener {
                isCameraMoving = false
                AppLog.d(TAG, "Camera IDLE, needsRefresh=$needsRefreshOnIdle")
                if (needsRefreshOnIdle) {
                    needsRefreshOnIdle = false
                    lastMapUpdate.set(0L)
                    refreshMapMarkers(force = true)
                }
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

        // Capture method picker
        btnCaptureMethod.setOnClickListener {
            CaptureMethodPickerDialog.show(this) {
                Toast.makeText(this, "Metodo cambiato", Toast.LENGTH_SHORT).show()
            }
        }

        // Lever: dash toward nearest egg
        btnLever.setOnClickListener {
            if (mgr.isSimulating) {
                mgr.stopSimulation()
                Toast.makeText(this, "Simulazione GPS disattivata", Toast.LENGTH_SHORT).show()
                refreshUi()
                return@setOnClickListener
            }
            val result = mgr.simulateApproach()
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        // AR toggle: switch to AR camera view (PRIMARY mode)
        btnArToggle.setOnClickListener {
            startActivity(Intent(this, ArNavigationActivity::class.java))
        }

        // Phase 1: Central catch button
        btnCatch.setOnClickListener {
            val nearestEgg = mgr.getEggs()
                .filter { !it.found }
                .minByOrNull { mgr.distanceMeters(it) }
            if (nearestEgg != null && mgr.distanceMeters(nearestEgg) <= mgr.getCatchRadiusM(nearestEgg)) {
                showEggSheet(nearestEgg)
            } else if (nearestEgg != null) {
                val d = mgr.distanceMeters(nearestEgg).toInt()
                Toast.makeText(this, "Avvicinati: mancano $d m", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nessuna uova nelle vicinanze", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom sheet buttons
        btnSheetHunt.setOnClickListener {
            if (currentMode == MODE_REALLIFE) {
                activePoiId?.let { poiId ->
                    val poi = mgr.getPois().firstOrNull { it.id == poiId }
                    if (poi != null) {
                        val bt = BuildingDefs.resolveBuildingType(poi.buildingType, poi.type)
                        if (bt != null) openBuilding(poi, bt)
                        else if (poi.type == "building" && poi.buildingType.isNotEmpty()) openBuilding(poi, null)
                        else openPoi(poiId)
                    }
                }
            } else {
                activeEggId?.let { doHunt(it) }
                activePoiId?.let { openPoi(it) }
            }
        }

        btnSheetAr.setOnClickListener {
            activeEggId?.let { doArNavigation(it) }
        }

        btnReportPosition.setOnClickListener {
            val poiId = activePoiId ?: return@setOnClickListener
            val poi = mgr.getPois().firstOrNull { it.id == poiId } ?: return@setOnClickListener
            showReportPositionCorrectionDialog(poi)
        }

        badgeWeather.setOnClickListener {
            weatherTooltip.visibility =
                if (weatherTooltip.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Post refresh with initial delay to allow map initialization
        refresh.postDelayed(tick, 1000L)
        AppLog.d(TAG, "Tick scheduled (1s delay)")

        // Mode-specific UI adjustments
        if (currentMode == MODE_REALLIFE) {
            findViewById<View>(R.id.badgeEggs)?.visibility = View.GONE
            findViewById<View>(R.id.badgeGyms)?.visibility = View.GONE
            findViewById<View>(R.id.btnCatch)?.visibility = View.GONE
            findViewById<View>(R.id.tvCatchHint)?.visibility = View.GONE
            findViewById<View>(R.id.btnLever)?.visibility = View.GONE
            findViewById<View>(R.id.btnArToggle)?.visibility = View.GONE
            findViewById<View>(R.id.btnPhoto)?.visibility = View.GONE
            findViewById<View>(R.id.btnSettings)?.visibility = View.GONE
            findViewById<View>(R.id.radarView)?.visibility = View.GONE
            findViewById<View>(R.id.schiusaProgress)?.visibility = View.GONE
            tvSheetHunt.text = "Entra"
            tvSheetAr.visibility = View.GONE
            val title = findViewById<TextView>(R.id.tvWeatherEmoji)
            title?.let { t ->
                t.textSize = 14f
                t.text = "🏙️ RL"
            }
        } else {
            findViewById<View>(R.id.btnReportPosition)?.visibility = View.GONE
            // AR is the primary mode - make the AR button prominent
            btnArToggle.visibility = View.VISIBLE
            btnArToggle.text = "📷 AR"
            btnArToggle.setBackgroundResource(R.drawable.btn_accent)
        }

        // Phase 3: compass sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as? android.hardware.SensorManager
        val rotationVector = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager?.registerListener(sensorListener, rotationVector, android.hardware.SensorManager.SENSOR_DELAY_UI)
            hasSensor = true
            AppLog.d(TAG, "Compass sensor registered")
        } else {
            AppLog.w(TAG, "Rotation vector sensor unavailable")
        }
        AppLog.i(TAG, "onCreate COMPLETE, mapInitialized=$mapInitialized")
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        AppLog.i(TAG, "onResume")
        mgr.isReallifeMode = currentMode == MODE_REALLIFE
        mgr.start(this)
        checkGpsEnabled()
        mapView?.onResume()
        if (!refresh.hasCallbacks(tick)) refresh.post(tick)
        if (mapInitialized) {
            centerOnUser()
            updateSkyColor()
        }
        isExploring = true
    }

    override fun onPause() {
        super.onPause()
        AppLog.i(TAG, "onPause — stopping tick, sensor, mgr")
        refresh.removeCallbacks(tick)
        mapView?.onPause()
        mgr.stopSimulation()
        mgr.stop()
        if (hasSensor) sensorManager?.unregisterListener(sensorListener)
        isExploring = false
    }

    override fun onStop() {
        super.onStop()
        refresh.removeCallbacks(tick)
        mapView?.onStop()
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        poiCooldownState.clear()
        mapView?.onDestroy()
        mgr.onLocationOverrideApplied = null
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
            AppLog.i(TAG, "Location permission GRANTED")
            checkGpsEnabled()
            centerOnUser()
        } else if (requestCode == 101) {
            AppLog.w(TAG, "Location permission DENIED — using demo spawn")
            Toast.makeText(this, "Permesso GPS negato: usato spawn dimostrativo", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkGpsEnabled() {
        val lm = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager ?: return
        if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) return
        AlertDialog.Builder(this).apply {
            setTitle("GPS non attivato")
            setMessage("Il GPS non è attivo. Senza GPS la posizione mostrata potrebbe non essere precisa.\n\nVuoi attivarlo nelle impostazioni?")
            setPositiveButton("Attiva GPS") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            setNegativeButton("Continua senza") { _, _ -> }
            setCancelable(true)
            show()
        }
    }

    private fun centerOnUser() {
        val loc = mgr.currentLocation ?: run {
            AppLog.w(TAG, "centerOnUser: no location available")
            return
        }
        AppLog.d(TAG, "centerOnUser: ${loc.latitude}, ${loc.longitude}")
        val latLng = LatLng(loc.latitude, loc.longitude)
        mapLibre?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.0))
    }

    // ─── Main UI refresh ───────────────────────────────────────

    private fun refreshUi() {
        if (!mapInitialized) return

        memoryCheckTimer -= 3f
        if (memoryCheckTimer <= 0f) {
            memoryCheckTimer = MEMORY_CHECK_INTERVAL
            checkMemoryPressure()
        }

        val w = WeatherZoneManager.currentWeather
        walkTick = (walkTick + 1) % 64

        tvWeatherEmoji.text = w.emoji
        tvWeatherName.text = w.displayName
        val boostDesc = w.rarityBoost.entries.joinToString("\n") { (r, b) -> "$r  x${"%.1f".format(b)}" }
        tvWeatherBonus.text = "Bonus rarita:\n$boostDesc"

        if (currentMode != MODE_REALLIFE) {
            val eggCount = mgr.getEggs().count { !it.found }
            tvEggCount.text = "$eggCount"
            tvGymCount.text = "${mgr.getPois().size}"
        }

        // Phase 1.1: Player level + EXP bar
        refreshPlayerHud()

        // Phase 1.1: MVC currency
        val mvc = SavedManager.getMvcBalance(this)
        tvMvcCount.text = "${mvc.toLong()}"

        if (currentMode != MODE_REALLIFE) {
            // Phase 1.6: Live event badge
            refreshEventBadge()

            // Phase 1.5: Radar view
            refreshRadar()

            // Phase 1.4: Catch button state
            refreshCatchButton()

            // Phase 1.7: Lever state indicator
            if (mgr.isSimulating) {
                btnLever.setBackgroundResource(R.drawable.btn_accent)
                btnLever.text = "⏹"
            } else {
                btnLever.setBackgroundResource(R.drawable.btn_purple)
                btnLever.text = "⛓"
            }

            // Phase 5.2: Proximity hint
            checkProximityHint()

            // Progress di schiusa in bottom sheet
            refreshSchiusaProgress()
        }

        // Phase 4.1: Sky event overlay
        refreshSkyOverlay()

        updateWeatherParticles(w)

        try {
            refreshMapMarkers()
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "OOM in refreshMapMarkers", e)
            try {
                mapView?.onLowMemory()
            } catch (_: Exception) {
                // ignore
            }
        } catch (e: Error) {
            AppLog.e(TAG, "Error in refreshMapMarkers", e)
        }
    }

    private val poiCooldownState = mutableMapOf<String, Boolean>()

    // ── Memory pressure monitoring ────────────────────────────
    private var memoryCheckTimer = 5f
    private val MEMORY_CHECK_INTERVAL = 3f

    private fun checkMemoryPressure() {
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val thresholdMb = memInfo.threshold / (1024 * 1024)

        AppLog.d(TAG, "Memory: ${availMb}MB avail / ${totalMb}MB total, threshold=${thresholdMb}MB")

        if (availMb < thresholdMb + 100L) {
            AppLog.w(TAG, "LOW MEMORY! ${availMb}MB avail")
            mapView?.onLowMemory()
            if (availMb < thresholdMb + 50L) {
                AppLog.w(TAG, "CRITICAL MEMORY ${availMb}MB — finishing activity")
                finish()
            }
        }
    }

    private fun addStaticStyleImages(style: Style) {
        try {
            EggRarity.entries.forEach { rarity ->
                style.addImage("egg_${rarity.name.lowercase()}", makeMarkerBitmap(rarity))
            }
            listOf("gym", "hub", "sponsor", "arena").forEach { type ->
                style.addImage("poi_$type", makePoiBitmap(type))
                style.addImage("poi_${type}_gray", makePoiBitmapGray(type))
            }
            BuildingDefs.BUILDINGS.forEach { bDef ->
                style.addImage("building_${bDef.type.name.lowercase()}", makeBuildingBitmap(bDef.emoji, bDef.color3D))
            }
            AppLog.d(TAG, "Static style images added to style")
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "OOM while adding static style images", e)
        } catch (e: Error) {
            AppLog.e(TAG, "Error adding static style images", e)
        }
    }

    private fun refreshMapMarkers(force: Boolean = false) {
        if (!styleReady) return
        if (isCameraMoving) {
            needsRefreshOnIdle = true
            AppLog.d(TAG, "refreshMapMarkers: SKIPPED (camera moving, queued idle refresh)")
            return
        }
        if (!force && !checkMapUpdateRequired()) return

        val map = mapLibre ?: return
        val style = map.style ?: return
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        AppLog.d(TAG, "refreshMapMarkers: START (force=$force) mem=${availMb}MB")

        // ── Eggs ──
        if (currentMode != MODE_REALLIFE) {
            val visibleEggs = mgr.getEggs().filter { !it.found }

            val eggFeatures = visibleEggs.map { egg ->
                val f = Feature.fromGeometry(Point.fromLngLat(egg.lng, egg.lat))
                f.addStringProperty("icon-id", "egg_${egg.rarity.name.lowercase()}")
                f.addStringProperty("id", egg.id)
                f.addStringProperty("type", "egg")
                f
            }
            style.getSourceAs<GeoJsonSource>("eggs-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(eggFeatures))

            val radiusFeatures = visibleEggs.map { egg ->
                val color = rarityColor(egg.rarity)
                val center = LatLng(egg.lat, egg.lng)
                val points = createCirclePoints(center, mgr.getCatchRadiusM(egg).toDouble())
                val ring = points.map { Point.fromLngLat(it.longitude, it.latitude) }
                val f = Feature.fromGeometry(GeoPolygon.fromLngLats(listOf(ring)))
                f.addStringProperty("id", egg.id)
                f.addStringProperty("fill-color", String.format("#%06X", 0xFFFFFF and color))
                f.addNumberProperty("fill-opacity", 0.35)
                f
            }
            style.getSourceAs<GeoJsonSource>("egg-radius-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(radiusFeatures))
        } else {
            style.getSourceAs<GeoJsonSource>("eggs-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            style.getSourceAs<GeoJsonSource>("egg-radius-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        // ── POIs ──
        val allPois = mgr.getPois()
        val currentPois = if (allPois.size > OutdoorManager.MAX_POIS) {
            val loc = mgr.currentLocation
            val sorted = if (loc != null) allPois.sortedBy { mgr.distanceMeters(it) } else allPois
            AppLog.w(TAG, "POI overflow: ${allPois.size} > ${OutdoorManager.MAX_POIS}, capping to nearest ${OutdoorManager.MAX_POIS}")
            sorted.take(OutdoorManager.MAX_POIS)
        } else allPois

        val currentPoiIds = currentPois.map { it.id }.toSet()
        poiCooldownState.keys.toList().forEach { id ->
            if (id !in currentPoiIds) poiCooldownState.remove(id)
        }

        val poiFeatures = currentPois.map { poi ->
            val onCooldown = mgr.isPoiOnCooldown(poi)
            poiCooldownState[poi.id] = onCooldown
            val iconId = if (poi.type == "building" && poi.buildingType.isNotEmpty()) {
                "building_${poi.buildingType.lowercase()}"
            } else {
                "poi_${poi.type}${if (onCooldown) "_gray" else ""}"
            }
            val f = Feature.fromGeometry(Point.fromLngLat(poi.lng, poi.lat))
            f.addStringProperty("icon-id", iconId)
            f.addStringProperty("id", poi.id)
            f.addStringProperty("type", "poi")
            f
        }
        style.getSourceAs<GeoJsonSource>("pois-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(poiFeatures))

        // ── Player avatar ──
        val currentLoc = mgr.currentLocation ?: return
        val profile = PlayerProfileManager.myProfile
        val level = profile?.level ?: 1

        try {
            val avatarDrawable = com.intelligame.huntix.avatar.AvatarMapRenderer
                .makeAvatarMarkerDrawable(resources, 104, walkTick, level, currentHeading, this)
            style.addImage("avatar", avatarDrawable.bitmap)
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "OOM creating avatar bitmap", e)
        } catch (e: Error) {
            AppLog.e(TAG, "Error creating avatar bitmap", e)
        }

        val avatarFeat = Feature.fromGeometry(Point.fromLngLat(currentLoc.longitude, currentLoc.latitude))
        avatarFeat.addStringProperty("icon-id", "avatar")
        avatarFeat.addStringProperty("type", "avatar")
        style.getSourceAs<GeoJsonSource>("avatar-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(avatarFeat)))

        // ── Fidato ──
        val fidato = com.intelligame.huntix.managers.SurpriseManager.getAll(this)
            .firstOrNull { it.isFidato }
        if (fidato != null) {
            val creature = com.intelligame.huntix.SurpriseCreature.ALL
                .firstOrNull { it.id == fidato.creatureId }
            if (creature != null) {
                try {
                    style.addImage("fidato", makeFidatoBitmap(creature.emoji))
                } catch (e: OutOfMemoryError) {
                    AppLog.e(TAG, "OOM creating fidato bitmap", e)
                } catch (e: Error) {
                    AppLog.e(TAG, "Error creating fidato bitmap", e)
                }
                val offsetLat = currentLoc.latitude + 0.00018
                val offsetLng = currentLoc.longitude + 0.00012
                val fidatoFeat = Feature.fromGeometry(Point.fromLngLat(offsetLng, offsetLat))
                fidatoFeat.addStringProperty("icon-id", "fidato")
                fidatoFeat.addStringProperty("type", "fidato")
                style.getSourceAs<GeoJsonSource>("fidato-source")
                    ?.setGeoJson(FeatureCollection.fromFeatures(listOf(fidatoFeat)))
            }
        } else {
            style.getSourceAs<GeoJsonSource>("fidato-source")
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        // ── Direction indicator ──
        try {
            val dirBitmap = makeDirectionBitmap(currentHeading)
            style.addImage("direction", dirBitmap)
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "OOM creating direction bitmap", e)
        } catch (e: Error) {
            AppLog.e(TAG, "Error creating direction bitmap", e)
        }
        val dirFeat = Feature.fromGeometry(Point.fromLngLat(currentLoc.longitude, currentLoc.latitude))
        dirFeat.addStringProperty("icon-id", "direction")
        dirFeat.addStringProperty("type", "direction")
        style.getSourceAs<GeoJsonSource>("direction-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(dirFeat)))

        AppLog.d(TAG, "refreshMapMarkers: DONE — eggs=${mgr.getEggs().count { !it.found }} pois=${currentPois.size} avatar=true mem=${availMb}MB")
    }
    
    // Track map movement to prevent excessive redraws during panning
    private val lastMapUpdate = AtomicLong(0L)
    private fun checkMapUpdateRequired(): Boolean {
        val now = System.currentTimeMillis()
        val minInterval = 5000L // Minimum 5 seconds between full marker redraws
        if (now - lastMapUpdate.get() < minInterval) {
            return false
        }
        lastMapUpdate.set(now)
        return true
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

    // ─── Progress di schiusa ───────────────────────────────────

    private fun refreshSchiusaProgress() {
        // Check if any egg in the bottom sheet is in termocolla
        val currentEggId = activeEggId ?: return
        val egg = mgr.getEgg(currentEggId) ?: return
        refreshSchiusaForEgg(egg)
    }

    private fun refreshSchiusaForEgg(egg: com.intelligame.huntix.WorldEgg) {
        val activeEggs = com.intelligame.huntix.managers.TermocullaManager.getActiveEggs(this)
        val inSchiusa = activeEggs.firstOrNull {
            it.rarityId == egg.rarity.name.lowercase() && !it.isReady
        }

        if (inSchiusa != null) {
            schiusaProgress.visibility = View.VISIBLE
            tvSchiusaKm.text = "%.1f / %.0f km".format(
                inSchiusa.distanceWalked, inSchiusa.distanceRequired
            )
            val lp = schiusaBarFill.layoutParams
            val progressPercent = (inSchiusa.progress * 100).toInt()
            lp.width = (120 * resources.displayMetrics.density * progressPercent / 100).toInt()
            schiusaBarFill.layoutParams = lp
        } else {
            schiusaProgress.visibility = View.GONE
        }
    }

    // ─── Phase 1.3: Screenshot ────────────────────────────────

    private fun takeScreenshot() {
        var bitmap: Bitmap? = null
        try {
            val rootView = window.decorView.rootView
            bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
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
        } finally {
            bitmap?.recycle()
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
        btnReportPosition.visibility = View.GONE
        bottomSheet.visibility = View.VISIBLE

        // Phase 5.1: Show progress di schiusa if this egg is in termocolla
        refreshSchiusaForEgg(egg)
    }

    private fun showPoiSheet(poi: OutdoorManager.Poi) {
        val d = mgr.distanceMeters(poi)
        activePoiId = poi.id
        activeEggId = null
        tvSheetTitle.text = "\uD83C\uDFDF\uFE0F ${poi.name}"
        tvSheetInfo.text = "${d.toInt()} m"
        if (currentMode == MODE_REALLIFE) {
            tvSheetHunt.text = "Entra"
            btnSheetAr.visibility = View.GONE
            btnReportPosition.visibility = View.GONE
        } else {
            tvSheetHunt.text = "Spinna"
            btnSheetAr.visibility = View.GONE
            btnReportPosition.visibility = View.VISIBLE
        }
        btnSheetHunt.visibility = View.VISIBLE
        bottomSheet.visibility = View.VISIBLE
    }

    private fun hideBottomSheet() {
        bottomSheet.visibility = View.GONE
        activeEggId = null
        activePoiId = null
    }

    private fun haversineM(la1: Double, ln1: Double, la2: Double, ln2: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(la2 - la1)
        val dLng = Math.toRadians(ln2 - ln1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }

    private fun normalizePoiName(name: String): String {
        var normalized = name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val stopWords = setOf("da", "di", "del", "della", "dei", "delle", "il", "la", "i", "gli", "le",
            "the", "of", "and", "pizzeria", "ristorante", "bar", "caffe", "caffè", "pastificio",
            "gelateria", "supermercato", "negozio", "store", "shop", "restaurant", "cafe", "coffee")
        val tokens = normalized.split(" ").filter { it.isNotBlank() && !stopWords.contains(it) }
        return tokens.sorted().joinToString(" ")
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m
        var prevRow = IntArray(n + 1) { it }
        var currRow = IntArray(n + 1)
        for (i in 1..m) {
            currRow[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currRow[j] = minOf(
                    prevRow[j] + 1,
                    currRow[j - 1] + 1,
                    prevRow[j - 1] + cost
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        return prevRow[n]
    }

    private fun nameSimilarity(name1: String, name2: String): Double {
        val n1 = normalizePoiName(name1)
        val n2 = normalizePoiName(name2)
        if (n1.isEmpty() || n2.isEmpty()) return 0.0
        val maxLen = maxOf(n1.length, n2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(n1, n2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun isSimilarName(name1: String, name2: String): Boolean {
        val similarity = nameSimilarity(name1, name2)
        return similarity >= 0.7
    }

    private fun hasSimilarPoiName(name: String): Boolean {
        return mgr.getPois().any { isSimilarName(name, it.name) }
    }

    /** Mostra dialog per segnalare un POI mancante */
    private fun showReportPoiDialog(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        // Rate-limit: too soon from last report
        if (now - lastReportTime < REPORT_COOLDOWN_MS) {
            Toast.makeText(this, "Aspetta un momento prima di segnalare di nuovo", Toast.LENGTH_SHORT).show()
            return
        }
        // Max per session
        if (reportsThisSession >= MAX_REPORTS_PER_SESSION) {
            Toast.makeText(this, "Limite di ${MAX_REPORTS_PER_SESSION} segnalazioni raggiunto in questa sessione", Toast.LENGTH_SHORT).show()
            return
        }
        // Guarda se ci sono POI vicini
        val tooClose = mgr.getPois().any { haversineM(lat, lng, it.lat, it.lng) < MIN_REPORT_DISTANCE_M }
        if (tooClose) return
        // Guarda se l'ultimo report è troppo vicino
        if (lastReportTime > 0 && haversineM(lat, lng, lastReportLat, lastReportLng) < MIN_REPORT_DISTANCE_M) {
            Toast.makeText(this, "Troppo vicino all'ultima segnalazione — spostati di almeno 10m", Toast.LENGTH_SHORT).show()
            return
        }

        reportsThisSession++
        lastReportTime = now
        lastReportLat = lat
        lastReportLng = lng

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 8),
                UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 8))
        }

        val txtHint = TextView(this).apply {
            text = "Nessun punto registrato in questa zona.\nSegnala un POI mancante:"
            textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(this@OutdoorWorldActivity, 8))
        }
        container.addView(txtHint)

        val inputName = EditText(this).apply {
            hint = "Nome del negozio/luogo"
            setTextColor(Color.WHITE)
            setHintTextColor(0x88FFFFFF.toInt())
        }
        container.addView(inputName)

        val spinnerCategory = AutoCompleteTextView(this).apply {
            hint = "Categoria"
            setTextColor(Color.WHITE)
            setHintTextColor(0x88FFFFFF.toInt())
            val categories = arrayOf("ristorante", "bar", "palestra", "ospedale", "monumento", "museo", "supermercato", "altro")
            setAdapter(ArrayAdapter(this@OutdoorWorldActivity, android.R.layout.simple_dropdown_item_1line, categories))
        }
        container.addView(spinnerCategory)

        AlertDialog.Builder(this).apply {
            setTitle("Segnala POI")
            setView(container)
            setPositiveButton("Invia") { _, _ ->
                val name = inputName.text.toString().trim()
                val cat = spinnerCategory.text.toString().trim()
                if (name.isEmpty()) {
                    reportsThisSession--
                    Toast.makeText(this@OutdoorWorldActivity, "Inserisci almeno un nome", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                savePoiSuggestion(name, cat, lat, lng)
            }
            setNegativeButton("Annulla") { _, _ -> reportsThisSession-- }
            show()
        }
    }

    /** Salva il suggerimento POI in un file cache locale */
    private fun savePoiSuggestion(name: String, category: String, lat: Double, lng: Double) {
        try {
            // Validate: not too close to any existing POI
            if (mgr.getPois().any { haversineM(lat, lng, it.lat, it.lng) < MIN_REPORT_DISTANCE_M }) {
                Toast.makeText(this, "Un POI già esiste nelle vicinanze", Toast.LENGTH_SHORT).show()
                reportsThisSession--
                return
            }
            // Validate: name not too similar to existing POI names
            if (hasSimilarPoiName(name)) {
                Toast.makeText(this, "Esiste già un POI con nome simile nelle vicinanze", Toast.LENGTH_SHORT).show()
                reportsThisSession--
                return
            }
            val suggestionsFile = File(filesDir, "poi_suggestions.json")
            val existing = mutableListOf<JSONObject>()
            if (suggestionsFile.exists()) {
                val content = suggestionsFile.readText()
                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    existing.add(arr.getJSONObject(i))
                }
            }
            // Check proximity to other saved suggestions too
            if (existing.any { s ->
                haversineM(lat, lng, s.optDouble("lat", 0.0), s.optDouble("lng", 0.0)) < MIN_REPORT_DISTANCE_M
            }) {
                Toast.makeText(this, "Esiste già una segnalazione nelle vicinanze", Toast.LENGTH_SHORT).show()
                reportsThisSession--
                return
            }
            // Check name similarity against saved suggestions
            if (existing.any { s ->
                isSimilarName(name, s.optString("name", ""))
            }) {
                Toast.makeText(this, "Esiste già una segnalazione con nome simile", Toast.LENGTH_SHORT).show()
                reportsThisSession--
                return
            }
            existing.add(JSONObject().apply {
                put("name", name)
                put("category", category)
                put("lat", lat)
                put("lng", lng)
                put("timestamp", System.currentTimeMillis())
            })
            val outArr = JSONArray(existing)
            suggestionsFile.writeText(outArr.toString(2))
            Toast.makeText(this, "Segnalazione inviata: grazie!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore salvataggio", Toast.LENGTH_SHORT).show()
            AppLog.e("ReportPoi", e.message ?: "unknown error")
        }
    }

    private fun showReportPositionCorrectionDialog(poi: OutdoorManager.Poi) {
        val now = System.currentTimeMillis()
        if (now - lastReportTime < REPORT_COOLDOWN_MS) {
            Toast.makeText(this, "Aspetta un momento prima di segnalare di nuovo", Toast.LENGTH_SHORT).show()
            return
        }
        if (reportsThisSession >= MAX_REPORTS_PER_SESSION) {
            Toast.makeText(this, "Limite di ${MAX_REPORTS_PER_SESSION} segnalazioni raggiunto in questa sessione", Toast.LENGTH_SHORT).show()
            return
        }

        reportsThisSession++
        lastReportTime = now

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 8),
                UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 8))
        }

        val txtInfo = TextView(this).apply {
            text = "Posizione attuale: ${poi.lat}, ${poi.lng}\nToca la mappa per selezionare la posizione corretta"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(this@OutdoorWorldActivity, 12))
        }
        container.addView(txtInfo)

        val btnSelectOnMap = TextView(this).apply {
            text = "Seleziona sulla mappa"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.btn_accent)
            setPadding(UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 12),
                UiKit.dp(this@OutdoorWorldActivity, 16),
                UiKit.dp(this@OutdoorWorldActivity, 12))
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                hideBottomSheet()
                startPositionCorrectionMode(poi)
            }
        }
        container.addView(btnSelectOnMap)

        val btnCancel = TextView(this).apply {
            text = "Annulla"
            textSize = 14f
            setTextColor(0x88FFFFFF.toInt())
            setPadding(0, UiKit.dp(this@OutdoorWorldActivity, 12), 0, 0)
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                reportsThisSession--
                hideBottomSheet()
            }
        }
        container.addView(btnCancel)

        AlertDialog.Builder(this).apply {
            setTitle("Correggi posizione")
            setView(container)
            show()
        }
    }

    private var positionCorrectionPoi: OutdoorManager.Poi? = null

    private fun startPositionCorrectionMode(poi: OutdoorManager.Poi) {
        positionCorrectionPoi = poi
        Toast.makeText(this, "Tocca la mappa per selezionare la posizione corretta", Toast.LENGTH_LONG).show()
    }

    private fun savePositionCorrection(poi: OutdoorManager.Poi, correctedLat: Double, correctedLng: Double) {
        try {
            val correctionsFile = File(filesDir, "poi_position_corrections.json")
            val existing = mutableListOf<JSONObject>()
            if (correctionsFile.exists()) {
                val content = correctionsFile.readText()
                if (content.isNotBlank()) {
                    val arr = JSONArray(content)
                    for (i in 0 until arr.length()) {
                        existing.add(arr.getJSONObject(i))
                    }
                }
            }
            existing.add(JSONObject().apply {
                put("poiId", poi.id)
                put("poiName", poi.name)
                put("originalLat", poi.lat)
                put("originalLng", poi.lng)
                put("correctedLat", correctedLat)
                put("correctedLng", correctedLng)
                put("timestamp", System.currentTimeMillis())
            })
            val outArr = JSONArray(existing)
            correctionsFile.writeText(outArr.toString(2))
            Toast.makeText(this, "Correzione posizione inviata: grazie!", Toast.LENGTH_SHORT).show()
            lastReportLat = correctedLat
            lastReportLng = correctedLng
        } catch (e: Exception) {
            Toast.makeText(this, "Errore salvataggio correzione", Toast.LENGTH_SHORT).show()
            AppLog.e("ReportPoi", e.message ?: "unknown error")
        }
    }

    private fun doHunt(eggId: String) {
        val egg = mgr.getEgg(eggId) ?: return
        if (egg.found) {
            Toast.makeText(this, "Gia catturato", Toast.LENGTH_SHORT).show()
            hideBottomSheet()
            return
        }
        val d = mgr.distanceMeters(egg)
        val radius = mgr.getCatchRadiusM(egg)
        if (d > radius) {
            Toast.makeText(this, "Avvicinati: mancano ${d.toInt()} m (raggio: ${radius.toInt()} m)", Toast.LENGTH_SHORT).show()
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
        startActivity(Intent(this, OutdoorArCatchActivity::class.java))
        hideBottomSheet()
    }

    private fun openBuilding(poi: OutdoorManager.Poi, bt: BuildingType?) {
        val bDef = bt?.let { t -> BuildingDefs.BUILDINGS.find { it.type == t } }
            ?: BuildingDefs.BUILDINGS.firstOrNull { it.type.name == poi.buildingType }
            ?: return
        try {
            startActivity(Intent(this, BuildingInteriorActivity::class.java).apply {
                putExtra(BuildingInteriorActivity.EXTRA_BUILDING_TYPE, bDef.type.ordinal)
                putExtra(BuildingInteriorActivity.EXTRA_POI_NAME, poi.name)
                if (poi.url.isNotBlank()) putExtra(BuildingInteriorActivity.EXTRA_POI_URL, poi.url)
                putExtra(BuildingInteriorActivity.EXTRA_VENUE_ID, poi.id)
                putExtra(BuildingInteriorActivity.EXTRA_VENUE_LAT, poi.lat)
                putExtra(BuildingInteriorActivity.EXTRA_VENUE_LNG, poi.lng)
                putExtra(BuildingInteriorActivity.EXTRA_BUILDING_TYPE_STR, poi.buildingType)
            })
            hideBottomSheet()
        } catch (e: ActivityNotFoundException) {
            AppLog.e(TAG, "Activity not found: ${BuildingInteriorActivity::class.java.name}", e)
            Toast.makeText(this, "Edificio non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebView(poi: OutdoorManager.Poi) {
        startActivity(Intent(this, POIWebViewActivity::class.java).apply {
            putExtra(POIWebViewActivity.EXTRA_URL, poi.url)
            putExtra(POIWebViewActivity.EXTRA_TITLE, poi.name)
        })
        hideBottomSheet()
    }

    private fun openCustomPage(poi: OutdoorManager.Poi) {
        startActivity(Intent(this, POICustomPageActivity::class.java).apply {
            putExtra(POICustomPageActivity.EXTRA_JSON_URL, poi.url)
            putExtra(POICustomPageActivity.EXTRA_POI_NAME, poi.name)
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

    private fun makeBuildingBitmap(emoji: String, color: Int): Bitmap {
        val w = 80; val h = 100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

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
        p.textSize = 28f
        p.textAlign = Paint.Align.CENTER
        c.drawText(emoji, w / 2f, headY + 10f, p)

        return bmp
    }

    private fun makePoiBitmap(type: String = "gym"): Bitmap {
        val w = 80; val h = 100
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Color based on type
        val color = when (type) {
            "hub" -> 0xFF42A5F5.toInt()  // blue
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
            "hub" -> {
                // Hub: cube/box shape
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

    private fun makeFidatoBitmap(emoji: String): Bitmap {
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

package com.intelligame.huntix.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.intelligame.huntix.EggElement
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.PlayerProfileManager
import com.intelligame.huntix.WorldEgg
import com.intelligame.huntix.managers.SavedManager
import com.intelligame.huntix.managers.WeatherZoneManager
import java.util.concurrent.atomic.AtomicLong

class OutdoorManager private constructor() : SensorEventListener {

    class Poi(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val type: String = "gym",
        var spun: Boolean = false
    )

    data class CatchResult(
        val success: Boolean,
        val message: String,
        val egg: WorldEgg? = null
    )

    companion object {
        private var instance: OutdoorManager? = null
        fun get(): OutdoorManager = instance ?: OutdoorManager().also { instance = it }

        private const val BASE_CATCH_RADIUS_M = 50f
        private const val DEFAULT_LAT = 41.9028
        private const val DEFAULT_LNG = 12.4964
        private const val RESPAWN_THRESHOLD_M = 150f
    }

    private var locationManager: LocationManager? = null
    var currentLocation: Location? = null
        private set
    private val eggs = mutableListOf<WorldEgg>()
    private val pois = mutableListOf<Poi>()
    private var listening = false
    private var lastSpawnLat = 0.0
    private var lastSpawnLng = 0.0

    private var sensorManager: SensorManager? = null
    @Volatile private var deviceAzimuth: Float = 0f
    private var sensorsRegistered = false

    private var activeClients = 0
    var huntingEggId: String? = null

    private val nextEggId = AtomicLong(0)
    private val nextPoiId = AtomicLong(0)

    fun getDeviceHeadingDeg(): Float = deviceAzimuth

    private val listener = LocationListener { loc ->
        currentLocation = loc
        ensureSpawns(loc)
        appCtx?.let { WeatherZoneManager.refreshAsync(it, loc.latitude, loc.longitude) }
    }

    private var appCtx: Context? = null

    fun start(ctx: Context) {
        activeClients++
        appCtx = ctx.applicationContext
        registerCompass(ctx)
        locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasPerm = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm || locationManager == null) {
            if (currentLocation == null) currentLocation = defaultLocation()
            ensureSpawns(currentLocation!!)
            return
        }
        val lm = locationManager!!
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        try {
            val lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLoc != null) {
                currentLocation = lastLoc
                ensureSpawns(lastLoc)
            }
        } catch (_: Exception) {}
        if (currentLocation == null) currentLocation = defaultLocation()
        ensureSpawns(currentLocation!!)
        var started = false
        if (gpsEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, listener)
                started = true
            } catch (_: Exception) {}
        } else if (netEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10f, listener)
                started = true
            } catch (_: Exception) {}
        }
        listening = started
        WeatherZoneManager.refreshAsync(ctx, currentLocation!!.latitude, currentLocation!!.longitude)
    }

    private fun registerCompass(ctx: Context) {
        if (sensorsRegistered) return
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        rotationSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorsRegistered = true
    }

    private var filterSin = 0f
    private var filterCos = 1f
    private var filterInitialized = false

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor.type
        if (type != Sensor.TYPE_ROTATION_VECTOR && type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        val v = event.values
        val qx = v[0]; val qy = v[1]; val qz = v[2]
        val qw = if (v.size >= 4) v[3] else {
            val s = 1f - qx * qx - qy * qy - qz * qz
            if (s > 0f) kotlin.math.sqrt(s) else 0f
        }
        var az = Math.toDegrees(
            Math.atan2(2.0 * (qw * qz + qx * qy), 1.0 - 2.0 * (qy * qy + qz * qz))
        ).toFloat()
        if (az < 0) az += 360f
        val alpha = if (!filterInitialized) { filterInitialized = true; 1.0f } else 0.15f
        val azRad = Math.toRadians(az.toDouble())
        filterSin = filterSin * (1 - alpha) + Math.sin(azRad).toFloat() * alpha
        filterCos = filterCos * (1 - alpha) + Math.cos(azRad).toFloat() * alpha
        var filtered = Math.toDegrees(Math.atan2(filterSin.toDouble(), filterCos.toDouble())).toFloat()
        if (filtered < 0) filtered += 360f
        deviceAzimuth = filtered
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun stop() {
        activeClients = (activeClients - 1).coerceAtLeast(0)
        if (activeClients > 0) return
        if (listening) {
            try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
            listening = false
        }
        if (sensorsRegistered) {
            try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
            sensorsRegistered = false
        }
    }

    fun defaultLocation(): Location = Location("seed").apply {
        latitude = DEFAULT_LAT
        longitude = DEFAULT_LNG
    }

    private fun ensureSpawns(loc: Location) {
        if (eggs.isEmpty()) {
            regenerate(loc)
            return
        }
        val distFromSpawn = haversine(loc.latitude, loc.longitude, lastSpawnLat, lastSpawnLng)
        if (distFromSpawn > RESPAWN_THRESHOLD_M) {
            if (huntingEggId != null) return
            regenerate(loc)
            return
        }
        pruneExpiredEggs()
    }

    private fun pruneExpiredEggs() {
        val now = System.currentTimeMillis()
        eggs.removeAll { egg ->
            if (egg.id == huntingEggId) return@removeAll false
            val ttlMs = egg.rarity.ttlMinutes * 60_000L
            (now - egg.spawnedAt) > ttlMs
        }
        if (eggs.isEmpty()) {
            currentLocation?.let { regenerate(it) }
        }
    }

    fun regenerate(loc: Location) {
        val rng = java.util.Random()
        lastSpawnLat = loc.latitude
        lastSpawnLng = loc.longitude

        val prefs = appCtx?.getSharedPreferences("outdoor_setup", android.content.Context.MODE_PRIVATE)
        val eggCount = prefs?.getInt("eggs", 10) ?: 10
        val includePois = prefs?.getBoolean("gyms", true) ?: true
        val difficulty = prefs?.getString("difficulty", "normale") ?: "normale"
        val setupRadius = prefs?.getInt("radius", 250) ?: 250

        val radiusMultiplier = when (difficulty) {
            "facile" -> 0.6
            "difficile" -> 1.5
            else -> 1.0
        }
        val rarityBoost: Float = when (difficulty) {
            "difficile" -> 1.5f
            "leggendario" -> 2.0f
            else -> 1.0f
        }

        val maxDist = setupRadius.toDouble()

        val newEggs = mutableListOf<WorldEgg>()
        val newPois = mutableListOf<Poi>()
        try {
            repeat(eggCount) {
                val dist = (10.0 + rng.nextDouble() * (maxDist - 10.0).coerceAtLeast(10.0)) * radiusMultiplier
                val (la, ln) = offset(loc.latitude, loc.longitude, dist, rng)
                val rarity = pickRarity(rng, rarityBoost)
                val element = EggElement.values()[rng.nextInt(EggElement.values().size)]
                newEggs.add(
                    WorldEgg(
                        id = "out_${nextEggId.incrementAndGet()}",
                        name = "Uovo ${it + 1}",
                        displayLabel = "${rarity.name} #${it + 1}",
                        rarity = rarity,
                        element = element,
                        lat = la,
                        lng = ln,
                        found = false
                    )
                )
            }
            if (includePois) {
                val poiCount = 2 + rng.nextInt(2)
                val poiNames = listOf("Palestra", "Arena", "Santuario", "Torre")
                repeat(poiCount) {
                    val (la, ln) = offset(loc.latitude, loc.longitude, 80.0 + rng.nextDouble() * 300.0, rng)
                    newPois.add(Poi("poi_${nextPoiId.incrementAndGet()}", "${poiNames[rng.nextInt(poiNames.size)]} ${it + 1}", la, ln, "gym"))
                }
            }
            eggs.clear()
            eggs.addAll(newEggs)
            pois.clear()
            pois.addAll(newPois)
        } catch (e: Exception) {
            if (newEggs.isNotEmpty()) {
                eggs.clear()
                eggs.addAll(newEggs)
            }
            if (newPois.isNotEmpty()) {
                pois.clear()
                pois.addAll(newPois)
            }
            android.util.Log.e("OutdoorManager", "regenerate error: ${e.message}")
        }
    }

    private fun offset(lat: Double, lng: Double, meters: Double, rng: java.util.Random): Pair<Double, Double> {
        val ang = rng.nextDouble() * 2 * Math.PI
        val dLat = meters / 111320.0
        val cosLat = kotlin.math.cos(Math.toRadians(lat))
        val dLng = if (kotlin.math.abs(cosLat) > 1e-10) meters / (111320.0 * cosLat) else 0.0
        return lat + dLat * kotlin.math.cos(ang) to lng + dLng * kotlin.math.sin(ang)
    }

    private fun pickRarity(rng: java.util.Random, boostMultiplier: Float = 1.0f): EggRarity {
        val weights = WeatherZoneManager.getRaritySpawnWeights(
            WeatherZoneManager.currentWeather, WeatherZoneManager.currentZone
        )
        val boosted = weights.toMutableMap()
        if (boostMultiplier > 1.0f) {
            boosted["rare"] = (boosted["rare"] ?: 12f) * boostMultiplier
            boosted["epic"] = (boosted["epic"] ?: 6f) * boostMultiplier
            boosted["legendary"] = (boosted["legendary"] ?: 2f) * boostMultiplier
        }
        return WeatherZoneManager.pickWeightedRarity(boosted)
    }

    fun distanceMeters(lat: Double, lng: Double): Float {
        val loc = currentLocation ?: return Float.MAX_VALUE
        return haversine(loc.latitude, loc.longitude, lat, lng)
    }

    fun distanceMeters(egg: WorldEgg): Float = distanceMeters(egg.lat, egg.lng)
    fun distanceMeters(poi: Poi): Float = distanceMeters(poi.lat, poi.lng)

    fun bearingTo(lat: Double, lng: Double): Float {
        val loc = currentLocation ?: return 0f
        return bearing(loc.latitude, loc.longitude, lat, lng)
    }

    fun bearingTo(egg: WorldEgg) = bearingTo(egg.lat, egg.lng)
    fun bearingTo(poi: Poi) = bearingTo(poi.lat, poi.lng)

    fun getCatchRadiusM(egg: WorldEgg): Float = BASE_CATCH_RADIUS_M * egg.rarity.catchRadius

    private fun haversine(la1: Double, ln1: Double, la2: Double, ln2: Double): Float {
        val r = 6371000.0
        val dLa = Math.toRadians(la2 - la1)
        val dLn = Math.toRadians(ln2 - ln1)
        val sLa = kotlin.math.sin(dLa / 2)
        val sLn = kotlin.math.sin(dLn / 2)
        val a = sLa * sLa +
                kotlin.math.cos(Math.toRadians(la1)) * kotlin.math.cos(Math.toRadians(la2)) * sLn * sLn
        return (2 * r * kotlin.math.asin(kotlin.math.sqrt(a))).toFloat()
    }

    private fun bearing(la1: Double, ln1: Double, la2: Double, ln2: Double): Float {
        val dLa = la2 - la1
        val dLn = ln2 - ln1
        if (kotlin.math.abs(dLa) < 1e-12 && kotlin.math.abs(dLn) < 1e-12) return 0f
        val rad = Math.toRadians(1.0)
        val la1r = la1 * rad; val la2r = la2 * rad; val dLnR = dLn * rad
        val y = kotlin.math.sin(dLnR) * kotlin.math.cos(la2r)
        val x = kotlin.math.cos(la1r) * kotlin.math.sin(la2r) -
                kotlin.math.sin(la1r) * kotlin.math.cos(la2r) * kotlin.math.cos(dLnR)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360).toFloat()
    }

    fun getEggs(): List<WorldEgg> = eggs.toList()
    fun getPois(): List<Poi> = pois.toList()

    fun nearestUnfoundEgg(): WorldEgg? =
        eggs.filter { !it.found }.minByOrNull { distanceMeters(it) }

    fun getEgg(id: String): WorldEgg? = eggs.firstOrNull { it.id == id }

    fun tryCatch(ctx: Context, eggId: String, foodBonus: Float = 1f): CatchResult {
        val egg = eggs.firstOrNull { it.id == eggId }
            ?: return CatchResult(false, "Uovo non trovato")
        if (egg.found) return CatchResult(false, "Gia' catturato")
        val d = distanceMeters(egg)
        val radius = getCatchRadiusM(egg) * foodBonus
        if (d > radius) {
            return CatchResult(false, "Avvicinati: mancano ${d.toInt()} m (raggio: ${radius.toInt()} m)")
        }
        val idx = eggs.indexOf(egg)
        val caught = egg.copy(found = true)
        eggs[idx] = caught
        val added = EggInventoryManager.addEgg(ctx, EggInventoryItem.fromWorldEgg(caught))
        PlayerProfileManager.recordEggCatch(caught.rarity) { }
        PlayerProfileManager.markHasPlayedOutdoor()
        val mvcReward = when (caught.rarity) {
            EggRarity.COMMON -> 5.0
            EggRarity.UNCOMMON -> 15.0
            EggRarity.RARE -> 40.0
            EggRarity.EPIC -> 100.0
            EggRarity.LEGENDARY -> 250.0
        }
        com.intelligame.huntix.managers.SavedManager.addMvc(ctx, mvcReward)
        // Track research tasks
        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "catch_3")
        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "catch_20")
        com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "earn_500_mvc", mvcReward.toInt())
        if (caught.rarity.ordinal >= EggRarity.RARE.ordinal) {
            com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "catch_rare")
        }
        if (caught.rarity.ordinal >= EggRarity.EPIC.ordinal) {
            com.intelligame.huntix.managers.ResearchTaskManager.trackProgress(ctx, "find_epic")
        }
        val msg = if (added) "Catturato! ${caught.displayLabel} +${mvcReward.toInt()} MVC"
        else "Catturato ma inventario pieno! +${mvcReward.toInt()} MVC"
        return CatchResult(true, msg, caught)
    }

    fun spinPoi(ctx: Context, poiId: String): String {
        val poi = pois.firstOrNull { it.id == poiId } ?: return "POI non trovato"
        if (poi.spun) return "Gia' visitato: torna piu' tardi"
        poi.spun = true
        val gemReward = 5 + (Math.random() * 10).toInt()
        val xpReward = 50 + (Math.random() * 150).toInt()
        SavedManager.addMvc(ctx, gemReward.toDouble())
        PlayerProfileManager.recordTraining(0, xpReward.toLong()) { }
        return "Palestra spinnata! +$gemReward 💎 +${xpReward} XP"
    }
}

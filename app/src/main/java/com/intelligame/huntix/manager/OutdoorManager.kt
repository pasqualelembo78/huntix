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
import com.intelligame.huntix.EggInventoryItem
import com.intelligame.huntix.EggInventoryManager
import com.intelligame.huntix.EggRarity
import com.intelligame.huntix.WorldEgg
import com.intelligame.huntix.managers.WeatherZoneManager

/**
 * OutdoorManager — cuore della modalita' GPS del mondo esterno.
 *
 * - Tiene traccia della posizione GPS del giocatore (LocationManager, no dipendenze extra).
 * - Genera uova e POI (palestre) proceduralmente attorno al giocatore.
 * - Espone distanza/bearing per la UI radar e la logica di cattura.
 * - Integra l'inventario tramite EggInventoryManager al momento della cattura.
 */
class OutdoorManager private constructor() : SensorEventListener {

    data class Poi(
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

        const val CATCH_RADIUS_M = 25f
        private const val DEFAULT_LAT = 41.9028   // Roma (seed se no GPS)
        private const val DEFAULT_LNG = 12.4964
    }

    private var locationManager: LocationManager? = null
    var currentLocation: Location? = null
        private set
    private val eggs = mutableListOf<WorldEgg>()
    private val pois = mutableListOf<Poi>()
    private var listening = false

    // ── Bussola (orientamento dispositivo) per il radar ──────────
    private var sensorManager: SensorManager? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    @Volatile private var deviceAzimuth: Float = 0f
    private var sensorsRegistered = false

    fun getDeviceHeadingDeg(): Float = deviceAzimuth

    private val listener = LocationListener { loc ->
        currentLocation = loc
        ensureSpawns(loc)
        appCtx?.let { WeatherZoneManager.refreshAsync(it, loc.latitude, loc.longitude) }
    }

    private var appCtx: Context? = null

    fun start(ctx: Context) {
        if (listening) return
        appCtx = ctx.applicationContext
        locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasPerm = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm || locationManager == null) {
            ensureSpawns(defaultLocation())
            return
        }
        val lm = locationManager!!
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { currentLocation = it; ensureSpawns(it) }
        } catch (_: Exception) {
            ensureSpawns(currentLocation ?: defaultLocation())
        }
        var started = false
        if (gpsEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, listener)
                started = true
            } catch (_: Exception) {}
        }
        if (netEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10f, listener)
                started = true
            } catch (_: Exception) {}
        }
        listening = started
        if (!started) ensureSpawns(currentLocation ?: defaultLocation())
        currentLocation?.let {
            WeatherZoneManager.refreshAsync(ctx, it.latitude, it.longitude)
        }
        registerCompass(ctx)
    }

    private fun registerCompass(ctx: Context) {
        if (sensorsRegistered) return
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorsRegistered = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> event.values.copyInto(gravity, 0)
            Sensor.TYPE_MAGNETIC_FIELD -> event.values.copyInto(geomagnetic, 0)
        }
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            deviceAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (deviceAzimuth < 0) deviceAzimuth += 360f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun stop() {
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
        if (eggs.isEmpty()) regenerate(loc)
    }

    /** Rigenera uova e POI attorno alla posizione corrente. */
    fun regenerate(loc: Location) {
        val rng = java.util.Random()
        eggs.clear()
        pois.clear()
        val eggCount = 6 + rng.nextInt(4)
        repeat(eggCount) {
            val (la, ln) = offset(loc.latitude, loc.longitude, 30.0 + rng.nextDouble() * 170.0, rng)
            eggs.add(
                WorldEgg(
                    id = "out_$it",
                    name = "Uovo ${it + 1}",
                    displayLabel = "${pickRarity(rng).name} #${it + 1}",
                    rarity = pickRarity(rng),
                    lat = la,
                    lng = ln,
                    found = false
                )
            )
        }
        val poiCount = 2 + rng.nextInt(2)
        val poiNames = listOf("Palestra", "Arena", "Santuario", "Torre")
        repeat(poiCount) {
            val (la, ln) = offset(loc.latitude, loc.longitude, 80.0 + rng.nextDouble() * 300.0, rng)
            pois.add(Poi("poi_$it", "${poiNames[rng.nextInt(poiNames.size)]} ${it + 1}", la, ln, "gym"))
        }
    }

    private fun offset(lat: Double, lng: Double, meters: Double, rng: java.util.Random): Pair<Double, Double> {
        val ang = rng.nextDouble() * 2 * Math.PI
        val dLat = meters / 111320.0
        val dLng = meters / (111320.0 * kotlin.math.cos(Math.toRadians(lat)))
        return lat + dLat * kotlin.math.cos(ang) to lng + dLng * kotlin.math.sin(ang)
    }

    private fun pickRarity(rng: java.util.Random): EggRarity {
        val weights = WeatherZoneManager.getRaritySpawnWeights(
            WeatherZoneManager.currentWeather, WeatherZoneManager.currentZone
        )
        return WeatherZoneManager.pickWeightedRarity(weights)
    }

    // ── Distanza / bearing ────────────────────────────────────────

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
        val rad = Math.toRadians(1.0)
        val la1r = la1 * rad; val la2r = la2 * rad; val dLn = (ln2 - ln1) * rad
        val y = kotlin.math.sin(dLn) * kotlin.math.cos(la2r)
        val x = kotlin.math.cos(la1r) * kotlin.math.sin(la2r) -
                kotlin.math.sin(la1r) * kotlin.math.cos(la2r) * kotlin.math.cos(dLn)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360).toFloat()
    }

    // ── Accesso dati ─────────────────────────────────────────────

    fun getEggs(): List<WorldEgg> = eggs.toList()
    fun getPois(): List<Poi> = pois.toList()

    fun nearestUnfoundEgg(): WorldEgg? =
        eggs.filter { !it.found }.minByOrNull { distanceMeters(it) }

    fun getEgg(id: String): WorldEgg? = eggs.firstOrNull { it.id == id }

    // ── Cattura ──────────────────────────────────────────────────

    fun tryCatch(ctx: Context, eggId: String): CatchResult {
        val egg = eggs.firstOrNull { it.id == eggId }
            ?: return CatchResult(false, "Uovo non trovato")
        if (egg.found) return CatchResult(false, "Gia' catturato")
        val d = distanceMeters(egg)
        if (d > CATCH_RADIUS_M) {
            return CatchResult(false, "Avvicinati: mancano ${d.toInt()} m")
        }
        val idx = eggs.indexOf(egg)
        eggs[idx] = egg.copy(found = true)
        val added = EggInventoryManager.addEgg(ctx, EggInventoryItem.fromWorldEgg(egg))
        val msg = if (added) "Catturato! ${egg.displayLabel}"
        else "Catturato ma inventario pieno!"
        return CatchResult(true, msg, egg)
    }

    // ── POI (palestre) ───────────────────────────────────────────

    fun spinPoi(poiId: String): String {
        val poi = pois.firstOrNull { it.id == poiId } ?: return "POI non trovato"
        if (poi.spun) return "Gia' visitato: torna piu' tardi"
        poi.spun = true
        val rewards = listOf("2 gemme 💎", "1 uovo raro", "3 caramelle", "50 XP")
        return "Palestra spinnata! Ricompensa: ${rewards.random()}"
    }
}

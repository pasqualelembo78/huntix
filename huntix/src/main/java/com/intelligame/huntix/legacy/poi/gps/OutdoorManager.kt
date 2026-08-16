package com.intelligame.huntix.legacy.poi.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.intelligame.huntix.legacy.poi.unity.PoiUnityBridge

class OutdoorManager private constructor(context: Context) {

    private val ctx = context.applicationContext
    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(ctx)
    }
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    @Volatile private var mockWalker: Job? = null
    @Volatile private var mockMode = false
    @Volatile private var mockOrigin: Location? = null
    @Volatile private var heading = 0.0

    companion object {
        @Volatile private var INSTANCE: OutdoorManager? = null
        fun get(context: Context): OutdoorManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: OutdoorManager(context).also { INSTANCE = it }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(realGps: Boolean = true) {
        // Seed con l'ultima posizione nota del sistema (LocationManager): così
        // MiAcitma parte subito dalla posizione reale anche prima del primo fix
        // di FusedLocationProvider, senza aspettare secondi con il default Roma.
        if (_location.value == null) {
            val last = currentBest()
            if (last != null) _location.value = last
        }
        if (realGps && !mockMode) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).build()
            val callback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let { _location.value = it }
                }
            }
            fused.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        }
    }

    fun isMockMode(): Boolean = mockMode

    fun toggleMockWalk() = enableMockWalk(!mockMode)

    fun nearestStore(
        stores: List<com.intelligame.huntix.legacy.poi.data.PoiStore>,
        lat: Double,
        lng: Double
    ): Pair<com.intelligame.huntix.legacy.poi.data.PoiStore, Float>? {
        if (stores.isEmpty()) return null
        val me = Location("player").apply { latitude = lat; longitude = lng }
        return stores.map { s ->
            val l = Location("store").apply { latitude = s.lat; longitude = s.lng }
            s to me.distanceTo(l)
        }.minByOrNull { it.second }?.let { if (it.second <= 60f) it else null }
    }

    fun tryCatchAt(stores: List<com.intelligame.huntix.legacy.poi.data.PoiStore>, lat: Double, lng: Double) {
        val (store, dist) = nearestStore(stores, lat, lng) ?: return
        val trainer = com.intelligame.huntix.legacy.poi.creature.Persistence.trainer()
        val engine = com.intelligame.huntix.legacy.poi.creature.CatchEngine(trainer)
        val result = engine.tryCatch(store, dist)
        if (result.caught && result.creature != null) {
            val updated = trainer.aggiungiEsperienza(result.expGuadagnata)
            com.intelligame.huntix.legacy.poi.creature.Persistence.saveTrainer(updated)
            PoiUnityBridge.sendEvent(
                "LevelUp", "{\"exp\":${result.expGuadagnata},\"lv\":${updated.livello}}"
            )
        }
    }

    fun enableMockWalk(enable: Boolean, origin: Location? = null) {
        if (enable) {
            val base = origin ?: currentBest() ?: defaultLocation()
            mockOrigin = base
            mockMode = true
            heading = Math.toRadians(45.0)
            startMock()
        } else {
            mockMode = false
            mockWalker?.cancel()
            mockWalker = null
        }
    }

    private fun startMock() {
        mockWalker?.cancel()
        mockWalker = scope.launch {
            var step = 0
            while (isActive && mockMode) {
                val origin = mockOrigin ?: continue
                val radius = 50.0
                val angle = heading + step * 0.02
                val dx = radius * kotlin.math.cos(angle) / 111_320.0
                val dy = radius * kotlin.math.sin(angle) / (111_320.0 * kotlin.math.cos(Math.toRadians(origin.latitude)))
                val lat = origin.latitude + dy
                val lng = origin.longitude + dx
                val loc = Location("mock").apply {
                    latitude = lat
                    longitude = lng
                    this.time = System.currentTimeMillis()
                    this.accuracy = 5f
                }
                _location.value = loc
                step++
                delay(1_000L)
            }
        }
    }

    fun currentLocationSync(): Location? = _location.value

    @SuppressLint("MissingPermission")
    private fun currentBest(): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasPerm = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }
    }

    private fun defaultLocation(): Location = Location("default").apply {
        latitude = 41.9028
        longitude = 12.4964
    }

    private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}

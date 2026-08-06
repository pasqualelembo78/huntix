package com.intelligame.huntix.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.intelligame.huntix.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PoiMapBridge — alimenta la mappa del gioco con i POI reali di Huntix (OSM).
 *
 * Scarica i POI dalla repository huntix-poi (OnlinePoiManager) e li deposita
 * nel ponte [HuntixPoiBridge] del modulo mappa, così la mappa plotta i nostri
 * negozi/edifici al posto dei POI di prova. Cliccando un POI si apre
 * direttamente la pagina JSON personalizzata del negozio.
 */
object PoiMapBridge {

    private const val DEFAULT_LAT = 41.9028
    private const val DEFAULT_LNG = 12.4964
    private const val MAX_POIS = 300

    /**
     * Avvia il fetch dei POI in background. La mappa viene aperta subito:
     * lo splash della mappa dà il tempo al caricamento di completarsi.
     */
    fun feed(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (lat, lng) = currentLocation(context)
                val repo = com.intelligame.huntix.legacy.poi.data.PoiRepository(context)
                val result = OnlinePoiManager().fetchPoiForLocation(
                    context, lat, lng, maxPois = MAX_POIS
                )
                result.onSuccess { pois ->
                    val stores = pois.map {
                        com.intelligame.huntix.legacy.poi.data.PoiStore(
                            id = it.id,
                            name = it.name,
                            lat = it.lat,
                            lng = it.lng,
                            buildingType = it.buildingType,
                            poiType = it.poiType,
                            url = it.url.ifEmpty { null },
                            pageType = pageTypeOf(it.pageType)
                        )
                    }
                    com.intelligame.huntix.legacy.poi.domain.PoiBridge.setPois(stores)
                    AppLog.d("PoiMapBridge", "Bridge POI (online) aggiornati: ${stores.size}")
                }.onFailure {
                    // Fallback offline: usa assets/shops.json locale
                    val local = repo.fetchPoiForLocation(lat, lng, MAX_POIS)
                    local.onSuccess { stores ->
                        com.intelligame.huntix.legacy.poi.domain.PoiBridge.setPois(stores)
                        AppLog.d("PoiMapBridge", "Bridge POI (locale) aggiornati: ${stores.size}")
                    }
                    local.onFailure { e -> AppLog.w("PoiMapBridge", "feed locale fallito: ${e.message}") }
                }
            } catch (e: Exception) {
                AppLog.w("PoiMapBridge", "feed fallito: ${e.message}")
            }
        }
    }

    private fun pageTypeOf(raw: String): com.intelligame.huntix.legacy.poi.data.PageType =
        when (raw.lowercase()) {
            "json", "custom" -> com.intelligame.huntix.legacy.poi.data.PageType.Json
            else -> com.intelligame.huntix.legacy.poi.data.PageType.Url
        }

    /**
     * Posizione corrente: preferisce la levetta/override (OutdoorManager),
     * poi l'ultima posizione GPS nota, poi un centro città di default.
     */
    private fun currentLocation(context: Context): Pair<Double, Double> {
        try {
            OutdoorManager.get().currentLocation?.let {
                return it.latitude to it.longitude
            }
        } catch (_: Exception) {}
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val hasPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm && lm != null) {
                val last = try {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (_: Exception) {
                    null
                }
                if (last != null) return last.latitude to last.longitude
            }
        } catch (_: Exception) {}
        return DEFAULT_LAT to DEFAULT_LNG
    }
}

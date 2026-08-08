package com.intelligame.huntix.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.managers.CustomPageRegistry
import com.intelligame.huntix.reallife.OsmClient
import com.intelligame.huntix.reallife.OsmPoiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PoiMapBridge — alimenta la mappa del gioco con i POI reali di Huntix (OSM).
 *
 * Fonte primaria: POI live da Overpass (OsmPoiRepository.loadNearby, fino a
 * 5 km) così sulla mappa appaiono subito i locali reali del mondo (bar,
 * tabacchi, supermercati, scuole, parchi…). I POI registrati della repository
 * huntix-poi (OnlinePoiManager) vengono aggiunti come arricchimento (hanno le
 * pagine JSON personalizzate). Il risultato viene depositato nel ponte
 * [HuntixPoiBridge] del modulo mappa, che li plotta al posto dei POI di prova.
 * Cliccando un POI si apre la pagina JSON (personalizzata o sintetica OSM).
 */
object PoiMapBridge {

    private const val DEFAULT_LAT = 41.9028
    private const val DEFAULT_LNG = 12.4964
    private const val MAX_POIS = 400
    private const val OSM_RADIUS_METERS = 5_000

    /**
     * Avvia il fetch dei POI in background. La mappa viene aperta subito:
     * lo splash della mappa dà il tempo al caricamento di completarsi, e
     * MapActivity riplotta via PoiBridge renderer quando i dati arrivano.
     */
    fun feed(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (lat, lng) = currentLocation(context)
                val stores = ArrayList<com.intelligame.huntix.legacy.poi.data.PoiStore>()
                val seen = HashSet<String>()

                // 1) POI live OSM (Overpass) — i locali reali intorno all'utente
                try {
                    OsmClient.init(context)
                    CustomPageRegistry.init(context)
                    val osm = OsmPoiRepository.loadNearby(lat, lng, OSM_RADIUS_METERS)
                    for (sr in osm) {
                        if (!seen.add(sr.id)) continue
                        val custom = CustomPageRegistry.resolve(sr.id)
                        stores.add(com.intelligame.huntix.legacy.poi.data.PoiStore(
                            id = sr.id,
                            name = sr.name,
                            lat = sr.lat,
                            lng = sr.lng,
                            buildingType = sr.buildingType,
                            poiType = sr.poiType,
                            url = custom?.url ?: sr.id,
                            pageType = com.intelligame.huntix.legacy.poi.data.PageType.Json
                        ))
                    }
                    AppLog.d("PoiMapBridge", "POI OSM live: ${osm.size}")
                } catch (e: Exception) {
                    AppLog.w("PoiMapBridge", "feed OSM fallito: ${e.message}")
                }

                // 2) POI registrati huntix-poi (pagine custom) come arricchimento
                try {
                    val online = OnlinePoiManager().fetchPoiForLocation(
                        context, lat, lng, maxPois = MAX_POIS
                    )
                    online.onSuccess { pois ->
                        for (p in pois) {
                            if (!seen.add(p.id)) continue
                            stores.add(com.intelligame.huntix.legacy.poi.data.PoiStore(
                                id = p.id,
                                name = p.name,
                                lat = p.lat,
                                lng = p.lng,
                                buildingType = p.buildingType,
                                poiType = p.poiType,
                                url = p.url.ifEmpty { null },
                                pageType = pageTypeOf(p.pageType)
                            ))
                        }
                        AppLog.d("PoiMapBridge", "POI registrati (online): ${pois.size}")
                    }
                } catch (e: Exception) {
                    AppLog.w("PoiMapBridge", "feed online fallito: ${e.message}")
                }

                // 3) Fallback offline (assets/shops.json) se non è arrivato nulla
                if (stores.isEmpty()) {
                    val repo = com.intelligame.huntix.legacy.poi.data.PoiRepository(context)
                    repo.fetchPoiForLocation(lat, lng, MAX_POIS).onSuccess { local ->
                        stores.addAll(local)
                        AppLog.d("PoiMapBridge", "Bridge POI (locale) aggiornati: ${local.size}")
                    }
                }

                val sorted = stores
                    .sortedBy { distanceMeters(lat, lng, it.lat, it.lng) }
                    .take(MAX_POIS)
                com.intelligame.huntix.legacy.poi.domain.PoiBridge.setPois(sorted)
                AppLog.d("PoiMapBridge", "Bridge POI aggiornati: ${sorted.size}")
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

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
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

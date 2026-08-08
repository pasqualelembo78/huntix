package com.intelligame.huntix.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.intelligame.huntix.AppLog
import com.intelligame.huntix.reallife.OsmClient
import com.intelligame.huntix.reallife.OsmNode

/**
 * 🔍 OsmPoiRepository — scoperta venue OSM intorno al GPS.
 *
 * È il motore della "modifica di ricerca negozi" di Esplora: invece di affidarsi
 * al repository statico e sparso `huntix-poi`, scarica (caching 24h + 3 mirror
 * Overpass) **tutti** i locali (negozi, bar, ristoranti, gym, musei…) entro un
 * raggio dal device. Questo risolve "non trovo il bar sotto casa": la copertura
 * OSM è universale, anche a Foggia (dove huntix-poi aveva 0 punti).
 *
 * Flusso (idempotente, per zona):
 *   1. OsmClient.fetchAreaCached(lat, lon, R) → Osmara (cache coordinate-aware)
 *   2. nodi venue (amenity/shop/leisure/tourism/craft/office) con nome → SearchResult
 *      buildingType = tag KEY (AMENITY/SHOP/…), poiType = tag VALUE (restaurant,…)
 *   3. id stabile "osm:node:<id>" → usato da CustomPageRegistry per la pagina JSON
 *   4. callback su main thread → PoiSearchPanel riempie la lista e la ricerca testuale
 */
object OsmPoiRepository {
    private const val TAG = "OsmPoiRepo"
    const val DEFAULT_RADIUS_METERS = 1000

    /** Tag OSM che identificano un "locale" (nodo da includere). */
    private val VENUE_TAGS = listOf("amenity", "shop", "leisure", "tourism", "craft", "office", "healthcare")

    /**
     * POI intorno a (lat,lng) in [radiusMeters] metri.
     * Usa la cache OsmClient (24h, 3 mirror): la stessa zona non viene ridownloadata.
     */
    fun loadNearby(
        lat: Double, lon: Double,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
        context: Context,
        callback: (List<PoiSearchManager.SearchResult>) -> Unit
    ) {
        Thread {
            var pois = emptyList<PoiSearchManager.SearchResult>()
            try {
                OsmClient.init(context)
                val data = OsmClient.fetchAreaCached(lat, lon, radiusMeters)
                pois = data.nodes.values.mapNotNull { nodeToResult(it) }
                AppLog.d(TAG, "loadNearby: ${pois.size} locali / ${data.nodes.size} nodiOSM (r=${radiusMeters}m)")
            } catch (e: Exception) {
                AppLog.w(TAG, "loadNearby failed: ${e.message}")
            }
            Handler(Looper.getMainLooper()).post { callback(pois) }
        }.start()
    }

    /** Mappa un OsmNode in SearchResult; null se non è un venue con nome. */
    fun nodeToResult(node: OsmNode): PoiSearchManager.SearchResult? {
        val name = node.name.takeIf { it.isNotBlank() } ?: return null
        val (key, value) = VENUE_TAGS.firstNotNullOf { tag ->
            val v = node.tags[tag]?.takeIf { it.isNotBlank() } ?: return@firstNotNullOf null
            tag to v
        } ?: return null
        return PoiSearchManager.SearchResult(
            id = "osm:node:${node.id}",
            name = name,
            lat = node.lat, lng = node.lon,
            buildingType = key.uppercase(),
            poiType = value,
            url = "",
            pageType = "",
            city = "", region = "",
            category = value
        )
    }

    /** Convert a SearchResult (osm id) back to its raw coordinates for the map. */
    fun coordinatesOf(result: PoiSearchManager.SearchResult): Pair<Double, Double> =
        result.lat to result.lng
}

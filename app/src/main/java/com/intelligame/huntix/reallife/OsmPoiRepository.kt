package com.intelligame.huntix.reallife

import com.intelligame.huntix.managers.PoiSearchManager
import java.util.Locale

/**
 * 📡 OsmPoiRepository — converte i nodi OSM live (Overpass) in [PoiSearchManager.SearchResult]
 * classificabili con la logica categorie di Esplora.
 *
 * Mappa un nodo OSM al SearchResult usando la sua PRIMA "main tag"
 * (shop/amenity/tourism/leisure/craft/office/healthcare) come [buildingType]
 * e il relativo valore come [poiType]. PoiSearchManager.isStore / categoryOf
 * (match esatto sui valori OSM canonici) lo classifica senza falsi positivi.
 *
 * Un nodo senza main tag oppure non "locale" (es. bench, hospital, parking)
 * viene scartato da isStore.
 */
object OsmPoiRepository {

    private val MAIN_TAGS = arrayOf("shop", "amenity", "tourism", "leisure", "craft", "office", "healthcare")

    /** POI locali entro [radiusMeters] da (lat,lng), classificati per categoria Esplora. */
    fun loadNearby(lat: Double, lng: Double, radiusMeters: Int = 1000): List<PoiSearchManager.SearchResult> {
        val data = OsmClient.fetchAreaCached(lat, lng, radiusMeters)
        val mgr = PoiSearchManager()
        val out = ArrayList<PoiSearchManager.SearchResult>()
        for (node in data.nodes.values) {
            val key = MAIN_TAGS.firstOrNull { node.tags[it]?.isNotBlank() == true } ?: continue
            val value = node.tags[key]!!
            val name = node.tags["name"]
                ?.takeIf { it.isNotBlank() }
                ?: buildDisplayName(key, value)
            val sr = PoiSearchManager.SearchResult(
                id = "osm:node:${node.id}",
                name = name,
                lat = node.lat,
                lng = node.lon,
                buildingType = key,
                poiType = value,
                url = "osm:${node.id}",
                pageType = "osm",
                city = "",
                region = "",
                category = ""
            )
            if (mgr.isStore(sr)) {
                out.add(sr.copy(category = mgr.categoryOf(sr).label))
            }
        }
        return out
    }

    private fun buildDisplayName(key: String, value: String): String {
        val pt = value.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        return when (key) {
            "shop" -> "Negozio $pt"
            else -> pt
        }
    }
}

package com.intelligame.huntix.reallife

import com.intelligame.huntix.managers.PoiSearchManager
import java.util.Locale

/**
 * 📡 OsmPoiRepository — converte i POI OSM live (Overpass) in [PoiSearchManager.SearchResult]
 * classificabili con la logica categorie di Esplora.
 *
 * Mappa un nodo/way OSM al SearchResult usando la sua PRIMA "main tag"
 * (shop/amenity/tourism/leisure/craft/office/healthcare/man_made/natural)
 * come [buildingType] e il relativo valore come [poiType]. Copre anche i
 * poligoni (way): parchi, scuole e supermercati "a edificio" vengono inclusi
 * usando il baricentro dei nodi. PoiSearchManager.isStore / categoryOf
 * (match esatto sui valori OSM canonici) lo classifica senza falsi positivi.
 *
 * Usa la query "solo POI" ([OsmClient.fetchPoisCached]) così il raggio può
 * arrivare fino a 10 km senza scaricare edifici/strade/alberi.
 */
object OsmPoiRepository {

    /** Raggio massimo supportato (10 km) — oltre viene saturato dal chiamante. */
    const val MAX_RADIUS_METERS = 10_000

    private val MAIN_TAGS = arrayOf(
        "shop", "amenity", "tourism", "leisure", "craft", "office", "healthcare",
        "man_made", "natural"
    )

    /** Valori natural/man_made scartati (rumore: alberi, materiali, ecc.). */
    private val NOISE_VALUES = setOf("tree", "wood", "grassland", "heath")

    /** POI entro [radiusMeters] (max 10 km) da (lat,lng), classificati per categoria Esplora. */
    fun loadNearby(lat: Double, lng: Double, radiusMeters: Int = 1000): List<PoiSearchManager.SearchResult> {
        val radius = radiusMeters.coerceIn(100, MAX_RADIUS_METERS)
        val data = OsmClient.fetchPoisCached(lat, lng, radius)
        return classify(data)
    }

    /** Classifica un OsmData scaricato in POI Esplora (solo store-like, con categoria). */
    fun classify(data: OsmData): List<PoiSearchManager.SearchResult> {
        val mgr = PoiSearchManager()
        val out = ArrayList<PoiSearchManager.SearchResult>()

        for (node in data.nodes.values) {
            toResult(node)?.let { sr ->
                if (mgr.isStore(sr)) {
                    out.add(sr.copy(category = mgr.categoryOf(sr).label))
                }
            }
        }

        for (way in data.ways) {
            toResult(way)?.let { sr ->
                if (mgr.isStore(sr)) {
                    out.add(sr.copy(category = mgr.categoryOf(sr).label))
                }
            }
        }
        return out
    }

    /** Nodo OSM → SearchResult (null se non è un POI riconosciuto). */
    private fun toResult(node: OsmNode): PoiSearchManager.SearchResult? {
        val (key, value) = mainTag(node.tags) ?: return null
        val name = node.tags["name"]?.takeIf { it.isNotBlank() }
            ?: buildDisplayName(key, value)
        return PoiSearchManager.SearchResult(
            id = "osm:node:${node.id}",
            name = name,
            lat = node.lat,
            lng = node.lon,
            buildingType = key,
            poiType = value,
            url = "osm:node:${node.id}",
            pageType = "osm",
            city = "",
            region = "",
            category = ""
        )
    }

    /** Way OSM (poligono: parco, scuola, supermercato…) → SearchResult al baricentro. */
    private fun toResult(way: OsmWay): PoiSearchManager.SearchResult? {
        if (way.nodes.isEmpty()) return null
        val (key, value) = mainTag(way.tags) ?: return null
        var lat = 0.0
        var lon = 0.0
        for (n in way.nodes) { lat += n.lat; lon += n.lon }
        lat /= way.nodes.size
        lon /= way.nodes.size
        val name = way.tags["name"]?.takeIf { it.isNotBlank() }
            ?: buildDisplayName(key, value)
        return PoiSearchManager.SearchResult(
            id = "osm:way:${way.id}",
            name = name,
            lat = lat,
            lng = lon,
            buildingType = key,
            poiType = value,
            url = "osm:way:${way.id}",
            pageType = "osm",
            city = "",
            region = "",
            category = ""
        )
    }

    /** Prima main tag presente; scarta valori di rumore (bench, tree, material…). */
    private fun mainTag(tags: Map<String, String>): Pair<String, String>? {
        for (key in MAIN_TAGS) {
            val value = tags[key]?.takeIf { it.isNotBlank() } ?: continue
            if (value in NOISE_VALUES) continue
            // i valori "natural/man_made" che non sono POI restano fuori da isStore
            return key to value
        }
        return null
    }

    private fun buildDisplayName(key: String, value: String): String {
        val pt = value.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        return when (key) {
            "shop" -> "Negozio $pt"
            else -> pt
        }
    }
}

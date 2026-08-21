package com.intelligame.huntix.reallife

import com.intelligame.huntix.AppLog
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * OsmCityJsonFactory — serializza i dati OSM scaricati in un envelope JSON
 * compatto consumabile da Unity (JsonUtility).
 *
 * La città di MiAcitma viene costruita in Unity a partire da questi dati:
 * strade (polyline lat/lng), edifici (poligono + tipo + nome + negozio/amenity),
 * alberi (punti) e parchi (poligoni). Le coordinate restano geografiche
 * (lat/lng): Unity le converte in metri locali con il proprio CoordinateConverter,
 * così lo streaming può ri-centrare il mondo sul giocatore senza ricalcoli.
 *
 * Limiti di volume (per tenere l'envelope < ~1 MB su aree dense):
 *   - 500 strade (le più lunghe)
 *   - 600 edifici (i più grandi, footprint >= 3 m)
 *   - 250 alberi
 *   - 30 parchi
 */
object OsmCityJsonFactory {

    private const val MAX_ROADS = 500
    private const val MAX_BUILDINGS = 600
    private const val MAX_TREES = 250
    private const val MAX_PARKS = 30
    private const val MIN_FOOTPRINT_M = 3.0
    private const val MIN_PARK_M = 10.0

    /** Costruisce l'envelope JSON per Unity. */
    fun build(data: OsmData, centerLat: Double, centerLng: Double, radiusMeters: Int): String {
        val root = JSONObject()
        root.put("centerLat", round6(centerLat))
        root.put("centerLng", round6(centerLng))
        root.put("radiusMeters", radiusMeters)
        root.put("done", true)

        root.put("roads", buildRoads(data))
        root.put("buildings", buildBuildings(data))
        root.put("trees", buildTrees(data))
        root.put("parks", buildParks(data))
        root.put("traffic_signals", buildTrafficSignals(data))

        val roads = root.getJSONArray("roads").length()
        val buildings = root.getJSONArray("buildings").length()
        val trees = root.getJSONArray("trees").length()
        val parks = root.getJSONArray("parks").length()
        root.put("count", roads + buildings + trees + parks)
        AppLog.d("OsmCityJsonFactory", "envelope OK: strade=$roads edifici=$buildings alberi=$trees parchi=$parks")
        return root.toString()
    }

    private fun buildRoads(data: OsmData): JSONArray {
        val arr = JSONArray()
        val roads = data.roads
            .filter { it.nodes.size >= 2 }
            .sortedByDescending { it.totalLength() }
            .take(MAX_ROADS)

        for (r in roads) {
            val jo = JSONObject()
            jo.put("id", r.id)
            jo.put("highway", r.highway)
            jo.put("name", r.streetName)
            jo.put("points", pointsOf(r.nodes))
            if (r.isTunnel) jo.put("tunnel", true)
            if (r.isBridge) jo.put("bridge", true)
            if (r.layer != 0) jo.put("layer", r.layer)
            if (r.maxspeed.isNotEmpty()) jo.put("maxspeed", r.maxspeed)
            arr.put(jo)
        }
        return arr
    }

    private fun buildBuildings(data: OsmData): JSONArray {
        val arr = JSONArray()
        val buildings = data.buildings
            .filter { it.nodes.size >= 3 }
            .sortedByDescending { it.calculateFootprint()?.let { fp -> fp.width.toDouble() * fp.depth } ?: 0.0 }
            .take(MAX_BUILDINGS)

        for (w in buildings) {
            val fp = w.calculateFootprint() ?: continue
            if (fp.width < MIN_FOOTPRINT_M || fp.depth < MIN_FOOTPRINT_M) continue

            val jo = JSONObject()
            jo.put("id", w.id)
            jo.put("kind", buildingKind(w))
            jo.put("name", w.name)
            jo.put("shop", w.shop)
            jo.put("amenity", w.amenity)
            jo.put("height", (w.height * 10).roundToLong() / 10.0)
            jo.put("levels", w.levels)
            jo.put("points", pointsOf(w.nodes))
            arr.put(jo)
        }
        return arr
    }

    private fun buildTrees(data: OsmData): JSONArray {
        val arr = JSONArray()
        for (t in data.trees.take(MAX_TREES)) {
            val p = JSONObject()
            p.put("lat", round6(t.lat))
            p.put("lng", round6(t.lon))
            arr.put(p)
        }
        return arr
    }

    private fun buildParks(data: OsmData): JSONArray {
        val arr = JSONArray()
        val parks = data.parks
            .filter { it.nodes.size >= 3 }
            .sortedByDescending { it.calculateFootprint()?.let { fp -> fp.width.toDouble() * fp.depth } ?: 0.0 }
            .take(MAX_PARKS)

        for (p in parks) {
            val fp = p.calculateFootprint() ?: continue
            if (fp.width < MIN_PARK_M || fp.depth < MIN_PARK_M) continue
            val jo = JSONObject()
            jo.put("points", pointsOf(p.nodes))
            arr.put(jo)
        }
        return arr
    }

    /** Serie di punti {lat,lng} da una lista di nodi (6 decimali ~ 11 cm). */
    private fun pointsOf(nodes: List<OsmNode>): JSONArray {
        val arr = JSONArray()
        for (n in nodes) {
            val p = JSONObject()
            p.put("lat", round6(n.lat))
            p.put("lng", round6(n.lon))
            arr.put(p)
        }
        return arr
    }

    private fun buildTrafficSignals(data: OsmData): JSONArray {
        val arr = JSONArray()
        for (n in data.trafficSignals) {
            val jo = JSONObject()
            jo.put("id", n.id)
            jo.put("lat", round6(n.lat))
            jo.put("lng", round6(n.lon))
            arr.put(jo)
        }
        return arr
    }

    /** Categoria semantica dell'edificio (per la palette Unity). */
    private fun buildingKind(w: OsmWay): String {
        if (w.shop.isNotEmpty()) return "shop"
        if (w.amenity.isNotEmpty()) return "amenity"
        val building = w.tags["building"]?.lowercase() ?: ""
        return when {
            building.contains("commercial") || building.contains("retail") || building.contains("office") -> "commercial"
            building.contains("industrial") || building.contains("warehouse") || building.contains("factory") -> "industrial"
            else -> "residential"
        }
    }

    private fun round6(v: Double): Double = (v * 1e6).roundToLong() / 1e6
}

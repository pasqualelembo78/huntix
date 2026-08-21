package com.intelligame.huntix.reallife

import android.graphics.Color

/**
 * Modelli dati per OpenStreetMap.
 * Rappresentano i nodi, way e relazioni scaricate da Overpass API.
 */

/** Nodo OSM singolo (punto geografico) */
data class OsmNode(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String> = emptyMap()
) {
    /** Coordinate locali (metri) calcolate dal CoordinateConverter */
    val localX: Float get() = CoordinateConverter.lonToX(lon)
    val localZ: Float get() = CoordinateConverter.latToZ(lat)

    /** Se è un albero */
    val isTree: Boolean get() = tags["natural"] == "tree"

    /** Nome del POI */
    val name: String get() = tags["name"] ?: ""

    /** Tipo di amenity */
    val amenity: String get() = tags["amenity"] ?: ""

    /** Tipo di shop */
    val shop: String get() = tags["shop"] ?: ""

    /** Tipo di leisure */
    val leisure: String get() = tags["leisure"] ?: ""
}

/** Way OSM (percorso/area chiusa) */
data class OsmWay(
    val id: Long,
    val nodeIds: List<Long>,
    val tags: Map<String, String> = emptyMap()
) {
    /** Vertici del perimetro (popolati dopo il parsing) */
    var nodes: List<OsmNode> = emptyList()

    /** Se è un edificio */
    val isBuilding: Boolean get() = tags["building"] != null

    /** Nome dell'edificio */
    val name: String get() = tags["name"] ?: ""

    /** Altezza in metri (da tag height o stima da building:levels) */
    val height: Double
        get() {
            val h = tags["height"]
            if (h != null) {
                val cleaned = h.replace("m", "").trim().toDoubleOrNull()
                if (cleaned != null) return cleaned
            }
            val levels = tags["building:levels"]
            if (levels != null) {
                val l = levels.toDoubleOrNull()
                if (l != null) return l * 3.0 // 3 metri per piano
            }
            return 10.0 // default: 3 piani
        }

    /** Numero piani */
    val levels: Int
        get() {
            val l = tags["building:levels"]
            if (l != null) return l.toIntOrNull() ?: 3
            return (height / 3.0).toInt().coerceAtLeast(1)
        }

    /** Colore facciata (da tag building:colour) */
    val facadeColor: Int?
        get() {
            val c = tags["building:colour"] ?: return null
            return try {
                Color.parseColor(c)
            } catch (e: Exception) {
                null
            }
        }

    /** Colore tetto (da tag building:roof:colour) */
    val roofColor: Int?
        get() {
            val c = tags["building:roof:colour"] ?: return null
            return try {
                Color.parseColor(c)
            } catch (e: Exception) {
                null
            }
        }

    /** Forma tetto (da tag building:roof:shape) */
    val roofShape: String get() = tags["building:roof:shape"] ?: "flat"

    /** Tipo strada (da tag highway) */
    val highway: String get() = tags["highway"] ?: ""

    /** Nome della via */
    val streetName: String get() = tags["name"] ?: ""

    /** Numero corsie */
    val lanes: Int get() = tags["lanes"]?.toIntOrNull() ?: 1

    /** Tipo superficie */
    val surface: String get() = tags["surface"] ?: "asphalt"

    /** Limite velocità */
    val maxspeed: String get() = tags["maxspeed"] ?: ""

    /** È un tunnel? */
    val isTunnel: Boolean get() = tags["tunnel"] in setOf("yes", "true", "1")

    /** È un ponte? */
    val isBridge: Boolean get() = tags["bridge"] in setOf("yes", "true", "1")

    /** Layer (piano verticale: -1=sotto terra, 0=piano strada, 1=sopra) */
    val layer: Int get() = tags["layer"]?.toIntOrNull() ?: 0

    /** Tipo di leisure (park, garden, etc.) */
    val leisure: String get() = tags["leisure"] ?: ""

    /** Tipo di amenity */
    val amenity: String get() = tags["amenity"] ?: ""

    /** Tipo di shop */
    val shop: String get() = tags["shop"] ?: ""

    /** Bounding box del footprint */
    data class Footprint(
        val centerX: Float,
        val centerZ: Float,
        val width: Float,
        val depth: Float,
        val rotation: Float // in radianti
    )

    /** Calcola il footprint semplificato (rettangolo orientato) del way */
    fun calculateFootprint(): Footprint? {
        if (nodes.isEmpty()) return null

        val xs = nodes.map { it.localX }
        val zs = nodes.map { it.localZ }

        val minX = xs.min()
        val maxX = xs.max()
        val minZ = zs.min()
        val maxZ = zs.max()

        val w = maxX - minX
        val d = maxZ - minZ
        if (w < 0.5f || d < 0.5f) return null // troppo piccolo

        val cx = (minX + maxX) / 2f
        val cz = (minZ + maxZ) / 2f

        // Calcola angolo di rotazione dal primo lato
        var rotation = 0f
        if (nodes.size >= 2) {
            val dx = nodes[1].localX - nodes[0].localX
            val dz = nodes[1].localZ - nodes[0].localZ
            rotation = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
        }

        return Footprint(cx, cz, w, d, rotation)
    }

    /** Lunghezza totale del way (per le strade) */
    fun totalLength(): Float {
        if (nodes.size < 2) return 0f
        var len = 0f
        for (i in 0 until nodes.size - 1) {
            val dx = nodes[i + 1].localX - nodes[i].localX
            val dz = nodes[i + 1].localZ - nodes[i].localZ
            len += Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        }
        return len
    }

    /** Segmenti del way (coppie di punti) */
    fun segments(): List<Pair<OsmNode, OsmNode>> {
        if (nodes.size < 2) return emptyList()
        return nodes.zipWithNext()
    }
}

/** Dati OSM completi scaricati e parsati */
data class OsmData(
    val nodes: Map<Long, OsmNode> = emptyMap(),
    val ways: List<OsmWay> = emptyList(),
    val south: Double = 0.0,
    val north: Double = 0.0,
    val west: Double = 0.0,
    val east: Double = 0.0
) {
    /** Strade filtrate */
    val roads: List<OsmWay>
        get() = ways.filter { it.highway.isNotEmpty() }

    /** Edifici filtrati */
    val buildings: List<OsmWay>
        get() = ways.filter { it.isBuilding }

    /** Parchi */
    val parks: List<OsmWay>
        get() = ways.filter { it.leisure == "park" }

    /** Alberi */
    val trees: List<OsmNode>
        get() = nodes.values.filter { it.isTree }

    /** POI (amenity o shop) */
    val pois: List<OsmNode>
        get() = nodes.values.filter { it.amenity.isNotEmpty() || it.shop.isNotEmpty() }

    /** Semafori (traffic signals) */
    val trafficSignals: List<OsmNode>
        get() = nodes.values.filter { it.tags["highway"] == "traffic_signals" }

    /** Statistiche */
    fun stats(): String {
        return "OSM Data: ${nodes.size} nodi, ${ways.size} way, " +
                "${roads.size} strade, ${buildings.size} edifici, " +
                "${parks.size} parchi, ${trees.size} alberi, ${pois.size} POI"
    }
}

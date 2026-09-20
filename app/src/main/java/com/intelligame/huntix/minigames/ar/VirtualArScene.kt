package com.intelligame.huntix.minigames.ar

import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Modello della scena "AR Virtuale": la porzione di mappa OSM (tile geo)
 * attorno al POI virtuale, gia' convertita in metri locali.
 *
 * Formato tile (GET /api/tiles/{key}/geo):
 *   roads:     [{nm, hw, pts:[{a:lat,o:lng}, ...]}]
 *   buildings: [{id, c:[lat,lng], d:[w,l] (m), r (deg), t, nm, pts}]
 *   pois:      [{id, t, p:[lat,lng], nm}]
 */
object VirtualArScene {

    data class Building(
        val x: Float, val z: Float,
        val w: Float, val d: Float,
        val rot: Float, val height: Float,
        val type: String, val name: String
    )

    data class Marker(
        val x: Float, val z: Float,
        val type: String, val name: String
    )

    data class Road(
        val xs: FloatArray,
        val zs: FloatArray,
        val width: Float,
        val name: String,
        val distToCenter: Float
    )

    data class Model(
        val key: String,
        val buildings: List<Building>,
        val markers: List<Marker>,
        val roads: List<Road>
    )

    // Un grado di latitudine vale ~111320 m; in longitudine va corretto col cos.
    private const val M_PER_DEG_LAT = 111320.0
    private const val MAX_BUILDINGS = 300
    private const val MAX_ROADS = 60
    private const val MAX_ROAD_SEGMENTS = 900

    /**
     * Converte la tile geo in metri locali con origine sul POI. x = est
     * (+lng), z = -nord (-lat) cosi' da usare la convenzione Unity/SceneView
     * (forward = -z). Restituisce null se il JSON manca o e' corrotto.
     */
    fun parse(json: String?, poiLat: Double, poiLng: Double, radiusMeters: Float): Model? {
        if (json.isNullOrEmpty()) return null
        return try {
            val root = JSONObject(json)
            val key = root.optString("tile", "")
            val mPerDegLng = M_PER_DEG_LAT * cos(Math.toRadians(poiLat))
            val r = radiusMeters.toDouble()
            val rRoads = r * 1.6

            val buildings = ArrayList<Building>()
            val barr = root.optJSONArray("buildings")
            if (barr != null) {
                for (i in 0 until barr.length()) {
                    val o = barr.optJSONObject(i) ?: continue
                    val c = o.optJSONArray("c") ?: continue
                    if (c.length() < 2) continue
                    val lat = c.optDouble(0)
                    val lng = c.optDouble(1)
                    if (lat.isNaN() || lng.isNaN()) continue
                    val dx = (lng - poiLng) * mPerDegLng
                    val dz = -(lat - poiLat) * M_PER_DEG_LAT
                    if (hypot(dx, dz) > r) continue

                    val dim = o.optJSONArray("d")
                    val rawW = if (dim != null && dim.length() > 0) dim.optDouble(0).toFloat() else 6f
                    val rawD = if (dim != null && dim.length() > 1) dim.optDouble(1).toFloat() else rawW
                    val type = o.optString("t", "")
                    buildings.add(
                        Building(
                            x = dx.toFloat(),
                            z = dz.toFloat(),
                            w = rawW.coerceIn(1.5f, 25f),
                            d = rawD.coerceIn(1.5f, 25f),
                            rot = o.optDouble("r", 0.0).toFloat(),
                            height = heightFor(type),
                            type = type,
                            name = o.optString("nm", "")
                        )
                    )
                }
            }
            buildings.sortBy { hypot(it.x.toDouble(), it.z.toDouble()) }
            val capped = if (buildings.size > MAX_BUILDINGS) buildings.subList(0, MAX_BUILDINGS) else buildings

            val markers = ArrayList<Marker>()
            val ps = root.optJSONArray("pois")
            if (ps != null) {
                for (i in 0 until ps.length()) {
                    val o = ps.optJSONObject(i) ?: continue
                    val p = o.optJSONArray("p") ?: continue
                    if (p.length() < 2) continue
                    val lat = p.optDouble(0)
                    val lng = p.optDouble(1)
                    if (lat.isNaN() || lng.isNaN()) continue
                    val dx = (lng - poiLng) * mPerDegLng
                    val dz = -(lat - poiLat) * M_PER_DEG_LAT
                    if (hypot(dx, dz) > r) continue
                    markers.add(Marker(dx.toFloat(), dz.toFloat(), o.optString("t", ""), o.optString("nm", "")))
                }
            }

            val roads = ArrayList<Road>()
            val rarr = root.optJSONArray("roads")
            if (rarr != null) {
                for (i in 0 until rarr.length()) {
                    if (roads.size >= MAX_ROADS) break
                    val o = rarr.optJSONObject(i) ?: continue
                    val pts = o.optJSONArray("pts") ?: continue
                    if (pts.length() < 2) continue
                    val n = pts.length()
                    val xs = FloatArray(n)
                    val zs = FloatArray(n)
                    var used = 0
                    var minDist = Double.MAX_VALUE
                    for (j in 0 until n) {
                        val p = pts.optJSONObject(j) ?: continue
                        val lat = p.optDouble("a")
                        val lng = p.optDouble("o")
                        if (lat.isNaN() || lng.isNaN()) continue
                        val dx = (lng - poiLng) * mPerDegLng
                        val dz = -(lat - poiLat) * M_PER_DEG_LAT
                        val d = hypot(dx, dz)
                        if (d < minDist) minDist = d
                        xs[used] = dx.toFloat()
                        zs[used] = dz.toFloat()
                        used++
                    }
                    if (used < 2 || minDist > rRoads) continue
                    roads.add(
                        Road(
                            xs = xs.copyOf(used),
                            zs = zs.copyOf(used),
                            width = roadWidthFor(o.optString("hw", "")),
                            name = o.optString("nm", ""),
                            distToCenter = minDist.toFloat()
                        )
                    )
                }
            }
            roads.sortBy { it.distToCenter }
            val segments = roads.sumOf { (it.xs.size - 1).coerceAtLeast(0) }
            val roadList = if (segments > MAX_ROAD_SEGMENTS) {
                var acc = 0
                var limit = roads.size
                for (i in roads.indices) {
                    acc += roads[i].xs.size - 1
                    if (acc >= MAX_ROAD_SEGMENTS) { limit = (i + 1).coerceAtMost(roads.size); break }
                }
                roads.subList(0, limit)
            } else roads

            Model(key, capped, markers, roadList)
        } catch (e: Exception) {
            null
        }
    }

    /** Larghezza stimata (m) della carreggiata in base al tipo di strada OSM. */
    fun roadWidthFor(highway: String): Float = when (highway.lowercase()) {
        "motorway", "trunk" -> 7f
        "primary" -> 4.8f
        "secondary" -> 4.2f
        "tertiary" -> 3.4f
        "unclassified" -> 2.8f
        "residential", "living_street" -> 2.6f
        "service", "track" -> 2.2f
        "pedestrian", "busway" -> 2.2f
        "footway", "path", "cycleway" -> 1.4f
        "steps" -> 1.2f
        else -> 3.0f
    }

    /** Altezza (m) stimata dall'uso OSM dell'edificio. */
    fun heightFor(type: String): Float = when (type.lowercase()) {
        "house", "residential", "detached", "terrace", "bungalow", "villa" -> 7f
        "apartments", "dormitory" -> 12f
        "commercial", "retail", "shop", "supermarket", "kiosk" -> 5.5f
        "industrial", "warehouse", "factory" -> 8f
        "school", "university", "hospital", "hotel" -> 12f
        "church", "cathedral", "chapel", "mosque", "synagogue" -> 16f
        "garage", "garages", "hut", "shed", "carport", "roof" -> 3f
        "train_station", "transportation" -> 10f
        else -> 6.5f
    }
}

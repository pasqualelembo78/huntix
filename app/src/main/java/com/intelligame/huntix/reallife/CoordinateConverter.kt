package com.intelligame.huntix.reallife

/**
 * Coordinate convertitore: da lat/lon OpenStreetMap a coordinate locali (metri).
 *
 * Origine (0,0,0) = centro della mappa (lat/lon specificati).
 * Asse X = Est/Ovest (1 unità = 1 metro)
 * Asse Z = Nord/Sud (1 unità = 1 metro)
 */
object CoordinateConverter {

    private var centerLat = 0.0
    private var centerLon = 0.0

    private const val METERS_PER_DEG_LAT = 110540.0
    private var metersPerDegLon = 82855.0

    private val lock = Any()

    fun init(centerLat: Double, centerLon: Double) {
        synchronized(lock) {
            this.centerLat = centerLat
            this.centerLon = centerLon
            this.metersPerDegLon = 111320.0 * Math.cos(Math.toRadians(centerLat))
        }
    }

    fun lonToX(lon: Double): Float {
        return synchronized(lock) { ((lon - centerLon) * metersPerDegLon).toFloat() }
    }

    fun latToZ(lat: Double): Float {
        return synchronized(lock) { ((lat - centerLat) * METERS_PER_DEG_LAT).toFloat() }
    }

    fun toLocal(lat: Double, lon: Double): Pair<Float, Float> {
        return lonToX(lon) to latToZ(lat)
    }
}

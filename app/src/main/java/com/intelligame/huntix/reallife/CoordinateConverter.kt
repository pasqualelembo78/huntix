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

    fun init(centerLat: Double, centerLon: Double) {
        this.centerLat = centerLat
        this.centerLon = centerLon
        this.metersPerDegLon = 111320.0 * Math.cos(Math.toRadians(centerLat))
    }

    fun lonToX(lon: Double): Float {
        return ((lon - centerLon) * metersPerDegLon).toFloat()
    }

    fun latToZ(lat: Double): Float {
        return ((lat - centerLat) * METERS_PER_DEG_LAT).toFloat()
    }

    fun toLocal(lat: Double, lon: Double): Pair<Float, Float> {
        return lonToX(lon) to latToZ(lat)
    }

    fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c
    }
}

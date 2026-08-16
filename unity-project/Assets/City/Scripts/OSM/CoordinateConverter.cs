using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Convertitore lat/lon OpenStreetMap -> coordinate locali (metri).
    /// Origine (0,0) = centro mappa (lat/lon impostati con Init).
    /// X = Est/Ovest, Z = Nord/Sud, 1 unità = 1 metro.
    /// Porting C# dell'omonimo CoordinateConverter (Kotlin) usato da CityActivity.
    /// </summary>
    public static class CoordinateConverter
    {
        private const double MetersPerDegLat = 110540.0;

        private static double _centerLat;
        private static double _centerLon;
        private static double _metersPerDegLon = 82855.0;

        public static void Init(double centerLat, double centerLon)
        {
            _centerLat = centerLat;
            _centerLon = centerLon;
            _metersPerDegLon = 111320.0 * Mathf.Cos((float)(centerLat * Mathf.Deg2Rad));
        }

        public static float LonToX(double lon)
        {
            return (float)((lon - _centerLon) * _metersPerDegLon);
        }

        public static float LatToZ(double lat)
        {
            return (float)((lat - _centerLat) * MetersPerDegLat);
        }

        public static Vector2 ToLocal(double lat, double lon)
        {
            return new Vector2(LonToX(lon), LatToZ(lat));
        }
    }
}

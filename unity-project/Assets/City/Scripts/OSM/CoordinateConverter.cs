using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Wrapper di compatibilita': il codice esistente (CityOSMWorld, traffico,
    /// veicoli) continua a usare LonToX/LatToZ, ma ora delega a WorldOrigin cosi'
    /// che sistema legacy e chunk system condividano la STESSA origine mobile.
    /// </summary>
    public static class CoordinateConverter
    {
        private const double MetersPerDegLat = 110540.0;

        public static void Init(double centerLat, double centerLon)
        {
            WorldOrigin.Init(centerLat, centerLon);
        }

        public static float LonToX(double lon)
        {
            return WorldOrigin.ToWorld(0.0, lon).x;
        }

        public static float LatToZ(double lat)
        {
            return WorldOrigin.ToWorld(lat, 0.0).z;
        }

        public static Vector2 ToLocal(double lat, double lon)
        {
            var v = WorldOrigin.ToWorld(lat, lon);
            return new Vector2(v.x, v.z);
        }
    }
}

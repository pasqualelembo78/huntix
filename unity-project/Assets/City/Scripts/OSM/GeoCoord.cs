using System;

namespace City.OSM
{
    /// <summary>
    /// Coordinata geografica in double precision (gradi decimali).
    /// Sostituisce l'uso di float per le coordinate assolute: un float ha
    /// ~7 cifre significative, a lat 41.9 l'errore arriva a ~0.4 m.
    /// </summary>
    [Serializable]
    public struct GeoCoord : IEquatable<GeoCoord>
    {
        public double lat;
        public double lng;

        public GeoCoord(double lat, double lng)
        {
            this.lat = lat;
            this.lng = lng;
        }

        public const double MetersPerDegLat = 110540.0;

        public static double MetersPerDegLon(double lat)
        {
            return 111320.0 * Math.Cos(lat * Math.PI / 180.0);
        }

        /// <summary>Distanza approssimata equirettangolare ( sufficiente sotto i ~100 km).</summary>
        public double DistanceMeters(GeoCoord other)
        {
            double mpdl = MetersPerDegLon((lat + other.lat) * 0.5);
            double dx = (lng - other.lng) * mpdl;
            double dz = (lat - other.lat) * MetersPerDegLat;
            return Math.Sqrt(dx * dx + dz * dz);
        }

        public bool Equals(GeoCoord other) => lat == other.lat && lng == other.lng;
        public override bool Equals(object obj) => obj is GeoCoord g && Equals(g);
        public override int GetHashCode() => lat.GetHashCode() ^ (lng.GetHashCode() << 16);
        public override string ToString() => $"({lat:F6},{lng:F6})";
    }
}

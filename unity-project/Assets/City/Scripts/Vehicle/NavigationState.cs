using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Destinazione di navigazione selezionata dal giocatore (mappa espansa,
    /// offerte contestuali). Bussola e minimappa la leggono a ogni refresh.
    /// </summary>
    public static class NavigationState
    {
        public class Destination
        {
            public string name;
            public string kind;      // dealer | repair | garage | hospital | ""
            public double lat;
            public double lng;

            public Vector3 WorldPos
            {
                get { return WorldOrigin.ToWorld(lat, lng); }
            }
        }

        public static Destination Current { get; private set; }

        /// <summary>Arrivato: la destinazione si annulla da sola sotto 20 m.</summary>
        public const float ArriveMeters = 20f;

        public static void Set(string name, string kind, double lat, double lng)
        {
            Current = new Destination
            {
                name = name, kind = kind, lat = lat, lng = lng
            };
        }

        /// <summary>Imposta come destinazione il POI piu' vicino del tipo dato.</summary>
        public static bool SetNearest(string kind, Vector3 nearWorldPos,
            string labelFallback)
        {
            GeoCoord g = WorldOrigin.ToGeo(nearWorldPos);
            var p = VehiclePoiRegistry.Nearest(kind, g.lat, g.lng);
            if (p == null) return false;
            Set(string.IsNullOrEmpty(p.name) ? labelFallback : p.name,
                kind, p.lat, p.lng);
            return true;
        }

        public static void Clear()
        {
            Current = null;
        }

        /// <summary>Distanza in linea d'aria dalla posizione data (o -1).</summary>
        public static float DistanceFrom(Vector3 worldPos)
        {
            if (Current == null) return -1f;
            return Vector3.Distance(Current.WorldPos, worldPos);
        }
    }
}

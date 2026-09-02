using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Registro statico dei POI veicoli incontrati (concessionarie, officine,
    /// garage): posizione geografica + locale. Serve al client per trovare
    /// "l'officina piu' vicina" dove il ladro consegna l'auto riscattata e
    /// per le distanze garage-auto nel ricovero.
    /// </summary>
    public static class VehiclePoiRegistry
    {
        public class PoiInfo
        {
            public string id;
            public string kind;      // dealer | repair | garage
            public string name;
            public double lat;
            public double lng;
            public string phone;     // telefono (se noto)
            public string website;   // sito web (se noto)
        }

        private static readonly Dictionary<string, PoiInfo> pois =
            new Dictionary<string, PoiInfo>();

        public static void Register(VehiclePoiZone zone, GeoCoord geoPos)
        {
            if (zone == null || string.IsNullOrEmpty(zone.poiId)) return;
            string key = zone.kind + "_" + zone.poiId;
            pois[key] = new PoiInfo
            {
                id = zone.poiId,
                kind = KindString(zone.kind),
                name = zone.DefaultName(),
                lat = geoPos.lat,
                lng = geoPos.lng,
            };
        }

        public static void Register(string kind, string id, string name,
            double lat, double lng, string phone = null, string website = null)
        {
            pois[kind + "_" + id] = new PoiInfo
            {
                id = id, kind = kind, name = name, lat = lat, lng = lng,
                phone = phone, website = website
            };
        }

        public static string KindString(VehiclePoiZone.PoiKind kind)
        {
            return kind == VehiclePoiZone.PoiKind.Dealer ? "dealer"
                : kind == VehiclePoiZone.PoiKind.Repair ? "repair"
                : kind == VehiclePoiZone.PoiKind.Hospital ? "hospital"
                : kind == VehiclePoiZone.PoiKind.Ramp ? "rampa"
                : kind == VehiclePoiZone.PoiKind.School ? "school"
                : kind == VehiclePoiZone.PoiKind.Bar ? "bar"
                : kind == VehiclePoiZone.PoiKind.Bank ? "bank"
                : "garage";
        }

        /// <summary>Officina piu' vicina a una posizione geografica.</summary>
        public static PoiInfo NearestRepair(double lat, double lng)
        {
            return Nearest("repair", lat, lng);
        }

        public static PoiInfo Nearest(string kind, double lat, double lng)
        {
            PoiInfo best = null;
            double bestD2 = double.MaxValue;
            foreach (var p in pois.Values)
            {
                if (kind != null && p.kind != kind) continue;
                double dlat = p.lat - lat;
                double dlng = (p.lng - lng) * 0.75;
                double d2 = dlat * dlat + dlng * dlng;
                if (d2 < bestD2) { bestD2 = d2; best = p; }
            }
            return best;
        }

        /// <summary>
        /// POI piu' vicino di un tipo, escludendo un id (per i cartelli che
        /// non devono segnalare se stessi).
        /// </summary>
        public static PoiInfo NearestExcept(string kind, string excludeId,
            double lat, double lng)
        {
            PoiInfo best = null;
            double bestD2 = double.MaxValue;
            foreach (var p in pois.Values)
            {
                if (p.kind != kind || p.id == excludeId) continue;
                double dlat = p.lat - lat;
                double dlng = (p.lng - lng) * 0.75;
                double d2 = dlat * dlat + dlng * dlng;
                if (d2 < bestD2) { bestD2 = d2; best = p; }
            }
            return best;
        }

        /// <summary>
        /// POI piu' vicino di QUALSIASI tipo entro maxMeters (per il tap
        /// sulla mappa espansa). La metrica e' in gradi approssimata.
        /// </summary>
        public static PoiInfo NearestAny(double lat, double lng,
            double meters)
        {
            PoiInfo best = null;
            double bestD2 = double.MaxValue;
            foreach (var p in pois.Values)
            {
                double dlat = p.lat - lat;
                double dlng = (p.lng - lng) * 0.75;
                double d2 = dlat * dlat + dlng * dlng;
                if (d2 < bestD2) { bestD2 = d2; best = p; }
            }
            double tolDeg = meters / 111000.0;
            return best != null && bestD2 <= tolDeg * tolDeg ? best : null;
        }

        /// <summary>Tutti i POI registrati (mappa espansa).</summary>
        public static IEnumerable<PoiInfo> All()
        {
            return pois.Values;
        }

        /// <summary>Tutti i POI noti di un tipo (per fallback di spawn).</summary>
        public static IEnumerable<PoiInfo> AllOf(string kind)
        {
            foreach (var p in pois.Values)
                if (p.kind == kind) yield return p;
        }
    }
}

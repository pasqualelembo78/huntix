using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using City.Interior.Visit;
using City.Events;

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
                // Le rampe METROPOLITANA portano il nome dichiarato: cosi'
                // l'entry del chunk (che poi sovrascrive quella sintetica)
                // non torna a "Parcheggio Coperto" in mappa/ricerca.
                name = MetroRampRegistry.IsMetroName(zone.poiName)
                    ? zone.poiName : zone.DefaultName(),
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
                : kind == VehiclePoiZone.PoiKind.Fuel ? "fuel"
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
            foreach (var p in All())
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
            foreach (var p in All())
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
            foreach (var p in All())
            {
                double dlat = p.lat - lat;
                double dlng = (p.lng - lng) * 0.75;
                double d2 = dlat * dlat + dlng * dlng;
                if (d2 < bestD2) { bestD2 = d2; best = p; }
            }
            double tolDeg = meters / 111000.0;
            return best != null && bestD2 <= tolDeg * tolDeg ? best : null;
        }

        // I POI sintetici (case + impianti OSM) derivano da asset statici e
        // non cambiano durante la partita: li creiamo una volta sola. La
        // mappa espansa e la minimappa iterano All() molto spesso, quindi
        // riallocare centinaia di PoiInfo a ogni redraw sarebbe uno spreco.
        private static PoiInfo[] _synthetic;

        private static PoiInfo[] Synthetic
        {
            get
            {
                if (_synthetic != null) return _synthetic;
                var list = new List<PoiInfo>();
                foreach (var spec in VisitInteriorRegistry.All)
                {
                    if (spec == null) continue;
                    list.Add(new PoiInfo
                    {
                        id = VisitInteriorRegistry.StableKey(spec.osmId),
                        kind = spec.kind ?? "casa",
                        name = spec.label,
                        lat = spec.lat,
                        lng = spec.lng
                    });
                }
                foreach (var venue in VenueRegistry.All)
                {
                    if (venue == null) continue;
                    list.Add(new PoiInfo
                    {
                        id = venue.id,
                        kind = venue.kind,
                        name = string.IsNullOrEmpty(venue.city)
                            ? venue.name : venue.name + " — " + venue.city,
                        lat = venue.lat,
                        lng = venue.lng
                    });
                }
                // Rampe METROPOLITANA vicine alla casa di Foggia: sono POI di
                // gioco, ma per map/search/nav sono rampe a tutti gli effetti.
                var metros = MetroRampRegistry.All;
                for (int i = 0; i < metros.Length; i++)
                {
                    var m = metros[i];
                    if (m == null) continue;
                    list.Add(new PoiInfo
                    {
                        // Stesso id del VehiclePoiZone creato dal placer:
                        // una sola entry per rampa, non due duplicate.
                        id = MetroRampRegistry.NumericId(i).ToString(
                            System.Globalization.CultureInfo.InvariantCulture),
                        kind = "rampa",
                        name = m.name,
                        lat = m.lat,
                        lng = m.lng
                    });
                }
                _synthetic = list.ToArray();
                return _synthetic;
            }
        }

        /// <summary>
        /// Tutti i POI registrati (mappa espansa). Oltre ai POI veicoli creati
        /// dai chunk include le case visitabili OSM e gli impianti sportivi
        /// REALI del registro OSM come POI sintetici: porta di casa, stadio e
        /// ippodromo diventano selezionabili con TELEPORTA/FRECCETTA esattamente
        /// come le rampe sotterranee, senza girare a vuoto.
        /// </summary>
        public static IEnumerable<PoiInfo> All()
        {
            foreach (var p in pois.Values) yield return p;
            PoiInfo[] extra = Synthetic;
            for (int i = 0; i < extra.Length; i++) yield return extra[i];
        }

        /// <summary>Tutti i POI noti di un tipo (per fallback di spawn).</summary>
        public static IEnumerable<PoiInfo> AllOf(string kind)
        {
            foreach (var p in All())
                if (p.kind == kind) yield return p;
        }
    }
}

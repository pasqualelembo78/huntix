using System;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;
using City.OSM;

namespace City.Events
{
    /// <summary>
    /// Catalogo degli impianti sportivi REALI generato da OSM
    /// (scripts/venue_registry_build.py). Sostituisce la vecchia scelta per
    /// hash del chunk: stadio e ippodromo nascono solo dove esistono davvero,
    /// con le coordinate dell'impronta OSM e un solo impianto principale per
    /// citta'. Nessun campo nuovo nel wire geo/graph e nessuna rete a runtime.
    /// </summary>
    public static class VenueRegistry
    {
        public const string KindStadio = "stadio";
        public const string KindIppodromo = "ippodromo";

        [Serializable]
        public sealed class Spec
        {
            public string id;
            public string kind;
            public long osmId;
            public string osmType;
            public string name;
            public string city;
            public string address;
            public double lat;
            public double lng;
            public float spanLatM;
            public float spanLonM;
            public float axisDeg;
            public float priority;
        }

        /// <summary>
        /// Formato del file JSON. Unity JsonUtility serializza in modo
        /// affidabile int/float/string, non double/long: i valori grezzi
        /// restano in tipi semplici e vengono convertiti in Spec solo dopo
        /// il parse (nessun rischio di coordinate a zero in build).
        /// </summary>
        [Serializable]
        private sealed class Doc
        {
            public int schema;
            public string source;
            public string country;
            public string rule;
            public Row[] venues;
        }

        [Serializable]
        private sealed class Row
        {
            public string id;
            public string kind;
            public int osmId;
            public string osmType;
            public string name;
            public string city;
            public string address;
            public float lat;
            public float lng;
            public float spanLatM;
            public float spanLonM;
            public float axisDeg;
            public float priority;
        }

        private static readonly Spec[] Empty = new Spec[0];
        private static Spec[] _all;
        private static Dictionary<Vector2Int, List<Spec>> _byChunk;

        /// <summary>Carica tutti i file generati in Resources/Venues: oggi
        /// l'Italia, domani eventuali altri paesi senza toccare il codice.</summary>
        public static Spec[] All
        {
            get
            {
                if (_all != null) return _all;
                var rows = new List<Spec>();
                TextAsset[] assets = Resources.LoadAll<TextAsset>("Venues");
                if (assets != null)
                {
                    for (int i = 0; i < assets.Length; i++)
                    {
                        if (assets[i] == null || string.IsNullOrEmpty(assets[i].text))
                            continue;
                        Doc doc;
                        try { doc = JsonUtility.FromJson<Doc>(assets[i].text); }
                        catch (Exception) { doc = null; }
                        if (doc == null || doc.venues == null) continue;
                        for (int j = 0; j < doc.venues.Length; j++)
                        {
                            Row r = doc.venues[j];
                            if (r == null || r.lat < -90f || r.lat > 90f ||
                                r.lng < -180f || r.lng > 180f ||
                                r.osmId <= 0) continue;
                            rows.Add(new Spec
                            {
                                id = r.id,
                                kind = r.kind,
                                osmId = r.osmId,
                                osmType = r.osmType,
                                name = r.name,
                                city = r.city,
                                address = r.address,
                                lat = r.lat,
                                lng = r.lng,
                                spanLatM = r.spanLatM,
                                spanLonM = r.spanLonM,
                                axisDeg = r.axisDeg,
                                priority = r.priority
                            });
                        }
                    }
                }
                rows.Sort(delegate(Spec a, Spec b)
                {
                    int c = string.Compare(a.kind, b.kind, StringComparison.Ordinal);
                    if (c != 0) return c;
                    c = b.priority.CompareTo(a.priority);
                    if (c != 0) return c;
                    return string.Compare(a.name, b.name, StringComparison.Ordinal);
                });
                _all = rows.Count == 0 ? Empty : rows.ToArray();
                return _all;
            }
        }

        /// <summary>Indice chunk -> impianti: il ChunkBuilder chiede solo gli
        /// impianti del chunk che sta costruendo, non l'elenco completo.</summary>
        public static List<Spec> InChunk(Vector2Int index)
        {
            if (_byChunk == null)
            {
                _byChunk = new Dictionary<Vector2Int, List<Spec>>();
                Spec[] all = All;
                for (int i = 0; i < all.Length; i++)
                {
                    Vector2Int c = CityGrid.ChunkIndexOf(all[i].lat, all[i].lng);
                    List<Spec> list;
                    if (!_byChunk.TryGetValue(c, out list))
                    {
                        list = new List<Spec>();
                        _byChunk.Add(c, list);
                    }
                    list.Add(all[i]);
                }
            }
            List<Spec> found;
            return _byChunk.TryGetValue(index, out found)
                ? found : new List<Spec>();
        }

        public static IEnumerable<Spec> AllOf(string kind)
        {
            Spec[] all = All;
            for (int i = 0; i < all.Length; i++)
                if (all[i].kind == kind) yield return all[i];
        }

        /// <summary>Primo impianto di una categoria: i casi guida (Foggia,
        /// Castelluccio dei Sauri) hanno priority massima e sono quindi le
        /// destinazioni dei pulsanti Android.</summary>
        public static Spec First(string kind)
        {
            Spec[] all = All;
            for (int i = 0; i < all.Length; i++)
                if (all[i].kind == kind) return all[i];
            return null;
        }

        public static Spec Find(string id)
        {
            if (string.IsNullOrEmpty(id)) return null;
            Spec[] all = All;
            for (int i = 0; i < all.Length; i++)
                if (all[i].id == id) return all[i];
            return null;
        }

        /// <summary>Ponte Unity -> Android: coordinate e indirizzo reale
        /// degli impianti. Con un centro valido vengono scelti i piu' vicini
        /// (default 8): la mappa in game usa comunque il registro completo,
        /// ma il snapshot resta piccolo e i pulsanti Android puntano
        /// all'impianto reale della zona del giocatore.</summary>
        public static string TargetsJson(double centerLat = 0, double centerLng = 0,
            int limit = 0)
        {
            Spec[] all = All;
            var rows = new List<Spec>();
            bool centered = centerLat != 0.0 || centerLng != 0.0;
            if (limit <= 0)
            {
                rows.AddRange(all);
            }
            else
            {
                // Budget separato per categoria: i pulsanti STADIO e
                // IPPODROMO devono trovare entrambi una destinazione reale.
                string[] kinds = { KindStadio, KindIppodromo };
                for (int k = 0; k < kinds.Length; k++)
                {
                    var group = new List<Spec>();
                    for (int i = 0; i < all.Length; i++)
                        if (all[i].kind == kinds[k]) group.Add(all[i]);
                    if (centered)
                    {
                        double clat = centerLat, clng = centerLng;
                        group.Sort(delegate(Spec a, Spec b)
                        {
                            return Dist2(a, clat, clng).CompareTo(
                                Dist2(b, clat, clng));
                        });
                    }
                    if (group.Count > limit) group.RemoveRange(limit, group.Count - limit);
                    rows.AddRange(group);
                }
            }
            var sb = new System.Text.StringBuilder("[");
            for (int i = 0; i < rows.Count; i++)
            {
                Spec s = rows[i];
                if (sb.Length > 1) sb.Append(',');
                sb.Append("{\"id\":\"").Append(Escape(s.id))
                  .Append("\",\"kind\":\"").Append(Escape(s.kind))
                  .Append("\",\"osmId\":").Append(s.osmId.ToString(CultureInfo.InvariantCulture))
                  .Append(",\"name\":\"").Append(Escape(s.name))
                  .Append("\",\"city\":\"").Append(Escape(s.city))
                  .Append("\",\"address\":\"").Append(Escape(s.address))
                  .Append("\",\"lat\":").Append(s.lat.ToString("R", CultureInfo.InvariantCulture))
                  .Append(",\"lng\":").Append(s.lng.ToString("R", CultureInfo.InvariantCulture))
                  .Append("\"}");
            }
            sb.Append(']');
            return sb.ToString();
        }

        private static double Dist2(Spec s, double lat, double lng)
        {
            double dlat = s.lat - lat;
            double dlng = (s.lng - lng) * System.Math.Cos(
                lat * System.Math.PI / 180.0);
            return dlat * dlat + dlng * dlng;
        }

        private static string Escape(string value)
        {
            return (value ?? "").Replace("\\", "\\\\").Replace("\"", "\\\"");
        }
    }
}

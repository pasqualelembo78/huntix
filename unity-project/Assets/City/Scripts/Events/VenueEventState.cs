using System;
using UnityEngine;
using City.Environment;
using City.OSM;
using Huntix.Bridge;

namespace City.Events
{
    /// <summary>Stati del ciclo di vita di un evento sportivo/ambientale.</summary>
    public enum VenuePhase
    {
        Inattivo = 0,
        Programmato = 1,
        InCorso = 2,
        Intervallo = 3,
        Terminato = 4
    }

    public enum VenueKind
    {
        Stadio = 0,
        Ippodromo = 1
    }

    /// <summary>Evento pubblicato su EventBus e simulato anche nel mirror Android.</summary>
    public struct VenueEventChangedEvent
    {
        public readonly VenueKind Kind;
        public readonly VenuePhase Phase;
        public readonly string Detail;
        public readonly string Lat;
        public readonly string Lng;
        public readonly long TsMt;

        public VenueEventChangedEvent(VenueKind kind, VenuePhase phase, string detail,
            string lat, string lng, long ts)
        {
            Kind = kind; Phase = phase; Detail = detail;
            Lat = lat; Lng = lng; TsMt = ts;
        }
    }

    /// <summary>Evento ambientale applicato alla simulazione (pioggia/vento/caldo).</summary>
    public struct EnvironmentalEventAppliedEvent
    {
        public readonly string Name;
        public readonly string Place;
        public EnvironmentalEventAppliedEvent(string name, string place)
        { Name = name; Place = place; }
    }

    /// <summary>
    /// Stato condiviso degli eventi. Non introduce un secondo sistema economico:
    /// pubblica solo EventBus + il ponte obbligatorio Unity↔Android e viaggia
    /// dentro lo snapshot CityStateSync (merge non distruttivo).
    /// </summary>
    public static class VenueEventState
    {
        private const string PREFS_KEY = "huntix_venue_event_v1";

        [Serializable]
        private class Saved
        {
            public int kind;
            public int phase;
            public string detail = "";
            public string lat = "0";
            public string lng = "0";
            public long tsMt;
        }

        public static VenueKind Kind { get; private set; }
        public static VenuePhase Phase { get; private set; }
        public static string Detail { get; private set; } = "";
        public static string Lat { get; private set; } = "0";
        public static string Lng { get; private set; } = "0";
        public static long TsMt { get; private set; }

        static VenueEventState()
        {
            try
            {
                string raw = PlayerPrefs.GetString(PREFS_KEY, "");
                if (!string.IsNullOrEmpty(raw)) Apply(JsonUtility.FromJson<Saved>(raw), false);
            }
            catch (Exception) { /* stato assente: default Inattivo */ }
        }

        public static void Set(VenueKind kind, VenuePhase phase, string detail, Vector3 at)
        {
            Apply(new Saved
            {
                kind = (int)kind,
                phase = (int)phase,
                detail = detail ?? "",
                lat = LatOf(at),
                lng = LngOf(at),
                tsMt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            }, true);
        }

        private static void Apply(Saved s, bool emit)
        {
            if (s == null) return;
            Kind = (VenueKind)Mathf.Clamp(s.kind, 0, 1);
            Phase = (VenuePhase)Mathf.Clamp(s.phase, 0, 4);
            Detail = s.detail ?? "";
            Lat = string.IsNullOrEmpty(s.lat) ? "0" : s.lat;
            Lng = string.IsNullOrEmpty(s.lng) ? "0" : s.lng;
            TsMt = s.tsMt;
            if (!emit) return;

            try { PlayerPrefs.SetString(PREFS_KEY, Export()); PlayerPrefs.Save(); }
            catch (Exception) { }
            try { UnityBridge.SendMessageToAndroid("VenueEventChanged", ExportBridgeJson()); }
            catch (Exception e) { OsmDiag.Log("[VenueEventState] bridge: " + e.Message); }
            EventBus.Publish(new VenueEventChangedEvent(Kind, Phase, Detail, Lat, Lng, TsMt));
        }

        private static string ExportBridgeJson()
        {
            return "{\"kind\":\"" + Kind + "\",\"phase\":\"" + Phase +
                "\",\"detail\":\"" + Esc(Detail) + "\",\"lat\":\"" + Lat +
                "\",\"lng\":\"" + Lng + "\",\"tsMt\":" + TsMt + "}";
        }

        public static string Export()
        {
            return JsonUtility.ToJson(new Saved
            {
                kind = (int)Kind, phase = (int)Phase, detail = Detail,
                lat = Lat, lng = Lng, tsMt = TsMt
            });
        }

        /// <summary>Merge non distruttivo: applica solo uno stato più recente.</summary>
        public static void Import(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            try
            {
                Saved s = JsonUtility.FromJson<Saved>(json);
                if (s == null || s.tsMt <= TsMt) return;
                Apply(s, false);
                try { PlayerPrefs.SetString(PREFS_KEY, json); PlayerPrefs.Save(); }
                catch (Exception) { }
            }
            catch (Exception) { }
        }

        private static string LatOf(Vector3 at)
        {
            if (!WorldOrigin.Initialized) return "0";
            var g = WorldOrigin.ToGeo(at);
            return g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
        }

        private static string LngOf(Vector3 at)
        {
            if (!WorldOrigin.Initialized) return "0";
            var g = WorldOrigin.ToGeo(at);
            return g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
        }

        private static string Esc(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
                    .Replace("\n", " ").Replace("\r", " ");
        }
    }
}

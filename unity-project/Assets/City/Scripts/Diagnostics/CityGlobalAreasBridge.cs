// Mandato §13 · MiCittà è una parte del tutto.
// Canale unico Unity -> Android per le aree globali (inventario/abilità) e per
// ogni azione di MiCittà (scoperte, acquisti, vendite, lavori). Android resta
// il proprietario del profilo: qui si manda l'evento e si lascia, al massimo,
// un uovo-indizio nel normale canale FeatureEggHidden. Nessun secondo inventario
// e nessuna seconda economia.
using System;
using System.Globalization;
using UnityEngine;
using Huntix.Bridge;

namespace City.Diagnostics
{
    public static class CityGlobalAreasBridge
    {
        public const string Prefix = "huntix_global_area_";
        public const string EventInventory = "AreaInventarioOpen";
        public const string EventSkills = "AreaAbilitaOpen";
        public const string EventActivity = "CityGlobalActivity";
        public const string AllPlace = "città→aree globali";

        private static readonly string[] Counters = { "inv", "skill", "disc", "buy", "sell" };

        /// <summary>Apre l'Area Inventario Android dal menu di MiCittà.</summary>
        public static void OpenInventory()
        {
            Open(EventInventory, "inventario", "apertura");
        }

        /// <summary>Apre l'Area Abilità Android dal menu di MiCittà.</summary>
        public static void OpenSkills()
        {
            Open(EventSkills, "abilita", "apertura");
        }

        /// <summary>Riporta una scoperta, un acquisto o una vendita al mondo Huntix.</summary>
        public static void Record(string area, string action, string detail, string place)
        {
            if (string.IsNullOrEmpty(area)) return;
            Bump(area);
            string json = BuildJson(area, action, detail, place);
            Send(EventActivity, json);
            // Riflesso uova una tantum per area+azione: il giocatore trova
            // l'indizio nel mondo, non riceve valuta o un secondo premio.
            MarkEgg(area, action, place);
        }

        /// <summary>Ripristino passivo dal JSON di setMode: prende il massimo
        /// mai visto, senza generare uova né notifiche.</summary>
        public static void RestoreFromAndroid(string flags)
        {
            if (string.IsNullOrEmpty(flags)) return;
            string[] parts = flags.Split(';');
            for (int i = 0; i < parts.Length; i++)
            {
                string[] kv = parts[i].Split('=');
                if (kv.Length != 2) continue;
                int value;
                if (!int.TryParse(kv[1], out value)) continue;
                string key = Prefix + kv[0].Trim().ToLowerInvariant();
                if (PlayerPrefs.GetInt(key, 0) < value) PlayerPrefs.SetInt(key, value);
            }
            PlayerPrefs.Save();
        }

        /// <summary>Flag compatti rimandati ad Android nello snapshot CityStateSync.</summary>
        public static string ExportFlags()
        {
            string[] result = new string[Counters.Length];
            for (int i = 0; i < Counters.Length; i++)
                result[i] = Counters[i] + "=" + PlayerPrefs.GetInt(Prefix + Counters[i], 0);
            return string.Join(";", result);
        }

        private static void Open(string eventName, string area, string action)
        {
            string json = BuildJson(area, action, "", AllPlace);
            Send(eventName, json);
            MarkEgg(area, action, AllPlace);
        }

        private static void Bump(string area)
        {
            string key = CounterKey(area);
            if (key == null) return;
            PlayerPrefs.SetInt(Prefix + key, PlayerPrefs.GetInt(Prefix + key, 0) + 1);
            PlayerPrefs.Save();
        }

        private static string CounterKey(string area)
        {
            switch ((area ?? "").ToLowerInvariant())
            {
                case "inventario": case "inventory": case "uova": return "inv";
                case "abilita": case "abilità": case "skill": case "skills": case "mestieri": return "skill";
                case "scoperte": case "discovery": case "discoveries": return "disc";
                case "acquisti": case "buy": case "purchase": case "purchases": return "buy";
                case "vendita": case "vendite": case "sell": case "sale": case "sales": return "sell";
                default: return null;
            }
        }

        private static string BuildJson(string area, string action, string detail, string place)
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var at = City.OSM.WorldOrigin.ToGeo(Vector3.zero);
                lat = at.lat.ToString("F6", CultureInfo.InvariantCulture);
                lng = at.lng.ToString("F6", CultureInfo.InvariantCulture);
            }
            return "{\"area\":\"" + Escape(area) +
                   "\",\"action\":\"" + Escape(string.IsNullOrEmpty(action) ? "azione" : action) +
                   "\",\"detail\":\"" + Escape(detail ?? "") +
                   "\",\"place\":\"" + Escape(string.IsNullOrEmpty(place) ? AllPlace : place) +
                   "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                   "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
        }

        private static void MarkEgg(string area, string action, string place)
        {
            string id = "globali->" + area + "->" + action;
            string key = Prefix + "egg_" + id.GetHashCode().ToString("X", CultureInfo.InvariantCulture);
            if (PlayerPrefs.GetInt(key, 0) != 0) return;
            PlayerPrefs.SetInt(key, 1);
            PlayerPrefs.Save();
            if (Game.Instance != null)
                Game.Instance.GrantAuditEgg(id, "Uovo dell'area " + area,
                    string.IsNullOrEmpty(place) ? AllPlace : place);
        }

        private static void Send(string eventName, string json)
        {
            try { UnityBridge.SendMessageToAndroid(eventName, json); }
            catch (Exception e) { City.OSM.OsmDiag.Log("[GlobalAreas] " + eventName + ": " + e.Message); }
        }

        private static string Escape(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
                    .Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }
}

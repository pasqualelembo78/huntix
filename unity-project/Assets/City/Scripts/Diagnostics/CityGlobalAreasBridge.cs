// Mandato §13 · MiCittà è una parte del tutto.
// Canale unico Unity -> Android per le aree globali (inventario/abilità) e per
// ogni azione di MiCittà (scoperte, acquisti, vendite, lavori). Android resta
// il proprietario del profilo: qui si manda l'evento e si lascia, al massimo,
// un uovo-indizio nel normale canale FeatureEggHidden. Nessun secondo inventario
// e nessuna seconda economia.
using System;
using System.Collections.Generic;
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
        /// <summary>1 = il beneficio "prima casa gratis" è già stato usato.
        /// Vive con gli altri flag globali: torna a Unity in setMode e torna
        /// ad Android nello snapshot CityStateSync.</summary>
        public const string KeyFirstHomeFree = "freehome";

        /// <summary>True finché il profilo non ha ancora preso la prima casa.</summary>
        public static bool FirstHomeFreeAvailable
        {
            get { return PlayerPrefs.GetInt(Prefix + KeyFirstHomeFree, 0) == 0; }
        }

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
            Record(area, action, detail, place, Vector3.zero);
        }

        /// <summary>Stessa segnalazione, ma con le coordinate reali del luogo
        /// (usata dalla compravendita casa: la porta, non l'origine del mondo).</summary>
        public static void Record(string area, string action, string detail, string place,
            Vector3 at)
        {
            if (string.IsNullOrEmpty(area)) return;
            Bump(area);
            string json = BuildJson(area, action, detail, place, at);
            Send(EventActivity, json);
            // Riflesso uova una tantum per area+azione: il giocatore trova
            // l'indizio nel mondo, non riceve valuta o un secondo premio.
            MarkEgg(area, action, place, at);
        }

        /// <summary>Consuma il beneficio una tantum della prima casa gratuita e
        /// lo racconta al mondo con luogo e coordinate della porta. Nessun
        /// premio in valuta: solo l'uovo-indizio una tantum.</summary>
        public static void RecordFirstHomeFree(string homeName, Vector3 at)
        {
            string nm = string.IsNullOrEmpty(homeName) ? "Casa" : homeName;
            string place = "città→casa→prima casa gratis→" + nm;
            Bump("acquisti");
            Send(EventActivity, BuildJson("acquisti", "prima casa gratis", nm, place, at));
            MarkEggAt("casa->prima-gratis", "Uovo della prima casa gratis", place, at);
            PlayerPrefs.SetInt(Prefix + KeyFirstHomeFree, 1);
            PlayerPrefs.Save();
        }

        /// <summary>Segna il beneficio come già usato senza eventi né uova.
        /// Serve alla migrazione: chi aveva già comprato una casa a pagamento
        /// prima di questa feature non riceve una seconda casa gratuita.</summary>
        public static void MarkFirstHomeFreeUsed()
        {
            if (!FirstHomeFreeAvailable) return;
            PlayerPrefs.SetInt(Prefix + KeyFirstHomeFree, 1);
            PlayerPrefs.Save();
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

        /// <summary>Flag compatti rimandati ad Android nello snapshot CityStateSync.
        /// Include freehome=1 dopo la prima casa: il beneficio non si ripresenta
        /// su un'altra installazione o dopo la vendita.</summary>
        public static string ExportFlags()
        {
            var result = new List<string>();
            for (int i = 0; i < Counters.Length; i++)
                result.Add(Counters[i] + "=" + PlayerPrefs.GetInt(Prefix + Counters[i], 0));
            result.Add(KeyFirstHomeFree + "=" +
                       (FirstHomeFreeAvailable ? 0 : 1).ToString(CultureInfo.InvariantCulture));
            return string.Join(";", result.ToArray());
        }

        private static void Open(string eventName, string area, string action)
        {
            string json = BuildJson(area, action, "", AllPlace);
            Send(eventName, json);
            MarkEgg(area, action, AllPlace, Vector3.zero);
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
            return BuildJson(area, action, detail, place, Vector3.zero);
        }

        private static string BuildJson(string area, string action, string detail, string place,
            Vector3 at)
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var g = City.OSM.WorldOrigin.ToGeo(at);
                lat = g.lat.ToString("F6", CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F6", CultureInfo.InvariantCulture);
            }
            return "{\"area\":\"" + Escape(area) +
                   "\",\"action\":\"" + Escape(string.IsNullOrEmpty(action) ? "azione" : action) +
                   "\",\"detail\":\"" + Escape(detail ?? "") +
                   "\",\"place\":\"" + Escape(string.IsNullOrEmpty(place) ? AllPlace : place) +
                   "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                   "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
        }

        private static void MarkEgg(string area, string action, string place, Vector3 at)
        {
            string id = "globali->" + area + "->" + action;
            MarkEggAt(id, "Uovo dell'area " + area,
                string.IsNullOrEmpty(place) ? AllPlace : place, at);
        }

        /// <summary>Uovo-indizio una tantum, piazzato nel punto reale
        /// dell'azione (la porta di casa) invece che accanto al player.</summary>
        private static void MarkEggAt(string id, string label, string place, Vector3 at)
        {
            string key = Prefix + "egg_" + id.GetHashCode().ToString("X", CultureInfo.InvariantCulture);
            if (PlayerPrefs.GetInt(key, 0) != 0) return;
            PlayerPrefs.SetInt(key, 1);
            PlayerPrefs.Save();
            if (Game.Instance != null)
                Game.Instance.GrantAuditEggAt(id, label, place, at);
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

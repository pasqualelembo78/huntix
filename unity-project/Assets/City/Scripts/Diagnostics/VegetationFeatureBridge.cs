// Ponte delle migliorie naturali (prati, fiori, cespugli, rocce, alberi di
// parco, luci). Gruppo ADDITIVO: pref, evento Android e chiave JSON dedicati,
// nessuna modifica a CinematicFeatureBridge/OpenSourceFeatureBridge.
// Nessun premio in valuta: solo il riflesso uova via FeatureEggHidden.
using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;
using City.OSM;

namespace City.Diagnostics
{
    /// <summary>
    /// Stato e bridge bidirezionale dei dettagli naturali di tutto il mondo.
    /// Android e' proprietario delle preferenze: le salva in huntix_prefs con
    /// prefisso huntix_veg_ e le rimanda a Unity nel JSON di setMode come
    /// vegetationFeatures=id=1,id=0. Il ripristino e' passivo (nessun uovo).
    /// </summary>
    public static class VegetationFeatureBridge
    {
        public const string PrefPrefix = "huntix_veg_";
        public const string AllPlace = "città→parco→vegetazione";
        /// <summary>Luogo testuale dei giardini OSM (leisure=garden): l'uovo
        /// resta nascosto fra cespugli e fiori, con coordinate GPS reali.</summary>
        public const string GardenPlace = "città→giardino→cespugli";

        /// <summary>Id stabili, allineati alle etichette e alle voci del menu.</summary>
        public static readonly string[] AllIds =
        {
            "prati-fioriti", "cespugli-rocce", "alberi-parco", "luci-naturali"
        };

        private static readonly string[] AllLabels =
        {
            "Prati e fiori", "Cespugli e rocce", "Alberi nei parchi", "Luci dei parchi"
        };

        private static readonly Dictionary<string, Action<bool>> Handlers =
            new Dictionary<string, Action<bool>>(StringComparer.OrdinalIgnoreCase);

        /// <summary>Uova una tantum per sessione: il ripristino da Android non
        /// ne genera, e il master switch non duplica quella del parco.</summary>
        private static readonly HashSet<string> GrantedEggs =
            new HashSet<string>(StringComparer.Ordinal);

        public static void Register(string id, Action<bool> handler)
        {
            if (string.IsNullOrEmpty(id) || handler == null) return;
            Handlers[id] = handler;
        }

        /// <summary>Le migliorie naturali sono attive di default: chi non ha
        /// ancora una preferenza Android vede il mondo gia' arricchito.</summary>
        public static bool Enabled(string id)
        {
            if (string.IsNullOrEmpty(id)) return false;
            return PlayerPrefs.GetInt(PrefPrefix + id, 1) == 1;
        }

        public static bool TryGetEnabled(string id, out bool on)
        {
            on = true;
            if (string.IsNullOrEmpty(id)) return false;
            string key = PrefPrefix + id;
            if (!PlayerPrefs.HasKey(key)) return false;
            on = PlayerPrefs.GetInt(key, 1) == 1;
            return true;
        }

        public static string LabelFor(string id)
        {
            for (int i = 0; i < AllIds.Length; i++)
                if (string.Equals(AllIds[i], id, StringComparison.OrdinalIgnoreCase))
                    return AllLabels[i];
            return "";
        }

        public static bool AllEnabled
        {
            get
            {
                for (int i = 0; i < AllIds.Length; i++)
                    if (!Enabled(AllIds[i])) return false;
                return true;
            }
        }

        public static void SetToggle(string id, bool on, string label, string place)
        {
            if (string.IsNullOrEmpty(id)) return;
            using (PerfProbe.Measure("menu"))
            {
                PlayerPrefs.SetInt(PrefPrefix + id, on ? 1 : 0);
                PlayerPrefs.Save();
                SendState(id, on, label, place, "toggle", true, "");
                Invoke(id, on);
            }
            if (on && Game.Instance != null)
            {
                string egg = "vegetazione->" + id;
                if (MarkEgg(egg))
                    Game.Instance.GrantAuditEgg(egg,
                        "Uovo tra il verde: " + (string.IsNullOrEmpty(label) ? LabelFor(id) : label),
                        string.IsNullOrEmpty(place) ? AllPlace : place);
            }
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("veg feature %s=%s", id, on ? "on" : "off");
        }

        public static void SetAll(bool on)
        {
            using (PerfProbe.Measure("menu"))
            {
                for (int i = 0; i < AllIds.Length; i++)
                {
                    string id = AllIds[i];
                    PlayerPrefs.SetInt(PrefPrefix + id, on ? 1 : 0);
                    SendState(id, on, LabelFor(id), AllPlace, "master", false, "");
                    Invoke(id, on);
                }
                PlayerPrefs.Save();
            }
            if (on && Game.Instance != null && MarkEgg("vegetazione->tutto"))
                Game.Instance.GrantAuditEgg("vegetazione->tutto",
                    "Uovo del mondo verde", AllPlace);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("veg features all=%s", on ? "on" : "off");
        }

        /// <summary>Riflesso uova di un parco visitato: posizione REALE nel
        /// verde (non accanto al player), con luogo e coordinate che viaggiano
        /// via FeatureEggHidden. Resta una sola uovo per sessione.</summary>
        public static void GrantParkEgg(string parkId, Vector3 at, string place)
        {
            // ID stabile e non prefissato: uno solo per l'intera sessione,
            // indipendentemente da quanti parchi vengano caricati.
            string key = "vegetazione->parco->fioriture";
            if (!MarkEgg(key)) return;
            if (Game.Instance == null) return;
            Game.Instance.GrantVegetationEgg(key, "Uovo tra cespugli e fiori",
                at, string.IsNullOrEmpty(place) ? AllPlace : place);
        }

        public static bool MarkEgg(string id)
        {
            if (string.IsNullOrEmpty(id)) return false;
            return GrantedEggs.Add(id);
        }

        private static void Invoke(string id, bool on)
        {
            Action<bool> handler;
            if (!Handlers.TryGetValue(id, out handler)) return;
            try { handler(on); }
            catch (Exception e) { OsmDiag.Log("[Vegetazione] " + id + ": " + e.Message); }
        }

        public static string Report(string id, string label, string place,
            string detail, bool ok)
        {
            SendState(id, ok, label, place, "audit", false, detail);
            return "Vegetazione " + id + ": " + (ok ? "attiva" : "inattiva") +
                   (string.IsNullOrEmpty(detail) ? "" : " — " + detail);
        }

        private static void SendState(string id, bool on, string label, string place,
            string reason, bool egg, string detail)
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var at = City.OSM.WorldOrigin.ToGeo(Vector3.zero);
                lat = at.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = at.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            string json = "{\"feature\":\"" + Escape(id) +
                "\",\"label\":\"" + Escape(label) +
                "\",\"place\":\"" + Escape(place) +
                "\",\"detail\":\"" + Escape(detail) +
                "\",\"on\":" + (on ? "true" : "false") +
                ",\"egg\":" + (egg && on ? "true" : "false") +
                ",\"reason\":\"" + Escape(reason) +
                "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
            try { UnityBridge.SendMessageToAndroid("VegetationFeatureChanged", json); }
            catch (Exception e) { OsmDiag.Log("[Vegetazione] bridge failed: " + e.Message); }
        }

        /// <summary>Ripristino passivo dallo stato Android (nessun uovo, nessun toast).</summary>
        public static void RestoreFromAndroid(string flags)
        {
            if (string.IsNullOrEmpty(flags)) return;
            string[] parts = flags.Split(',');
            for (int i = 0; i < parts.Length; i++)
            {
                string[] kv = parts[i].Split('=');
                if (kv.Length != 2) continue;
                bool on = kv[1] == "1" || kv[1] == "true";
                PlayerPrefs.SetInt(PrefPrefix + kv[0], on ? 1 : 0);
                Invoke(kv[0], on);
            }
            PlayerPrefs.Save();
        }

        private static string Escape(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
                .Replace("\n", " ").Replace("\r", " ");
        }
    }
}

// Ponte della modalita' grafica cinematografica. Non tocca il ponte
// dell'audit open source: pref, evento Android e chiave JSON sono dedicati.
// Nessuna modifica a valute, drop, Firestore o P2P.
using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Diagnostics
{
    /// <summary>
    /// Stato e bridge delle migliorie visive opt-in. Android e' proprietario
    /// delle preferenze: le salva in huntix_prefs con prefisso huntix_gfx_ e le
    /// rimanda a Unity nel JSON di setMode come cinematicFeatures=id=1,id=0.
    /// </summary>
    public static class CinematicFeatureBridge
    {
        public const string PrefPrefix = "huntix_gfx_";
        public const string AllPlace = "città→grafica cinematografica";

        /// <summary>Id stabili, allineati alle etichette e alle voci del menu.</summary>
        public static readonly string[] AllIds =
        {
            "asphalt-pbr", "pavement-pbr", "facade-pbr",
            "street-lamps", "night-atmosphere", "vehicle-detail",
            "raw-cc0-assets"
        };

        private static readonly string[] AllLabels =
        {
            "Asfalto PBR", "Marciapiedi PBR", "Facciate PBR",
            "Lampioni urbani", "Atmosfera notturna", "Dettagli veicoli",
            "Asset CC0 grezzi"
        };

        private static readonly Dictionary<string, Action<bool>> Handlers =
            new Dictionary<string, Action<bool>>(StringComparer.OrdinalIgnoreCase);

        public static void Register(string id, Action<bool> handler)
        {
            if (string.IsNullOrEmpty(id) || handler == null) return;
            Handlers[id] = handler;
        }

        public static bool Enabled(string id)
        {
            return !string.IsNullOrEmpty(id) && PlayerPrefs.GetInt(PrefPrefix + id, 0) == 1;
        }

        public static string LabelFor(string id)
        {
            for (int i = 0; i < AllIds.Length; i++)
                if (string.Equals(AllIds[i], id, StringComparison.OrdinalIgnoreCase))
                    return AllLabels[i];
            return "";
        }

        /// <summary>True solo quando tutte le opzioni grafiche sono attive.</summary>
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
                SendState(id, on, label, place, "toggle", true);
                Action<bool> handler;
                if (Handlers.TryGetValue(id, out handler))
                {
                    try { handler(on); }
                    catch (Exception e)
                    { City.OSM.OsmDiag.Log("[Cinematic] " + id + ": " + e.Message); }
                }
            }
            if (on && Game.Instance != null)
                Game.Instance.GrantAuditEgg("gfx-" + id,
                    "Uovo " + (string.IsNullOrEmpty(label) ? LabelFor(id) : label), place);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("gfx feature %s=%s", id, on ? "on" : "off");
        }

        /// <summary>Master switch del menu: commuta tutte le opzioni in una volta.</summary>
        public static void SetAll(bool on)
        {
            using (PerfProbe.Measure("menu"))
            {
                for (int i = 0; i < AllIds.Length; i++)
                {
                    string id = AllIds[i];
                    PlayerPrefs.SetInt(PrefPrefix + id, on ? 1 : 0);
                    SendState(id, on, LabelFor(id), AllPlace, "master", false);
                    Action<bool> handler;
                    if (Handlers.TryGetValue(id, out handler))
                    {
                        try { handler(on); }
                        catch (Exception e)
                        { City.OSM.OsmDiag.Log("[Cinematic] " + id + " master: " + e.Message); }
                    }
                }
                PlayerPrefs.Save();
            }
            if (on && Game.Instance != null)
                Game.Instance.GrantAuditEgg("gfx-all",
                    "Uovo della grafica cinematografica", AllPlace);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("gfx features all=%s", on ? "on" : "off");
        }

        public static string Report(string id, string label, string place,
            string detail, bool ok)
        {
            SendState(id, ok, label, place, "audit", false, detail);
            return "Grafica " + id + ": " + (ok ? "attiva" : "inattiva") +
                   (string.IsNullOrEmpty(detail) ? "" : " — " + detail);
        }

        private static void SendState(string id, bool on, string label, string place,
            string reason, bool egg, string detail = "")
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var at = City.OSM.WorldOrigin.ToGeo(Vector3.zero);
                lat = at.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = at.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            // Stesso canale FeatureEggHidden gia' whitelistato: Android registra
            // luogo e coordinate nel diario condiviso, senza versare premi.
            string json = "{\"feature\":\"" + Escape(id) +
                "\",\"label\":\"" + Escape(label) +
                "\",\"place\":\"" + Escape(place) +
                "\",\"detail\":\"" + Escape(detail) +
                "\",\"on\":" + (on ? "true" : "false") +
                ",\"egg\":" + (egg && on ? "true" : "false") +
                ",\"reason\":\"" + Escape(reason) +
                "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
            try { UnityBridge.SendMessageToAndroid("CinematicFeatureChanged", json); }
            catch (Exception e) { City.OSM.OsmDiag.Log("[Cinematic] bridge failed: " + e.Message); }
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
                Action<bool> handler;
                if (Handlers.TryGetValue(kv[0], out handler))
                {
                    try { handler(on); }
                    catch (Exception e)
                    { City.OSM.OsmDiag.Log("[Cinematic] restore " + kv[0] + ": " + e.Message); }
                }
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

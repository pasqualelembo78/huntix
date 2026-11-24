// Ponte condiviso delle migliorie acquisite nell'audit dei progetti Unity
// open source (24/11/2026). Non copia codice dei repository analizzati: e'
// infrastruttura Huntix per transport, preferenze, telemetria e riflesso uova.
using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Diagnostics
{
    /// <summary>
    /// Canale unico per le opzioni addittive dell'audit. Ogni miglioria ha un
    /// id stabile: lo stato viaggia Unity -> Android (OpenSourceFeatureChanged)
    /// e torna Android -> Unity nel JSON di setMode (openSourceFeatures).
    /// L'attivazione lascia un uovo posizionale nel solo diario, senza premi:
    /// la cattura canonica resta EggCapturedInCity.
    /// </summary>
    public static class OpenSourceFeatureBridge
    {
        public const string PrefPrefix = "huntix_os_";
        public const string AllPlace = "città→audit open source";

        /// <summary>Ids delle opzioni governate dall'interruttore unico del menu.
        /// Ordine stabile, coerente con le etichette qui sotto e i comandi QA.</summary>
        public static readonly string[] AllIds =
        {
            "egg-cull", "door-latch", "egg-compass", "egg-journal",
            "camera-radius", "npc-persona", "quest-def"
        };

        private static readonly string[] AllLabels =
        {
            "Culling uova con grazia", "Chiusura porta anti-doppio",
            "Bussola uova a bassa velocità", "Diario locale uova",
            "Raggio camera dal frustum", "Personaggi NPC offline",
            "Definizioni missioni"
        };

        private static readonly Dictionary<string, Action<bool>> Handlers =
            new Dictionary<string, Action<bool>>(StringComparer.OrdinalIgnoreCase);

        /// <summary>Registra un ricevitore per il ripristino da Android.</summary>
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

        /// <summary>True solo quando tutte le opzioni dell'audit sono attive.</summary>
        public static bool AllEnabled
        {
            get
            {
                for (int i = 0; i < AllIds.Length; i++)
                    if (!Enabled(AllIds[i])) return false;
                return true;
            }
        }

        /// <summary>
        /// Commuta un'opzione: pref locale, notifica Android, telemetria e
        /// (solo all'attivazione) uovo con luogo e coordinate.
        /// </summary>
        public static void SetToggle(string id, bool on, string label, string place)
        {
            if (string.IsNullOrEmpty(id)) return;
            PlayerPrefs.SetInt(PrefPrefix + id, on ? 1 : 0);
            PlayerPrefs.Save();
            using (PerfProbe.Measure("menu"))
                SendState(id, on, label, place, "toggle", true);
            Action<bool> handler;
            if (Handlers.TryGetValue(id, out handler))
            {
                try { handler(on); }
                catch (Exception e) { City.OSM.OsmDiag.Log("[OpenSource] " + id + " handler: " + e.Message); }
            }
            if (on && Game.Instance != null)
                Game.Instance.GrantAuditEgg(id, "Uovo " + label, place);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("os feature %s=%s", id, on ? "on" : "off");
        }

        /// <summary>
        /// Interruttore unico del menu: commuta tutte le opzioni in una volta.
        /// Android riceve ogni pref, ma non fa sette indizi; Huntix lascia un
        /// solo uovo-indizio per l'attivazione del blocco. Disattivare non
        /// genera alcun uovo. I comandi QA restano disponibili per il singolo.
        /// </summary>
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
                        { City.OSM.OsmDiag.Log("[OpenSource] " + id + " master: " + e.Message); }
                    }
                }
                PlayerPrefs.Save();
            }
            if (on && Game.Instance != null)
                Game.Instance.GrantAuditEgg("os-all", "Uovo dell'audit open source", AllPlace);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("os features all=%s", on ? "on" : "off");
        }

        /// <summary>Esito di un audit: nessun toggle, solo report + indizio uovo.</summary>
        public static void Report(string id, string label, string place, string detail, bool ok)
        {
            using (PerfProbe.Measure("menu"))
                SendState(id, ok, label, place, detail, ok);
            if (ok && Game.Instance != null)
                Game.Instance.GrantAuditEgg(id, "Uovo " + label, place);
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("os audit %s=%s", id, ok ? "pass" : "fail");
        }

        private static void SendState(string id, bool on, string label, string place,
                                      string detail, bool egg)
        {
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var at = City.OSM.WorldOrigin.ToGeo(Vector3.zero);
                lat = at.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = at.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            // FeatureEggHidden e' il canale gia' whitelistato: Android registra
            // feature|lat|lng|ts nel diario condiviso, senza versare premi.
            string json = "{\"feature\":\"" + Escape(id) +
                "\",\"label\":\"" + Escape(label) +
                "\",\"place\":\"" + Escape(place) +
                "\",\"detail\":\"" + Escape(detail) +
                "\",\"on\":" + (on ? "true" : "false") +
                ",\"egg\":" + (egg ? "true" : "false") +
                ",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
            try { UnityBridge.SendMessageToAndroid("OpenSourceFeatureChanged", json); }
            catch (Exception e) { City.OSM.OsmDiag.Log("[OpenSource] bridge failed: " + e.Message); }
        }

        /// <summary>
        /// Ripristino passivo dal JSON di setMode: "egg-cull=1,egg-compass=0".
        /// Nessun uovo e nessuna notifica: e' solo il ripristino di Android.
        /// </summary>
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
                    catch (Exception e) { City.OSM.OsmDiag.Log("[OpenSource] restore " + kv[0] + ": " + e.Message); }
                }
            }
        }

        private static string Escape(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"")
                .Replace("\n", " ").Replace("\r", " ");
        }
    }
}

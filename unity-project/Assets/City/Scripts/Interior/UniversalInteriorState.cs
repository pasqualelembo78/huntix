using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Stato persistente degli interni universali: quali locali sono stati
    /// visitati e quante volte. Nessuna valuta e nessuna rarita' (il Wallet
    /// resta l'unico sistema economico, le uova restano in ContainerEgg).
    /// Viaggia nello snapshot CityWorldSync, quindi Android lo conserva e lo
    /// restituisce con merge non distruttivo.
    /// </summary>
    public static class UniversalInteriorState
    {
        public const string BridgeEvent = "UniversalInteriorVisited";

        [Serializable]
        public sealed class Record
        {
            public string key = "";
            public string kind = "";
            public int visits;
            public long lastTs;
        }

        [Serializable]
        private sealed class Store
        {
            public List<Record> interiors = new List<Record>();
        }

        private const string PrefKey = "huntix_universal_interiors_v1";

        private static Store _store;

        private static Store Data
        {
            get
            {
                if (_store == null) _store = Load();
                return _store;
            }
        }

        private static Store Load()
        {
            Store s = null;
            try
            {
                string json = PlayerPrefs.GetString(PrefKey, "");
                if (!string.IsNullOrEmpty(json)) s = JsonUtility.FromJson<Store>(json);
            }
            catch (Exception) { }
            if (s == null || s.interiors == null) s = new Store();
            return s;
        }

        private static void Save()
        {
            try
            {
                PlayerPrefs.SetString(PrefKey, JsonUtility.ToJson(Data));
                PlayerPrefs.Save();
            }
            catch (Exception) { }
        }

        public static string Export()
        {
            string json = JsonUtility.ToJson(Data);
            return string.IsNullOrEmpty(json) ? "{\"interiors\":[]}" : json;
        }

        /// <summary>Unione non distruttiva: per chiave il conteggio piu' alto
        /// vince, esattamente come ShopState per i negozi.</summary>
        public static void Import(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            Store incoming;
            try { incoming = JsonUtility.FromJson<Store>(json); }
            catch (Exception) { return; }
            if (incoming == null || incoming.interiors == null) return;

            bool changed = false;
            for (int i = 0; i < incoming.interiors.Count; i++)
            {
                Record r = incoming.interiors[i];
                if (r == null || string.IsNullOrEmpty(r.key)) continue;
                Record local = Find(r.key);
                if (local == null)
                {
                    Data.interiors.Add(r);
                    changed = true;
                    continue;
                }
                if (r.visits > local.visits) { local.visits = r.visits; changed = true; }
                if (r.lastTs > local.lastTs) { local.lastTs = r.lastTs; changed = true; }
                if (string.IsNullOrEmpty(local.kind) && !string.IsNullOrEmpty(r.kind))
                {
                    local.kind = r.kind;
                    changed = true;
                }
            }
            if (changed) Save();
        }

        private static Record Find(string key)
        {
            for (int i = 0; i < Data.interiors.Count; i++)
                if (Data.interiors[i] != null && Data.interiors[i].key == key)
                    return Data.interiors[i];
            return null;
        }

        /// <summary>Un locale visitato: aggiorna il contatore e notifica Android
        /// con un evento dedicato (oltre al successivo snapshot periodico).</summary>
        public static void MarkVisited(string key, string kind)
        {
            if (string.IsNullOrEmpty(key)) return;
            Record r = Find(key);
            if (r == null)
            {
                r = new Record();
                r.key = key;
                r.kind = kind;
                Data.interiors.Add(r);
            }
            r.visits++;
            r.lastTs = DateTime.UtcNow.Ticks;
            if (string.IsNullOrEmpty(r.kind)) r.kind = kind;
            Save();
            try
            {
                var payload = new VisitedEvent();
                payload.key = key;
                payload.kind = kind ?? "";
                payload.visits = r.visits;
                UnityBridge.SendMessageToAndroid(BridgeEvent, JsonUtility.ToJson(payload));
            }
            catch (Exception) { }
        }

        [Serializable]
        private sealed class VisitedEvent
        {
            public string key = "";
            public string kind = "";
            public int visits;
        }

        public static int Count { get { return Data.interiors.Count; } }

        public static int Visits(string key)
        {
            Record r = Find(key);
            return r == null ? 0 : r.visits;
        }
    }
}

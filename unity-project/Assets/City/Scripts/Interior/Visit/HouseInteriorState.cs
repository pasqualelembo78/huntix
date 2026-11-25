using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior.Visit
{
    /// <summary>
    /// Stato persistente delle case visitabili: quante volte sono state aperte
    /// e quali uova-feature sono già state raccolte al loro interno.
    ///
    /// Viaggia nello snapshot CityWorldSync: Android conserva il mirror e lo
    /// restituisce al restore con merge non distruttivo (il massimo vince).
    /// Nessuna valuta: le uova restano quelle del normale EggController.
    /// </summary>
    public static class HouseInteriorState
    {
        public const string BridgeEvent = "HouseVisitChanged";

        [Serializable]
        public sealed class Record
        {
            public string key = "";
            public long osmId;
            public string kind = "";
            public string label = "";
            public int visits;
            public long lastTs;
        }

        [Serializable]
        private sealed class Store
        {
            public List<Record> houses = new List<Record>();
            public List<string> eggs = new List<string>();
        }

        private const string PrefKey = "huntix_visit_houses_v1";

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
            if (s == null) s = new Store();
            if (s.houses == null) s.houses = new List<Record>();
            if (s.eggs == null) s.eggs = new List<string>();
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
            return string.IsNullOrEmpty(json) ? "{\"houses\":[],\"eggs\":[]}" : json;
        }

        /// <summary>Unione non distruttiva: per chiave il conteggio e il
        /// timestamp più alti vincono; le uova note si uniscono senza duplicati.
        /// </summary>
        public static void Import(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            Store incoming;
            try { incoming = JsonUtility.FromJson<Store>(json); }
            catch (Exception) { return; }
            if (incoming == null) return;

            bool changed = false;
            if (incoming.houses != null)
            {
                for (int i = 0; i < incoming.houses.Count; i++)
                {
                    Record r = incoming.houses[i];
                    if (r == null || string.IsNullOrEmpty(r.key)) continue;
                    Record local = Find(r.key);
                    if (local == null)
                    {
                        Data.houses.Add(r);
                        changed = true;
                        continue;
                    }
                    if (r.visits > local.visits) { local.visits = r.visits; changed = true; }
                    if (r.lastTs > local.lastTs) { local.lastTs = r.lastTs; changed = true; }
                    if (local.osmId == 0 && r.osmId != 0) { local.osmId = r.osmId; changed = true; }
                    if (string.IsNullOrEmpty(local.label) && !string.IsNullOrEmpty(r.label))
                    { local.label = r.label; changed = true; }
                }
            }
            if (incoming.eggs != null)
            {
                for (int i = 0; i < incoming.eggs.Count; i++)
                    if (MarkEgg(incoming.eggs[i])) changed = true;
            }
            if (changed) Save();
        }

        private static Record Find(string key)
        {
            for (int i = 0; i < Data.houses.Count; i++)
                if (Data.houses[i] != null && Data.houses[i].key == key)
                    return Data.houses[i];
            return null;
        }

        /// <summary>Registra l'apertura della casa e invia l'evento bridge con
        /// id OSM, chiave stabile, contatore e timestamp (mirror Android).</summary>
        public static void MarkVisited(long osmId, string kind, string label)
        {
            string key = VisitInteriorRegistry.StableKey(osmId);
            Record r = Find(key);
            if (r == null)
            {
                r = new Record();
                r.key = key;
                r.osmId = osmId;
                Data.houses.Add(r);
            }
            r.visits++;
            r.lastTs = DateTime.UtcNow.Ticks;
            if (string.IsNullOrEmpty(r.kind)) r.kind = kind;
            if (string.IsNullOrEmpty(r.label)) r.label = label;
            Save();
            try
            {
                var payload = new VisitEvent();
                payload.key = key;
                payload.osmId = osmId;
                payload.kind = kind ?? "";
                payload.label = label ?? "";
                payload.visits = r.visits;
                payload.lastTs = r.lastTs;
                UnityBridge.SendMessageToAndroid(BridgeEvent, JsonUtility.ToJson(payload));
            }
            catch (Exception) { }
        }

        /// <summary>True se l'uovo-feature di questa casa è già stato assegnato.
        /// Evita duplicati dopo rebuild del chunk o restore dello snapshot.</summary>
        public static bool HasEgg(string featureId)
        {
            if (string.IsNullOrEmpty(featureId)) return false;
            for (int i = 0; i < Data.eggs.Count; i++)
                if (Data.eggs[i] == featureId) return true;
            return false;
        }

        public static bool MarkEgg(string featureId)
        {
            if (string.IsNullOrEmpty(featureId) || HasEgg(featureId)) return false;
            Data.eggs.Add(featureId);
            Save();
            return true;
        }

        public static int Visits(long osmId)
        {
            Record r = Find(VisitInteriorRegistry.StableKey(osmId));
            return r == null ? 0 : r.visits;
        }

        [Serializable]
        private sealed class VisitEvent
        {
            public string key = "";
            public long osmId;
            public string kind = "";
            public string label = "";
            public int visits;
            public long lastTs;
        }
    }
}

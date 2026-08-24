using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace City.NPC
{
    /// <summary>
    /// Amicizia locale con i personaggi RealLife incontrati in citta'.
    /// Punti: +1 ogni chiacchierata (max 3 al giorno per personaggio),
    /// +3 per ogni missione del personaggio completata.
    /// Livelli: Straniero, Conoscente, Amico, Grande amico, Migliore amico.
    /// A livello "Amico"+ le missioni pagano il prezzo amico (+10%).
    /// Persistito su JSON in persistentDataPath.
    /// </summary>
    public static class RelationshipManager
    {
        public const int ChatPts = 1;
        public const int MissionPts = 3;
        public const int ChatCapPerDay = 3;
        public const int FriendLevelForPerk = 2;

        private static readonly int[] Mins = { 0, 5, 15, 40, 80 };
        private static readonly string[] Labels =
            { "Straniero", "Conoscente", "Amico", "Grande amico", "Migliore amico" };

        [Serializable] private class Store { public List<Entry> entries = new List<Entry>(); }
        [Serializable] private class Entry
        {
            public string id;
            public int pts;
            public string day;
            public int chatToday;
        }

        private static Store _store;
        private static readonly Dictionary<string, Entry> _byId =
            new Dictionary<string, Entry>();

        public static event Action<string> OnChanged;

        private static string FilePath =>
            Path.Combine(Application.persistentDataPath, "huntix_city_relationships.json");

        // ── API ──────────────────────────────────────────────────

        public static void AddChat(string characterId)
        {
            if (string.IsNullOrEmpty(characterId)) return;
            Entry e = Get(characterId);
            string today = DateTime.UtcNow.ToString("yyyy-MM-dd");
            if (e.day != today) { e.day = today; e.chatToday = 0; }
            if (e.chatToday >= ChatCapPerDay) return;
            e.chatToday++;
            Mutate(e, ChatPts);
        }

        /// <summary>Toglie punti (es. pedone travolto): mai sotto zero.</summary>
        public static void RemovePoints(string characterId, int pts)
        {
            if (string.IsNullOrEmpty(characterId)) return;
            EnsureLoaded();
            Entry e;
            if (!_byId.TryGetValue(characterId, out e)) return;
            int delta = pts < 0 ? -pts : pts;
            if (delta <= 0) return;
            if (e.pts <= 0) return;
            e.pts = Mathf.Max(0, e.pts - delta);
            Save();
            var h = OnChanged;
            if (h != null) h(characterId);
        }

        public static void AddMissionComplete(string characterId)
        {
            if (string.IsNullOrEmpty(characterId)) return;
            Mutate(Get(characterId), MissionPts);
        }

        public static int Points(string characterId)
        {
            if (string.IsNullOrEmpty(characterId)) return 0;
            Entry e;
            return _byId.TryGetValue(characterId, out e) ? e.pts : 0;
        }

        /// <summary>0..4 secondo la soglia raggiunta.</summary>
        public static int LevelIndex(string characterId)
        {
            int p = Points(characterId);
            int lvl = 0;
            for (int i = 0; i < Mins.Length; i++)
                if (p >= Mins[i]) lvl = i;
            return lvl;
        }

        public static string LevelLabel(int index)
        {
            return (index >= 0 && index < Labels.Length) ? Labels[index] : Labels[0];
        }

        // ── interno ──────────────────────────────────────────────

        private static Entry Get(string id)
        {
            EnsureLoaded();
            Entry e;
            if (!_byId.TryGetValue(id, out e))
            {
                e = new Entry { id = id, pts = 0, day = "", chatToday = 0 };
                _byId[id] = e;
                _store.entries.Add(e);
            }
            return e;
        }

        private static void Mutate(Entry e, int delta)
        {
            e.pts += delta;
            Save();
            var h = OnChanged;
            if (h != null) h(e.id);
        }

        private static void EnsureLoaded()
        {
            if (_store != null) return;
            try
            {
                if (File.Exists(FilePath))
                    _store = JsonUtility.FromJson<Store>(File.ReadAllText(FilePath));
            }
            catch (Exception) { }
            if (_store == null) _store = new Store();
            if (_store.entries == null) _store.entries = new List<Entry>();
            foreach (var e in _store.entries)
                if (e != null && !string.IsNullOrEmpty(e.id))
                    _byId[e.id] = e;
        }

        private static void Save()
        {
            try
            {
                File.WriteAllText(FilePath, JsonUtility.ToJson(_store));
            }
            catch (Exception e)
            {
                Debug.LogWarning("[Relations] save: " + e.Message);
            }
        }
    }
}

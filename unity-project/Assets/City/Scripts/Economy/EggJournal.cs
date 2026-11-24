// Adattato da 3ddriving — codice sorgente SENZA licenza: solo il pattern
// generale (scrittura .tmp -> backup -> main e validazione) e' stato riusato,
// riscritto da zero. Nessuna riga del repository e' stata copiata.
using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// Cache locale SOLO LETTURA/SCRITTURA DEL DIARIO UOVA, non autoritativa:
    /// Android resta la fonte di verita'. Opt-in (default OFF), scrive solo su
    /// eventi uovo espliciti, mai per frame. Nessun premio, nessuna valuta.
    /// </summary>
    public static class EggJournal
    {
        public const string Id = "egg-journal";
        public const int Version = 1;
        public const int MaxEntries = 200;
        private const string FileName = "egg-journal.json";
        private const string BackupName = "egg-journal.backup.json";
        private const string TempName = "egg-journal.json.tmp";

        [Serializable]
        public struct Entry
        {
            public string eggId;
            public string lat;
            public string lng;
            public string rarity;
            public long capturedAt;
        }

        [Serializable]
        private class Journal
        {
            public int version;
            public List<Entry> entries = new List<Entry>();
        }

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled(Id);
            City.Diagnostics.OpenSourceFeatureBridge.Register(Id, on => Enabled = on);
        }

        public static string MainPath { get { return Path.Combine(Application.persistentDataPath, FileName); } }

        /// <summary>Sanitize: deduplica per eggId, clamp lat/lng, scarta
        /// timestamp futuri e limita il numero di voci.</summary>
        public static JournalData Sanitize(JournalData input)
        {
            var result = new JournalData();
            long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            for (int i = 0; i < input.entries.Count && result.entries.Count < MaxEntries; i++)
            {
                Entry e = input.entries[i];
                if (string.IsNullOrEmpty(e.eggId) || !seen.Add(e.eggId)) continue;
                double lat, lng;
                if (!double.TryParse(e.lat, out lat) || !double.TryParse(e.lng, out lng)) continue;
                e.lat = Math.Max(-90d, Math.Min(90d, lat)).ToString("F6");
                e.lng = Math.Max(-180d, Math.Min(180d, lng)).ToString("F6");
                if (e.capturedAt <= 0 || e.capturedAt > now) e.capturedAt = now;
                result.entries.Add(e);
            }
            return result;
        }

        /// <summary>Struttura pubblica usata per leggere/scrivere il JSON.</summary>
        public class JournalData
        {
            public int version = Version;
            public List<Entry> entries = new List<Entry>();
        }

        public static void Record(string eggId, string lat, string lng,
                                  string rarity, long capturedAt)
        {
            if (!Enabled || string.IsNullOrEmpty(eggId)) return;
            using (City.Diagnostics.PerfProbe.Measure("diary"))
            {
                try
                {
                    JournalData data = Load();
                    Entry entry = new Entry
                    {
                        eggId = eggId, lat = lat, lng = lng,
                        rarity = rarity ?? "", capturedAt = capturedAt
                    };
                    data.entries.Add(entry);
                    data = Sanitize(data);
                    string dir = Application.persistentDataPath;
                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                    string main = MainPath;
                    string backup = Path.Combine(dir, BackupName);
                    string tmp = Path.Combine(dir, TempName);
                    File.WriteAllText(tmp, JsonUtility.ToJson(data));
                    if (File.Exists(main)) File.Copy(main, backup, true);
                    File.Copy(tmp, main, true);
                    File.Delete(tmp);
                    if (City.Diagnostics.SessionTelemetry.Instance != null)
                        City.Diagnostics.SessionTelemetry.Instance.Mark("Diario uova locale: {0} voci",
                            data.entries.Count);
                }
                catch (Exception e)
                {
                    City.OSM.OsmDiag.Log("[EggJournal] scrittura fallita: " + e.Message);
                }
            }
        }

        public static JournalData Load()
        {
            var empty = new JournalData();
            try
            {
                string main = MainPath;
                string backup = Path.Combine(Application.persistentDataPath, BackupName);
                string text = File.Exists(main) ? File.ReadAllText(main)
                    : (File.Exists(backup) ? File.ReadAllText(backup) : "");
                if (string.IsNullOrEmpty(text)) return empty;
                Journal parsed = JsonUtility.FromJson<Journal>(text);
                if (parsed == null || parsed.version != Version || parsed.entries == null)
                    return empty;
                var data = new JournalData { version = parsed.version, entries = parsed.entries };
                return Sanitize(data);
            }
            catch (Exception)
            {
                return empty;
            }
        }
    }
}

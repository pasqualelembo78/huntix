using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>Un settore del mondo: una tile OSM (10x10 chunk da 1 km).</summary>
    [Serializable]
    public class WorldSectorInfo
    {
        public string key;
        public double lat;
        public double lng;
        public double[] bbox;
        public int buildings;
        public long bytes;
    }

    [Serializable]
    public class WorldSectorCatalogData
    {
        public List<WorldSectorInfo> sectors = new List<WorldSectorInfo>();
    }

    /// <summary>
    /// Catalogo dei settori disponibili, generato da
    /// backend/preprocessing/world_sector_catalog.py in Assets/Resources.
    /// Non contiene cartografia: solo key, centro, bbox e numero edifici.
    /// Serve per ampliare la citta' senza toccare il codice: nuove tile nel
    /// catalogo = nuovi quartieri/skyline riconosciuti dallo streaming.
    /// </summary>
    public static class WorldStreamingCatalog
    {
        private const string ResourceName = "world_sectors";
        private static WorldSectorCatalogData _data;
        private static readonly Dictionary<string, WorldSectorInfo> ByKey =
            new Dictionary<string, WorldSectorInfo>();

        public static int Count
        {
            get { Ensure(); return ByKey.Count; }
        }

        private static void Ensure()
        {
            if (_data != null) return;
            _data = new WorldSectorCatalogData();
            try
            {
                var asset = Resources.Load<TextAsset>(ResourceName);
                if (asset == null || string.IsNullOrEmpty(asset.text)) return;
                var parsed = JsonUtility.FromJson<WorldSectorCatalogData>(asset.text);
                if (parsed == null || parsed.sectors == null) return;
                foreach (var s in parsed.sectors)
                {
                    if (s == null || string.IsNullOrEmpty(s.key) || ByKey.ContainsKey(s.key)) continue;
                    ByKey.Add(s.key, s);
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] catalogo settori non leggibile: " + e.Message);
            }
        }

        public static bool TryGet(string key, out WorldSectorInfo info)
        {
            Ensure();
            return ByKey.TryGetValue(key, out info);
        }

        public static IEnumerable<WorldSectorInfo> All
        {
            get { Ensure(); return ByKey.Values; }
        }
    }
}

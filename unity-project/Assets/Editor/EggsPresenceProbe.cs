using System;
using System.IO;
using System.Text;
using UnityEditor;
using UnityEngine;
using City.Economy;
using City.OSM;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Verifica presenza + FISICA della uova con DEM reale: carica la geo
    /// reale di Roma (con griglia 'ele'), la registra in TileElevation e
    /// spara SpawnEggsInChunk su un chunk reale della griglia CityGrid.
    /// Controlla che ogni uovo stia sopra il terreno reale (eggY >= DEM +
    /// soglia bob) e che non ci siano uova sprofondate.
    /// Uso: -executeMethod Huntix.EditorTools.EggsPresenceProbe.Run
    /// </summary>
    public static class EggsPresenceProbe
    {
        public static void Run()
        {
            var sb = new StringBuilder();
            sb.AppendLine("== EGGS PRESENCE + PHYSICS PROBE (edit-mode, geo reale) ==");

            string geoPath = "/tmp/rome_geo.json";
            if (!File.Exists(geoPath))
            {
                sb.AppendLine("MISSING geo file " + geoPath + " -> prima scaricare la geo dal server tiles.");
                Finish(sb);
                return;
            }
            var geo = JsonUtility.FromJson<TileGeoDoc>(File.ReadAllText(geoPath));
            sb.AppendLine("geo=" + geo.tile + " bbox=[" + string.Join(",", geo.bbox) +
                "] roads=" + geo.roads.Length + " ele=" + (geo.ele != null ? geo.ele.Length : 0));

            TileElevation.Reset();
            bool registered = TileElevation.Register(geo);
            sb.AppendLine("TileElevation.Register -> " + registered);

            // chunk CityGrid che contiene Roma (41.9028, 12.4964)
            Vector2Int chunk = new Vector2Int(878, 619);
            // replica esatta di ChunkBuilder: root al centro chunk e ToLocal
            WorldOrigin.Init(41.9028, 12.4964);
            GeoCoord center = CityGrid.ChunkCenter(chunk);
            GameObject rootGo = new GameObject("__EggsProbeRoot__");
            rootGo.transform.position = WorldOrigin.ToWorld(center.lat, center.lng);
            Vector3 originWorld = rootGo.transform.position;
            Func<GeoLL, Vector3> toLocal = ll =>
            {
                var w = WorldOrigin.ToWorld(ll.a, ll.o);
                return new Vector3(w.x - originWorld.x, w.y, w.z - originWorld.z);
            };
            Rect bounds = CityGrid.ChunkLocalBounds();

            var esm = rootGo.AddComponent<EggSpawnManager>();
            esm.SpawnEggsInChunk(rootGo.transform, geo, toLocal, bounds,
                unchecked(chunk.x * 73856093 ^ chunk.y * 19349663));

            var eggsList = new System.Collections.Generic.List<EggController>();
            foreach (var e in UnityEngine.Object.FindObjectsOfType<EggController>(true))
            {
                if (e == null || e.gameObject == null) continue;
                if (!e.gameObject.transform.IsChildOf(rootGo.transform)) continue;
                eggsList.Add(e);
            }
            sb.AppendLine("uova create: " + eggsList.Count + "  (attese 8..30)");

            int belowTerrain = 0;      // eggY < dem -> sprofondata
            int floatingHigh = 0;      // eggY - dem > 0.35 (troppo alta? accettabile)
            int sinked = 0;            // eggY - dem < 0.05 (a contatto col suolo? ok)
            float minGap = float.MaxValue, maxGap = float.MinValue;
            int tooClose = 0;
            float minDist = float.MaxValue;
            var positions = new System.Collections.Generic.List<Vector3>();
            foreach (var e in eggsList)
            {
                Vector3 p = e.transform.position;
                positions.Add(p);
                float dem = TileElevation.HeightAtWorld(p);
                float gap = p.y - dem;
                if (gap < minGap) minGap = gap;
                if (gap > maxGap) maxGap = gap;
                if (gap < 0f) belowTerrain++;
                else if (gap < 0.05f) sinked++;
                if (gap > 0.35f) floatingHigh++;
                sb.AppendLine(string.Format(
                    "  uovo {0} rarity={1} type={2} pos=({3:F1},{4:F1},{5:F1}) dem={6:F1} gap={7:F2}",
                    e.gameObject.name, e.rarity, e.eggType, p.x, p.y, p.z, dem, gap));
            }
            for (int i = 0; i < positions.Count; i++)
                for (int j = i + 1; j < positions.Count; j++)
                {
                    float d = Vector3.Distance(positions[i], positions[j]);
                    if (d < minDist) minDist = d;
                    if (d < 8f) tooClose++;
                }
            sb.AppendLine(string.Format(
                "uova sotto terreno (gap<0): {0}; quasi a contatto (gap<5cm): {1}; oltre +0.35m: {2}; " +
                "gap min={3:F2}m max={4:F2}m; coppie vicine(<8m): {5}; distanza min: {6:F1}m",
                belowTerrain, sinked, floatingHigh, minGap, maxGap, tooClose, minDist));

            bool ok = eggsList.Count >= 8 && eggsList.Count <= 30 &&
                      belowTerrain == 0 && tooClose == 0 &&
                      minGap >= 0.05f;
            // bob di EggController.Update: ±0.25m attorno a startPos.y che è
            // 0.3 sopra il DEM -> l'uovo resta SEMPRE ≥ DEM+0.05 (mai sotto).
            float bobLow = minGap - 0.25f;
            if (bobLow < 0f) ok = false;
            sb.AppendLine("min gap - bob 0.25m -> quota minima uovo sopra DEM: " + bobLow.ToString("F2") + "m (deve essere >=0)");
            sb.AppendLine("RISULTATO: " + (ok ? "ESITO OK" : "ESITO ANOMALO"));
            Finish(sb);
        }

        private static void Finish(StringBuilder sb)
        {
            Debug.Log("[EggsProbe]\n" + sb);
            string path = "/root/giochi/huntix/unitylic/probe_eggs.log";
            File.WriteAllText(path, sb.ToString());
            EditorApplication.Exit(0);
        }
    }
}
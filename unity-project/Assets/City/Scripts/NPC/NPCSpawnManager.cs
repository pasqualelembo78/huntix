using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.NPC
{
    public class NPCSpawnManager : MonoBehaviour
    {
        public static NPCSpawnManager Instance;

        private const int MAX_NPCS = 40;
        private const float MIN_ROAD_LEN = 20f;
        private const float DESPAWN_DIST = 200f;

        private readonly List<GameObject> npcs = new List<GameObject>();
        private Transform player;
        private Vector3 lastCenter;
        private bool spawned;

        private void Awake()
        {
            Instance = this;
        }

        public void SpawnNPCs(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            player = City.Game.Instance != null ? City.Game.Instance.player.transform : null;

            var paths = BuildNPCPaths(env);
            if (paths.Count == 0) return;

            var rng = new System.Random(123);
            int count = Mathf.Min(MAX_NPCS, paths.Count);

            for (int i = 0; i < count; i++)
            {
                int idx = rng.Next(paths.Count);
                var path = paths[idx];

                var go = new GameObject("NPC_" + i);
                go.transform.SetParent(root, false);
                go.tag = "Untagged";

                var npc = go.AddComponent<NPCController>();
                npc.Init(path.ToArray(), rng);

                npcs.Add(go);
            }

            spawned = true;
            lastCenter = root.position;
            UnityEngine.Debug.Log("[NPCSpawnManager] NPC creati: " + npcs.Count);
        }

        private List<List<Vector3>> BuildNPCPaths(OsmCityEnvelope env)
        {
            var paths = new List<List<Vector3>>();
            var rng = new System.Random(77);

            foreach (var road in env.roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                string hw = road.highway ?? "";
                if (hw == "motorway" || hw == "trunk") continue;

                float roadLen = 0f;
                var pts = new List<Vector3>();
                foreach (var p in road.points)
                {
                    pts.Add(Local(p));
                }
                for (int i = 0; i < pts.Count - 1; i++)
                    roadLen += (pts[i + 1] - pts[i]).magnitude;

                if (roadLen < MIN_ROAD_LEN) continue;

                // Una o due path per strada lunga
                int pathCount = roadLen > 60 ? 2 : 1;
                for (int k = 0; k < pathCount; k++)
                {
                    var path = new List<Vector3>();
                    // Aggiungi punti con offset marciapiede
                    Vector3 right = Vector3.zero;
                    float side = (rng.Next(2) == 0) ? 1f : -1f;
                    for (int i = 0; i < pts.Count; i++)
                    {
                        if (i < pts.Count - 1)
                        {
                            Vector3 dir = (pts[i + 1] - pts[i]).normalized;
                            right = Vector3.Cross(Vector3.up, dir).normalized;
                        }
                        float offset = 2.5f * side;
                        // Alterna lato ogni tanto
                        if (rng.NextDouble() < 0.15) side = -side;
                        path.Add(pts[i] + right * offset);
                    }
                    paths.Add(path);
                }
            }
            return paths;
        }

        private void Update()
        {
            if (!spawned || player == null) return;

            // Respawn quando il player si muove troppo
            float moved = Vector3.Distance(transform.position, lastCenter);
            if (moved > DESPAWN_DIST)
            {
                lastCenter = transform.position;
                // Non rifare tutto, solo gestisci distanza
            }

            // Disattiva NPC lontani per performance
            Vector3 pp = player.position;
            for (int i = 0; i < npcs.Count; i++)
            {
                if (npcs[i] == null) continue;
                float d = Vector3.Distance(npcs[i].transform.position, pp);
                npcs[i].SetActive(d < 80f);
            }
        }

        private static Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        public void DespawnAll()
        {
            foreach (var go in npcs)
                if (go != null) Destroy(go);
            npcs.Clear();
            spawned = false;
        }
    }
}

using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using Huntix.Bridge;

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

        private GameObject _charPrefab;

        private void Awake()
        {
            Instance = this;
        }

        private void LoadCharacterPrefab()
        {
            if (_charPrefab != null) return;
            _charPrefab = Resources.Load<GameObject>("Characters/characterMedium");
            if (_charPrefab == null)
            {
                UnityBridge.LogToAndroid("NPCSpawn", "characterMedium not found in Resources/Characters/");
                return;
            }
            UnityBridge.LogToAndroid("NPCSpawn", "Kenney character loaded");
        }

        public void SpawnNPCs(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            LoadCharacterPrefab();
            player = City.Game.Instance != null ? City.Game.Instance.player.transform : null;

            var paths = BuildNPCPaths(env);
            if (paths.Count == 0) return;

            var rng = new System.Random(123);
            int count = Mathf.Min(MAX_NPCS, paths.Count);

            string[] skinNames = { "humanMaleA", "humanFemaleA", "humanMaleA", "humanFemaleA" };

            for (int i = 0; i < count; i++)
            {
                int idx = rng.Next(paths.Count);
                var path = paths[idx];

                GameObject go;
                if (_charPrefab != null)
                {
                    go = Instantiate(_charPrefab, root);
                    go.name = "NPC_" + i;
                    go.tag = "Untagged";
                    go.transform.localPosition = path[0];
                    float s = 0.455f;
                    go.transform.localScale = Vector3.one * s;
                    foreach (var c in go.GetComponentsInChildren<Collider>())
                        if (c != null) c.enabled = false;

                    var renderers = go.GetComponentsInChildren<SkinnedMeshRenderer>();
                    if (renderers.Length > 0)
                    {
                        string skinName = skinNames[i % skinNames.Length];
                        var skinTex = Resources.Load<Texture2D>("Characters/Skins/" + skinName);
                        if (skinTex != null)
                        {
                            UnityBridge.LogToAndroid("NPCSpawn", $"Skin applied: {skinName} to NPC_{i}");
                            foreach (var mr in renderers)
                            {
                                var mats = mr.sharedMaterials;
                                for (int m = 0; m < mats.Length; m++)
                                {
                                    var mat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
                                    if (mat.shader == null) mat = new Material(Shader.Find("Standard"));
                                    if (mat.shader.name.StartsWith("Universal Render Pipeline"))
                                    {
                                        mat.SetColor("_BaseColor", Color.white);
                                        mat.SetTexture("_BaseMap", skinTex);
                                    }
                                    else
                                    {
                                        mat.SetColor("_Color", Color.white);
                                        mat.SetTexture("_MainTex", skinTex);
                                    }
                                    mats[m] = mat;
                                }
                                mr.sharedMaterials = mats;
                            }
                        }
                    }
                }
                else
                {
                    go = new GameObject("NPC_" + i);
                    go.transform.SetParent(root, false);
                    go.tag = "Untagged";
                }

                var npc = go.AddComponent<NPCController>();
                npc.Init(path.ToArray(), rng);

                npcs.Add(go);
            }

            spawned = true;
            lastCenter = root.position;
            UnityBridge.LogToAndroid("NPCSpawn", $"NPC creati: {npcs.Count} con modello Kenney={(_charPrefab != null)}");
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

using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using Huntix.Bridge;

namespace City.NPC
{
    /// <summary>
    /// Popolamento deterministico dei pedoni sui marciapiedi del chunk
    /// (stesso schema di ChunkVehiclePopulator): seed dall'indice del chunk,
    /// waypoint lungo le strade urbane con l'offset marciapiede usato anche
    /// da RoadRenderer, cosi' i pedoni camminano sul cordolo e non in strada.
    /// </summary>
    public static class NPCPopulator
    {
        private const int MaxNpcPerChunk = 5;
        private const float MinPathLen = 18f;
        private const float Y_SIDEWALK = 0.12f;   // = RoadRenderer.Y_SIDEWALK
        private const float GAP = 0.15f;          // = RoadRenderer gap asfalto/marciapiede
        private const float SIDEWALK_W = 1.8f;    // = RoadRenderer.SIDEWALK_W

        private static GameObject _charPrefab;

        private static readonly string[] CitizenSkins =
            { "humanMaleA", "humanFemaleA", "humanMaleA", "humanFemaleA" };

        public static void Populate(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (chunk.geo?.roads == null || chunk.geo.roads.Length == 0) return;
            LoadCharacterPrefab();
            if (_charPrefab == null) return;

            // seed deterministico: stesso chunk -> stessi pedoni
            int seed = chunk.index.x * 73856093 ^ chunk.index.y * 19349663;
            var rng = new System.Random(seed);

            var paths = BuildSidewalkPaths(chunk.geo.roads, toLocal, bounds, rng);
            if (paths.Count == 0) return;

            chunk.npcsGo = new GameObject("NPC");
            chunk.npcsGo.transform.SetParent(chunk.root.transform, false);

            int count = Mathf.Min(MaxNpcPerChunk, paths.Count);
            int placed = 0;
            for (int i = 0; i < count; i++)
            {
                try
                {
                    var path = paths[rng.Next(paths.Count)];
                    GameObject go = Object.Instantiate(_charPrefab, chunk.npcsGo.transform);
                    go.name = "NPC_" + chunk.key + "_" + i;
                    go.tag = "Untagged";
                    go.transform.localScale = Vector3.one * 0.455f;

                    // i collider importati dal FBX restano spenti: il corpo
                    // solido lo aggiunge NPCController (capsula)
                    foreach (var c in go.GetComponentsInChildren<Collider>())
                        if (c != null) c.enabled = false;

                    ApplySkin(go, CitizenSkins[placed % CitizenSkins.Length]);

                    string npcId = "npc_" + chunk.index.x + "_" + chunk.index.y + "_" + i;
                    var npc = go.AddComponent<NPCController>();
                    npc.Init(path.ToArray(), rng, npcId);
                    placed++;
                }
                catch (System.Exception e)
                {
                    UnityBridge.LogToAndroid("NPCPopulator",
                        "NPC saltato nel " + chunk.key + ": " + e.ToString());
                }
            }
            if (placed > 0)
                OsmDiag.Log("[NPCPopulator] " + chunk.key + ": " + placed + " pedoni");
        }

        private static void LoadCharacterPrefab()
        {
            if (_charPrefab != null) return;
            _charPrefab = Resources.Load<GameObject>("Characters/characterMedium");
        }

        private static List<List<Vector3>> BuildSidewalkPaths(TileRoadRec[] roads,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds, System.Random rng)
        {
            var paths = new List<List<Vector3>>();

            foreach (var road in roads)
            {
                if (road?.pts == null || road.pts.Length < 2) continue;
                string hw = road.hw ?? "";
                if (!SidewalkClass(hw)) continue;

                float width = City.OSM.RoadRenderer.RoadWidth(hw);
                float offset = width * 0.5f + GAP + SIDEWALK_W * 0.5f;

                // due percorsi per strada: lato destro e sinistro
                for (int sideIdx = 0; sideIdx < 2; sideIdx++)
                {
                    float side = sideIdx == 0 ? 1f : -1f;
                    var pts = new List<Vector3>();
                    for (int i = 0; i < road.pts.Length; i++)
                        pts.Add(toLocal(road.pts[i]));

                    var path = SampleWithOffset(pts, offset * side, rng);
                    float len = PathLength(path);
                    if (len < MinPathLen) continue;
                    if (!RectIntersects(path, bounds)) continue;
                    paths.Add(path);
                }
            }
            return paths;
        }

        // Classi stradali con marciapiede (specchio di RoadRenderer).
        private static bool SidewalkClass(string hw)
        {
            switch (hw)
            {
                case "primary":
                case "secondary":
                case "tertiary":
                case "residential":
                case "living_street":
                case "unclassified":
                    return true;
                default:
                    return false;
            }
        }

        // Punti interpolati lungo la polyline ogni ~8-13 m, spostati a
        // destra (offset > 0) o sinistra della mezzeria.
        private static List<Vector3> SampleWithOffset(List<Vector3> line,
            float offset, System.Random rng)
        {
            var result = new List<Vector3>();
            if (line.Count < 2) return result;
            float step = 8f + (float)rng.NextDouble() * 5f;
            float carry = 0f;

            for (int i = 1; i < line.Count; i++)
            {
                Vector3 a = line[i - 1], b = line[i];
                Vector3 seg = b - a; seg.y = 0f;
                float segLen = seg.magnitude;
                if (segLen < 0.01f) continue;
                Vector3 dir = seg / segLen;
                Vector3 right = new Vector3(dir.z, 0f, -dir.x) * Mathf.Sign(offset);
                float offAbs = Mathf.Abs(offset);

                float t = carry;
                while (t <= segLen)
                {
                    Vector3 p = a + dir * t;
                    p.y = Y_SIDEWALK;
                    result.Add(p + right * offAbs);
                    t += step;
                }
                carry = t - segLen;
            }
            return result;
        }

        private static float PathLength(List<Vector3> p)
        {
            float len = 0f;
            for (int i = 1; i < p.Count; i++) len += Vector3.Distance(p[i - 1], p[i]);
            return len;
        }

        private static bool RectIntersects(List<Vector3> p, Rect r)
        {
            for (int i = 0; i < p.Count; i++)
                if (r.Contains(new Vector2(p[i].x, p[i].z))) return true;
            return false;
        }

        // Stessa tecnica dello storico NPCSpawnManager: sostituisce le
        // texture dei materiali degli SkinnedMeshRenderer con la skin scelta.
        private static void ApplySkin(GameObject go, string skinName)
        {
            var skinTex = Resources.Load<Texture2D>("Characters/Skins/" + skinName);
            if (skinTex == null) return;
            var renderers = go.GetComponentsInChildren<SkinnedMeshRenderer>();
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

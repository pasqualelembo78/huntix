using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using City.Diagnostics;
using City.Interior;
using City.OSM;

namespace City.Environment
{
    /// <summary>
    /// Dettagli naturali additivi per TUTTO il mondo (chunk OSM, citta'
    /// fittizia, mondo legacy). Usa i modelli Kenney Nature Kit CC0 gia'
    /// importati in Resources/Nature: nessun asset nuovo, nessuna rete.
    ///
    /// Ogni tipo di dettaglio e' un LOTTO DI ISTANZE GPU (Graphics.DrawMeshInstanced)
    /// con mesh combinata una sola volta e materiale condiviso: centinaia di
    /// ciuffi d'erba costano POCHI draw call e zero GameObject per pianta.
    /// Il batch si disegna solo entro una distanza (streaming) e i gruppi sono
    /// spenti dal LOD del chunk. Nessun collider: la vegetazione non puo'
    /// mai bloccare porte, marciapiedi o veicoli (PORTA_RADIUS = 4 m).
    /// </summary>
    public static class NatureDetailPlacer
    {
        public const string NearGroup = "PratiFioriti";
        public const string FarGroup = "CespugliRocci";
        public const string TreeGroup = "AlberiParco";
        public const string LightGroup = "LuciNature";

        private const float PORTA_RADIUS2 = 4f * 4f;
        private const int MaxNearPerChunk = 420;
        private const int MaxFarPerChunk = 190;
        private const int MaxTreesPerChunk = 12;
        // Una sola luce calda per chunk, e solo a LOD0: 9 point light invece
        // di decine in tutto il raggio di streaming.
        private const int MaxLightsPerChunk = 1;

        private static readonly string[] GrassNames =
            { "grass", "grass_large", "grass_leafs", "grass_leafsLarge" };
        private static readonly string[] FlowerNames =
        {
            "flower_redA", "flower_redB", "flower_yellowA", "flower_yellowB",
            "flower_purpleA", "flower_purpleB", "flower_purpleC"
        };
        private static readonly string[] BushNames =
            { "plant_bush", "plant_bushSmall", "plant_bushDetailed", "plant_bushTriangle" };
        private static readonly string[] RockNames =
            { "rock_smallA", "rock_smallB", "rock_smallC", "rock_smallFlatA", "rock_tallA" };
        private static readonly string[] ParkTreeNames =
            { "tree_oak", "tree_default", "tree_cone", "tree_fat", "tree_palm" };

        private static readonly Dictionary<string, PrefabBatch> BatchCache =
            new Dictionary<string, PrefabBatch>(StringComparer.Ordinal);
        private static readonly Dictionary<int, Material> MaterialCache =
            new Dictionary<int, Material>();
        private static Material _fallbackMat;
        private static Material _lampMat;
        private static bool _registered;
        // Mondi non-chunked (Citta' Gioco e CityOSMWorld): i loro gruppi vanno
        // aggiornati quando il menu cambia una preferenza a caldo.
        private static readonly List<GameObject> FlatRoots = new List<GameObject>();

        private sealed class PrefabBatch
        {
            public Mesh mesh;
            public Material material;
        }

        /// <summary>Registra gli handler prima di ogni costruzione: il toggle
        /// del menu deve agire anche sui chunk gia' in scena.</summary>
        public static void EnsureHandlers()
        {
            if (_registered) return;
            _registered = true;
            VegetationFeatureBridge.Register(VegetationFeatureBridge.AllIds[0],
                on => ApplyGroup(NearGroup, on));
            VegetationFeatureBridge.Register(VegetationFeatureBridge.AllIds[1],
                on => ApplyGroup(FarGroup, on));
            VegetationFeatureBridge.Register(VegetationFeatureBridge.AllIds[2],
                on => ApplyGroup(TreeGroup, on));
            VegetationFeatureBridge.Register(VegetationFeatureBridge.AllIds[3],
                on => ApplyGroup(LightGroup, on));
        }

        private static void ApplyGroup(string group, bool on)
        {
            var mgr = UnityEngine.Object.FindObjectOfType<ChunkManager>();
            if (mgr != null)
            {
                var chunks = mgr.BuiltChunks();
                for (int i = 0; i < chunks.Count; i++)
                {
                    var c = chunks[i];
                    if (c != null && c.root != null)
                        c.SetDetailPreference(group, on);
                }
            }
            for (int i = FlatRoots.Count - 1; i >= 0; i--)
            {
                var root = FlatRoots[i];
                if (root == null) { FlatRoots.RemoveAt(i); continue; }
                var t = root.transform.Find(group);
                if (t != null) t.gameObject.SetActive(on);
            }
        }

        // ── ingresso principale: chunk OSM ────────────────────────────

        public static int Build(ChunkData chunk, TerrainHeightField heights,
            Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            EnsureHandlers();
            if (chunk == null || chunk.root == null || chunk.geo == null) return 0;

            chunk.detailsGo = NewGroup(chunk.root.transform, "DettagliNaturali");
            var near = NewGroup(chunk.detailsGo.transform, NearGroup);
            var far = NewGroup(chunk.detailsGo.transform, FarGroup);
            var treeGroup = NewGroup(chunk.detailsGo.transform, TreeGroup);
            var lights = NewGroup(chunk.detailsGo.transform, LightGroup);

            VegetationFeatureBridge.TryGetEnabled(VegetationFeatureBridge.AllIds[0], out bool nearOn);
            VegetationFeatureBridge.TryGetEnabled(VegetationFeatureBridge.AllIds[1], out bool farOn);
            VegetationFeatureBridge.TryGetEnabled(VegetationFeatureBridge.AllIds[2], out bool treeOn);
            VegetationFeatureBridge.TryGetEnabled(VegetationFeatureBridge.AllIds[3], out bool lightOn);
            near.SetActive(nearOn);
            far.SetActive(farOn);
            treeGroup.SetActive(treeOn);
            lights.SetActive(lightOn);

            var doorPos = CollectDoors(chunk);
            // Posizioni locali degli alberi OSM: servono per non sovrapporre
            // gli alberi aggiuntivi dei parchi a quelli gia' presenti.
            var treeLocals = new List<Vector2>();
            if (chunk.geo.trees != null)
            {
                for (int i = 0; i < chunk.geo.trees.Length; i++)
                {
                    var t = chunk.geo.trees[i];
                    if (t == null) continue;
                    var lp = toLocal(t);
                    if (bounds.Contains(new Vector2(lp.x, lp.z)))
                        treeLocals.Add(new Vector2(lp.x, lp.z));
                }
            }
            var nearMats = new Dictionary<string, List<Matrix4x4>>(StringComparer.Ordinal);
            var farMats = new Dictionary<string, List<Matrix4x4>>(StringComparer.Ordinal);
            var treeMats = new Dictionary<string, List<Matrix4x4>>(StringComparer.Ordinal);
            int nNear = 0, nFar = 0, nTrees = 0;

            int seed = chunk.index.x * 73856093 ^ chunk.index.y * 19349663;
            var parks = chunk.geo.parks;
            if (parks != null)
            {
                for (int p = 0; p < parks.Length; p++)
                {
                    var park = parks[p];
                    if (park == null || park.poly == null || park.poly.Length < 3 ||
                        string.Equals(park.kd, "water", StringComparison.OrdinalIgnoreCase))
                        continue;

                    // Dedup: un parco appartiene al chunk del suo centroide,
                    // come la mesh verde di VegetationPlacer.
                    double clat = 0, clng = 0;
                    for (int i = 0; i < park.poly.Length; i++)
                    { clat += park.poly[i].a; clng += park.poly[i].o; }
                    var centerGeo = new GeoLL { a = clat / park.poly.Length, o = clng / park.poly.Length };
                    var center = toLocal(centerGeo);
                    var expanded = new Rect(bounds.x - 200f, bounds.y - 200f,
                        bounds.width + 400f, bounds.height + 400f);
                    if (!expanded.Contains(new Vector2(center.x, center.z))) continue;

                    var poly = new List<Vector2>(park.poly.Length);
                    float minX = float.MaxValue, maxX = float.MinValue;
                    float minZ = float.MaxValue, maxZ = float.MinValue;
                    for (int i = 0; i < park.poly.Length; i++)
                    {
                        var v = toLocal(park.poly[i]);
                        poly.Add(new Vector2(v.x, v.z));
                        minX = Mathf.Min(minX, v.x); maxX = Mathf.Max(maxX, v.x);
                        minZ = Mathf.Min(minZ, v.z); maxZ = Mathf.Max(maxZ, v.z);
                    }

                    int parkSeed = Hash(seed ^ (int)park.id);
                    // ScatterPark aggiorna il totale tramite ref: non sommare
                    // anche il valore di ritorno (conteggio doppio).
                    if (nearOn)
                        ScatterPark(poly, minX, maxX, minZ, maxZ, parkSeed,
                            11f, chunk, heights, doorPos, nearMats, ref nNear);
                    if (farOn)
                        ScatterPark(poly, minX, maxX, minZ, maxZ, parkSeed ^ 0x5eed,
                            26f, chunk, heights, doorPos, farMats, ref nFar, true);
                    if (treeOn && nTrees < MaxTreesPerChunk)
                        nTrees += ScatterParkTrees(poly, minX, maxX, minZ, maxZ,
                            parkSeed ^ 0xa11e, chunk, heights, doorPos, treeLocals,
                            treeMats, MaxTreesPerChunk - nTrees);

                    // Luce calda SOLO nel parco del chunk, mai per ogni pianta:
                    // al massimo 2 sorgenti non-ombreggianti per chunk.
                    if (lightOn)
                        ScatterLight(lights, chunk, heights, center, parkSeed);

                    if (nearOn)
                        VegetationFeatureBridge.GrantParkEgg(
                            park.id.ToString(),
                            chunk.root.transform.position + new Vector3(center.x,
                                SampleHeight(chunk, heights, center) + 0.65f, center.z),
                            VegetationFeatureBridge.AllPlace);
                }
            }

            // Corone d'erba e fiori attorno agli alberi OSM: rende il passaggio
            // fra asfalto e chioma graduale, senza toccare gli alberi esistenti.
            var trees = chunk.geo.trees;
            if (nearOn && trees != null)
            {
                for (int i = 0; i < trees.Length && nNear < MaxNearPerChunk; i++)
                {
                    var t = trees[i];
                    if (t == null) continue;
                    var p = toLocal(t);
                    if (!bounds.Contains(new Vector2(p.x, p.z))) continue;
                    int h = Hash(seed ^ i * 7919);
                    for (int k = 0; k < 4 && nNear < MaxNearPerChunk; k++)
                    {
                        int q = Hash(h ^ k * 104729);
                        float ang = (q % 360) * Mathf.Deg2Rad;
                        float r = 2.6f + ((q >> 8) % 40) * 0.1f;
                        var at = new Vector3(p.x + Mathf.Cos(ang) * r, 0f,
                            p.z + Mathf.Sin(ang) * r);
                        if (!bounds.Contains(new Vector2(at.x, at.z))) continue;
                        if (!FarFromDoors(at, doorPos)) continue;
                        string prefab = (k + q) % 3 == 0
                            ? FlowerNames[q % FlowerNames.Length]
                            : GrassNames[(q >> 4) % GrassNames.Length];
                        AddInstance(nearMats, prefab, MatrixFor(chunk, heights, at,
                            0.55f + (q % 40) * 0.012f, q % 360));
                        nNear++;
                    }
                }
            }

            // Aiuole lungo i marciapiedi delle strade urbane: smoothing dei
            // bordi e varieta' per le zone senza parchi. Distanza dalle porte
            // identica a PropSpawner (4 m), nessun collider.
            var roads = chunk.geo.roads;
            if (nearOn && roads != null && nNear < MaxNearPerChunk)
            {
                for (int r = 0; r < roads.Length && nNear < MaxNearPerChunk; r++)
                {
                    var road = roads[r];
                    if (road?.pts == null || road.pts.Length < 2 ||
                        string.IsNullOrEmpty(road.hw)) continue;
                    if (!IsUrbanRoad(road.hw)) continue;
                    int roadSeed = Hash(seed ^ r * 6151);
                    for (int s = 0; s < road.pts.Length - 1 && nNear < MaxNearPerChunk; s++)
                    {
                        var a = toLocal(road.pts[s]);
                        var b = toLocal(road.pts[s + 1]);
                        var dir = b - a; dir.y = 0f;
                        float len = dir.magnitude;
                        if (len < 20f) continue;
                        dir /= len;
                        var side = new Vector3(-dir.z, 0f, dir.x);
                        for (float t = 18f; t < len - 10f; t += 46f)
                        {
                            int h = Hash(roadSeed ^ s * 131 ^ (int)t);
                            var at = a + dir * t + side * (h % 2 == 0 ? 3.1f : -3.1f);
                            if (!bounds.Contains(new Vector2(at.x, at.z))) continue;
                            if (!FarFromDoors(at, doorPos)) continue;
                            int count = 2 + (h % 2);
                            for (int k = 0; k < count && nNear < MaxNearPerChunk; k++)
                            {
                                var q = Hash(h ^ k * 7717);
                                var tx = at.x + ((q % 100) / 100f - 0.5f) * 4f;
                                var tz = at.z + (((q >> 7) % 100) / 100f - 0.5f) * 4f;
                                var spot = new Vector3(tx, 0f, tz);
                                if (!FarFromDoors(spot, doorPos)) continue;
                                string prefab = q % 4 == 0
                                    ? GrassNames[(q >> 3) % GrassNames.Length]
                                    : FlowerNames[q % FlowerNames.Length];
                                AddInstance(nearMats, prefab, MatrixFor(chunk, heights,
                                    spot, 0.6f + (q % 30) * 0.012f, q % 360));
                                nNear++;
                            }
                        }
                    }
                }
            }

            int batches = Flush(near, nearMats, 260f, ShadowCastingMode.Off) +
                          Flush(far, farMats, 620f, ShadowCastingMode.Off) +
                          Flush(treeGroup, treeMats, 700f, ShadowCastingMode.On);
            if (nNear + nFar + nTrees > 0)
                OsmDiag.Log("[Dettagli] " + chunk.key + " erba/fiori=" + nNear +
                    " cespugli/rocce=" + nFar + " alberi=" + nTrees +
                    " lotti=" + batches);
            return nNear + nFar + nTrees;
        }

        // ── mondo fittizio e mondo legacy (suolo piatto, nessun chunk DEM) ─

        /// <summary>Variante per world piatti (Citta' Gioco e CityOSMWorld):
        /// riceve punti in coordinate locali del mondo, quota costante, e
        /// crea gli stessi lotti GPU. Nessuna dipendenza da tile/DEM/GeoLL.</summary>
        public static GameObject BuildFlat(Transform parent, IList<Vector2> points,
            float groundY, int seed, float maxInstances = 360f)
        {
            EnsureHandlers();
            if (parent == null || points == null || points.Count == 0) return null;
            var root = new GameObject("DettagliNaturali");
            root.transform.SetParent(parent, false);
            if (!FlatRoots.Contains(root)) FlatRoots.Add(root);
            var near = NewGroup(root.transform, NearGroup);
            var far = NewGroup(root.transform, FarGroup);
            bool nearOn = VegetationFeatureBridge.Enabled(VegetationFeatureBridge.AllIds[0]);
            bool farOn = VegetationFeatureBridge.Enabled(VegetationFeatureBridge.AllIds[1]);
            near.SetActive(nearOn);
            far.SetActive(farOn);
            var nearMats = new Dictionary<string, List<Matrix4x4>>(StringComparer.Ordinal);
            var farMats = new Dictionary<string, List<Matrix4x4>>(StringComparer.Ordinal);
            int nNear = 0, nFar = 0;
            for (int i = 0; i < points.Count; i++)
            {
                int h = Hash(seed ^ i * 7919);
                Vector2 c = points[i];
                int count = Mathf.Min(7, 3 + (h % 4));
                for (int k = 0; k < count && nNear < maxInstances; k++)
                {
                    int q = Hash(h ^ k * 104729);
                    float ang = (q % 360) * Mathf.Deg2Rad;
                    float r = ((q >> 6) % 80) * 0.1f;
                    var at = new Vector3(c.x + Mathf.Cos(ang) * r, groundY,
                        c.y + Mathf.Sin(ang) * r);
                    string prefab = q % 3 == 0
                        ? FlowerNames[q % FlowerNames.Length]
                        : GrassNames[(q >> 4) % GrassNames.Length];
                    AddInstance(nearMats, prefab, MatrixFor(at,
                        0.6f + (q % 30) * 0.012f, q % 360));
                    nNear++;
                }
                if (farOn && nFar < maxInstances * 0.25f)
                {
                    string prefab = h % 2 == 0 ? BushNames[h % BushNames.Length]
                        : RockNames[(h >> 3) % RockNames.Length];
                    var at = new Vector3(c.x + 2f, groundY, c.y + 1.5f);
                    AddInstance(farMats, prefab, MatrixFor(at, 0.85f, h % 360));
                    nFar++;
                }
            }
            Flush(near, nearMats, 320f, ShadowCastingMode.Off);
            Flush(far, farMats, 700f, ShadowCastingMode.Off);
            if (nNear + nFar > 0)
                OsmDiag.Log("[Dettagli] mondo piatto: erba/fiori=" + nNear +
                    " cespugli/rocce=" + nFar);
            return root;
        }

        // ── scattering ────────────────────────────────────────────────

        private static int ScatterPark(List<Vector2> poly, float minX, float maxX,
            float minZ, float maxZ, int seed, float step, ChunkData chunk,
            TerrainHeightField heights, List<Vector3> doors,
            Dictionary<string, List<Matrix4x4>> mats, ref int total,
            bool far = false)
        {
            if (maxX - minX < 2f || maxZ - minZ < 2f) return 0;
            int placed = 0;
            int ix = 0, iz = 0;
            for (float x = minX + step * 0.5f; x < maxX; x += step, ix++)
            {
                for (float z = minZ + step * 0.5f; z < maxZ; z += step, iz++)
                {
                    int h = Hash(seed ^ ix * 92837111 ^ iz * 689287499);
                    var p = new Vector2(x + ((h % 100) / 100f - 0.5f) * step * 0.7f,
                        z + (((h >> 8) % 100) / 100f - 0.5f) * step * 0.7f);
                    if (!Inside(poly, p)) continue;
                    var at = new Vector3(p.x, 0f, p.y);
                    if (!FarFromDoors(at, doors)) continue;
                    string prefab;
                    float scale;
                    if (far)
                    {
                        int cap = Mathf.Min(MaxFarPerChunk, 190);
                        if (total >= cap) return placed;
                        prefab = (h & 1) == 0 ? BushNames[(h >> 4) % BushNames.Length]
                            : RockNames[(h >> 9) % RockNames.Length];
                        scale = 0.75f + (h % 35) * 0.012f;
                    }
                    else
                    {
                        if (total >= MaxNearPerChunk) return placed;
                        prefab = h % 4 == 0
                            ? FlowerNames[(h >> 6) % FlowerNames.Length]
                            : GrassNames[(h >> 11) % GrassNames.Length];
                        scale = 0.6f + (h % 30) * 0.014f;
                    }
                    AddInstance(mats, prefab, MatrixFor(chunk, heights, at, scale, h % 360));
                    placed++;
                    total++;
                }
            }
            return placed;
        }

        private static int ScatterParkTrees(List<Vector2> poly, float minX, float maxX,
            float minZ, float maxZ, int seed, ChunkData chunk,
            TerrainHeightField heights, List<Vector3> doors,
            List<Vector2> existingTrees, Dictionary<string, List<Matrix4x4>> mats, int cap)
        {
            if (cap <= 0 || maxX - minX < 12f || maxZ - minZ < 12f) return 0;
            int placed = 0;
            for (int k = 0; k < cap * 4 && placed < cap; k++)
            {
                int h = Hash(seed ^ k * 6151);
                var p = new Vector2(
                    minX + ((h % 1000) / 1000f) * (maxX - minX),
                    minZ + (((h >> 10) % 1000) / 1000f) * (maxZ - minZ));
                if (!Inside(poly, p)) continue;
                var at = new Vector3(p.x, 0f, p.y);
                if (!FarFromDoors(at, doors)) continue;
                // Non sovrapporre gli alberi OSM gia' presenti (7 m).
                bool tooClose = false;
                for (int t = 0; t < existingTrees.Count; t++)
                {
                    float dx = existingTrees[t].x - p.x;
                    float dz = existingTrees[t].y - p.y;
                    if (dx * dx + dz * dz < 49f) { tooClose = true; break; }
                }
                if (tooClose) continue;
                AddInstance(mats, ParkTreeNames[h % ParkTreeNames.Length],
                    MatrixFor(chunk, heights, at, 0.85f + (h % 30) * 0.01f, h % 360));
                placed++;
            }
            return placed;
        }

        private static bool ScatterLight(GameObject parent, ChunkData chunk,
            TerrainHeightField heights, Vector3 center, int seed)
        {
            if (parent.transform.childCount >= MaxLightsPerChunk) return false;
            var go = new GameObject("LuceParco");
            go.transform.SetParent(parent.transform, false);
            go.transform.localPosition = new Vector3(center.x,
                SampleHeight(chunk, heights, center) + 2.6f, center.z);

            var lamp = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            lamp.name = "LampoParco";
            lamp.transform.SetParent(go.transform, false);
            lamp.transform.localScale = Vector3.one * 0.34f;
            if (_lampMat == null)
            {
                var shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                _lampMat = new Material(shader);
                Color warm = new Color(1f, 0.84f, 0.55f);
                if (_lampMat.HasProperty("_BaseColor")) _lampMat.SetColor("_BaseColor", warm);
                if (_lampMat.HasProperty("_Color")) _lampMat.SetColor("_Color", warm);
                if (_lampMat.HasProperty("_EmissionColor"))
                {
                    _lampMat.EnableKeyword("_EMISSION");
                    _lampMat.SetColor("_EmissionColor", warm * 1.6f);
                }
                _lampMat.enableInstancing = true;
            }
            lamp.GetComponent<Renderer>().sharedMaterial = _lampMat;
            var col = lamp.GetComponent<Collider>();
            if (col != null) col.enabled = false;

            var light = go.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.87f, 0.68f);
            light.intensity = 1.1f;
            light.range = 14f;
            light.shadows = LightShadows.None;
            light.renderMode = LightRenderMode.ForcePixel;
            return true;
        }

        // ── istanze e lotti GPU ───────────────────────────────────────

        private static GameObject NewGroup(Transform parent, string name)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            return go;
        }

        private static Matrix4x4 MatrixFor(ChunkData chunk, TerrainHeightField heights,
            Vector3 local, float scale, float yaw)
        {
            var p = new Vector3(local.x, SampleHeight(chunk, heights, local), local.z);
            return Matrix4x4.TRS(p, Quaternion.Euler(0f, yaw, 0f), Vector3.one * scale);
        }

        private static Matrix4x4 MatrixFor(Vector3 at, float scale, float yaw)
        {
            return Matrix4x4.TRS(at, Quaternion.Euler(0f, yaw, 0f), Vector3.one * scale);
        }

        private static float SampleHeight(ChunkData chunk, TerrainHeightField heights,
            Vector3 local)
        {
            if (heights != null) return heights.HeightNear(local.x, local.z);
            if (chunk != null && chunk.root != null && WorldOrigin.Initialized)
            {
                var world = chunk.root.transform.position +
                    new Vector3(local.x, 0f, local.z);
                float h;
                if (TileElevation.TryHeightAtWorld(world, out h)) return h;
            }
            return 0f;
        }

        private static void AddInstance(Dictionary<string, List<Matrix4x4>> mats,
            string prefab, Matrix4x4 m)
        {
            if (mats == null || string.IsNullOrEmpty(prefab)) return;
            List<Matrix4x4> list;
            if (!mats.TryGetValue(prefab, out list))
            {
                list = new List<Matrix4x4>();
                mats[prefab] = list;
            }
            list.Add(m);
        }

        private static int Flush(GameObject parent,
            Dictionary<string, List<Matrix4x4>> mats, float maxDistance,
            ShadowCastingMode shadows)
        {
            if (parent == null || mats == null || mats.Count == 0) return 0;
            int batches = 0;
            foreach (var kv in mats)
            {
                var batch = GetBatch(kv.Key);
                if (batch == null || batch.mesh == null) continue;
                var list = kv.Value;
                for (int start = 0; start < list.Count; start += NatureInstancedBatch.MaxPerDraw)
                {
                    int count = Mathf.Min(NatureInstancedBatch.MaxPerDraw, list.Count - start);
                    var block = new Matrix4x4[count];
                    list.CopyTo(start, block, 0, count);
                    var go = new GameObject(kv.Key);
                    go.transform.SetParent(parent.transform, false);
                    var renderer = go.AddComponent<NatureInstancedBatch>();
                    renderer.Configure(parent.transform, batch.mesh, batch.material,
                        block, maxDistance, shadows);
                    batches++;
                }
            }
            return batches;
        }

        private static PrefabBatch GetBatch(string prefabName)
        {
            PrefabBatch cached;
            if (BatchCache.TryGetValue(prefabName, out cached)) return cached;
            var prefab = Resources.Load<GameObject>("Nature/" + prefabName);
            if (prefab == null) return null;
            var filters = prefab.GetComponentsInChildren<MeshFilter>(true);
            if (filters == null || filters.Length == 0) return null;
            var combine = new CombineInstance[filters.Length];
            int used = 0;
            Material sourceMat = null;
            var rootInv = prefab.transform.localToWorldMatrix.inverse;
            foreach (var f in filters)
            {
                if (f == null || f.sharedMesh == null) continue;
                combine[used].mesh = f.sharedMesh;
                combine[used].transform = rootInv * f.transform.localToWorldMatrix;
                if (sourceMat == null)
                {
                    var r = f.GetComponent<MeshRenderer>();
                    if (r != null && r.sharedMaterial != null) sourceMat = r.sharedMaterial;
                }
                used++;
            }
            if (used == 0) return null;
            if (used < combine.Length)
            {
                var compact = new CombineInstance[used];
                Array.Copy(combine, compact, used);
                combine = compact;
            }
            var mesh = new Mesh { name = "Veg_" + prefabName };
            mesh.CombineMeshes(combine, true, true);
            mesh.RecalculateBounds();
            cached = new PrefabBatch { mesh = mesh, material = SharedMaterial(sourceMat) };
            BatchCache[prefabName] = cached;
            return cached;
        }

        private static Material SharedMaterial(Material source)
        {
            if (source == null)
            {
                if (_fallbackMat == null)
                {
                    var shader = Shader.Find("Universal Render Pipeline/Lit");
                    if (shader == null) shader = Shader.Find("Standard");
                    _fallbackMat = new Material(shader);
                    if (_fallbackMat.HasProperty("_BaseColor"))
                        _fallbackMat.SetColor("_BaseColor", new Color(0.30f, 0.52f, 0.25f));
                    _fallbackMat.enableInstancing = true;
                }
                return _fallbackMat;
            }
            int key = source.GetInstanceID();
            Material instanced;
            if (MaterialCache.TryGetValue(key, out instanced)) return instanced;

            bool urp = IsUrp(source);
            if (urp && source.enableInstancing)
            {
                // Materiale d'asset gia' pronto: non lo modifichiamo.
                MaterialCache[key] = source;
                return source;
            }
            if (urp)
            {
                instanced = new Material(source);
            }
            else
            {
                // FBX con shader built-in: in URP diventerebbe magenta.
                // Ripartiamo dal Lit URP conservando colore e texture.
                var shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                instanced = new Material(shader);
                Color c = source.color;
                if (instanced.HasProperty("_BaseColor")) instanced.SetColor("_BaseColor", c);
                if (instanced.HasProperty("_Color")) instanced.SetColor("_Color", c);
                if (source.mainTexture != null) instanced.mainTexture = source.mainTexture;
            }
            instanced.enableInstancing = true;
            MaterialCache[key] = instanced;
            return instanced;
        }

        private static bool IsUrp(Material m)
        {
            if (m == null || m.shader == null) return false;
            string n = m.shader.name;
            return n.StartsWith("Universal Render Pipeline", StringComparison.Ordinal) ||
                   n.StartsWith("Hidden/Universal", StringComparison.Ordinal);
        }

        // ── helper geometrici ─────────────────────────────────────────

        private static readonly HashSet<string> UrbanRoads = new HashSet<string>
        {
            "primary", "secondary", "tertiary", "residential", "unclassified",
            "living_street", "service", "pedestrian"
        };

        private static bool IsUrbanRoad(string hw)
        {
            return UrbanRoads.Contains(hw);
        }

        private static List<Vector3> CollectDoors(ChunkData chunk)
        {
            var list = new List<Vector3>();
            if (chunk == null || chunk.buildingsGo == null) return list;
            var entrances = chunk.buildingsGo.GetComponentsInChildren<BuildingEntrance>(true);
            for (int i = 0; i < entrances.Length; i++)
                if (entrances[i] != null) list.Add(entrances[i].transform.position);
            return list;
        }

        private static bool FarFromDoors(Vector3 local, List<Vector3> doors)
        {
            if (doors == null) return true;
            for (int i = 0; i < doors.Count; i++)
            {
                float dx = local.x - doors[i].x;
                float dz = local.z - doors[i].z;
                if (dx * dx + dz * dz < PORTA_RADIUS2) return false;
            }
            return true;
        }

        private static bool Inside(List<Vector2> poly, Vector2 p)
        {
            bool inside = false;
            for (int i = 0, j = poly.Count - 1; i < poly.Count; j = i++)
            {
                var a = poly[i];
                var b = poly[j];
                if ((a.y > p.y) != (b.y > p.y) &&
                    p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)
                    inside = !inside;
            }
            return inside;
        }

        private static int Hash(int x)
        {
            uint u = (uint)x;
            u ^= u >> 16; u *= 0x7feb352d; u ^= u >> 15; u *= 0x846ca68b; u ^= u >> 16;
            return (int)(u & 0x7fffffff);
        }
    }

    /// <summary>
    /// Un lotto di dettagli identici disegnato con GPU instancing.Una sola
    /// chiamata a DrawMeshInstanced per blocco da 1023, distanza di streaming
    /// e matrici world ricalcolate SOLO quando il chunk si muove (rebase GPS).
    /// Nessun GameObject per pianta, nessun collider, nessuna Light per pianta.
    /// </summary>
    public sealed class NatureInstancedBatch : MonoBehaviour
    {
        public const int MaxPerDraw = 1023;

        private Mesh _mesh;
        private Material _material;
        private Matrix4x4[][] _blocks;
        private Transform _anchor;
        private bool _dirty = true;
        private float _maxDistance;
        private ShadowCastingMode _shadows;

        public void Configure(Transform anchor, Mesh mesh, Material material,
            Matrix4x4[] matrices, float maxDistance, ShadowCastingMode shadows)
        {
            _anchor = anchor;
            _mesh = mesh;
            _material = material;
            _maxDistance = maxDistance;
            _shadows = shadows;
            _blocks = new[] { matrices };
            _dirty = true;
        }

        private void Update()
        {
            if (_mesh == null || _material == null || _blocks == null) return;
            if (_anchor == null) { enabled = false; return; }
            if (_anchor.hasChanged) _dirty = true;
            if (_dirty)
            {
                var rootMatrix = _anchor.localToWorldMatrix;
                for (int b = 0; b < _blocks.Length; b++)
                {
                    var local = _blocks[b];
                    for (int i = 0; i < local.Length; i++)
                        local[i] = rootMatrix * local[i];
                }
                _dirty = false;
                // hasChanged NON si azzera da solo in Unity: senza questo
                // reset le matrici verrebbero moltiplicate di nuovo a ogni
                // frame, facendo volare via i dettagli dal chunk.
                _anchor.hasChanged = false;
            }
            var cam = Camera.main;
            if (cam != null && _maxDistance > 0f)
            {
                float dx = cam.transform.position.x - _anchor.position.x;
                float dz = cam.transform.position.z - _anchor.position.z;
                if (dx * dx + dz * dz > _maxDistance * _maxDistance) return;
            }
            for (int b = 0; b < _blocks.Length; b++)
            {
                var block = _blocks[b];
                Graphics.DrawMeshInstanced(_mesh, 0, _material, block, block.Length,
                    null, _shadows, false, gameObject.layer);
            }
        }

        private void OnDisable()
        {
            // DrawMeshInstanced non lascia oggetti in scena: disabilitare il
            // gruppo LOD e' sufficiente a rimuovere i dettagli dal frame.
        }
    }
}

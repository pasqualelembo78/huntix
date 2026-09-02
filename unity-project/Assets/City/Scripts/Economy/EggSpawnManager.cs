using System;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Economy
{
    public class EggSpawnManager : MonoBehaviour
    {
        public static EggSpawnManager Instance;

        private const int MAX_EGGS = 30;
        private const float MIN_SPAWN_DIST = 8f;
        private const float EGG_RADIUS = 100f;

        private readonly List<GameObject> eggs = new List<GameObject>();
        private Transform player;
        private Vector3 lastCenter;
        private bool spawned;
        private System.Random rng;

        private void Awake()
        {
            Instance = this;
        }

        public void SpawnEggs(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            if (Game.Instance != null && Game.Instance.player != null) player = Game.Instance.player.transform;
            rng = new System.Random(456);

            var positions = FindSpawnPositions(env);
            if (positions.Count == 0) return;

            int count = Mathf.Min(MAX_EGGS, positions.Count);

            for (int i = 0; i < count; i++)
            {
                Vector3 pos = positions[i];
                EggController.Rarity rarity = RollRarity();
                int value = GetValue(rarity);

                var go = new GameObject("Egg_" + i);
                go.transform.SetParent(root, false);

                var egg = go.AddComponent<EggController>();
                egg.Init(pos, rarity);

                eggs.Add(go);
            }

            spawned = true;
            lastCenter = root.position;
            Debug.Log("[EggSpawnManager] Uova create: " + eggs.Count);
        }

        private List<Vector3> FindSpawnPositions(OsmCityEnvelope env)
        {
            var positions = new List<Vector3>();

            // Spawn near roads
            foreach (var road in env.roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                string hw = road.highway ?? "";
                if (hw == "motorway" || hw == "trunk") continue;

                for (int i = 0; i < road.points.Length; i += 3)
                {
                    if (rng.NextDouble() > 0.15) continue;

                    Vector3 pt = Local(road.points[i]);
                    // Offset from road center
                    Vector3 offset = new Vector3(
                        (float)(rng.NextDouble() - 0.5) * 6f,
                        0f,
                        (float)(rng.NextDouble() - 0.5) * 6f
                    );
                    Vector3 pos = pt + offset;
                    pos.y = 0.3f;

                    // Check min distance from other eggs
                    bool tooClose = false;
                    foreach (var existing in positions)
                    {
                        if (Vector3.Distance(existing, pos) < MIN_SPAWN_DIST)
                        {
                            tooClose = true;
                            break;
                        }
                    }
                    if (!tooClose)
                        positions.Add(pos);

                    if (positions.Count >= MAX_EGGS * 2) break;
                }
                if (positions.Count >= MAX_EGGS * 2) break;
            }

            // Shuffle
            for (int i = positions.Count - 1; i > 0; i--)
            {
                int j = rng.Next(i + 1);
                var tmp = positions[i];
                positions[i] = positions[j];
                positions[j] = tmp;
            }

            return positions;
        }

        private EggController.Rarity RollRarity()
        {
            double roll = rng.NextDouble();
            if (roll < 0.02) return EggController.Rarity.Legendary;
            if (roll < 0.10) return EggController.Rarity.Rare;
            if (roll < 0.30) return EggController.Rarity.Uncommon;
            return EggController.Rarity.Common;
        }

        private int GetValue(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return 2;
                case EggController.Rarity.Uncommon: return 5;
                case EggController.Rarity.Rare: return 15;
                case EggController.Rarity.Legendary: return 50;
                default: return 2;
            }
        }

        private void Update()
        {
            if (!spawned || player == null) return;

            // Performance: disable distant eggs
            Vector3 pp = player.position;
            for (int i = 0; i < eggs.Count; i++)
            {
                if (eggs[i] == null) continue;
                float d = Vector3.Distance(eggs[i].transform.position, pp);
                eggs[i].SetActive(d < 60f);
            }
        }

        public void RemoveEgg(GameObject egg)
        {
            eggs.Remove(egg);
        }

        private struct EggCandidate
        {
            public Vector3 pos;
            public EggController.EggType type;
        }

        /// <summary>
        /// Uova per il mondo chunked: 5-6 per chunk, distribuite su tutte le
        /// superfici disponibili (strade, parchi, boschi, alberi, edifici,
        /// acqua, sabbia, fango, ecc.). Deterministiche sul seed del chunk.
        /// </summary>
        public void SpawnEggsInChunk(Transform root, TileGeoDoc geo,
            Func<GeoLL, Vector3> toLocal, Rect bounds, int chunkSeed)
        {
            if (root == null || geo == null || toLocal == null) return;

            rng = new System.Random(chunkSeed);
            if (player == null)
                if (Game.Instance != null && Game.Instance.player != null)
                    player = Game.Instance.player.transform;

            int maxEggs = 5 + (rng.Next() % 2);
            var candidates = new List<EggCandidate>();

            // ── 1) Strade ───────────────────────────────────────
            if (geo.roads != null)
            {
                foreach (var road in geo.roads)
                {
                    if (road == null || road.pts == null || road.pts.Length < 2) continue;
                    string hw = road.hw ?? "";
                    if (hw == "motorway" || hw == "trunk") continue;
                    for (int i = 0; i < road.pts.Length; i += 3)
                    {
                        if (rng.NextDouble() > 0.15) continue;
                        Vector3 local = SafeToLocal(toLocal, road.pts[i]);
                        if (!bounds.Contains(new Vector2(local.x, local.z))) continue;
                        local += new Vector3((float)(rng.NextDouble() - 0.5) * 4f, 0f,
                                             (float)(rng.NextDouble() - 0.5) * 4f);
                        local.y = 0.3f;
                        candidates.Add(new EggCandidate { pos = local, type = EggController.EggType.Strada });
                    }
                }
            }

            // ── 2) Parchi / terreni (tutti i kd dal geo) ────────
            if (geo.parks != null)
            {
                foreach (var park in geo.parks)
                {
                    if (park == null || park.poly == null || park.poly.Length < 3) continue;
                    EggController.EggType ptype = KdToEggType(park.kd);
                    for (int tries = 0; tries < 3; tries++)
                    {
                        Vector3 pt = RandomPointInPolygon(park.poly, toLocal);
                        if (!bounds.Contains(new Vector2(pt.x, pt.z))) continue;
                        pt.y = 0.3f;
                        candidates.Add(new EggCandidate { pos = pt, type = ptype });
                    }
                }
            }

            // ── 3) Alberi singoli ───────────────────────────────
            if (geo.trees != null)
            {
                foreach (var tree in geo.trees)
                {
                    if (rng.NextDouble() > 0.2) continue;
                    Vector3 local = SafeToLocal(toLocal, tree);
                    if (!bounds.Contains(new Vector2(local.x, local.z))) continue;
                    local.y = 0.3f;
                    candidates.Add(new EggCandidate { pos = local, type = EggController.EggType.Albero });
                }
            }

            // ── 4) Edifici (punti vicini) ───────────────────────
            if (geo.buildings != null)
            {
                foreach (var b in geo.buildings)
                {
                    if (b == null || b.c == null || b.c.Length < 2) continue;
                    if (rng.NextDouble() > 0.1) continue;
                    var geoPt = new GeoLL { a = b.c[0], o = b.c[1] };
                    Vector3 local = SafeToLocal(toLocal, geoPt);
                    if (!bounds.Contains(new Vector2(local.x, local.z))) continue;
                    local += new Vector3((float)(rng.NextDouble() - 0.5) * 6f, 0f,
                                         (float)(rng.NextDouble() - 0.5) * 6f);
                    local.y = 0.3f;
                    candidates.Add(new EggCandidate { pos = local, type = EggController.EggType.Edificio });
                }
            }

            // ── 5) Terreno libero nel bbox (se pochi candidati) ──
            if (candidates.Count < maxEggs)
            {
                var fallbackTypes = new[] {
                    EggController.EggType.Terra, EggController.EggType.Aria,
                    EggController.EggType.Breccia
                };
                for (int i = 0; i < maxEggs; i++)
                {
                    float x = bounds.xMin + (float)rng.NextDouble() * bounds.width;
                    float z = bounds.yMin + (float)rng.NextDouble() * bounds.height;
                    var t = fallbackTypes[rng.Next(fallbackTypes.Length)];
                    candidates.Add(new EggCandidate { pos = new Vector3(x, 0.3f, z), type = t });
                }
            }

            // ── Shuffle + piazza ────────────────────────────────
            for (int i = candidates.Count - 1; i > 0; i--)
            {
                int j = rng.Next(i + 1);
                var tmp = candidates[i];
                candidates[i] = candidates[j];
                candidates[j] = tmp;
            }

            int placed = 0;
            foreach (var c in candidates)
            {
                if (placed >= maxEggs) break;
                try
                {
                    var go = new GameObject("Egg_" + c.type + "_" + placed);
                    go.transform.SetParent(root, false);
                    var egg = go.AddComponent<EggController>();
                    egg.Init(root.TransformPoint(c.pos), RollRarity(), c.type);
                }
                catch (System.Exception ex)
                {
                    UnityEngine.Debug.LogWarning("[EggSpawnManager] egg create failed: " + ex);
                }
                placed++;
            }
        }

        private static EggController.EggType KdToEggType(string kd)
        {
            switch (kd)
            {
                case "wood": case "forest": return EggController.EggType.Bosco;
                case "park": case "garden": return EggController.EggType.Parco;
                case "water": return EggController.EggType.Acqua;
                case "wetland": case "marsh": return EggController.EggType.Fango;
                case "sand": case "beach": return EggController.EggType.Sabbia;
                case "scrub": case "grassland": return EggController.EggType.Breccia;
                case "farmland": case "meadow": case "vineyard": case "orchard":
                    return EggController.EggType.Terra;
                case "grass": return EggController.EggType.Parco;
                case "residential": case "commercial": case "industrial":
                case "retail": case "construction": return EggController.EggType.Edificio;
                case "cemetery": return EggController.EggType.Terra;
                case "golf_course": case "playground": return EggController.EggType.Parco;
                default: return EggController.EggType.Terra;
            }
        }

        private Vector3 SafeToLocal(Func<GeoLL, Vector3> toLocal, GeoLL pt)
        {
            try { return toLocal(pt); }
            catch { return Vector3.zero; }
        }

        private Vector3 RandomPointInPolygon(GeoLL[] poly, Func<GeoLL, Vector3> toLocal)
        {
            // bounding box del poligono
            double latMin = double.MaxValue, latMax = double.MinValue;
            double lonMin = double.MaxValue, lonMax = double.MinValue;
            foreach (var p in poly)
            {
                if (p.a < latMin) latMin = p.a;
                if (p.a > latMax) latMax = p.a;
                if (p.o < lonMin) lonMin = p.o;
                if (p.o > lonMax) lonMax = p.o;
            }
            // prova punti casuali dentro il bbox finche' ne trova uno dentro il poligono
            for (int i = 0; i < 10; i++)
            {
                double lat = latMin + rng.NextDouble() * (latMax - latMin);
                double lon = lonMin + rng.NextDouble() * (lonMax - lonMin);
                if (PointInPolygon(lat, lon, poly))
                    return toLocal(new GeoLL { a = lat, o = lon });
            }
            // fallback: centroide
            double cLat = (latMin + latMax) * 0.5;
            double cLon = (lonMin + lonMax) * 0.5;
            return toLocal(new GeoLL { a = cLat, o = cLon });
        }

        private static bool PointInPolygon(double lat, double lon, GeoLL[] poly)
        {
            bool inside = false;
            for (int i = 0, j = poly.Length - 1; i < poly.Length; j = i++)
            {
                if ((poly[i].a > lat) != (poly[j].a > lat) &&
                    lon < (poly[j].o - poly[i].o) * (lat - poly[i].a) / (poly[j].a - poly[i].a) + poly[i].o)
                    inside = !inside;
            }
            return inside;
        }

        private static Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        public void DespawnAll()
        {
            foreach (var go in eggs)
                if (go != null) Destroy(go);
            eggs.Clear();
            spawned = false;
        }
    }
}

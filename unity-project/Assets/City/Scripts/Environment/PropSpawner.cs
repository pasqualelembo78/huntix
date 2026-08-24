using System;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Environment
{
    /// <summary>
    /// Arredo urbano interattivo deterministico per chunk (stesso schema di
    /// NPCPopulator): panchine/fontanelle/cestini campionati lungo i lati dei
    /// marciapiedi + POI commerciali ricavati dai nomi/tipo degli edifici OSM
    /// (bar, farmacie, banche/ATM). Tutto tappabile via TouchInputHandler.
    /// </summary>
    public static class PropSpawner
    {
        private const float Y_SIDEWALK = 0.12f;
        private const float SIDEWALK_W = 1.8f;
        private const int MaxPoisPerChunk = 6;

        private static readonly HashSet<string> UrbanHw = new HashSet<string>
        {
            "primary", "secondary", "tertiary", "residential",
            "unclassified", "living_street", "service", "pedestrian"
        };

        public static void Populate(ChunkData chunk,
            Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (chunk.geo == null || chunk.root == null) return;
            int seed = chunk.index.x * 92837111 ^ chunk.index.y * 689287499;
            var rng = new System.Random(seed);

            var root = new GameObject("Props");
            root.transform.SetParent(chunk.root.transform, false);

            int n = 0;
            n += SpawnStreetProps(chunk, rng, root.transform, toLocal);
            n += SpawnBuildingPois(chunk, rng, root.transform, toLocal, bounds);
            if (n > 0)
                OsmDiag.Log("[Props] " + chunk.key + ": " + n + " oggetti");
        }

        // ── panchine / fontanelle / cestini ─────────────────────

        private static int SpawnStreetProps(ChunkData chunk,
            System.Random rng, Transform parent, Func<GeoLL, Vector3> toLocal)
        {
            var roads = chunk.geo.roads;
            if (roads == null || roads.Length == 0) return 0;

            var points = new List<Vector3>();
            var dirs = new List<Vector3>();

            foreach (var road in roads)
            {
                if (road?.pts == null || road.pts.Length < 2) continue;
                string hw = road.hw ?? "";
                if (!UrbanHw.Contains(hw)) continue;

                for (int i = 0; i < road.pts.Length - 1; i++)
                {
                    GeoLL A = road.pts[i], B = road.pts[i + 1];
                    Vector3 a = toLocal(A);
                    Vector3 b = toLocal(B);
                    Vector3 seg = b - a;
                    seg.y = 0f;
                    float len = seg.magnitude;
                    if (len < 8f) continue;
                    Vector3 dir = seg / len;
                    // normale del lato sinistro della strada
                    Vector3 side = new Vector3(-dir.z, 0f, dir.x);

                    for (float t = 15f; t < len - 6f; t += 32f)
                    {
                        Vector3 p = a + dir * t + side * (SIDEWALK_W + 0.55f);
                        p.y = Y_SIDEWALK;
                        points.Add(p);
                        dirs.Add(-side);
                    }
                }
            }
            if (points.Count == 0) return 0;

            int placed = 0;
            int benches = rng.NextDouble() < 0.6 ? 1 + rng.Next(2) : 0;
            int bins = rng.NextDouble() < 0.65 ? 1 + rng.Next(2) : 0;
            int fountains = rng.NextDouble() < 0.18 ? 1 : 0;
            placed += Take(points, dirs, rng, benches, parent, BuildBench);
            placed += Take(points, dirs, rng, bins, parent, BuildBin);
            placed += Take(points, dirs, rng, fountains, parent, BuildFountain);
            return placed;
        }

        private static int Take(List<Vector3> pts, List<Vector3> dirs,
            System.Random rng, int count, Transform parent,
            Func<Transform, Vector3, Quaternion, GameObject> builder)
        {
            int placed = 0;
            for (int k = 0; k < count && pts.Count > 0; k++)
            {
                int idx = rng.Next(pts.Count);
                Vector3 fwd = dirs[idx];
                fwd.y = 0f;
                if (fwd.sqrMagnitude < 0.001f) fwd = Vector3.forward;
                GameObject go = builder(parent, pts[idx],
                    Quaternion.LookRotation(fwd, Vector3.up));
                placed++;
                pts.RemoveAt(idx);
                dirs.RemoveAt(idx);
            }
            return placed;
        }

        // ── POI dagli edifici OSM ───────────────────────────────

        private static int SpawnBuildingPois(ChunkData chunk,
            System.Random rng, Transform parent,
            Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            var buildings = chunk.geo.buildings;
            if (buildings == null) return 0;

            int placed = 0;
            foreach (var b in buildings)
            {
                if (placed >= MaxPoisPerChunk) break;
                if (b?.c == null || b.c.Length < 2 || b.d == null || b.d.Length < 2)
                    continue;

                InteractableProp.Kind? kind = Classify(b.t, b.nm, rng);
                if (kind == null) continue;

                try
                {
                    var ll = new GeoLL { a = b.c[0], o = b.c[1] };
                    Vector3 p = toLocal(ll);
                    if (!bounds.Contains(new Vector2(p.x, p.z))) continue;

                    // davanti all'edificio lungo l'asse profondita', ruotato
                    Vector3 front = Quaternion.Euler(0f, b.r, 0f) *
                        Vector3.forward * (b.d[1] * 0.5f + 1.5f);
                    Vector3 pos = p + front;
                    pos.y = 0.05f;
                    Quaternion facing = Quaternion.Euler(0f, b.r + 180f, 0f);

                    BuildPoi(parent, pos, facing, kind.Value);
                    placed++;
                }
                catch (Exception)
                {
                    // un dato anomalo non costa il chunk
                }
            }
            return placed;
        }

        private static InteractableProp.Kind? Classify(string type, string nm,
            System.Random rng)
        {
            string s = (nm ?? "").ToLowerInvariant();
            if (s.Contains("farmacia") || s.Contains("parafarm") ||
                s.Contains("pharmacy"))
                return InteractableProp.Kind.Pharmacy;
            if (s.Contains("banca") || s.Contains("bancomat") ||
                s.Contains("atm") || s.Contains("poste") || type == "bank")
                return InteractableProp.Kind.Atm;
            if (s.StartsWith("bar") || s.Contains(" bar ") ||
                s.Contains("caff") || s.Contains("cafe") ||
                s.Contains("pub") || s.Contains("ristorant") ||
                s.Contains("pizzeria") || s.Contains("gelater") ||
                s.Contains("trattoria"))
                return InteractableProp.Kind.Cafe;
            return null;
        }

        // ── costruttori dei prop ────────────────────────────────

        private static GameObject BuildBench(Transform parent, Vector3 pos,
            Quaternion rot)
        {
            var go = Base("Bench", pos, rot, false);
            AddBox(go, new Vector3(1.7f, 0.95f, 0.65f), new Vector3(0f, 0.47f, 0f));

            Part(go, new Vector3(1.7f, 0.09f, 0.55f),
                new Vector3(0f, 0.45f, 0f),
                new Color(0.55f, 0.38f, 0.22f));
            Part(go, new Vector3(1.7f, 0.5f, 0.07f),
                new Vector3(0f, 0.75f, -0.26f),
                new Color(0.55f, 0.38f, 0.22f));
            Tag(go, InteractableProp.Kind.Bench, "\ud83e\ude91 Panchina", "Siediti");
            Attach(parent, go);
            return go;
        }

        private static GameObject BuildBin(Transform parent, Vector3 pos,
            Quaternion rot)
        {
            var go = Base("Bin", pos, rot, true);
            var body = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            UnityEngine.Object.Destroy(body.GetComponent<Collider>());
            body.transform.SetParent(go.transform, false);
            body.transform.localScale = new Vector3(0.55f, 0.45f, 0.55f);
            body.transform.localPosition = new Vector3(0f, 0.45f, 0f);
            Paint(body, new Color(0.16f, 0.35f, 0.25f));
            Tag(go, InteractableProp.Kind.Bin,
                "\ud83d\uddd1\ufe0f Cestino", "Butta i rifiuti");
            Attach(parent, go);
            return go;
        }

        private static GameObject BuildFountain(Transform parent, Vector3 pos,
            Quaternion rot)
        {
            var go = Base("Fountain", pos, rot, true);
            var base_ = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            UnityEngine.Object.Destroy(base_.GetComponent<Collider>());
            base_.transform.SetParent(go.transform, false);
            base_.transform.localScale = new Vector3(1f, 0.4f, 1f);
            base_.transform.localPosition = new Vector3(0f, 0.4f, 0f);
            Paint(base_, new Color(0.6f, 0.62f, 0.65f));

            var water = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            UnityEngine.Object.Destroy(water.GetComponent<Collider>());
            water.transform.SetParent(go.transform, false);
            water.transform.localScale = new Vector3(0.78f, 0.36f, 0.78f);
            water.transform.localPosition = new Vector3(0f, 0.42f, 0f);
            Paint(water, new Color(0.25f, 0.55f, 0.9f, 0.9f));

            Tag(go, InteractableProp.Kind.Fountain,
                "\u26fd Fontanella", "Bevi (+8\u26a1)");
            Attach(parent, go);
            return go;
        }

        private static void BuildPoi(Transform parent, Vector3 pos,
            Quaternion rot, InteractableProp.Kind kind)
        {
            string name = kind == InteractableProp.Kind.Cafe ? "Cafe"
                : kind == InteractableProp.Kind.Pharmacy ? "Pharmacy" : "Atm";
            var go = Base(name, pos, rot, true);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            UnityEngine.Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(go.transform, false);
            pole.transform.localScale = new Vector3(0.12f, 0.9f, 0.12f);
            pole.transform.localPosition = new Vector3(0f, 0.9f, 0f);

            Color c;
            string title, action;
            if (kind == InteractableProp.Kind.Cafe)
            {
                c = new Color(0.95f, 0.62f, 0.15f);
                title = "\u2615 Bar";
                action = "Caff\u00e8 2\u20ac \u00b7 Aperitivo 5\u20ac";
            }
            else if (kind == InteractableProp.Kind.Pharmacy)
            {
                c = new Color(0.85f, 0.2f, 0.25f);
                title = "\ud83d\udc8a Farmacia";
                action = "Kit 8\u20ac (+25\u26a1)";
            }
            else
            {
                c = new Color(0.2f, 0.45f, 0.9f);
                title = "\ud83c\udfe6 ATM";
                action = "Gestisci denaro";
            }

            var disc = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            UnityEngine.Object.Destroy(disc.GetComponent<Collider>());
            disc.transform.SetParent(go.transform, false);
            disc.transform.localScale = new Vector3(0.7f, 0.05f, 0.7f);
            disc.transform.localPosition = new Vector3(0f, 1.85f, 0f);
            Paint(disc, c);
            Paint(pole, new Color(0.75f, 0.76f, 0.78f));

            AddBox(go, new Vector3(1.6f, 2.4f, 1.6f), new Vector3(0f, 1.2f, 0f));
            Tag(go, kind, title, action);

            // vetrina rompibile davanti al cartello (solo bar/farmacia)
            if (kind == InteractableProp.Kind.Cafe ||
                kind == InteractableProp.Kind.Pharmacy)
            {
                var win = GameObject.CreatePrimitive(PrimitiveType.Cube);
                UnityEngine.Object.Destroy(win.GetComponent<Collider>());
                win.transform.SetParent(go.transform, false);
                win.transform.localPosition = new Vector3(0f, 1.15f, 0.55f);
                win.transform.localScale = new Vector3(1.7f, 1.35f, 0.07f);
                var bw = win.AddComponent<City.Environment.BreakableWindow>();
                go.GetComponent<InteractableProp>().Window = bw;
            }

            Attach(parent, go);
        }

        // ── helper ──────────────────────────────────────────────

        private static GameObject Base(string name, Vector3 pos, Quaternion rot,
            bool triggerCollider)
        {
            var go = new GameObject(name);
            go.transform.position = pos;
            go.transform.rotation = rot;
            var col = go.AddComponent<BoxCollider>();
            col.isTrigger = triggerCollider;
            col.size = triggerCollider
                ? new Vector3(1.5f, 2.2f, 1.5f)
                : new Vector3(1.7f, 0.95f, 0.65f);
            col.center = triggerCollider
                ? new Vector3(0f, 1.1f, 0f)
                : new Vector3(0f, 0.47f, 0f);
            return go;
        }

        private static void AddBox(GameObject go, Vector3 size, Vector3 center)
        {
            var col = go.GetComponent<BoxCollider>();
            if (col == null) col = go.AddComponent<BoxCollider>();
            col.isTrigger = false;
            col.size = size;
            col.center = center;
        }

        private static void Tag(GameObject go, InteractableProp.Kind kind,
            string title, string action)
        {
            var ip = go.AddComponent<InteractableProp>();
            ip.kind = kind;
            ip.title = title;
            ip.action = action;
        }

        private static void Attach(Transform parent, GameObject go)
        {
            // true: preserva la posizione mondiale gia' impostata
            if (parent != null) go.transform.SetParent(parent, true);
        }

        private static GameObject Part(GameObject parentGo, Vector3 scale,
            Vector3 localPos, Color c)
        {
            var cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
            UnityEngine.Object.Destroy(cube.GetComponent<Collider>());
            cube.transform.SetParent(parentGo.transform, false);
            cube.transform.localScale = scale;
            cube.transform.localPosition = localPos;
            Paint(cube, c);
            return cube;
        }

        private static void Paint(GameObject go, Color c)
        {
            var r = go.GetComponent<Renderer>();
            if (r == null) return;
            var mats = r.sharedMaterials;
            for (int i = 0; i < mats.Length; i++)
            {
                Material m = new Material(Shader.Find("Sprites/Default"));
                m.color = c;
                mats[i] = m;
            }
            r.sharedMaterials = mats;
        }
    }
}

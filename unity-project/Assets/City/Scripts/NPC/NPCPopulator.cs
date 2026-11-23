using System.Collections;
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
    ///
    /// Audit 23/11: il vecchio loop costruiva 30 pedoni in un solo frame
    /// (355 ms misurati fra 12:20:50.650 e 12:20:51.005). Ora la popolazione
    /// e' una coroutine budgetata: al massimo 2 pedoni e 8 ms per frame, con
    /// un yield esplicito fra i blocchi. Stesso seed, stessi id, stessa skin.
    /// </summary>
    public static class NPCPopulator
    {
        private const int MaxNpcPerChunk = 30;
        private const int MaxNpcPerFrame = 2;
        private const long FrameBudgetMs = 8;
        private const float MinPathLen = 18f;
        private const float Y_SIDEWALK = 0.12f;   // = RoadRenderer.Y_SIDEWALK
        private const float GAP = 0.15f;          // = RoadRenderer gap asfalto/marciapiede
        private const float SIDEWALK_W = 1.8f;    // = RoadRenderer.SIDEWALK_W

        private static GameObject _charPrefab;

        // Ogni cittadino usa una skin DIVERSa (nessun PNG uguale): 24 varianti
        // maschili + 24 femminili = 48 look distincti per colore di pelle,
        // camicia, pantaloni e capelli. La selezione e' deterministica sull'id
        // (stesso chunk -> stessi pedoni) e varia anche l'altezza (scale) e
        // la statura cosi' ogni PNG ha le sue particolarita' fisiche.
        private static readonly string[] CitizenMaleSkins =
        {
            "citizenM01","citizenM02","citizenM03","citizenM04","citizenM05",
            "citizenM06","citizenM07","citizenM08","citizenM09","citizenM10",
            "citizenM11","citizenM12","citizenM13","citizenM14","citizenM15",
            "citizenM16","citizenM17","citizenM18","citizenM19","citizenM20",
            "citizenM21","citizenM22","citizenM23","citizenM24",
        };
        private static readonly string[] CitizenFemaleSkins =
        {
            "citizenF01","citizenF02","citizenF03","citizenF04","citizenF05",
            "citizenF06","citizenF07","citizenF08","citizenF09","citizenF10",
            "citizenF11","citizenF12","citizenF13","citizenF14","citizenF15",
            "citizenF16","citizenF17","citizenF18","citizenF19","citizenF20",
            "citizenF21","citizenF22","citizenF23","citizenF24",
        };

        /// <summary>Compatibilita' con chiamanti fuori dal build dei chunk:
        /// svuota la coroutine in sincrono. Il build usa PopulateRoutine.</summary>
        public static void Populate(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            var routine = PopulateRoutine(chunk, toLocal, bounds);
            while (routine.MoveNext()) { }
        }

        /// <summary>Popolazione a blocchi. Ogni yield torna al main loop del
        /// builder, che a sua volta restituisce il frame: il costo dei 30 NPC
        /// non puo' piu' concentrarsi in un solo frame.</summary>
        public static IEnumerator PopulateRoutine(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (chunk.geo?.roads == null || chunk.geo.roads.Length == 0) yield break;
            LoadCharacterPrefab();
            if (_charPrefab == null) yield break;

            // seed deterministico: stesso chunk -> stessi pedoni
            int seed = chunk.index.x * 73856093 ^ chunk.index.y * 19349663;
            var rng = new System.Random(seed);

            var paths = BuildSidewalkPaths(chunk.geo.roads, toLocal, bounds, rng);
            if (paths.Count == 0) yield break;

            chunk.npcsGo = new GameObject("NPC");
            chunk.npcsGo.transform.SetParent(chunk.root.transform, false);

            // piu' pedoni dello stesso chunk possono condividere un percorso:
            // dopo ogni blocco si cede il frame, cosi' le strade restano animate
            int count = MaxNpcPerChunk;
            int placed = 0;
            int inFrame = 0;
            var frameClock = new System.Diagnostics.Stopwatch();
            var totalClock = System.Diagnostics.Stopwatch.StartNew();

            for (int i = 0; i < count; i++)
            {
                if (inFrame >= MaxNpcPerFrame ||
                    frameClock.ElapsedMilliseconds >= FrameBudgetMs)
                {
                    frameClock.Reset();
                    inFrame = 0;
                    yield return null;
                }
                // UnloadFar/WorldOrigin possono aver già' distrutto la radice
                // mentre la coda e' in corso: niente NPC in un chunk fantasma.
                if (chunk.root == null || chunk.npcsGo == null) yield break;
                if (!frameClock.IsRunning) frameClock.Start();
                try
                {
                    SpawnOne(chunk, paths, rng, i);
                    placed++;
                }
                catch (System.Exception e)
                {
                    UnityBridge.LogToAndroid("NPCPopulator",
                        "NPC saltato nel " + chunk.key + ": " + e.ToString());
                }
                inFrame++;
            }
            OsmDiag.Log("[NPCPopulator] " + chunk.key + ": " + placed + "/" + count +
                " pedoni paths=" + paths.Count +
                " rootY=" + chunk.root.transform.position.y.ToString("F2") +
                " bounds=" + bounds +
                " ms=" + totalClock.ElapsedMilliseconds.ToString("F0"));
        }

        private static void SpawnOne(ChunkData chunk, List<List<Vector3>> paths,
            System.Random rng, int i)
        {
            using (City.Diagnostics.PerfProbe.Measure("spawn"))
            {
                var path = paths[rng.Next(paths.Count)];
                GameObject go = Object.Instantiate(_charPrefab, chunk.npcsGo.transform);
                go.name = "NPC_" + chunk.key + "_" + i;
                go.tag = "Untagged";

                // statura/presenza fisica unica per ogni PNG (0.42..0.50)
                float h = 0.455f + (float)(StableHashScaled(i) - 0.5f) * 0.055f;
                go.transform.localScale = Vector3.one * h;

                // i collider importati dal FBX restano spenti: il corpo
                // solido lo aggiunge NPCController (capsula)
                foreach (var c in go.GetComponentsInChildren<Collider>())
                    if (c != null) c.enabled = false;

                string npcId = "npc_" + chunk.index.x + "_" + chunk.index.y + "_" + i;
                ApplySkin(go, PickCitizenSkin(npcId, i));

                var npc = go.AddComponent<NPCController>();
                // offset di partenza sul percorso condiviso: evita che i
                // pedoni impilati partano tutti dal primo waypoint
                npc.Init(path.ToArray(), rng, npcId, i);
            }
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
                    var elevs = new List<float>();
                    for (int i = 0; i < road.pts.Length; i++)
                    {
                        pts.Add(toLocal(road.pts[i]));
                        elevs.Add(TileElevation.HeightAt(
                            road.pts[i].a, road.pts[i].o));
                    }

                    var path = SampleWithOffset(pts, elevs, offset * side, rng);
                    float len = PathLength(path);
                    if (len < MinPathLen) continue;
                    if (!RectIntersects(path, bounds)) continue;
                    // L'offset perpendicolare (SampleWithOffset) proietta i
                    // punti fuori dal rettangolo del chunk quando la strada
                    // passa vicino al bordo: il waypoint finisce in una zona
                    // non costruita e l'NPC cammina nel vuoto (log "Hak
                    // localPos=..."). Ritaglia i punti dentro i bounds.
                    ClipToBounds(path, bounds);
                    if (PathLength(path) < MinPathLen) continue;
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
            List<float> elevs, float offset, System.Random rng)
        {
            var result = new List<Vector3>();
            if (line.Count < 2) return result;
            float step = 8f + (float)rng.NextDouble() * 5f;
            float carry = 0f;

            for (int i = 1; i < line.Count; i++)
            {
                Vector3 a = line[i - 1], b = line[i];
                float elevA = elevs[i - 1], elevB = elevs[i];
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
                    float f = segLen > 0.001f ? t / segLen : 0f;
                    p.y = Mathf.Lerp(elevA, elevB, f) + Y_SIDEWALK;
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

        // Ritaglia i waypoint dentro il rettangolo del chunk (con un piccolo
        // margine dal bordo, visto che l'NPC cammina sul marciapiede lungo la
        // strada e il bordo stesso e' contiguo al chunk vicino). I punti gia'
        // dentro restano identici; i proiettati fuori dall'offset vengono
        // riportati sul limite, cosi' il percorso resta in zona costruita.
        private static void ClipToBounds(List<Vector3> p, Rect r)
        {
            const float inset = 1f;
            float xMin = r.xMin + inset, xMax = r.xMax - inset;
            float zMin = r.yMin + inset, zMax = r.yMax - inset;
            for (int i = 0; i < p.Count; i++)
            {
                Vector3 v = p[i];
                v.x = Mathf.Clamp(v.x, xMin, xMax);
                v.z = Mathf.Clamp(v.z, zMin, zMax);
                p[i] = v;
            }
        }

        // Sceglie una skin deterministica per un pedone: alterna maschile/
        // femminile e ruota dentro le 24 varianti di quel genere in base
        // all'hash dell'id stabile -> lo stesso NPC conserva lo stesso look
        // tra una sessione e l'altra. Variazione locale (offset) per evitare
        // che i pedoni vicini nello stesso chunk condividano subito la skin.
        private static string PickCitizenSkin(string npcId, int i)
        {
            string[] pool = StableHash(npcId + "|g") % 2 == 0
                ? CitizenMaleSkins : CitizenFemaleSkins;
            int baseIdx = StableHash(npcId) % pool.Length;
            int idx = (baseIdx + i) % pool.Length;
            return pool[idx];
        }

        // scala in [0,1] deterministica da un intero (per statura)
        private static float StableHashScaled(int v)
        {
            uint h = 2166136261u;
            unchecked { h ^= (uint)v; h *= 16777619u; }
            return (h % 10000) / 10000f;
        }

        private static int StableHash(string s)
        {
            uint h = 2166136261u;
            for (int i = 0; i < s.Length; i++)
            {
                h ^= s[i];
                h *= 16777619u;
            }
            return (int)(h % int.MaxValue);
        }

        // Stessa tecnica dello storico NPCSpawnManager: sostituisce le
        // texture dei materiali degli SkinnedMeshRenderer con la skin scelta.
        //
        // MEMORIA: prima nasceva un materiale NUOVO per OGNI renderer di OGNI
        // pedone (30 pedoni/chunk x 49 chunk = migliaia di materiali, con
        // altrettante Shader.Find e nessun batching). Le skin sono solo 48:
        // si crea UN materiale per skin (lazy) e lo si condivide fra tutti i
        // pedoni con quel look. Zero differenza visiva, molta memoria e draw
        // call in meno durante lo streaming.
        private static readonly Dictionary<string, Material> SkinMaterials =
            new Dictionary<string, Material>();

        private static void ApplySkin(GameObject go, string skinName)
        {
            var mat = GetSkinMaterial(skinName);
            if (mat == null) return;
            var renderers = go.GetComponentsInChildren<SkinnedMeshRenderer>();
            foreach (var mr in renderers)
            {
                var mats = mr.sharedMaterials;
                for (int m = 0; m < mats.Length; m++) mats[m] = mat;
                mr.sharedMaterials = mats;
            }
        }

        private static Material GetSkinMaterial(string skinName)
        {
            Material cached;
            if (SkinMaterials.TryGetValue(skinName, out cached) && cached != null)
                return cached;

            var skinTex = Resources.Load<Texture2D>("Characters/Skins/" + skinName);
            if (skinTex == null) return null;

            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return null;

            var mat = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline"))
            {
                mat.SetColor("_BaseColor", Color.white);
                mat.SetTexture("_BaseMap", skinTex);
            }
            else
            {
                mat.SetColor("_Color", Color.white);
                mat.SetTexture("_MainTex", skinTex);
            }
            SkinMaterials[skinName] = mat;
            return mat;
        }
    }
}

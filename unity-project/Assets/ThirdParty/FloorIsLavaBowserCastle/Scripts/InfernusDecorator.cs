using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

namespace FloorIsLava
{
    /// <summary>
    /// Decori retro-2D (kit PVGames Inferno) come billboard emissivi + luci
    /// animate nella scena del minigioco scala-inferno.
    ///
    /// Si auto-installa quando viene caricata una scena col nome che contiene
    /// "Inferno"; se gli ancoraggi della scena mancano fa un fallback morbido
    /// (niente crash). I decori sono puramente visivi: nessun collider, i
    /// billboard ruotano sempre verso la camera, le fiamme animano le strisce
    /// Lightsource del kit (3 frame) e qualche luce point tremola a caldo.
    /// </summary>
    [DisallowMultipleComponent]
    public class InfernusDecorator : MonoBehaviour
    {
        private const string KitRoot = "InfernusKit";
        private static bool _autoBooted;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void AutoBoot()
        {
            try
            {
                string name = SceneManager.GetActiveScene().name;
                if (name == null || name.IndexOf("Inferno", StringComparison.OrdinalIgnoreCase) < 0) return;
                if (_autoBooted) return;
                _autoBooted = true;
                if (FindObjectOfType<InfernusDecorator>() == null)
                    new GameObject("InfernusDecorator").AddComponent<InfernusDecorator>();
            }
            catch (Exception e)
            {
                Debug.LogWarning("[InfernusDecorator] boot scena fallito: " + e.Message);
            }
        }

        private readonly Dictionary<string, Texture2D> _tex = new Dictionary<string, Texture2D>();
        private readonly List<FlameFx> _flames = new List<FlameFx>();
        private readonly List<Flicker> _flicker = new List<Flicker>();

        private Material _matProp;
        private Material _matFlame;
        private Camera _cam;

        // Altezza in metri per ogni tag (senza variante).
        private static readonly Dictionary<string, float> PropSize = new Dictionary<string, float>
        {
            { "rock", 0.42f }, { "pile", 0.30f }, { "skull", 0.20f }, { "bones", 0.36f },
            { "dragonbones", 2.4f }, { "grave", 0.55f }, { "candles", 0.5f },
            { "candelabra", 0.95f }, { "brasero", 0.5f }, { "burnercolumn", 1.7f },
            { "throne", 1.9f }, { "altar", 1.5f }, { "spire", 4.0f }, { "giant", 3.6f },
            { "hand", 1.9f }, { "wallsword", 1.1f }, { "wallspear", 1.2f }, { "wallshield", 0.9f },
            { "wallcandles", 0.6f }, { "walllantern", 0.7f },
        };

        private static Texture2D[] _flameStrips;

        private void Awake()
        {
            if (FindObjectsOfType<InfernusDecorator>().Length > 1)
            {
                Destroy(gameObject);
                return;
            }
            _cam = Camera.main;
        }

        private void Start()
        {
            try { Build(); }
            catch (Exception e)
            {
                Debug.LogWarning("[InfernusDecorator] setup decori fallito (modalita' cosmetico): " + e.Message);
            }
        }

        private void Build()
        {
            _matProp = MakeMaterial(additive: false);
            _matFlame = MakeMaterial(additive: true);

            Transform[] castle = BestAnchors(new[] { "Big Castle", "Castle" });
            Transform[] doors = BestAnchors(new[] { "Big Door", "Big Arch" });
            Transform ground = BestAnchorByName("Floor");
            if (ground == null) ground = BestAnchors(new[] { "Playable Level", "Area 1", "Area 2", "Area 3" })[0];
            Transform exit = BestAnchor(new[] { "Exit", "Area Final" });
            Transform bridge = BestAnchor(new[] { "Big Bridge" });
            Bounds groundBounds = BoundsOf(ground);

            if (ground != null) ScatterGround(groundBounds);

            if (castle.Length > 0)
            {
                Bounds cb = BoundsOf(castle[0]);
                SpawnSetPiece("spire", cb.center + new Vector3(-cb.extents.x - 1.4f, 0, cb.extents.z * 0.4f), groundBounds);
                SpawnSetPiece("giant", cb.center + new Vector3(cb.extents.x + 1.2f, 0, -cb.extents.z * 0.6f), groundBounds);
                SpawnSetPiece("hand", cb.center + new Vector3(-cb.extents.x * 0.4f, 0, cb.extents.z + 1.2f), groundBounds);
                AttachWallProps(cb);
                PlaceBrazier(cb.center + new Vector3(cb.extents.x * 0.35f, 0, cb.extents.z + 0.6f), groundBounds);
                PlaceBrazier(cb.center + new Vector3(-cb.extents.x * 0.35f, 0, cb.extents.z + 0.6f), groundBounds);
            }

            if (doors.Length > 0)
            {
                Bounds db = BoundsOf(doors[0]);
                float side = Mathf.Max(0.8f, Mathf.Max(db.extents.x, db.extents.z) + 0.7f);
                PlaceTorch(db.center + new Vector3(side, 0, 0), groundBounds);
                PlaceTorch(db.center + new Vector3(-side, 0, 0), groundBounds);
            }

            if (bridge != null)
            {
                Bounds bb = BoundsOf(bridge);
                Vector3 dir = bb.center - groundBounds.center;
                dir.y = 0;
                if (dir.sqrMagnitude < 0.001f) dir = bb.size.x >= bb.size.z ? Vector3.right : Vector3.forward;
                dir.Normalize();
                Vector3 side = Vector3.Cross(Vector3.up, dir).normalized;
                SpawnSetPiece("dragonbones", bb.center - dir * (Mathf.Max(1f, bb.size.magnitude * 0.3f)) + side * 2.4f, groundBounds);
                SpawnSetPiece("dragonbones", bb.center - dir * (Mathf.Max(1f, bb.size.magnitude * 0.3f)) - side * 2.4f, groundBounds);
            }

            if (exit != null)
            {
                Bounds eb = BoundsOf(exit);
                SpawnSetPiece("throne", eb.center + new Vector3(0, 0, Mathf.Max(1.3f, eb.extents.z + 1.4f)), groundBounds);
            }
            SpawnSetPiece("altar", groundBounds.center, groundBounds);
        }

        // ─── utili ───

        private static Texture2D[] FlameStrips()
        {
            if (_flameStrips != null) return _flameStrips;
            var list = new List<Texture2D>();
            for (int i = 1; i <= 9; i++)
            {
                var t = Resources.Load<Texture2D>(KitRoot + "/Fire/lightsource_" + i);
                if (t != null) list.Add(t);
            }
            _flameStrips = list.ToArray();
            return _flameStrips;
        }

        private Texture2D GetTex(string id)
        {
            Texture2D t;
            if (_tex.TryGetValue(id, out t)) return t;
            t = Resources.Load<Texture2D>(KitRoot + "/" + FolderOf(id) + "/" + id);
            _tex[id] = t;
            return t;
        }

        /// <summary>Prima variante disponibile per un tag (es. "rock" → "rock_3").</summary>
        private string VariantOf(string tag)
        {
            for (int i = 1; i <= 20; i++)
            {
                string cand = tag + "_" + i;
                if (GetTex(cand) != null) return cand;
            }
            return tag;
        }

        private static string FolderOf(string id)
        {
            string baseName = id;
            int u = id.IndexOf('_');
            if (u > 0) baseName = id.Substring(0, u);
            switch (baseName)
            {
                case "rock": return "Rocks";
                case "pile": return "Piles";
                case "skull": return "Skulls";
                case "bones":
                case "dragonbones": return "Bones";
                case "grave": return "Graves";
                case "candles":
                case "candelabra":
                case "brasero":
                case "burnercolumn": return "Fire";
                case "throne": return "Throne";
                case "altar": return "Altar";
                case "spire": return "Spires";
                case "giant": return "Giants";
                case "hand": return "Hands";
                default: return "Wall";
            }
        }

        private static Material MakeMaterial(bool additive)
        {
            Shader s = Shader.Find("Universal Render Pipeline/Unlit");
            if (s == null) s = Shader.Find("Unlit/Texture");
            if (s == null) s = Shader.Find("Sprites/Default");
            var m = new Material(s);
            m.SetOverrideTag("RenderType", "Transparent");
            m.SetInt("_Surface", 1);           // surface type transparent (URP)
            m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            m.SetInt("_Blend", additive ? 2 : 0); // additive / alpha (URP)
            if (additive) m.EnableKeyword("_ALPHAPREMULTIPLY_ON");
            m.SetInt("_SrcBlend", additive ? (int)BlendMode.One : (int)BlendMode.SrcAlpha);
            m.SetInt("_DstBlend", additive ? (int)BlendMode.One : (int)BlendMode.OneMinusSrcAlpha);
            m.SetInt("_ZWrite", 0);
            m.SetInt("_Cull", 0);
            m.renderQueue = additive ? 3100 : 3000;
            return m;
        }

        private Transform BestAnchor(string[] names)
        {
            Transform[] t = BestAnchors(names);
            return t.Length > 0 ? t[0] : null;
        }

        /// <summary>Ancoraggio per nome esatto (primo trovato), senza sort.</summary>
        private static Transform BestAnchorByName(string name)
        {
            var go = GameObject.Find(name);
            return go != null ? go.transform : null;
        }

        private static Transform[] BestAnchors(string[] names)
        {
            var pool = new List<Transform>();
            foreach (string n in names)
            {
                var found = GameObject.Find(n);
                if (found != null) pool.Add(found.transform);
            }
            pool.Sort(delegate (Transform a, Transform b)
            {
                if (a == null) return 1;
                if (b == null) return -1;
                return BoundsVolume(BoundsOf(b)).CompareTo(BoundsVolume(BoundsOf(a)));
            });
            return pool.ToArray();
        }

        private static float BoundsVolume(Bounds b) { return b.size.x * b.size.y * b.size.z; }

        private static Bounds BoundsOf(Transform t)
        {
            Bounds b = new Bounds(Vector3.zero, Vector3.zero);
            bool any = false;
            if (t == null) return b;
            foreach (var r in t.GetComponentsInChildren<Renderer>(true))
            {
                if (!any) { b = r.bounds; any = true; } else b.Encapsulate(r.bounds);
            }
            foreach (var c in t.GetComponentsInChildren<Collider>(true))
            {
                if (!any) { b = c.bounds; any = true; } else b.Encapsulate(c.bounds);
            }
            if (!any) b = new Bounds(t.position, Vector3.one);
            return b;
        }

        // ─── scattering a terra ───

        private void ScatterGround(Bounds ground)
        {
            float area = ground.size.x * ground.size.z;
            int count = Mathf.Clamp(Mathf.RoundToInt(area / 7f), 12, 34);
            var weights = new[]
            {
                new { tag = "rock", w = 30f }, new { tag = "pile", w = 10f },
                new { tag = "skull", w = 10f }, new { tag = "bones", w = 8f },
                new { tag = "candles", w = 6f }, new { tag = "grave", w = 4f },
            };
            System.Random rnd = new System.Random(1337);
            for (int i = 0; i < count; i++)
            {
                float total = 0f;
                for (int k = 0; k < weights.Length; k++) total += weights[k].w;
                float roll = (float)rnd.NextDouble() * total;
                string tag = "rock";
                for (int k = 0; k < weights.Length; k++)
                {
                    roll -= weights[k].w;
                    if (roll <= 0f) { tag = weights[k].tag; break; }
                }
                float rx = ((float)rnd.NextDouble() * 2f - 1f) * ground.extents.x * 0.45f;
                float rz = ((float)rnd.NextDouble() * 2f - 1f) * ground.extents.z * 0.45f;
                Vector3 pos = ground.center + new Vector3(rx, 0f, rz);
                pos.y = ground.max.y;
                string id = VariantOf(tag);
                SpawnBillboard(id, pos, PropSizeOf(id), (float)rnd.NextDouble() * 360f);
            }
        }

        private static float PropSizeOf(string id)
        {
            string tag = id;
            int u = id.IndexOf('_');
            if (u > 0) tag = id.Substring(0, u);
            float s;
            return PropSize.TryGetValue(tag, out s) ? s : 0.5f;
        }

        private void SpawnSetPiece(string tag, Vector3 wanted, Bounds ground)
        {
            float y = (ground != null && ground.size.y > 0) ? ground.max.y : wanted.y;
            Vector3 pos = new Vector3(wanted.x, y, wanted.z);
            string id = VariantOf(tag);
            SpawnBillboard(id, pos, PropSizeOf(id), UnityEngine.Random.Range(0f, 360f));
        }

        private void PlaceBrazier(Vector3 pos, Bounds ground)
        {
            float groundY = ground.size.y > 0 ? ground.max.y : pos.y;
            pos.y = groundY;
            SpawnBillboard("brasero_1", pos, PropSizeOf("brasero_1"), UnityEngine.Random.Range(0f, 360f));
            AddFlame(pos + new Vector3(0, 0.4f, 0), 0.7f);
            AddFlickerLight(pos + new Vector3(0, 0.8f, 0), 4.2f, 1.5f);
        }

        private void PlaceTorch(Vector3 pos, Bounds ground)
        {
            float groundY = ground.size.y > 0 ? ground.max.y : pos.y;
            pos.y = groundY;
            SpawnBillboard("burnercolumn_1", pos, PropSizeOf("burnercolumn_1"), UnityEngine.Random.Range(0f, 360f));
            AddFlame(pos + new Vector3(0, 1.15f, 0), 1.0f);
            AddFlickerLight(pos + new Vector3(0, 1.5f, 0), 5.5f, 1.7f);
        }

        private void AttachWallProps(Bounds castle)
        {
            Vector3 c = castle.center;
            Vector3 ext = castle.extents;
            AttachWallRow("wallsword", c + new Vector3(ext.x * 0.55f, ext.y * 0.25f, ext.z + 0.15f));
            AttachWallRow("wallspear", c + new Vector3(-ext.x * 0.55f, ext.y * 0.25f, ext.z + 0.15f));
            AttachWallRow("wallshield", c + new Vector3(0f, ext.y * 0.25f, -ext.z - 0.15f));
            Vector3 candlePos = c + new Vector3(0f, ext.y * 0.35f, ext.z + 0.2f);
            SpawnBillboard("wallcandles_1", candlePos, PropSizeOf("wallcandles_1"), 0f);
            AddFlickerLight(candlePos + new Vector3(0f, 0.05f, 0f), 3.4f, 0.9f);
        }

        private void AttachWallRow(string tag, Vector3 pos)
        {
            SpawnBillboard(VariantOf(tag), pos, PropSizeOf(tag), 0f);
        }

        // ─── billboard ───

        private GameObject SpawnBillboard(string id, Vector3 pos, float height, float yaw)
        {
            Texture2D t = GetTex(id);
            if (t == null) return null;
            return NewProp(id, pos, t, height, yaw);
        }

        private GameObject NewProp(string id, Vector3 pos, Texture2D tex, float height, float yaw)
        {
            var go = new GameObject(id + " prop");
            go.transform.SetParent(transform);
            go.transform.position = pos;
            float w = Mathf.Max(0.02f, height * ((float)tex.width / tex.height));
            go.transform.localScale = new Vector3(w, height, 1f);
            go.transform.Rotate(0f, yaw, 0f, Space.World);
            var mf = go.AddComponent<MeshFilter>();
            mf.sharedMesh = BuildQuad();
            var mr = go.AddComponent<MeshRenderer>();
            mr.sharedMaterial = _matProp;
            var mpb = new MaterialPropertyBlock();
            mpb.SetTexture("_BaseMap", tex);
            mr.SetPropertyBlock(mpb);
            var billboard = go.AddComponent<GoBillboard>();
            billboard.owner = this;
            return go;
        }

        private static Mesh BuildQuad()
        {
            var mesh = new Mesh();
            mesh.vertices = new[]
            {
                new Vector3(-0.5f, 0f, 0f), new Vector3(0.5f, 0f, 0f),
                new Vector3(0.5f, 1f, 0f), new Vector3(-0.5f, 1f, 0f)
            };
            mesh.uv = new[]
            {
                new Vector2(0f, 0f), new Vector2(1f, 0f),
                new Vector2(1f, 1f), new Vector2(0f, 1f)
            };
            mesh.triangles = new[] { 0, 2, 1, 0, 3, 2 };
            mesh.RecalculateNormals();
            mesh.name = "InfernusQuad";
            return mesh;
        }

        // ─── fiamme animate ───

        private void AddFlame(Vector3 pos, float height)
        {
            var strips = FlameStrips();
            if (strips == null || strips.Length == 0) return;
            var tex = strips[Mathf.Abs((int)(pos.x * 7f + pos.z * 13f)) % strips.Length];
            int frameW = tex.width / 3;
            var f = new FlameFx
            {
                mesh = new Mesh(),
                tex = tex,
                uvBaseX = (float)frameW / tex.width,
                speed = 8f + ((pos.x + pos.z) % 3.0f),
                phase = (pos.x * 0.37f + pos.z * 0.61f) % 10f,
            };
            var go = new GameObject("flame");
            go.transform.SetParent(transform);
            go.transform.position = pos;
            go.transform.localScale = new Vector3(
                Mathf.Max(0.02f, height * ((float)frameW / tex.height)), height, 1f);
            var mf = go.AddComponent<MeshFilter>();
            mf.mesh = f.mesh;
            var mr = go.AddComponent<MeshRenderer>();
            mr.sharedMaterial = _matFlame;
            var mpb = new MaterialPropertyBlock();
            mpb.SetTexture("_BaseMap", tex);
            mr.SetPropertyBlock(mpb);
            f.renderer = mr;
            var billboard = go.AddComponent<GoBillboard>();
            billboard.owner = this;
            WriteFlameUV(f, 0);
            _flames.Add(f);
        }

        private void WriteFlameUV(FlameFx f, int frame)
        {
            float w = f.uvBaseX;
            var uvs = new[]
            {
                new Vector2(w * frame, 0f), new Vector2(w * (frame + 1), 0f),
                new Vector2(w * (frame + 1), 1f), new Vector2(w * frame, 1f)
            };
            var m = f.mesh;
            m.Clear();
            m.vertices = new[]
            {
                new Vector3(-0.5f, 0f, 0f), new Vector3(0.5f, 0f, 0f),
                new Vector3(0.5f, 1f, 0f), new Vector3(-0.5f, 1f, 0f)
            };
            m.uv = uvs;
            m.triangles = new[] { 0, 2, 1, 0, 3, 2 };
            m.RecalculateNormals();
        }

        private void AddFlickerLight(Vector3 pos, float range, float intensity)
        {
            var go = new GameObject("flame light");
            go.transform.SetParent(transform);
            go.transform.position = pos;
            var light = go.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.62f, 0.28f);
            light.intensity = intensity;
            light.range = range;
            light.renderMode = LightRenderMode.ForcePixel;
            _flicker.Add(new Flicker { light = light, baseIntensity = intensity, seed = _flicker.Count });
        }

        private void Update()
        {
            if (_cam == null) _cam = Camera.main;

            float t = Time.time;
            for (int i = 0; i < _flames.Count; i++)
            {
                FlameFx f = _flames[i];
                int frame = Mathf.RoundToInt((t * f.speed + f.phase) % 3f);
                if (frame != f.lastFrame)
                {
                    f.lastFrame = frame;
                    if (f.mesh != null) WriteFlameUV(f, frame);
                }
            }
            for (int i = 0; i < _flicker.Count; i++)
            {
                Flicker fl = _flicker[i];
                if (fl.light == null) { _flicker.RemoveAt(i); i--; continue; }
                float n = Mathf.PerlinNoise(t * 8f, fl.seed * 17.31f) * 0.22f;
                fl.light.intensity = Mathf.Max(0.3f, fl.baseIntensity * (0.88f + n));
            }
        }

        public Camera Cam { get { return _cam; } }

        /// <summary>
        /// Probe di validazione (usabile anche in edit mode): risolve gli
        /// ancoraggi della scena con la stessa logica usata a runtime e
        /// riporta per ciascuno nome, bounds e volume. Ritorna una stringa
        /// pronta per il log.
        /// </summary>
        public static string ProbeAnchors()
        {
            var sb = new System.Text.StringBuilder();
            sb.AppendLine("== INFERNUS ANCHORS ==");
            var names = new Dictionary<string, string[]>
            {
                { "castle", new[] { "Big Castle", "Castle" } },
                { "doors", new[] { "Big Door", "Big Arch" } },
                { "ground", new[] { "Floor", "Playable Level", "Area 1", "Area 2", "Area 3" } },
                { "exit", new[] { "Exit", "Area Final" } },
                { "bridge", new[] { "Big Bridge" } },
            };
            foreach (var kv in names)
            {
                Transform[] t = BestAnchors(kv.Value);
                if (t.Length == 0) { sb.AppendLine(kv.Key + ": NONE"); continue; }
                foreach (Transform tr in t)
                {
                    if (tr == null) continue;
                    Bounds b = BoundsOf(tr);
                    sb.AppendLine(kv.Key + ": " + tr.name + " center=" + b.center +
                        " size=" + b.size);
                }
            }
            return sb.ToString();
        }

        private class FlameFx
        {
            public Mesh mesh;
            public Texture2D tex;
            public MeshRenderer renderer;
            public float uvBaseX;
            public float speed;
            public float phase;
            public int lastFrame = -1;
        }

        private class Flicker
        {
            public Light light;
            public float baseIntensity;
            public int seed;
        }
    }

    /// <summary>Billboard cilindrico: guarda la camera ma resta verticale.</summary>
    public class GoBillboard : MonoBehaviour
    {
        public InfernusDecorator owner;
        private Camera cam;

        private void LateUpdate()
        {
            cam = cam != null ? cam : (owner != null ? owner.Cam : Camera.main);
            if (cam == null) return;
            Vector3 flat = cam.transform.position - transform.position;
            flat.y = 0f;
            if (flat.sqrMagnitude < 0.0001f) return;
            transform.rotation = Quaternion.LookRotation(flat, Vector3.up);
        }
    }
}
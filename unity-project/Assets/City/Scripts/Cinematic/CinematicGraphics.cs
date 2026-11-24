// Trasformazione visiva opt-in di Huntix: applica le 13 texture PBR CC0
// importate (Poly Haven) alle superfici della citta', aggiunge lampioni e
// arredo urbano dai kit Kenney CC0 gia' presenti e intensifica l'atmosfera.
// Ogni effetto resta disattivato di default e si commuta dal menu hamburger.
using System.Collections.Generic;
using UnityEngine;
using City.Diagnostics;
using City.Environment;
using City.OSM;

namespace City.Cinematic
{
    public static class CinematicGraphics
    {
        private static Huntix.Core.CinematicAssetRegistry _registry;
        private static readonly HashSet<Material> _facadeDone = new HashSet<Material>();
        private static readonly Dictionary<Material, MatSnapshot> _saved =
            new Dictionary<Material, MatSnapshot>();
        private static Material _lampMat;
        private static Material _lampGlow;

        /// <summary>Letto da DayNightManager: nebbia e luce notturna piu' ricche.</summary>
        public static bool AtmosphereEnabled { get; private set; }

        // Copia dello stato originale: lo switch deve essere davvero
        // reversibile, anche dopo che i materiali sono stati arricchiti.
        private sealed class MatSnapshot
        {
            public Texture baseMap, mainTex, bump, occlusion, gloss;
            public float bumpScale, occlusionStrength, smoothness, metallic, glossScale;
        }

        // Material.GetTexture() logga un errore se la proprieta' non esiste
        // nello shader (es. Sprites/Default non ha _OcclusionMap): controlare
        // sempre HasProperty prima di leggere.
        private static Texture GetTex(Material m, string prop)
        {
            return m != null && m.HasProperty(prop) ? m.GetTexture(prop) : null;
        }

        private static bool HasTex(Material m, string prop)
        {
            return GetTex(m, prop) != null;
        }

        private static void Save(Material m)
        {
            if (m == null || _saved.ContainsKey(m)) return;
            _saved[m] = new MatSnapshot
            {
                baseMap = GetTex(m, "_BaseMap"),
                mainTex = GetTex(m, "_MainTex"),
                bump = GetTex(m, "_BumpMap"),
                occlusion = GetTex(m, "_OcclusionMap"),
                gloss = GetTex(m, "_MetallicGlossMap"),
                bumpScale = m.HasProperty("_BumpScale") ? m.GetFloat("_BumpScale") : 0f,
                occlusionStrength = m.HasProperty("_OcclusionStrength") ? m.GetFloat("_OcclusionStrength") : 0f,
                smoothness = m.HasProperty("_Smoothness") ? m.GetFloat("_Smoothness") : 0f,
                metallic = m.HasProperty("_Metallic") ? m.GetFloat("_Metallic") : 0f,
                glossScale = m.HasProperty("_GlossMapScale") ? m.GetFloat("_GlossMapScale") : 0f,
            };
        }

        private static void Restore(Material m)
        {
            MatSnapshot s;
            if (m == null || !_saved.TryGetValue(m, out s)) return;
            if (m.HasProperty("_BaseMap")) m.SetTexture("_BaseMap", s.baseMap);
            if (m.HasProperty("_MainTex")) m.SetTexture("_MainTex", s.mainTex);
            if (m.HasProperty("_BumpMap")) m.SetTexture("_BumpMap", s.bump);
            if (m.HasProperty("_OcclusionMap")) m.SetTexture("_OcclusionMap", s.occlusion);
            if (m.HasProperty("_MetallicGlossMap")) m.SetTexture("_MetallicGlossMap", s.gloss);
            if (m.HasProperty("_BumpScale")) m.SetFloat("_BumpScale", s.bumpScale);
            if (m.HasProperty("_OcclusionStrength")) m.SetFloat("_OcclusionStrength", s.occlusionStrength);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", s.smoothness);
            if (m.HasProperty("_Metallic")) m.SetFloat("_Metallic", s.metallic);
            if (m.HasProperty("_GlossMapScale")) m.SetFloat("_GlossMapScale", s.glossScale);
            m.DisableKeyword("_NORMALMAP");
            m.DisableKeyword("_OCCLUSIONMAP");
            m.DisableKeyword("_METALLICSPECGLOSSMAP");
            _saved.Remove(m);
        }

        private static Huntix.Core.CinematicAssetRegistry Registry
        {
            get
            {
                if (_registry == null && Huntix.Core.GameManager.Instance != null)
                    _registry = Huntix.Core.GameManager.Instance.cinematicRegistry;
                return _registry;
            }
        }

        public static void Register()
        {
            CinematicFeatureBridge.Register("asphalt-pbr", on => RefreshRoads());
            CinematicFeatureBridge.Register("pavement-pbr", on => RefreshSidewalks());
            CinematicFeatureBridge.Register("facade-pbr", on =>
            {
                if (!on)
                {
                    foreach (var m in new List<Material>(_facadeDone)) Restore(m);
                    _facadeDone.Clear();
                    return;
                }
                RefreshFacades();
            });
            CinematicFeatureBridge.Register("street-lamps", on => { if (!on) ClearLamps(); });
            CinematicFeatureBridge.Register("night-atmosphere", SetAtmosphere);
            CinematicFeatureBridge.Register("vehicle-detail", on => RefreshVehicle(on));
            CinematicFeatureBridge.Register("raw-cc0-assets", on =>
            {
                if (!on) ClearRawProps();
            });
        }

        private static Color _savedFogColor;
        private static float _savedAmbientIntensity;
        private static bool _atmosphereSnapshot;

        public static void SetAtmosphere(bool on)
        {
            if (on && !AtmosphereEnabled)
            {
                _savedFogColor = RenderSettings.fogColor;
                _savedAmbientIntensity = RenderSettings.ambientIntensity;
                _atmosphereSnapshot = true;
            }
            AtmosphereEnabled = on;
            if (!on && _atmosphereSnapshot)
            {
                RenderSettings.fogColor = _savedFogColor;
                RenderSettings.ambientIntensity = _savedAmbientIntensity;
                _atmosphereSnapshot = false;
                if (RenderSettings.fog)
                    RenderSettings.fogDensity = DayNightManager.IsNight ? 0.0012f : 0.0009f;
                UpdateLampLights(false);
                return;
            }
            if (RenderSettings.fog)
            {
                RenderSettings.fogDensity = on
                    ? (DayNightManager.IsNight ? 0.0022f : 0.0014f)
                    : (DayNightManager.IsNight ? 0.0012f : 0.0009f);
            }
        }

        private static void SetTex(Material m, string prop, Texture2D tex, Vector2 scale)
        {
            if (m == null || tex == null || !m.HasProperty(prop)) return;
            m.SetTexture(prop, tex);
            m.SetTextureScale(prop, scale);
            m.SetTextureOffset(prop, Vector2.zero);
        }

        /// <summary>Asfalto PBR reale: base color + normale + occlusione + roughness.</summary>
        public static void ApplyRoad(Material m)
        {
            var r = Registry;
            if (m == null || r == null) return;
            if (!CinematicFeatureBridge.Enabled("asphalt-pbr"))
            {
                Restore(m);
                return;
            }
            ApplyAsphalt(m, r.asphaltDiffuse, r.asphaltNormal, r.asphaltAO,
                         r.asphaltRoughness, false);
        }

        /// <summary>
        /// Variante usurata: stessa firma PBR, texture diverse, assegnata a una
        /// porzione delle strade per evitare il manto perfettamente uniforme.
        /// </summary>
        public static void ApplyWornRoad(Material m)
        {
            var r = Registry;
            if (m == null || r == null) return;
            if (!CinematicFeatureBridge.Enabled("asphalt-pbr"))
            {
                Restore(m);
                return;
            }
            if (r.asphaltWornDiffuse == null && r.asphaltWornNormal == null)
            {
                ApplyRoad(m);
                return;
            }
            ApplyAsphalt(m, r.asphaltWornDiffuse != null ? r.asphaltWornDiffuse : r.asphaltDiffuse,
                         r.asphaltWornNormal != null ? r.asphaltWornNormal : r.asphaltNormal,
                         r.asphaltAO, r.asphaltRoughness, true);
        }

        private static void ApplyAsphalt(Material m, Texture2D diffuse, Texture2D normal,
                                         Texture2D ao, Texture2D roughness, bool worn)
        {
            Save(m);
            var scale = new Vector2(2f, 2f);
            SetTex(m, "_BaseMap", diffuse, scale);
            SetTex(m, "_MainTex", diffuse, scale);
            SetTex(m, "_BumpMap", normal, scale);
            SetTex(m, "_OcclusionMap", ao, scale);
            // URP Lit non ha uno slot "roughness": la mappa viene legata come
            // MetallicGloss (alpha=smoothness), con metallic forzato a 0.
            // La scala 0.34 converte il valore costante in smoothness 0.34.
            SetTex(m, "_MetallicGlossMap", roughness, scale);
            if (m.HasProperty("_Metallic")) m.SetFloat("_Metallic", 0f);
            if (m.HasProperty("_GlossMapScale")) m.SetFloat("_GlossMapScale", 0.34f);
            if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", Color.white);
            if (m.HasProperty("_Color")) m.SetColor("_Color", Color.white);
            if (m.HasProperty("_BumpScale")) m.SetFloat("_BumpScale", worn ? 1.05f : 0.8f);
            if (m.HasProperty("_OcclusionStrength")) m.SetFloat("_OcclusionStrength", 0.9f);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.34f);
            Enable(m, "_NORMALMAP");
            Enable(m, "_OCCLUSIONMAP");
            Enable(m, "_METALLICSPECGLOSSMAP");
        }

        /// <summary>Erba PBR per le aree verdi, incluso nello switch superfici.</summary>
        public static void ApplyPark(Material m)
        {
            var r = Registry;
            if (m == null || r == null) return;
            if (!CinematicFeatureBridge.Enabled("pavement-pbr"))
            {
                Restore(m);
                return;
            }
            Save(m);
            var scale = new Vector2(4f, 4f);
            SetTex(m, "_BaseMap", r.grassDiffuse, scale);
            SetTex(m, "_MainTex", r.grassDiffuse, scale);
            if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", Color.white);
            if (m.HasProperty("_Color")) m.SetColor("_Color", Color.white);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.05f);
        }

        /// <summary>Pavimentazione PBR reale per i marciapiedi.</summary>
        public static void ApplySidewalk(Material m)
        {
            var r = Registry;
            if (m == null || r == null) return;
            if (!CinematicFeatureBridge.Enabled("pavement-pbr"))
            {
                Restore(m);
                return;
            }
            Save(m);
            var scale = new Vector2(3f, 3f);
            SetTex(m, "_BaseMap", r.pavementDiffuse, scale);
            SetTex(m, "_MainTex", r.pavementDiffuse, scale);
            SetTex(m, "_BumpMap", r.pavementNormal, scale);
            if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", Color.white);
            if (m.HasProperty("_Color")) m.SetColor("_Color", Color.white);
            if (m.HasProperty("_BumpScale")) m.SetFloat("_BumpScale", 0.65f);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.22f);
            Enable(m, "_NORMALMAP");
        }

        /// <summary>
        /// Aggiunge rilievo in mattoni solo alle facciate piatte (kit Kenney),
        /// lasciando intatte quelle Quaternius che hanno gia' le proprie texture.
        /// </summary>
        public static void ApplyFacade(GameObject inst)
        {
            var r = Registry;
            if (inst == null || r == null || !CinematicFeatureBridge.Enabled("facade-pbr")) return;
            var rs = inst.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rs.Length; i++)
                ApplyFacadeMaterials(rs[i], r.brickWallDiffuse, r.brickWallNormal);
        }

        private static void ApplyFacadeMaterials(Renderer rr, Texture2D diffuse, Texture2D normal)
        {
            if (rr == null || normal == null) return;
            var ms = rr.sharedMaterials;
            for (int j = 0; j < ms.Length; j++)
            {
                var m = ms[j];
                if (m == null || _facadeDone.Contains(m)) continue;
                // Solo materiali 3D con supporto normal map: esclude sprite,
                // UI, testo e unlit, che non hanno _BumpMap e genererebbero
                // errori "doesn't have a texture property".
                if (!m.HasProperty("_BumpMap")) continue;
                var sh = m.shader;
                if (sh != null && sh.name != null &&
                    (sh.name.StartsWith("Sprites/") || sh.name.StartsWith("UI/") ||
                     sh.name.StartsWith("TextMeshPro/") || sh.name.StartsWith("Particles/")))
                    continue;
                if (HasTex(m, "_BaseMap") || HasTex(m, "_MainTex")) continue;
                if (!_facadeDone.Add(m)) continue;
                Save(m);
                var scale = new Vector2(2f, 2f);
                if (diffuse != null)
                {
                    SetTex(m, "_BaseMap", diffuse, scale);
                    SetTex(m, "_MainTex", diffuse, scale);
                }
                SetTex(m, "_BumpMap", normal, scale);
                if (m.HasProperty("_BumpScale")) m.SetFloat("_BumpScale", 0.28f);
                Enable(m, "_NORMALMAP");
            }
        }

        private static void RefreshFacades()
        {
            var r = Registry;
            if (r == null) return;
            var rs = Object.FindObjectsOfType<Renderer>();
            for (int i = 0; i < rs.Length; i++)
                ApplyFacadeMaterials(rs[i], r.brickWallDiffuse, r.brickWallNormal);
        }

        /// <summary>Vernice piu' profonda e leggero rilievo sui veicoli Kenney。</summary>
        public static void ApplyVehicle(Material m)
        {
            var r = Registry;
            if (m == null || r == null) return;
            if (!CinematicFeatureBridge.Enabled("vehicle-detail"))
            {
                Restore(m);
                return;
            }
            Save(m);
            SetTex(m, "_BumpMap", r.concreteNormal, new Vector2(3f, 3f));
            if (m.HasProperty("_BumpScale")) m.SetFloat("_BumpScale", 0.12f);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.55f);
            Enable(m, "_NORMALMAP");
        }

        private static void Enable(Material m, string keyword)
        {
            if (m != null) m.EnableKeyword(keyword);
        }

        private static void RefreshRoads()
        {
            var mgr = Object.FindObjectOfType<ChunkManager>();
            if (mgr == null) return;
            ApplyRoad(mgr.SharedRoadMaterial);
            ApplyWornRoad(mgr.SharedWornRoadMaterial);
        }

        private static void RefreshSidewalks()
        {
            var mgr = Object.FindObjectOfType<ChunkManager>();
            if (mgr == null) return;
            ApplySidewalk(mgr.SharedSidewalkMaterial);
            City.OSM.VegetationPlacer.RefreshParkMaterial();
        }

        private static void RefreshVehicle(bool on)
        {
            // Il materiale condiviso dei veicoli viene creato lazy: il toggle
            // lo raggiunge al primo veicolo successivo; qui forziamo il refresh
            // se esiste gia' in scena.
            var rs = Object.FindObjectsOfType<Renderer>();
            var seen = new HashSet<Material>();
            for (int i = 0; i < rs.Length; i++)
            {
                var r = rs[i];
                if (r == null || !r.gameObject.name.StartsWith("Vehicle")) continue;
                var ms = r.sharedMaterials;
                for (int j = 0; j < ms.Length; j++)
                    if (ms[j] != null && seen.Add(ms[j])) ApplyVehicle(ms[j]);
            }
        }

        // ── lampioni e arredo CC0 ────────────────────────────────────

        private static readonly List<GameObject> _lamps = new List<GameObject>();
        private static readonly List<GameObject> _rawProps = new List<GameObject>();

        /// <summary>
        /// Modelli Kenney CC0 grezzi importati in RawCc0: lampioni, coni,
        /// barriere e segnali distribuiti lungo le strade. Sono decorativi:
        /// i collider vengono disattivati per non alterare la fisica di guida.
        /// </summary>
        public static void PlaceRawProps(Transform parent, List<Vector3> pts, List<Vector3> dirs)
        {
            var r = Registry;
            if (r == null || r.rawProps == null || r.rawProps.Length == 0) return;
            if (!CinematicFeatureBridge.Enabled("raw-cc0-assets")) return;
            int stride = Mathf.Max(6, 14 - Mathf.Min(8, r.rawProps.Length * 2));
            for (int i = 0; i < pts.Count; i += stride)
            {
                var prefab = r.rawProps[(i / stride) % r.rawProps.Length];
                if (prefab == null) continue;
                GameObject go = Object.Instantiate(prefab);
                go.name = "CC0-" + prefab.name;
                go.transform.SetParent(parent, false);
                go.transform.position = pts[i];
                go.transform.rotation = Quaternion.LookRotation(
                    dirs != null && i < dirs.Count ? dirs[i] : Vector3.forward, Vector3.up);
                // Scala originale Kenney: i modelli sono in metri.
                ApplyRawMaterial(go, r.rawPropsColormap);
                var cols = go.GetComponentsInChildren<Collider>(true);
                for (int c = 0; c < cols.Length; c++) cols[c].enabled = false;
                _rawProps.Add(go);
            }
        }

        private static void ApplyRawMaterial(GameObject go, Texture2D colormap)
        {
            if (colormap == null) return;
            var rs = go.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rs.Length; i++)
            {
                var rr = rs[i];
                if (rr == null) continue;
                var ms = rr.sharedMaterials;
                for (int j = 0; j < ms.Length; j++)
                {
                    var m = ms[j];
                    if (m == null) continue;
                    if (m.HasProperty("_BaseMap")) m.SetTexture("_BaseMap", colormap);
                    else if (m.HasProperty("_MainTex")) m.SetTexture("_MainTex", colormap);
                }
            }
        }

        private static void ClearRawProps()
        {
            for (int i = 0; i < _rawProps.Count; i++)
                if (_rawProps[i] != null) Object.Destroy(_rawProps[i]);
            _rawProps.Clear();
        }

        public static void PlaceLamps(Transform parent, List<Vector3> pts, List<Vector3> dirs)
        {
            if (pts == null || !CinematicFeatureBridge.Enabled("street-lamps")) return;
            for (int i = 0; i < pts.Count; i += 4)
            {
                var go = BuildLamp(parent, pts[i], dirs != null && i < dirs.Count ? dirs[i] : Vector3.forward);
                if (go != null) _lamps.Add(go);
            }
        }

        public static void UpdateLampLights(bool night)
        {
            for (int i = 0; i < _lamps.Count; i++)
            {
                if (_lamps[i] == null) continue;
                var ls = _lamps[i].GetComponentsInChildren<Light>(true);
                for (int j = 0; j < ls.Length; j++)
                    ls[j].intensity = night ? 1.6f : 0f;
            }
            if (_lampGlow != null)
                _lampGlow.color = night ? new Color(1f, 0.86f, 0.55f)
                                        : new Color(0.45f, 0.44f, 0.40f);
        }

        private static GameObject BuildLamp(Transform parent, Vector3 at, Vector3 fwd)
        {
            if (_lampMat == null)
            {
                _lampMat = new Material(Shader.Find("Universal Render Pipeline/Lit") ??
                                        Shader.Find("Standard"));
                _lampMat.color = new Color(0.18f, 0.19f, 0.21f);
                _lampGlow = new Material(Shader.Find("Universal Render Pipeline/Unlit") ??
                                         Shader.Find("Unlit/Color"));
                _lampGlow.color = new Color(1f, 0.86f, 0.55f);
            }
            // Il palo usa la mappa cemento CC0: e' lo stesso materiale condiviso
            // per tutti i lampioni, quindi il costo resta una sola texture.
            var reg = Registry;
            if (reg != null && reg.concreteDiffuse != null)
            {
                SetTex(_lampMat, "_BaseMap", reg.concreteDiffuse, new Vector2(1.5f, 3f));
                SetTex(_lampMat, "_MainTex", reg.concreteDiffuse, new Vector2(1.5f, 3f));
            }
            var root = new GameObject("Lampione CC0");
            root.transform.SetParent(parent, false);
            root.transform.position = at;
            root.transform.rotation = Quaternion.LookRotation(fwd, Vector3.up);

            var post = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            post.name = "Palo";
            post.transform.SetParent(root.transform, false);
            post.transform.localPosition = new Vector3(0f, 2.3f, 0f);
            post.transform.localScale = new Vector3(0.12f, 2.3f, 0.12f);
            Object.Destroy(post.GetComponent<Collider>());
            post.GetComponent<Renderer>().sharedMaterial = _lampMat;

            var head = GameObject.CreatePrimitive(PrimitiveType.Cube);
            head.name = "Luce";
            head.transform.SetParent(root.transform, false);
            head.transform.localPosition = new Vector3(0f, 4.6f, 0f);
            head.transform.localScale = new Vector3(0.55f, 0.16f, 0.28f);
            Object.Destroy(head.GetComponent<Collider>());
            head.GetComponent<Renderer>().sharedMaterial = _lampGlow;

            // Una sola Light ogni 4 lampioni: il budget Android resta basso.
            if (_lamps.Count % 4 == 0)
            {
                var lightGo = new GameObject("PointLight");
                lightGo.transform.SetParent(root.transform, false);
                lightGo.transform.localPosition = new Vector3(0f, 4.4f, 0f);
                var l = lightGo.AddComponent<Light>();
                l.type = LightType.Point;
                l.range = 9f;
                l.intensity = DayNightManager.IsNight ? 1.6f : 0f;
                l.color = new Color(1f, 0.85f, 0.6f);
                l.shadows = LightShadows.None;
            }
            return root;
        }

        private static void ClearLamps()
        {
            for (int i = 0; i < _lamps.Count; i++)
                if (_lamps[i] != null) Object.Destroy(_lamps[i]);
            _lamps.Clear();
        }
    }
}

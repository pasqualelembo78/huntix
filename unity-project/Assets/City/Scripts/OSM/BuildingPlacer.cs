using System;
using System.Collections.Generic;
using UnityEngine;
using City.Interior;

namespace City.OSM
{
    /// <summary>
    /// Piazza gli edifici del chunk partendo dai placement record della tile
    /// (centro, impronta w/d, rotazione, tipo OSM) e istanzia i prefab Kenney
    /// con la stessa logica di CityOSMWorld.TryPlaceKenneyBuilding:
    /// misura i bounds PRIMA della rotazione, scala sull'impronta, appoggia a
    /// terra. Un solo BoxCollider per edificio (layer 8 Buildings).
    /// </summary>
    public static class BuildingPlacer
    {
        private static readonly string[] SuburbanHouses =
        {
            "building-type-a", "building-type-b", "building-type-c", "building-type-d",
            "building-type-e", "building-type-f", "building-type-g", "building-type-h",
            "building-type-i", "building-type-j", "building-type-k", "building-type-l",
            "building-type-m", "building-type-n", "building-type-o", "building-type-p",
            "building-type-q", "building-type-r", "building-type-s", "building-type-t",
        };

        private static readonly string[] CommercialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n",
        };

        private static readonly string[] IndustrialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n", "building-o",
            "building-p", "building-q", "building-r", "building-s", "building-t",
        };

        private static readonly string[] Skyscrapers =
        {
            "building-skyscraper-a", "building-skyscraper-b", "building-skyscraper-c",
            "building-skyscraper-d", "building-skyscraper-e",
        };

        // Cache statica dei prefab Kenney (fallback quando non c'è registrato
        // il CityKitAssetRegistry): una sola prima di per percorso, poi riusato
        // per tutti gli edifici che condividono il prefab.
        private static readonly Dictionary<string, GameObject> _kenneyCache =
            new Dictionary<string, GameObject>();

        private static GameObject LoadKenney(string folder, string prefabName)
        {
            string key = folder + "/" + prefabName;
            GameObject cached;
            if (_kenneyCache.TryGetValue(key, out cached)) return cached;
            var go = Resources.Load<GameObject>("Buildings/" + key);
            _kenneyCache[key] = go;
            OsmDiag.Log("[Building][Kenney] load='" + key + "' ok=" + (go != null));
            return go;
        }

        // Cache per-prefab "ha collider?" (fix performance): interrogato UNA volta
        // sul prefab, non su ogni istanza. I prefab senza collider (molti modelli
        // HQ) saltano il GetComponentsInChildren<Collider> a ogni edificio e
        // ricevono solo il BoxCollider dimensionato sui bounds reali (sotto).
        private static readonly HashSet<GameObject> _prefabWithColliders =
            new HashSet<GameObject>();
        private static readonly HashSet<GameObject> _prefabNoColliders =
            new HashSet<GameObject>();

        private static bool PrefabHasColliders(GameObject prefab)
        {
            if (_prefabWithColliders.Contains(prefab)) return true;
            if (_prefabNoColliders.Contains(prefab)) return false;
            bool has = prefab.GetComponentsInChildren<Collider>(true).Length > 0;
            (has ? _prefabWithColliders : _prefabNoColliders).Add(prefab);
            return has;
        }

        private const int BuildingLayer = 8;

        // ── Conversione runtime a URP/Lit dei materiali prefab ─────────
        // I prefab Quaternius/Kenney sono FBX con materiali EMBEDDED
        // (.fbx.meta: materialImportMode 0 / materialLocation 1): Unity li
        // importa sullo shader built-in "Standard". In URP quello shader non
        // campiona le texture e rende le facciate grigie/spente.
        // Convertiamo i MATERIALI CONDIVISI in place su URP/Lit (stessa
        // rimappatura di URPUpgradeMaterials, ma a runtime e senza toccare
        // gli asset): il materiale e' l'asset condiviso del prefab, quindi
        // convertirlo una volta corregge TUTTI gli edifici della stessa
        // facciata, subito e senza copie Material per istanza (zero GC).
        // Idempotente: i materiali gia' su URP (o gia' convertiti) vengono
        // solo marcati e saltati, nessun double-work per sessione.

        /// <summary>True se il materiale usa uno shader built-in non-URP
        /// da convertire (Standard/Legacy/Diffuse).</summary>
        private static bool NeedsURPConversion(Material m)
        {
            if (m == null || m.shader == null) return false;
            string n = m.shader.name;
            if (string.IsNullOrEmpty(n)) return false;
            if (n.StartsWith("Universal Render Pipeline", StringComparison.Ordinal) ||
                n.StartsWith("HDRP", StringComparison.Ordinal) ||
                n.StartsWith("Shader Graphs/", StringComparison.Ordinal) ||
                n.StartsWith("Sprites/", StringComparison.Ordinal) ||
                n.StartsWith("UI/", StringComparison.Ordinal) ||
                n.StartsWith("Skybox/", StringComparison.Ordinal) ||
                n.StartsWith("Particles/", StringComparison.Ordinal))
                return false;
            return true;
        }

        // Cache dei materiali gia' processati (convertiti O gia' URP):
        // il primo edificio converte, tutti gli altri con lo stesso prefab
        // solo guardano e passano.
        private static readonly HashSet<Material> _convertedMats =
            new HashSet<Material>();

        /// <summary>Converte UN materiale condiviso a URP/Lit in place.
        /// Anche se un materiale built-in compare su piu' renderer, viene
        /// ripassato una sola volta (cache per sessione).</summary>
        private static void ConvertToURP(Material m)
        {
            if (m == null || _convertedMats.Contains(m)) return;
            string legacyName = m.shader != null ? m.shader.name : "NONE";
            if (!NeedsURPConversion(m))
            {
                _convertedMats.Add(m);
                return;
            }

            Shader lit = Shader.Find("Universal Render Pipeline/Lit");
            if (lit == null)
            {
                // Se per qualche motivo manca il shader URP (torna a Standard
                // senza rompere): niente conversione, evita errori.
                _convertedMats.Add(m);
                OsmDiag.Log("[Building][Mat] URP/Lit NON trovato, lascio '" + legacyName + "'");
                return;
            }

            bool alphaTest = m.renderQueue >= 2450 && m.renderQueue < 2500;
            bool transparent = m.renderQueue >= 3000;

            Texture albedo = m.HasProperty("_MainTex") ? m.GetTexture("_MainTex") : null;
            Color color = m.HasProperty("_Color") ? m.GetColor("_Color") : Color.white;
            float metallic = m.HasProperty("_Metallic") ? m.GetFloat("_Metallic") : 0f;
            float glossiness = m.HasProperty("_Glossiness") ? m.GetFloat("_Glossiness") : 0f;
            float smoothness = m.HasProperty("_Smoothness") ? m.GetFloat("_Smoothness") : glossiness;
            Texture normals = m.HasProperty("_BumpMap") ? m.GetTexture("_BumpMap") : null;
            float bumpScale = m.HasProperty("_BumpScale") ? m.GetFloat("_BumpScale") : 1f;
            Texture emissionMap = m.HasProperty("_EmissionMap") ? m.GetTexture("_EmissionMap") : null;
            Color emissionColor = m.HasProperty("_EmissionColor")
                ? m.GetColor("_EmissionColor") : Color.black;
            float cutoff = m.HasProperty("_Cutoff") ? m.GetFloat("_Cutoff") : 0.5f;
            bool hasEmission = emissionColor.r > 0.01f || emissionColor.g > 0.01f
                            || emissionColor.b > 0.01f;

            m.shader = lit;

            m.SetColor("_BaseColor", color);
            if (albedo != null) m.SetTexture("_BaseMap", albedo);
            m.SetFloat("_Metallic", metallic);
            m.SetFloat("_Smoothness", smoothness);
            if (normals != null)
            {
                m.SetTexture("_BumpMap", normals);
                m.SetFloat("_BumpScale", bumpScale);
                m.EnableKeyword("_NORMALMAP");
            }
            if (emissionMap != null) m.SetTexture("_EmissionMap", emissionMap);
            m.SetColor("_EmissionColor", emissionColor);
            m.SetColor("_EmissiveColor", emissionColor);
            if (hasEmission)
                m.EnableKeyword("_EMISSION");
            else
                m.DisableKeyword("_EMISSION");

            // Rimappa la coda di rendering (opaco/alpha-test/trasparente),
            // identico alla conversione URP dell'editor. I valori raw delle
            // enum corrispondono a RenderQueue.{Geometry,AlphaTest,Transparent}.
            if (transparent)
            {
                m.SetFloat("_Surface", 1f);
                m.SetFloat("_AlphaClip", 0f);
                m.SetFloat("_Blend", 0f);
                m.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
                m.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                m.DisableKeyword("_ALPHATEST_ON");
                m.renderQueue = 3000;
            }
            else if (alphaTest)
            {
                m.SetFloat("_Surface", 0f);
                m.SetFloat("_AlphaClip", 1f);
                m.SetFloat("_Cutoff", cutoff);
                m.EnableKeyword("_ALPHATEST_ON");
                m.DisableKeyword("_SURFACE_TYPE_TRANSPARENT");
                m.renderQueue = 2450;
            }
            else
            {
                m.SetFloat("_Surface", 0f);
                m.SetFloat("_AlphaClip", 0f);
                m.DisableKeyword("_ALPHATEST_ON");
                m.DisableKeyword("_SURFACE_TYPE_TRANSPARENT");
                m.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.One);
                m.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.Zero);
                m.SetFloat("_ZWrite", 1f);
                m.renderQueue = 2000;
            }

            _convertedMats.Add(m);
            OsmDiag.Log("[Building][Mat] convertito shader='" + legacyName +
                "' -> URP/Lit tex=" + (albedo != null ? albedo.name : "-") +
                " nome='" + (m.name != null ? m.name : "") + "'");
        }

        /// <summary>Converte in place a URP/Lit i materiali built-in di TUTTI
        /// i renderer dell'oggetto appena istanziato (facciate FBX grigie ->
        /// texturizzate). Idempotente e cacheato per materiale condiviso:
        /// chiamabile da qualunque punto che istanzia prefab edifici (chunk
        /// e seed city) senza rischio di lavoro doppio.</summary>
        public static void EnsureURPMaterials(GameObject inst)
        {
            if (inst == null) return;
            var rs = inst.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rs.Length; i++)
            {
                var rr = rs[i];
                if (rr == null) continue;
                var ms = rr.sharedMaterials;
                for (int j = 0; j < ms.Length; j++)
                    if (ms[j] != null) ConvertToURP(ms[j]);
            }
        }

        // Materiale del segnaposto porta condiviso tra tutti gli ingressi
        // (fix #3): prima ogni porta clonava un Material nuovo -> spreco GC.
        private static Material _doorMarkMat;
        private static Material DoorMarkMat()
        {
            if (_doorMarkMat == null)
            {
                var shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                _doorMarkMat = new Material(shader);
                if (shader != null && shader.name.StartsWith("Universal Render Pipeline/Lit"))
                    _doorMarkMat.SetColor("_BaseColor", new Color(0.35f, 0.28f, 0.2f));
                else
                    _doorMarkMat.SetColor("_Color", new Color(0.35f, 0.28f, 0.2f));
            }
            return _doorMarkMat;
        }

        // ── Entrate interni (Fase 4) ─────────────────────────────────────
        // Tutti gli edifici sono esplorabili: niente budget né filtro casuale
        // per chunk. Gli enterabile sono costruiti IN-PLACE (guscio reale
        // cavo con porte, finestre e arredi), così si può entrare in quasi
        // ogni edificio della città. ResetChunkBudget resta per
        // compatibilità (ChunkBuilder lo chiama) ma non limita piu' nulla.
        public static void ResetChunkBudget() { }

        public static bool IsCommercial(string t)
        {
            return ContainsAny(t, "commercial", "retail", "office", "civic",
                "hotel", "supermarket");
        }

        /// <summary>Mappa il tag OSM al costruttore interior esistente.
        /// InteriorGenerator ha gia' 10 builder (shop/apartment/bar/hospital/)
        /// dealer/repair/garage/fuel/bank/school/hotel + house di default):
        /// finora il placer ne usava solo 2 ("shop"/"house"), quindi quasi
        /// tutta la città era "shop" o "house". L'ordine delle regole conta
        /// (più specifiche prima: car_repair prima di car).</summary>
        public static string MapInteriorType(string t)
        {
            if (ContainsAny(t, "fuel", "petrol", "gas_station")) return "fuel";
            if (ContainsAny(t, "car_repair", "repair", "mechanic")) return "repair";
            if (ContainsAny(t, "garage", "parking")) return "garage";
            if (ContainsAny(t, "dealer", "showroom", "cars")) return "dealer";
            if (ContainsAny(t, "shop", "commercial", "retail", "supermarket", "market")) return "shop";
            if (ContainsAny(t, "bar", "cafe", "pub", "restaurant", "fast_food")) return "bar";
            if (ContainsAny(t, "bank", "atm", "office", "civic")) return "bank";
            if (ContainsAny(t, "hotel", "hostel", "motel", "guest")) return "hotel";
            if (ContainsAny(t, "hospital", "clinic", "pharmacy", "medical")) return "hospital";
            if (ContainsAny(t, "school", "kindergarten", "college", "university", "library")) return "school";
            if (ContainsAny(t, "apartments", "residential", "apartment")) return "apartment";
            if (ContainsAny(t, "church", "chapel", "cathedral")) return "house";
            if (ContainsAny(t, "industrial", "warehouse", "barn", "shed", "hangar", "farm")) return "repair";
            return "house";
        }

        /// <summary>Nome leggibile per la porta (UI in italiano).</summary>
        public static string FriendlyBuildingName(string iType, long id)
        {
            switch (iType)
            {
                case "shop": return "Negozio " + id;
                case "apartment": return "Palazzina " + id;
                case "bar": return "Bar " + id;
                case "hospital": return "Ospedale " + id;
                case "dealer": return "Concessionaria " + id;
                case "repair":
                case "garage":
                case "fuel": return "Officina " + id;
                case "bank": return "Banca " + id;
                case "school": return "Scuola " + id;
                case "hotel": return "Hotel " + id;
                default: return "Casa " + id;
            }
        }

        /// <summary>Piani suggeriti dall'altezza del guscio (~3 m per piano).
        /// La decisione finale resta a InteriorGenerator/InteriorManager.</summary>
        public static int SuggestedFloors(float h)
        {
            if (h >= 12.5f) return 4;
            if (h >= 9.5f) return 3;
            if (h >= 6.5f) return 2;
            return 1;
        }

        public static bool IsIndustrial(string t)
        {
            return ContainsAny(t, "industrial", "warehouse", "farm_auxiliary",
                "barn", "shed", "hangar");
        }

        private static bool ContainsAny(string value, params string[] keys)
        {
            if (string.IsNullOrEmpty(value)) return false;
            for (int i = 0; i < keys.Length; i++)
                if (value.IndexOf(keys[i], StringComparison.OrdinalIgnoreCase) >= 0)
                    return true;
            return false;
        }

        /// <summary>
        /// Prefab + altezza target deterministici da tipo e id OSM.
        /// blockSeed (hash dell'isolato ~120 m) guida la scelta dentro una
        /// famiglia piccola di prefab: gli edifici dello stesso isolato
        /// condividono lo stile, con variazione individuale limitata.
        /// </summary>
        public static string PickPrefabName(TileBuildingRec b, out float height, int blockSeed = 0)
        {
            bool commercial = IsCommercial(b.t);
            bool industrial = IsIndustrial(b.t);
            bool civicBig = IsCivicBig(b.t);
            float area = b.d != null && b.d.Length >= 2 ? b.d[0] * b.d[1] : 25f;
            int h = Hash(b.id);

            // Ospedali/scuole/chiese: il kit suburbano non li rappresenta;
            // usano gli edifici commerciali grandi cosi' sembrano pubblici.
            if (civicBig && area > 300f)
            {
                height = 16f + (blockSeed % 6);
                return Skyscrapers[(blockSeed >> 2) % Skyscrapers.Length];
            }
            if (civicBig)
            {
                height = 10f + (blockSeed % 4);
                return CommercialBuildings[(blockSeed >> 2) % CommercialBuildings.Length];
            }

            if (commercial && area > 600f)
            {
                height = 22f + (h % 18);
                return Skyscrapers[h % Skyscrapers.Length];
            }
            if (commercial)
            {
                // famiglia coerente per isolato: 4 gruppi da 3-4 prefab
                int family = ((blockSeed >> 4) & 3) * 4;
                height = 9f + (blockSeed % 6) + (h % 2);
                return CommercialBuildings[(family + h % 4) % CommercialBuildings.Length];
            }
            if (industrial)
            {
                height = 6f + (h % 5);
                return IndustrialBuildings[h % IndustrialBuildings.Length];
            }

            // Case: 5 famiglie di 4; l'isolato sceglie la famiglia,
            // l'edificio solo una variante dentro la famiglia.
            int fam = ((blockSeed >> 3) % 5) * 4;
            height = 4.5f + (blockSeed % 3) * 0.8f + (h % 2) * 0.4f;
            return SuburbanHouses[(fam + h % 4) % SuburbanHouses.Length];
        }

        // hospital / scuola / culto: volumi pubblici riconoscibili
        public static bool IsCivicBig(string t)
        {
            return ContainsAny(t, "hospital", "school", "university", "college",
                "kindergarten", "church", "place_of_worship", "museum",
                "theatre", "library");
        }

        // Kit Quaternius Downtown City (CC0): edifici grandi con facciate
        // gia' texturizzate (Brick/Trim/Metal/Concrete). Volume per area:
        //   Small (<120 mq)   -> casa/boutique a 2 piani
        //   Medium (120-300)  -> palazzina media
        //   Large (>300 mq)   -> isolato/lotto con base ampia
        private static Dictionary<string, GameObject> _quaterniusCache =
            new Dictionary<string, GameObject>();

        // I modelli Quaternius sono pochi (3) e condivisi: il caricamento via
        // Resources.Load avviene UNA sola volta per modello e viene riusato per
        // tutti gli edifici che lo condividono (niente I/O a ogni build).
        private static GameObject LoadQuaternius(float w, float d)
        {
            float area = w * d;
            string name = area > 300f ? "Building_Large_2"
                        : area > 120f ? "Building_Medium_2_001"
                                      : "Building_Small_1";
            GameObject cached;
            if (_quaterniusCache.TryGetValue(name, out cached)) return cached;
            var go = Resources.Load<GameObject>("Buildings/Quaternius/" + name);
            _quaterniusCache[name] = go;
            OsmDiag.Log("[Building][Quat] load='" + name + "' area=" + area.ToString("F0") +
                " ok=" + (go != null));
            return go;
        }

        // Altezza target suggerita per i modelli Quaternius: piu' simile a
        // quella che l'edificio OSM dovrebbe avere. Non e' mai inferiore a
        // quella Kenney (i modelli Quaternius hanno gia' finestre/tratti).
        private static float SuggestQuaterniusHeight(string t, float w, float d, float fallback)
        {
            if (IsCivicBig(t)) return Mathf.Max(fallback, 14f);
            if (IsCommercial(t)) return Mathf.Max(fallback, 10f + Mathf.Min(w, d) * 0.2f);
            return Mathf.Max(fallback, 6f);
        }

        private static int Hash(long id)
        {
            uint x = (uint)id;
            x ^= x >> 16; x *= 0x7feb352d; x ^= x >> 15; x *= 0x846ca68b; x ^= x >> 16;
            return (int)(x & 0x7fffffff);
        }

        // Cella di stile ~120 m basata sulle coordinate GEO assolute dell'
        // edificio (lat/lon), NON sulle coordinate locali al chunk. Se si
        // usassero le locali, una leggera differenza di origine del chunk
        // (fix GPS di sessione, retry tile, UnloadAll) farebbe cadere
        // l'edificio in una cella diversa -> stesso OSM, prefab diverso.
        // Con lat/lon il risultato è IDENTICO in ogni sessione e rigenerazione.
        private static int GeoBlockSeed(double lat, double lon)
        {
            double latStep = 120.0 / 111320.0; // ~120 m in gradi di latitudine
            double lonStep = latStep / Math.Cos(lat * Math.PI / 180.0);
            long ix = (long)Math.Floor(lat / latStep);
            long iy = (long)Math.Floor(lon / lonStep);
            return Hash((ix << 16) ^ iy);
        }

        /// <summary>
        /// Istanzia il prefab per il record b. centerLocal = posizione dell'
        /// impronta in coordinate locali al chunk. Ritorna false se manca il
        /// prefab o il record e' degenere.
        /// </summary>
        /// <param name="terrainHeights">Dictionary opzionale {Vector2 localPos -> altezza in metri}.
        /// Se fornito, l'edificio viene sollevato di tale altezza sul terreno.</param>
        public static bool Place(Huntix.Core.CityKitAssetRegistry registry,
            Transform parent, TileBuildingRec b, Vector3 centerLocal,
            Dictionary<Vector2, float> terrainHeights = null)
        {
            if (b.c == null || b.c.Length < 2 || b.d == null || b.d.Length < 2)
                return false;
            float w = Mathf.Max(b.d[0], 1.5f);
            float d = Mathf.Max(b.d[1], 1.5f);

            // Ogni edificio e' esplorabile: esterno = prefab Quaternius/Kenney
            // (facciate texturizzate) + interno reale generato lazy dentro
            // l'impronta (stesso macchinario degli in-place, v. BuildingPlacer
            // ridotto: BuildingEntrance.Enter con fade). Mai edifici assenti o
            // parziali: il prefab completo resta il vero corpo dell'edificio.
            return PlaceLegacy(registry, parent, b, centerLocal, w, d, terrainHeights);
        }

        /// <summary>Percorso legacy: prefab Kenney/Quaternius pieno. Completo e
        /// robusto (edificio chiuso non enterabile): mai parziale.</summary>
        /// <param name="terrainHeights">Dictionary opzionale {Vector2 localPos -> altezza in metri}.
        /// Se fornito, l'edificio viene sollevato di tale altezza sul terreno.</param>
        private static bool PlaceLegacy(Huntix.Core.CityKitAssetRegistry registry,
            Transform parent, TileBuildingRec b, Vector3 centerLocal, float w, float d,
            Dictionary<Vector2, float> terrainHeights = null)
        {
            float h;
            // isolato ~120 m: gli edifici vicini condividono stile e fascia
            // d'altezza (quartieri omogenei, niente patchwork casuale).
            // Seed derivato dal centro GEO dell'edificio (b.c = [lat, lon]):
            // stabile tra sessioni e rigenerazioni dei chunk (vedi GeoBlockSeed).
            int blockSeed = b.c != null && b.c.Length >= 2
                ? GeoBlockSeed(b.c[0], b.c[1])
                : Hash(((long)Mathf.FloorToInt(centerLocal.x / 120f) << 16) ^
                       (long)Mathf.FloorToInt(centerLocal.z / 120f));
            string prefabName = PickPrefabName(b, out h, blockSeed);
            GameObject prefab = null;

            // Edifici non visitabili: prima si prova il kit high-quality
            // Quaternius (facciate gia' texturizzate, piu' belle dei prefab
            // Kenney piatti), altrimenti si ripiega sul prefab Kenney legacy.
            // La scelta volume (Small/Medium/Large) segue l'area. Lo switch
            // stile (hamburger menu / PlayerPrefs) torna a Kenney a caldo:
            // basta rigenerare il chunk per riapplicare la scelta.
            bool useQ = City.UI.CityStyle.UseQuaternius;
            GameObject quat = useQ ? LoadQuaternius(w, d) : null;
            if (quat != null)
            {
                prefab = quat;
                h = SuggestQuaterniusHeight(b.t, w, d, h);
            }
            else
            {
                prefab = registry != null ? registry.Get(prefabName) : null;
                if (prefab == null)
                {
                    string folder = IsIndustrial(b.t) ? "Industrial"
                        : IsCommercial(b.t) ? "Commercial" : "Suburban";
                    prefab = LoadKenney(folder, prefabName);
                }
            }
            OsmDiag.Log("[Building][Pick] id=" + b.id + " t='" + b.t + "' w=" + w.ToString("F1") +
                " d=" + d.ToString("F1") + " area=" + (w * d).ToString("F0") +
                " useQuaternius=" + useQ +
                " prefab=" + (prefab != null ? prefab.name : "NULL") +
                " via=" + (quat != null ? "Quaternius" : "Kenney"));
            if (prefab == null) return false;

            var inst = UnityEngine.Object.Instantiate(prefab, parent);
            inst.name = "Edificio " + b.id;

            // Diagnostica materiali/submesh del prefab appena istanziato
            // + conversione a URP/Lit in place: prima si conta lo stato di
            // import (come arrivano da FBX: Standard/Diffuse -> grigio in URP),
            // poi i materiali built-in vengono rimappati su URP/Lit (vedi
            // ConvertToURP). I materiali sono CONDIVISI per prefab: la
            // conversione del primo edificio corregge tutte le facciate.
            var instRenderers = inst.GetComponentsInChildren<Renderer>(true);
            int matsTotal = 0, matsNullShader = 0, matsBuiltin = 0, matsWithTex = 0;
            Material firstMat = null;
            for (int ri = 0; ri < instRenderers.Length; ri++)
            {
                var rr = instRenderers[ri];
                if (rr == null) continue;
                var ms = rr.sharedMaterials;
                for (int mi = 0; mi < ms.Length; mi++)
                {
                    var m = ms[mi];
                    if (m == null) continue;
                    matsTotal++;
                    if (firstMat == null) firstMat = m;
                    bool builtin = NeedsURPConversion(m);
                    if (m.shader == null || string.IsNullOrEmpty(m.shader.name))
                        matsNullShader++;
                    else if (builtin)
                        matsBuiltin++;
                    // Textura originaria: _MainTex sullo shader legacy,
                    // _BaseMap su quello URP — contata PRIMA della conversione.
                    string baseProp = builtin ? "_MainTex" : "_BaseMap";
                    if (m.HasProperty(baseProp) && m.GetTexture(baseProp) != null)
                        matsWithTex++;
                    ConvertToURP(m);
                }
            }
            OsmDiag.Log("[Building][Spawn] id=" + b.id + " name='" + inst.name +
                "' renderers=" + instRenderers.Length +
                " matsTot=" + matsTotal +
                " matsNoShader=" + matsNullShader +
                " matsBuiltinNonURP=" + matsBuiltin +
                " matsConvertitiURP=" + matsBuiltin +
                " matsConTextura=" + matsWithTex +
                " primo=" + (firstMat != null
                    ? (firstMat.shader != null ? firstMat.shader.name : "NONE") : "NULL"));

            if (PrefabHasColliders(prefab))
                foreach (var col in inst.GetComponentsInChildren<Collider>(true))
                    UnityEngine.Object.Destroy(col);

            // Misura PRIMA della rotazione: bounds axis-aligned su oggetto ruotato gonfia la scala.
            Bounds baseB = UnionBounds(inst);
            float sx = w / Mathf.Max(baseB.size.x, 0.1f);
            float sz = d / Mathf.Max(baseB.size.z, 0.1f);
            float s = Mathf.Max(sx, sz);
            float sy = Mathf.Clamp(h / Mathf.Max(baseB.size.y, 0.01f), s * 0.6f, s * 1.5f);
            inst.transform.localScale = new Vector3(sx, sy, sz);
            inst.transform.localRotation = Quaternion.Euler(0f, b.r, 0f);
            inst.transform.localPosition = Vector3.zero;

            // Fix #4: calcola i bounds post-rotazione per via matematica
            // (AABB del box baseB scalato e ruotato attorno a Y) invece di
            // fare una seconda UnionBounds -> si elimina un secondo
            // GetComponentsInChildren<Renderer> per edificio.
            Bounds wb = UnionBoundsAfter(new Vector3(sx, sy, sz),
                b.r * Mathf.Deg2Rad, baseB);
            var pos = new Vector3(
                centerLocal.x - wb.center.x,
                -wb.min.y,
                centerLocal.z - wb.center.z);
            inst.transform.localPosition = pos;

            // Aggiungi altezza terreno se disponibile
            if (terrainHeights != null)
            {
                // Posizione approssimativa dell'edificio (centro footprint)
                float buildX = centerLocal.x;
                float buildZ = centerLocal.z;
                // Cerca l'altezza del terreno più vicina
                float nearestHeight = 0f;
                float nearestDistSq = float.MaxValue;
                foreach (var kv in terrainHeights)
                {
                    float distSq = (buildX - kv.Key.x) * (buildX - kv.Key.x) +
                                   (buildZ - kv.Key.y) * (buildZ - kv.Key.y);
                    if (distSq < nearestDistSq)
                    {
                        nearestDistSq = distSq;
                        nearestHeight = kv.Value;
                    }
                }
                // Solleva l'edificio dall'altezza del terreno (aggiunge sopra y=0)
                inst.transform.localPosition += new Vector3(0f, nearestHeight, 0f);
            }

            // Un collider solo, dimensionato sui bounds reali del modello.
            var box = inst.AddComponent<BoxCollider>();
            box.size = baseB.size;
            box.center = baseB.center;
            inst.layer = BuildingLayer;

            // Guardia anti-"player che vola": se l'edificio viene istanziato
            // mentre il player e' dentro (o a ridosso de) la sua impronta, il
            // BoxCollider pieno spingerebbe il CC verso l'alto sul tetto e,
            // col layer 8 escluso dalle sonde di GroundSnapper, il player non
            // tornerebbe mai a terra. Teniamo il collider spento finche' il
            // player non esce dal footprint (ColliderReArm lo riattiva poi).
            var guardPlayer = City.Game.Instance != null
                ? City.Game.Instance.player : null;
            if (guardPlayer != null)
            {
                Vector3 worldCenter = parent.TransformPoint(
                    new Vector3(centerLocal.x, 0f, centerLocal.z));
                Vector3 gp = guardPlayer.transform.position;
                float hw = w * 0.5f;
                float hd = d * 0.5f;
                float halfDiag = Mathf.Sqrt(hw * hw + hd * hd);
                // Impronta allargata del raggio del CC (la capsule del player
                // deve poter uscire senza scontrarsi col muro appena comparso).
                if (Mathf.Abs(gp.x - worldCenter.x) <= halfDiag + 1.2f &&
                    Mathf.Abs(gp.z - worldCenter.z) <= halfDiag + 1.2f)
                {
                    box.enabled = false;
                    var guard = inst.AddComponent<ColliderReArm>();
                    guard.Setup(box, worldCenter, w, d);
                    OsmDiag.Log("[Building][Guard] id=" + b.id +
                        " collider differito (player nell'impronta): anti-volo");
                }
            }

            var entranceComp = MaybeAddEntrance(inst, b, baseB, w, d, h, sx, sy, sz);

            // Interno reale in modalita' PREFAB: ogni edificio è esplorabile.
            // L'interno si genera lazy dentro l'impronta (stesso macchinario
            // in-place) con muri/arredi/luci propri. Si entra con il fade dalla
            // porta (BuildingEntrance.Enter -> fade nero -> esterno nascosto ->
            // player dentro), si esce dal trigger "USCITA" o varcando la soglia
            // (InteriorManager.MarkOutside -> fade -> esterno ripristinato).
            // Il collider esterno (box) resta acceso FUORI e spento DENTRO,
            // cosi' il giocatore non lo attraversa da fuori.
            var gen = inst.AddComponent<City.Interior.InteriorGenerator>();
            gen.PreparePrefabExterior(
                MapInteriorType(b.t),
                w, d, h,
                entranceComp != null ? entranceComp.shop : null,
                box);
            if (entranceComp != null) gen.SetEntrance(entranceComp);
            OsmDiag.Log("[Building][Done] id=" + b.id + " scale=" + sx.ToString("F2") +
                "x" + sy.ToString("F2") + "x" + sz.ToString("F2") +
                " bounds=" + baseB.size.ToString("F2") +
                " porta=" + (entranceComp != null) + " interno=si");
            return true;
        }

// ── Edifici PREFAB (Quaternius/Kenney) esplorabili ────────────────
        // Ogni edificio è il prefab pieno (facciate texturizzate) + un interno
        // reale generato lazy nell'impronta: BuildingEntrance.Enter esegue il
        // fade di ingresso e InteriorManager.MarkOutside quello di uscita.

        // Alcuni edifici piccoli diventano visitabili: un PORTALE stretto sul
        // lato della facciata (dove l'interno mette la PortaIngresso, +Z
        // locale), largo quanto una porta. L'ingresso scatta SOLO da interazione
        // (tap sulla porta -> BuildingEntrance.Interact -> fade), mai in modo
        // accidentale camminando accanto al muro.
        private static BuildingEntrance MaybeAddEntrance(GameObject inst, TileBuildingRec b,
            Bounds baseB, float w, float d, float h,
            float sx, float sy, float sz)
        {
            string iType = MapInteriorType(b.t);
            bool shop = iType == "shop";

            // Lato porta = +Z locale (facciata), coerente con la PortaIngresso
            // interna di InteriorGenerator (a +d*0.5). Il trigger-porta sporge
            // appena oltre il muro frontale e resta stretto in larghezza, cosi'
            // il giocatore deve passare ATTRAVERSO la porta, non solo accanto.
            const float doorW = 2.4f;     // larghezza porta (mondo)
            const float doorT = 0.4f;     // spessore fascia porta (mondo)
            const float protr = 0.15f;    // sporgenza oltre il muro (mondo)
            const float doorCenterY = 1.0f; // altezza centro porta (mondo)

            // offset del muro frontale rispetto al box center (+ baseB.size.z/2)
            float frontOffsetZ = baseB.size.z * 0.5f + protr;
            float doorCenterZ = baseB.center.z + frontOffsetZ;

            var trig = new GameObject("Porta");
            trig.transform.SetParent(inst.transform, false);
            trig.layer = BuildingLayer;
            // posiziono il GAMEOBJECT al centro del portale e tengo il collider
            // centrato sull'origine: cosi' il segnaposto visivo (figlio) sta
            // al centro. Le coordinate locali sono sottoposte alla scala NON
            // uniforme del genitore -> divido per (sx,sy,sz) per i metri mondo.
            trig.transform.localPosition =
                new Vector3(baseB.center.x, doorCenterY / sy, doorCenterZ / sz);
            var tc = trig.AddComponent<BoxCollider>();
            tc.isTrigger = true;
            tc.center = Vector3.zero;
            tc.size = new Vector3(doorW / sx, 2.2f / sy, doorT / sz);

            // segnaposto VISIVO della porta: una piastra piana verticale sul
            // muro frontale, cosi' il giocatore sa dove passare
            var doorMark = GameObject.CreatePrimitive(PrimitiveType.Quad);
            UnityEngine.Object.Destroy(doorMark.GetComponent<Collider>());
            doorMark.name = "PortaSegnaposto";
            doorMark.transform.SetParent(trig.transform, false);
            doorMark.transform.localPosition = Vector3.zero;
            doorMark.transform.localRotation = Quaternion.identity;
            doorMark.transform.localScale = new Vector3(2.3f / sx, 2.1f / sy, 1f);
            var r = doorMark.GetComponent<Renderer>();
            r.sharedMaterial = DoorMarkMat();

            var entrance = trig.AddComponent<City.Interior.BuildingEntrance>();
            entrance.buildingType = iType;
            entrance.buildingName = FriendlyBuildingName(iType, b.id);
            entrance.buildingWidth = w;
            entrance.buildingDepth = d;
            entrance.buildingHeight = h;
            entrance.floorCount = SuggestedFloors(h);

            if (shop)
            {
                // Negozi nel mondo chunked: il bancone di acquisto dentro
                // l'interno viene creato da InteriorGenerator SOLO se
                // entrance.shop != null. Prima lo Shop era attachato solo nel
                // percorso legacy (disattivato) percio' il commercio era morto.
                var shopComp = inst.AddComponent<City.World.Shop>();
                shopComp.shopName = string.IsNullOrEmpty(b.nm)
                    ? "Negozio " + b.id : b.nm;
                ShopItemsFor(shopComp, b.id);
                entrance.shop = shopComp;
            }

            return entrance;
        }

        /// <summary>Catalogo deterministico per id OSM. I placement della tile
        /// espongono solo il tipo building (senza amenity/shop), quindi non si
        /// puo' distinguere supermercato/pizzeria: prodotti "da quartiere"
        /// variati dall'id.</summary>
        private static void ShopItemsFor(City.World.Shop shop, long bId)
        {
            string[][] cats =
            {
                new[] { "Pane", "Latte", "Acqua", "Mele" },
                new[] { "Caffe", "Cornetto", "Cappuccino" },
                new[] { "Maglietta", "Cappellino", "Jeans" },
                new[] { "Cuffie", "Cavo USB", "Powerbank" },
                new[] { "Libro", "Quaderno", "Penna" },
                new[] { "Cerotti", "Vitamine", "Nastro adesivo" },
            };
            int h = Hash(bId);
            var cat = cats[h % cats.Length];
            for (int i = 0; i < cat.Length; i++)
            {
                int price = 1 + (h >> (2 + i * 2)) % (4 + i * 2);
                shop.items.Add(new City.World.ShopItem(cat[i], price));
            }
        }

        public static Bounds UnionBounds(GameObject go)
        {
            var bounds = new Bounds();
            bool first = true;
            var renderers = go.GetComponentsInChildren<Renderer>();
            for (int i = 0; i < renderers.Length; i++)
            {
                if (renderers[i] is ParticleSystemRenderer) continue;
                if (first) { bounds = renderers[i].bounds; first = false; }
                else bounds.Encapsulate(renderers[i].bounds);
            }
            if (first) bounds = new Bounds(Vector3.zero, Vector3.one);
            // riporta in spazio locale dell'istanza (pivot non centrato)
            var t = go.transform;
            var localCenter = t.worldToLocalMatrix.MultiplyPoint3x4(bounds.center);
            var localSize = Vector3.Scale(bounds.size, InverseScale(t.lossyScale));
            return new Bounds(localCenter, localSize);
        }

        private static Vector3 InverseScale(Vector3 s)
        {
            return new Vector3(
                s.x != 0 ? 1f / s.x : 0f,
                s.y != 0 ? 1f / s.y : 0f,
                s.z != 0 ? 1f / s.z : 0f);
        }

        /// <summary>
        /// AABB del box baseB dopo aver applicato una scala non uniforme
        /// (attorno all'origine locale) e una rotazione pura attorno a Y.
        /// Evita il secondo GetComponentsInChildren<Renderer> in Place.
        /// </summary>
        private static Bounds UnionBoundsAfter(Vector3 scale, float yawRad, Bounds baseB)
        {
            Vector3 c = baseB.center;
            Vector3 e = baseB.extents;
            float cos = Mathf.Cos(yawRad);
            float sin = Mathf.Sin(yawRad);

            Vector3 mn = new Vector3(float.MaxValue, float.MaxValue, float.MaxValue);
            Vector3 mx = new Vector3(float.MinValue, float.MinValue, float.MinValue);

            for (int i = 0; i < 8; i++)
            {
                var sx = (i & 1) == 0 ? -1f : 1f;
                var sy = (i & 2) == 0 ? -1f : 1f;
                var sz = (i & 4) == 0 ? -1f : 1f;
                Vector3 p = new Vector3(
                    (c.x + e.x * sx) * scale.x,
                    (c.y + e.y * sy) * scale.y,
                    (c.z + e.z * sz) * scale.z);
                float x = p.x * cos + p.z * sin;
                float z = -p.x * sin + p.z * cos;
                Vector3 q = new Vector3(x, p.y, z);
                if (q.x < mn.x) mn.x = q.x;
                if (q.y < mn.y) mn.y = q.y;
                if (q.z < mn.z) mn.z = q.z;
                if (q.x > mx.x) mx.x = q.x;
                if (q.y > mx.y) mx.y = q.y;
                if (q.z > mx.z) mx.z = q.z;
            }
            return new Bounds((mn + mx) * 0.5f, mx - mn);
        }
    }
}

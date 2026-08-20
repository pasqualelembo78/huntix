using UnityEngine;
using System.Collections.Generic;
using Huntix.Core;

namespace Huntix.Indoor
{
     /// <summary>
     /// Costruisce dinamicamente l'interno del negozio usando gli asset Kenney Mini Market (CC0).
     /// Layout diversi in base al tipo di POI: ristorante, palestra, ospedale, bar, supermercato.
     /// Carica i prefabs da Resources/KenneyMiniMarket/ a runtime.
     /// Se useCuteStore=true carica invece i prefab da Resources/CuteStore/ (asset non-Kenney)
     /// e usa la colormap CuteStore; il layout e le posizioni restano gli stessi.
     /// </summary>
     public class StoreBuilder : MonoBehaviour
     {
         [Header("Layout")]
         public float storeWidth = 10f;
        public float storeDepth = 8f;
        public float wallHeight = 3f;

        private readonly List<GameObject> _spawned = new List<GameObject>();

        // Cached prefab references loaded from Resources
        private GameObject _floorPrefab;
        private GameObject _wallPrefab;
        private GameObject _wallDoorPrefab;
        private GameObject _wallWindowPrefab;
        private GameObject _wallCornerPrefab;
        private GameObject _cashRegisterPrefab;
        private GameObject _shelfBagsPrefab;
        private GameObject _shelfBoxesPrefab;
        private GameObject _shelfEndPrefab;
        private GameObject _freezerPrefab;
        private GameObject _freezersStandingPrefab;
        private GameObject _displayBreadPrefab;
        private GameObject _displayFruitPrefab;
        private GameObject _shoppingCartPrefab;
        private GameObject _columnPrefab;
        private GameObject _characterPrefab;
        private bool _loaded = false;

        // Prefab "Cute Supermarket Lite" passati da IndoorManager (riferimenti di
        // scena, non Resources.Load) usati come decorazioni nel supermercato.
        public GameObject[] cuteDecorPrefabs;

        // Registry Kenney (risoluzione asset senza Resources.Load). Se non impostato
        // direttamente, viene preso dal GameManager globale.
        public KenneyAssetRegistry kenneyRegistry;
        private KenneyAssetRegistry KenneyReg
        {
            get
            {
                if (kenneyRegistry == null && GameManager.Instance != null)
                    kenneyRegistry = GameManager.Instance.kenneyRegistry;
                return kenneyRegistry;
            }
        }

     [Header("Asset set")]
     [SerializeField] private bool useCuteStore = true;

        // Riparazione runtime per URP: i modelli Kenney (materiali FbxSurfaceLambert)
        // vengono importati senza shader validi sotto URP → invisibili. A runtime si
        // assegna URP/Lit e si ricollega la texture colormap per farli comparire.
        private Shader _urplit;
        private Texture2D _colormap;

     private void LoadPrefabs()
         {
             if (_loaded) return;

             // Se si usa il set CuteStore (non-Kenney), usa i prefab da Resources/CuteStore/ se
             // presenti; altrimenti genera a runtime forme low-poly colorate (palette diversa da
             // Kenney) in modo da garantire comunque un aspetto non-Kenney senza installare Editor.
             _floorPrefab = LoadCuteOrRuntime("floor", new Color(0.92f, 0.78f, 0.52f), new Vector3(1, 0.1f, 1));
             _wallPrefab = LoadCuteOrRuntime("wall", new Color(0.95f, 0.90f, 0.72f), new Vector3(1, 1, 0.1f));
             _wallDoorPrefab = LoadCuteOrRuntime("wall-door-rotate", new Color(0.95f, 0.90f, 0.72f), new Vector3(1, 1, 0.1f));
             _wallWindowPrefab = LoadCuteOrRuntime("wall-window", new Color(0.95f, 0.90f, 0.72f), new Vector3(1, 1, 0.1f));
             _wallCornerPrefab = LoadCuteOrRuntime("wall-corner", new Color(0.70f, 0.70f, 0.80f), Vector3.one);
             _cashRegisterPrefab = LoadCuteOrRuntime("cash-register", new Color(0.25f, 0.25f, 0.30f), new Vector3(0.6f, 0.8f, 0.4f));
             _shelfBagsPrefab = LoadCuteOrRuntime("shelf-bags", new Color(0.86f, 0.52f, 0.52f), new Vector3(1, 1.2f, 0.4f));
             _shelfBoxesPrefab = LoadCuteOrRuntime("shelf-boxes", new Color(0.50f, 0.60f, 0.95f), new Vector3(1, 1.2f, 0.4f));
             _shelfEndPrefab = LoadCuteOrRuntime("shelf-end", new Color(0.96f, 0.82f, 0.34f), new Vector3(0.4f, 1.2f, 0.4f));
             _freezerPrefab = LoadCuteOrRuntime("freezer", new Color(0.62f, 0.86f, 0.95f), new Vector3(0.8f, 1.4f, 0.6f));
             _freezersStandingPrefab = LoadCuteOrRuntime("freezers-standing", new Color(0.62f, 0.86f, 0.95f), new Vector3(0.7f, 1.6f, 0.7f));
             _displayBreadPrefab = LoadCuteOrRuntime("display-bread", new Color(0.95f, 0.40f, 0.40f), new Vector3(1, 0.7f, 0.7f));
             _displayFruitPrefab = LoadCuteOrRuntime("display-fruit", new Color(0.96f, 0.70f, 0.34f), new Vector3(1, 0.7f, 0.7f));
             _shoppingCartPrefab = LoadCuteOrRuntime("shopping-cart", new Color(0.80f, 0.80f, 0.95f), new Vector3(0.8f, 0.6f, 0.8f));
             _columnPrefab = LoadCuteOrRuntime("column", new Color(0.70f, 0.70f, 0.80f), new Vector3(0.3f, 1.5f, 0.3f));
             _characterPrefab = LoadCuteOrRuntime("character-employee", new Color(0.94f, 0.68f, 0.80f), new Vector3(0.4f, 1.6f, 0.4f));

              _urplit = Shader.Find("Universal Render Pipeline/Lit");
              _colormap = useCuteStore
                  ? null
                  : (KenneyReg != null ? KenneyReg.colormap : null);
             _loaded = true;
             Debug.Log($"[StoreBuilder] Prefabs loaded via {(useCuteStore ? "CuteStore (non-Kenney" : "KenneyMiniMarket")} ({(_floorPrefab != null ? "ok" : "NULL")}, urpLit={_urplit != null}, colormap={_colormap != null})");
         }

         private void FixMaterials(GameObject go)
        {
            if (go == null) return;

            // Shader di fallback: URP/Lit se URP è attivo, altrimenti Standard (Built-in),
            // altrimenti si lascia invariato (legacy compat). Senza fallback (URP non
            // assegnato in Graphics Settings) i modelli Kenney Legacy resterebbero grigi.
            Shader targetShader = _urplit;
            bool isURP = targetShader != null &&
                (targetShader.name.Contains("Universal") || targetShader.name.Contains("URP"));
            if (targetShader == null)
            {
                targetShader = Shader.Find("Standard");
                isURP = false;
            }
            string baseMapProp = isURP ? "_BaseMap" : "_MainTex";

            foreach (var r in go.GetComponentsInChildren<Renderer>(true))
            {
                var mats = r.sharedMaterials;
                for (int i = 0; i < mats.Length; i++)
                {
                    var m = mats[i];
                    if (m == null) continue;
                    if (targetShader != null &&
                        (m.shader == null || string.IsNullOrEmpty(m.shader.name) ||
                         m.shader.name.Contains("Standard") || m.shader.name.Contains("Diffuse") ||
                         (!m.shader.name.Contains("URP") && !m.shader.name.Contains("Universal"))))
                    {
                        m.shader = targetShader;
                    }
                    // Colormap Kenney → texture principale (URP= _BaseMap, Standard= _MainTex)
                    if (m.HasProperty(baseMapProp) && m.GetTexture(baseMapProp) == null && _colormap != null)
                        m.SetTexture(baseMapProp, _colormap);
                }
            }
        }

         // ── Main entry point ──────────────────────────────────────────

         /// <summary>Forza il set di asset (true = non-Kenney CuteStore).</summary>
         public void SetAssetSet(bool useCute)
         {
             if (useCuteStore == useCute) return;
             useCuteStore = useCute;
             _loaded = false;
         }

         public void BuildStore(string poiType = "")
         {
             BuildStore(poiType, useCuteStore);
         }

         public void BuildStore(string poiType, bool useCuteAssets)
         {
             useCuteStore = useCuteAssets;
             _loaded = false;
             ClearStore();
             LoadPrefabs();

            var root = new GameObject("StoreInterior");

            // Build walls + floor (common to all types)
            BuildShell(root);

            // Dispatch layout by type
            var t = (poiType ?? "").ToLower().Trim();
            if (t.Contains("ristorante") || t.Contains("restaurant") || t.Contains("pizzeria"))
                BuildRestaurant(root);
            else if (t.Contains("palestra") || t.Contains("gym") || t.Contains("fitness")
                     || t.Contains("yoga") || t.Contains("danza") || t.Contains("boxing"))
                BuildGym(root);
            else if (t.Contains("hospital") || t.Contains("ospedale") || t.Contains("clinica")
                     || t.Contains("medico") || t.Contains("studio"))
                BuildHospital(root);
            else if (t.Contains("bar") || t.Contains("café") || t.Contains("caffe")
                     || t.Contains("caffetteria") || t.Contains("pasticceria")
                     || t.Contains("gelateria") || t.Contains("enoteca"))
                BuildBar(root);
            else if (t.Contains("libreria") || t.Contains("library") || t.Contains("biblioteca"))
                BuildLibrary(root);
            else
                BuildSupermarket(root);

            Debug.Log($"[StoreBuilder] Store built: {poiType}, {storeWidth}x{storeDepth}m");
        }

        // ── Shell: floor + walls + corners ────────────────────────────

        private void BuildShell(GameObject root)
        {
            // Floor
            Spawn(_floorPrefab, root.transform, Vector3.zero, Vector3.zero, new Vector3(storeWidth, 1, storeDepth));

            // Walls
            Spawn(_wallPrefab, root.transform, new Vector3(0, wallHeight / 2f, storeDepth / 2f),
                  Vector3.zero, new Vector3(storeWidth, wallHeight, 0.1f));                     // North
            Spawn(_wallDoorPrefab, root.transform, new Vector3(0, wallHeight / 2f, -storeDepth / 2f),
                  Vector3.zero, new Vector3(storeWidth, wallHeight, 0.1f));                     // South (door)
            Spawn(_wallWindowPrefab, root.transform, new Vector3(storeWidth / 2f, wallHeight / 2f, 0),
                  new Vector3(0, 90, 0), new Vector3(storeDepth, wallHeight, 0.1f));            // East (window)
            Spawn(_wallPrefab, root.transform, new Vector3(-storeWidth / 2f, wallHeight / 2f, 0),
                  new Vector3(0, 90, 0), new Vector3(storeDepth, wallHeight, 0.1f));            // West

            // Corners
            Spawn(_wallCornerPrefab, root.transform, new Vector3(storeWidth / 2f, 0, storeDepth / 2f), Vector3.zero, Vector3.one);
            Spawn(_wallCornerPrefab, root.transform, new Vector3(-storeWidth / 2f, 0, storeDepth / 2f), Vector3.zero, Vector3.one);
            Spawn(_wallCornerPrefab, root.transform, new Vector3(storeWidth / 2f, 0, -storeDepth / 2f), Vector3.zero, Vector3.one);
            Spawn(_wallCornerPrefab, root.transform, new Vector3(-storeWidth / 2f, 0, -storeDepth / 2f), Vector3.zero, Vector3.one);
        }

        // ── SUPERMARKET (default) ─────────────────────────────────────

        private void BuildSupermarket(GameObject root)
        {
            // Columns
            Spawn(_columnPrefab, root.transform, new Vector3(-2f, 0, 2f), Vector3.zero, Vector3.one);
            Spawn(_columnPrefab, root.transform, new Vector3(2f, 0, 2f), Vector3.zero, Vector3.one);

            // Cash register (interactive — buy)
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(3.5f, 0, -2.5f), Vector3.zero, Vector3.one,
                "cash_register", "Cassa", "buy", "🛒", "", 0, "");

            // Back wall shelves (interactive — collect food)
            SpawnInteractive(_shelfBoxesPrefab, root.transform, new Vector3(-3f, 0, 3f), Vector3.zero, Vector3.one,
                "shelf_boxes", "Scaffale snack", "collect", "📦", "hunger", 15, "snack");
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(0, 0, 3f), Vector3.zero, Vector3.one,
                "shelf_bags", "Scaffale bevande", "collect", "🛍️", "thirst", 12, "water");
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(3f, 0, 3f), Vector3.zero, Vector3.one,
                "shelf_end", "Scaffale lattine", "collect", "🥫", "hunger", 10, "can");

            // Middle aisle
            Spawn(_shelfBoxesPrefab, root.transform, new Vector3(-2f, 0, 0.5f), new Vector3(0, 90, 0), Vector3.one);
            Spawn(_shelfBagsPrefab, root.transform, new Vector3(2f, 0, 0.5f), new Vector3(0, 90, 0), Vector3.one);

            // Freezers
            Spawn(_freezerPrefab, root.transform, new Vector3(4f, 0, 1f), Vector3.zero, Vector3.one);
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(4f, 0, -1f), Vector3.zero, Vector3.one);

            // Display tables (interactive — fresh food)
            SpawnInteractive(_displayBreadPrefab, root.transform, new Vector3(-1.5f, 0, -1f), Vector3.zero, Vector3.one,
                "display_bread", "Panificio", "collect", "🍞", "hunger", 20, "bread");
            SpawnInteractive(_displayFruitPrefab, root.transform, new Vector3(1.5f, 0, -1f), Vector3.zero, Vector3.one,
                "display_fruit", "Frutta fresca", "collect", "🍎", "hunger", 18, "fruit");

            // Shopping cart
            Spawn(_shoppingCartPrefab, root.transform, new Vector3(0, 0, -3f), new Vector3(0, 45, 0), Vector3.one);

            // NPC: cashier
            SpawnNPC(root.transform, new Vector3(3.5f, 0, -1.5f), "cashier", "Cassiere",
                "employee", "🧑‍💼", new[] { "Benvenuto al supermercato!", "Hai trovato tutto quello che cercavi?", "Questo pane è appena sfornato!" });

            // Decorazioni: modelli "Cute Supermarket Lite" sugli scaffali/espositori.
            SpawnCuteDecor(root);
        }

        /// <summary>
        /// Piazza i modelli del pack "Cute Supermarket Lite" (cibo/prodotti carini)
        /// sopra scaffali ed espositori del supermercato, così la skin Cute è
        /// visibile oltre allo shell non-Kenney. I prefab stanno in
        /// Resources/CuteStore/Prefabs/ (importati dal unitypackage).
        /// </summary>
        private void SpawnCuteDecor(GameObject root)
        {
            var foods = cuteDecorPrefabs;
            if (foods == null || foods.Length == 0)
            {
                Debug.Log("[StoreBuilder] Nessun prefab Cute decorativo (cuteDecorPrefabs vuoto)");
                return;
            }

            // Posizioni "sopra" scaffali ed espositori già spawnati.
            var slots = new[]
            {
                new Vector3(-3f, 1.15f, 3f),
                new Vector3(0f, 1.15f, 3f),
                new Vector3(3f, 1.15f, 3f),
                new Vector3(-2f, 1.35f, 0.5f),
                new Vector3(2f, 1.35f, 0.5f),
                new Vector3(-1.5f, 0.95f, -1f),
                new Vector3(1.5f, 0.95f, -1f),
                new Vector3(4f, 1.25f, 1f),
                new Vector3(4f, 1.25f, -1f),
                new Vector3(-3.5f, 0.95f, -2f),
                new Vector3(3.5f, 0.95f, 1f),
            };

            int i = 0;
            foreach (var food in foods)
            {
                if (food == null) continue;
                var pos = slots[i % slots.Length];
                var obj = Instantiate(food, root.transform);
                obj.transform.localPosition = pos;
                obj.transform.localRotation = Quaternion.identity;
                obj.transform.localScale = Vector3.one * 0.5f;
                FixMaterials(obj);
                _spawned.Add(obj);
                i++;
            }

            Debug.Log($"[StoreBuilder] Decorazioni Cute Supermarket Lite: {i} oggetti su {slots.Length} slot");
        }

        // ── RESTAURANT ────────────────────────────────────────────────

        private void BuildRestaurant(GameObject root)
        {
            // Tables (interactive — sit & eat)
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(-3f, 0, 2f), new Vector3(0, 45, 0), new Vector3(1.2f, 0.4f, 0.8f),
                "table_1", "Tavolo 1", "use", "🍽️", "hunger", 25, "meal");
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(-3f, 0, -1f), new Vector3(0, -30, 0), new Vector3(1.2f, 0.4f, 0.8f),
                "table_2", "Tavolo 2", "use", "🍽️", "hunger", 25, "meal");
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(0, 0, 2f), new Vector3(0, 15, 0), new Vector3(1.2f, 0.4f, 0.8f),
                "table_3", "Tavolo 3", "use", "🍽️", "hunger", 25, "meal");
            Spawn(_shelfEndPrefab, root.transform, new Vector3(0, 0, -1f), new Vector3(0, -60, 0), new Vector3(1.2f, 0.4f, 0.8f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(3f, 0, 2f), new Vector3(0, 30, 0), new Vector3(1.2f, 0.4f, 0.8f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(3f, 0, -1f), new Vector3(0, -45, 0), new Vector3(1.2f, 0.4f, 0.8f));

            // Table legs / decorative pillars
            Spawn(_columnPrefab, root.transform, new Vector3(-3f, 0, 2f), Vector3.zero, new Vector3(0.3f, 0.6f, 0.3f));
            Spawn(_columnPrefab, root.transform, new Vector3(0, 0, 2f), Vector3.zero, new Vector3(0.3f, 0.6f, 0.3f));
            Spawn(_columnPrefab, root.transform, new Vector3(3f, 0, 2f), Vector3.zero, new Vector3(0.3f, 0.6f, 0.3f));

            // Kitchen counter (interactive — order)
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(0, 0, 3.2f), Vector3.zero, new Vector3(1.5f, 1f, 1f),
                "kitchen", "Cucina", "buy", "👨‍🍳", "hunger", 30, "pizza");

            // Wine shelves (interactive — drinks)
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(-3f, 0, 3.5f), Vector3.zero, new Vector3(1f, 1.2f, 1f),
                "wine_left", "Vini", "collect", "🍷", "thirst", 20, "wine");
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(3f, 0, 3.5f), Vector3.zero, new Vector3(1f, 1.2f, 1f),
                "wine_right", "Birre", "collect", "🍺", "thirst", 15, "beer");

            // Display: appetizers
            SpawnInteractive(_displayBreadPrefab, root.transform, new Vector3(-2f, 0, -2.5f), Vector3.zero, Vector3.one,
                "appetizers", "Antipasti", "collect", "🧀", "hunger", 12, "cheese");
            SpawnInteractive(_displayFruitPrefab, root.transform, new Vector3(2f, 0, -2.5f), Vector3.zero, Vector3.one,
                "dessert", "Dolci", "collect", "🍰", "fun", 10, "cake");

            // NPCs: waiter + chef
            SpawnNPC(root.transform, new Vector3(-1f, 0, -1f), "waiter", "Cameriere",
                "employee", "🧑‍🍳", new[] { "Buonasera! Il nostro piatto del giorno è la pasta alla carbonara.", "Avete provato il nostro tiramisù? È eccezionale!" });
            SpawnNPC(root.transform, new Vector3(0, 0, 2.5f), "chef", "Chef",
                "employee", "👨‍🍳", new[] { "Benvenuto in cucina!", "Sto preparando qualcosa di speciale per te.", "Il segreto è usare ingredienti freschi." });
        }

        // ── GYM ───────────────────────────────────────────────────────

        private void BuildGym(GameObject root)
        {
            // Weight machines (interactive — exercise)
            SpawnInteractive(_freezerPrefab, root.transform, new Vector3(4f, 0, 3f), Vector3.zero, new Vector3(0.8f, 0.6f, 1.2f),
                "bench_1", "Panca piana", "use", "💪", "energy", 20, "");
            SpawnInteractive(_freezerPrefab, root.transform, new Vector3(4f, 0, 1f), Vector3.zero, new Vector3(0.8f, 0.6f, 1.2f),
                "bench_2", "Chest press", "use", "🏋️", "energy", 25, "");
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(4f, 0, -1f), new Vector3(0, 90, 0), new Vector3(1f, 0.8f, 0.6f));
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(4f, 0, -3f), new Vector3(0, 90, 0), new Vector3(1f, 0.8f, 0.6f));

            // Weight racks (back wall)
            Spawn(_shelfBoxesPrefab, root.transform, new Vector3(-3f, 0, 3.5f), Vector3.zero, new Vector3(1f, 0.7f, 1f));
            Spawn(_shelfBoxesPrefab, root.transform, new Vector3(0, 0, 3.5f), Vector3.zero, new Vector3(1f, 0.7f, 1f));
            Spawn(_shelfBoxesPrefab, root.transform, new Vector3(3f, 0, 3.5f), Vector3.zero, new Vector3(1f, 0.7f, 1f));

            // Open floor markers
            Spawn(_columnPrefab, root.transform, new Vector3(-2.5f, 0, 0), Vector3.zero, new Vector3(0.5f, 1.5f, 0.5f));
            Spawn(_columnPrefab, root.transform, new Vector3(2.5f, 0, 0), Vector3.zero, new Vector3(0.5f, 1.5f, 0.5f));

            // Water station (interactive — drink)
            SpawnInteractive(_displayFruitPrefab, root.transform, new Vector3(-3.5f, 0, -2f), Vector3.zero, new Vector3(0.6f, 0.8f, 0.6f),
                "water", "Fontanella", "collect", "💧", "thirst", 25, "water");

            // Reception
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(3.5f, 0, -3f), Vector3.zero, Vector3.one,
                "reception", "Reception", "talk", "🧑‍💼", "", 0, "");

            // Cleaning cart
            Spawn(_shoppingCartPrefab, root.transform, new Vector3(-4f, 0, -3f), new Vector3(0, 90, 0), new Vector3(0.8f, 0.8f, 0.8f));

            // NPC: trainer
            SpawnNPC(root.transform, new Vector3(-1f, 0, 0), "trainer", "Allenatore",
                "employee", "💪", new[] { "Ciao! Vuoi fare un po' di allenamento?", "L'esercizio fa bene alla salute!", "Prova il nuovo attrezzo, ti piacerà!" });
        }

        // ── HOSPITAL ──────────────────────────────────────────────────

        private void BuildHospital(GameObject root)
        {
            // Beds (interactive — heal)
            SpawnInteractive(_freezerPrefab, root.transform, new Vector3(-3f, 0, 2f), new Vector3(0, 90, 0), new Vector3(0.8f, 0.5f, 1.5f),
                "bed_1", "Letto 1", "heal", "🛏️", "energy", 30, "");
            SpawnInteractive(_freezerPrefab, root.transform, new Vector3(-3f, 0, 0f), new Vector3(0, 90, 0), new Vector3(0.8f, 0.5f, 1.5f),
                "bed_2", "Letto 2", "heal", "🛏️", "energy", 30, "");
            Spawn(_freezerPrefab, root.transform, new Vector3(-3f, 0, -2f), new Vector3(0, 90, 0), new Vector3(0.8f, 0.5f, 1.5f));

            // IV stands
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(-2f, 0, 2f), Vector3.zero, new Vector3(0.3f, 1.2f, 0.3f));
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(-2f, 0, 0f), Vector3.zero, new Vector3(0.3f, 1.2f, 0.3f));
            Spawn(_freezersStandingPrefab, root.transform, new Vector3(-2f, 0, -2f), Vector3.zero, new Vector3(0.3f, 1.2f, 0.3f));

            // Reception (interactive — check in)
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(3.5f, 0, -3f), Vector3.zero, Vector3.one,
                "reception", "Accettazione", "talk", "🏥", "", 0, "");

            // Medical supply shelves
            Spawn(_shelfBoxesPrefab, root.transform, new Vector3(-2f, 0, 3.5f), Vector3.zero, new Vector3(1f, 1.2f, 0.6f));
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(1f, 0, 3.5f), Vector3.zero, new Vector3(1f, 1.2f, 0.6f),
                "pharmacy", "Farmacia", "collect", "💊", "hygiene", 20, "medicine");
            Spawn(_shelfEndPrefab, root.transform, new Vector3(3f, 0, 3.5f), Vector3.zero, new Vector3(0.8f, 1.2f, 0.6f));

            // Pillar
            Spawn(_columnPrefab, root.transform, new Vector3(1.5f, 0, 1f), Vector3.zero, Vector3.one);

            // Medicine cabinets (interactive)
            SpawnInteractive(_displayBreadPrefab, root.transform, new Vector3(3.5f, 0, 1f), Vector3.zero, new Vector3(0.6f, 0.9f, 0.6f),
                "cabinet_1", "Medicina 1", "collect", "🩹", "hygiene", 15, "bandage");
            SpawnInteractive(_displayFruitPrefab, root.transform, new Vector3(3.5f, 0, -1f), Vector3.zero, new Vector3(0.6f, 0.9f, 0.6f),
                "cabinet_2", "Medicina 2", "collect", "💉", "energy", 18, "vitamin");

            // NPC: doctor
            SpawnNPC(root.transform, new Vector3(1f, 0, 0), "doctor", "Dottore",
                "employee", "🩺", new[] { "Buongiorno, sono il Dott. Rossi.", "Come posso aiutarla oggi?", "Si accomodi, La visito subito." });
        }

        // ── BAR / CAFÉ ────────────────────────────────────────────────

        private void BuildBar(GameObject root)
        {
            // Bar counter (interactive — order)
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(-3f, 0, 3f), new Vector3(0, 0, 0), new Vector3(1.5f, 0.8f, 0.6f),
                "counter_l", "Bancone sinistro", "buy", "🍸", "thirst", 20, "cocktail");
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(0, 0, 3f), new Vector3(0, 0, 0), new Vector3(1.5f, 0.8f, 0.6f),
                "counter_c", "Bancone centrale", "buy", "☕", "thirst", 15, "coffee");
            SpawnInteractive(_shelfEndPrefab, root.transform, new Vector3(3f, 0, 3f), new Vector3(0, 0, 0), new Vector3(1.5f, 0.8f, 0.6f),
                "counter_r", "Bancone destro", "buy", "🍺", "thirst", 18, "beer");

            // Cash register
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(3.5f, 0.8f, 3f), Vector3.zero, new Vector3(0.8f, 0.8f, 0.8f),
                "cash", "Cassa", "buy", "💰", "", 0, "");

            // Bottle shelves (interactive — collect)
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(-3f, 0, 3.8f), Vector3.zero, new Vector3(1f, 1.4f, 0.5f),
                "bottles_l", "Vini", "collect", "🍷", "thirst", 22, "wine");
            SpawnInteractive(_shelfBoxesPrefab, root.transform, new Vector3(0, 0, 3.8f), Vector3.zero, new Vector3(1f, 1.4f, 0.5f),
                "bottles_c", "Birre artigianali", "collect", "🍺", "thirst", 18, "craft_beer");

            // High tables
            Spawn(_shelfEndPrefab, root.transform, new Vector3(-3f, 0, 0.5f), new Vector3(0, 90, 0), new Vector3(0.5f, 0.7f, 0.5f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(-3f, 0, -1.5f), new Vector3(0, 90, 0), new Vector3(0.5f, 0.7f, 0.5f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(0, 0, 0.5f), new Vector3(0, 90, 0), new Vector3(0.5f, 0.7f, 0.5f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(0, 0, -1.5f), new Vector3(0, 90, 0), new Vector3(0.5f, 0.7f, 0.5f));

            // Pillars
            Spawn(_columnPrefab, root.transform, new Vector3(-2f, 0, 1.5f), Vector3.zero, new Vector3(0.4f, 1.2f, 0.4f));
            Spawn(_columnPrefab, root.transform, new Vector3(2f, 0, 1.5f), Vector3.zero, new Vector3(0.4f, 1.2f, 0.4f));

            // Pastry displays (interactive)
            SpawnInteractive(_displayFruitPrefab, root.transform, new Vector3(-2f, 0, -3f), Vector3.zero, new Vector3(0.8f, 0.6f, 0.8f),
                "pastry", "Pasticceria", "collect", "🥐", "hunger", 12, "pastry");
            SpawnInteractive(_displayBreadPrefab, root.transform, new Vector3(2f, 0, -3f), Vector3.zero, new Vector3(0.8f, 0.6f, 0.8f),
                "snacks", "Stuzzichini", "collect", "🧀", "hunger", 10, "snack");

            // NPC: bartender
            SpawnNPC(root.transform, new Vector3(0, 0, 2.5f), "bartender", "Barista",
                "employee", "🍸", new[] { "Cosa desideri?", "Il nostro caffè è il migliore della città!", "Prova l'aperitivo della casa." });
        }

        // ── LIBRARY ───────────────────────────────────────────────────

        private void BuildLibrary(GameObject root)
        {
            // Bookshelves (interactive — read books)
            SpawnInteractive(_shelfBoxesPrefab, root.transform, new Vector3(-3.5f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f),
                "books_1", "Scaffale narrativa", "collect", "📚", "fun", 15, "novel");
            SpawnInteractive(_shelfBoxesPrefab, root.transform, new Vector3(-2f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f),
                "books_2", "Scaffale fantascienza", "collect", "🚀", "fun", 18, "scifi");
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(-0.5f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f),
                "books_3", "Scaffale storia", "collect", "📜", "fun", 12, "history");
            SpawnInteractive(_shelfBagsPrefab, root.transform, new Vector3(1f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f),
                "books_4", "Scaffale scienza", "collect", "🔬", "fun", 16, "science");
            Spawn(_shelfEndPrefab, root.transform, new Vector3(2.5f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(4f, 0, 3.2f), Vector3.zero, new Vector3(0.8f, 1.4f, 0.6f));

            // Reading tables
            Spawn(_shelfEndPrefab, root.transform, new Vector3(-2f, 0, 0.5f), new Vector3(0, 0, 0), new Vector3(1.2f, 0.35f, 0.8f));
            Spawn(_shelfEndPrefab, root.transform, new Vector3(2f, 0, 0.5f), new Vector3(0, 0, 0), new Vector3(1.2f, 0.35f, 0.8f));

            // Librarian desk (interactive — ask for help)
            SpawnInteractive(_cashRegisterPrefab, root.transform, new Vector3(3.5f, 0, -3f), Vector3.zero, Vector3.one,
                "librarian", "Bibliotecario", "talk", "🧑‍🏫", "", 0, "");

            // Columns
            Spawn(_columnPrefab, root.transform, new Vector3(-1f, 0, 1.5f), Vector3.zero, Vector3.one);
            Spawn(_columnPrefab, root.transform, new Vector3(1f, 0, 1.5f), Vector3.zero, Vector3.one);

            // Magazine rack (interactive)
            SpawnInteractive(_displayBreadPrefab, root.transform, new Vector3(-3.5f, 0, -2f), Vector3.zero, new Vector3(0.6f, 0.8f, 0.6f),
                "magazines", "Riviste", "collect", "📰", "fun", 8, "magazine");

            // NPC: librarian
            SpawnNPC(root.transform, new Vector3(2.5f, 0, -2f), "librarian", "Bibliotecaria",
                "employee", "📚", new[] { "Benvenuto in biblioteca!", "Silenzio, per favore.", "Hai bisogno di aiuto per trovare un libro?" });
        }

        // ── Utility ───────────────────────────────────────────────────

        private void Spawn(GameObject prefab, Transform parent, Vector3 pos, Vector3 rot, Vector3 scale)
        {
            if (prefab == null) return;
            var obj = Instantiate(prefab, parent);
            obj.transform.localPosition = pos;
            obj.transform.localRotation = Quaternion.Euler(rot);
            obj.transform.localScale = scale;
            FixMaterials(obj);
            _spawned.Add(obj);
        }

        // ── Asset-set non-Kenney (CuteStore) ──────────────────────────────

        /// <summary>
        /// Se useCuteStore carica il prefab da Resources/CuteStore/{name} se disponibile,
        /// altrimenti genera a runtime una primitiva low-poly colorata (palette non-Kenney).
        /// Se !useCuteStore carica da Resources/KenneyMiniMarket/{name}.
        /// </summary>
        private GameObject LoadCuteOrRuntime(string name, Color color, Vector3 scale)
        {
            if (!useCuteStore)
            {
                var kenney = KenneyReg != null ? KenneyReg.Get(name) : null;
                if (kenney != null) return kenney;
                return RuntimePrimitive(name, color, scale);
            }

            var cached = Resources.Load<GameObject>("CuteStore/" + name);
            if (cached != null) return cached;

            return RuntimePrimitive(name, color, scale);
        }

        private GameObject RuntimePrimitive(string name, Color color, Vector3 scale)
        {
            PrimitiveType prim = PrimitiveType.Cube;
            if (name == "column") prim = PrimitiveType.Cylinder;
            else if (name == "character-employee") prim = PrimitiveType.Capsule;

            var go = GameObject.CreatePrimitive(prim);
            var col = go.GetComponent<Collider>();
            if (col != null)
            {
#if UNITY_EDITOR
                Object.DestroyImmediate(col);
#else
                Object.Destroy(col);
#endif
            }
            go.transform.localPosition = Vector3.zero;
            go.transform.localRotation = Quaternion.identity;
            go.transform.localScale = scale;
            go.name = name;

            var rend = go.GetComponent<MeshRenderer>();
            var shader = Shader.Find("Universal Render Pipeline/Lit")
                         ?? Shader.Find("Standard")
                         ?? Shader.Find("Diffuse");
            var mat = new Material(shader);
            if (mat.HasProperty("_BaseColor")) mat.SetColor("_BaseColor", color);
            if (mat.HasProperty("_Color")) mat.SetColor("_Color", color);
            if (_colormap != null && mat.HasProperty("_BaseMap")) mat.SetTexture("_BaseMap", _colormap);
            if (_colormap != null && mat.HasProperty("_MainTex")) mat.SetTexture("_MainTex", _colormap);
            rend.material = mat;

            Debug.Log($"[StoreBuilder] CuteStore runtime primitive: {name}");
            return go;
        }

        /// <summary>
        /// Spawn + attach InteractionComponent. The object becomes interactive.
        /// </summary>
        private GameObject SpawnInteractive(GameObject prefab, Transform parent, Vector3 pos, Vector3 rot,
            Vector3 scale, string id, string name, string action, string emoji,
            string need = "", int gain = 0, string itemId = "")
        {
            if (prefab == null) return null;
            var obj = Instantiate(prefab, parent);
            obj.transform.localPosition = pos;
            obj.transform.localRotation = Quaternion.Euler(rot);
            obj.transform.localScale = scale;
            FixMaterials(obj);
            _spawned.Add(obj);

            var ic = obj.AddComponent<InteractionComponent>();
            ic.interactionId = id;
            ic.interactionName = name;
            ic.action = action;
            ic.emoji = emoji;
            ic.need = need;
            ic.gain = gain;
            ic.itemId = itemId;
            return obj;
        }

        /// <summary>
        /// Spawn an NPC with the character-employee prefab.
        /// Adds NavMeshAgent + NPC component.
        /// </summary>
        private GameObject SpawnNPC(Transform parent, Vector3 pos, string id, string name,
            string role, string emoji, string[] dialogue)
        {
            if (_characterPrefab == null)
            {
                Debug.LogWarning("[StoreBuilder] character-employee prefab not found, skipping NPC");
                return null;
            }

            var obj = Instantiate(_characterPrefab, parent);
            obj.transform.localPosition = pos;
            obj.transform.localRotation = Quaternion.identity;
            obj.transform.localScale = Vector3.one;
            FixMaterials(obj);
            obj.name = $"NPC_{id}";
            _spawned.Add(obj);

            // Add NavMeshAgent for pathfinding
            var agent = obj.AddComponent<UnityEngine.AI.NavMeshAgent>();
            agent.speed = 1.5f;
            agent.stoppingDistance = 0.3f;
            agent.radius = 0.3f;
            agent.height = 1.5f;

            // Add NPC component
            var npc = obj.AddComponent<NPC>();
            npc.npcId = id;
            npc.npcName = name;
            npc.role = role;
            npc.emoji = emoji;
            npc.dialogueLines = dialogue;
            npc.interactionRange = 2.5f;
            npc.dialogueRange = 4f;

            return obj;
        }

        public void ClearStore()
        {
            foreach (var obj in _spawned)
            {
                if (obj != null) Destroy(obj);
            }
            _spawned.Clear();

            var existing = GameObject.Find("StoreInterior");
            if (existing != null) Destroy(existing);
        }
    }
}

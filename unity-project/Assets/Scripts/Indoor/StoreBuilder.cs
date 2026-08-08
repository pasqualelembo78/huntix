using UnityEngine;
using System.Collections.Generic;

namespace Huntix.Indoor
{
    /// <summary>
    /// Costruisce dinamicamente l'interno del negozio usando gli asset Kenney Mini Market (CC0).
    /// Layout diversi in base al tipo di POI: ristorante, palestra, ospedale, bar, supermercato.
    /// Carica i prefabs da Resources/KenneyMiniMarket/ a runtime.
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

        private void LoadPrefabs()
        {
            if (_loaded) return;
            const string path = "KenneyMiniMarket/";
            _floorPrefab = Resources.Load<GameObject>(path + "floor");
            _wallPrefab = Resources.Load<GameObject>(path + "wall");
            _wallDoorPrefab = Resources.Load<GameObject>(path + "wall-door-rotate");
            _wallWindowPrefab = Resources.Load<GameObject>(path + "wall-window");
            _wallCornerPrefab = Resources.Load<GameObject>(path + "wall-corner");
            _cashRegisterPrefab = Resources.Load<GameObject>(path + "cash-register");
            _shelfBagsPrefab = Resources.Load<GameObject>(path + "shelf-bags");
            _shelfBoxesPrefab = Resources.Load<GameObject>(path + "shelf-boxes");
            _shelfEndPrefab = Resources.Load<GameObject>(path + "shelf-end");
            _freezerPrefab = Resources.Load<GameObject>(path + "freezer");
            _freezersStandingPrefab = Resources.Load<GameObject>(path + "freezers-standing");
            _displayBreadPrefab = Resources.Load<GameObject>(path + "display-bread");
            _displayFruitPrefab = Resources.Load<GameObject>(path + "display-fruit");
            _shoppingCartPrefab = Resources.Load<GameObject>(path + "shopping-cart");
            _columnPrefab = Resources.Load<GameObject>(path + "column");
            _characterPrefab = Resources.Load<GameObject>(path + "character-employee");
            _loaded = true;
            Debug.Log("[StoreBuilder] Prefabs loaded from Resources/KenneyMiniMarket/");
        }

        // ── Main entry point ──────────────────────────────────────────

        public void BuildStore(string poiType = "")
        {
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
            _spawned.Add(obj);
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

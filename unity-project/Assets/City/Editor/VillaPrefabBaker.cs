// Solo editor: crea il prefab completo "VillaVittorianaCompleta" usando i
// modelli SENDU (Cozy Interiors + Builder) già importati e licenziati.
// Non fa parte del player: sta in Assets/City/Editor, escluso dalla build Unity.
#if UNITY_EDITOR
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using City.Interior;
using City.Interior.Visit;

namespace City.Interior.Victorian.Editor
{
    /// <summary>Baker editoriale: compone una villa vittoriana completa e la
    /// salva come prefab in Resources. Il prefab contiene guscio, pavimenti,
    /// soffitti, arredi SENDU, luci, collider e cinque HouseEggSpot. Zone,
    /// etichette e trigger d'uscita restano al runtime (dipendono dalla casa).
    /// </summary>
    public static class VillaPrefabBaker
    {
        public const string PrefabPath =
            "Assets/Resources/Villas/VillaVittorianaCompleta.prefab";
        public const string ResourcePath = "Villas/VillaVittorianaCompleta";
        private const string Sendu = "HouseInteriors/Sendu/FBX/";
        private const string MatFolder = "Assets/Resources/Villas/Materials";

        private static Transform _root;
        private static readonly Dictionary<string, Material> Mats =
            new Dictionary<string, Material>();

        [MenuItem("Huntix/Bake Villa Vittoriana completa")]
        public static void Bake()
        {
            EnsureFolder("Assets/Resources", "Villas");
            EnsureFolder("Assets/Resources/Villas", "Materials");
            EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var go = new GameObject("VillaVittorianaCompleta");
            _root = go.transform;

            Material cream = Mat("Villa_Cream", new Color(0.86f, 0.79f, 0.67f));
            Material wood = Mat("Villa_Wood", new Color(0.34f, 0.20f, 0.11f));
            Material red = Mat("Villa_Red", new Color(0.46f, 0.09f, 0.12f));
            Material green = Mat("Villa_Green", new Color(0.12f, 0.28f, 0.20f));
            Material gold = Mat("Villa_Gold", new Color(0.74f, 0.56f, 0.20f));
            Material stone = Mat("Villa_Stone", new Color(0.48f, 0.40f, 0.32f));
            Material glow = Mat("Villa_Glow", new Color(1f, 0.93f, 0.74f), true);

            BuildColliders();
            BuildShell(cream, wood, gold, stone);
            BuildFoyer(cream, wood, red, gold, glow, stone);
            BuildSalon(wood, red, gold, glow);
            BuildLibrary(wood, green, gold, glow);
            BuildDining(wood, red, gold, glow);
            BuildCityInside(cream, wood, gold, glow, stone);
            BuildMasterBedroom(cream, wood, red, gold, glow);
            BuildBathroom(cream, wood, gold, glow, stone);
            BuildKitchen(cream, wood, red, gold, glow, stone);
            BuildKidsAndStudy(cream, wood, green, gold, glow);
            BuildExtraComforts(wood, red, gold, glow, stone);
            BuildExitDoor(wood, gold, glow);
            BuildLighting(gold, glow);
            BuildEggSpots(wood, gold);

            GameObject saved = PrefabUtility.SaveAsPrefabAsset(go, PrefabPath);
            if (saved == null)
                throw new System.Exception("Salvataggio prefab villa fallito: " + PrefabPath);

            Object.DestroyImmediate(go);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[VillaPrefabBaker] prefab completo creato: " + PrefabPath);
        }

        // ── VILLA DA 300 MQ IN 3 LIVELLI (casa configurata) ──────────
        // Nuovo prefab ADDITIVO: la reggia TARDIS di sopra resta intatta e
        // continua a essere il fallback delle case non configurate.
        public const string Prefab300Path =
            "Assets/Resources/Villas/Villa300mqTreLivelli.prefab";
        public const string Resource300Path = "Villas/Villa300mqTreLivelli";
        public const int Piani300 = 3;
        public const float LatoPiano300 = 10f;      // 10 x 10 m = 100 mq
        public const float AltezzaPiano300 = 3.5f; // 3 piani = 10,5 m
        public const float AreaPiano300 = LatoPiano300 * LatoPiano300;
        public const float AreaTotale300 = Piani300 * AreaPiano300; // 300 mq

        [MenuItem("Huntix/Bake Villa 300 mq (3 livelli)")]
        public static void Bake300()
        {
            EnsureFolder("Assets/Resources", "Villas");
            EnsureFolder("Assets/Resources/Villas", "Materials");
            EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var go = new GameObject("Villa300mqTreLivelli");
            _root = go.transform;

            Material cream = Mat("Villa_Cream", new Color(0.86f, 0.79f, 0.67f));
            Material wood = Mat("Villa_Wood", new Color(0.34f, 0.20f, 0.11f));
            Material red = Mat("Villa_Red", new Color(0.46f, 0.09f, 0.12f));
            Material green = Mat("Villa_Green", new Color(0.12f, 0.28f, 0.20f));
            Material gold = Mat("Villa_Gold", new Color(0.74f, 0.56f, 0.20f));
            Material stone = Mat("Villa_Stone", new Color(0.48f, 0.40f, 0.32f));
            Material glow = Mat("Villa_Glow", new Color(1f, 0.93f, 0.74f), true);

            Build300Piani(cream, wood, stone, gold, glow);
            Build300Arredi(wood, red, green, gold, glow, stone);
            Build300Uova(wood, gold);
            Build300Uscita(wood, gold, glow);

            GameObject saved = PrefabUtility.SaveAsPrefabAsset(go, Prefab300Path);
            if (saved == null)
                throw new System.Exception("Salvataggio prefab villa 300 mq fallito: " + Prefab300Path);

            Object.DestroyImmediate(go);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[VillaPrefabBaker] villa 300 mq / 3 piani creata: " + Prefab300Path +
                " (" + AreaTotale300.ToString("F0") + " mq)");
        }

        /// <summary>Coordinate locali di un oggetto del piano. La radice del
        /// prefab coincide con la radice della tasca: il piano 1 è a quota 0.</summary>
        private static Vector3 InPiano(int floor, float x, float y, float z)
        {
            return new Vector3(x, floor * AltezzaPiano300 + y, z);
        }

        private static void Build300Piani(Material cream, Material wood,
            Material stone, Material gold, Material glow)
        {
            const float lato = LatoPiano300;
            const float h = AltezzaPiano300;
            float half = lato * 0.5f;
            for (int f = 0; f < Piani300; f++)
            {
                float y = f * h;
                // Pavimento calpestabile: layer 0, come ogni altro interno,
                // così il GroundSnapper trova il suolo del piano giusto.
                Collider("Collider300_Pavimento" + f, InPiano(f, 0f, -0.15f, 0f),
                    new Vector3(lato, 0.3f, lato), 0);
                Panel("Shell/CI_Shell_Floor_Herringbone_2m", "Pavimento300_" + f,
                    InPiano(f, 0f, -0.15f, 0f), new Vector3(lato, 0.3f, lato), 0f,
                    f % 2 == 0 ? wood : stone);

                Collider("Collider300_PareteN" + f, InPiano(f, 0f, h * 0.5f, -half),
                    new Vector3(lato, h, 0.3f), 8);
                Panel("Shell/CI_Shell_Wall_Window_2m", "Parete300_N" + f,
                    InPiano(f, 0f, h * 0.5f, -half), new Vector3(lato, h, 0.3f), 180f, cream);
                Collider("Collider300_PareteS" + f, InPiano(f, 0f, h * 0.5f, half),
                    new Vector3(lato, h, 0.3f), 8);
                Panel(f == 0 ? "Shell/CI_Shell_Wall_DoorDouble_2m"
                             : "Shell/CI_Shell_Wall_Window_2m",
                    "Parete300_S" + f, InPiano(f, 0f, h * 0.5f, half),
                    new Vector3(lato, h, 0.3f), 0f, cream);
                Collider("Collider300_PareteE" + f, InPiano(f, half, h * 0.5f, 0f),
                    new Vector3(0.3f, h, lato), 8);
                Panel("Shell/CI_Shell_Wall_Window_2m", "Parete300_E" + f,
                    InPiano(f, half, h * 0.5f, 0f), new Vector3(0.3f, h, lato), -90f, cream);
                Collider("Collider300_PareteO" + f, InPiano(f, -half, h * 0.5f, 0f),
                    new Vector3(0.3f, h, lato), 8);
                Panel("Shell/CI_Shell_Wall_Window_2m", "Parete300_O" + f,
                    InPiano(f, -half, h * 0.5f, 0f), new Vector3(0.3f, h, lato), 90f, cream);

                Panel("Shell/CI_Shell_Ceiling_Flat_2m", "Soffitto300_" + f,
                    InPiano(f, 0f, h - 0.15f, 0f), new Vector3(lato, 0.3f, lato), 0f, cream);
                LightAt("Luce300_Piano" + f, InPiano(f, 0f, h - 0.9f, 0f), 1.05f, 15f);
                Primitive(PrimitiveType.Sphere, "Plafoniera300_" + f,
                    InPiano(f, 0f, h - 0.55f, 0f), new Vector3(1.1f, 0.3f, 1.1f), glow);

                if (f < Piani300 - 1) Build300ScalaSu(f, stone, gold);
                if (f > 0) Build300ScalaGiù(f, gold);
            }
        }

        /// <summary>Scale visive + trigger che portano al piano superiore.
        /// Nessun sistema di fisica nuovo: si riusa InteriorTeleport come
        /// per tutti gli altri gate dell'interior.</summary>
        private static void Build300ScalaSu(int floor, Material stone, Material gold)
        {
            for (int i = 0; i < 6; i++)
                Primitive(PrimitiveType.Cube, "Gradino300_Su" + floor + "_" + i,
                    InPiano(floor, -2.8f, 0.3f + i * 0.52f, -2.2f - i * 0.42f),
                    new Vector3(2f, 0.26f, 0.5f), stone);
            var go = new GameObject("Scala300_Su" + floor);
            go.transform.SetParent(_root, false);
            go.transform.localPosition = InPiano(floor, -2.8f, 1.15f, -3.6f);
            var col = go.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(2.6f, 2.2f, 1.4f);
            var stairs = go.AddComponent<City.Interior.Victorian.GrandVillaStairs>();
            stairs.targetFloor = floor + 1;
            stairs.down = false;
            Primitive(PrimitiveType.Sphere, "LuceScala300_Su" + floor,
                InPiano(floor, -2.8f, AltezzaPiano300 - 0.5f, -3.6f),
                new Vector3(0.5f, 0.5f, 0.5f), gold);
        }

        private static void Build300ScalaGiù(int floor, Material gold)
        {
            var go = new GameObject("Scala300_Giu" + floor);
            go.transform.SetParent(_root, false);
            go.transform.localPosition = InPiano(floor, 2.6f, 1.15f, -3.6f);
            var col = go.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(2.4f, 2.2f, 1.4f);
            var stairs = go.AddComponent<City.Interior.Victorian.GrandVillaStairs>();
            stairs.targetFloor = floor - 1;
            stairs.down = true;
            Primitive(PrimitiveType.Sphere, "LuceScala300_Giu" + floor,
                InPiano(floor, 2.6f, AltezzaPiano300 - 0.5f, -3.6f),
                new Vector3(0.5f, 0.5f, 0.5f), gold);
        }

        private static void Build300Arredi(Material wood, Material red,
            Material green, Material gold, Material glow, Material stone)
        {
            // PIANO 1 — salone + cucina + atrio.
            Model("Decor/CI_Decor_Rug_LargeRect", "TappetoSalone300",
                InPiano(0, 0f, 0.06f, 0.5f), new Vector3(6f, 0.12f, 5f), 0f);
            Model("Living/CI_Living_Sofa_ThreeSeat", "DivanoSalone300",
                InPiano(0, -2.5f, 0f, 2f), new Vector3(3.2f, 1.8f, 1.6f), 0f);
            Model("Living/CI_Living_CoffeeTable", "TavolinoSalone300",
                InPiano(0, 0f, 0f, 1.2f), new Vector3(1.6f, 0.9f, 1.2f), 0f);
            Model("Decor/CI_Decor_CeilingLight_ThreeArm", "LampadarioSalone300",
                InPiano(0, 0f, 2.4f, 0.5f), new Vector3(2.6f, 1.2f, 2.6f), 0f);
            Model("Kitchen/CI_Kitchen_RangeCooker", "FornoCucina300",
                InPiano(0, 3.8f, 0f, -2.5f), new Vector3(1.3f, 1.7f, 1.3f), -90f);
            Model("Kitchen/CI_Kitchen_Table", "TavoloCucina300",
                InPiano(0, 2f, 0f, -0.8f), new Vector3(1.8f, 1.2f, 1.8f), 0f);
            Model("Kitchen/CI_Kitchen_Fridge_Retro", "FrigoCucina300",
                InPiano(0, 4.1f, 0f, 1.5f), new Vector3(1.2f, 2.8f, 1.2f), -90f);

            // PIANO 2 — camera + bagno + studio.
            Model("Bedroom/CI_Bedroom_Bed_Double", "LettoCamera300",
                InPiano(1, -2f, 0f, 1.8f), new Vector3(3f, 1.6f, 4f), 0f);
            Model("Bedroom/CI_Bedroom_Wardrobe_Double", "ArmadioCamera300",
                InPiano(1, -4f, 0f, -1.5f), new Vector3(1.8f, 3f, 2.4f), 90f);
            Model("Bathroom/CI_Bathroom_Bathtub_Clawfoot", "VascaBagno300",
                InPiano(1, 3f, 0f, -2.5f), new Vector3(2.6f, 1.6f, 1.6f), 90f);
            Model("Bathroom/CI_Bathroom_Vanity_Basin", "LavandinoBagno300",
                InPiano(1, 4f, 0f, 0.5f), new Vector3(1.8f, 1.6f, 1f), 0f);
            Model("Office/CI_Office_Desk_Writing", "ScrivaniaStudio300",
                InPiano(1, 0.5f, 0f, -3.5f), new Vector3(2.8f, 1.5f, 1.4f), 0f);
            Model("Office/CI_Office_Bookcase_Glazed", "LibreriaStudio300",
                InPiano(1, 3f, 0f, 3.5f), new Vector3(1.8f, 3f, 2.4f), 90f);

            // PIANO 3 — biblioteca + stanza bambini + terrazza.
            Model("Office/CI_Office_Bookcase_Glazed", "LibreriaBiblioteca300",
                InPiano(2, -4f, 0f, 0f), new Vector3(1.8f, 3f, 5f), 90f);
            Model("Living/CI_Living_Bookcase_Low", "LibreriaBassa300",
                InPiano(2, -1f, 0f, 3.8f), new Vector3(2.6f, 1.8f, 1f), 0f);
            Model("Kids/CI_Kids_BunkBed", "LettiBambini300",
                InPiano(2, 3f, 0f, -2.2f), new Vector3(2.4f, 3f, 3.4f), 0f);
            Model("Kids/CI_Kids_ToyChest", "CassaGiochi300",
                InPiano(2, 0.5f, 0f, -3.8f), new Vector3(1.5f, 1.2f, 1.2f), 0f);
            Model("Cafe/CI_Cafe_Table_Bistro", "TavolinoTerrazza300",
                InPiano(2, 2f, 0f, 2f), new Vector3(1.4f, 1.3f, 1.4f), 0f);
            Model("Decor/CI_Decor_Plant_Monstera", "PiantaTerrazza300",
                InPiano(2, 4f, 0f, 3.6f), new Vector3(1.2f, 2f, 1.2f), 0f);
        }

        /// <summary>Nove nascondigli, tre per piano: ogni stanza ha il proprio
        /// uovo con luogo testuale che include il livello. Nessun id duplicato
        /// rispetto alla reggia TARDIS (prefabs mutually exclusive a runtime).</summary>
        private static void Uovo300(string id, string room, string thing,
            string model, Vector3 center, Vector3 size, Material mat)
        {
            Egg(id, room, thing, model, center, size, mat);
        }

        private static void Build300Uova(Material wood, Material gold)
        {
            Uovo300("salotto-piano1", "salone al piano 1", "cassetto del tavolino del salone",
                "Decor/CI_Decor_StorageBox_Pair", InPiano(0, 0f, 0f, 1.2f),
                new Vector3(1.2f, 0.9f, 0.9f), wood);
            Uovo300("cucina-piano1", "cucina al piano 1", "forno della cucina",
                "Kitchen/CI_Kitchen_RangeCooker", InPiano(0, 3.6f, 0f, -2.5f),
                new Vector3(1.2f, 1.2f, 1.2f), gold);
            Uovo300("atrio-piano1", "atrio al piano 1", "consolle dell'atrio",
                "Living/CI_Living_Sideboard", InPiano(0, 4f, 0f, 3.6f),
                new Vector3(1.4f, 1.2f, 0.9f), wood);

            Uovo300("camera-piano2", "camera al piano 2", "armadio della camera",
                "Bedroom/CI_Bedroom_Wardrobe_Double", InPiano(1, -4f, 0f, -1.5f),
                new Vector3(1.4f, 1.4f, 1.2f), wood);
            Uovo300("bagno-piano2", "bagno al piano 2", "mobile del lavandino",
                "Bathroom/CI_Bathroom_Vanity_Basin", InPiano(1, 4f, 0f, 0.5f),
                new Vector3(1.2f, 1.2f, 1f), gold);
            Uovo300("studio-piano2", "studio al piano 2", "scrivania dello studio",
                "Office/CI_Office_Desk_Writing", InPiano(1, 0.5f, 0f, -3.5f),
                new Vector3(1.4f, 1.1f, 1f), wood);

            Uovo300("biblioteca-piano3", "biblioteca al piano 3", "scaffale della biblioteca",
                "Office/CI_Office_Bookcase_Glazed", InPiano(2, -4f, 0f, 0f),
                new Vector3(1.2f, 1.4f, 1.8f), wood);
            Uovo300("bambini-piano3", "stanza dei bambini al piano 3", "cassa dei giocattoli",
                "Kids/CI_Kids_ToyChest", InPiano(2, 0.5f, 0f, -3.8f),
                new Vector3(1.2f, 1.1f, 1f), wood);
            Uovo300("terrazza-piano3", "terrazza al piano 3", "tavolino della terrazza",
                "Cafe/CI_Cafe_Table_Bistro", InPiano(2, 2f, 0f, 2f),
                new Vector3(1.1f, 1.1f, 1.1f), gold);
        }

        private static void Build300Uscita(Material wood, Material gold, Material glow)
        {
            Panel("Shell/CI_Shell_Wall_DoorDouble_2m", "PortaUscita300",
                InPiano(0, 0f, 1.75f, LatoPiano300 * 0.5f + 0.02f),
                new Vector3(3f, 3.5f, 0.4f), 180f, wood);
            Primitive(PrimitiveType.Sphere, "LuceUscita300",
                InPiano(0, 0f, 3f, LatoPiano300 * 0.5f - 0.4f),
                new Vector3(0.6f, 0.6f, 0.6f), glow);
            Primitive(PrimitiveType.Sphere, "ManigliaUscita300",
                InPiano(0, 1f, 1.4f, LatoPiano300 * 0.5f - 0.35f),
                new Vector3(0.3f, 0.3f, 0.3f), gold);
        }

        private static void EnsureFolder(string parent, string name)
        {
            string path = parent + "/" + name;
            if (!AssetDatabase.IsValidFolder(path)) AssetDatabase.CreateFolder(parent, name);
        }

        private static Material Mat(string name, Color color, bool emissive = false)
        {
            Material m;
            if (Mats.TryGetValue(name, out m) && m != null) return m;
            string path = MatFolder + "/" + name + ".mat";
            m = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (m == null)
            {
                Shader shader = Shader.Find("Universal Render Pipeline/Lit");
                if (shader == null) shader = Shader.Find("Standard");
                m = new Material(shader);
                m.name = name;
                AssetDatabase.CreateAsset(m, path);
            }
            string prop = m.HasProperty("_BaseColor") ? "_BaseColor" : "_Color";
            if (m.HasProperty(prop)) m.SetColor(prop, color);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.26f);
            if (emissive)
            {
                m.EnableKeyword("_EMISSION");
                if (m.HasProperty("_EmissionColor")) m.SetColor("_EmissionColor", color * 1.5f);
            }
            EditorUtility.SetDirty(m);
            Mats[name] = m;
            return m;
        }

        private static void BuildColliders()
        {
            Collider("Collider_Pavimento", new Vector3(0f, -0.6f, 0f),
                new Vector3(164f, 1.2f, 114f), 0);
            Collider("Collider_PareteFondo", new Vector3(0f, 6f, -57f),
                new Vector3(166f, 13f, 1f), 8);
            Collider("Collider_PareteIngresso", new Vector3(0f, 6f, 57f),
                new Vector3(166f, 13f, 1f), 8);
            Collider("Collider_PareteSinistra", new Vector3(-82f, 6f, 0f),
                new Vector3(1f, 13f, 116f), 8);
            Collider("Collider_PareteDestra", new Vector3(82f, 6f, 0f),
                new Vector3(1f, 13f, 116f), 8);
        }

        private static void Collider(string name, Vector3 center, Vector3 size, int layer)
        {
            var go = new GameObject(name);
            go.transform.SetParent(_root, false);
            go.transform.localPosition = center;
            go.layer = layer;
            var c = go.AddComponent<BoxCollider>();
            c.size = size;
        }

        private static void BuildShell(Material cream, Material wood,
            Material gold, Material stone)
        {
            // Pavimento continuo su TUTTA la tasca (164 x 114 m): nessun buco
            // fra gli ambienti. Il materiale cambia da stanza a stanza, così
            // la pianta si legge camminando anche con il soffitto chiuso.
            for (int ix = -4; ix <= 3; ix++)
                for (int iz = -4; iz <= 3; iz++)
                {
                    float x = ix * 20f + 10f;
                    float z = iz * 16f + 8f;
                    string res;
                    Material mat;
                    if (z > 2f && Mathf.Abs(x) < 36f)
                    {
                        res = "Shell/CI_Shell_Floor_Herringbone_2m"; mat = wood;
                    }
                    else if (z > 2f && x < -36f)
                    {
                        res = "Shell/CI_Shell_Floor_Carpet_2m"; mat = redFloor();
                    }
                    else if (z > 2f)
                    {
                        res = "Shell/CI_Shell_Floor_HexTile_2m"; mat = stone;
                    }
                    else if (Mathf.Abs(x) < 36f)
                    {
                        res = "Shell/CI_Shell_Floor_SquareTile_2m"; mat = stone;
                    }
                    else if (x < -36f)
                    {
                        res = "Shell/CI_Shell_Floor_Planks_2m"; mat = wood;
                    }
                    else
                    {
                        res = z > -22f ? "Shell/CI_Shell_Floor_HexTile_2m"
                                       : "Shell/CI_Shell_Floor_Planks_2m";
                        mat = z > -22f ? stone : wood;
                    }
                    Panel(res, "Pavimento_" + ix + "_" + iz,
                        new Vector3(x, 0f, z), new Vector3(20f, 0.3f, 16f), 0f, mat);
                }

            // Guscio esterno: due ordini da 6 m = pareti alte 12 m, circa
            // sette volte la statura del giocatore (1,7 m).
            for (int row = 0; row < 2; row++)
            {
                float y = row * 6f + 3f;
                for (int i = -5; i <= 5; i++)
                {
                    string res = (row == 1 || i % 2 == 0)
                        ? "Shell/CI_Shell_Wall_Window_2m"
                        : "Shell/CI_Shell_Wall_Dado_2m";
                    Panel(res, "PareteSud_" + row + "_" + i,
                        new Vector3(i * 16f, y, 56f),
                        new Vector3(16f, 6f, 0.6f), 0f, cream);
                    Panel("Shell/CI_Shell_Wall_Dado_2m", "PareteNord_" + row + "_" + i,
                        new Vector3(i * 16f, y, -56f),
                        new Vector3(16f, 6f, 0.6f), 180f, cream);
                }
                for (int i = -3; i <= 3; i++)
                {
                    Panel("Shell/CI_Shell_Wall_Window_2m", "PareteEst_" + row + "_" + i,
                        new Vector3(81f, y, i * 16f),
                        new Vector3(16f, 6f, 0.6f), -90f, cream);
                    Panel("Shell/CI_Shell_Wall_Window_2m", "PareteOvest_" + row + "_" + i,
                        new Vector3(-81f, y, i * 16f),
                        new Vector3(16f, 6f, 0.6f), 90f, cream);
                }
            }
            for (int i = -5; i <= 5; i++)
            {
                Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceSud_" + i,
                    new Vector3(i * 16f, 12f, 55.4f),
                    new Vector3(16f, 0.5f, 0.5f), 0f, gold);
                Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceNord_" + i,
                    new Vector3(i * 16f, 12f, -55.4f),
                    new Vector3(16f, 0.5f, 0.5f), 180f, gold);
            }
            for (int i = -3; i <= 3; i++)
            {
                Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceEst_" + i,
                    new Vector3(80.4f, 12f, i * 16f),
                    new Vector3(16f, 0.5f, 0.5f), -90f, gold);
                Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceOvest_" + i,
                    new Vector3(-80.4f, 12f, i * 16f),
                    new Vector3(16f, 0.5f, 0.5f), 90f, gold);
            }

            // Setti interni a tutta altezza: la villa è divisa in ali e
            // stanze vere, con archi di passaggio al piano terra.
            for (int row = 0; row < 2; row++)
                for (int i = -5; i <= 5; i++)
                {
                    bool arch = row == 0 && (i == -3 || i == 0 || i == 3);
                    Panel(arch ? "Shell/CI_Shell_Wall_Arch_1p5m"
                               : "Shell/CI_Shell_Wall_Dado_2m",
                        "SettoCentrale_" + row + "_" + i,
                        new Vector3(i * 16f, row * 6f + 3f, 2f),
                        new Vector3(16f, 6f, 0.5f), 180f, cream);
                }
            for (int side = -1; side <= 1; side += 2)
            {
                // Ali nord (salone/atrio ↔ camera e cucina).
                for (int row = 0; row < 2; row++)
                    for (int i = 0; i < 5; i++)
                    {
                        bool arch = row == 0 && (i == 2 || i == 4);
                        Panel(arch ? "Shell/CI_Shell_Wall_Arch_1p5m"
                                   : "Shell/CI_Shell_Wall_Dado_2m",
                            "SettoLatoNord_" + side + "_" + row + "_" + i,
                            new Vector3(side * 35f, row * 6f + 3f, 7f + i * 10f),
                            new Vector3(10f, 6f, 0.5f), side > 0 ? -90f : 90f, cream);
                    }
                // Ali sud (città ↔ biblioteca/pranzo ↔ bagno, bambini, studio).
                for (int row = 0; row < 2; row++)
                    for (int i = 0; i < 6; i++)
                    {
                        bool arch = row == 0 && (i == 1 || i == 4);
                        Panel(arch ? "Shell/CI_Shell_Wall_Arch_1p5m"
                                   : "Shell/CI_Shell_Wall_Dado_2m",
                            "SettoLatoSud_" + side + "_" + row + "_" + i,
                            new Vector3(side * 35f, row * 6f + 3f, -51f + i * 10f),
                            new Vector3(10f, 6f, 0.5f), side > 0 ? -90f : 90f, cream);
                    }
                // Tramezze trasversali che separano le stanze delle ali.
                for (int row = 0; row < 2; row++)
                    for (int wing = 0; wing < 2; wing++)
                        for (int i = 0; i < 4; i++)
                        {
                            bool arch = row == 0 && i == 2;
                            string tag = wing == 0 ? "Nord" : "Sud";
                            Panel(arch ? "Shell/CI_Shell_Wall_Arch_1p5m"
                                       : "Shell/CI_Shell_Wall_Dado_2m",
                                "SettoTrasversale_" + side + "_" + tag + "_" + row + "_" + i,
                                new Vector3(side * (45f + i * 10f), row * 6f + 3f,
                                    wing == 0 ? 22f : -22f),
                                new Vector3(10f, 6f, 0.5f), 180f, cream);
                        }
            }

            // Colonnato monumentale del salone (12 m di altezza).
            for (int side = -1; side <= 1; side += 2)
                for (int i = -3; i <= 3; i++)
                {
                    var col = Primitive(PrimitiveType.Cylinder,
                        "ColonnaVittoriana_" + side + "_" + i,
                        new Vector3(side * 27f, 6f, 12f + i * 12f),
                        new Vector3(1.2f, 6f, 1.2f), stone);
                    col.layer = 8;
                    Panel("Shell/CI_Shell_Trim_Crown_1m",
                        "Capitello_" + side + "_" + i,
                        new Vector3(side * 27f, 11.6f, 12f + i * 12f),
                        new Vector3(2.4f, 0.6f, 2.4f), 0f, gold);
                }

            // Soffitto a cassettoni: copre TUTTA la superficie, non solo il
            // salone. Nove file x otto colonne di pannelli 20 x 16 m.
            for (int ix = -4; ix <= 3; ix++)
                for (int iz = -4; iz <= 3; iz++)
                    Panel("Shell/CI_Shell_Ceiling_Flat_2m",
                        "Soffitto_" + ix + "_" + iz,
                        new Vector3(ix * 20f + 10f, 12.4f, iz * 16f + 8f),
                        new Vector3(20f, 0.4f, 16f), 0f, cream);
            for (int i = -3; i <= 2; i++)
                Panel("Shell/CI_Shell_Ceiling_Beam_2m", "Trave_" + i,
                    new Vector3(0f, 12f, i * 16f + 8f),
                    new Vector3(164f, 0.5f, 0.8f), 0f, wood);
        }

        private static Material redFloor()
        {
            // Tappeto della camera: materiale dedicato, creato una sola volta.
            return Mat("Villa_Carpet", new Color(0.42f, 0.16f, 0.19f));
        }

        private static void BuildSalon(Material wood, Material red,
            Material gold, Material glow)
        {
            Panel("Living/CI_Living_Fireplace_Mantel", "CaminoMonumentale",
                new Vector3(-30f, 0f, 46f), new Vector3(9f, 6f, 2f), 0f, null);
            Primitive(PrimitiveType.Cube, "FuocoCamino",
                new Vector3(-30f, 1.2f, 47.2f), new Vector3(4f, 2f, 0.3f), glow);
            Model("Living/CI_Living_Piano_Upright", "Pianoforte",
                new Vector3(24f, 0f, 42f), new Vector3(3f, 2.6f, 2f), 0f);
            Model("Living/CI_Living_Piano_Stool", "SgabelloPiano",
                new Vector3(24f, 0f, 39f), new Vector3(1f, 0.8f, 1f), 0f);
            for (int i = 0; i < 4; i++)
            {
                float z = 8f + i * 9f;
                Model("Living/CI_Living_Bookcase_Tall", "LibreriaSalone_A" + i,
                    new Vector3(-31f, 0f, z), new Vector3(2f, 7f, 8f), 90f);
                Model("Living/CI_Living_Sofa_ThreeSeat", "Divano_" + i,
                    new Vector3(-11f, 0f, z), new Vector3(5f, 1.6f, 2f), 90f);
                Model("Living/CI_Living_Armchair_Wingback", "Poltrona_" + i,
                    new Vector3(11f, 0f, z), new Vector3(2.4f, 1.8f, 2.2f), -90f);
                Model("Living/CI_Living_CoffeeTable", "Tavolino_" + i,
                    new Vector3(0f, 0f, z), new Vector3(4f, 1f, 3f), 0f);
                Model("Decor/CI_Decor_Rug_LargeRect", "Tappeto_" + i,
                    new Vector3(0f, 0.06f, z), new Vector3(14f, 0.12f, 8f), 0f);
                Model("Living/CI_Living_TeaSet_Tray", "TeaSet_" + i,
                    new Vector3(0f, 1.1f, z), new Vector3(1.4f, 0.4f, 1.2f), 0f);
            }
            Model("Living/CI_Living_Sideboard", "SideboardSalone",
                new Vector3(30f, 0f, 20f), new Vector3(4f, 2f, 1.6f), -90f);
            Model("Decor/CI_Decor_Frame_Cluster", "QuadriSalone",
                new Vector3(30f, 4f, 36f), new Vector3(4f, 4f, 0.3f), -90f);
            Model("Decor/CI_Decor_Curtain_Long", "TendeSalone",
                new Vector3(0f, 3.4f, 55f), new Vector3(20f, 6f, 0.3f), 0f);
            for (int i = 0; i < 3; i++)
            {
                float z = 12f + i * 14f;
                Model("Decor/CI_Decor_CeilingLight_ThreeArm", "LampadarioSalone_" + i,
                    new Vector3(0f, 10.2f, z), new Vector3(7f, 3.2f, 7f), 0f);
                Primitive(PrimitiveType.Sphere, "CuoreLampadario_" + i,
                    new Vector3(0f, 9.4f, z), new Vector3(1.4f, 1.4f, 1.4f), glow);
            }
        }

        private static void BuildLibrary(Material wood, Material green,
            Material gold, Material glow)
        {
            for (int i = 0; i < 7; i++)
            {
                float z = -18f + i * 6f;
                Model("Living/CI_Living_Bookcase_Tall", "Scaffale_" + i,
                    new Vector3(-69f, 0f, z), new Vector3(2f, 8f, 5f), 0f);
                Model("Decor/CI_Decor_Book_Row", "Libri_" + i,
                    new Vector3(-66f, 1.2f, z), new Vector3(1f, 1.6f, 4f), 0f);
            }
            Model("Office/CI_Office_Desk_Writing", "ScrivaniaBiblioteca",
                new Vector3(-50f, 0f, -8f), new Vector3(6f, 2f, 4f), 0f);
            Model("Office/CI_Office_Globe", "Globo",
                new Vector3(-45f, 0f, -8f), new Vector3(2.2f, 3.2f, 2.2f), 0f);
            Model("Office/CI_Office_Chair_Swivel", "SediaBiblioteca",
                new Vector3(-50f, 0f, -12f), new Vector3(2f, 2f, 2f), 180f);
            Model("Office/CI_Office_Bookcase_Glazed", "ArmadioBiblioteca",
                new Vector3(-44f, 0f, 8f), new Vector3(3f, 7f, 8f), 90f);
            Model("Office/CI_Office_LadderShelf", "ScalaBiblioteca",
                new Vector3(-60f, 0f, 12f), new Vector3(2f, 5f, 2f), 0f);
            Model("Decor/CI_Decor_CeilingLight_ThreeArm", "LampadarioBiblioteca",
                new Vector3(-54f, 10f, 0f), new Vector3(5f, 2.6f, 5f), 0f);
            Primitive(PrimitiveType.Sphere, "LuceBiblioteca",
                new Vector3(-54f, 9.3f, 0f), new Vector3(1.1f, 1.1f, 1.1f), glow);
        }

        private static void BuildDining(Material wood, Material red,
            Material gold, Material glow)
        {
            for (int i = 0; i < 4; i++)
            {
                float z = -14f + i * 9f;
                Model("Kitchen/CI_Kitchen_Table", "TavoloPranzo_" + i,
                    new Vector3(54f, 0f, z), new Vector3(4f, 1.4f, 5f), 90f);
                Model("Kitchen/CI_Kitchen_Chair", "SediaPranzo_A" + i,
                    new Vector3(50f, 0f, z), new Vector3(1.6f, 1.6f, 1.6f), -90f);
                Model("Kitchen/CI_Kitchen_Chair", "SediaPranzo_B" + i,
                    new Vector3(58f, 0f, z), new Vector3(1.6f, 1.6f, 1.6f), 90f);
                Model("Decor/CI_Decor_Plant_FernStand", "FelcePranzo_" + i,
                    new Vector3(70f, 0f, z), new Vector3(1.6f, 2.4f, 1.6f), 0f);
            }
            Model("Kitchen/CI_Kitchen_DresserHutch", "CredenzaPranzo",
                new Vector3(74f, 0f, 0f), new Vector3(3f, 5f, 16f), -90f);
            Model("Decor/CI_Decor_CeilingLight_ThreeArm", "LampadarioPranzo",
                new Vector3(54f, 10f, 0f), new Vector3(5f, 2.6f, 5f), 0f);
            Primitive(PrimitiveType.Sphere, "LucePranzo",
                new Vector3(54f, 9.3f, 0f), new Vector3(1.1f, 1.1f, 1.1f), glow);
        }

        private static void BuildCityInside(Material cream, Material wood,
            Material gold, Material glow, Material stone)
        {
            for (int side = -1; side <= 1; side += 2)
            {
                for (int i = 0; i < 6; i++)
                {
                    float z = -12f - i * 7f;
                    Panel("Shell/CI_Shell_Wall_Window_2m", "Facciata_" + side + "_" + i,
                        new Vector3(side * 26f, 4.6f, z),
                        new Vector3(7f, 9.2f, 7f), side > 0 ? 90f : -90f, cream);
                    Panel("Shell/CI_Shell_Window_Casement", "Vetrina_" + side + "_" + i,
                        new Vector3(side * 25f, 2.6f, z),
                        new Vector3(4f, 4f, 0.3f), side > 0 ? 90f : -90f, glow);
                    Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceFacciata_" + side + "_" + i,
                        new Vector3(side * 26f, 9.4f, z),
                        new Vector3(7.6f, 0.5f, 7f), side > 0 ? 90f : -90f, gold);
                }
                for (int i = 0; i < 4; i++)
                {
                    float z = -16f - i * 9f;
                    Model("Decor/CI_Decor_Pendant_Glass", "Lampione_" + side + "_" + i,
                        new Vector3(side * 10f, 4.4f, z), new Vector3(1.4f, 3f, 1.4f), 0f);
                    Primitive(PrimitiveType.Sphere, "LuceLampione_" + side + "_" + i,
                        new Vector3(side * 10f, 3.9f, z),
                        new Vector3(0.9f, 0.9f, 0.9f), glow);
                    Model("Living/CI_Living_Sofa_Loveseat", "Panchina_" + side + "_" + i,
                        new Vector3(side * 7f, 0f, z - 2f), new Vector3(3f, 1.2f, 1.2f), 0f);
                    Model("Decor/CI_Decor_Plant_OliveTree", "Olivo_" + side + "_" + i,
                        new Vector3(side * 18f, 0f, z), new Vector3(2f, 4f, 2f), 0f);
                }
            }
            var vasca = Primitive(PrimitiveType.Cylinder, "VascaFontana",
                new Vector3(0f, 0.6f, -48f), new Vector3(6f, 0.6f, 6f), stone);
            vasca.layer = 8;
            Primitive(PrimitiveType.Cylinder, "ColonnaFontana",
                new Vector3(0f, 2.2f, -48f), new Vector3(1f, 2.2f, 1f), gold);
            Primitive(PrimitiveType.Sphere, "AcquaFontana",
                new Vector3(0f, 3.8f, -48f), new Vector3(2.4f, 2.4f, 2.4f), glow);
            Model("Decor/CI_Decor_Rug_Round", "TappetoPiazzetta",
                new Vector3(0f, 0.06f, -48f), new Vector3(22f, 0.12f, 22f), 0f);
        }

        /// <summary>Atrio d'onore: il primo ambiente che si vede varcando la
        /// porta, collegato al salone e alle due ali della villa.</summary>
        private static void BuildFoyer(Material cream, Material wood,
            Material red, Material gold, Material glow, Material stone)
        {
            Model("Decor/CI_Decor_Rug_Runner", "TappetoAtrio",
                new Vector3(0f, 0.06f, 46f), new Vector3(6f, 0.12f, 16f), 0f);
            Model("Living/CI_Living_Sideboard", "ConsolleAtrio",
                new Vector3(-24f, 0f, 46f), new Vector3(4f, 2f, 1.6f), 90f);
            Model("Bedroom/CI_Bedroom_Mirror_Floor", "SpecchioAtrio",
                new Vector3(-24f, 0f, 50f), new Vector3(2f, 4f, 0.3f), 90f);
            Model("Decor/CI_Decor_Plant_Monstera", "MonsteraAtrio",
                new Vector3(28f, 0f, 50f), new Vector3(2f, 3f, 2f), 0f);
            Model("Bedroom/CI_Bedroom_CoatStand", "AttaccapanniAtrio",
                new Vector3(-28f, 0f, 38f), new Vector3(1.4f, 3f, 1.4f), 0f);
            Model("Cafe/CI_Cafe_UmbrellaStand", "PortaOmbrelliAtrio",
                new Vector3(28f, 0f, 38f), new Vector3(1.2f, 1.6f, 1.2f), 0f);
            Model("Decor/CI_Decor_Clock_Round", "OrologioAtrio",
                new Vector3(0f, 5f, 55f), new Vector3(2f, 2f, 0.3f), 180f);
            Model("Decor/CI_Decor_Doormat", "ZerbinoAtrio",
                new Vector3(0f, 0.05f, 52f), new Vector3(4f, 0.1f, 2f), 0f);
            Model("Decor/CI_Decor_CeilingLight_ThreeArm", "LampadarioAtrio",
                new Vector3(0f, 10.2f, 46f), new Vector3(5f, 2.6f, 5f), 0f);
            Primitive(PrimitiveType.Sphere, "CuoreLampadarioAtrio",
                new Vector3(0f, 9.4f, 46f), new Vector3(1f, 1f, 1f), glow);
            // Scale monumentali: due statue di pietra ai lati dell'atrio.
            Primitive(PrimitiveType.Cylinder, "PiedistalloAtrioSX",
                new Vector3(-18f, 1f, 54f), new Vector3(2f, 2f, 2f), stone);
            Primitive(PrimitiveType.Cylinder, "PiedistalloAtrioDX",
                new Vector3(18f, 1f, 54f), new Vector3(2f, 2f, 2f), stone);
            Primitive(PrimitiveType.Sphere, "VasoAtrioSX",
                new Vector3(-18f, 2.6f, 54f), new Vector3(1.6f, 1.6f, 1.6f), gold);
            Primitive(PrimitiveType.Sphere, "VasoAtrioDX",
                new Vector3(18f, 2.6f, 54f), new Vector3(1.6f, 1.6f, 1.6f), gold);
        }

        private static void BuildMasterBedroom(Material cream, Material wood,
            Material red, Material gold, Material glow)
        {
            Model("Bedroom/CI_Bedroom_Bed_Double", "LettoMatrimoniale",
                new Vector3(-58f, 0f, 42f), new Vector3(5f, 1.8f, 6f), 0f);
            Model("Bedroom/CI_Bedroom_BedsideTable_Drawer", "ComodinoCameraSX",
                new Vector3(-64f, 0f, 42f), new Vector3(1.6f, 1.4f, 1.6f), 0f);
            Model("Bedroom/CI_Bedroom_BedsideTable_Drawer", "ComodinoCameraDX",
                new Vector3(-52f, 0f, 42f), new Vector3(1.6f, 1.4f, 1.6f), 0f);
            Model("Bedroom/CI_Bedroom_Lamp_Bedside", "LampadaComodinoSX",
                new Vector3(-64f, 1.5f, 42f), new Vector3(0.8f, 1.2f, 0.8f), 0f);
            Model("Bedroom/CI_Bedroom_Lamp_Bedside", "LampadaComodinoDX",
                new Vector3(-52f, 1.5f, 42f), new Vector3(0.8f, 1.2f, 0.8f), 0f);
            Model("Bedroom/CI_Bedroom_Wardrobe_Double", "ArmadioCamera",
                new Vector3(-76f, 0f, 34f), new Vector3(3f, 5f, 5f), 90f);
            Model("Bedroom/CI_Bedroom_Dresser_Mirror", "SpecchieraCamera",
                new Vector3(-76f, 0f, 48f), new Vector3(3f, 3f, 4f), 90f);
            Model("Bedroom/CI_Bedroom_Armchair_Reading", "PoltronaCamera",
                new Vector3(-48f, 0f, 50f), new Vector3(2.4f, 2f, 2.4f), -90f);
            Model("Living/CI_Living_Footstool", "PoggiapiediCamera",
                new Vector3(-48f, 0f, 46f), new Vector3(1.4f, 0.8f, 1.4f), 0f);
            Model("Bedroom/CI_Bedroom_BlanketChest", "CassaCoperte",
                new Vector3(-70f, 0f, 53f), new Vector3(3f, 1.4f, 2f), 0f);
            Model("Decor/CI_Decor_Rug_LargeRect", "TappetoCamera",
                new Vector3(-58f, 0.06f, 42f), new Vector3(16f, 0.12f, 12f), 0f);
            Model("Bedroom/CI_Bedroom_Pendant_Fabric", "LampadaCamera",
                new Vector3(-58f, 9.6f, 42f), new Vector3(4f, 2.4f, 4f), 0f);
            Primitive(PrimitiveType.Sphere, "LuceCamera",
                new Vector3(-58f, 9f, 42f), new Vector3(0.9f, 0.9f, 0.9f), glow);
            Model("Bedroom/CI_Bedroom_Mirror_Floor", "SpecchioCamera",
                new Vector3(-72f, 0f, 52f), new Vector3(1.8f, 3.6f, 0.3f), 90f);
            Model("Bedroom/CI_Bedroom_PhotoFrame_Pair", "FotoCamera",
                new Vector3(-64f, 2.6f, 36f), new Vector3(1.4f, 1.2f, 0.2f), 0f);
            Model("Bedroom/CI_Bedroom_JewelleryBox", "Gioielliere",
                new Vector3(-76f, 3.1f, 48f), new Vector3(0.8f, 0.4f, 0.6f), 90f);
            Model("Bedroom/CI_Bedroom_Slippers", "PantofoleCamera",
                new Vector3(-58f, 0.1f, 35f), new Vector3(1f, 0.2f, 0.8f), 0f);
            Model("Bedroom/CI_Bedroom_LaundryBasket", "CestoBucatoCamera",
                new Vector3(-70f, 0f, 28f), new Vector3(1.2f, 1.2f, 1.2f), 0f);
        }

        private static void BuildBathroom(Material cream, Material wood,
            Material gold, Material glow, Material stone)
        {
            Model("Bathroom/CI_Bathroom_Bathtub_Clawfoot", "VascaBagno",
                new Vector3(-70f, 0f, -30f), new Vector3(5f, 2f, 3f), 90f);
            Model("Bathroom/CI_Bathroom_RubberDuck", "PaperellaBagno",
                new Vector3(-70f, 1.9f, -30f), new Vector3(0.6f, 0.6f, 0.6f), 0f);
            Model("Bathroom/CI_Bathroom_BathCandles", "CandeleBagno",
                new Vector3(-74f, 2.1f, -27f), new Vector3(0.8f, 0.5f, 0.8f), 0f);
            Model("Bathroom/CI_Bathroom_BathMat", "TappetinoBagno",
                new Vector3(-60f, 0.06f, -30f), new Vector3(2f, 0.12f, 3f), 0f);
            Model("Bathroom/CI_Bathroom_Basin_Pedestal", "LavandinoBagno",
                new Vector3(-60f, 0f, -28f), new Vector3(1.6f, 2f, 1.2f), 0f);
            Model("Bathroom/CI_Bathroom_Vanity_Basin", "MobileLavandino",
                new Vector3(-60f, 0f, -40f), new Vector3(3f, 2f, 1.4f), 0f);
            Model("Bathroom/CI_Bathroom_MirrorCabinet", "ArmadioSpecchioBagno",
                new Vector3(-60f, 2.6f, -40f), new Vector3(2.4f, 2f, 0.3f), 0f);
            Model("Bathroom/CI_Bathroom_Toilet", "WC",
                new Vector3(-52f, 0f, -30f), new Vector3(1.4f, 1.8f, 1.6f), 0f);
            Model("Bathroom/CI_Bathroom_TowelRail_Towels", "AsciugamaniBagno",
                new Vector3(-76f, 1.6f, -36f), new Vector3(1.6f, 1.4f, 0.3f), 90f);
            Model("Bathroom/CI_Bathroom_LinenCabinet_Tall", "ArmadioBiancheria",
                new Vector3(-76f, 0f, -48f), new Vector3(2f, 4f, 1.6f), 0f);
            Model("Bathroom/CI_Bathroom_LaundryHamper", "CestoBucatoBagno",
                new Vector3(-70f, 0f, -48f), new Vector3(1.2f, 1.4f, 1.2f), 0f);
            Model("Bathroom/CI_Bathroom_Sconce_Wall", "AppliqueBagno",
                new Vector3(-58f, 4f, -50f), new Vector3(1f, 1.4f, 0.4f), 180f);
            Model("Bathroom/CI_Bathroom_ShowerRail", "DocciaBagno",
                new Vector3(-50f, 1.8f, -50f), new Vector3(2f, 3f, 2f), 0f);
            Model("Decor/CI_Decor_Basket_Lidded", "CestoBagno",
                new Vector3(-52f, 0f, -44f), new Vector3(1.2f, 1.4f, 1.2f), 0f);
            Primitive(PrimitiveType.Sphere, "LuceBagno",
                new Vector3(-62f, 7f, -38f), new Vector3(0.6f, 0.6f, 0.6f), glow);
        }

        private static void BuildKitchen(Material cream, Material wood,
            Material red, Material gold, Material glow, Material stone)
        {
            Model("Kitchen/CI_Kitchen_Base_Sink_1m", "LavelloCucina",
                new Vector3(76f, 0f, 34f), new Vector3(2f, 1.8f, 1.6f), -90f);
            Model("Kitchen/CI_Kitchen_RangeCooker", "FornoCucina",
                new Vector3(76f, 0f, 44f), new Vector3(2f, 2f, 2f), -90f);
            Model("Kitchen/CI_Kitchen_RangeHood", "CappaCucina",
                new Vector3(76f, 3.6f, 44f), new Vector3(2f, 1.4f, 2f), -90f);
            Model("Kitchen/CI_Kitchen_Fridge_Retro", "FrigoCucina",
                new Vector3(76f, 0f, 52f), new Vector3(2f, 3.4f, 2f), -90f);
            Model("Kitchen/CI_Kitchen_Pantry_Tall", "DispensaCucina",
                new Vector3(66f, 0f, 52f), new Vector3(3f, 5f, 1.6f), 0f);
            Model("Kitchen/CI_Kitchen_WallCabinet_0p6m", "PensileCucina",
                new Vector3(77f, 3f, 34f), new Vector3(1.4f, 1.6f, 1.6f), -90f);
            Model("Kitchen/CI_Kitchen_Table", "TavoloCucina",
                new Vector3(54f, 0f, 40f), new Vector3(4f, 1.4f, 4f), 0f);
            Model("Kitchen/CI_Kitchen_Chair", "SediaCucinaA",
                new Vector3(50f, 0f, 40f), new Vector3(1.6f, 1.6f, 1.6f), -90f);
            Model("Kitchen/CI_Kitchen_Chair", "SediaCucinaB",
                new Vector3(58f, 0f, 40f), new Vector3(1.6f, 1.6f, 1.6f), 90f);
            Model("Kitchen/CI_Kitchen_Kettle", "BollitoreCucina",
                new Vector3(75f, 2.1f, 44f), new Vector3(0.8f, 0.8f, 0.8f), 0f);
            Model("Kitchen/CI_Kitchen_Toaster", "ToasterCucina",
                new Vector3(75f, 2.1f, 48f), new Vector3(0.8f, 0.7f, 0.9f), 0f);
            Model("Kitchen/CI_Kitchen_FruitBowl", "FruttaCucina",
                new Vector3(54f, 1.5f, 40f), new Vector3(1f, 0.4f, 1f), 0f);
            Model("Kitchen/CI_Kitchen_StorageJars", "BarattoliCucina",
                new Vector3(66f, 3.2f, 52f), new Vector3(1f, 0.9f, 0.8f), 0f);
            Model("Kitchen/CI_Kitchen_HerbPots", "ErbeCucina",
                new Vector3(77f, 1.2f, 30f), new Vector3(1.2f, 0.8f, 0.8f), 0f);
            Model("Kitchen/CI_Kitchen_WallClock", "OrologioCucina",
                new Vector3(40f, 4f, 52f), new Vector3(1.4f, 1.4f, 0.3f), -90f);
            Model("Kitchen/CI_Kitchen_PedalBin", "PattumieraCucina",
                new Vector3(70f, 0f, 30f), new Vector3(1f, 1.4f, 1f), 0f);
            Model("Decor/CI_Decor_Rug_BraidedOval", "TappetoCucina",
                new Vector3(54f, 0.06f, 40f), new Vector3(10f, 0.12f, 8f), 0f);
            Model("Decor/CI_Decor_Pendant_Glass", "LampadaCucina",
                new Vector3(54f, 9.6f, 40f), new Vector3(2f, 3f, 2f), 0f);
            Primitive(PrimitiveType.Sphere, "LuceCucina",
                new Vector3(54f, 9f, 40f), new Vector3(0.7f, 0.7f, 0.7f), glow);
        }

        private static void BuildKidsAndStudy(Material cream, Material wood,
            Material green, Material gold, Material glow)
        {
            // Ala sud-est: stanza dei bambini e studio privato, divise da un
            // setto con arco in corrispondenza di x = 58.
            Model("Kids/CI_Kids_BunkBed", "LettiBambini",
                new Vector3(44f, 0f, -30f), new Vector3(4f, 4f, 6f), 0f);
            Model("Kids/CI_Kids_ToyChest", "CassaGiochi",
                new Vector3(44f, 0f, -42f), new Vector3(2.4f, 1.6f, 1.8f), 0f);
            Model("Kids/CI_Kids_TeddyBear", "Orsetto",
                new Vector3(44f, 3.2f, -30f), new Vector3(0.9f, 0.9f, 0.9f), 0f);
            Model("Kids/CI_Kids_ToyCar", "Macchinina",
                new Vector3(48f, 0.1f, -36f), new Vector3(1.2f, 0.6f, 0.9f), 0f);
            Model("Kids/CI_Kids_TrainSet", "TrenoGiocattolo",
                new Vector3(48f, 0.1f, -48f), new Vector3(5f, 0.5f, 5f), 0f);
            Model("Kids/CI_Kids_CubbyShelf", "ScaffaleGiochi",
                new Vector3(38f, 0f, -48f), new Vector3(4f, 3f, 1.2f), 90f);
            Model("Kids/CI_Kids_Desk", "ScrivaniaBambini",
                new Vector3(52f, 0f, -48f), new Vector3(3f, 1.6f, 1.6f), 180f);
            Model("Kids/CI_Kids_Chair", "SediaBambini",
                new Vector3(52f, 0f, -51f), new Vector3(1.2f, 1.6f, 1.2f), 0f);
            Model("Kids/CI_Kids_BeanBag", "SaccoBambini",
                new Vector3(48f, 0f, -52f), new Vector3(1.6f, 1f, 1.6f), 0f);
            Model("Kids/CI_Kids_NightLamp_Mushroom", "LucinaBambini",
                new Vector3(46f, 0f, -26f), new Vector3(0.8f, 1f, 0.8f), 0f);
            Model("Kids/CI_Kids_PictureBooks", "LibriBambini",
                new Vector3(38f, 1.6f, -48f), new Vector3(1f, 0.6f, 1f), 90f);
            Model("Decor/CI_Decor_Rug_SmallRect", "TappetoBambini",
                new Vector3(44f, 0.06f, -38f), new Vector3(10f, 0.12f, 8f), 0f);

            Model("Office/CI_Office_Desk_Writing", "ScrivaniaStudio",
                new Vector3(70f, 0f, -30f), new Vector3(6f, 2f, 4f), 0f);
            Model("Office/CI_Office_Chair_Swivel", "SediaStudio",
                new Vector3(70f, 0f, -35f), new Vector3(2f, 2f, 2f), 180f);
            Model("Office/CI_Office_Bookcase_Glazed", "LibreriaStudio",
                new Vector3(76f, 0f, -42f), new Vector3(3f, 6f, 8f), 90f);
            Model("Office/CI_Office_Typewriter", "MacchinaDaScrivere",
                new Vector3(70f, 2.1f, -30f), new Vector3(1.4f, 0.8f, 1.4f), 0f);
            Model("Office/CI_Office_Lamp_Banker", "LampadaStudio",
                new Vector3(67f, 2.1f, -30f), new Vector3(0.8f, 0.8f, 0.8f), 0f);
            Model("Office/CI_Office_Map_Framed", "MappaStudio",
                new Vector3(64f, 5f, -54f), new Vector3(4f, 3f, 0.3f), 180f);
            Model("Office/CI_Office_FilingCabinet", "ArchivioStudio",
                new Vector3(70f, 0f, -48f), new Vector3(2f, 2.4f, 1.6f), 0f);
            Model("Decor/CI_Decor_Rug_SmallRect", "TappetoStudio",
                new Vector3(68f, 0.06f, -38f), new Vector3(8f, 0.12f, 6f), 0f);
            Model("Decor/CI_Decor_Plant_SnakePlant", "PiantaStudio",
                new Vector3(76f, 0f, -26f), new Vector3(1.4f, 2.4f, 1.4f), 0f);
            Primitive(PrimitiveType.Sphere, "LuceStudioBambini",
                new Vector3(60f, 8f, -38f), new Vector3(0.7f, 0.7f, 0.7f), glow);
        }

        /// <summary>Comodità diffuse: salone, biblioteca, pranzo e città
        /// interna ricevono gli oggetti che mancavano al primo passo.</summary>
        private static void BuildExtraComforts(Material wood, Material red,
            Material gold, Material glow, Material stone)
        {
            Model("Living/CI_Living_FireTools", "AttrezziCamino",
                new Vector3(-26f, 0f, 46f), new Vector3(1f, 2f, 1f), 0f);
            Model("Living/CI_Living_LogBasket", "CestoLegna",
                new Vector3(-24f, 0f, 44f), new Vector3(1.4f, 1f, 1.4f), 0f);
            Model("Living/CI_Living_RockingChair", "SediaADondolareSalone",
                new Vector3(28f, 0f, 54f), new Vector3(2f, 2f, 2f), -90f);
            Model("Living/CI_Living_Lamp_Floor", "LampadaSalone",
                new Vector3(-28f, 0f, 12f), new Vector3(1.2f, 3f, 1.2f), 0f);
            Model("Living/CI_Living_MantelClock", "OrologioCamino",
                new Vector3(-30f, 5f, 46f), new Vector3(1.2f, 0.8f, 0.6f), 0f);
            Model("Living/CI_Living_RecordCabinet", "DiscotecaSalone",
                new Vector3(30f, 0f, 8f), new Vector3(2f, 2f, 3f), -90f);
            Model("Living/CI_Living_ValveRadio", "RadioSalone",
                new Vector3(30f, 2.1f, 8f), new Vector3(1.4f, 0.9f, 1f), -90f);
            Model("Living/CI_Living_Cushion_Pair", "CusciniSalone",
                new Vector3(-11f, 1.5f, 26f), new Vector3(1f, 0.5f, 1f), 0f);

            Model("Office/CI_Office_Book_Stack_Desk", "LibriScrivaniaBiblioteca",
                new Vector3(-48f, 2.1f, -8f), new Vector3(1f, 0.5f, 1f), 0f);
            Model("Decor/CI_Decor_Clock_Round", "OrologioBiblioteca",
                new Vector3(-44f, 5f, 20f), new Vector3(1.6f, 1.6f, 0.3f), 180f);
            Model("Decor/CI_Decor_Plant_FernStand", "FelceBiblioteca",
                new Vector3(-38f, 0f, -18f), new Vector3(1.6f, 2.4f, 1.6f), 0f);
            Model("Living/CI_Living_Bookcase_Low", "LibreriaBassaBiblioteca",
                new Vector3(-58f, 0f, 18f), new Vector3(4f, 2.4f, 1.4f), 0f);

            Model("Bakery/CI_Bakery_Cake_Stand", "TortaPranzo",
                new Vector3(54f, 1.5f, 0f), new Vector3(1.2f, 0.9f, 1.2f), 0f);
            Model("Cafe/CI_Cafe_Cup_Stack", "TazzePranzo",
                new Vector3(74f, 5.1f, 0f), new Vector3(0.8f, 0.5f, 0.8f), -90f);
            Model("Living/CI_Living_Candle_Cluster", "CandelePranzo",
                new Vector3(54f, 1.5f, 6f), new Vector3(0.8f, 0.6f, 0.8f), 0f);
            Model("Decor/CI_Decor_Frame_Medium", "QuadroPranzo",
                new Vector3(78f, 5f, 8f), new Vector3(2f, 2f, 0.3f), -90f);

            for (int i = 0; i < 4; i++)
            {
                float z = -14f - i * 9f;
                Model("Cafe/CI_Cafe_Table_Bistro", "TavolinoCitta_" + i,
                    new Vector3(-4f, 0f, z), new Vector3(2f, 1.6f, 2f), 0f);
                Model("Cafe/CI_Cafe_Chair_Bistro", "SediaCitta_" + i,
                    new Vector3(-7f, 0f, z), new Vector3(1.2f, 1.6f, 1.2f), 90f);
                Model("Bakery/CI_Bakery_DisplayCounter_Glass", "VetrinaCitta_" + i,
                    new Vector3(20f, 0f, z), new Vector3(3f, 2.4f, 2f), -90f);
                Model("Cafe/CI_Cafe_MenuBoard_Wall", "MenuCitta_" + i,
                    new Vector3(23f, 3f, z), new Vector3(1.2f, 1.6f, 0.3f), -90f);
            }
        }

        private static void BuildExitDoor(Material wood, Material gold, Material glow)
        {
            Panel("Shell/CI_Shell_Wall_DoorDouble_2m", "PortaUscitaVittoriana",
                new Vector3(0f, 3f, 55.4f), new Vector3(6f, 6f, 0.6f), 180f, wood);
            Panel("Shell/CI_Shell_Door_Panel", "BattentiUscita",
                new Vector3(0f, 2.6f, 54.9f), new Vector3(5.4f, 5.2f, 0.4f), 180f, wood);
            Primitive(PrimitiveType.Sphere, "ManigliaUscita",
                new Vector3(2f, 2.8f, 54.4f), new Vector3(0.45f, 0.45f, 0.45f), gold);
            Collider("Collider_PortaUscita", new Vector3(0f, 3f, 55f),
                new Vector3(6f, 6f, 0.8f), 8);
            Primitive(PrimitiveType.Sphere, "LuceUscita",
                new Vector3(0f, 5.6f, 54f), new Vector3(1.1f, 1.1f, 1.1f), glow);
        }

        private static void BuildLighting(Material gold, Material glow)
        {
            LightAt("LuceSaloneCentrale", new Vector3(0f, 9.4f, 26f), 1.35f, 36f);
            LightAt("LuceSaloneFondo", new Vector3(0f, 9.4f, 4f), 1.1f, 32f);
            LightAt("LuceBibliotecaVera", new Vector3(-54f, 9.2f, 0f), 1f, 30f);
            LightAt("LucePranzoVera", new Vector3(54f, 9.2f, 0f), 1f, 30f);
            LightAt("LuceCittaVera", new Vector3(0f, 8f, -36f), 0.9f, 34f);
            LightAt("LuceAtrioVero", new Vector3(0f, 9f, 46f), 1f, 30f);
            LightAt("LuceCameraVera", new Vector3(-58f, 8.6f, 42f), 1f, 32f);
            LightAt("LuceBagnoVero", new Vector3(-62f, 6f, -38f), 0.8f, 26f);
            LightAt("LuceCucinaVera", new Vector3(54f, 8.6f, 40f), 1f, 32f);
            LightAt("LuceStudioVero", new Vector3(60f, 8f, -38f), 0.8f, 30f);
        }

        private static void LightAt(string name, Vector3 pos, float intensity, float range)
        {
            var go = new GameObject(name);
            go.transform.SetParent(_root, false);
            go.transform.localPosition = pos;
            var light = go.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.9f, 0.72f);
            light.intensity = intensity;
            light.range = range;
        }

        private static void BuildEggSpots(Material wood, Material gold)
        {
            Egg("cassetto-lampadario", "salone", "cassetto sotto il lampadario",
                "Decor/CI_Decor_StorageBox_Pair",
                new Vector3(4f, 0f, 30f), new Vector3(2.2f, 1.2f, 1.6f), wood);
            Egg("cassetto-biblioteca", "biblioteca", "cassetto della scrivania",
                "Office/CI_Office_FilingCabinet",
                new Vector3(-50f, 0f, -8f), new Vector3(2.4f, 1.4f, 1.8f), wood);
            Egg("credenza-pranzo", "sala da pranzo", "credenza della sala da pranzo",
                "Decor/CI_Decor_Basket_Lidded",
                new Vector3(70f, 0f, 0f), new Vector3(2.4f, 1.4f, 1.8f), gold);
            Egg("vetrina-citta", "città dentro casa", "vetrina del negozio",
                "Decor/CI_Decor_Basket_Open",
                new Vector3(-16f, 0f, -35f), new Vector3(2.4f, 1.4f, 1.8f), gold);
            Egg("fontana-piazzetta", "piazzetta della fontana", "vasca della fontana",
                "Decor/CI_Decor_Bowl_Ceramic",
                new Vector3(6f, 0f, -48f), new Vector3(2.4f, 1.4f, 2.4f), gold);
            // Le nuove comodità della villa sono anche nuovi nascondigli del
            // Riflesso uova: ogni stanza ha il suo oggetto e il suo luogo.
            Egg("armadio-camera", "camera da letto", "armadio della camera da letto",
                "Bedroom/CI_Bedroom_Wardrobe_Double",
                new Vector3(-76f, 0f, 34f), new Vector3(2.4f, 1.4f, 1.8f), wood);
            Egg("lavandino-bagno", "bagno", "lavandino del bagno",
                "Bathroom/CI_Bathroom_Vanity_Basin",
                new Vector3(-60f, 0f, -40f), new Vector3(2.4f, 1.4f, 1.8f), gold);
            Egg("forno-cucina", "cucina", "forno della cucina",
                "Kitchen/CI_Kitchen_RangeCooker",
                new Vector3(76f, 0f, 44f), new Vector3(2.4f, 1.4f, 1.8f), gold);
            Egg("cassetto-bambini", "stanza dei bambini", "cassetto dei giocattoli",
                "Kids/CI_Kids_ToyChest",
                new Vector3(44f, 0f, -42f), new Vector3(2.4f, 1.4f, 1.8f), wood);
            Egg("scrivania-studio", "studio privato", "scrivania dello studio",
                "Office/CI_Office_Desk_Writing",
                new Vector3(70f, 0f, -30f), new Vector3(2.4f, 1.4f, 1.8f), wood);
        }

        private static void Egg(string id, string room, string thing,
            string model, Vector3 center, Vector3 size, Material mat)
        {
            var holder = new GameObject("Contenitore_" + id);
            holder.transform.SetParent(_root, false);
            holder.transform.localPosition = center;
            GameObject visual = Model(model, "Mobile_" + id,
                Vector3.zero, size, 0f);
            if (visual != null)
            {
                visual.transform.SetParent(holder.transform, false);
                visual.transform.localPosition = Vector3.zero;
                visual.transform.localRotation = Quaternion.identity;
            }
            var marker = holder.AddComponent<UniversalContainerMarker>();
            marker.room = room;
            marker.interiorKey = "vittoriano";
            var spot = holder.AddComponent<HouseEggSpot>();
            spot.featureId = "uovo-" + id;
            spot.label = thing;
            spot.room = room;
            spot.place = "città→casa→Casa Vittoriana→" + room + "→" + thing;
        }

        /// <summary>Modulo architettonico: scala indipendente sugli assi, così
        /// un pannello 2 m diventa una parete 16 m senza deformare i mobili.</summary>
        private static GameObject Panel(string res, string name, Vector3 center,
            Vector3 size, float yaw, Material mat)
        {
            return Spawn(res, name, center, size, yaw, mat, false);
        }

        /// <summary>Arredo SENDU: scala uniforme e materiali originali.</summary>
        private static GameObject Model(string res, string name, Vector3 center,
            Vector3 size, float yaw, Material mat = null)
        {
            return Spawn(res, name, center, size, yaw, mat, true);
        }

        private static GameObject Spawn(string res, string name, Vector3 center,
            Vector3 size, float yaw, Material mat, bool uniform)
        {
            GameObject src = Resources.Load<GameObject>(Sendu + res);
            if (src == null)
            {
                Debug.LogWarning("[VillaPrefabBaker] modello mancante: " + res);
                return Primitive(PrimitiveType.Cube, name, center, size,
                    mat != null ? mat : Mat("Villa_Cream", new Color(0.86f, 0.79f, 0.67f)));
            }
            var go = PrefabUtility.InstantiatePrefab(src) as GameObject;
            if (go == null) return null;
            go.name = name;
            go.transform.SetParent(_root, false);
            go.transform.localPosition = Vector3.zero;
            go.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            go.transform.localScale = Vector3.one;
            DisableLods(go);

            Bounds b = BoundsOf(go);
            Vector3 ext = new Vector3(
                Mathf.Max(b.size.x, 0.01f),
                Mathf.Max(b.size.y, 0.01f),
                Mathf.Max(b.size.z, 0.01f));
            if (uniform)
            {
                float k = Mathf.Min(size.x / ext.x, size.y / ext.y, size.z / ext.z);
                go.transform.localScale *= k;
            }
            else
            {
                go.transform.localScale = new Vector3(
                    go.transform.localScale.x * size.x / ext.x,
                    go.transform.localScale.y * size.y / ext.y,
                    go.transform.localScale.z * size.z / ext.z);
            }
            b = BoundsOf(go);
            go.transform.localPosition += center - b.center;
            if (mat != null)
            {
                var renderers = go.GetComponentsInChildren<Renderer>(true);
                for (int i = 0; i < renderers.Length; i++) renderers[i].sharedMaterial = mat;
            }
            return go;
        }

        private static GameObject Primitive(PrimitiveType type, string name,
            Vector3 center, Vector3 size, Material mat)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            go.transform.SetParent(_root, false);
            go.transform.localPosition = center;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            Object.DestroyImmediate(go.GetComponent<Collider>());
            return go;
        }

        private static Bounds BoundsOf(GameObject go)
        {
            var renderers = go.GetComponentsInChildren<Renderer>(true);
            Bounds b = new Bounds(go.transform.position, Vector3.zero);
            bool first = true;
            for (int i = 0; i < renderers.Length; i++)
            {
                if (!renderers[i].enabled) continue;
                if (first) { b = renderers[i].bounds; first = false; }
                else b.Encapsulate(renderers[i].bounds);
            }
            return b;
        }

        private static void DisableLods(GameObject go)
        {
            var all = go.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
            {
                string n = all[i].name;
                if (n.IndexOf("_LOD1", System.StringComparison.Ordinal) >= 0 ||
                    n.IndexOf("_LOD2", System.StringComparison.Ordinal) >= 0)
                    all[i].gameObject.SetActive(false);
            }
        }
    }
}
#endif

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
            BuildSalon(wood, red, gold, glow);
            BuildLibrary(wood, green, gold, glow);
            BuildDining(wood, red, gold, glow);
            BuildCityInside(cream, wood, gold, glow, stone);
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
            // Quattro ambienti reali, tutti calpestabili: salone, biblioteca,
            // sala da pranzo e la "città" con piazzetta.
            for (int ix = -2; ix <= 1; ix++)
                for (int iz = 0; iz < 3; iz++)
                    Panel("Shell/CI_Shell_Floor_Herringbone_2m",
                        "ParquetSalone_" + ix + "_" + iz,
                        new Vector3(ix * 16f + 8f, 0f, 6f + iz * 14f),
                        new Vector3(16f, 0.3f, 14f), 0f, wood);
            for (int ix = -3; ix <= -2; ix++)
                for (int iz = -2; iz <= 1; iz++)
                    Panel("Shell/CI_Shell_Floor_Planks_2m",
                        "ParquetBiblioteca_" + ix + "_" + iz,
                        new Vector3(ix * 18f + 9f, 0f, iz * 11f + 5.5f),
                        new Vector3(18f, 0.3f, 11f), 0f, wood);
            for (int ix = 2; ix <= 3; ix++)
                for (int iz = -2; iz <= 1; iz++)
                    Panel("Shell/CI_Shell_Floor_HexTile_2m",
                        "PavimentoPranzo_" + ix + "_" + iz,
                        new Vector3(ix * 18f - 9f, 0f, iz * 11f + 5.5f),
                        new Vector3(18f, 0.3f, 11f), 0f, stone);
            for (int ix = -2; ix <= 1; ix++)
                for (int iz = -3; iz <= 0; iz++)
                    Panel("Shell/CI_Shell_Floor_SquareTile_2m",
                        "PavimentoCitta_" + ix + "_" + iz,
                        new Vector3(ix * 16f + 8f, 0f, iz * 14f - 7f),
                        new Vector3(16f, 0.3f, 14f), 0f, stone);

            // Guscio esterno a moduli: finestre sul lato lungo, cornicioni,
            // due ordini di pannelli per i soffitti alti 12 m.
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
                Panel("Shell/CI_Shell_Trim_Crown_2m", "CorniceSud_" + i,
                    new Vector3(i * 16f, 12f, 55.4f),
                    new Vector3(16f, 0.5f, 0.5f), 0f, gold);

            // Setto interno: archi verso biblioteca, pranzo e città.
            for (int i = -5; i <= 5; i++)
            {
                bool arch = (i == -3 || i == 0 || i == 3);
                Panel(arch ? "Shell/CI_Shell_Wall_Arch_1p5m" : "Shell/CI_Shell_Wall_Dado_2m",
                    "SettoSalone_" + i, new Vector3(i * 16f, 3f, 2f),
                    new Vector3(16f, 6f, 0.5f), 180f, cream);
            }
            for (int side = -1; side <= 1; side += 2)
                for (int i = 0; i < 2; i++)
                    Panel("Shell/CI_Shell_HalfWall_2m", "SettoLato_" + side + "_" + i,
                        new Vector3(side * 34f, 3f, -6f - i * 14f),
                        new Vector3(16f, 6f, 0.5f), side > 0 ? 90f : -90f, cream);

            // Colonnato e soffitto a cassettoni: la scala della villa.
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
            for (int ix = -2; ix <= 1; ix++)
                for (int iz = -3; iz <= 2; iz++)
                    Panel("Shell/CI_Shell_Ceiling_Flat_2m",
                        "Soffitto_" + ix + "_" + iz,
                        new Vector3(ix * 16f + 8f, 12.2f, iz * 16f),
                        new Vector3(16f, 0.4f, 16f), 0f, cream);
            for (int i = -3; i <= 3; i++)
                Panel("Shell/CI_Shell_Ceiling_Beam_2m", "Trave_" + i,
                    new Vector3(0f, 11.9f, i * 16f),
                    new Vector3(160f, 0.5f, 0.7f), 0f, wood);
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

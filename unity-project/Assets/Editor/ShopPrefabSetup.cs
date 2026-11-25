using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using City.Interior;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Crea UNA VOLTA il prefabbricato CC0 usato dallo switch A/B dei negozi.
    /// È un vero prefab Unity salvato in Resources: i modelli Kenney sono
    /// innestati come nested prefab, quindi restano modificabili dall'editor.
    /// Nessun download, nessun Asset Store, nessun collider sui mobili.
    /// Richiamato da HuntixBuild.ConfigureAndExport() prima dell'export AAR.
    /// </summary>
    public static class ShopPrefabSetup
    {
        public const string Folder = "Assets/Resources/ShopPrefabs";
        public const string Path = Folder + "/NegozioProntoCC0.prefab";

        public static void EnsurePrefab()
        {
            if (AssetDatabase.LoadAssetAtPath<GameObject>(Path) != null) return;
            if (!AssetDatabase.IsValidFolder(Folder))
                AssetDatabase.CreateFolder("Assets/Resources", "ShopPrefabs");

            // In batchmode non c'e' sempre una scena attiva: InstantiatePrefab
            // la richiede. Ne creo una vuota, temporanea, senza toccare le
            // scene di gioco ne' EditorBuildSettings.
            if (!SceneManager.GetActiveScene().isLoaded)
                EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var root = new GameObject("NegozioProntoCC0");
            Material wood = Resources.Load<Material>("Furniture/Materials/Material::wood");
            Material metal = Resources.Load<Material>("Furniture/Materials/Material::metalLight");

            // Banco e pedana: volumi semplici, senza collider.
            Box(root.transform, "BancoPronto", new Vector3(0f, 0.55f, -1.75f),
                new Vector3(3.8f, 1.1f, 0.75f), wood);
            Box(root.transform, "PedanaPronto", new Vector3(0f, 0.05f, 0f),
                new Vector3(5.4f, 0.1f, 5.4f), metal);

            // Scaffali e cassa: modelli CC0 già importati (Mini Market/Furniture).
            Model(root.transform, "Furniture/kitchenFridge", "FrigoPronto",
                new Vector3(2.35f, 1f, -1.7f), new Vector3(0.85f, 2f, 0.85f), 90f);
            Model(root.transform, "KenneyMiniMarket/cash-register", "CassaPronto",
                new Vector3(-1.35f, 1.22f, -1.72f), new Vector3(0.7f, 0.45f, 0.6f), 0f);
            Model(root.transform, "KenneyMiniMarket/shelf-bags", "ScaffaleProntoA",
                new Vector3(-2.05f, 0.9f, -0.55f), new Vector3(0.65f, 1.8f, 2.5f), 0f);
            Model(root.transform, "KenneyMiniMarket/shelf-boxes", "ScaffaleProntoB",
                new Vector3(2.05f, 0.9f, -0.55f), new Vector3(0.65f, 1.8f, 2.5f), 0f);
            Model(root.transform, "KenneyMiniMarket/display-bread", "DisplayPronto",
                new Vector3(0f, 0.55f, -0.6f), new Vector3(1.3f, 1.1f, 0.9f), 0f);
            Model(root.transform, "KenneyMiniMarket/shopping-cart", "CarrelloPronto",
                new Vector3(1.5f, 0.5f, 1.6f), new Vector3(0.7f, 1f, 1.1f), 25f);

            // Prodotti CuteStore: gli stessi già usati da ShopInteriorBuilder.
            string[] products = { "apple_green", "bottle1_water", "can_food_blue", "donut_red" };
            for (int i = 0; i < products.Length; i++)
                Model(root.transform, "CuteStore/Prefabs/" + products[i], "ProdottoPronto" + i,
                    new Vector3(-1.5f + i * 0.5f, 1.28f, -1.72f),
                    new Vector3(0.16f, 0.26f, 0.16f), 0f);

            // Contenitori uova: il marker viaggia DENTRO il prefab e viene
            // letto da ContainerEgg (riflesso del Mandato).
            Model(root.transform, "Furniture/cabinetBedDrawer", "CassettoPronto",
                new Vector3(-2.2f, 0.42f, 1.6f), new Vector3(1f, 0.84f, 0.6f), 0f,
                "banco negozio prefabbricato");
            Model(root.transform, "Furniture/cardboardBoxClosed", "DispensaPronto",
                new Vector3(2.2f, 0.32f, 1.6f), new Vector3(0.8f, 0.64f, 0.6f), 0f,
                "magazzino prefabbricato");

            var marker = root.AddComponent<UniversalInteriorMarker>();
            marker.key = "prefab#negozio";
            marker.kind = "negozio";
            marker.label = "Negozio prefabbricato CC0";
            marker.eggHint = "Uovo nel cassetto del banco.";

            // I collider dei modelli innestati NON si toccano qui: cancellarli
            // con allowDestroyingAssets danneggerebbe i prefab sorgente. Il
            // runtime ShopPrefabInstaller li disattiva sull'istanza, come già
            // fa ShopInteriorBuilder per i mobili procedurali.

            PrefabUtility.SaveAsPrefabAsset(root, Path);
            Object.DestroyImmediate(root);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[ShopPrefabSetup] Prefabbricato CC0 creato: " + Path);
        }

        private static void Box(Transform parent, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            if (mat != null) go.GetComponent<Renderer>().sharedMaterial = mat;
            Object.DestroyImmediate(go.GetComponent<Collider>());
        }

        private static void Model(Transform parent, string resource, string name,
            Vector3 pos, Vector3 size, float yaw, string containerRoom = null)
        {
            GameObject prefab = Resources.Load<GameObject>(resource);
            if (prefab == null)
            {
                Debug.LogWarning("[ShopPrefabSetup] Risorsa mancante: " + resource);
                return;
            }
            GameObject inst = (GameObject)PrefabUtility.InstantiatePrefab(prefab);
            inst.name = name;
            inst.transform.SetParent(parent, false);
            inst.transform.localPosition = pos;
            inst.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            inst.transform.localScale = Fit(inst, size);

            if (!string.IsNullOrEmpty(containerRoom))
            {
                var cm = inst.AddComponent<UniversalContainerMarker>();
                cm.room = containerRoom;
                cm.interiorKey = "prefab#negozio";
            }
        }

        /// <summary>Ricalcola la scala locale dalla bounds del modello, come
        /// ShopInteriorBuilder.Prop, così il prefab non cambia forma.</summary>
        private static Vector3 Fit(GameObject go, Vector3 size)
        {
            var renderers = go.GetComponentsInChildren<Renderer>();
            if (renderers == null || renderers.Length == 0) return size;
            Bounds b = renderers[0].bounds;
            for (int i = 1; i < renderers.Length; i++) b.Encapsulate(renderers[i].bounds);
            Vector3 s = b.size;
            if (s.x < 0.001f || s.y < 0.001f || s.z < 0.001f) return size;
            return new Vector3(size.x / s.x, size.y / s.y, size.z / s.z);
        }
    }
}

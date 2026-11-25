using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using City.Interior.Visit;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Crea UNA VOLTA il prefabbricato della casa visitabile associata a un
    /// edificio OSM reale. Il prefab non contiene mobili istanziati: contiene
    /// solo gli SLOT normalizzati sull'impronta (4 stanze, 3 nascondigli uovo).
    /// I modelli CC0 `HouseInteriors/CozyHome` vengono istanziati a runtime dal
    /// VisitInteriorDecorator, riscalati sullo slot e senza collider.
    ///
    /// Richiamato da HuntixBuild.ExportAndroidGradleProject prima dell'AAR:
    /// il prefab finisce così in Resources e resta versionato nel repository.
    /// Nessun download, nessun Asset Store, nessun collider sui mobili.
    /// </summary>
    public static class HousePrefabSetup
    {
        public const string Folder = "Assets/Resources/HouseInteriors";
        public const string Path = Folder + "/CasaVisitabileCC0.prefab";

        public static void EnsurePrefab()
        {
            if (AssetDatabase.LoadAssetAtPath<GameObject>(Path) != null) return;
            if (!AssetDatabase.IsValidFolder(Folder))
                AssetDatabase.CreateFolder("Assets/Resources", "HouseInteriors");

            // In batchmode può non esserci una scena attiva: creane una vuota,
            // temporanea, senza toccare le scene di gioco né EditorBuildSettings.
            if (!SceneManager.GetActiveScene().isLoaded)
                EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var root = new GameObject("CasaVisitabileCC0");
            var slots = root.AddComponent<HouseSlots>();
            slots.slots = HouseSlots.DefaultLayout();

            PrefabUtility.SaveAsPrefabAsset(root, Path);
            Object.DestroyImmediate(root);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[HousePrefabSetup] Casa visitabile creata: " + Path +
                " (" + slots.slots.Count + " arredi, 4 stanze, 3 uova)");
        }
    }
}

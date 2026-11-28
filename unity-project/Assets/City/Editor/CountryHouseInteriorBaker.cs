using UnityEditor;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Baker editoriale: estrae SOLO il ramo `mainIndoor01` della casa Asset
    /// Store Сountry House FREE e lo salva come prefab Resources. Nessun codice
    /// o materiale del venditore viene modificato: si copia la gerarchia con le
    /// sue mesh, texture e collider. L'esterno (`main`/`mainHouse`) resta nel
    /// prefab originale usato da BuildingPlacer come guscio eroe.
    ///
    /// Eseguibile in batchmode:
    /// -executeMethod Huntix.EditorTools.CountryHouseInteriorBaker.Bake
    /// </summary>
    public static class CountryHouseInteriorBaker
    {
        private const string Source =
            "Assets/Resources/Houses/ViaSilvioPellico/AssetStore/CountryHouse_Prefab.prefab";
        private const string Target =
            "Assets/Resources/Houses/ViaSilvioPellico/AssetStore/CountryHouse_Interior.prefab";

        public static void Bake()
        {
            GameObject source = AssetDatabase.LoadAssetAtPath<GameObject>(Source);
            if (source == null)
            {
                Debug.LogError("[CountryBaker] prefab sorgente mancante: " + Source);
                return;
            }

            GameObject temp = Object.Instantiate(source);
            Transform indoor = Find(temp.transform, "mainIndoor01");
            if (indoor == null)
            {
                Debug.LogError("[CountryBaker] ramo mainIndoor01 mancante in " + Source);
                Object.DestroyImmediate(temp);
                return;
            }

            var root = new GameObject("CountryHouse_Interior");
            GameObject copy = Object.Instantiate(indoor.gameObject);
            copy.name = "mainIndoor01";
            copy.transform.SetParent(root.transform, false);
            copy.transform.localPosition = indoor.localPosition;
            copy.transform.localRotation = indoor.localRotation;
            copy.transform.localScale = indoor.localScale;

            // L'interno e' una tasca: tutti i collider dell'asset diventano
            // "edificio" (layer 8, escluso dalle sonde di terreno). Il suolo
            // calpestabile viene aggiunto a runtime da FinalizeCountryInterior.
            Collider[] colliders = root.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < colliders.Length; i++)
                colliders[i].gameObject.layer = 8;

            GameObject saved = PrefabUtility.SaveAsPrefabAsset(root, Target);
            Debug.Log("[CountryBaker] salvato " + Target +
                " root=" + (saved != null) + " collider=" + colliders.Length);

            Object.DestroyImmediate(root);
            Object.DestroyImmediate(temp);
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
        }

        private static Transform Find(Transform root, string name)
        {
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i].name == name) return all[i];
            return null;
        }
    }
}

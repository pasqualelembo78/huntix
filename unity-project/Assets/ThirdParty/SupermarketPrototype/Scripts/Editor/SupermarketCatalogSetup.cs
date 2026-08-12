using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections.Generic;
using System.Linq;
using Supermarket.Products;

namespace Huntix.EditorTools
{
    // Popola i riferimenti di catalogo (prodotti/licenze) sull'OnlineShopPresenter
    // della scena MainGame, così il caricamento a runtime non dipende da
    // Resources.LoadAll. Chiamato da HuntixBuild prima del BuildPlayer.
    public static class SupermarketCatalogSetup
    {
        const string SCENE_PATH = "Assets/ThirdParty/SupermarketPrototype/Scenes/MainGame.unity";
        const string PRODUCTS_DIR = "Assets/ThirdParty/SupermarketPrototype/Resources";
        const string LICENSES_DIR = "Assets/ThirdParty/SupermarketPrototype/Resources/Licenses";

        [MenuItem("Huntix/Supermarket/Populate Catalog (MainGame)")]
        public static void PopulateCatalogMenu()
        {
            PopulateCatalog();
            AssetDatabase.SaveAssets();
        }

        public static void PopulateCatalog()
        {
            var scene = EditorSceneManager.OpenScene(SCENE_PATH, OpenSceneMode.Single);
            var presenter = Object.FindObjectOfType<OnlineShopPresenter>(true);
            if (presenter == null)
            {
                UnityEngine.Debug.LogError("[SupermarketCatalogSetup] OnlineShopPresenter non trovato in " + SCENE_PATH);
                return;
            }

            var products = new List<ProductInfo>();
            foreach (var guid in AssetDatabase.FindAssets("t:ProductInfo", new[] { PRODUCTS_DIR }))
            {
                var obj = AssetDatabase.LoadAssetAtPath(AssetDatabase.GUIDToAssetPath(guid), typeof(ProductInfo));
                if (obj is ProductInfo p) products.Add(p);
            }

            var licenses = new List<License>();
            foreach (var guid in AssetDatabase.FindAssets("Product License", new[] { LICENSES_DIR })
                     .Concat(AssetDatabase.FindAssets("t:License", new[] { LICENSES_DIR })).Distinct())
            {
                var obj = AssetDatabase.LoadAssetAtPath(AssetDatabase.GUIDToAssetPath(guid), typeof(License));
                if (obj is License l) licenses.Add(l);
            }

            presenter.catalogProducts = products.ToArray();
            presenter.catalogLicenses = licenses.ToArray();

            EditorUtility.SetDirty(presenter);
            EditorSceneManager.SaveScene(scene);
            AssetDatabase.SaveAssets();

            UnityEngine.Debug.Log($"[SupermarketCatalogSetup] MainGame: {products.Count} prodotti e {licenses.Count} licenze collegati all'OnlineShopPresenter.");
        }
    }
}

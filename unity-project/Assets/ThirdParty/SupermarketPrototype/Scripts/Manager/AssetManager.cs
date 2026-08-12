using System.Collections.Generic;
using UnityEngine;
using Cysharp.Threading.Tasks;
using Supermarket;
using Supermarket.Products;

[DefaultExecutionOrder(-299)]
public static class AssetManager
{
    const string PRODUCTS_PATH = "Products";
    const string LICENSES_PATH = "Licenses";

    public static Dictionary<int, ProductInfo> ProductInfos { get; set; }
    public static Dictionary<int, License> Licenses { get; set; }

    public static async UniTask<bool> LoadLAllLicenses()
    {
        await UniTask.Yield();

        License[] result = Resources.LoadAll<License>(LICENSES_PATH);
        if (result == null || result.Length == 0)
        {
            Debug.LogWarning("[AssetManager] Nessuna licenza in Resources/" + LICENSES_PATH);
            return false;
        }

        Licenses = new Dictionary<int, License>(result.Length);
        foreach (var license in result)
        {
            license.Init();
            Licenses[license.LicenseId] = license;
        }

        return true;
    }

    public static async UniTask<bool> LoadAllProducts()
    {
        await UniTask.Yield();

        ProductInfo[] result = Resources.LoadAll<ProductInfo>(PRODUCTS_PATH);
        if (result == null || result.Length == 0)
        {
            Debug.LogWarning("[AssetManager] Nessun prodotto in Resources/" + PRODUCTS_PATH);
            return false;
        }

        ProductInfos = new Dictionary<int, ProductInfo>(result.Length);
        foreach (var productInfo in result)
        {
            ProductInfos[productInfo.ProductId] = productInfo;
        }

        return true;
    }

    // Popola i dizionari da riferimenti espliciti (asset referenziati dalla scena),
    // così il caricamento non dipende da Resources.LoadAll (che nel build AAR non
    // risolve gli asset impacchettati come dipendenze di scena).
    public static void SetCatalog(ProductInfo[] products, License[] licenses)
    {
        if (products != null && products.Length > 0)
        {
            ProductInfos = new Dictionary<int, ProductInfo>(products.Length);
            foreach (var productInfo in products)
            {
                if (productInfo != null) ProductInfos[productInfo.ProductId] = productInfo;
            }
            Debug.Log("[AssetManager] Catalogo prodotti caricato da riferimenti scena: " + ProductInfos.Count);
        }

        if (licenses != null && licenses.Length > 0)
        {
            Licenses = new Dictionary<int, License>(licenses.Length);
            foreach (var license in licenses)
            {
                if (license != null)
                {
                    license.Init();
                    Licenses[license.LicenseId] = license;
                }
            }
            Debug.Log("[AssetManager] Licenze caricate da riferimenti scena: " + Licenses.Count);
        }
    }

    public static ProductInfo GetProduct(int productId)
    {
        return ProductInfos[productId];
    }

    public static License GetLicense(int licenseId)
    {
        return Licenses[licenseId];
    }
}

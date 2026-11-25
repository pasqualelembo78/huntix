#!/usr/bin/env python3
"""Verifiche statiche dello switch A/B del negozio (prefabbricato CC0).

Controlla che l'opzione sia ADDITIVA: sistema Huntix e prefab coesistono,
nessun cambio geo/graph, nessuna nuova economia, uova e bridge invariati.
"""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


STYLE = read("unity-project/Assets/City/Scripts/Interior/ShopStyle.cs")
INSTALLER = read("unity-project/Assets/City/Scripts/Interior/ShopPrefabInstaller.cs")
GENERATOR = read("unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs")
MENU = read("unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs")
EDITOR = read("unity-project/Assets/Editor/ShopPrefabSetup.cs")
BUILD = read("unity-project/Assets/Editor/HuntixBuild.cs")
SYNC = read("unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
PLACER = read("unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs")
IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
ANDROID = read("app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt")


class TestInterruttore(unittest.TestCase):
    def test_stato_con_timestamp_e_evento(self):
        self.assertIn('BridgeEvent = "ShopStyleChanged"', STYLE)
        self.assertIn('PrefabResource = "ShopPrefabs/NegozioProntoCC0"', STYLE)
        self.assertIn('PlayerPrefs.GetInt(PrefKey, 0)', STYLE)
        self.assertIn('PlayerPrefs.SetString(TsKey, DateTime.UtcNow.Ticks', STYLE)
        self.assertIn("UnityBridge.SendMessageToAndroid(BridgeEvent, Export())", STYLE)

    def test_import_non_distruttivo_per_timestamp(self):
        self.assertIn("public static void Import(string json)", STYLE)
        self.assertIn("if (p.ts <= local) return;", STYLE)
        self.assertIn("public static string Export()", STYLE)

    def test_default_e_il_sistema_huntix(self):
        self.assertIn('PlayerPrefs.GetInt(PrefKey, 0)', STYLE)
        self.assertNotIn("UsePrefab { get { return true", STYLE)


class TestPrefabbricato(unittest.TestCase):
    def test_prefab_reale_nelle_resources(self):
        self.assertIn('public const string Path = Folder + "/NegozioProntoCC0.prefab"', EDITOR)
        self.assertIn("Assets/Resources/ShopPrefabs", EDITOR)
        self.assertIn("PrefabUtility.SaveAsPrefabAsset(root, Path)", EDITOR)
        self.assertIn("AssetDatabase.CreateFolder", EDITOR)

    def test_asset_cc0_gia_presenti(self):
        for path in ("Furniture/kitchenFridge", "Furniture/cabinetBedDrawer",
                     "Furniture/cardboardBoxClosed", "KenneyMiniMarket/cash-register",
                     "KenneyMiniMarket/shelf-bags", "KenneyMiniMarket/shelf-boxes",
                     "KenneyMiniMarket/display-bread", "KenneyMiniMarket/shopping-cart"):
            self.assertIn('"' + path + '"', EDITOR, path)
        self.assertIn('"CuteStore/Prefabs/" + products[i]', EDITOR)
        for prodotto in ("apple_green", "bottle1_water", "can_food_blue", "donut_red"):
            self.assertIn('"' + prodotto + '"', EDITOR, prodotto)
        self.assertNotIn("AssetStore", EDITOR)
        self.assertNotIn("Mixamo", EDITOR)

    def test_editor_non_distrugge_i_prefab_sorgente(self):
        self.assertNotIn("DestroyImmediate(col, true)", EDITOR)
        self.assertIn("ShopPrefabInstaller li disattiva sull'istanza", EDITOR)
        self.assertIn("EditorSceneManager.NewScene(NewSceneSetup.EmptyScene", EDITOR)
        # build_release.sh chiama ExportAndroidGradleProject, NON
        # ConfigureAndExport: l'hook deve stare dentro il metodo eseguito.
        esporta = BUILD.split("public static void ExportAndroidGradleProject()", 1)[1]
        self.assertIn("ShopPrefabSetup.EnsurePrefab();",
                      esporta.split("public static void", 1)[0])

    def test_installer_idempotente_e_senza_collider(self):
        self.assertIn('FindChild(floor, "NegozioProntoCC0") != null', INSTALLER)
        self.assertIn("Resources.Load<GameObject>(ShopStyle.PrefabResource)", INSTALLER)
        self.assertIn("UnityEngine.Object.Destroy(col)", INSTALLER)
        self.assertIn("Mathf.Clamp", INSTALLER)

    def test_uova_viaggiano_nel_prefab(self):
        self.assertIn("UniversalContainerMarker", INSTALLER)
        self.assertIn("UniversalInteriorMarker", INSTALLER)
        self.assertIn("UniversalInteriorRegistry.StableKey", INSTALLER)
        self.assertIn('marker.eggHint = UniversalInteriorRegistry.EggHint(kind)', INSTALLER)
        self.assertIn("CassettoPronto", EDITOR)
        self.assertIn("DispensaPronto", EDITOR)

    def test_fallback_senza_stanza_vuota(self):
        self.assertIn("UniversalInteriorBuilder.Build(parent, type, shop, w, d,", INSTALLER)
        self.assertTrue(re.search(r'if \(prefab == null\).*?UniversalInteriorBuilder\.Build',
                                  INSTALLER, re.S))


class TestCoesistenza(unittest.TestCase):
    def test_generatore_sceglie_senza_eliminare(self):
        self.assertIn("if (ShopStyle.UsePrefab)", GENERATOR)
        self.assertIn("ShopPrefabInstaller.Install(parent, type, shop", GENERATOR)
        self.assertIn("UniversalInteriorBuilder.Build(parent, type, shop", GENERATOR)
        self.assertIn("ShopInteriorBuilder.Decorate(parent, type, shop", GENERATOR)

    def test_menu_voce_e_reload_sicuro(self):
        self.assertIn('"Negozio: Huntix"', MENU)
        self.assertIn("() => ToggleShopStyle()", MENU)
        self.assertIn("private void ToggleShopStyle()", MENU)
        self.assertIn("private void RefreshShopLabel()", MENU)
        self.assertIn("RefreshShopLabel();", MENU)
        self.assertIn("cw.ReloadWorld();", MENU)
        self.assertIn("Esci dall'edificio prima di cambiare negozio", MENU)
        self.assertIn("Scendi dall'auto prima di cambiare negozio", MENU)
        self.assertIn("new Vector2(360f, 1620f)", MENU)

    def test_invarianti_fisica_e_cache(self):
        self.assertIn("CacheVersion = 5", TILE_CLIENT)
        self.assertIn("private const int MaxEnterablePerChunk = 8", PLACER)
        self.assertNotIn("SceneManager.LoadScene", INSTALLER)
        self.assertNotIn("Wallet", STYLE)
        self.assertNotIn("Rarity", STYLE)


class TestPonteAndroid(unittest.TestCase):
    def test_snapshot_unico(self):
        self.assertIn("j.shopStyle = City.Interior.ShopStyle.Export()", SYNC)
        self.assertIn('public string shopStyle = ""', SYNC)
        self.assertIn("City.Interior.ShopStyle.Import(j.shopStyle)", SYNC)

    def test_whitelist_e_router(self):
        self.assertIn('"ShopStyleChanged"', IPC)
        self.assertIn('"ShopStyleChanged" ->', BRIDGE)
        self.assertIn("CityStateSync.onShopStyleChanged(ctx, jsonData)", BRIDGE)

    def test_mirror_e_check_sync(self):
        self.assertIn('KEY_SHOP_STYLE = "city_shop_style_v1"', ANDROID)
        self.assertIn("fun onShopStyleChanged(context: Context, json: String)", ANDROID)
        self.assertIn('j.optString("shopStyle", "")', ANDROID)
        self.assertIn('report.put("shopStyle"', ANDROID)
        self.assertIn('report.put("shopStyleSnapshot"', ANDROID)
        self.assertIn('incoming.optLong("ts", 0) > local.optLong("ts", 0)', ANDROID)


if __name__ == "__main__":
    unittest.main()

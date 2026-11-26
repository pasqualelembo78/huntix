#!/usr/bin/env python3
"""Verifiche statiche del restyling condiviso e del profilo prestazioni additivo.

Non c'e' un device in questa macchina: i test controllano le invarianti che
rendono l'intervento sicuro (nessun valore protetto dal Mandato toccato, bridge
bidirezionale completo, riflesso uova, token UI centralizzati).
"""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


PROFILE = read("unity-project/Assets/City/Scripts/Diagnostics/AdaptivePerformanceProfile.cs")
CHUNK_MANAGER = read("unity-project/Assets/City/Scripts/OSM/ChunkManager.cs")
CHUNK_DATA = read("unity-project/Assets/City/Scripts/OSM/ChunkData.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
TRAFFIC = read("unity-project/Assets/City/Scripts/Vehicle/Traffic/TileRoadNetwork.cs")
GAME = read("unity-project/Assets/City/Scripts/Game.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
MENU = read("unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs")

UNITY_IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
BRIDGE_ACTIVITY = read("app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java")
BRIDGE_SERVICE = read("app/src/main/java/com/intelligame/huntix/bridge/UnityBridgeService.kt")
OUTDOOR = read("app/src/main/java/com/intelligame/huntix/manager/OutdoorManager.kt")
UI_KIT = read("app/src/main/java/com/intelligame/huntix/UiKit.kt")
COLORS = read("app/src/main/res/values/colors.xml")
THEMES = read("app/src/main/res/values/themes.xml")


class TestProfiloAdattivo(unittest.TestCase):
    def test_tier_e_valori_conservativi(self):
        self.assertIn("public enum Tier { Low = 0, Medium = 1, High = 2 }", PROFILE)
        self.assertIn("ChunkBuildBudgetMs", PROFILE)
        self.assertIn("PrefetchKeep", PROFILE)
        self.assertIn("VehicleDensity", PROFILE)
        self.assertIn("DistantBuildingShadows", PROFILE)
        # Il budget non puo' superare i 12 ms storici, ne' il prefetch gli 8 file.
        nums = re.findall(r"ChunkBuildBudgetMs\s*=>\s*(.*?);", PROFILE, re.S)
        self.assertTrue(nums)
        self.assertLessEqual(max(int(x) for x in re.findall(r"\d+", nums[0])), 12)
        prefetch = re.findall(r"PrefetchKeep\s*=>\s*(.*?);", PROFILE, re.S)[0]
        self.assertLessEqual(max(int(x) for x in re.findall(r"\d+", prefetch)), 8)

    def test_valori_protetti_non_toccati(self):
        self.assertRegex(CHUNK_MANAGER, r"LoadRadius\s*=\s*3")
        self.assertRegex(CHUNK_MANAGER, r"UnloadRadius\s*=\s*4")
        self.assertIn("TerrainColliderLod = 0", CHUNK_DATA)
        self.assertIn("CacheVersion = 5", TILE_CLIENT)
        self.assertIn("CarsPerTile = 22", TRAFFIC)
        self.assertIn("MaxCars = 180", TRAFFIC)
        self.assertNotIn("UseQuaternius = 1", read(
            "unity-project/Assets/City/Scripts/UI/CityStyle.cs"))

    def test_budget_chunk_solo_runtime(self):
        self.assertIn("CurrentBuildBudgetMs", CHUNK_MANAGER)
        self.assertIn("AdaptivePerformanceProfile.Ensure()", CHUNK_MANAGER)
        self.assertIn("Instance.ChunkBuildBudgetMs", CHUNK_MANAGER)
        self.assertIn("Instance.PrefetchKeep", TILE_CLIENT)
        self.assertIn("TargetCarCount()", TRAFFIC)
        self.assertIn("Instance.VehicleDensity", TRAFFIC)

    def test_ombre_lod_senza_cambiare_collider(self):
        self.assertIn("SetBuildingShadows", CHUNK_DATA)
        self.assertIn("RefreshAdaptiveDetails", CHUNK_DATA)
        self.assertIn("RefreshAdaptiveDetails()", CHUNK_MANAGER)
        self.assertIn("DistantBuildingShadows", CHUNK_DATA)

    def test_riflesso_uova_unica(self):
        self.assertIn("adaptivePerfEggGranted", GAME)
        self.assertIn("OnAdaptivePerformanceImproved()", GAME)
        self.assertIn('GrantAuditEgg("performance->adattivo->recupero"', GAME)
        # Una sola chiave di premio: nessun doppio uovo rispetto al profilo qualita'.
        self.assertEqual(GAME.count("performance->adattivo->recupero"), 1)
        self.assertIn("Prestazioni adattive", MENU)
        self.assertIn("ToggleAdaptivePerformance", MENU)


class TestPontePrestazioni(unittest.TestCase):
    def test_unity_to_android(self):
        self.assertIn("CityPerfTierChanged", PROFILE)
        self.assertIn("SendMessageToAndroid", PROFILE)
        self.assertIn('"CityPerfTierChanged"', UNITY_IPC)
        self.assertIn('"CityPerfTierChanged" -> handleCityPerfTierChanged(jsonData)', BRIDGE)
        self.assertIn("fun handleCityPerfTierChanged", BRIDGE)
        self.assertIn('putString("city_perf_tier", tier)', BRIDGE)

    def test_android_to_unity(self):
        self.assertIn("public void setAdaptivePerformance(string json)", GAME_MANAGER)
        self.assertIn('case "setAdaptivePerformance":', GAME_MANAGER)
        self.assertIn("HandleSetAdaptivePerformance", GAME_MANAGER)
        self.assertIn('\\"adaptivePerformance\\"', GAME_MANAGER)
        self.assertIn("ApplyRestored", GAME_MANAGER)
        self.assertIn('j.put("adaptivePerformance", adaptive)', BRIDGE_ACTIVITY)
        self.assertIn('adaptive.put("enabled"', BRIDGE_ACTIVITY)
        self.assertIn('adaptive.put("quality"', BRIDGE_ACTIVITY)
        self.assertIn("ApplyRestored(bool enabled)", PROFILE)
        # Il ripristino non deve generare uova ne' eco verso Android.
        self.assertIn("_restoring", PROFILE)


class TestTemaCondiviso(unittest.TestCase):
    def test_token_semantici(self):
        for name in ("huntix_bg", "huntix_surface", "huntix_accent", "huntix_gold",
                     "huntix_success", "huntix_text_dim", "huntix_nav_bg",
                     "huntix_nav_active", "huntix_nav_inactive"):
            self.assertIn('<color name="%s">' % name, COLORS)
        self.assertIn("android:statusBarColor\">@color/huntix_bg", THEMES)

    def test_helper_condivisi(self):
        self.assertIn("fun themed(c: Context, res: Int)", UI_KIT)
        self.assertIn("fun topBar(c: Context, title: String", UI_KIT)
        self.assertIn("fun backButton(c: Context", UI_KIT)
        self.assertIn("fun chip(c: Context", UI_KIT)
        self.assertIn("UiKit.topBar(this, \"Il mio Profilo\"", read(
            "app/src/main/java/com/intelligame/huntix/ui/PlayerProfileActivity.kt"))
        self.assertIn("UiKit.themed", read(
            "app/src/main/java/com/intelligame/huntix/ui/OfflinePacksActivity.kt"))
        self.assertIn("UiKit.themed", read(
            "app/src/main/java/com/intelligame/huntix/ui/ShopActivity.kt"))


class TestOttimizzazioniAndroid(unittest.TestCase):
    def test_handler_condiviso(self):
        self.assertIn("mainHandler.post", BRIDGE_SERVICE)
        self.assertIn("private val mainHandler by lazy", BRIDGE_SERVICE)
        self.assertNotIn("Handler(Looper.getMainLooper()).post {\n                try {\n                    StoreUnityBridge",
                         BRIDGE_SERVICE)

    def test_outdoor_stop_annulla_camera(self):
        self.assertIn("walkHandler?.removeCallbacksAndMessages(null)", OUTDOOR)
        self.assertIn("walkHandler = null", OUTDOOR)


if __name__ == "__main__":
    unittest.main()

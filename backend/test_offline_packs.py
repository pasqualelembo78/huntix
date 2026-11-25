#!/usr/bin/env python3
"""Verifiche statiche del pacchetto offline su richiesta di MiCittà.

Non c'e' un device Android/Unity in questa macchina: il test controlla che il
manifest generato, il flusso di spawn, la cache v5, il ponte bidirezionale e il
riflesso uova siano coerenti. Non dichiara test visivi o di FPS.
"""
import json
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


MANIFEST = json.loads(read("app/src/main/assets/offline_packs.json"))
INDEX = json.loads(read("backend/preprocessing/tiles/index.json"))
HOME = read("app/src/main/java/com/intelligame/huntix/HomeActivity.kt")
MANAGER = read("app/src/main/java/com/intelligame/huntix/bridge/OfflinePackManager.kt")
PRELOADER = read("app/src/main/java/com/intelligame/huntix/bridge/CityTilePreloader.kt")
ACTIVITY = read("app/src/main/java/com/intelligame/huntix/ui/OfflinePacksActivity.kt")
STORE = read("app/src/main/java/com/intelligame/huntix/bridge/StoreUnityBridge.kt")
DISPATCH = read("app/src/main/java/com/intelligame/huntix/bridge/UnityAppCallback.kt")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
UNITY_BRIDGE = read("unity-project/Assets/City/Scripts/OSM/OfflinePackBridge.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
SPAWN_CHOOSER = read("app/src/main/java/com/intelligame/huntix/bridge/WorldPosCloud.kt")
MANIFEST_SRC = read("backend/preprocessing/offline_pack_manifest.py")
MANIFEST_XML = read("app/src/main/AndroidManifest.xml")


class TestManifest(unittest.TestCase):
    def test_formato_e_versione_cache(self):
        self.assertEqual(MANIFEST["version"], 1)
        m = re.search(r"CacheVersion\s*=\s*(\d+)", TILE_CLIENT)
        self.assertIsNotNone(m)
        self.assertEqual(MANIFEST["cacheVersion"], int(m.group(1)))
        self.assertEqual(MANIFEST["cacheVersion"], 5)

    def test_tutte_le_tile_italia_presenti(self):
        world = [p for p in MANIFEST["packs"] if p["id"] == "italia"]
        self.assertEqual(len(world), 1)
        index_keys = {t["key"] for t in INDEX["tiles"]}
        self.assertEqual(set(world[0]["keys"]), index_keys)
        self.assertEqual(MANIFEST["tileCount"], len(index_keys))

    def test_ogni_citta_di_spawn_ha_un_pacchetto(self):
        names = re.findall(
            r'arrayOf\(\s*"([^"]+)"\s*,\s*"(-?[\d.]+)"\s*,\s*"(-?[\d.]+)"\s*\)',
            SPAWN_CHOOSER)
        labels = {p["label"] for p in MANIFEST["packs"]}
        self.assertTrue(names)
        missing = [n for n, _, _ in names if n not in labels]
        self.assertEqual(missing, [], f"citta' spawn senza pacchetto: {missing}")

    def test_chiavi_pacchetto_sono_tile_reali(self):
        index_keys = {t["key"] for t in INDEX["tiles"]}
        for p in MANIFEST["packs"]:
            self.assertTrue(p["keys"], p["id"])
            self.assertTrue(set(p["keys"]).issubset(index_keys), p["id"])
            self.assertGreater(p["bytes"], 0, p["id"])
            self.assertGreater(p["installedBytes"], p["bytes"], p["id"])

    def test_generatore_riproducibile(self):
        self.assertIn('OUT = ROOT / "app/src/main/assets/offline_packs.json"', MANIFEST_SRC)
        self.assertIn("SPAWN_CHOOSER.read_text", MANIFEST_SRC)


class TestFlussoGioco(unittest.TestCase):
    def test_un_solo_apk_e_sezione_home(self):
        self.assertEqual(MANIFEST_XML.count("<application"), 1)
        self.assertIn("OfflinePacksActivity", MANIFEST_XML)
        self.assertIn('quickChip("\\uD83D\\uDCBE", "Offline"', HOME)

    def test_dopo_spawn_controlla_flag_e_disco(self):
        self.assertIn("runMiacittaPreload(mode, name, lat, lng)", HOME)
        self.assertIn("OfflinePackManager.isFlagged(this, \"italia\")", HOME)
        self.assertIn("OfflinePackManager.isInstalled(this, it)", HOME)
        self.assertIn("askOfflineDownload(name, spawnLat, spawnLng, cityPack)", HOME)
        self.assertIn('"Gioca online"', HOME)
        self.assertIn("Non chiedere più per questa città", HOME)

    def test_flag_separato_dallo_stato_reale(self):
        self.assertIn("city_offline_packs_v1", MANAGER)
        self.assertIn("fun isFlagged", MANAGER)
        self.assertNotIn("id == WORLD_ID ||", MANAGER)
        self.assertIn("fun missingKeys", MANAGER)
        self.assertIn("fun downloadPacks", MANAGER)
        self.assertIn("Il flag NON basta", ACTIVITY)
        self.assertIn("isInstalled", ACTIVITY)

    def test_cache_v5_invariata(self):
        self.assertIn('CacheVersion = 5', TILE_CLIENT)
        self.assertIn('private const val CACHE_VERSION = 5', PRELOADER)
        self.assertIn('"$key.v$CACHE_VERSION.$kind.json"', PRELOADER)
        self.assertIn("downloadKeysSync", PRELOADER)

    def test_download_solo_tile_mancanti(self):
        self.assertIn("missingKeys(ctx, it)", MANAGER)
        self.assertIn("LinkedHashSet<String>()", MANAGER)
        self.assertIn("CityTilePreloader.downloadKeysSync", MANAGER)
        self.assertIn("Spazio insufficiente", MANAGER)


class TestPonteEParadigma(unittest.TestCase):
    def test_ponte_unity_android(self):
        for name in ("getOfflinePackStatus", "setOfflinePackFlag",
                     "requestOfflinePack", "isTileOffline"):
            self.assertIn(name, STORE)
            self.assertIn(f'"{name}"', DISPATCH)
        self.assertIn('sendToUnityIfAlive(\n                        "GameManager", "OnOfflinePackChanged"', MANAGER)

    def test_lato_unity(self):
        self.assertIn("OfflinePackBridge.LogStatusOnce()", GAME_MANAGER)
        self.assertIn("OnOfflinePackChanged(string json)", GAME_MANAGER)
        self.assertIn("getOfflinePackStatus", UNITY_BRIDGE)
        self.assertIn("requestOfflinePack", UNITY_BRIDGE)

    def test_riflesso_uova(self):
        self.assertIn('"offline_pack_ready"', UNITY_BRIDGE)
        self.assertIn("Cacciatore Offline", UNITY_BRIDGE)
        self.assertIn("GrantAuditEgg", UNITY_BRIDGE)


if __name__ == "__main__":
    unittest.main()

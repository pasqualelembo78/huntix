#!/usr/bin/env python3
"""Verifiche statiche del World Streaming additivo di MiCittà.

Non c'e' un device Android/Unity in questa macchina: il test controlla le
invarianti del mondo, il catalogo generato, l'assenza di accessi ai dati del
profilo durante lo scaricamento dei settori, il ponte bidirezionale e il
riflesso della caccia alle uova. Non dichiara test visivi o di FPS.
"""
import json
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


CHUNK_MANAGER = read("unity-project/Assets/City/Scripts/OSM/ChunkManager.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
CITY_WORLD = read("unity-project/Assets/City/Scripts/OSM/CityChunkedWorld.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
STREAM_MANAGER = read("unity-project/Assets/City/Scripts/OSM/WorldStreamingManager.cs")
STREAM_SKYLINE = read("unity-project/Assets/City/Scripts/OSM/WorldStreamingSkyline.cs")
STREAM_CATALOG = read("unity-project/Assets/City/Scripts/OSM/WorldStreamingCatalog.cs")
STREAM_BRIDGE = read("unity-project/Assets/City/Scripts/OSM/WorldStreamingBridge.cs")
CATALOG = json.loads(read("unity-project/Assets/Resources/world_sectors.json"))
CATALOG_SRC = read("backend/preprocessing/world_sector_catalog.py")
INDEX = json.loads(read("backend/preprocessing/tiles/index.json"))
UNITY_IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE_KT = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
DISPATCH = read("app/src/main/java/com/intelligame/huntix/bridge/UnityAppCallback.kt")
STORE = read("app/src/main/java/com/intelligame/huntix/bridge/StoreUnityBridge.kt")
MIRROR = read("app/src/main/java/com/intelligame/huntix/bridge/WorldStreamingMirror.kt")


class TestInvariantiMondo(unittest.TestCase):
    def test_raggio_chunk_invariato(self):
        self.assertIn("public const int LoadRadius = 3", CHUNK_MANAGER)
        self.assertIn("public const int UnloadRadius = 4", CHUNK_MANAGER)
        self.assertIn("ChunkManager.LoadRadius", STREAM_MANAGER)
        self.assertIn("ChunkManager.UnloadRadius", STREAM_MANAGER)

    def test_cache_v5_invariata(self):
        self.assertIn("public const int CacheVersion = 5", TILE_CLIENT)
        self.assertIn("TileClient.CacheVersion", STREAM_BRIDGE)

    def test_snap_altimetrico_non_toccato(self):
        # Nessun raycast "hit piu' alto": lo streaming non puo' piazzare il
        # player sui tetti con il DEM attivo (trappola del ramo altitudine).
        for name, body in (("manager", STREAM_MANAGER), ("skyline", STREAM_SKYLINE)):
            self.assertNotIn("RaycastAll", body, name)
            self.assertNotIn("bestY", body, name)
        self.assertIn("TerrainColliderLod", CHUNK_MANAGER)

    def test_chunk_manager_non_sostituito(self):
        # Lo streaming e' ADDITIVO: il tick esistente resta e riceve solo un
        # hint di direzione; nessun nuovo manager spegne StreamEnabled.
        self.assertIn("WorldStreamingManager.Ensure(this)", CHUNK_MANAGER)
        self.assertIn("public void SetDirectionalHint(Vector2Int dir)", CHUNK_MANAGER)
        self.assertIn("StreamingEnabled", STREAM_MANAGER)
        self.assertNotIn("StreamingEnabled = false", STREAM_MANAGER)
        self.assertNotIn("LoadRadius =", STREAM_MANAGER)


class TestCatalogoSettori(unittest.TestCase):
    def test_formato_e_copertura(self):
        self.assertEqual(CATALOG["version"], 1)
        sectors = CATALOG["sectors"]
        self.assertGreater(len(sectors), 3000)
        index_keys = {t["key"] for t in INDEX["tiles"]}
        self.assertEqual({s["key"] for s in sectors}, index_keys)
        self.assertEqual(len(sectors), len(index_keys))

    def test_centro_in_bbox_e_dati_noti(self):
        for s in CATALOG["sectors"]:
            b = s["bbox"]
            self.assertEqual(len(b), 4, s["key"])
            self.assertLessEqual(b[0], s["lat"], s["key"])
            self.assertLessEqual(s["lat"], b[2], s["key"])
            self.assertLessEqual(b[1], s["lng"], s["key"])
            self.assertLessEqual(s["lng"], b[3], s["key"])
            self.assertGreaterEqual(s["buildings"], 0, s["key"])
            self.assertGreaterEqual(s["bytes"], 0, s["key"])

    def test_generatore_senza_rete_e_senza_geo(self):
        self.assertIn('INDEX = HERE / "tiles" / "index.json"', CATALOG_SRC)
        self.assertIn('Resources/world_sectors.json', CATALOG_SRC)
        self.assertNotIn("requests.", CATALOG_SRC)
        self.assertNotIn("urlopen", CATALOG_SRC)
        self.assertIn("WorldStreamingCatalog.Count", STREAM_MANAGER)


class TestStreamingAdditivo(unittest.TestCase):
    UNITY = (STREAM_MANAGER, STREAM_SKYLINE, STREAM_CATALOG, STREAM_BRIDGE)

    def test_nessun_dato_persistente_toccato_durante_unload(self):
        vietati = ("PlayerPrefs", "CityStateSync", "getEggCount", "spendMvc",
                   "addMvc", "getMvcBalance", "getPlayerXp", "setSharedPreference")
        for body in self.UNITY:
            for token in vietati:
                self.assertNotIn(token, body, f"{token} in uno script streaming")

    def test_prefetch_solo_cache(self):
        self.assertIn("Task.Run(() => ReadSafe(path))", TILE_CLIENT)
        self.assertIn("public static bool HasCachedGeo(string tileKey)", TILE_CLIENT)
        self.assertIn("public static bool TryGetGeoJson(string tileKey, out string json)",
                      TILE_CLIENT)
        self.assertIn("public static void TrimPrefetch(int keep)", TILE_CLIENT)
        blocco = TILE_CLIENT[TILE_CLIENT.index("public static IEnumerator PrefetchSector"):
                             TILE_CLIENT.index("private static void Remember")]
        self.assertNotIn("UnityWebRequest", blocco)
        self.assertNotIn("BaseUrl", blocco)

    def test_teleport_prepara_prima_di_unload(self):
        pos = CITY_WORLD.index("WorldStreamingManager.NotifyTeleport(lat, lng)")
        self.assertIn("Manager.UnloadAll();", CITY_WORLD[pos:pos + 500])
        self.assertIn("TeleportChunkThreshold", STREAM_MANAGER)
        self.assertIn("RequestResourceCompaction", STREAM_MANAGER)
        self.assertIn("Resources.UnloadUnusedAssets()", STREAM_MANAGER)

    def test_culling_e_skyline_non_esplorabile(self):
        self.assertIn("Vector3.Dot(fwd", STREAM_MANAGER)
        self.assertIn("SkylineDistanceM", STREAM_MANAGER)
        self.assertIn("MaxSkylineSectors", STREAM_MANAGER)
        self.assertNotIn("Collider", STREAM_SKYLINE)
        self.assertNotIn("Light", STREAM_SKYLINE)
        self.assertNotIn("Instantiate(", STREAM_SKYLINE)
        self.assertNotIn("Resources.Load<GameObject>", STREAM_SKYLINE)
        self.assertIn("new Mesh()", STREAM_SKYLINE)
        self.assertIn("sharedMaterial", STREAM_SKYLINE)
        self.assertIn("public void Shift(Vector3 delta)", STREAM_SKYLINE)
        self.assertIn("skyline.Shift(delta)", STREAM_MANAGER)

    def test_lod_e_memoria(self):
        self.assertIn("LowMemSkylineBuildings", STREAM_MANAGER)
        self.assertIn("TrimPrefetch(2)", STREAM_MANAGER)
        self.assertIn("UnloadUnusedCooldownS", STREAM_MANAGER)
        self.assertIn("SetDirectionalHint", STREAM_MANAGER)


class TestPonteEParadigma(unittest.TestCase):
    def test_eventi_whitelistati_e_router(self):
        for ev in ("WorldStreamingReady", "WorldSectorChanged"):
            self.assertIn(f'"{ev}"', UNITY_IPC)
            self.assertIn(f'"{ev}"', BRIDGE_KT)
        self.assertIn("StoreUnityBridge.onWorldStreamingReady(jsonData)", BRIDGE_KT)
        self.assertIn("StoreUnityBridge.onWorldSectorChanged(jsonData)", BRIDGE_KT)

    def test_dispatcher_unity_verso_android(self):
        for method in ("getWorldStreamingStatus", "requestWorldSector",
                       "getDeviceMemoryInfo"):
            self.assertIn(f'"{method}"', DISPATCH)
            self.assertIn(f'fun {method}', STORE)
            self.assertIn(f'"{method}"', STREAM_BRIDGE)

    def test_android_verso_unity(self):
        self.assertIn('sendToUnity("GameManager", "OnWorldStreamingStatus"', MIRROR)
        self.assertIn("OnWorldStreamingStatus(string json)", GAME_MANAGER)
        self.assertIn("WorldStreamingBridge.OnStatusFromAndroid(json)", GAME_MANAGER)

    def test_pref_dedicati_e_nessuna_rete_automatica(self):
        self.assertIn('PREF_FILE = "world_game_prefs"', MIRROR)
        self.assertIn('KEY_VISITED = "world_streaming_visited_v1"', MIRROR)
        self.assertIn('KEY_REQUESTED = "world_streaming_requested"', MIRROR)
        self.assertNotIn('getString("city_offline_packs_v1"', MIRROR)
        for token in ("OkHttp", "HttpURLConnection", "URL(", "downloadPack"):
            self.assertNotIn(token, MIRROR, token)
        self.assertIn("mi.lowMemory || mi.availMem < mi.threshold", MIRROR)
        self.assertIn('\\"low\\":true', STREAM_MANAGER)

    def test_riflesso_uova(self):
        self.assertIn('"world_sector_discovered"', STREAM_BRIDGE)
        self.assertIn("Esploratore del Settore", STREAM_BRIDGE)
        self.assertIn("GrantAuditEgg", STREAM_BRIDGE)
        self.assertIn("FeatureEggHidden", UNITY_IPC)
        self.assertIn('"FeatureEggHidden" -> handleFeatureEggHidden(jsonData)', BRIDGE_KT)


if __name__ == "__main__":
    unittest.main()

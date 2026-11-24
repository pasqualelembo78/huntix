"""Verifica statica delle integrazioni dell'audit Unity 23/11/2026.

Questi test NON sostituiscono il collaudo su dispositivo: controllano che i
collegamenti fra UI, logica di gioco, veicolo, uova e bridge Android siano
effettivamente presenti nei sorgenti che entrano nella build.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_audit_features.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITY = os.path.join(ROOT, "unity-project", "Assets")
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix", "bridge")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestHandbrakeIntegration(unittest.TestCase):
    def setUp(self):
        self.vehicle = read(os.path.join(UNITY, "City", "Scripts", "Vehicle", "VehicleController.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))

    def test_vehicle_espone_set_handbrake_e_derapata(self):
        self.assertIn("public void SetHandbrake(bool on)", self.vehicle)
        self.assertIn("handbrakeInput", self.vehicle)
        self.assertIn("HandbrakeBrakeFactor", self.vehicle)
        self.assertIn("HandbrakeYawBoost", self.vehicle)
        self.assertIn("HandbrakeSlideFactor", self.vehicle)

    def test_nessun_wheelcollider_aggiunto(self):
        # Il modulo guida esistente e' Rigidbody + velocita' scalare: la
        # derapata non deve introdurre un sistema fisico nuovo sul mobile.
        # Il nome puo' comparire solo nei commenti, mai come tipo usato.
        self.assertNotIn("new WheelCollider", self.vehicle)
        self.assertNotIn("AddComponent<WheelCollider>", self.vehicle)
        self.assertNotIn("GetComponent<WheelCollider>", self.vehicle)
        self.assertNotIn("WheelCollider[]", self.vehicle)

    def test_pulsante_touch_tieni_premuto(self):
        self.assertIn("FRENO\\nA MANO", self.ui)
        self.assertIn("EventTriggerType.PointerDown", self.ui)
        self.assertIn("EventTriggerType.PointerUp", self.ui)
        self.assertIn("Game.Instance.SetHandbrake(true)", self.ui)
        self.assertIn("Game.Instance.SetHandbrake(false)", self.ui)

    def test_game_collega_freno_a_veicolo_e_uovo(self):
        self.assertIn("public void SetHandbrake(bool on)", self.game)
        self.assertIn("CurrentVehicle.SetHandbrake(on)", self.game)
        self.assertIn('GrantFeatureEgg("handbrake"', self.game)


class TestQualityProfileIntegration(unittest.TestCase):
    def setUp(self):
        self.selector = read(
            os.path.join(UNITY, "City", "Scripts", "Diagnostics", "QualityProfileSelector.cs")
        )
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))

    def test_livelli_unity_esistenti(self):
        # Non vanno aggiunti livelli ne' cambiate dipendenze: si usano i 6
        # QualitySettings gia' definiti nel progetto.
        self.assertIn("{ 1, 2, 3 }", self.selector)
        self.assertIn("QualitySettings.SetQualityLevel", self.selector)

    def test_nessuna_forzatura_all_avvio(self):
        self.assertIn("applyStoredOnStart", self.selector)
        self.assertIn("if (applyStoredOnStart) Apply(Current, false);", self.selector)

    def test_menu_e_riflesso_uova(self):
        self.assertIn("Qualità grafica", self.menu)
        self.assertIn("ToggleQuality()", self.menu)
        self.assertIn("OnQualityProfileApplied", self.game)


class TestEggReflection(unittest.TestCase):
    def test_spawn_reward_egg_usando_eggc_controller(self):
        src = read(os.path.join(UNITY, "City", "Scripts", "Economy", "EggSpawnManager.cs"))
        self.assertIn("public GameObject SpawnRewardEgg(", src)
        self.assertIn("AddComponent<EggController>()", src)
        self.assertIn("MAX_EGGS", src)


class TestAndroidBridge(unittest.TestCase):
    def setUp(self):
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))

    def test_eventi_whitelistati(self):
        self.assertIn('"FeatureEggHidden"', self.ipc)
        self.assertIn('"CityQualityChanged"', self.ipc)

    def test_android_riceve_e_persiste(self):
        self.assertIn('"FeatureEggHidden" -> handleFeatureEggHidden(jsonData)', self.bridge)
        self.assertIn('"CityQualityChanged" -> handleCityQualityChanged(jsonData)', self.bridge)
        self.assertIn('putString("city_quality", profile)', self.bridge)

    def test_android_restituisce_la_qualita_a_unity(self):
        self.assertIn('j.put("cityQuality", quality)', self.activity)
        self.assertIn(r'\"cityQuality\"', self.manager)
        self.assertIn("QualityProfileSelector.Ensure().Apply(p, false)", self.manager)


class TestAlternateTouchIntegration(unittest.TestCase):
    """Joystick alternativo Zone Zero (MIT): affiancato, non sostitutivo."""

    def setUp(self):
        self.touch = read(os.path.join(UNITY, "City", "Scripts", "UI", "AlternateTouchInput.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))

    def test_attribuzione_mit_presente(self):
        self.assertIn("Adattato da Zone Zero", self.touch)
        self.assertIn("Mishal Bhasim", self.touch)

    def test_sorgente_alternativa_e_opzionale(self):
        self.assertIn("public static bool Enabled", self.touch)
        self.assertIn("SetEnabled(bool on, bool notify)", self.touch)
        self.assertIn('"huntix_alt_touch"', self.touch)
        self.assertIn("AlternateTouchInput.Enabled && altJoystick != null", self.ui)
        self.assertIn("altJoystick.Value : joystick.Value", self.ui.replace("\n", ""))

    def test_menu_e_riflesso_uova(self):
        self.assertIn("Input touch:", self.menu)
        self.assertIn("ToggleTouch()", self.menu)
        self.assertIn("OnAlternateTouchEnabled()", self.game)
        self.assertIn('GrantFeatureEgg("touch-zone-zero"', self.game)

    def test_bridge_bidirezionale(self):
        self.assertIn('"AltTouchChanged"', self.ipc)
        self.assertIn('"AltTouchChanged" -> handleAltTouchChanged(jsonData)', self.bridge)
        self.assertIn('putBoolean("alt_touch"', self.bridge)
        self.assertIn('j.put("altTouch", true)', self.activity)
        self.assertIn(r'\"altTouch\"', self.manager)
        self.assertIn("AlternateTouchInput.SetEnabled(true, false)", self.manager)


class TestTrafficYieldInstrumentation(unittest.TestCase):
    """Misura diagnostica della precedenza pedonale: zero cambio di guida."""

    def setUp(self):
        self.meter = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                      "TrafficYieldMeter.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                       "PerfProbe.cs"))
        self.car = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                     "Traffic", "CarAgent.cs"))

    def test_misura_senza_modificare_velocita(self):
        self.assertIn("TrafficYieldMeter.Record(", self.car)
        self.assertIn('PerfProbe.Measure("traffic")', self.car)
        self.assertIn("non policy di guida", self.meter)
        self.assertNotIn("currentSpeed = Mathf.Min", self.meter)

    def test_slot_probe_e_telemetria(self):
        self.assertIn("SlotCount = 10", self.probe)
        self.assertIn('"spawn", "roads", "traffic", "fictional"', self.probe)
        self.assertIn("telemetry.Mark(\"crosswalk:", self.meter)


class TestFictionalCityIntegration(unittest.TestCase):
    """Città Gioco affiancata: ricetta offline, flag, ponte e uovo."""

    def setUp(self):
        self.gen = read(os.path.join(UNITY, "City", "Scripts", "Game",
                                     "FictionalCityWorld.cs"))
        self.recipe = read(os.path.join(UNITY, "Resources", "CityRecipes",
                                        "foggia_city_recipe.json"))
        self.chunk = read(os.path.join(UNITY, "City", "Scripts", "OSM",
                                       "ChunkManager.cs"))
        self.world = read(os.path.join(UNITY, "City", "Scripts", "OSM",
                                       "CityChunkedWorld.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(ROOT, "unity-project", "Assets", "Scripts",
                                         "Core", "GameManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "HamburgerMenu.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.home = read(os.path.join(ROOT, "app", "src", "main", "java",
                                      "com", "intelligame", "huntix", "HomeActivity.kt"))

    def test_ricetta_solo_parametri(self):
        self.assertIn('"road_spacing_m"', self.recipe)
        self.assertIn('"poi_mix"', self.recipe)
        self.assertNotIn('"nodes"', self.recipe)
        self.assertNotIn('"ele"', self.recipe)
        self.assertIn("Resources/CityRecipes/foggia_city_recipe.json", self.gen)
        self.assertIn("System.Random", self.gen)

    def test_modalita_default_reale_e_streaming_bloccato(self):
        self.assertIn('PlayerPrefs.GetInt(PrefKey, 0)', self.gen)
        self.assertIn("FictionalCityWorld.Enabled) return", self.chunk)
        self.assertIn("Manager.StreamingEnabled = false", self.world)
        self.assertIn("if (!fictional && Manager != null", self.world)
        self.assertIn("FictionalCityWorld.DestroyWorld()", self.world)

    def test_uovo_e_bridge_bidirezionale(self):
        self.assertIn("OnFictionalCityEntered()", self.game)
        self.assertIn('GrantFeatureEgg("fictional-city"', self.game)
        self.assertIn('"Uovo della città di carta"', self.game)
        self.assertIn('"WorldModeChanged"', self.ipc)
        self.assertIn('"WorldModeChanged" -> handleWorldModeChanged(jsonData)', self.bridge)
        self.assertIn('putString("world_mode", mode)', self.bridge)
        self.assertIn('j.put("worldMode", "fictional")', self.activity)
        self.assertIn(r'\"worldMode\"', self.manager)
        self.assertIn("Mondo:", self.menu)

    def test_misura_del_build(self):
        self.assertIn('PerfProbe.Measure("fictional")', self.gen)
        self.assertIn("citta-gioco: edifici=%d poi=%d", self.gen)

    def test_nessun_preload_tile_su_android(self):
        self.assertIn('worldMode == "fictional"', self.home)
        self.assertIn('preload tile saltato', self.home)

    def test_suolo_allineato_allo_spawn(self):
        # Faccia superiore del suolo a y=0: strade e parchi devono restare
        # sopra, non sepolti dentro il collider (regressione 24/11).
        self.assertIn('Box("Suolo", new Vector3(0f, -0.25f, 0f)', self.gen)


class TestVehicleMaterials(unittest.TestCase):
    """Auto Kenney con colore ripristinato: atlas CC0, nessun asset dei clone."""

    def setUp(self):
        self.fix = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                     "VehicleMaterialFix.cs"))
        self.spawn = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                       "VehicleSpawnManager.cs"))
        self.traffic = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                         "TrafficCar.cs"))
        self.notice = read(os.path.join(ROOT, "THIRD_PARTY_NOTICES.md"))

    def test_materiale_condiviso_e_colormap_atlas(self):
        self.assertIn('Resources.Load<Texture2D>("Vehicles/Textures/colormap")', self.fix)
        self.assertIn('Shader.Find("Universal Render Pipeline/Lit")', self.fix)
        self.assertIn("r.sharedMaterial = mat", self.fix)
        self.assertIn("private static bool tried", self.fix)

    def test_percorso_chunked_applica_la_riparazione(self):
        self.assertIn("VehicleMaterialFix.Apply(go)", self.spawn)
        self.assertIn("VehicleMaterialFix.Apply(inst)", self.traffic)

    def test_attribuzione_cc0_e_asset_esclusi(self):
        self.assertIn("Kenney Car Kit", self.notice)
        self.assertIn("CC0 1.0", self.notice)
        self.assertIn("ConceptCar006.fbx", self.notice)
        self.assertNotIn("VehicleMaterialFix.Apply(prefabSketchfabKit)", self.spawn)


if __name__ == "__main__":
    unittest.main(verbosity=2)

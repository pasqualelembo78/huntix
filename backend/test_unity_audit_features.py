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


if __name__ == "__main__":
    unittest.main(verbosity=2)

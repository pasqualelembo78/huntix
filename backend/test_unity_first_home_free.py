"""Guardie statiche per la prima casa gratuita di MiCittà.

La prima casa è un beneficio una tantum per profilo: lo stato vive nei flag
globali già esistenti, quindi viaggia Unity → Android via CityGlobalActivity
e torna Android → Unity via setMode/snapshot CityStateSync. Le case successive
restano a pagamento; una casa gratuita venduta non genera rimborsi dal nulla.
Nessun sistema esistente viene rimosso o disattivato.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_first_home_free.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix")
UNITY = os.path.join(ROOT, "unity-project", "Assets")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestHomeSystemPrimaCasa(unittest.TestCase):
    def setUp(self):
        self.home = read(os.path.join(
            UNITY, "City", "Scripts", "Environment", "HomeSystem.cs"))

    def test_prezzo_e_beneficio_una_tantum(self):
        self.assertIn("public const int HomeCost = 900", self.home)
        self.assertIn('private const string KeyFree = "city_home_free"', self.home)
        self.assertIn("public static bool CurrentHomeIsFree", self.home)
        self.assertIn("public static string BuyLabel", self.home)
        self.assertIn('"PRIMA CASA GRATUITA"', self.home)

    def test_acquisto_salta_pagamento_solo_se_beneficio_disponibile(self):
        self.assertIn(
            "bool free = City.Diagnostics.CityGlobalAreasBridge.FirstHomeFreeAvailable;",
            self.home)
        self.assertIn("if (!free && !World.Wallet.TrySpend(HomeCost))", self.home)
        self.assertIn("PlayerPrefs.SetInt(KeyFree, free ? 1 : 0)", self.home)
        self.assertIn(
            "City.Diagnostics.CityGlobalAreasBridge.RecordFirstHomeFree(nm, doorPos)",
            self.home)
        self.assertIn('"Prima casa gratuita: " + nm', self.home)

    def test_vendita_gratuita_non_crea_denaro(self):
        self.assertIn("bool wasFree = CurrentHomeIsFree", self.home)
        self.assertIn("int refund = wasFree ? 0 : Mathf.RoundToInt(HomeCost * SellRefund)",
                      self.home)
        self.assertIn("if (refund > 0) World.Wallet.Earn(refund)", self.home)
        self.assertIn(
            "City.Diagnostics.CityGlobalAreasBridge.MarkFirstHomeFreeUsed()", self.home)
        self.assertIn("PlayerPrefs.DeleteKey(KeyFree)", self.home)
        self.assertIn('"vendita", "casa"', self.home)


class TestPonteGlobalEAuovo(unittest.TestCase):
    def setUp(self):
        self.bridge = read(os.path.join(
            UNITY, "City", "Scripts", "Diagnostics", "CityGlobalAreasBridge.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.menu = read(os.path.join(
            UNITY, "City", "Scripts", "UI", "ContextActionController.cs"))
        self.sync = read(os.path.join(
            UNITY, "City", "Scripts", "Sync", "CityWorldSync.cs"))

    def test_flag_globale_viaggia_nei_due_sensi(self):
        self.assertIn('KeyFirstHomeFree = "freehome"', self.bridge)
        self.assertIn("public static bool FirstHomeFreeAvailable", self.bridge)
        self.assertIn("public static void RecordFirstHomeFree", self.bridge)
        self.assertIn("public static void MarkFirstHomeFreeUsed", self.bridge)
        self.assertIn('result.Add(KeyFirstHomeFree + "="', self.bridge)
        self.assertIn("public static void RestoreFromAndroid", self.bridge)
        self.assertIn("PlayerPrefs.SetInt(Prefix + KeyFirstHomeFree, 1)", self.bridge)

    def test_coordinate_reali_e_uovo_indizio_unico(self):
        self.assertIn("public static void Record(string area, string action, string detail, string place,\n            Vector3 at)", self.bridge)
        self.assertIn("City.OSM.WorldOrigin.ToGeo(at)", self.bridge)
        self.assertIn('"città→casa→prima casa gratis→" + nm', self.bridge)
        self.assertIn("MarkEggAt(\"casa->prima-gratis\"", self.bridge)
        self.assertIn("public void GrantAuditEggAt(", self.game)
        self.assertIn('SendMessageToAndroid("FeatureEggHidden"', self.game)

    def test_ui_e_snapshot_restano_additivi(self):
        self.assertEqual(self.menu.count("City.Environment.HomeSystem.BuyLabel"), 2)
        self.assertIn('parts.Add(HomeSystem.CurrentHomeIsFree ? "1" : "0")', self.sync)
        self.assertIn('PlayerPrefs.SetInt("city_home_free", parts[6] == "1" ? 1 : 0)',
                      self.sync)


class TestSpecchioAndroid(unittest.TestCase):
    def setUp(self):
        self.global_areas = read(os.path.join(APP, "managers", "GlobalAreas.kt"))
        self.sync = read(os.path.join(APP, "bridge", "CityStateSync.kt"))
        self.mode = read(os.path.join(APP, "bridge", "BridgeActivity.java"))
        self.bridge = read(os.path.join(APP, "bridge", "Bridge.kt"))
        self.ipc = read(os.path.join(APP, "bridge", "UnityIpc.kt"))

    def test_flag_ricevuto_esportato_e_visibile(self):
        self.assertIn('private const val KEY_FREEHOME = "freehome"', self.global_areas)
        self.assertIn('"freehome", "prima casa", "prima casa gratis" -> KEY_FREEHOME',
                      self.global_areas)
        self.assertIn("KEY_BUY, KEY_SELL, KEY_FREEHOME", self.global_areas)
        self.assertIn('put("primaCasaGratis"', self.global_areas)
        self.assertIn("prima casa gratis:", self.global_areas)

    def test_bridge_bidirezionale_esistente_riutilizzato(self):
        self.assertIn('"CityGlobalActivity" -> handleCityGlobalActivity', self.bridge)
        self.assertIn("GlobalAreas.record(ctx, jsonData)", self.bridge)
        self.assertIn('"CityGlobalActivity"', self.ipc)
        self.assertIn("GlobalAreas.importFlags(", self.sync)
        self.assertIn('j.put("globalAreas"', self.mode)
        self.assertIn("GlobalAreas.INSTANCE.exportFlags(this)", self.mode)
        self.assertIn("EggWhereLog.recordActivity", self.global_areas)


if __name__ == "__main__":
    unittest.main(verbosity=2)

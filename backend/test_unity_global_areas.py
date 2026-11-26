"""Guardie statiche per aree globali e integrazione di MiCittà nel mondo Huntix.

Verifica il Mandato §13: Area Inventario e Area Abilità raggiungibili da Home,
menu globale, partita AR e menu Unity; eventi MiCittà nello stesso profilo
Android e nello stesso diario posizionale; ponte bidirezionale e riflesso uova.
Nessun sistema esistente viene rimosso o disattivato.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_global_areas.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix")
UNITY = os.path.join(ROOT, "unity-project", "Assets")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestMandatoMicittaParteDelTutto(unittest.TestCase):
    def setUp(self):
        self.mandato = read(os.path.join(ROOT, "HUNTIX_MANDATO.md"))

    def test_principio_scritto_nel_mandato(self):
        self.assertIn("## 13 · MiCittà è una parte del tutto", self.mandato)
        self.assertIn("non è un universo a sé", self.mandato)
        for parola in ("inventario", "abilità", "scoperte", "acquisti", "vendite"):
            self.assertIn(parola, self.mandato)
        self.assertIn("se un evento di MiCittà non è visibile dalla Home", self.mandato)

    def test_nessuna_rimozione_dei_sistemi_esistenti(self):
        self.assertIn("la conservazione del §7 e l'autonomia del §11 restano pieni",
                      self.mandato)


class TestAreeGlobaliAndroid(unittest.TestCase):
    def setUp(self):
        self.global_areas = read(os.path.join(APP, "managers", "GlobalAreas.kt"))
        self.home = read(os.path.join(APP, "HomeActivity.kt"))
        self.nav = read(os.path.join(APP, "BaseNavActivity.kt"))
        self.bridge = read(os.path.join(APP, "bridge", "Bridge.kt"))
        self.ipc = read(os.path.join(APP, "bridge", "UnityIpc.kt"))
        self.mode = read(os.path.join(APP, "bridge", "BridgeActivity.java"))
        self.sync = read(os.path.join(APP, "bridge", "CityStateSync.kt"))
        self.collection = read(os.path.join(APP, "EggCollectionActivity.kt"))
        self.ability = read(os.path.join(APP, "ui", "AbilityActivity.kt"))
        self.profile = read(os.path.join(APP, "ui", "PlayerProfileTabs.kt"))
        self.game_ui = read(os.path.join(APP, "MainActivityUi.kt"))

    def test_unico_punto_ingresso_con_contatori_condivisi(self):
        self.assertIn("object GlobalAreas", self.global_areas)
        self.assertIn("huntix_city_global_prefs", self.global_areas)
        for key in ("KEY_INV", "KEY_SKILL", "KEY_DISC", "KEY_BUY", "KEY_SELL"):
            self.assertIn(key, self.global_areas)
        self.assertIn("fun openInventory", self.global_areas)
        self.assertIn("fun openSkills", self.global_areas)
        self.assertIn("fun exportFlags", self.global_areas)
        self.assertIn("fun importFlags", self.global_areas)
        self.assertIn("EggWhereLog.recordActivity", self.global_areas)

    def test_home_e_menu_globale(self):
        self.assertIn("AREA INVENTARIO", self.home)
        self.assertIn("AREA ABILITÀ", self.home)
        self.assertIn("GlobalAreas.openInventory(this@HomeActivity)", self.home)
        self.assertIn("GlobalAreas.openSkills(this@HomeActivity)", self.home)
        self.assertIn("\\uD83D\\uDCE6 Area Inventario", self.nav)
        self.assertIn("Area Abilità", self.nav)
        self.assertIn("8 -> com.intelligame.huntix.managers.GlobalAreas.openInventory(this)", self.nav)
        self.assertIn("9 -> com.intelligame.huntix.managers.GlobalAreas.openSkills(this)", self.nav)

    def test_bridge_bidirezionale_e_whitelist(self):
        self.assertIn('"AreaInventarioOpen" -> handleGlobalAreaOpen', self.bridge)
        self.assertIn('"AreaAbilitaOpen" -> handleGlobalAreaOpen', self.bridge)
        self.assertIn('"CityGlobalActivity" -> handleCityGlobalActivity', self.bridge)
        self.assertIn("GlobalAreas.record(ctx, jsonData)", self.bridge)
        for event in ("AreaInventarioOpen", "AreaAbilitaOpen", "CityGlobalActivity"):
            self.assertIn('"' + event + '"', self.ipc)
        self.assertIn('j.put("globalAreas"', self.mode)
        self.assertIn("GlobalAreas.INSTANCE.exportFlags(this)", self.mode)
        self.assertIn("GlobalAreas.importFlags(", self.sync)

    def test_raggiungibile_da_tutta_l_app(self):
        self.assertIn("GlobalAreas.summary(c)", self.collection)
        self.assertIn("GlobalAreas.openSkills(c)", self.collection)
        self.assertIn("GlobalAreas.summary(this)", self.ability)
        self.assertIn("GlobalAreas.openInventory(this@AbilityActivity)", self.ability)
        self.assertIn("GlobalAreas.summary(this@buildStatsTab)", self.profile)
        self.assertIn("globalAreasRow", self.game_ui)
        self.assertIn("GlobalAreas.openInventory(this@openInGameMenu)", self.game_ui)
        self.assertIn("GlobalAreas.openSkills(this@openInGameMenu)", self.game_ui)

    def test_nessun_inventario_duplicato(self):
        # L'area globale riapre la collezione reale: nessun secondo archivio.
        self.assertIn("EggInventoryViewManager", self.collection)
        self.assertIn("GlobalAreas.openInventory(this@HomeActivity)", self.home)


class TestAreeGlobaliUnity(unittest.TestCase):
    def setUp(self):
        self.bridge = read(os.path.join(
            UNITY, "City", "Scripts", "Diagnostics", "CityGlobalAreasBridge.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.sync = read(os.path.join(UNITY, "City", "Scripts", "Sync", "CityWorldSync.cs"))
        self.buy = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))
        self.sell = read(os.path.join(UNITY, "City", "Scripts", "Vehicle", "VehicleInteract.cs"))
        self.dex = read(os.path.join(UNITY, "City", "Scripts", "Economy", "EggDex.cs"))

    def test_ponte_unity_notifica_e_ripristina(self):
        self.assertIn('EventInventory = "AreaInventarioOpen"', self.bridge)
        self.assertIn('EventSkills = "AreaAbilitaOpen"', self.bridge)
        self.assertIn('EventActivity = "CityGlobalActivity"', self.bridge)
        self.assertIn("public static void OpenInventory()", self.bridge)
        self.assertIn("public static void OpenSkills()", self.bridge)
        self.assertIn("public static void Record(", self.bridge)
        self.assertIn("public static void RestoreFromAndroid(", self.bridge)
        self.assertIn("UnityBridge.SendMessageToAndroid(eventName, json)", self.bridge)
        self.assertIn('lat', self.bridge)
        self.assertIn('lng', self.bridge)
        self.assertIn('place', self.bridge)

    def test_menu_unity_additivo(self):
        self.assertIn('MakeSubPanel("EggPanel", 620f, 590f)', self.menu)
        self.assertIn('new MenuEntry("📦 Area Inventario (globale)", -1', self.menu)
        self.assertIn('new MenuEntry("⚡ Area Abilità (globale)", -1', self.menu)
        self.assertEqual(self.menu.count('new MenuEntry('), 31)

    def test_ripristino_e_snapshot(self):
        self.assertIn('globalAreas', self.manager)
        self.assertIn("CityGlobalAreasBridge.RestoreFromAndroid", self.manager)
        self.assertIn("j.globalAreas = City.Diagnostics.CityGlobalAreasBridge.ExportFlags()", self.sync)
        self.assertIn("public string globalAreas", self.sync)

    def test_acquisti_vendite_e_scoperte_visibili(self):
        self.assertIn('CityGlobalAreasBridge.Record(\n                "acquisti"', self.buy)
        self.assertIn('CityGlobalAreasBridge.Record(\n                    "vendita"', self.sell)
        self.assertEqual(self.dex.count('CityGlobalAreasBridge.Record('), 2)
        self.assertIn("GrantAuditEgg", self.bridge)


if __name__ == "__main__":
    unittest.main(verbosity=2)

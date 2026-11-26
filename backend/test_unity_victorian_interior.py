"""Guardie statiche per la casa vittoriana "TARDIS" di MiCittà.

La casa resta minuscola nel mondo OSM; varcando la porta si entra in una tasca
enorme con salone, lampadari, biblioteca, sala da pranzo e piazzetta-città.
Il livello è ADDITIVO: interior generator/manager, decorator CC0/SENDU e
contenitori uova esistenti restano intatti. Ogni zona registrata nel diario
Android hardcoded con luogo, coordinate della porta e timestamp, e le chiavi
uovo/zone tornano a Unity nello snapshot HouseVisitState/ContainerEgg.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_victorian_interior.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix")
UNITY = os.path.join(ROOT, "unity-project", "Assets")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestTascaVittoriana(unittest.TestCase):
    def setUp(self):
        self.victorian = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Victorian",
            "VictorianGrandInterior.cs"))
        self.generator = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "InteriorGenerator.cs"))
        self.manager = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "InteriorManager.cs"))

    def test_tasca_sotto_il_guscio_e_dimensione_enorme(self):
        self.assertIn("private const float PocketDrop = 1000f", self.victorian)
        self.assertIn("new Vector3(164f, 1.2f, 114f)", self.victorian)
        self.assertIn("PavimentoVittoriano", self.victorian)
        self.assertIn("SoffittoVittoriano", self.victorian)
        self.assertIn("Colonna_", self.victorian)
        self.assertIn("CasaVittoriana_TARDIS", self.victorian)

    def test_salone_lampadari_biblioteca_e_citta_interna(self):
        for token in ("Salone dei lampadari", "LampadarioCerchio", "Candela_",
                      "BIBLIOTECA", "SALA DA PRANZO", "LA CITTÀ DENTRO CASA",
                      "Vetrina_", "Lampione_", "VascaFontana", "Piazzetta"):
            self.assertIn(token, self.victorian)

    def test_innesto_additivo_dopo_i_builder_esistenti(self):
        self.assertIn(
            "City.Interior.Victorian.VictorianGrandInterior.Decorate(parent, type,",
            self.generator)
        self.assertGreater(
            self.generator.index("VisitInteriorDecorator.Decorate"),
            self.generator.index("UniversalInteriorBuilder.Build"))
        self.assertGreater(
            self.generator.index("VictorianGrandInterior.Decorate"),
            self.generator.index("VisitInteriorDecorator.Decorate"))
        self.assertIn("private void BuildHouse(", self.generator)

    def test_ingresso_uscita_e_camera_non_rompono_il_flusso(self):
        self.assertIn("VictorianGrandInterior.TryEnter(this, e)", self.manager)
        self.assertIn("IsInsidePocket) return;", self.manager)
        self.assertIn("VictorianGrandInterior.IsInsidePocket)",
                      self.manager)
        self.assertIn("VictorianGrandInterior.ClearCamera()", self.manager)
        self.assertIn("VictorianGrandInterior.Tick()", self.manager)
        self.assertIn("ActiveRoot;", self.manager)


class TestRiflessoUovaESpecchioAndroid(unittest.TestCase):
    def setUp(self):
        self.victorian = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Victorian",
            "VictorianGrandInterior.cs"))
        self.bridge = read(os.path.join(
            UNITY, "City", "Scripts", "Diagnostics", "CityGlobalAreasBridge.cs"))
        self.sync = read(os.path.join(
            UNITY, "City", "Scripts", "Sync", "CityWorldSync.cs"))
        self.container = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "ContainerEgg.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.global_areas = read(os.path.join(APP, "managers", "GlobalAreas.kt"))
        self.ipc = read(os.path.join(APP, "bridge", "UnityIpc.kt"))

    def test_uova_nascoste_con_luogo_testuale(self):
        self.assertEqual(self.victorian.count("EggSpot(root,"), 5)
        self.assertIn("HouseEggSpot", self.victorian)
        self.assertIn("UniversalContainerMarker", self.victorian)
        self.assertIn('"città→casa→Casa Vittoriana→" + room + "→" + thing',
                      self.victorian)
        self.assertIn("root.GetComponentsInChildren<Visit.HouseEggSpot>(true)",
                      self.container)

    def test_zone_con_coordinate_e_dedup_nello_snapshot(self):
        self.assertEqual(self.victorian.count("Zone(root, p,"), 6)
        self.assertIn("CityGlobalAreasBridge.Record(\"scoperte\",", self.victorian)
        self.assertIn("HouseInteriorState.MarkEgg(featureId)", self.victorian)
        self.assertIn("pocket.doorWorld", self.victorian)
        self.assertIn("WorldOrigin.ToGeo(door)", self.victorian)
        self.assertIn("j.houseVisitState = City.Interior.Visit.HouseInteriorState.Export();",
                      self.sync)
        self.assertIn("j.foundContainerEggs = City.Interior.ContainerEgg.ExportFound();",
                      self.sync)

    def test_bridge_esistente_riutilizzato_senza_nuova_economia(self):
        self.assertIn("public static void Record(string area, string action,", self.bridge)
        self.assertIn('SendMessageToAndroid("FeatureEggHidden"', self.game)
        self.assertIn("MarkEggAt", self.bridge)
        self.assertIn('"CityGlobalActivity"', self.ipc)
        self.assertIn('"FeatureEggHidden"', self.ipc)
        self.assertIn("EggWhereLog.recordActivity", self.global_areas)
        self.assertNotIn("Wallet.Earn", self.victorian)
        self.assertNotIn("Wallet.TrySpend", self.victorian)


class TestPrefabVillaCompleta(unittest.TestCase):
    """La villa non è più solo primitive: è un prefab SENDU completo.

    Il fallback primitivo resta per sicurezza, ma quando il prefab è presente
    viene usato una sola volta: le uova non devono quindi duplicarsi.
    """

    def setUp(self):
        self.victorian = read(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Victorian",
            "VictorianGrandInterior.cs"))
        self.baker = read(os.path.join(
            UNITY, "City", "Editor", "VillaPrefabBaker.cs"))
        self.prefab = os.path.join(
            UNITY, "Resources", "Villas", "VillaVittorianaCompleta.prefab")
        self.prefab_text = read(self.prefab)

    @staticmethod
    def guid(meta_path):
        for line in read(meta_path).splitlines():
            if line.startswith("guid: "):
                return line.split(": ", 1)[1].strip()
        return ""

    def test_prefab_creato_e_caricato_a_runtime(self):
        self.assertTrue(os.path.isfile(self.prefab))
        self.assertIn("Villas/VillaVittorianaCompleta", self.victorian)
        self.assertIn("Resources.Load<GameObject>", self.victorian)
        self.assertIn("Instantiate(prefab", self.victorian)
        self.assertIn("BuildFallbackArchitecture", self.victorian)
        self.assertIn("FinalizePocket", self.victorian)
        self.assertIn("VillaVittorianaCompleta.prefab", self.baker)
        self.assertIn("LightAt(", self.baker)

    def test_prefab_contiene_collider_luci_e_cinque_uova(self):
        self.assertIn("!u!65", self.prefab_text)  # BoxCollider
        self.assertIn("!u!108", self.prefab_text)  # Light
        egg_guid = self.guid(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Visit",
            "HouseEggSpot.cs.meta"))
        self.assertTrue(egg_guid)
        self.assertGreaterEqual(self.prefab_text.count(egg_guid), 5)
        self.assertEqual(self.baker.count('Egg("'), 5)

    def test_marker_aggiunto_a_runtime_per_i_contenitori_del_prefab(self):
        # UniversalContainerMarker è una classe MonoBehaviour secondaria nello
        # stesso file del builder universale: Unity non la serializza nel
        # prefab (m_Script: 0). Il runtime la ricrea solo se manca, senza
        # toccare i contenitori delle case visitabili esistenti.
        self.assertIn("GetComponentsInChildren<HouseEggSpot>(true)", self.victorian)
        self.assertIn("GetComponent<UniversalContainerMarker>() != null",
                      self.victorian)
        self.assertIn("marker.room = spot.room", self.victorian)

    def test_uova_non_duplicate(self):
        # Il fallback conserva le cinque uova storiche; il prefab ne ha altre
        # cinque, ma i due rami sono mutually exclusive a runtime.
        self.assertEqual(self.victorian.count("EggSpot(root,"), 5)
        self.assertEqual(self.victorian.count("Zone(root, p,"), 6)
        self.assertIn("bool prefabUsed = prefab != null;", self.victorian)
        self.assertIn("BuildFallbackArchitecture", self.victorian)


if __name__ == "__main__":
    unittest.main(verbosity=2)

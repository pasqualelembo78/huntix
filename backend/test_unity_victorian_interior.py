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

    def test_tasca_non_e_sepolta_nell_interno_disattivato(self):
        # Con gli esterni prefab InteriorGenerator disattiva "_interiorRoot":
        # se la tasca restasse sua figlia, il teleporto arriverebbe nel vuoto
        # (l'utente vedeva ancora la casa piccola). La tasca è agganciata al
        # generatore e l'ingresso riattiva ogni antenato disattivato.
        self.assertIn("Transform anchor = gen != null ? gen.transform : parent;",
                      self.victorian)
        self.assertIn("go.transform.SetParent(anchor, true);", self.victorian)
        self.assertIn("for (Transform t = pocket.transform.parent; t != null; t = t.parent)",
                      self.victorian)
        self.assertIn("if (!t.gameObject.activeSelf) t.gameObject.SetActive(true);",
                      self.victorian)

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
        self.assertEqual(self.victorian.count("EggSpot(root,"), 10)
        self.assertIn("HouseEggSpot", self.victorian)
        self.assertIn("UniversalContainerMarker", self.victorian)
        self.assertIn('"città→casa→Casa Vittoriana→" + room + "→" + thing',
                      self.victorian)
        self.assertIn("root.GetComponentsInChildren<Visit.HouseEggSpot>(true)",
                      self.container)

    def test_zone_con_coordinate_e_dedup_nello_snapshot(self):
        self.assertEqual(self.victorian.count("Zone(root, p,"), 12)
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

    def test_villa_reggia_con_soffitto_intero_e_stanze_nuove(self):
        # La villa occupa tutta la tasca: pareti 12 m (≈7 × 1,7 m), soffitto
        # 164 × 114 m senza buchi, e le comodità mancanti nella build 3ea97…
        self.assertIn("Soffitto_\" + ix + \"_\" + iz", self.baker)
        self.assertIn("new Vector3(20f, 0.4f, 16f)", self.baker)
        self.assertIn("for (int ix = -4; ix <= 3; ix++)", self.baker)
        self.assertIn("for (int iz = -4; iz <= 3; iz++)", self.baker)
        self.assertIn("BuildFoyer(", self.baker)
        self.assertIn("BuildMasterBedroom(", self.baker)
        self.assertIn("BuildBathroom(", self.baker)
        self.assertIn("BuildKitchen(", self.baker)
        self.assertIn("BuildKidsAndStudy(", self.baker)
        for token in ("Bedroom/CI_Bedroom_Bed_Double",
                      "Bathroom/CI_Bathroom_Bathtub_Clawfoot",
                      "Kitchen/CI_Kitchen_RangeCooker",
                      "Kids/CI_Kids_BunkBed",
                      "Office/CI_Office_Desk_Writing"):
            self.assertIn(token, self.baker)

    def test_prefab_contiene_collider_luci_e_dieci_uova(self):
        self.assertIn("!u!65", self.prefab_text)  # BoxCollider
        self.assertIn("!u!108", self.prefab_text)  # Light
        egg_guid = self.guid(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Visit",
            "HouseEggSpot.cs.meta"))
        self.assertTrue(egg_guid)
        self.assertGreaterEqual(self.prefab_text.count(egg_guid), 10)
        self.assertEqual(self.baker.count('Egg("'), 10)

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
        # Il fallback conserva le dieci uova della villa; il prefab ne ha
        # altrettante, ma i due rami sono mutually exclusive a runtime.
        self.assertEqual(self.victorian.count("EggSpot(root,"), 10)
        self.assertEqual(self.victorian.count("Zone(root, p,"), 12)
        self.assertIn("bool prefabUsed = prefab != null;", self.victorian)
        self.assertIn("BuildFallbackArchitecture", self.victorian)


class TestVilla300MqTreLivelli(unittest.TestCase):
    """Nuova villa da 300 mq (3 x 100) per le case configurate.
    La reggia TARDIS storica resta intatta come fallback: qui si verificano
    solo le aggiunte, senza toccare le guardie sulle dieci uova vecchie."""

    def setUp(self):
        base = os.path.join(UNITY, "City")
        self.victorian = read(os.path.join(
            base, "Scripts", "Interior", "Victorian", "VictorianGrandInterior.cs"))
        self.stairs = read(os.path.join(
            base, "Scripts", "Interior", "Victorian", "GrandVillaStairs.cs"))
        self.exterior = read(os.path.join(
            base, "Scripts", "Interior", "Visit", "GrandHouseExterior.cs"))
        self.placer = read(os.path.join(base, "Scripts", "OSM", "BuildingPlacer.cs"))
        self.baker = read(os.path.join(base, "Editor", "VillaPrefabBaker.cs"))
        self.prefab = os.path.join(
            UNITY, "Resources", "Villas", "Villa300mqTreLivelli.prefab")
        self.prefab_text = read(self.prefab)

    @staticmethod
    def guid(meta_path):
        for line in read(meta_path).splitlines():
            if line.startswith("guid: "):
                return line.split(": ", 1)[1].strip()
        return ""

    def test_tre_livelli_da_cento_mq_ciascuno(self):
        for token in ("GrandFloors = 3", "GrandFloorHeight = 3.5f",
                      "GrandAreaPerFloor = 100f", "GrandTotalArea = 300f",
                      "pocket.grand = grand", "GrandVillaPrefabResource"):
            self.assertIn(token, self.victorian)
        for token in ("Piani300 = 3", "LatoPiano300 = 10f",
                      "AreaPiano300 = LatoPiano300 * LatoPiano300",
                      "AreaTotale300 = Piani300 * AreaPiano300",
                      "for (int f = 0; f < Piani300; f++)",
                      'Collider("Collider300_Pavimento"',
                      '"Pavimento300_" + f', '"Soffitto300_" + f'):
            self.assertIn(token, self.baker)
        self.assertTrue(os.path.isfile(self.prefab))
        self.assertIn("Villa300mqTreLivelli", self.prefab_text)
        self.assertIn("Pavimento300_0", self.prefab_text)
        self.assertIn("Pavimento300_1", self.prefab_text)
        self.assertIn("Pavimento300_2", self.prefab_text)

    def test_grandezza_visibile_da_fuori(self):
        for token in ("Floors = 3", "FloorHeight = 3.5f",
                      "TotalHeight = Floors * FloorHeight",
                      "AreaPerFloor = 100f", "TotalArea = Floors * AreaPerFloor",
                      '"FasciaPiano" + floor', '"FinestraPiano" + floor',
                      '"BalconePiano" + floor', "CornicioneVilla",
                      "ComignoloVilla", "PorticoIngressoVilla"):
            self.assertIn(token, self.exterior)
        for token in ("GrandVillaFloors",
                      "City.Interior.Visit.GrandHouseExterior.TotalHeight",
                      "if (grandVilla) h = Mathf.Max(h, GrandVillaHeight);",
                      "GrandHouseExterior.Dress(parent, centerLocal",
                      "villa300mq=",
                      "sy = Mathf.Clamp(GrandVillaHeight / Mathf.Max(baseB.size.y"):
            self.assertIn(token, self.placer)
        # L'impronta OSM e la porta del registro NON si spostano.
        self.assertIn("float w = Mathf.Clamp(Mathf.Max(b.d[0], 1.5f), 1.5f, 90f);",
                      self.placer)
        self.assertIn("TryDoorLocalOffset(\n                        visitSpec, b.r",
                      self.placer)
        self.assertIn("entrance.floorCount = SuggestedFloors(h);", self.placer)

    def test_prefab_con_nove_uova_e_quattro_scale(self):
        egg_guid = self.guid(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Visit", "HouseEggSpot.cs.meta"))
        stairs_guid = self.guid(os.path.join(
            UNITY, "City", "Scripts", "Interior", "Victorian",
            "GrandVillaStairs.cs.meta"))
        self.assertTrue(egg_guid)
        self.assertTrue(stairs_guid)
        self.assertGreaterEqual(self.prefab_text.count(egg_guid), 9)
        self.assertGreaterEqual(self.prefab_text.count(stairs_guid), 4)
        self.assertEqual(self.baker.count('Uovo300("'), 9)
        for token in ('"Scala300_Su" + floor', '"Scala300_Giu" + floor',
                      "stairs.targetFloor = floor + 1",
                      "stairs.targetFloor = floor - 1",
                      "private static void Uovo300("):
            self.assertIn(token, self.baker)
        for floor in ("piano1", "piano2", "piano3"):
            self.assertIn(floor, self.baker)

    def test_riflesso_uova_e_bridge_per_tutti_i_piani(self):
        self.assertEqual(self.victorian.count("AddZonaVilla(root, p,"), 9)
        self.assertIn("private static void AddZonaVilla(", self.victorian)
        self.assertIn('"città→casa→" + p.key + "→salone al piano 1"', self.victorian)
        self.assertIn("CityGlobalAreasBridge.Record(\"scoperte\",", self.victorian)
        self.assertIn("pocket.doorWorld", self.victorian)
        self.assertIn("FinalizeGrandVilla(root, p, label)", self.victorian)
        self.assertIn("GetComponentsInChildren<HouseEggSpot>(true)", self.victorian)
        self.assertIn("InteriorTeleport.Move(pocket.transform.TransformPoint(local)",
                      self.stairs)
        self.assertIn("UnityBridge.LogToAndroid", self.stairs)
        self.assertNotIn("Wallet.Earn", self.victorian)
        self.assertNotIn("Wallet.TrySpend", self.stairs)


if __name__ == "__main__":
    unittest.main(verbosity=2)

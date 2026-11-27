"""Guardie statiche per i gusci esterni dedicati di Via Silvio Pellico.

Intervento ADDITIVO: i sei edifici OSM configurati usano un prefab esterno
reale (Grab3D CC0 + vinish.dev CC BY 4.0 + Сountry House FREE dall'Unity Asset
Store) senza stiramento a 10,5 m e senza vestizione GrandHouseExterior.
L'interno da 300 mq, le uova e il bridge Android restano invariati. Nessun
asset proprietario di Brookhaven importato.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_house_assets.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITY = os.path.join(ROOT, "unity-project", "Assets")
SCRIPTS = os.path.join(UNITY, "City", "Scripts")
REGISTRY = os.path.join(SCRIPTS, "Interior", "Visit", "VisitInteriorRegistry.cs")
PLACER = os.path.join(SCRIPTS, "OSM", "BuildingPlacer.cs")
HOUSES = os.path.join(UNITY, "Resources", "Houses", "ViaSilvioPellico")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestCaseCaseEsterne(unittest.TestCase):
    def setUp(self):
        self.registry = read(REGISTRY)
        self.placer = read(PLACER)

    def test_registro_ha_sei_case_di_via_silvio_pellico(self):
        osm_ids = ["405219860L", "405228832L", "405228884L",
                   "405228771L", "405227079L", "405221437L"]
        for osm_id in osm_ids:
            self.assertIn("osmId = " + osm_id, self.registry,
                          "id OSM mancante dal registro: " + osm_id)

    def test_import_fbx_non_rimane_minuscolo(self):
        # I cinque FBX arrivano da GLB con scala 100x troppo piccola: con
        # `globalScale: 1` il tetto finiva all'altezza dei piedi del player.
        fbxs = ("grab3d_suburban_family_house", "vinish_family_house",
                "vinish_townhouses", "vinish_mediterranean_villa",
                "vinish_victorian_house")
        for name in fbxs:
            meta = read(os.path.join(HOUSES, name + ".fbx.meta"))
            first = next(line.strip() for line in meta.splitlines()
                         if line.strip().startswith("globalScale:"))
            self.assertEqual(first, "globalScale: 100",
                             "ImportFBX troppo piccolo: " + name)

    def test_ogni_casa_ha_un_guscio_dedicato(self):
        resources = [
            "Houses/ViaSilvioPellico/grab3d_suburban_family_house",
            "Houses/ViaSilvioPellico/vinish_family_house",
            "Houses/ViaSilvioPellico/vinish_townhouses",
            "Houses/ViaSilvioPellico/vinish_mediterranean_villa",
            "Houses/ViaSilvioPellico/vinish_victorian_house",
            "Houses/ViaSilvioPellico/AssetStore/CountryHouse_Prefab",
        ]
        for resource in resources:
            self.assertIn('exteriorPrefab = "' + resource + '"', self.registry)
            candidate = os.path.join(UNITY, "Resources", resource + ".fbx")
            if not os.path.isfile(candidate):
                candidate = os.path.join(UNITY, "Resources", resource + ".prefab")
            self.assertTrue(os.path.isfile(candidate), "Asset mancante: " + resource)

    def test_helper_espone_il_solo_path_resources(self):
        self.assertIn("public static bool TryGetExteriorPrefab(long osmId, out string resource)",
                      self.registry)
        self.assertIn("resource = spec.exteriorPrefab;", self.registry)
        self.assertIn("public string exteriorPrefab = \"\";", self.registry)

    def test_placer_non_stira_e_non_veste_gusci_dedicati(self):
        self.assertIn("Resources.Load<GameObject>(customExteriorResource)", self.placer)
        self.assertIn("bool customExterior = customPrefab != null;", self.placer)
        self.assertIn("if (grandVilla && !customExterior)", self.placer)
        self.assertGreater(self.placer.count("if (grandVilla && !customExterior)"), 1)
        # La vestizione fasce/finestre resta solo per il vecchio guscio generico.
        dress = self.placer.index("GrandHouseExterior.Dress(parent, centerLocal,")
        guard = self.placer.rindex("if (grandVilla && !customExterior)", 0, dress)
        self.assertLess(dress - guard, 400)
        # L'asset dedicato viene scalato in modo uniforme: nessuna facciata
        # stirata per riempire l'impronta OSM.
        self.assertIn("float uniformCustomScale = customExterior", self.placer)
        self.assertIn("sx = sz = uniformCustomScale;", self.placer)
        self.assertIn("? uniformCustomScale", self.placer)

    def test_licenze_originali_presenti_e_attribuzione_corretta(self):
        grab = read(os.path.join(HOUSES, "LICENSE_grab3d.txt"))
        vinish = read(os.path.join(HOUSES, "CREDITS_vinish.txt"))
        assetstore = read(os.path.join(
            HOUSES, "LICENSE_assetstore_country_house.txt"))
        global_licenses = read(os.path.join(UNITY, "ThirdPartyLicenses.md"))
        self.assertIn("CC0", grab.upper())
        self.assertIn("vinish.dev", vinish)
        self.assertIn("CC BY 4.0", global_licenses)
        self.assertIn("Grab3D", global_licenses)
        self.assertIn("Unity Asset Store", global_licenses)
        self.assertIn("Standard Unity Asset Store EULA", assetstore)
        self.assertIn("Objective Environment", assetstore)
        self.assertIn("Сountry House FREE", global_licenses)

    def test_riflesso_uova_e_interno_non_toccati(self):
        # Nessuna nuova economia: i gusci sono solo visuali e il registro non
        # dichiara nuovi tipi di premio.
        self.assertNotIn("eggReward", self.registry)
        self.assertNotIn("newHuntixCurrency", self.registry)
        self.assertIn("legacyEggIds", self.registry)


if __name__ == "__main__":
    unittest.main()

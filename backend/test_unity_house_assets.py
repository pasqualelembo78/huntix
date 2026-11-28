"""Guardie statiche per le case visitabili di Via Silvio Pellico.

Intervento ADDITIVO (28/11/2026): i cinque FBX CC0/CC BY sono stati rimossi
su richiesta del proprietario. Restano sei edifici OSM visitabili con uova e
interno da 300 mq: una casa «eroe» reale (Сountry House FREE, Unity Asset
Store, Standard EULA) e cinque gusci procedurali condivisi. Nessun asset
proprietario di Brookhaven importato, nessuna nuova economia uova.

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
HERO = "Houses/ViaSilvioPellico/AssetStore/CountryHouse_Prefab"


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

    def test_solo_una_casa_eroe_ha_guscio_dedicato(self):
        dedicated = [line for line in self.registry.splitlines()
                     if 'exteriorPrefab = "Houses/' in line]
        self.assertEqual(len(dedicated), 1,
                         "Deve restare un solo guscio eroe dedicato")
        self.assertIn('exteriorPrefab = "' + HERO + '"', self.registry)
        prefab = os.path.join(UNITY, "Resources", HERO + ".prefab")
        self.assertTrue(os.path.isfile(prefab), "Prefab eroe mancante: " + HERO)
        removed = ("grab3d_suburban_family_house", "vinish_family_house",
                   "vinish_townhouses", "vinish_mediterranean_villa",
                   "vinish_victorian_house")
        for name in removed:
            self.assertNotIn("Houses/ViaSilvioPellico/" + name, self.registry)
            self.assertFalse(os.path.exists(os.path.join(HOUSES, name + ".fbx")),
                             "FBX rimosso ancora presente: " + name)

    def test_helper_espone_il_solo_path_resources(self):
        self.assertIn("public static bool TryGetExteriorPrefab(long osmId, out string resource)",
                      self.registry)
        self.assertIn("resource = spec.exteriorPrefab;", self.registry)
        self.assertIn("public string exteriorPrefab = \"\";", self.registry)

    def test_placer_non_stira_e_non_veste_gusci_dedicati(self):
        # Il vecchio stiramento a 10,5 m e la vestizione fasce/finestre restano
        # nel codice ma sono spenti per decisione del proprietario.
        self.assertIn("public const bool LegacyVillaStretchDress = false;", self.placer)
        self.assertIn("bool legacyVillaStretchDress = grandVilla && LegacyVillaStretchDress;",
                      self.placer)
        self.assertNotIn("if (grandVilla && !customExterior)", self.placer)
        self.assertGreaterEqual(self.placer.count("if (legacyVillaStretchDress && !customExterior)"), 2)
        # Guscio eroe e guscio procedurale condividono la stessa scala uniforme:
        # nessuna facciata stirata per riempire l'impronta OSM.
        self.assertIn("bool uniformShell = customExterior || grandVilla;", self.placer)
        self.assertIn("sx = sz = uniformCustomScale;", self.placer)
        self.assertIn("? uniformCustomScale", self.placer)

    def test_edifici_senza_porta_non_vengono_istanziati(self):
        # Richiesta esplicita del proprietario (28/11/2026): rimuovere le case
        # non visitabili, cioe' quelle senza porta/entrata. Il vecchio guscio
        # muto resta nel codice ma non viene piu' creato quando il budget del
        # chunk e' esaurito.
        self.assertIn("public const bool RemoveBuildingsWithoutEntrance = true;",
                      self.placer)
        self.assertIn("if (RemoveBuildingsWithoutEntrance &&", self.placer)
        self.assertIn("senza porta/entrata: non istanziato", self.placer)
        self.assertIn("public static void ResetChunkBudget()", self.placer)
        self.assertIn("MaxEnterablePerChunk = 8", self.placer)

    def test_licenze_originali_presenti_e_attribuzione_corretta(self):
        assetstore = read(os.path.join(
            HOUSES, "LICENSE_assetstore_country_house.txt"))
        global_licenses = read(os.path.join(UNITY, "ThirdPartyLicenses.md"))
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

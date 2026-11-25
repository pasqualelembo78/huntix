#!/usr/bin/env python3
"""Verifiche statiche delle case visitabili collegate a edifici OSM reali.

Il sistema è ADDITIVO: registry OSM, prefab con slot, decorator CC0, uova nei
nascondigli, innesti su ingresso/uscita e bridge bidirezionale con Android.
Nessun test dichiara un test visivo o su device non eseguito.
"""
import glob
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


REGISTRY = read("unity-project/Assets/City/Scripts/Interior/Visit/VisitInteriorRegistry.cs")
STATE = read("unity-project/Assets/City/Scripts/Interior/Visit/HouseInteriorState.cs")
SLOTS = read("unity-project/Assets/City/Scripts/Interior/Visit/HouseSlot.cs")
EGG_SPOT = read("unity-project/Assets/City/Scripts/Interior/Visit/HouseEggSpot.cs")
DECORATOR = read("unity-project/Assets/City/Scripts/Interior/Visit/VisitInteriorDecorator.cs")
MANAGER = read("unity-project/Assets/City/Scripts/Interior/Visit/VisitInteriorManager.cs")
GENERATOR = read("unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs")
ENTRANCE = read("unity-project/Assets/City/Scripts/Interior/BuildingEntrance.cs")
PLACER = read("unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs")
CHUNKS = read("unity-project/Assets/City/Scripts/OSM/ChunkBuilder.cs")
INTERIOR_MANAGER = read("unity-project/Assets/City/Scripts/Interior/InteriorManager.cs")
CONTAINER = read("unity-project/Assets/City/Scripts/Interior/ContainerEgg.cs")
GAME = read("unity-project/Assets/City/Scripts/Game.cs")
SYNC = read("unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
ANDROID_SYNC = read("app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt")
SENDU_DECORATOR = read("unity-project/Assets/City/Scripts/Interior/Visit/SenduInteriorDecorator.cs")
PREFAB_SETUP = read("unity-project/Assets/Editor/HousePrefabSetup.cs")
HUNTIX_BUILD = read("unity-project/Assets/Editor/HuntixBuild.cs")
IMPORT_SCRIPT = read("scripts/house_assets_import.py")
SENDU_IMPORT_SCRIPT = read("scripts/sendu_assets_import.py")
LICENSES = read("unity-project/Assets/ThirdPartyLicenses.md")
NOTICES = read("THIRD_PARTY_NOTICES.md")


class TestAssetCC0(unittest.TestCase):
    def test_modelli_reali_e_ripetibili(self):
        self.assertIn("https://3dassets.dev/packs/cozy-home-interiors", IMPORT_SCRIPT)
        self.assertIn("CC0 1.0", IMPORT_SCRIPT)
        folder = os.path.join(ROOT, "unity-project/Assets/Resources/HouseInteriors/CozyHome")
        fbx = sorted(glob.glob(os.path.join(folder, "*.fbx")))
        self.assertEqual(len(fbx), 43)
        for path in fbx:
            self.assertTrue(os.path.isfile(path + ".meta"), path)
        names = {os.path.basename(p) for p in fbx}
        for expected in ("SofaThreeSeat.fbx", "DiningTable.fbx", "DoubleBed.fbx",
                         "Bath.fbx", "Wardrobe.fbx", "DrawerBase.fbx", "BasinUnit.fbx"):
            self.assertIn(expected, names)
        self.assertEqual(glob.glob(os.path.join(folder, "*.png")), [])


class TestRegistroECasa(unittest.TestCase):
    def test_edificio_osm_associato(self):
        self.assertIn("204023010L", REGISTRY)
        self.assertIn("Casa di Roma", REGISTRY)
        self.assertIn('"casa-osm-"', REGISTRY)
        self.assertIn("TryGet(long osmId, out Spec spec)", REGISTRY)
        self.assertIn("UniversalInteriorRegistry.IsKnown(kind)", REGISTRY)
        self.assertIn("public long osmId;", ENTRANCE)

    def test_layout_quattro_stanze(self):
        for room in ("soggiorno", "cucina", "camera", "bagno"):
            self.assertIn('"' + room + '"', SLOTS)
        for model in ("SofaThreeSeat", "DiningTable", "DoubleBed", "Bath",
                      "Fridge", "Wardrobe", "BasinUnit"):
            self.assertIn(model, SLOTS)
        self.assertGreaterEqual(SLOTS.count("Add(s,"), 25)
        for feature in ("casa-cucina-cassetto2", "casa-camera-armadio",
                        "casa-bagno-lavandino"):
            self.assertIn(feature, SLOTS)
        for place in ("casa→cucina→cassetto2", "casa→camera→armadio",
                      "casa→bagno→lavandino"):
            self.assertIn(place, SLOTS)

    def test_decorator_additivo_collider_solo_paratie(self):
        self.assertIn("VisitInteriorRegistry.PrefabResource", DECORATOR)
        self.assertIn("Resources.Load<GameObject>", DECORATOR)
        self.assertIn("Universal Render Pipeline/Lit", DECORATOR)
        self.assertIn("FindChild(floor, RootName) != null", DECORATOR)
        self.assertIn("GetComponentsInChildren<Collider>(true)", DECORATOR)
        self.assertIn("go.layer = 8", DECORATOR)  # BuildingLayer: escluso da GroundSnapper
        self.assertIn("BuildPartitions(root.transform, W, D)", DECORATOR)
        self.assertIn("RemoveVisualDividers(floor)", DECORATOR)
        self.assertIn("UniversalInteriorBuilder.FindMarker(floor)", DECORATOR)
        self.assertIn("gap = 1.1f", DECORATOR)
        self.assertNotIn("UnityWebRequest", DECORATOR)

    def test_prefab_generato_in_build(self):
        self.assertIn("CasaVisitabileCC0.prefab", PREFAB_SETUP)
        self.assertIn("HouseSlots.DefaultLayout()", PREFAB_SETUP)
        self.assertIn("PrefabUtility.SaveAsPrefabAsset", PREFAB_SETUP)
        self.assertIn("HousePrefabSetup.EnsurePrefab();", HUNTIX_BUILD)
        base = "unity-project/Assets/"
        for rel in (
            "City/Scripts/Interior/Visit.meta",
            "Resources/HouseInteriors.meta",
            "Resources/HouseInteriors/CozyHome.meta",
            "City/Scripts/Interior/Visit/HouseSlot.cs.meta",
            "Editor/HousePrefabSetup.cs.meta",
        ):
            self.assertTrue(os.path.isfile(os.path.join(ROOT, base + rel)), rel)

    def test_conservazione_sistemi_esistenti(self):
        self.assertIn("ShopInteriorBuilder.Decorate(parent, type, shop", GENERATOR)
        self.assertIn("ShopPrefabInstaller.Install(parent, type, shop", GENERATOR)
        self.assertIn("UniversalInteriorBuilder.Build(parent, type, shop", GENERATOR)
        self.assertIn("MaxEnterablePerChunk = 8", PLACER)
        self.assertIn("public const int CacheVersion = 5;", TILE_CLIENT)


class TestPlacementOSM(unittest.TestCase):
    def test_id_configurato_non_tagliato(self):
        self.assertIn("public bool priority;", CHUNKS)
        self.assertIn("VisitInteriorRegistry.IsConfigured(b.id)", CHUNKS)
        self.assertIn("if (!priority && poiSpots != null", CHUNKS)
        self.assertIn("if (a.priority != b.priority) return a.priority ? -1 : 1;", CHUNKS)
        self.assertIn("if (!visitConfigured && enterableBudget <= 0) return null;", PLACER)
        self.assertIn("entrance.osmId = b.id;", PLACER)
        self.assertIn("entrance.buildingName = visitSpec.label;", PLACER)
        self.assertIn("if (!visitConfigured) enterableBudget--;", PLACER)
        self.assertIn("gen.SetOsmId(b.id);", PLACER)

    def test_decorator_innestato_dopo_builder(self):
        self.assertIn("VisitInteriorDecorator.Decorate(parent, visitId", GENERATOR)
        self.assertIn("Entrance.osmId != 0L", GENERATOR)
        self.assertIn("public void SetOsmId(long id)", GENERATOR)

    def test_ingresso_uscita_e_prompt(self):
        self.assertIn("VisitInteriorManager.OnEntered(e);", INTERIOR_MANAGER)
        self.assertEqual(INTERIOR_MANAGER.count("VisitInteriorManager.OnExited(e);"), 2)
        self.assertIn('"ENTRA IN CASA"', GAME)
        self.assertIn(".IsConfigured(entrance.osmId)", GAME)
        self.assertIn("InteriorTeleport.Move(pos, rot, g);", ENTRANCE)
        self.assertIn("public static void OnEntered(BuildingEntrance entrance)", MANAGER)
        self.assertIn("public static void OnExited(BuildingEntrance entrance)", MANAGER)


class TestRiflessoUova(unittest.TestCase):
    def test_tre_nascondigli_reali(self):
        self.assertIn("Visit.HouseEggSpot[] houseSpots", CONTAINER)
        self.assertIn("BeginHouseEggs(houseSpots)", CONTAINER)
        self.assertIn("Egg_Casa_", CONTAINER)
        self.assertIn("egg.containerEggKey = spot.featureId;", CONTAINER)
        self.assertIn("egg.placeHint", CONTAINER)
        self.assertIn("EggType.Edificio", CONTAINER)
        self.assertIn("Visit.HouseInteriorState.HasEgg(spot.featureId)", CONTAINER)
        self.assertIn("Visit.VisitInteriorManager.OnEggCollected(egg.containerEggKey);", CONTAINER)
        self.assertIn('featureId.StartsWith("casa-"', MANAGER)
        self.assertIn("public Vector3 EggPosition", EGG_SPOT)
        self.assertIn('"casa→" + spot.room', CONTAINER)

    def test_stato_persistente(self):
        self.assertIn('BridgeEvent = "HouseVisitChanged"', STATE)
        self.assertIn("public static string Export()", STATE)
        self.assertIn("public static void Import(string json)", STATE)
        self.assertIn("HouseInteriorState.MarkEgg(featureId)", MANAGER)
        self.assertIn("UnityBridge.SendMessageToAndroid(BridgeEvent", STATE)
        self.assertIn("r.visits++;", STATE)


class TestKitSENDU(unittest.TestCase):
    """Kit completo SENDU importato davvero (FBX + PNG), non solo CC0."""

    def test_assets_reali_e_meta(self):
        folder = os.path.join(ROOT, "unity-project/Assets/Resources/HouseInteriors/Sendu")
        fbx = sorted(glob.glob(os.path.join(folder, "FBX", "*", "*.fbx")))
        png = sorted(glob.glob(os.path.join(folder, "*", "*.png")))
        self.assertEqual(len(fbx), 339)
        self.assertEqual(len(png), 20)
        for path in fbx + png:
            self.assertTrue(os.path.isfile(path + ".meta"), path)
        self.assertTrue(os.path.isfile(os.path.join(folder, "README_models.txt")))
        self.assertIn("https://sendudev.itch.io/cozy-interiors", SENDU_IMPORT_SCRIPT)

    def test_texture_palette_senza_mipmap(self):
        meta = read("unity-project/Assets/Resources/HouseInteriors/Sendu/Palettes/CI_Theme_Honey.png.meta")
        self.assertIn("enableMipMap: 0", meta)
        self.assertIn("filterMode: 0", meta)  # Point: celle piatte, non sfumate
        self.assertIn("alphaIsTransparency: 0", meta)  # alpha = smoothness

    def test_decorator_additivo_e_quarto_uovo(self):
        self.assertIn("SenduInteriorDecorator.Decorate(root.transform, W, D);", DECORATOR)
        self.assertIn("EndsWith(\"_LOD1\", StringComparison.Ordinal)", SENDU_DECORATOR)
        self.assertIn("CI_Kitchen_Base_Drawer_0p6m", SENDU_DECORATOR)
        self.assertIn("casa-cucina-cassetto-sendu", SENDU_DECORATOR)
        self.assertIn("casa→cucina→cassetto-sendu", SENDU_DECORATOR)
        self.assertIn("UnityEngine.Object.Destroy(cols[i])", SENDU_DECORATOR)
        self.assertIn("Universal Render Pipeline/Lit", SENDU_DECORATOR)
        self.assertIn("CI_Theme_Honey", SENDU_DECORATOR)
        self.assertIn("s.x * W", SENDU_DECORATOR)  # coordinate sull'impronta OSM
        self.assertIn("s.z * D", SENDU_DECORATOR)
        self.assertIn("rends[i].gameObject.layer = 8", SENDU_DECORATOR)
        self.assertEqual(SENDU_DECORATOR.count("new Slot("), 32)
        slots = re.findall(r'new Slot\("([^"]+)", "([^"]+)"', SENDU_DECORATOR)
        for folder, model in slots:
            fbx = os.path.join(ROOT, "unity-project/Assets/Resources/HouseInteriors/Sendu",
                               "FBX", folder, model + ".fbx")
            self.assertTrue(os.path.isfile(fbx), fbx)

    def test_licenza_commerciale_documentata(self):
        text = LICENSES + NOTICES
        self.assertIn("sendudev.itch.io/cozy-interiors", text)
        self.assertIn("non CC0", text)
        self.assertIn("rivendita", text)
        self.assertIn("Unity 6000.6", text)


class TestBridgeBidirezionale(unittest.TestCase):
    def test_unity_versus_android(self):
        self.assertIn('"HouseVisitChanged"', IPC)
        self.assertIn('"HouseVisitChanged" ->', BRIDGE)
        self.assertIn("CityStateSync.onHouseVisit(ctx, jsonData)", BRIDGE)
        self.assertIn('KEY_HOUSE_VISITS = "city_house_visits_v1"', ANDROID_SYNC)
        self.assertIn("fun onHouseVisit(context: Context, json: String)", ANDROID_SYNC)
        self.assertIn("fun getHouseVisits(context: Context): String", ANDROID_SYNC)
        self.assertIn("private fun mergeHouseSnapshot(context: Context, json: String)",
                      ANDROID_SYNC)
        self.assertIn('j.optString("houseVisitState", "")', ANDROID_SYNC)
        self.assertIn('report.put("houseEggs"', ANDROID_SYNC)
        self.assertIn("j.houseVisitState = City.Interior.Visit.HouseInteriorState.Export();", SYNC)
        self.assertIn("City.Interior.Visit.HouseInteriorState.Import(j.houseVisitState);", SYNC)
        self.assertIn("public string houseVisitState = \"\";", SYNC)


if __name__ == "__main__":
    unittest.main()

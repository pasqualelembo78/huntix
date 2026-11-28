#!/usr/bin/env python3
"""Verifiche statiche delle case visitabili collegate a edifici OSM reali.

Il sistema è ADDITIVO: registry OSM, prefab con slot, decorator CC0, uova nei
nascondigli, innesti su ingresso/uscita e bridge bidirezionale con Android.
Nessun test dichiara un test visivo o su device non eseguito.
"""
import glob
import math
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
VEHICLE_POI = read("unity-project/Assets/City/Scripts/Vehicle/VehiclePoiRegistry.cs")
COMPASS = read("unity-project/Assets/City/Scripts/Vehicle/CompassUI.cs")
POI_SIGNPOST = read("unity-project/Assets/City/Scripts/Vehicle/PoiSignpost.cs")
MAP_SELECT = read("unity-project/Assets/City/Scripts/OSM/MapSelectUI.cs")
MINIMAP = read("unity-project/Assets/City/Scripts/OSM/MinimapHud.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
CITY_MAP_ACTIVITY = read("app/src/main/java/com/intelligame/huntix/ui/CityMapActivity.kt")
METRO = read("unity-project/Assets/City/Scripts/Vehicle/MetroRampRegistry.cs")
BEACON = read("unity-project/Assets/City/Scripts/Interior/Visit/HouseBeacon.cs")
POI_PLACER = read("unity-project/Assets/City/Scripts/Vehicle/VehiclePoiPlacer.cs")
POI_ZONE = read("unity-project/Assets/City/Scripts/Vehicle/VehiclePoiZone.cs")
UNDERGROUND = read("unity-project/Assets/City/Scripts/Vehicle/UndergroundUI.cs")


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

    def test_porta_sul_lato_indicato_dal_registro(self):
        # Il QA 26/11 ha trovato la casa illuminata senza porta visibile:
        # il segnaposto veniva creato solo col lazy dell'interno e il portale
        # stava a meta' muro. Ora lato E posizione lungo il muro vengono dal
        # registro; i valori qui sotto sono verificati sulle geo IT_082_087 e
        # IT_087_061 (dims OSM 26.5x13.7 e 21.0x13.6).
        self.assertIn("TryDoorLocalOffset(Spec spec, float yawDeg", REGISTRY)
        self.assertIn("spec.centerLat", REGISTRY)
        self.assertIn("TryDoorLocalOffset(\n                        visitSpec, b.r", PLACER)
        self.assertIn("doorX = baseB.center.x + Mathf.Clamp(lx, -marginX, marginX);", PLACER)
        self.assertIn("doorZ = baseB.center.z + Mathf.Clamp(lz, -marginZ, marginZ);", PLACER)
        self.assertIn("doorYaw", PLACER)
        self.assertIn("trig.transform.localRotation = Quaternion.Euler(0f, doorYaw, 0f);",
                      PLACER)
        # Le case a ingresso naturale non mostrano il portale dorato (il
        # segnaposto veniva scambiato per un pulsante): resta solo sugli altri
        # edifici configurati.
        self.assertIn("entrance.forceDoorMarker = visitConfigured && haveSpec "
                      "&& !naturalHouse;", PLACER)
        self.assertIn("public bool forceDoorMarker;", ENTRANCE)
        self.assertIn("DoorFramePart(\"StipiteSx\"", ENTRANCE)
        self.assertIn("Entrance.transform.position", GENERATOR)
        self.assertIn("gen.PrefabEnterWorld", ENTRANCE)
        self.assertIn("gen.PrefabExitWorld", INTERIOR_MANAGER)

        def door(label, yaw, dims):
            block = REGISTRY.split('label = "' + label + '"', 1)[1].split(
                "new Spec", 1)[0]
            def num(key):
                return float(re.search(r"\b" + key + r" = ([0-9.]+)", block).group(1))
            lat, lng = num("lat"), num("lng")
            clat, clon = num("centerLat"), num("centerLng")
            east = (lng - clon) * 111320.0 * math.cos(math.radians(
                (lat + clat) * 0.5))
            north = (lat - clat) * 110540.0
            rad = math.radians(yaw)
            c, s = math.cos(rad), math.sin(rad)
            x, z = east * c - north * s, east * s + north * c
            half_x, half_z = dims[0] * 0.5, dims[1] * 0.5
            # Faccia piu' vicina: conta la distanza in frazione
            # di semilato, non il valore assoluto in metri.
            if abs(x) / half_x >= abs(z) / half_z:
                side = "+X" if x >= 0 else "-X"
                along = min(max(z, -half_z + 0.6), half_z - 0.6)
            else:
                side = "+Z" if z >= 0 else "-Z"
                along = min(max(x, -half_x + 0.6), half_x - 0.6)
            return side, along, (x, z)

        foggia = door("Casa di Foggia", 311, (26.5, 13.7))
        roma = door("Casa di Roma", 317, (21.0, 13.6))
        self.assertEqual(foggia[0], "+Z")
        self.assertLess(foggia[1], -6.0)  # vicino all'angolo ovest, non a meta' muro
        self.assertEqual(roma[0], "+Z")
        self.assertAlmostEqual(roma[1], 0.0, delta=1.0)

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
        self.assertIn("SenduInteriorDecorator.Decorate(root.transform, W, D, osmId);",
                      DECORATOR)
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


class TestAccessoDeterministico(unittest.TestCase):
    """La casa e le rampe devono essere raggiungibili senza girare a vuoto."""

    def test_porta_registrata_come_destinazione(self):
        self.assertIn("public double lat;", REGISTRY)
        self.assertIn("public double lng;", REGISTRY)
        self.assertIn("lat = 41.906056", REGISTRY)
        self.assertIn("lng = 12.495734", REGISTRY)
        self.assertIn("centerLat = 41.90601", REGISTRY)
        self.assertIn("public static string TargetsJson()", REGISTRY)
        self.assertIn("\\\"lat\\\":", REGISTRY)
        self.assertIn("kind = spec.kind ?? \"casa\"", VEHICLE_POI)
        self.assertIn("foreach (var spec in VisitInteriorRegistry.All)", VEHICLE_POI)
        self.assertIn("AllOf(string kind)", VEHICLE_POI)
        self.assertIn("foreach (var p in All())", VEHICLE_POI)

    def test_mappa_espansa_e_minimappa(self):
        self.assertIn('{ "casa", "stadio", "ippodromo"', MAP_SELECT)
        self.assertIn('case "casa": return "CASA";', POI_SIGNPOST)
        self.assertIn("ColCasa = new Color(1.00f, 0.50f, 0.16f)", COMPASS)
        self.assertIn('case "casa": return ColCasa;', COMPASS)
        self.assertIn('DrawPoiKind("casa", _showCasa, CompassUI.ColCasa', MINIMAP)
        self.assertIn('MakeToggle(mapImg, "A", CompassUI.ColCasa', MINIMAP)

    def test_ponte_android_destinazione_casa(self):
        self.assertIn("j.houseTargets = City.Interior.Visit.VisitInteriorRegistry.TargetsJson();",
                      SYNC)
        self.assertIn('public string houseTargets = "";', SYNC)
        self.assertIn('case "navigateToHouse":', GAME_MANAGER)
        self.assertIn('HandleNavigateToHouse(jsonData);', GAME_MANAGER)
        self.assertIn('NavigationState.Set(label, "casa", lat, lng);', GAME_MANAGER)
        self.assertIn("fun getHouseTargets(context: Context): JSONArray", ANDROID_SYNC)
        # Unity esporta houseTargets come stringa JsonUtility: il ponte Android
        # deve accettare sia l'array sia il testo che contiene l'array.
        self.assertIn("private fun snapshotArray(context: Context, key: String): JSONArray",
                      ANDROID_SYNC)
        self.assertIn('snap.optJSONArray(key)?.let { return it }', ANDROID_SYNC)
        self.assertIn('val cleaned = strayNumberQuote.replace(raw, "$1")', ANDROID_SYNC)
        self.assertIn('return try { JSONArray(cleaned) }', ANDROID_SYNC)
        self.assertIn('getHouseTargets(context: Context): JSONArray = snapshotArray(context, "houseTargets")',
                      ANDROID_SYNC)
        self.assertNotIn('optJSONArray("houseTargets")', ANDROID_SYNC)
        self.assertIn('snapshotArray(context, "houseTargets").length() > 0', ANDROID_SYNC)
        self.assertIn("fun goToHouse(context: Context, osmId: Long = 405228771L): Boolean",
                      ANDROID_SYNC)
        self.assertIn('"navigateToHouse", payload.toString()', ANDROID_SYNC)
        self.assertIn('report.put("houseTargets"', ANDROID_SYNC)
        self.assertIn("CityStateSync.goToHouse(this@CityMapActivity)", CITY_MAP_ACTIVITY)

    def test_rampa_resta_poi_di_navigazione(self):
        self.assertIn('case "rampa": return "SOTTERRANEO";', POI_SIGNPOST)
        self.assertIn('"hospital", "rampa", "repair"', MAP_SELECT)
        self.assertIn('DrawPoiKind("rampa", _showRamp', MINIMAP)


class TestCasaFoggiaELuovoMetro(unittest.TestCase):
    """Copia della casa a Foggia + rampe metro vicine, senza toccare Roma.
    Nessun test dichiara comportamento visivo su device non verificato."""

    def test_casa_foggia_copia_non_spostamento(self):
        self.assertIn("405228771L", REGISTRY)
        self.assertIn("Casa di Foggia", REGISTRY)
        self.assertIn("Via Silvio Pellico 53", REGISTRY)
        self.assertIn("lat = 41.4530015", REGISTRY)
        self.assertIn("lng = 15.5344424", REGISTRY)
        self.assertIn("204023010L", REGISTRY)  # Roma intatta
        self.assertIn("legacyEggIds = true", REGISTRY)
        self.assertIn("public const long DefaultOsmId = 405228771L;", REGISTRY)

    def test_uova_per_casa_senza_duplicati(self):
        self.assertIn("EggId(long osmId, string baseId)", REGISTRY)
        self.assertIn("spot.featureId = VisitInteriorRegistry.EggId(osmId,",
                      DECORATOR)
        self.assertIn("spot.featureId = VisitInteriorRegistry.EggId(osmId, s.featureId);",
                      SENDU_DECORATOR)
        self.assertIn("string key = VisitInteriorRegistry.StableKey(osmId);",
                      SENDU_DECORATOR)

    def test_faro_come_uovo(self):
        self.assertIn("HouseBeacon.Attach(parent,", PLACER)
        self.assertIn("class HouseBeacon : MonoBehaviour", BEACON)
        self.assertIn('private const string RootName = "CasaBeacon";', BEACON)
        self.assertIn('new GameObject(RootName + "_"', BEACON)
        self.assertIn("LightType.Point", BEACON)
        self.assertIn("Mathf.Sin(Time.unscaledTime", BEACON)
        self.assertIn('m.EnableKeyword("_EMISSION")', BEACON)
        self.assertIn("Destroy(", BEACON)

    def test_rampe_metro_foggia(self):
        self.assertIn("foggia-metro-pellico", METRO)
        self.assertIn("foggia-metro-biondi", METRO)
        self.assertIn("foggia-metro-turati", METRO)
        self.assertIn("lat = 41.4530955", METRO)
        self.assertIn("lng = 15.5341185", METRO)
        self.assertIn("FindByName(string name)", METRO)
        self.assertIn("MetroRampRegistry.All", POI_PLACER)
        self.assertIn("MetroRampRegistry.NumericId(i)", POI_PLACER)
        self.assertIn("MetroRampRegistry.NumericId(i).ToString(", VEHICLE_POI)
        self.assertIn("MetroRampRegistry.IsMetroName(zone.poiName)", VEHICLE_POI)
        self.assertIn("METROPOLITANA - ", POI_ZONE)
        self.assertIn('"METROPOLITANA"', UNDERGROUND)
        self.assertNotIn("poi_overrides.json", METRO)

    def test_uovo_metro_una_volta_sola(self):
        self.assertIn('"huntix_metro_egg_" + ramp.id', UNDERGROUND)
        self.assertIn("PlayerPrefs.GetInt(prefKey, 0) != 0", UNDERGROUND)
        self.assertIn("PlayerPrefs.SetInt(prefKey, 1)", UNDERGROUND)
        self.assertIn("GrantAuditEgg(\"metro-foggia-\" + ramp.id", UNDERGROUND)
        self.assertIn("Foggia→metropolitana→", UNDERGROUND)
        self.assertIn("MetroRampRegistry.CoordinateLabel(ramp)", UNDERGROUND)

    def test_default_casa_foggia_roma_selezionabile(self):
        self.assertIn("DefaultOsmId", GAME_MANAGER)
        self.assertIn("osmId: Long = 405228771L", ANDROID_SYNC)
        self.assertIn("204023010L", ANDROID_SYNC)
        self.assertIn("Casa di Foggia", CITY_MAP_ACTIVITY)


class TestVilla300MqDaFuori(unittest.TestCase):
    """Le case configurate sono visibilmente grandi da fuori: tre piani,
    stessa impronta OSM e stessa porta (coordinate Android invariate)."""

    EXTERIOR = read("unity-project/Assets/City/Scripts/Interior/Visit/GrandHouseExterior.cs")

    def test_altezza_tre_piani_senza_spostare_impronta_e_porta(self):
        self.assertIn("public const int GrandVillaFloors =", PLACER)
        self.assertIn("public const float GrandVillaHeight =", PLACER)
        # Dal 28/11/2026 stiramento e vestizione sono solo codice legacy spento:
        # la casa visitabile resta grande da fuori grazie al guscio eroe quando
        # presente, altrimenti al guscio procedurale condiviso senza deformazioni.
        self.assertIn("public const bool LegacyVillaStretchDress = false;", PLACER)
        self.assertIn("bool legacyVillaStretchDress = grandVilla && LegacyVillaStretchDress;", PLACER)
        self.assertIn("if (legacyVillaStretchDress && !customExterior) h = Mathf.Max(h, GrandVillaHeight);", PLACER)
        self.assertIn("GrandHouseExterior.Dress(parent, centerLocal", PLACER)
        self.assertIn("float w = Mathf.Clamp(Mathf.Max(b.d[0], 1.5f), 1.5f, 90f);",
                      PLACER)
        self.assertIn("float d = Mathf.Clamp(Mathf.Max(b.d[1], 1.5f), 1.5f, 90f);",
                      PLACER)
        self.assertIn("TryDoorLocalOffset(\n                        visitSpec, b.r", PLACER)
        self.assertIn("entrance.floorCount = SuggestedFloors(h);", PLACER)
        self.assertIn("Floors = 3", self.EXTERIOR)
        self.assertIn("FloorHeight = 3.5f", self.EXTERIOR)
        self.assertIn("TotalHeight = Floors * FloorHeight", self.EXTERIOR)

    def test_prefab_villa_300_mq_generato_dal_baker(self):
        base = os.path.join(ROOT, "unity-project", "Assets")
        prefab = os.path.join(base, "Resources", "Villas",
                              "Villa300mqTreLivelli.prefab")
        self.assertTrue(os.path.isfile(prefab))
        self.assertTrue(os.path.isfile(prefab + ".meta"))
        self.assertTrue(os.path.isfile(os.path.join(
            base, "City", "Scripts", "Interior", "Victorian",
            "GrandVillaStairs.cs.meta")))


if __name__ == "__main__":
    unittest.main()

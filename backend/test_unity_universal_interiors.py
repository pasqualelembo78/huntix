#!/usr/bin/env python3
"""Verifiche statiche degli interni universali 3D (2026-11-25).

Controlla che il sistema sia ADDITIVO: builder/gestioni esistenti conservati,
nessun cambio geo/graph o fisica, varchi stile Gateways adattati ai chunk,
contenitori uova con chiave geografica e snapshot Android bidirezionale.
"""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


REGISTRY = read("unity-project/Assets/City/Scripts/Interior/UniversalInteriorRegistry.cs")
BUILDER = read("unity-project/Assets/City/Scripts/Interior/UniversalInteriorBuilder.cs")
GATE = read("unity-project/Assets/City/Scripts/Interior/InteriorGate.cs")
STATE = read("unity-project/Assets/City/Scripts/Interior/UniversalInteriorState.cs")
GENERATOR = read("unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs")
MANAGER = read("unity-project/Assets/City/Scripts/Interior/InteriorManager.cs")
ENTRANCE = read("unity-project/Assets/City/Scripts/Interior/BuildingEntrance.cs")
EGGS = read("unity-project/Assets/City/Scripts/Interior/ContainerEgg.cs")
SHOPS = read("unity-project/Assets/City/Scripts/Interior/ShopInteriorBuilder.cs")
STORE = read("unity-project/Assets/Scripts/Indoor/StoreBuilder.cs")
PLACER = read("unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs")
SYNC = read("unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
ANDROID_SYNC = read("app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt")


class TestCatalogo(unittest.TestCase):
    def test_diciannove_tipologie_universali(self):
        for kind in ("casa", "appartamento", "negozio", "supermercato", "bar",
                     "ristorante", "abbigliamento", "concessionaria", "farmacia",
                     "ufficio", "parruccheria", "lavaggiocani", "autolavaggio",
                     "biglietteria", "officina", "banca", "scuola", "ospedale",
                     "hotel"):
            self.assertIn('"%s"' % kind, REGISTRY, kind)

    def test_classificazione_riutilizza_negozi(self):
        self.assertIn("ShopInteriorBuilder.Classify(t, shop)", REGISTRY)
        self.assertIn('ContainsAny(n, "farmacia", "pharmacy", "drugstore")', REGISTRY)
        self.assertIn('ContainsAny(n, "ristorante", "trattoria", "pizzeria", "pizza"', REGISTRY)
        self.assertIn('case "hospital": return "ospedale"', REGISTRY)

    def test_nomi_e_indizi_in_italiano(self):
        self.assertIn('case "farmacia": return "Farmacia"', REGISTRY)
        self.assertIn('case "ufficio": return "Ufficio"', REGISTRY)
        self.assertIn('Uovo nel cassetto del banco farmacia.', REGISTRY)

    def test_chiave_geografica_stabile(self):
        self.assertIn("WorldOrigin.Initialized", REGISTRY)
        self.assertIn("WorldOrigin.ToGeo(worldPos)", REGISTRY)
        self.assertIn("CultureInfo.InvariantCulture", REGISTRY)
        self.assertIn('"interno@"', REGISTRY)

    def test_id_varco_deterministico(self):
        self.assertIn('public static string GateId(string interiorKey, string gateName)', REGISTRY)
        self.assertIn('"#varco-"', REGISTRY)


class TestBuilderAdditivo(unittest.TestCase):
    def test_builder_esistenti_conservati(self):
        for method in ("BuildShop(", "BuildBar(", "BuildDealer(",
                       "BuildWorkshop(", "BuildHouse("):
            self.assertIn(method, GENERATOR, method)
        self.assertIn("ShopInteriorBuilder.Decorate(parent, type, shop", GENERATOR)

    def test_innesto_unico_a_fine_costruzione(self):
        self.assertIn("UniversalInteriorBuilder.Build(parent, type, shop", GENERATOR)
        self.assertIn('FindChild(floor, "InternoUniversale") != null', BUILDER)
        self.assertIn('new GameObject("InternoUniversale")', BUILDER)

    def test_budget_e_lazy_invariati(self):
        self.assertIn("private const int MaxEnterablePerChunk = 8", PLACER)
        self.assertIn("private const float LAZY_RADIUS = 30f", MANAGER)
        self.assertIn("CacheVersion = 5", TILE_CLIENT)

    def test_asset_cc0_gia_presenti(self):
        assets = (
            "unity-project/Assets/Resources/Furniture/cabinetBedDrawer.fbx",
            "unity-project/Assets/Resources/Furniture/kitchenCoffeeMachine.fbx",
            "unity-project/Assets/Resources/Furniture/loungeSofa.fbx",
            "unity-project/Assets/Resources/Furniture/bookcaseClosed.fbx",
            "unity-project/Assets/Resources/Furniture/cardboardBoxClosed.fbx",
            "unity-project/Assets/Resources/KenneyMiniMarket/shelf-boxes.fbx",
            "unity-project/Assets/Resources/KenneyMiniMarket/cash-register.fbx",
            "unity-project/Assets/Resources/Vehicles/sedan.fbx",
            "unity-project/Assets/Resources/Pets/animal-dog.fbx",
            "assets/furniture/License.txt",
            "assets/car-kit/License.txt",
        )
        for path in assets:
            self.assertTrue(os.path.isfile(os.path.join(ROOT, path)), path)

    def test_fallback_senza_collider_sui_mobili(self):
        self.assertIn("Resources.Load<GameObject>(resourcePath)", BUILDER)
        self.assertIn("GameObject.CreatePrimitive(PrimitiveType.Cube)", BUILDER)
        self.assertIn("UnityEngine.Object.Destroy(col)", BUILDER)
        self.assertNotIn("Rigidbody", BUILDER)
        self.assertNotIn("NavMeshAgent", BUILDER)
        self.assertNotIn("CharacterController", BUILDER)
        self.assertNotIn("Mixamo", BUILDER)
        self.assertNotIn("AssetStore", BUILDER)

    def test_percorso_credenza_corretto(self):
        self.assertIn('Prop(root, "Furniture/cabinetBedDrawer", "CredenzaNegozio"', SHOPS)
        self.assertNotIn('"Furniture/credenza"', SHOPS)

    def test_percorso_cute_store_corretto(self):
        self.assertIn('Resources.Load<GameObject>("CuteStore/Prefabs/" + name)', STORE)
        self.assertNotIn('Resources.Load<GameObject>("CuteStore/" + name)', STORE)


class TestVarchiStileGateways(unittest.TestCase):
    def test_gateways_adattato_senza_cambi_scena(self):
        self.assertIn("nmacadam/Gateways", GATE)
        self.assertIn("UniversalInteriorRegistry.GateId(key", BUILDER)
        self.assertIn("target.targetGateId = gateId", GATE)
        self.assertNotIn("SceneManager.LoadScene", GATE)
        self.assertNotIn("DontDestroyOnLoad", GATE)

    def test_registro_con_pulizia_su_destroy(self):
        self.assertIn("private static readonly Dictionary<string, InteriorGate> Gates", GATE)
        self.assertIn("InteriorGateRegistry.Register(this)", GATE)
        # AddComponent chiama Awake prima di Configure: senza questa riga
        # le porte create a runtime non entrerebbero mai nel registro.
        self.assertTrue(re.search(
            r'label = labelText \?\? "VARCO";.*?InteriorGateRegistry\.Register\(this\);',
            GATE, re.S), "Configure deve registrare la porta con l'id definitivo")
        self.assertIn("InteriorGateRegistry.Unregister(this)", GATE)
        self.assertIn("if (Gates.TryGetValue(gate.gateId, out current) && current == gate)", GATE)

    def test_trigger_e_anti_loop(self):
        self.assertIn("col.isTrigger = true", BUILDER)
        self.assertIn("other.CompareTag(\"Player\")", GATE)
        self.assertIn("_cooldownUntil = Time.time + 2f", GATE)
        self.assertIn('"VARCO INGRESSO"', BUILDER)
        self.assertIn('"VARCO RETRO"', BUILDER)

    def test_teleporto_unico_con_guard_nan(self):
        self.assertIn("public static bool Move(Vector3 pos, Quaternion rot, Game g)", GATE)
        self.assertIn("WorldOrigin.IsFinite(pos)", GATE)
        self.assertIn("cc.enabled = false", GATE)
        self.assertIn("cc.enabled = true", GATE)
        self.assertIn("InteriorTeleport.Move(", ENTRANCE)
        self.assertIn("InteriorTeleport.Move(", MANAGER)
        self.assertNotIn("private static void SetPlayerPos", ENTRANCE)
        self.assertNotIn("private static void SetPlayerPos", MANAGER)


class TestUova(unittest.TestCase):
    def test_contenitori_universali_riconosciuti(self):
        for name in ("Cassetto", "Armadio", "Dispensa", "Scrivania"):
            self.assertIn(name, BUILDER, name)
        for name in ("cassetto", "armadio", "dispensa", "scrivania"):
            self.assertIn('"%s"' % name, EGGS, name)

    def test_indizio_stanza_dal_marker(self):
        self.assertIn("container.GetComponent<UniversalContainerMarker>()", EGGS)
        self.assertIn('public string room = ""', BUILDER)
        self.assertIn('"città→" + name + "→" + room + "→" + thing', EGGS)

    def test_rarita_unica_non_duplicata(self):
        self.assertNotIn("Legendary", STATE)
        self.assertNotIn("Rarity", STATE)
        self.assertIn("ContainerEgg", EGGS)

    def test_visite_registrate_all_ingresso(self):
        self.assertIn("UniversalInteriorBuilder.FindMarker(interiorRoot)", MANAGER)
        self.assertIn("UniversalInteriorState.MarkVisited(marker.key, marker.kind)", MANAGER)
        self.assertIn("public const string BridgeEvent = \"UniversalInteriorVisited\"", STATE)


class TestPonteAndroid(unittest.TestCase):
    def test_snapshot_unico(self):
        self.assertIn("j.universalInteriors = City.Interior.UniversalInteriorState.Export()", SYNC)
        self.assertIn('public string universalInteriors = ""', SYNC)
        self.assertIn("City.Interior.UniversalInteriorState.Import(j.universalInteriors)", SYNC)
        self.assertIn("public static string Export()", STATE)
        self.assertIn("public static void Import(string json)", STATE)

    def test_evento_whitelist_e_router(self):
        self.assertIn('"UniversalInteriorVisited"', IPC)
        self.assertIn('"UniversalInteriorVisited" ->', BRIDGE)
        self.assertIn("CityStateSync.onUniversalInteriorVisit(ctx, jsonData)", BRIDGE)

    def test_mirror_android_e_check(self):
        self.assertIn('KEY_UNIVERSAL_INTERIORS = "city_universal_interiors_v1"', ANDROID_SYNC)
        self.assertIn("fun onUniversalInteriorVisit(context: Context, json: String)", ANDROID_SYNC)
        self.assertIn('j.optString("universalInteriors", "")', ANDROID_SYNC)
        self.assertIn('report.put("universalInteriors"', ANDROID_SYNC)
        self.assertIn('report.put("universalInteriorsSnapshot"', ANDROID_SYNC)

    def test_merge_non_distruttivo(self):
        self.assertIn("if (visits > mirror.optInt(key, 0))", ANDROID_SYNC)
        self.assertIn("if (r.visits > local.visits)", STATE)


if __name__ == "__main__":
    unittest.main()

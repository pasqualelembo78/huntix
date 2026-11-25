#!/usr/bin/env python3
"""Verifiche statiche dei negozi walk-in 3D (7 attività reali).

Controlla che l'intervento sia ADDITIVO: builder esistenti conservati, nuovi
layout innestati, cassiera animata con asset CC0 già importati, uova con chiave
stabile, stato tycoon sincronizzato con Android e fisica/collider invariati.
"""
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


BUILDER = read("unity-project/Assets/City/Scripts/Interior/ShopInteriorBuilder.cs")
STATE = read("unity-project/Assets/City/Scripts/Economy/ShopState.cs")
GENERATOR = read("unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs")
MANAGER = read("unity-project/Assets/City/Scripts/Interior/InteriorManager.cs")
PLACER = read("unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs")
SYNC = read("unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs")
EGGS = read("unity-project/Assets/City/Scripts/Interior/ContainerEgg.cs")
IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
ANDROID_SYNC = read("app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")


class TestShopKinds(unittest.TestCase):
    def test_sette_tipologie_reali(self):
        for kind in ("supermercato", "bar", "abbigliamento", "concessionaria",
                     "parruccheria", "lavaggiocani", "biglietteria", "autolavaggio"):
            self.assertIn('"%s"' % kind, BUILDER, kind)

    def test_un_layout_dedicato_per_tipologia(self):
        for method in ("LayoutSupermarket", "LayoutBar", "LayoutClothes",
                       "LayoutAuto", "LayoutHairdresser", "LayoutDogWash",
                       "LayoutTicket", "LayoutCarWash"):
            self.assertIn("private static void %s" % method, BUILDER, method)
            self.assertIn("%s(root.transform, w, d)" % method, BUILDER, method)

    def test_decorate_idempotente(self):
        self.assertIn('FindChild(floor, "Negozio3D") != null', BUILDER)
        self.assertIn('new GameObject("Negozio3D")', BUILDER)

    def test_solo_edifici_commerciali(self):
        self.assertIn("if (!commercial) return;", BUILDER)
        for kind in ("shop", "bar", "dealer", "clothes", "ticket"):
            self.assertIn('t == "%s"' % kind, BUILDER, kind)


class TestIntegrazioneAdditiva(unittest.TestCase):
    def test_builder_esistenti_conservati(self):
        for method in ("BuildShop(", "BuildBar(", "BuildDealer(",
                       "BuildWorkshop(", "BuildHouse("):
            self.assertIn(method, GENERATOR, method)

    def test_nuovi_tipi_innestati(self):
        self.assertIn("ShopInteriorBuilder.Decorate(parent, type, shop", GENERATOR)
        for kind in ("clothes", "hairdresser", "dogwash", "carwash", "ticket"):
            self.assertIn('case "%s":' % kind, GENERATOR, kind)

    def test_mapping_osm_e_bancone(self):
        for kind in ("clothes", "hairdresser", "pet_grooming", "car_wash", "ticket"):
            self.assertIn('"%s"' % kind, PLACER, kind)
        self.assertIn('iType == "clothes" || iType == "hairdresser"', PLACER)
        self.assertIn('iType == "dogwash" || iType == "carwash" || iType == "ticket"', PLACER)

    def test_tick_solo_dentro(self):
        self.assertIn("ShopInteriorBuilder.TickActive(ActiveInteriorRoot", MANAGER)
        self.assertIn("if (!IsInside) return;", MANAGER)


class TestCassieraEAttivita(unittest.TestCase):
    def test_cassiera_usa_modello_cc0_animato(self):
        self.assertIn('Resources.Load<AnimationClip>("Characters/idle")', BUILDER)
        self.assertIn('Resources.Load<GameObject>("Characters/characterMedium")', BUILDER)
        self.assertIn('anim.AddClip(idle, "idle")', BUILDER)
        self.assertIn("WrapMode.Loop", BUILDER)

    def test_clienti_senza_fisica(self):
        self.assertIn("public sealed class ShopCustomer", BUILDER)
        self.assertNotIn("AddComponent<UnityEngine.AI.NavMeshAgent>", BUILDER)

    def test_collider_rimossi_dai_mobili(self):
        self.assertIn("UnityEngine.Object.Destroy(col)", BUILDER)
        self.assertNotIn("AddComponent<BoxCollider>()", BUILDER)
        self.assertIn("solo i volumi trigger", BUILDER)

    def test_contatori_tycoon(self):
        for token in ("served", "revenue", "level", "stock", "RegisterCustomer",
                      "CustomerInterval", "StatusLine"):
            self.assertIn(token, STATE, token)


class TestUovaEBridge(unittest.TestCase):
    def test_contenitori_riconosciuti_da_containeregg(self):
        for name in ("CassettoCassa", "Dispensa", "Credenza",
                     "CassettoBiglietteria", "DispensaCani", "DispensaAutolavaggio"):
            self.assertIn(name, BUILDER, name)
        self.assertIn('"cassetto"', EGGS)
        self.assertIn('"dispensa"', EGGS)
        self.assertIn('"credenza"', EGGS)
        self.assertIn("EggHint(", STATE)
        self.assertIn("negozio@", BUILDER)

    def test_snapshot_unico_con_android(self):
        self.assertIn('j.shopStates = City.Economy.ShopState.Export()', SYNC)
        self.assertIn('public string shopStates = ""', SYNC)
        self.assertIn("City.Economy.ShopState.Import(j.shopStates)", SYNC)
        self.assertIn('public static string Export()', STATE)
        self.assertIn('public static void Import(string json)', STATE)

    def test_evento_android_whitelist_e_mirror(self):
        self.assertIn('"ShopActivity"', IPC)
        self.assertIn('"ShopActivity" ->', BRIDGE)
        self.assertIn("CityStateSync.onShopActivity(ctx, jsonData)", BRIDGE)
        self.assertIn("fun onShopActivity(context: Context, json: String)", ANDROID_SYNC)
        self.assertIn('j.optString("shopStates", "")', ANDROID_SYNC)
        self.assertIn('report.put("shopStates"', ANDROID_SYNC)

    def test_chiave_stabile_geografica(self):
        self.assertIn("WorldOrigin.Initialized", BUILDER)
        self.assertIn("WorldOrigin.ToGeo(parent.position)", BUILDER)
        self.assertIn("CultureInfo.InvariantCulture", BUILDER)

    def test_rarita_unica_non_duplicata(self):
        self.assertNotIn("Legendary", STATE)
        self.assertNotIn("EggRarity", STATE)


class TestAssetEFisica(unittest.TestCase):
    def test_asset_cc0_gia_presenti(self):
        assets = (
            "unity-project/Assets/Resources/KenneyMiniMarket/shelf-bags.fbx",
            "unity-project/Assets/Resources/KenneyMiniMarket/freezer.fbx",
            "unity-project/Assets/Resources/KenneyMiniMarket/cash-register.fbx",
            "unity-project/Assets/Resources/Furniture/coatRackStanding.fbx",
            "unity-project/Assets/Resources/Furniture/kitchenCoffeeMachine.fbx",
            "unity-project/Assets/Resources/Vehicles/sedan.fbx",
            "unity-project/Assets/Resources/Pets/animal-dog.fbx",
            "unity-project/Assets/Resources/Characters/characterMedium.fbx",
            "unity-project/Assets/Resources/Characters/idle.fbx",
            "unity-project/Assets/ThirdParty/CuteSupermarketLite/Resources/"
            "CuteStore/Prefabs/apple_green.prefab",
        )
        for path in assets:
            self.assertTrue(os.path.isfile(os.path.join(ROOT, path)), path)

    def test_nessuna_dipendenza_proprietaria(self):
        for text in (BUILDER, STATE):
            self.assertNotIn("Mixamo", text)
            self.assertNotIn("AssetStore", text)
            self.assertNotIn("unitypackage", text)

    def test_fisica_e_cache_invariate(self):
        self.assertNotIn("Rigidbody", BUILDER)
        self.assertNotIn("CharacterController", BUILDER)
        self.assertIn("CacheVersion = 5", TILE_CLIENT)


if __name__ == "__main__":
    unittest.main()

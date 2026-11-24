"""Verifica statica delle integrazioni dell'audit Unity 23/11/2026.

Questi test NON sostituiscono il collaudo su dispositivo: controllano che i
collegamenti fra UI, logica di gioco, veicolo, uova e bridge Android siano
effettivamente presenti nei sorgenti che entrano nella build.

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_audit_features.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITY = os.path.join(ROOT, "unity-project", "Assets")
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix", "bridge")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestHandbrakeIntegration(unittest.TestCase):
    def setUp(self):
        self.vehicle = read(os.path.join(UNITY, "City", "Scripts", "Vehicle", "VehicleController.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))

    def test_vehicle_espone_set_handbrake_e_derapata(self):
        self.assertIn("public void SetHandbrake(bool on)", self.vehicle)
        self.assertIn("handbrakeInput", self.vehicle)
        self.assertIn("HandbrakeBrakeFactor", self.vehicle)
        self.assertIn("HandbrakeYawBoost", self.vehicle)
        self.assertIn("HandbrakeSlideFactor", self.vehicle)

    def test_nessun_wheelcollider_aggiunto(self):
        # Il modulo guida esistente e' Rigidbody + velocita' scalare: la
        # derapata non deve introdurre un sistema fisico nuovo sul mobile.
        # Il nome puo' comparire solo nei commenti, mai come tipo usato.
        self.assertNotIn("new WheelCollider", self.vehicle)
        self.assertNotIn("AddComponent<WheelCollider>", self.vehicle)
        self.assertNotIn("GetComponent<WheelCollider>", self.vehicle)
        self.assertNotIn("WheelCollider[]", self.vehicle)

    def test_pulsante_touch_tieni_premuto(self):
        self.assertIn("FRENO\\nA MANO", self.ui)
        self.assertIn("EventTriggerType.PointerDown", self.ui)
        self.assertIn("EventTriggerType.PointerUp", self.ui)
        self.assertIn("Game.Instance.SetHandbrake(true)", self.ui)
        self.assertIn("Game.Instance.SetHandbrake(false)", self.ui)

    def test_game_collega_freno_a_veicolo_e_uovo(self):
        self.assertIn("public void SetHandbrake(bool on)", self.game)
        self.assertIn("CurrentVehicle.SetHandbrake(on)", self.game)
        self.assertIn('GrantFeatureEgg("handbrake"', self.game)


class TestQualityProfileIntegration(unittest.TestCase):
    def setUp(self):
        self.selector = read(
            os.path.join(UNITY, "City", "Scripts", "Diagnostics", "QualityProfileSelector.cs")
        )
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))

    def test_livelli_unity_esistenti(self):
        # Non vanno aggiunti livelli ne' cambiate dipendenze: si usano i 6
        # QualitySettings gia' definiti nel progetto.
        self.assertIn("{ 1, 2, 3 }", self.selector)
        self.assertIn("QualitySettings.SetQualityLevel", self.selector)

    def test_nessuna_forzatura_all_avvio(self):
        self.assertIn("applyStoredOnStart", self.selector)
        self.assertIn("if (applyStoredOnStart) Apply(Current, false);", self.selector)

    def test_menu_e_riflesso_uova(self):
        self.assertIn("Qualità grafica", self.menu)
        self.assertIn("ToggleQuality()", self.menu)
        self.assertIn("OnQualityProfileApplied", self.game)


class TestEggReflection(unittest.TestCase):
    def test_spawn_reward_egg_usando_eggc_controller(self):
        src = read(os.path.join(UNITY, "City", "Scripts", "Economy", "EggSpawnManager.cs"))
        self.assertIn("public GameObject SpawnRewardEgg(", src)
        self.assertIn("AddComponent<EggController>()", src)
        self.assertIn("MAX_EGGS", src)


class TestAndroidBridge(unittest.TestCase):
    def setUp(self):
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))

    def test_eventi_whitelistati(self):
        self.assertIn('"FeatureEggHidden"', self.ipc)
        self.assertIn('"CityQualityChanged"', self.ipc)

    def test_android_riceve_e_persiste(self):
        self.assertIn('"FeatureEggHidden" -> handleFeatureEggHidden(jsonData)', self.bridge)
        self.assertIn('"CityQualityChanged" -> handleCityQualityChanged(jsonData)', self.bridge)
        self.assertIn('putString("city_quality", profile)', self.bridge)

    def test_android_restituisce_la_qualita_a_unity(self):
        self.assertIn('j.put("cityQuality", quality)', self.activity)
        self.assertIn(r'\"cityQuality\"', self.manager)
        self.assertIn("QualityProfileSelector.Ensure().Apply(p, false)", self.manager)


class TestAlternateTouchIntegration(unittest.TestCase):
    """Joystick alternativo Zone Zero (MIT): affiancato, non sostitutivo."""

    def setUp(self):
        self.touch = read(os.path.join(UNITY, "City", "Scripts", "UI", "AlternateTouchInput.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))

    def test_attribuzione_mit_presente(self):
        self.assertIn("Adattato da Zone Zero", self.touch)
        self.assertIn("Mishal Bhasim", self.touch)

    def test_sorgente_alternativa_e_opzionale(self):
        self.assertIn("public static bool Enabled", self.touch)
        self.assertIn("SetEnabled(bool on, bool notify)", self.touch)
        self.assertIn('"huntix_alt_touch"', self.touch)
        self.assertIn("AlternateTouchInput.Enabled && altJoystick != null", self.ui)
        self.assertIn("altJoystick.Value : joystick.Value", self.ui.replace("\n", ""))

    def test_menu_e_riflesso_uova(self):
        self.assertIn("Input touch:", self.menu)
        self.assertIn("ToggleTouch()", self.menu)
        self.assertIn("OnAlternateTouchEnabled()", self.game)
        self.assertIn('GrantFeatureEgg("touch-zone-zero"', self.game)

    def test_bridge_bidirezionale(self):
        self.assertIn('"AltTouchChanged"', self.ipc)
        self.assertIn('"AltTouchChanged" -> handleAltTouchChanged(jsonData)', self.bridge)
        self.assertIn('putBoolean("alt_touch"', self.bridge)
        self.assertIn('j.put("altTouch", true)', self.activity)
        self.assertIn(r'\"altTouch\"', self.manager)
        self.assertIn("AlternateTouchInput.SetEnabled(true, false)", self.manager)


class TestTrafficYieldInstrumentation(unittest.TestCase):
    """Misura diagnostica della precedenza pedonale: zero cambio di guida."""

    def setUp(self):
        self.meter = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                      "TrafficYieldMeter.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                       "PerfProbe.cs"))
        self.car = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                     "Traffic", "CarAgent.cs"))

    def test_misura_senza_modificare_velocita(self):
        self.assertIn("TrafficYieldMeter.Record(", self.car)
        self.assertIn('PerfProbe.Measure("traffic")', self.car)
        self.assertIn("non policy di guida", self.meter)
        self.assertNotIn("currentSpeed = Mathf.Min", self.meter)

    def test_slot_probe_e_telemetria(self):
        # SlotCount sale con gli slot "bait" e "harvest" della Fase 2; gli
        # slot preesistenti (traffic incluso) restano tutti presenti.
        self.assertIn("SlotCount = 21", self.probe)
        self.assertIn('"spawn", "roads", "traffic", "fictional", "mount"', self.probe)
        self.assertIn("telemetry.Mark(\"crosswalk:", self.meter)


class TestFictionalCityIntegration(unittest.TestCase):
    """Città Gioco affiancata: ricetta offline, flag, ponte e uovo."""

    def setUp(self):
        self.gen = read(os.path.join(UNITY, "City", "Scripts", "Game",
                                     "FictionalCityWorld.cs"))
        self.recipe = read(os.path.join(UNITY, "Resources", "CityRecipes",
                                        "foggia_city_recipe.json"))
        self.chunk = read(os.path.join(UNITY, "City", "Scripts", "OSM",
                                       "ChunkManager.cs"))
        self.world = read(os.path.join(UNITY, "City", "Scripts", "OSM",
                                       "CityChunkedWorld.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(ROOT, "unity-project", "Assets", "Scripts",
                                         "Core", "GameManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "HamburgerMenu.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.home = read(os.path.join(ROOT, "app", "src", "main", "java",
                                      "com", "intelligame", "huntix", "HomeActivity.kt"))

    def test_ricetta_solo_parametri(self):
        self.assertIn('"road_spacing_m"', self.recipe)
        self.assertIn('"poi_mix"', self.recipe)
        self.assertNotIn('"nodes"', self.recipe)
        self.assertNotIn('"ele"', self.recipe)
        self.assertIn("Resources/CityRecipes/foggia_city_recipe.json", self.gen)
        self.assertIn("System.Random", self.gen)

    def test_modalita_default_reale_e_streaming_bloccato(self):
        self.assertIn('PlayerPrefs.GetInt(PrefKey, 0)', self.gen)
        self.assertIn("FictionalCityWorld.Enabled) return", self.chunk)
        self.assertIn("Manager.StreamingEnabled = false", self.world)
        self.assertIn("if (!fictional && Manager != null", self.world)
        self.assertIn("FictionalCityWorld.DestroyWorld()", self.world)

    def test_uovo_e_bridge_bidirezionale(self):
        self.assertIn("OnFictionalCityEntered()", self.game)
        self.assertIn('GrantFeatureEgg("fictional-city"', self.game)
        self.assertIn('"Uovo della città di carta"', self.game)
        self.assertIn('"WorldModeChanged"', self.ipc)
        self.assertIn('"WorldModeChanged" -> handleWorldModeChanged(jsonData)', self.bridge)
        self.assertIn('putString("world_mode", mode)', self.bridge)
        self.assertIn('j.put("worldMode", "fictional")', self.activity)
        self.assertIn(r'\"worldMode\"', self.manager)
        self.assertIn("Mondo:", self.menu)

    def test_misura_del_build(self):
        self.assertIn('PerfProbe.Measure("fictional")', self.gen)
        self.assertIn("citta-gioco: edifici=%d poi=%d", self.gen)

    def test_nessun_preload_tile_su_android(self):
        self.assertIn('worldMode == "fictional"', self.home)
        self.assertIn('preload tile saltato', self.home)

    def test_suolo_allineato_allo_spawn(self):
        # Faccia superiore del suolo a y=0: strade e parchi devono restare
        # sopra, non sepolti dentro il collider (regressione 24/11).
        self.assertIn('Box("Suolo", new Vector3(0f, -0.25f, 0f)', self.gen)


class TestVehicleMaterials(unittest.TestCase):
    """Auto Kenney con colore ripristinato: atlas CC0, nessun asset dei clone."""

    def setUp(self):
        self.fix = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                     "VehicleMaterialFix.cs"))
        self.spawn = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                       "VehicleSpawnManager.cs"))
        self.traffic = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                         "TrafficCar.cs"))
        self.notice = read(os.path.join(ROOT, "THIRD_PARTY_NOTICES.md"))

    def test_materiale_condiviso_e_colormap_atlas(self):
        self.assertIn('Resources.Load<Texture2D>("Vehicles/Textures/colormap")', self.fix)
        self.assertIn('Shader.Find("Universal Render Pipeline/Lit")', self.fix)
        self.assertIn("r.sharedMaterial = mat", self.fix)
        self.assertIn("private static bool tried", self.fix)

    def test_percorso_chunked_applica_la_riparazione(self):
        self.assertIn("VehicleMaterialFix.Apply(go)", self.spawn)
        self.assertIn("VehicleMaterialFix.Apply(inst)", self.traffic)
        self.assertIn('!def.prefabKey.StartsWith("Sketchfab/"', self.spawn)

    def test_attribuzione_cc0_e_asset_esclusi(self):
        self.assertIn("Kenney Car Kit", self.notice)
        self.assertIn("CC0 1.0", self.notice)
        self.assertIn("ConceptCar006.fbx", self.notice)
        self.assertNotIn("VehicleMaterialFix.Apply(prefabSketchfabKit)", self.spawn)


class TestVehicleMount(unittest.TestCase):
    """Profilo di guida originale: dentro/sopra, ponte e uova."""

    def setUp(self):
        self.profile = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                         "VehicleMountProfile.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.spawn = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                       "VehicleSpawnManager.cs"))
        self.controller = read(os.path.join(UNITY, "City", "Scripts", "Vehicle",
                                            "VehicleController.cs"))
        self.camera = read(os.path.join(UNITY, "City", "Scripts", "Player",
                                        "CameraRig.cs"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))

    def test_posti_dentro_e_sopra(self):
        self.assertIn("enum VehicleMountKind", self.profile)
        self.assertIn("Inside,", self.profile)
        self.assertIn("Straddled", self.profile)
        self.assertIn("VehicleCategory.Motorcycle", self.profile)
        self.assertIn("VehicleCategory.EBike", self.profile)
        self.assertIn('name.IndexOf("Pullman"', self.profile)
        self.assertIn("interior.driverSeatPos", self.profile)
        self.assertIn("VehicleMountProfile.DriverLocalPosition(vc)", self.game)
        self.assertNotIn("player.gameObject.SetActive(false)", self.game)

    def test_camera_per_tipo_senza_cambiare_quella_auto(self):
        self.assertIn("SetDrivingMode(bool driving, float distance,", self.camera)
        self.assertIn("DefaultDriveDistance = 12f", self.camera)
        self.assertIn("rig.SetDrivingMode(true, camDistance, camHeight, camPitch)", self.game)
        self.assertIn("distance = 7.5f; height = 2.6f; pitch = 8f;", self.profile)

    def test_modelli_due_ruote_originali(self):
        self.assertIn("BuildTwoWheelerProcedural", self.spawn)
        self.assertIn('"wheel-front"', self.spawn)
        self.assertIn('"wheel-rear"', self.spawn)
        self.assertIn('"Saddle"', self.spawn)
        self.assertIn('"Handlebar"', self.spawn)
        self.assertNotIn("WheelCollider", self.spawn)

    def test_uovo_ponte_e_telemetria(self):
        self.assertIn("OnVehicleMounted(mountKind, vehicleType)", self.game)
        self.assertIn('string id = "vehicle-mount-" + place;', self.game)
        self.assertIn("GrantFeatureEgg(id, label, EggController.Rarity.Rare,", self.game)
        self.assertIn('"Uovo del guidatore"', self.game)
        self.assertIn('"Uovo del guidatore in sella"', self.game)
        self.assertIn('SendMessageToAndroid("VehicleMountChanged", json)', self.profile)
        self.assertIn('PerfProbe.Measure("mount")', self.game)
        self.assertIn("telemetry.Mark(\"VEICOLO salita", self.profile)

    def test_bridge_bidirezionale(self):
        self.assertIn('"VehicleMountChanged"', self.ipc)
        self.assertIn('"VehicleMountChanged" -> handleVehicleMountChanged(jsonData)', self.bridge)
        self.assertIn('putString("vehicle_mount"', self.bridge)
        self.assertIn('j.put("vehicleMount", vehicleMount)', self.activity)
        self.assertIn(r'\"vehicleMount\"', self.manager)
        self.assertIn("VehicleMountProfile.RestoreFromAndroid(", self.manager)

    def test_abitacolo_solo_dentro(self):
        self.assertIn("VehicleMountProfile.KindOf(data) == VehicleMountKind.Inside",
                      self.controller)
        self.assertIn("interior.BuildIfNeeded()", self.controller)


class TestMitReuseGate(unittest.TestCase):
    """Inventario 24/11: solo file MIT legalmente e tecnicamente integrabili."""

    def setUp(self):
        self.inventory = read(os.path.join(ROOT, "docs",
            "INVENTARIO_INTEGRAZIONI_CLONE_20261124.md"))
        self.notice = read(os.path.join(ROOT, "THIRD_PARTY_NOTICES.md"))

    def test_shortlist_e_esclusioni_documentate(self):
        self.assertIn("MainMenuAnimator.cs", self.inventory)
        self.assertIn("BakeTransformIntoMesh.cs", self.inventory)
        self.assertIn("EnableGPUInstancing.cs", self.inventory)
        self.assertIn("CommandManager.cs", self.inventory)
        self.assertIn("Console.cs", self.inventory)
        self.assertIn("ConsoleLogEventSubscriber.cs", self.inventory)
        self.assertIn("RagdollBuilder.cs", self.inventory)
        self.assertIn("non integrabile", self.inventory)

    def test_attribuzioni_e_modifiche_nel_notice(self):
        self.assertIn("Copyright (c) 2026 Mishal Bhasim", self.notice)
        self.assertIn("Copyright (c) 2015 James King", self.notice)
        self.assertIn("Integrazioni con codice adattato", self.notice)
        self.assertIn("**Modifiche**:", self.notice)
        self.assertIn("Grand-Road-Auto", self.notice)
        self.assertIn("nessun asset", self.notice.lower())


class TestMenuEntranceAnimation(unittest.TestCase):
    """Animazione menu Zone Zero (MIT): affiancata, opzionale, con uovo e bridge."""

    def setUp(self):
        self.anim = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "MenuEntranceAnimator.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "HamburgerMenu.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(ROOT, "unity-project", "Assets", "Scripts",
                                         "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(ROOT, "app", "src", "main", "java",
                                          "com", "intelligame", "huntix",
                                          "bridge", "BridgeActivity.java"))

    def test_attribuzione_e_default_inattivo(self):
        self.assertIn("Adattato da Zone Zero", self.anim)
        self.assertIn("Mishal Bhasim", self.anim)
        self.assertIn('PlayerPrefs.GetInt(PrefsKey, 0)', self.anim)
        self.assertIn("public static bool Enabled", self.anim)
        self.assertNotIn("using TMPro", self.anim)
        self.assertNotIn("PulseButton", self.anim)

    def test_menu_affiancato_e_non_sostitutivo(self):
        self.assertIn("Animazione menu: Huntix", self.menu)
        self.assertIn("ToggleMenuAnimation()", self.menu)
        self.assertIn("MenuEntranceAnimator.Initialize()", self.ui)
        self.assertIn("if (MenuEntranceAnimator.Enabled && panel != null)", self.menu)
        self.assertIn("panel.AddComponent<MenuEntranceAnimator>()", self.menu)

    def test_uovo_telemetria_e_probe(self):
        self.assertIn("OnMenuAnimationEnabled()", self.game)
        self.assertIn('GrantFeatureEgg("menu-zone-zero"', self.game)
        self.assertIn('"Uovo dell\'ingresso animato"', self.game)
        self.assertIn('città→impostazioni→animazione menu', self.game)
        self.assertIn('menu anim=%s', self.anim)
        self.assertIn('PerfProbe.Measure("menu")', self.anim)

    def test_bridge_bidirezionale(self):
        self.assertIn('"MenuAnimationChanged"', self.ipc)
        self.assertIn('"MenuAnimationChanged" -> handleMenuAnimationChanged(jsonData)',
                      self.bridge)
        self.assertIn('putBoolean("menu_animation"', self.bridge)
        self.assertIn('j.put("menuAnimation", true)', self.activity)
        self.assertIn(r'\"menuAnimation\"', self.manager)
        self.assertIn("MenuEntranceAnimator.SetEnabled(true, false)", self.manager)


class TestQaConsole(unittest.TestCase):
    """Console QA SanAndreasUnity (MIT): viewer locale, nessuna dipendenza GTA."""

    def setUp(self):
        self.console = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                         "HuntixDebugConsole.cs"))
        self.commands = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                          "HuntixCommandManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "HamburgerMenu.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI", "UIManager.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(ROOT, "unity-project", "Assets", "Scripts",
                                         "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(ROOT, "app", "src", "main", "java",
                                          "com", "intelligame", "huntix",
                                          "bridge", "BridgeActivity.java"))

    def test_attribuzione_e_nessuna_dipendenza_proprietaria(self):
        for src in (self.console, self.commands):
            self.assertIn("Adattato da SanAndreasUnity", src)
            self.assertIn("James King", src)
            # Le dipendenze rimosse possono comparire nelle note di modifica:
            # il controllo verte sul codice effettivo, non sui commenti.
            code = "\n".join(line for line in src.splitlines()
                             if not line.lstrip().startswith("//"))
            self.assertNotIn("using SanAndreasUnity", code)
            self.assertNotIn("using UGameCore", code)
            self.assertNotIn("UGameCore.Utilities", code)
            self.assertNotIn("PauseMenuWindow", code)
            self.assertNotIn("NetStatus", code)
            self.assertNotIn("using UnityEditor", code)
            self.assertNotIn("GTA", code)

    def test_parser_mit_adattato_e_probe(self):
        self.assertIn("SplitCommandIntoArguments", self.commands)
        self.assertIn("limitInterval", self.commands)
        self.assertIn('new CommandInfo("help"', self.commands)
        self.assertIn('new CommandInfo("prof"', self.commands)
        self.assertIn("PerfProbe.SnapshotAndReset()", self.commands)
        self.assertIn('PerfProbe.Measure("console")', self.console)

    def test_default_chiuso_e_menu_affiancato(self):
        self.assertIn('PlayerPrefs.GetInt(PrefsKey, 0)', self.console)
        self.assertIn("public static bool Enabled", self.console)
        self.assertIn("Console QA: Off", self.menu)
        self.assertIn("ToggleQaConsole()", self.menu)
        self.assertIn("HuntixDebugConsole.Initialize()", self.ui)

    def test_uovo_telemetria_e_bridge(self):
        self.assertIn("OnQaConsoleEnabled()", self.game)
        self.assertIn('GrantFeatureEgg("console-qa"', self.game)
        self.assertIn('"Uovo del registro di bordo"', self.game)
        self.assertIn('città→strumenti→console QA', self.game)
        self.assertIn('console QA=%s', self.console)
        self.assertIn('"QaConsoleChanged"', self.ipc)
        self.assertIn('"QaConsoleChanged" -> handleQaConsoleChanged(jsonData)',
                      self.bridge)
        self.assertIn('putBoolean("qa_console"', self.bridge)
        self.assertIn('j.put("qaConsole", true)', self.activity)
        self.assertIn(r'\"qaConsole\"', self.manager)
        self.assertIn("HuntixDebugConsole.SetEnabled(true, false)", self.manager)


class TestEditorMeshTools(unittest.TestCase):
    """Utility Editor Zone Zero (MIT): esplicite, con Undo, non runtime."""

    def setUp(self):
        self.tools = read(os.path.join(UNITY, "City", "Editor", "CityMeshTools.cs"))

    def test_attribuzione_e_menu_italiano(self):
        self.assertIn("Adattato da Zone Zero", self.tools)
        self.assertIn("Mishal Bhasim", self.tools)
        self.assertIn("Huntix/Strumenti (MIT Zone Zero)/Cuoce transform nel mesh",
                      self.tools)
        self.assertIn("Huntix/Strumenti (MIT Zone Zero)/Abilita GPU instancing",
                      self.tools)

    def test_solo_editor_e_con_undo(self):
        self.assertIn("using UnityEditor;", self.tools)
        self.assertIn("Undo.RecordObject", self.tools)
        self.assertNotIn("MonoBehaviour", self.tools)
        self.assertIn("non componenti runtime", self.tools)


class TestNpcCompanion(unittest.TestCase):
    """Compagno NPC: pattern MIT riscritto, affiancato, con uovo e bridge."""

    def setUp(self):
        self.companion = read(os.path.join(UNITY, "City", "Scripts", "NPC",
                                          "NpcCompanion.cs"))
        self.npc = read(os.path.join(UNITY, "City", "Scripts", "NPC",
                                     "NPCController.cs"))
        self.actions = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                         "ContextActionController.cs"))
        self.ui = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                    "UIManager.cs"))
        self.menu = read(os.path.join(UNITY, "City", "Scripts", "UI",
                                      "HamburgerMenu.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(ROOT, "unity-project", "Assets", "Scripts",
                                         "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(ROOT, "app", "src", "main", "java",
                                          "com", "intelligame", "huntix",
                                          "bridge", "BridgeActivity.java"))

    def test_attribuzione_e_riscrittura_originale(self):
        self.assertIn("SanAndreasUnity", self.companion)
        self.assertIn("James King", self.companion)
        self.assertIn("Riscritto da zero", self.companion)
        # Le dipendenze rimosse possono comparire nelle note di attribuzione:
        # il controllo verte sul codice effettivo, non sui commenti.
        code = "\n".join(line for line in self.companion.splitlines()
                         if not line.lstrip().startswith("//"))
        for banned in ("using SanAndreasUnity", "using Mirror", "using UGameCore",
                       "PedestrianDef", "NodeReader", "UnityEditor", "GTA"):
            self.assertNotIn(banned, code)
        self.assertNotIn("RagdollBuilder", code)

    def test_default_inattivo_e_affiancato(self):
        self.assertIn("PlayerPrefs.GetInt(PrefsKey, 0)", self.companion)
        self.assertIn("public static bool Enabled", self.companion)
        self.assertIn("Compagno NPC: Off", self.menu)
        self.assertIn("ToggleNpcCompanion()", self.menu)
        self.assertIn("City.NPC.NpcCompanion.Initialize()", self.ui)
        self.assertIn('Make("PORTA CON TE"', self.actions)
        self.assertIn("TryRecruit(nearNPC)", self.actions)

    def test_follow_mode_non_distrugge_il_pedone(self):
        self.assertIn("public bool FollowMode;", self.npc)
        self.assertIn("if (FollowMode)", self.npc)
        self.assertIn("private void TickFollow()", self.npc)
        self.assertIn("SnapToGround();", self.npc)
        self.assertIn("_companion.FollowTarget = game.player.transform.position",
                      self.companion)
        self.assertIn("PerfProbe.Measure(\"companion\")", self.companion)

    def test_uovo_con_posizione_e_cattura_canonica(self):
        self.assertIn("GrantCompanionEgg(\"companion-recruit\"", self.companion)
        self.assertIn("GrantCompanionEgg(\"companion-spot-\" + _spots", self.companion)
        self.assertIn("public void GrantCompanionEgg(", self.game)
        self.assertIn("SpawnFeatureEggAt(featureId, label,", self.game)
        self.assertIn('"FeatureEggHidden"', self.game)
        self.assertIn("WorldOrigin.ToGeo(at)", self.companion)
        self.assertIn("città→pedone→compagno→", self.companion)
        self.assertIn("città→compagno→avvistamento→", self.companion)

    def test_bridge_bidirezionale_e_ripristino_passivo(self):
        for token in ('"NpcCompanionChanged"', '"CompanionRecruited"',
                      '"CompanionEggSpotted"'):
            self.assertIn(token, self.ipc)
            self.assertIn(token + ' -> ', self.bridge)
        self.assertIn('putBoolean("npc_companion"', self.bridge)
        self.assertIn('putString("npc_companion_name"', self.bridge)
        self.assertIn('j.put("npcCompanion", true)', self.activity)
        self.assertIn(r'\"npcCompanion\"', self.manager)
        self.assertIn("NpcCompanion.RestoreFromAndroid(", self.manager)
        self.assertIn("Restore passivo", self.manager)
        self.assertIn("nessun uovo viene dato", self.companion)


class TestFase1RegistroAttivita(unittest.TestCase):
    """Fase 1 · voce 2: diario unico uova+attività, senza duplicare il log."""

    def setUp(self):
        app = os.path.join(ROOT, "app", "src", "main")
        java = os.path.join(app, "java", "com", "intelligame", "huntix")
        self.log = read(os.path.join(java, "managers", "EggWhereLog.kt"))
        self.activity = read(os.path.join(java, "ActivityLogActivity.kt"))
        self.profile = read(os.path.join(java, "ui", "PlayerProfileTabs.kt"))
        self.quests = read(os.path.join(java, "ResearchTaskActivity.kt"))
        self.streak = read(os.path.join(java, "DailyStreakActivity.kt"))
        self.badge = read(os.path.join(java, "LocationBadge.kt"))
        self.manifest = read(os.path.join(app, "AndroidManifest.xml"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.mission = read(os.path.join(UNITY, "City", "Scripts", "Economy",
                                         "NPCMission.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                       "PerfProbe.cs"))

    def test_unico_archivio_con_categorie_e_compatibilita(self):
        self.assertIn("private const val KEY_LOG = \"egg_where_log\"", self.log)
        self.assertIn("const val KIND_EGG = \"uovo\"", self.log)
        self.assertIn("fun recordActivity(", self.log)
        self.assertIn("optString(\"kind\", KIND_EGG)", self.log)
        self.assertIn("MAX = 500", self.log)
        # Le uova continuano apassare da record(): nessun doppio archivio.
        self.assertIn("fun record(ctx: Context, rarityId: String", self.log)

    def test_diario_consultabile_dal_profilo(self):
        self.assertIn(".ActivityLogActivity\"", self.manifest)
        self.assertIn("class ActivityLogActivity : BaseNavActivity()", self.activity)
        self.assertIn("EggWhereLog.getLog(c)", self.activity)
        self.assertIn("entry.optDouble(\"lat\"", self.activity)
        self.assertIn("EggRarity.values()", self.activity)
        self.assertIn("com.intelligame.huntix.ActivityLogActivity::class.java",
                      self.profile)
        self.assertIn("luoghi salvati →", self.profile)

    def test_eventi_gioco_alimentano_il_diario(self):
        self.assertIn("EggWhereLog.recordActivity(", self.quests)
        self.assertIn("EggWhereLog.KIND_QUEST", self.quests)
        self.assertIn("EggWhereLog.recordActivity(", self.streak)
        self.assertIn("EggWhereLog.KIND_LOGIN", self.streak)
        self.assertIn("EggWhereLog.KIND_BADGE", self.badge)
        self.assertIn("SendDiaryEntry(paid)", self.mission)
        self.assertIn('"ActivityLogEvent"', self.mission)
        self.assertIn('"città→missione→"', self.mission)

    def test_bridge_bidirezionale_e_misure(self):
        self.assertIn('"ActivityLogEvent"', self.ipc)
        self.assertIn('"ActivityLogEvent" -> handleActivityLogEvent', self.bridge)
        self.assertIn("EggWhereLog.recordActivity(", self.bridge)
        self.assertIn('j.put("activityLogCount",', self.bridge_activity)
        self.assertIn("EggWhereLog.INSTANCE.count(this)", self.bridge_activity)
        self.assertIn(r'\"activityLogCount\"', self.manager)
        self.assertIn('PlayerPrefs.SetInt("huntix_activity_log_count"', self.manager)
        self.assertIn('Measure("diary")', self.manager)
        self.assertIn("Diario attività: {0} voci", self.manager)
        self.assertIn('"diary"', self.probe)
        # Il ripristino NON genera uova ne' compagni.
        self.assertIn("ricostruisce la cronologia", self.manager)


class TestFase2ConsumabiliEsca(unittest.TestCase):
    """Fase 2 · voce 13: esca collegata alla cattura senza cambiare default."""

    def setUp(self):
        app_main = os.path.join(ROOT, "app", "src", "main")
        java = os.path.join(app_main, "java", "com", "intelligame", "huntix")
        legacy = os.path.join(ROOT, "huntix", "src", "main", "java",
                              "com", "intelligame", "huntix", "legacy", "poi")
        self.bait = read(os.path.join(java, "managers", "EggBaitManager.kt"))
        self.activity = read(os.path.join(java, "BaitActivity.kt"))
        self.nutriment = read(os.path.join(java, "EggNutrimentManager.kt"))
        self.profile = read(os.path.join(java, "ui", "PlayerProfileTabs.kt"))
        self.manifest = read(os.path.join(app_main, "AndroidManifest.xml"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.controller = read(os.path.join(legacy, "game", "CatchController.kt"))
        self.engine = read(os.path.join(legacy, "creature", "CatchEngine.kt"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.interaction = read(os.path.join(UNITY, "Scripts", "Indoor", "InteractionManager.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_esca_usa_inventario_esistente_e_default_invariato(self):
        self.assertIn("EggNutrimentManager.consume(ctx, food)", self.bait)
        self.assertIn("EggNutrimentManager.calculateCatchBonus(bait.food, element)", self.bait)
        self.assertIn("fun calculateCatchBonus(food: EggFood, eggElement: EggElement): Float",
                      self.nutriment)
        self.assertIn("fun giveStarterKit(ctx: Context)", self.nutriment)
        self.assertIn("COOLDOWN_MS = 30_000L", self.bait)
        self.assertIn("DURATION_MS = 5 * 60 * 1000L", self.bait)
        self.assertIn("catchBonus: Float = 1f", self.engine)
        self.assertIn("catchBonus: Float = 1f", self.controller)
        self.assertIn("engine.tryCatch(poi, dist, catchBonus)", self.controller)
        self.assertIn("coerceAtMost(0.98)", self.engine)
        self.assertIn("val catchBonus = com.intelligame.huntix.managers.EggBaitManager.consumeForCatch(ctx)",
                      self.bridge)

    def test_riflesso_uova_posizionale_e_unico(self):
        self.assertIn('val eggId = "bait_${food.id}"', self.bait)
        self.assertIn("EggInventoryManager.getInventory(ctx).any { it.eggId == eggId }", self.bait)
        self.assertIn("EggWhereLog.record(ctx, rarity.id", self.bait)
        self.assertIn("EggWhereLog.recordActivity(", self.bait)
        self.assertIn("OutdoorManager.lastKnownLocation(ctx)", self.bait)

    def test_bridge_bidirezionale(self):
        self.assertIn('"ConsumableUsed"', self.ipc)
        self.assertIn('"ConsumableUsed" -> handleConsumableUsed', self.bridge)
        self.assertIn('"ActiveBaitChanged"', self.bait)
        self.assertIn('PoiUnityBridge.sendEvent("ActiveBaitChanged"', self.bait)
        self.assertIn('UnityBridge.SendMessageToAndroid("ConsumableUsed", json)', self.interaction)
        # WorldOrigin vive in City.OSM: il namespace errato City.World non
        # compila nella build Unity (cscheck non copre Assets/Scripts/Indoor).
        self.assertIn("City.OSM.WorldOrigin.Initialized", self.interaction)
        self.assertIn("City.OSM.WorldOrigin.ToGeo", self.interaction)
        self.assertIn('case "ActiveBaitChanged":', self.manager)
        self.assertIn('j.put("activeBaitId"', self.bridge_activity)
        self.assertIn('j.put("baitCount"', self.bridge_activity)
        self.assertIn(r'\"activeBaitId\"', self.manager)
        self.assertIn('PlayerPrefs.SetString("huntix_active_bait"', self.manager)

    def test_ui_e_misure(self):
        self.assertIn('android:name=".BaitActivity"', self.manifest)
        self.assertIn("class BaitActivity : BaseNavActivity()", self.activity)
        self.assertIn("EggBaitManager.select(c, food.id)", self.activity)
        self.assertIn("com.intelligame.huntix.BaitActivity::class.java", self.profile)
        self.assertIn('"bait"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)
        self.assertIn('Measure("bait")', self.manager)
        self.assertIn("Aromi disponibili: {0}", self.manager)


class TestFase2RaccoltaRisorse(unittest.TestCase):
    """Fase 2 · voce 14: raccolta additiva, senza toccare cattura o valute."""

    def setUp(self):
        app_main = os.path.join(ROOT, "app", "src", "main")
        java = os.path.join(app_main, "java", "com", "intelligame", "huntix")
        self.manager_kt = read(os.path.join(java, "managers", "EggResourceHarvestManager.kt"))
        self.activity = read(os.path.join(java, "ResourceHarvestActivity.kt"))
        self.profile = read(os.path.join(java, "ui", "PlayerProfileTabs.kt"))
        self.manifest = read(os.path.join(app_main, "AndroidManifest.xml"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager_cs = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.interaction = read(os.path.join(UNITY, "Scripts", "Indoor", "InteractionManager.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_evento_dedicato_senza_cattura_o_valute(self):
        self.assertIn('"ResourceHarvested"', self.ipc)
        self.assertIn('"ResourceHarvested" -> handleResourceHarvested', self.bridge)
        self.assertIn('UnityBridge.SendMessageToAndroid("ResourceHarvested", harvestJson)',
                      self.interaction)
        self.assertIn('_currentTarget.action == "collect"', self.interaction)
        # Una raccolta non genera anche un ConsumableUsed: una sola voce nel
        # diario e nessun consumo di aroma.
        self.assertIn('_currentTarget.action != "collect"', self.interaction)
        # Nessun collegamento al bonus di cattura o alle valute.
        self.assertNotIn("catchBonus", self.manager_kt)
        self.assertNotIn("MVC", self.manager_kt)
        self.assertNotIn("gems", self.manager_kt)
        self.assertIn("COOLDOWN_MS = 15_000L", self.manager_kt)

    def test_riflesso_uova_posizionale_e_diario_unico(self):
        self.assertIn('val eggId = "harvest_$id"', self.manager_kt)
        self.assertIn("EggInventoryManager.getInventory(ctx).any { it.eggId == eggId }",
                      self.manager_kt)
        self.assertIn("EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)", self.manager_kt)
        self.assertIn("EggWhereLog.recordActivity(", self.manager_kt)
        self.assertIn("OutdoorManager.lastKnownLocation(ctx)", self.manager_kt)
        self.assertIn("xpReward = 0", self.manager_kt)

    def test_bridge_bidirezionale_e_misure(self):
        self.assertIn('j.put("resourceHarvestCount"', self.bridge_activity)
        self.assertIn('j.put("resourceHarvestKinds"', self.bridge_activity)
        self.assertIn(r'\"resourceHarvestCount\"', self.manager_cs)
        self.assertIn('PlayerPrefs.SetInt("huntix_resource_harvest_count"', self.manager_cs)
        self.assertIn('Measure("harvest")', self.manager_cs)
        self.assertIn('Measure("harvest")', self.interaction)
        self.assertIn('"harvest"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)
        self.assertIn("Raccolte: {0}", self.manager_cs)

    def test_ui_italiano_senza_consumare_inventari_esistenti(self):
        self.assertIn('android:name=".ResourceHarvestActivity"', self.manifest)
        self.assertIn("class ResourceHarvestActivity : BaseNavActivity()", self.activity)
        self.assertIn("com.intelligame.huntix.ResourceHarvestActivity::class.java", self.profile)
        self.assertIn("val manager = EggResourceHarvestManager", self.activity)
        self.assertIn("manager.total(c)", self.activity)
        self.assertIn("manager.allCounts(c)", self.activity)
        # La raccolta non scala le esche e non usa il secchiello di cattura.
        self.assertNotIn("EggNutrimentManager.consume", self.manager_kt)
        self.assertNotIn("CatchToolManager", self.manager_kt)


class TestFase2Crafting(unittest.TestCase):
    """Fase 2 · voce 15: fusione uova additiva, senza toccare SavedManager."""

    def setUp(self):
        app_main = os.path.join(ROOT, "app", "src", "main")
        java = os.path.join(app_main, "java", "com", "intelligame", "huntix")
        self.manager_kt = read(os.path.join(java, "managers", "EggFusionCraftManager.kt"))
        self.activity = read(os.path.join(java, "CraftActivity.kt"))
        self.saved = read(os.path.join(java, "managers", "SavedManager.kt"))
        self.profile = read(os.path.join(java, "ui", "PlayerProfileTabs.kt"))
        self.manifest = read(os.path.join(app_main, "AndroidManifest.xml"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager_cs = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.menu_cs = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_wrapper_non_modifica_saved_manager(self):
        # La fusione storica resta intatta: il chiamante e' solo il wrapper.
        self.assertIn("SavedManager.executeFusionSlots(ctx)", self.manager_kt)
        self.assertIn("SavedManager.canFuse(ctx)", self.manager_kt)
        self.assertIn("SavedManager.getFusionSlots(ctx)", self.manager_kt)
        self.assertIn("SavedManager.addToFusionSlot(ctx, egg)", self.manager_kt)
        self.assertIn("SavedManager.removeFromFusionSlot(ctx, index)", self.manager_kt)
        # Nessuna valuta, drop o shop nel nuovo percorso.
        for token in ("spendMvc", "addMvc", "spendGems", "dropTable", "ShopDefs"):
            self.assertNotIn(token, self.manager_kt)
        # Le API storiche sono ancora presenti e invariate nel loro file.
        self.assertIn("fun executeFusionSlots(ctx: Context): HatchedEgg?", self.saved)
        self.assertIn("val newLevel = (base.level + 1).coerceAtMost(4)", self.saved)

    def test_riflesso_uovo_posizionale_idempotente_e_un_solo_diario(self):
        self.assertIn('val eggId = "fusion_" + actual.joinToString("_")', self.manager_kt)
        self.assertIn("val actual = fused.fusedFrom.sorted()", self.manager_kt)
        self.assertIn('EggInventoryManager.getInventory(ctx).none { it.eggId == eggId }',
                      self.manager_kt)
        self.assertIn("EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)",
                      self.manager_kt)
        self.assertIn("EggWhereLog.recordActivity(", self.manager_kt)
        self.assertIn("xpReward = 0", self.manager_kt)
        # Ripristino dell'uovo fuso se addEgg storico ha fallito (inv. pieno).
        self.assertIn("inventoryFull = !EggInventoryManager.addEgg(ctx, restored)",
                      self.manager_kt)
        self.assertIn("PoiUnityBridge.sendEvent(\"FusionCrafted\"", self.manager_kt)

    def test_bridge_bidirezionale_e_misure(self):
        self.assertIn('"CraftRequest"', self.ipc)
        self.assertIn('"CraftRequest" -> handleCraftRequest', self.bridge)
        self.assertIn('j.optString("action", "open") == "craft"', self.bridge)
        self.assertIn('SendMessageToAndroid(\n                            "CraftRequest", "{\\"action\\":\\"open\\"}"',
                      self.menu_cs)
        self.assertIn('case "FusionCrafted":', self.manager_cs)
        self.assertIn('j.put("fusionCraftCount"', self.bridge_activity)
        self.assertIn('j.put("fusionCraftLastLevel"', self.bridge_activity)
        self.assertIn('PlayerPrefs.SetInt("huntix_fusion_craft_count"', self.manager_cs)
        self.assertIn('Measure("craft")', self.manager_cs)
        self.assertIn('"craft"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)

    def test_ui_italiana_senza_secondario_inventario(self):
        self.assertIn('android:name=".CraftActivity"', self.manifest)
        self.assertIn("class CraftActivity : BaseNavActivity()", self.activity)
        self.assertIn("com.intelligame.huntix.CraftActivity::class.java", self.profile)
        self.assertIn("Fondi le uova", self.activity)
        self.assertIn("mgr.slots(c)", self.activity)
        self.assertIn("mgr.craft(c,", self.activity)
        self.assertNotIn("EggInventoryManager.addEgg", self.activity)


class TestFase2InventarioEvoluto(unittest.TestCase):
    """Fase 2 · voce 11: ordinamenti e squadra uova senza secondo inventario."""

    def setUp(self):
        app_main = os.path.join(ROOT, "app", "src", "main")
        java = os.path.join(app_main, "java", "com", "intelligame", "huntix")
        self.manager_kt = read(os.path.join(java, "managers", "EggInventoryViewManager.kt"))
        self.activity = read(os.path.join(java, "EggCollectionActivity.kt"))
        self.inventory = read(os.path.join(java, "EggInventoryManager.kt"))
        self.profile = read(os.path.join(java, "ui", "PlayerProfileTabs.kt"))
        self.manifest = read(os.path.join(app_main, "AndroidManifest.xml"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager_cs = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.menu_cs = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_ordinamenti_sola_vista_e_api_storiche_intatte(self):
        self.assertIn("EggInventoryManager.getSortedByRarity(ctx)", self.manager_kt)
        self.assertIn("EggInventoryManager.getSortedByPower(ctx)", self.manager_kt)
        self.assertIn("EggInventoryManager.getInventory(ctx)", self.manager_kt)
        self.assertIn('prefs(ctx).edit().putString(KEY_MODE, SortMode.fromId(id).id)',
                      self.manager_kt)
        self.assertNotIn("saveInventory", self.manager_kt)
        for api in ("fun getSortedByRarity", "fun getSortedByPower",
                    "fun toggleBattleTeam", "fun getBattleTeam"):
            self.assertIn(api, self.inventory)

    def test_riflesso_uovo_posizionale_per_rarita(self):
        self.assertIn('"inventory_team_$rarityId"', self.manager_kt)
        self.assertIn('none { it.eggId == "inventory_team_$rarityId" }', self.manager_kt)
        self.assertIn("EggWhereLog.record(ctx, rarityId, where, lat, lng, name)",
                      self.manager_kt)
        self.assertIn("EggWhereLog.recordActivity(", self.manager_kt)
        self.assertIn("xpReward = 0", self.manager_kt)
        self.assertIn("OutdoorManager.lastKnownLocation(ctx)", self.manager_kt)
        # La squadra creature dei raid non viene toccata.
        for token in ("RaidBattle", "SurpriseManager", "setBattleTeam"):
            self.assertNotIn(token, self.manager_kt)

    def test_bridge_bidirezionale_e_misure(self):
        self.assertIn('"InventoryViewRequest"', self.ipc)
        self.assertIn('"InventoryViewRequest" -> handleInventoryViewRequest', self.bridge)
        self.assertIn('EggCollectionActivity::class.java', self.bridge)
        self.assertIn('SendMessageToAndroid(\n                            "InventoryViewRequest", "{\\"action\\":\\"open\\"}"',
                      self.menu_cs)
        self.assertIn('case "EggInventoryChanged":', self.manager_cs)
        self.assertIn('PoiUnityBridge.sendEvent(\n                "EggInventoryChanged"',
                      self.manager_kt)
        self.assertIn('j.put("inventoryCount"', self.bridge_activity)
        self.assertIn('PlayerPrefs.SetInt("huntix_inventory_egg_count"', self.manager_cs)
        self.assertIn('Measure("inventory")', self.manager_cs)
        self.assertIn('"inventory"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)

    def test_ui_italiana_senza_secondario_inventario(self):
        self.assertIn('android:name=".EggCollectionActivity"', self.manifest)
        self.assertIn("class EggCollectionActivity : BaseNavActivity()", self.activity)
        self.assertIn("com.intelligame.huntix.EggCollectionActivity::class.java", self.profile)
        self.assertIn("Le tue uova", self.activity)
        self.assertIn("mgr.sorted(c)", self.activity)
        self.assertIn("mgr.toggleTeam(c, egg.istanzaId)", self.activity)
        self.assertIn("squadra uova è diversa", self.activity)
        self.assertNotIn("addEgg", self.activity)


class TestFase2Ricette(unittest.TestCase):
    """Fase 2 · voce 16: ricettario in sola lettura, scoperte addittive."""

    def setUp(self):
        java = os.path.join(ROOT, "app", "src", "main", "java", "com", "intelligame", "huntix")
        self.manager = read(os.path.join(java, "managers", "EggFusionRecipeManager.kt"))
        self.craft_manager = read(os.path.join(java, "managers", "EggFusionCraftManager.kt"))
        self.activity = read(os.path.join(java, "CraftActivity.kt"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.bridge = read(os.path.join(APP, "Bridge.kt"))
        self.bridge_activity = read(os.path.join(APP, "BridgeActivity.java"))
        self.manager_cs = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.menu_cs = read(os.path.join(UNITY, "City", "Scripts", "UI", "HamburgerMenu.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_catalogo_aderente_al_motore_senza_livelli_uguali(self):
        self.assertIn('Recipe(1, 2, "𝄞 Cristallo Potenziato"', self.manager)
        self.assertIn('Recipe(2, 3, "✧ Cristallo Supremo"', self.manager)
        self.assertIn('Recipe(3, 4, "✦ Cristallo MAXIMUM"', self.manager)
        self.assertIn("3 uova Lv.1 della stessa creatura → 1 uovo Lv.2 (potenza ×2)",
                      self.manager)
        self.assertIn("non occorre che i tre livelli siano uguali", self.activity)
        self.assertIn("EggFusionCraftManager.available(ctx)", self.manager)
        for token in ("SavedManager.executeFusionSlots(ctx)", "SavedManager.canFuse(ctx)",
                      "spendMvc", "spendGems", "dropTable"):
            self.assertNotIn(token, self.manager)

    def test_riflesso_uovo_posizionale_distinto_dalla_fusione(self):
        self.assertIn('val id: String get() = "recipe_${fromLevel}_to_${toLevel}"', self.manager)
        self.assertIn('"fusion_" + actual.joinToString("_")', self.craft_manager)
        self.assertIn("EggFusionRecipeManager.markUsed(\n            ctx, fromLevel, rarity.id, fused.displayName, place, lat, lng)",
                      self.craft_manager)
        self.assertIn('none { it.eggId == id }', self.manager)
        self.assertIn("EggWhereLog.record(ctx, rarity.id, where, lat, lng, name)", self.manager)
        self.assertIn("EggWhereLog.recordActivity(", self.manager)
        self.assertIn("xpReward = 0", self.manager)
        self.assertIn("PoiUnityBridge.sendEvent(\"RecipeDiscoveryChanged\"", self.manager)

    def test_bridge_bidirezionale_e_misure(self):
        self.assertIn('"RecipeRequest"', self.ipc)
        self.assertIn('"RecipeRequest" -> handleRecipeRequest', self.bridge)
        self.assertIn('EggFusionRecipeManager\n                .report(', self.bridge)
        self.assertIn('SendMessageToAndroid(\n                            "RecipeRequest", "{\\"action\\":\\"open\\"}"',
                      self.menu_cs)
        self.assertIn('case "RecipeDiscoveryChanged":', self.manager_cs)
        self.assertIn('j.put("recipeDiscoveredCount"', self.bridge_activity)
        self.assertIn('PlayerPrefs.SetInt("huntix_recipe_discovered_count"', self.manager_cs)
        self.assertIn('Measure("recipe")', self.manager_cs)
        self.assertIn('"recipe"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)

    def test_ui_sola_lettura_senza_nuova_activity(self):
        self.assertIn("Ricette di fusione — ", self.activity)
        self.assertIn("EggFusionRecipeManager.views(c).forEach", self.activity)
        self.assertIn("Non avvia fusioni", self.activity)
        self.assertIn("Ricette di fusione (guida)", self.menu_cs)
        self.assertIn("prt.sizeDelta = new Vector2(360f, 1220f)", self.menu_cs)
        self.assertIn("Le ricette sono una guida", self.activity)


class TestEventBusSubscribeOnceIntegration(unittest.TestCase):
    """rob1997/Controller (MIT): SubscribeOnce one-shot, senza handler appesi."""

    def setUp(self):
        self.bus = read(os.path.join(UNITY, "City", "Scripts", "Environment", "EventBus.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.probe = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "PerfProbe.cs"))

    def test_api_esistente_invariata(self):
        for token in ("public static void Subscribe<T>(Action<T> handler) where T : struct",
                      "public static void Unsubscribe<T>(Action<T> handler) where T : struct",
                      "public static void Publish<T>(T evt) where T : struct",
                      "public static void Clear()"):
            self.assertIn(token, self.bus)

    def test_subscribe_once_si_auto_disiscrive(self):
        self.assertIn("public static void SubscribeOnce<T>(Action<T> handler) where T : struct", self.bus)
        self.assertIn("Unsubscribe(wrapper);", self.bus)
        self.assertIn("Robel Getnet Geremew", self.bus)
        self.assertIn("public struct EggHintObservedEvent", self.bus)

    def test_collegamento_al_primo_indizio_uovo(self):
        self.assertIn("EventBus.SubscribeOnce<City.Environment.EggHintObservedEvent>", self.game)
        self.assertIn("SendFirstEggHintDiary(evt)", self.game)
        self.assertIn("EventBus.Publish(\n                new City.Environment.EggHintObservedEvent(featureId, lat, lng))", self.game)
        self.assertIn('"ActivityLogEvent", json', self.game)
        self.assertIn('"egg-hint"', self.probe)
        self.assertIn("SlotCount = 21", self.probe)


class TestOpenSourceFeatureBridgeInfrastructure(unittest.TestCase):
    """Ponte bidirezionale comune: preferenza Android, uovo e telemetria."""

    def setUp(self):
        self.bridge = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                        "OpenSourceFeatureBridge.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))
        self.manager = read(os.path.join(UNITY, "Scripts", "Core", "GameManager.cs"))
        self.ipc = read(os.path.join(APP, "UnityIpc.kt"))
        self.kt_bridge = read(os.path.join(APP, "Bridge.kt"))
        self.activity = read(os.path.join(APP, "BridgeActivity.java"))

    def test_unity_ver_android(self):
        self.assertIn('"OpenSourceFeatureChanged"', self.bridge)
        self.assertIn('"OpenSourceFeatureChanged"', self.ipc)
        self.assertIn('"OpenSourceFeatureChanged" -> handleOpenSourceFeatureChanged(jsonData)',
                      self.kt_bridge)
        self.assertIn('prefs.edit().putBoolean("huntix_os_$id"', self.kt_bridge)
        self.assertIn("handleFeatureEggHidden(jsonData)", self.kt_bridge)

    def test_android_ver_unity(self):
        self.assertIn('j.put("openSourceFeatures", osFlags.toString())', self.activity)
        self.assertIn("openSourceFeatures", self.manager)
        self.assertIn("OpenSourceFeatureBridge.RestoreFromAndroid(", self.manager)
        self.assertIn("public static void Register(string id, Action<bool> handler)", self.bridge)

    def test_riflesso_uovo_e_misura(self):
        self.assertIn("Game.Instance.GrantAuditEgg(id", self.bridge)
        self.assertIn("public void GrantAuditEgg(string featureId", self.game)
        self.assertIn("GrantFeatureEgg(featureId, label, EggController.Rarity.Uncommon, place)",
                      self.game)
        self.assertIn('using (PerfProbe.Measure("menu"))', self.bridge)
        self.assertIn('SessionTelemetry.Instance.Mark("os feature %s=%s"', self.bridge)


class TestEggCullGraceIntegration(unittest.TestCase):
    """SanAndreasUnity (MIT): culling uova a 1 Hz con grazia e isteresi."""

    def setUp(self):
        self.grace = read(os.path.join(UNITY, "City", "Scripts", "Economy", "EggCullGrace.cs"))
        self.spawn = read(os.path.join(UNITY, "City", "Scripts", "Economy", "EggSpawnManager.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))

    def test_opt_in_e_valori_invariati(self):
        self.assertIn("public static bool Enabled { get; private set; }", self.grace)
        self.assertIn("public const float DeactivateDistance = 60f;", self.grace)
        self.assertIn("public const float ReactivateDistance = 70f;", self.grace)
        self.assertIn("public const float GraceSeconds = 3f;", self.grace)
        self.assertIn("public const float TickSeconds = 1f;", self.grace)
        self.assertIn("James King", self.grace)

    def test_integrazione_senza_cancellazione(self):
        self.assertIn("if (EggCullGrace.Enabled)", self.spawn)
        self.assertIn("EggCullGrace.Tick(eggs, player, Time.time);", self.spawn)
        self.assertIn("egg.SetActive(false);", self.grace)
        self.assertIn("egg.SetActive(true);", self.grace)
        self.assertNotIn("Destroy(", self.grace)
        self.assertIn("City.Economy.EggCullGrace.Initialize();", self.game)
        self.assertIn('OpenSourceFeatureBridge.Register(Id, on =>', self.grace)


class TestOpenSourceAuditIntegration(unittest.TestCase):
    """Unity-Rpg, OpenStash, Grand Road Auto e Quaternius: audit locali."""

    def setUp(self):
        self.audit = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics", "OpenSourceAudit.cs"))
        self.menu = read(os.path.join(ROOT, "unity-project", "Assets", "Editor",
                                      "OpenSourceAuditMenu.cs"))
        self.commands = read(os.path.join(UNITY, "City", "Scripts", "Diagnostics",
                                          "OpenSourceFeatureCommands.cs"))
        self.game = read(os.path.join(UNITY, "City", "Scripts", "Game.cs"))

    def test_quattro_sorgenti_distinte(self):
        for source in ("asset-handles", "resource-defs", "load-lifecycle", "asset-packs"):
            self.assertIn('"' + source + '"', self.audit)

    def test_controlli_senza_modifiche(self):
        self.assertIn("GetRawConstantValue()", self.audit)
        self.assertIn("UnloadRadius", self.audit)
        self.assertIn("CarsPerTile", self.audit)
        self.assertIn("PackManifest", self.audit)
        self.assertIn("CC0", self.audit)
        self.assertIn("QAL", self.audit)
        self.assertNotIn("SetValue(", self.audit)
        self.assertNotIn("TypeNameHandling", self.audit)

    def test_menu_editor_e_console_qa(self):
        for item in ("Huntix/Audit asset handles", "Huntix/Audit resource definitions",
                     "Huntix/Audit load lifecycle", "Huntix/Audit pack licenses"):
            self.assertIn('[MenuItem("' + item + '")]', self.menu)
        self.assertIn('"osfeature"', self.commands)
        self.assertIn('"osaudit"', self.commands)
        self.assertIn("OpenSourceFeatureCommands.Register();", self.game)


if __name__ == "__main__":
    unittest.main(verbosity=2)

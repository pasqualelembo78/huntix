#!/usr/bin/env python3
"""Verifiche statiche dell'abitazione open world (2026-11-28).

Nessun test dichiara un comportamento visivo su device non eseguito: qui si
controlla che il codice sorgente contini i gate effettivi (ingresso naturale,
permessi nel punto di modifica, acquisto dall'interno, spawn iniziale) e che
i sistemi esistenti (economia, uova, teleport, snapshot) siano riutilizzati.
"""
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


ACCESS = read("unity-project/Assets/City/Scripts/Interior/HouseAccess.cs")
SPOT = read("unity-project/Assets/City/Scripts/Interior/HousePurchaseSpot.cs")
ENTRANCE = read("unity-project/Assets/City/Scripts/Interior/BuildingEntrance.cs")
PLACER = read("unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs")
MANAGER = read("unity-project/Assets/City/Scripts/Interior/InteriorManager.cs")
DOOR = read("unity-project/Assets/City/Scripts/Interior/InteriorDoor.cs")
GENERATOR = read("unity-project/Assets/City/Scripts/Interior/InteriorGenerator.cs")
EXIT = read("unity-project/Assets/City/Scripts/Interior/ExitTrigger.cs")
FURNITURE = read("unity-project/Assets/Scripts/Indoor/FurniturePlacer.cs")
WORLD = read("unity-project/Assets/City/Scripts/OSM/CityChunkedWorld.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java")
GAME = read("unity-project/Assets/City/Scripts/Game.cs")
HOME = read("unity-project/Assets/City/Scripts/Environment/HomeSystem.cs")


class TestIngressoNaturale(unittest.TestCase):
    def test_modello_ingressi_estendibile(self):
        for kind in ("MainDoor", "Window", "Garage", "Gutter", "Stealth"):
            self.assertIn(kind, ACCESS)
        self.assertIn("public enum Role", ACCESS)
        self.assertIn("public static bool IsResidential", ACCESS)

    def test_porta_senza_pulsante(self):
        self.assertIn("public bool naturalEntry;", ENTRANCE)
        self.assertIn("public bool reservedByOther;", ENTRANCE)
        self.assertIn("public bool restrictedAccess;", ENTRANCE)
        self.assertIn("StartAutoEntry();", ENTRANCE)
        self.assertIn("if (naturalEntry) return;", ENTRANCE)
        self.assertIn("if (naturalEntry && HouseAccess.IsResidential(this))", ENTRANCE)
        self.assertIn("if (naturalEntry && HouseAccess.IsResidential(this))\n            {\n                // Nessun tap", ENTRANCE)
        self.assertIn("entrance.naturalEntry =", PLACER)
        self.assertIn("public void OnEntranceFocusChanged(BuildingEntrance entrance)", GAME)
        self.assertIn("ui.HideInteract();", GAME)

    def test_porte_interne_si_aprono_camminando(self):
        self.assertIn("public bool autoOpen;", DOOR)
        self.assertIn("if (!_open) SetOpen(true);", DOOR)
        self.assertIn("door.autoOpen = Entrance != null", GENERATOR)
        self.assertIn("panel.localRotation = Quaternion.Euler(0f, _yaw, 0f);", DOOR)

    def test_uscita_dalla_porta_senza_prompt(self):
        self.assertIn("mgr.MarkOutside(this);", ENTRANCE)
        self.assertIn("public bool suppressPrompt;", EXIT)
        self.assertIn("if (mgr != null && mgr.IsInside) mgr.ExitInterior();", EXIT)
        self.assertIn("exit.suppressPrompt =", GENERATOR)

    def test_nessun_segnaposto_giallo_sulle_case(self):
        self.assertIn("if (naturalEntry) return;", ENTRANCE)


class TestVisitaEPermessi(unittest.TestCase):
    def test_ruolo_senza_secondo_registro(self):
        self.assertIn("City.Environment.HomeSystem.IsThisHome(door)", ACCESS)
        self.assertIn("Role.Visitor", ACCESS)
        self.assertIn("Role.Owner", ACCESS)

    def test_gate_nelle_logiche_che_modificano(self):
        self.assertIn("HouseAccess.CanPlaceFurniture()", FURNITURE)
        self.assertIn("CanModifyCurrentInterior()", ACCESS)
        self.assertIn("if (!mgr.IsInside) return true;", ACCESS)
        self.assertIn("if (!IsResidential(e)) return true;", ACCESS)
        # Il gate deve essere presente sia all'inizio sia al commit.
        self.assertEqual(FURNITURE.count("CanPlaceFurniture()"), 2)

    def test_visita_non_acquista(self):
        self.assertIn("HousePurchaseSpot.Install(ActiveInteriorRoot, e);", MANAGER)
        self.assertIn("public void Buy()", SPOT)
        self.assertIn("HouseAccess.TryBuy(_entrance)", SPOT)
        self.assertIn("if (!_focused) return;", SPOT)

    def test_azioni_persistenti_riservate_al_proprietario(self):
        ctx = read("unity-project/Assets/City/Scripts/UI/ContextActionController.cs")
        self.assertIn("City.Interior.HouseAccess.CanModifyCurrentInterior()", ctx)
        self.assertIn("DORMI", ctx)


class TestAcquistoDallInterno(unittest.TestCase):
    def test_prezzo_e_fondi_dal_sistema_esistente(self):
        self.assertIn("City.Environment.HomeSystem.HomeCost", ACCESS)
        self.assertIn("City.World.Wallet.CanAfford", ACCESS)
        self.assertIn("City.Environment.HomeSystem.Buy(", ACCESS)
        self.assertIn("City.Environment.HomeSystem.BuyLabel", ACCESS)

    def test_casa_altrui_non_acquistabile(self):
        self.assertIn("if (e.reservedByOther)", ACCESS)
        self.assertIn("HouseAccess.RoleFor(_entrance) == HouseAccess.Role.Owner", SPOT)
        self.assertIn("HAI GIA UNA CASA", ACCESS)

    def test_riflesso_uova_conservato(self):
        self.assertIn("CityGlobalAreasBridge.RecordFirstHomeFree", HOME)
        self.assertIn("FeatureEggHidden", read(
            "unity-project/Assets/City/Scripts/Diagnostics/CityGlobalAreasBridge.cs"))
        self.assertIn("CityGlobalAreasBridge.Record(", ACCESS)
        self.assertIn("HouseAccess.NoteEntry(e, e.entryKind);", MANAGER)


class TestSpawnCasaIniziale(unittest.TestCase):
    def test_segnale_nuova_partita(self):
        self.assertIn('j.put("firstGame"', BRIDGE)
        self.assertIn("hasLastPosition(this)", BRIDGE)
        self.assertIn("firstGame", GAME_MANAGER)
        self.assertIn("UseFirstHomeSpawn()", GAME_MANAGER)
        self.assertIn("UseFirstHomeSpawn", WORLD)

    def test_casa_assegnata_con_home_system(self):
        self.assertIn("private void AssignInitialHome()", WORLD)
        self.assertIn("City.Environment.HomeSystem.Buy(home.label, door)", WORLD)
        self.assertIn("VisitInteriorRegistry.DefaultOsmId", WORLD)

    def test_spawn_dentro_dopo_gate(self):
        self.assertIn("if (_initialHomeAssigned) StartCoroutine(EnterInitialHomeRoutine());", WORLD)
        self.assertIn("InteriorTeleport.Move(gen.PrefabEnterWorld", WORLD)
        self.assertIn("mgr.MarkInside(e);", WORLD)
        self.assertIn("FindEntranceByOsmId", MANAGER)
        self.assertIn("if (_firstHomeSpawnRequested && !City.Environment.HomeSystem.OwnsHome)",
                      WORLD)

    def test_salvataggi_non_sovrascritti(self):
        self.assertIn('j.put("firstGame", !WorldPosCloud.INSTANCE.hasLastPosition(this));', BRIDGE)
        self.assertIn("if (!_firstHomeSpawnRequested) return;", WORLD)
        self.assertIn("if (_firstHomeSpawnRequested && !City.Environment.HomeSystem.OwnsHome)",
                      WORLD)


class TestConservazione(unittest.TestCase):
    def test_sistemi_esistenti_intatti(self):
        self.assertIn("public bool inPlace;", ENTRANCE)
        self.assertIn("public bool forceDoorMarker;", ENTRANCE)
        self.assertIn("StartCoroutine(EnterPrefab(gen));", ENTRANCE)
        self.assertIn("public static bool Buy(string name, Vector3 doorPos)", HOME)
        self.assertIn("MarkInsideShell", MANAGER)


if __name__ == "__main__":
    unittest.main()

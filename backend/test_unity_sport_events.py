#!/usr/bin/env python3
"""Verifiche statiche dell'integrazione stadio/ippodromo/eventi (25/11/2026).

Il test è statico perché non c'è un device Unity/Android in questa macchina:
controlla che gli asset siano davvero presenti e che sistemi esistenti,
geo/graph, CacheVersion, priorità di interazione e ponte bidirezionale siano
stati conservati. Non dichiara test visivi o di FPS.
"""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as handle:
        return handle.read()


def path_exists(path):
    return os.path.exists(os.path.join(ROOT, path))


DIRECTOR = read("unity-project/Assets/City/Scripts/Events/VenueDirector.cs")
SIM = read("unity-project/Assets/City/Scripts/Events/VenueSimulation.cs")
STATE = read("unity-project/Assets/City/Scripts/Events/VenueEventState.cs")
AUDIO = read("unity-project/Assets/City/Scripts/Events/VenueAudio.cs")
MATERIALS = read("unity-project/Assets/City/Scripts/Events/VenueMaterials.cs")
BUILDER = read("unity-project/Assets/City/Scripts/OSM/ChunkBuilder.cs")
GAME = read("unity-project/Assets/City/Scripts/Game.cs")
MENU = read("unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs")
SYNC = read("unity-project/Assets/City/Scripts/Sync/CityWorldSync.cs")
TILE_CLIENT = read("unity-project/Assets/City/Scripts/OSM/TileClient.cs")
IPC = read("app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt")
BRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
ANDROID_SYNC = read("app/src/main/java/com/intelligame/huntix/bridge/CityStateSync.kt")
LICENSES = read("unity-project/Assets/ThirdPartyLicenses.md")
NOTICES = read("THIRD_PARTY_NOTICES.md")
VENUE_ROOT = "unity-project/Assets/Resources/EventVenues"


class TestAssetAcquisiti(unittest.TestCase):
    def test_modelli_reali_presenti(self):
        for group, count in (("stadium", 20), ("racecourse", 20),
                             ("stable", 15), ("people", 5), ("horses", 1)):
            folder = os.path.join(ROOT, VENUE_ROOT, group)
            self.assertTrue(os.path.isdir(folder), group)
            fbxs = [f for f in os.listdir(folder) if f.endswith(".fbx")]
            self.assertGreaterEqual(len(fbxs), count, group)
            for f in fbxs:
                self.assertGreater(os.path.getsize(os.path.join(folder, f)), 1000, f)

    def test_modelli_fondamentali(self):
        for p in ("stadium/Pitch.fbx", "stadium/FullSizeOpenNetSoccerGoal.fbx",
                  "stadium/ThreeRowSeatedStandBay.fbx",
                  "stadium/ClubNeutralElevatedScoreboard.fbx",
                  "racecourse/TurfTrackStraight.fbx",
                  "racecourse/StartingStallsSixBay.fbx",
                  "racecourse/ParadeRingLawn.fbx",
                  "stable/StallBayWithDoor.fbx",
                  "horses/RaceHorseAnimated.fbx"):
            self.assertTrue(path_exists(VENUE_ROOT + "/" + p), p)

    def test_clip_cavallo_animato(self):
        with open(os.path.join(ROOT, VENUE_ROOT, "horses/RaceHorseAnimated.fbx"), "rb") as f:
            data = f.read()
        for clip in (b"Idle", b"Walk", b"Trot", b"Gallop", b"Graze", b"Rear"):
            self.assertIn(clip, data, clip.decode())

    def test_attribuzioni_e_licenze(self):
        self.assertIn("CC0 1.0 Universal", LICENSES)
        self.assertIn("https://3dassets.dev/packs/football-soccer-club-grounds", LICENSES)
        self.assertIn("https://3dassets.dev/packs/racecourse-and-parade-ring", LICENSES)
        self.assertIn("https://3dassets.dev/packs/equestrian-yard-and-stables", LICENSES)
        self.assertIn("https://3dassets.dev/packs/low-poly-character-figures", LICENSES)
        self.assertIn('"Race Horse" by vinish.dev', LICENSES)
        self.assertIn("https://creativecommons.org/licenses/by/4.0/", LICENSES)
        self.assertIn("decimazione della mesh", LICENSES)
        self.assertIn("vinish.dev", NOTICES)
        self.assertIn("Animatics", LICENSES)
        self.assertIn("Scartato", LICENSES)

    def test_nessun_asset_scommesse(self):
        for name in ("BookmakersPitchBoard", "ToteHut"):
            self.assertFalse(path_exists(VENUE_ROOT + "/racecourse/" + name + ".fbx"), name)


class TestInnestoAdditivo(unittest.TestCase):
    def test_registro_chunk_senza_toccare_wire(self):
        self.assertIn("City.Events.VenueDirector.Ensure().OnChunkBuilt(chunk)", BUILDER)
        self.assertIn('Section("Eventi sportivi")', BUILDER)
        self.assertIn("chunk.built = true", BUILDER)
        self.assertIn("CacheVersion = 5", TILE_CLIENT)
        self.assertNotEqual("CacheVersion = 6", TILE_CLIENT)

    def test_carico_lazy_e_un_solo_impianto(self):
        self.assertIn("BUILD_RADIUS = 115f", DIRECTOR)
        self.assertIn("MAX_ACTIVE = 1", DIRECTOR)
        self.assertIn("StartCoroutine(BuildRoutine(s))", DIRECTOR)
        self.assertIn("s.Root.SetActive(d < SIM_RADIUS)", DIRECTOR)
        self.assertIn("EventsEnabled", DIRECTOR)
        self.assertIn("PlayerPrefs.GetInt(\"huntix_venue_events_enabled\", 0)", DIRECTOR)

    def test_default_off_e_voce_menu(self):
        self.assertIn("Eventi sportivi: Off", MENU)
        self.assertIn("() => ToggleVenueEvents()", MENU)
        self.assertIn("private void ToggleVenueEvents()", MENU)
        self.assertIn("RefreshVenueLabel()", MENU)
        self.assertIn("Eventi sportivi attivi", MENU)

    def test_interazione_a_priorita_bassa(self):
        self.assertIn("if (currentEntrance != null)", GAME)
        self.assertIn("City.Events.VenueDirector.TryInteractNearby()", GAME)
        self.assertIn("City.Events.VenueDirector.Ensure()", GAME)

    def test_fisica_non_inquinata(self):
        # I commenti dichiarano l'assenza di fisica: va controllato il codice
        # eseguibile, non il testo dei commenti stessi.
        code = re.sub(r"/\*.*?\*/", "", SIM, flags=re.S)
        code = re.sub(r"///.*|//.*", "", code)
        self.assertNotIn("CharacterController", code)
        self.assertNotIn("Rigidbody", code)
        self.assertNotIn("BoxCollider", code)
        self.assertNotIn("SphereCollider", code)
        self.assertIn("Physics", BUILDER)  # sync esistente conservato
        self.assertIn("Universal Render Pipeline/Lit", MATERIALS)

    def test_audio_poolizzato_e_distanza(self):
        self.assertIn("AudioClip.Create", AUDIO)
        self.assertIn("spatialBlend", AUDIO)
        self.assertIn("maxDistance", AUDIO)
        self.assertIn("SetCrowd", SIM)
        self.assertIn("TickHooves", SIM)


class TestSimulazioni(unittest.TestCase):
    def test_stati_ciclo(self):
        for state in ("Inattivo", "Programmato", "InCorso", "Intervallo", "Terminato"):
            self.assertIn(state, STATE)
        self.assertIn("VenuePhase.Programmato", SIM)
        self.assertIn("VenuePhase.Intervallo", SIM)
        self.assertIn("VenuePhase.Terminato", SIM)
        self.assertIn("sim.Begin()", DIRECTOR)

    def test_partita_e_corsa(self):
        self.assertIn("Kind == VenueKind.Stadio", SIM)
        self.assertIn("_goalsHome", SIM)
        self.assertIn("Secondo tempo", SIM)
        self.assertIn("Classifica:", SIM)
        self.assertIn("VenueEventState.Set", SIM)

    def test_evento_ambientale(self):
        self.assertIn("EnvironmentalEventAppliedEvent", STATE)
        self.assertIn("ApplyWeather", SIM)
        self.assertIn("EnvironmentalEventApplied", SIM)
        self.assertIn("SpeedFactor", SIM)

    def test_culling_attori(self):
        self.assertIn("dist > 210f", SIM)
        self.assertIn("dist < 70f", SIM)
        self.assertIn("Animator", SIM)

    def test_animazione_reale(self):
        # Cavalli: clip legacy del FBX; giocatori: eroe già rigged del progetto.
        self.assertIn('Resources.Load<GameObject>("PlayerHero")', DIRECTOR)
        self.assertIn("Resources.LoadAll<AnimationClip>", DIRECTOR)
        self.assertIn("AddClip", DIRECTOR)
        self.assertIn("clip.legacy = true", DIRECTOR)
        self.assertIn("AttachHorseClips(go)", DIRECTOR)
        self.assertIn("CrossFade", SIM)
        self.assertIn('"Gallop"', SIM)
        self.assertIn('a.Anim.SetFloat("Speed"', SIM)
        # Nessun CharacterController/Rigidbody nel direttore: solo commenti.
        code = re.sub(r"/\*.*?\*/", "", DIRECTOR, flags=re.S)
        code = re.sub(r"///.*|//.*", "", code)
        self.assertNotIn("CharacterController", code)
        self.assertNotIn("Rigidbody", code)


class TestRiflessoUova(unittest.TestCase):
    def test_uova_nei_luoghi_nuovi(self):
        self.assertIn('"stadio→spogliatoio"', DIRECTOR)
        self.assertIn('"ippodromo→box 3"', DIRECTOR)
        self.assertIn('"ippodromo→parade ring"', DIRECTOR)
        self.assertIn("GrantCompanionEgg", DIRECTOR)
        self.assertIn("huntix_venue_eggs_", DIRECTOR)

    def test_bridge_eventi(self):
        self.assertIn('"VenueEventChanged"', IPC)
        self.assertIn('"EnvironmentalEventApplied"', IPC)
        self.assertIn('"VenueEventChanged" ->', BRIDGE)
        self.assertIn('"EnvironmentalEventApplied" ->', BRIDGE)
        self.assertIn("onVenueEvent", ANDROID_SYNC)
        self.assertIn("onEnvironmentalEvent", ANDROID_SYNC)
        self.assertIn("KEY_VENUE_EVENT", ANDROID_SYNC)

    def test_snapshot_non_distruttivo(self):
        self.assertIn("venueEventState", SYNC)
        self.assertIn("VenueEventState.Export()", SYNC)
        self.assertIn("VenueEventState.Import(j.venueEventState)", SYNC)
        self.assertIn("mergeVenueSnapshot", ANDROID_SYNC)
        self.assertIn("venueEventSnapshot", ANDROID_SYNC)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Verifiche statiche dell'integrazione stadio/ippodromo/eventi (25/11/2026).

Il test è statico perché non c'è un device Unity/Android in questa macchina:
controlla che gli asset siano davvero presenti e che sistemi esistenti,
geo/graph, CacheVersion, priorità di interazione e ponte bidirezionale siano
stati conservati. Non dichiara test visivi o di FPS.
"""
import json
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
REGISTRY = read("unity-project/Assets/City/Scripts/Events/VenueRegistry.cs")
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
POI_REGISTRY = read("unity-project/Assets/City/Scripts/Vehicle/VehiclePoiRegistry.cs")
MAP_SELECT = read("unity-project/Assets/City/Scripts/OSM/MapSelectUI.cs")
MINIMAP = read("unity-project/Assets/City/Scripts/OSM/MinimapHud.cs")
COMPASS = read("unity-project/Assets/City/Scripts/Vehicle/CompassUI.cs")
SIGNPOST = read("unity-project/Assets/City/Scripts/Vehicle/PoiSignpost.cs")
GAME_MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
CITY_MAP_ACTIVITY = read(
    "app/src/main/java/com/intelligame/huntix/ui/CityMapActivity.kt")
VENUE_BUILDER = read("scripts/venue_registry_build.py")
VENUES_JSON = json.loads(read(
    "unity-project/Assets/Resources/Venues/italy_venues.json"))
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


class TestGeoreferenziazioneOSM(unittest.TestCase):
    """Gli impianti non sono piu' piazzati per hash del chunk: vengono
    dall'impronta OSM reale, con un solo impianto principale per citta'."""

    def by_osm_id(self, oid):
        for venue in VENUES_JSON["venues"]:
            if venue["osmId"] == oid:
                return venue
        self.fail("OSM id mancante dal registro: " + str(oid))

    def test_stadio_reale_di_foggia(self):
        v = self.by_osm_id(69969260)
        self.assertEqual(v["id"], "stadio-osm-69969260")
        self.assertEqual(v["kind"], "stadio")
        self.assertEqual(v["name"], "Stadio Pino Zaccheria")
        self.assertEqual(v["city"], "Foggia")
        self.assertIn("Via Vincenzo Gioberti", v["address"])
        self.assertIn("71122", v["address"])
        # Incrocio Via Gioberti / Viale Ofanto: il baricentro OSM e' qui.
        self.assertAlmostEqual(v["lat"], 41.4549339, places=5)
        self.assertAlmostEqual(v["lng"], 15.5420975, places=5)
        self.assertGreater(v["spanLatM"], 150.0)
        self.assertGreater(v["spanLonM"], 150.0)

    def test_ippodromo_reale_di_castelluccio(self):
        v = self.by_osm_id(1350956337)
        self.assertEqual(v["id"], "ippodromo-osm-1350956337")
        self.assertEqual(v["kind"], "ippodromo")
        self.assertEqual(v["name"], "Ippodromo dei Sauri")
        self.assertEqual(v["city"], "Castelluccio dei Sauri")
        self.assertIn("Ortanova", v["address"])
        self.assertAlmostEqual(v["lat"], 41.3084701, places=5)
        self.assertAlmostEqual(v["lng"], 15.4539235, places=5)

    def test_un_solo_impianto_per_citta(self):
        groups = {}
        for venue in VENUES_JSON["venues"]:
            key = (venue["kind"], venue["city"].casefold())
            groups.setdefault(key, []).append(venue["id"])
        for key, ids in groups.items():
            self.assertEqual(len(ids), 1, str(key))
        self.assertGreater(len(groups), 100)

    def test_fonte_ODbL_e_schema(self):
        self.assertEqual(VENUES_JSON["schema"], 1)
        self.assertIn("OpenStreetMap contributors", VENUES_JSON["source"])
        self.assertIn("ODbL", VENUES_JSON["source"])
        self.assertIn("un solo impianto", VENUES_JSON["rule"])

    def test_posizionamento_senza_hash(self):
        self.assertIn("VenueRegistry.InChunk(chunk.index)", DIRECTOR)
        self.assertIn("WorldOrigin.ToWorld(spec.lat, spec.lng)", DIRECTOR)
        self.assertIn("s.LocalOffset.x", DIRECTOR)
        self.assertIn("world.y = TileElevation.HeightAtWorld(world)", DIRECTOR)
        self.assertNotIn("pick = (int)(h % 19u)", DIRECTOR)
        self.assertNotIn("chunk.key + (kind ==", DIRECTOR)

    def test_parse_json_tipi_unity_sicuri(self):
        self.assertIn("public int osmId;", REGISTRY)
        self.assertIn("public float lat;", REGISTRY)
        self.assertIn("public float lng;", REGISTRY)
        self.assertIn("lat = r.lat,", REGISTRY)
        self.assertIn("lng = r.lng,", REGISTRY)

    def test_ponte_esporta_solo_impianti_vicini(self):
        self.assertIn("public static string TargetsJson(double centerLat = 0",
                      REGISTRY)
        self.assertIn("string[] kinds = { KindStadio, KindIppodromo };", REGISTRY)
        self.assertIn("City.OSM.WorldOrigin.ToGeo", SYNC)
        self.assertIn("TargetsJson(lat, lng, 8)", SYNC)

    def test_generatore_osm_offline(self):
        self.assertIn("osmium", VENUE_BUILDER)
        self.assertIn("tags-filter", VENUE_BUILDER)
        self.assertIn("w/leisure=stadium", VENUE_BUILDER)
        self.assertIn("sport=horse_racing", VENUE_BUILDER)
        self.assertIn("69969260", VENUE_BUILDER)
        self.assertIn("1350956337", VENUE_BUILDER)
        self.assertNotIn("urllib", VENUE_BUILDER)
        self.assertNotIn("requests", VENUE_BUILDER)


class TestWaypointImpianti(unittest.TestCase):
    def test_poi_registry_include_registro_osm(self):
        self.assertIn("using City.Events;", POI_REGISTRY)
        self.assertIn("foreach (var venue in VenueRegistry.All)", POI_REGISTRY)
        self.assertIn("id = venue.id", POI_REGISTRY)
        self.assertIn('kind = venue.kind', POI_REGISTRY)

    def test_menu_e_colori(self):
        self.assertIn('"casa", "stadio", "ippodromo"', MAP_SELECT)
        self.assertIn("ColStadio", COMPASS)
        self.assertIn("ColIppodromo", COMPASS)
        self.assertIn('case "stadio": return ColStadio;', COMPASS)
        self.assertIn('case "ippodromo": return ColIppodromo;', COMPASS)
        self.assertIn('case "stadio": return "STADIO";', SIGNPOST)
        self.assertIn('case "ippodromo": return "IPPODROMO";', SIGNPOST)

    def test_minimappa_sempre_visibili(self):
        self.assertIn('DrawPoiKind("stadio", true, CompassUI.ColStadio', MINIMAP)
        self.assertIn('DrawPoiKind("ippodromo", true, CompassUI.ColIppodromo', MINIMAP)

    def test_bridge_destinazioni_venue(self):
        self.assertIn("j.venueTargets = BuildVenueTargets();", SYNC)
        self.assertIn("VenueRegistry.TargetsJson(lat, lng, 8)", SYNC)
        self.assertIn("public string venueTargets", SYNC)
        self.assertIn('case "navigateToVenue":', GAME_MANAGER)
        self.assertIn("HandleNavigateToVenue(jsonData)", GAME_MANAGER)
        self.assertIn("City.Events.VenueRegistry.Find", GAME_MANAGER)
        self.assertIn("fun getVenueTargets", ANDROID_SYNC)
        # Stesso formato di houseTargets: JsonUtility scrive una stringa con
        # dentro un JSONArray, non un array annidato.
        self.assertIn('getVenueTargets(context: Context): JSONArray = snapshotArray(context, "venueTargets")',
                      ANDROID_SYNC)
        self.assertNotIn('optJSONArray("venueTargets")', ANDROID_SYNC)
        # VenueRegistry.TargetsJson scrive `..."lng":16.84"}` (virgolette in
        # eccesso dopo il numero): il ponte deve sanitarla prima di parsare.
        self.assertIn('private val strayNumberQuote = Regex(', ANDROID_SYNC)
        self.assertIn('val cleaned = strayNumberQuote.replace(raw, "$1")', ANDROID_SYNC)
        self.assertIn('return try { JSONArray(cleaned) }', ANDROID_SYNC)
        self.assertIn('snapshotArray(context, "venueTargets").length() > 0', ANDROID_SYNC)
        self.assertIn("fun goToVenue", ANDROID_SYNC)
        self.assertIn('"navigateToVenue"', ANDROID_SYNC)
        self.assertIn("report.put(\"venueTargets\"", ANDROID_SYNC)
        self.assertIn('addVenueButton("STADIO', CITY_MAP_ACTIVITY)
        self.assertIn('addVenueButton("IPPODROMO', CITY_MAP_ACTIVITY)


if __name__ == "__main__":
    unittest.main()

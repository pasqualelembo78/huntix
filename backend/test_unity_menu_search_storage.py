"""Invarianti statici di menu snellito, ricerca destinazione e gestione spazio.

I test sono puramente testuali (come gli altri test_unity_*): verificano che
bridge, UI e pulizia selettiva restino collegati senza dover avviare Unity.
"""
import os
import unittest


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as fh:
        return fh.read()


MENU = read("unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs")
MAP = read("unity-project/Assets/City/Scripts/OSM/MapSelectUI.cs")
GAME = read("unity-project/Assets/City/Scripts/Game.cs")
MANAGER = read("unity-project/Assets/Scripts/Core/GameManager.cs")
EXPLORE = read("unity-project/Assets/Scripts/UI/ExploreUIController.cs")
UBRIDGE = read("unity-project/Assets/Scripts/Bridge/UnityBridge.cs")
MINIMAP = read("unity-project/Assets/City/Scripts/OSM/MinimapHud.cs")
STORE = read("app/src/main/java/com/intelligame/huntix/bridge/StoreUnityBridge.kt")
CALLBACK = read("app/src/main/java/com/intelligame/huntix/bridge/UnityAppCallback.kt")
ABRIDGE = read("app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt")
CITYMAP = read("app/src/main/java/com/intelligame/huntix/ui/CityMapActivity.kt")
PACKS = read("app/src/main/java/com/intelligame/huntix/ui/OfflinePacksActivity.kt")


class TestMenuSnellito(unittest.TestCase):
    def test_primo_livello_solo_azioni_di_gioco(self):
        # 11 righe + blocco aiuto: nessuna impostazione resta di primo livello.
        self.assertIn("Lavoro (guadagna \\u20ac)", MENU)
        self.assertIn("Mappa espansa", MENU)
        self.assertIn("Cerca destinazione", MENU)
        self.assertIn("Profilo (Sim)", MENU)
        self.assertIn("Stile edifici: Veloce", MENU)
        self.assertIn("Negozio: Huntix", MENU)
        self.assertIn("Eventi sportivi: Off", MENU)
        self.assertIn("Esci dal gioco", MENU)
        self.assertIn("Chiudi menu", MENU)
        self.assertIn('new MenuEntry("Uova e ricette (3 in 1)"', MENU)
        self.assertIn('new MenuEntry("Impostazioni e strumenti"', MENU)
        # 11 di primo livello + 3 uova/ricette + 15 impostazioni:
        # "Prestazioni adattive" e i due dettagli naturali sono additivi.
        self.assertEqual(MENU.count("new MenuEntry("), 31)

    def test_nessuna_funzione_vecchia_eliminata(self):
        for label in (
            "Fondi le uova (crafting)", "Le tue uova (collezione)",
            "Ricette di fusione (guida)", "Personalizza aspetto", "Note legali",
            "Qualità grafica: Media", "Input touch: Huntix", "Mondo: Reale",
            "Animazione menu: Huntix", "Console QA: Off",
            "Opzioni audit open source: Off", "Compagno NPC: Off",
            "Modalità cinematografica: Off",
            "Dettagli grafica cinematografica",
        ):
            self.assertIn('"' + label + '"', MENU)
        for event in ("CraftRequest", "InventoryViewRequest", "RecipeRequest"):
            self.assertIn(event, MENU)
        self.assertIn("Gestisci spazio", MENU)
        self.assertIn("StorageSettingsRequest", MENU)

    def test_aiuto_poi_nel_menu_e_nella_minimappa(self):
        self.assertIn("COME RAGGIUNGERE I LUOGHI", MENU)
        self.assertIn("TELEPORTA QUI (arrivi davvero) o FRECCETTA", MENU)
        self.assertIn("SOTTERRANEO", MENU)
        self.assertIn("Mappa: CASA · STADIO · IPPODROMO", MINIMAP)
        self.assertIn("Cerca destinazione", MINIMAP)

    def test_sottopannelli_legacy_index_e_chiusura(self):
        self.assertIn("private sealed class MenuEntry", MENU)
        self.assertIn("LegacyIndex", MENU)
        self.assertIn("BindLegacyLabel", MENU)
        self.assertIn('MakeSubPanel("EggPanel"', MENU)
        self.assertIn('MakeSubPanel("SettingsPanel"', MENU)
        self.assertIn("for (int i = 0; i < _subPanels.Count; i++)", MENU)


class TestRicercaDestinazione(unittest.TestCase):
    def test_campo_ricerca_e_apertura_diretta(self):
        self.assertIn("public static void OpenSearch()", MAP)
        self.assertIn("MapSelectUI.OpenSearch();", MENU)
        self.assertIn("private InputField _searchField", MAP)
        self.assertIn("Cerca un POI o una via + città", MAP)
        self.assertIn("public void FocusSearch()", MAP)
        self.assertIn("private void RunSearch()", MAP)
        self.assertIn("_searchField.onEndEdit.AddListener", MAP)
        # Stessa correzione nella barra "Esplora" della modalità outdoor.
        self.assertIn("Cerca un locale", EXPLORE)
        self.assertIn("field.shouldHideMobileInput = true;", EXPLORE)

    def test_registrie_locali_e_geocoding(self):
        self.assertIn("VenueRegistry.All", MAP)
        self.assertIn("VisitInteriorRegistry.All", MAP)
        self.assertIn("VehiclePoiRegistry.All()", MAP)
        self.assertIn("public void MergeSearchResults(string json)", MAP)
        self.assertIn('AddHit(name, "indirizzo", lat, lng)', MAP)
        self.assertIn("huntix_last_searched_destination", GAME)
        self.assertIn("JsonField(row, \"lat\")", MAP)

    def test_teleport_usufruisce_dello_snap_robusto(self):
        self.assertIn("public void TeleportToSearchedPoi(", GAME)
        self.assertIn("cw.Teleport(lat, lng)", GAME)
        self.assertIn("SnapToGroundForTeleport", GAME)
        self.assertNotIn("TileElevation.HeightAtWorld(pos);", MAP)
        self.assertIn("game.TeleportToSearchedPoi(d.name, d.kind, d.lat, d.lng)", MAP)
        self.assertIn("SettleAfterSearchTeleport", GAME)

    def test_riflesso_uova_senza_duplicati(self):
        self.assertIn("huntix_search_egg_", GAME)
        self.assertIn('PlayerPrefs.GetInt(eggKey, 0) == 0', GAME)
        self.assertIn('GrantAuditEgg("ricerca->"', GAME)
        self.assertIn('"Archivista del Goblin"', MANAGER)
        self.assertIn("huntix_space_cleanup_egg", MANAGER)

    def test_bridge_bidirezionale(self):
        self.assertIn("public static void GeocodeAddress(string query)", UBRIDGE)
        self.assertIn('jc.CallStatic("geocodeAddress", query ?? "")', UBRIDGE)
        self.assertIn('case "OnGeocodeResult":', MANAGER)
        self.assertIn('case "openDestinationSearch":', MANAGER)
        for receiver in ("navigateToHouse", "navigateToVenue",
                         "openDestinationSearch", "OnGeocodeResult",
                         "TileCacheCleared"):
            self.assertIn("public void " + receiver + "(string json)", MANAGER)


class TestGestioneSpazio(unittest.TestCase):
    def test_stats_e_pulizia_sono_selettive(self):
        self.assertIn("fun tileCacheStats(): String", STORE)
        self.assertIn("fun clearTileCache(olderThanDays: Int = -1): String", STORE)
        self.assertIn("CityTilePreloader.cacheDir(ctx)", STORE)
        self.assertIn('Regex("""^.+\\.v[1-9]\\.(?:graph|geo)\\.json(?:\\.tmp)?$""")', STORE)
        self.assertIn("NOFOLLOW_LINKS", STORE)
        self.assertIn('.put("legacy", legacy)', STORE)
        self.assertIn('.put("temp", temp)', STORE)
        self.assertNotIn("deleteRecursively", STORE)

    def test_dispatcher_e_evento_di_pulizia(self):
        for token in ('"tileCacheStats"', '"clearTileCache"', '"geocodeAddress"'):
            self.assertIn(token, CALLBACK)
        self.assertIn('UnityIpc.sendToUnity("GameManager", "TileCacheCleared", stats)', STORE)
        self.assertIn("TileClient.TrimPrefetch(0)", MANAGER)
        self.assertIn('case "TileCacheCleared":', MANAGER)

    def test_ui_conferma_esplicita(self):
        self.assertIn("EXTRA_FOCUS_STORAGE", PACKS)
        self.assertIn("Pulisci solo cache tile", PACKS)
        self.assertIn("Cancella solo le tile", PACKS)
        self.assertIn("Resta intatto: profilo, uova, ricette, preferenze", PACKS)
        self.assertIn("tileCacheStats()", PACKS)
        self.assertIn("clearTileCache()", PACKS)
        self.assertIn('"StorageSettingsRequest" ->', ABRIDGE)
        self.assertIn("EXTRA_FOCUS_STORAGE", ABRIDGE)

    def test_chip_ricerca_e_toast_chiarito(self):
        self.assertIn("EXTRA_FOCUS_SEARCH", CITYMAP)
        self.assertIn("CERCA DESTINAZIONE", CITYMAP)
        self.assertIn('EXTRA_SEARCH_QUERY', CITYMAP)
        self.assertIn('UnityIpc.sendToUnity("GameManager", "openDestinationSearch", payload)', CITYMAP)
        self.assertIn("Freccetta impostata", CITYMAP)
        self.assertIn("Per arrivarci davvero premi TELEPORTA", CITYMAP)
        self.assertIn('"DestinationSearchOpen" ->', ABRIDGE)

    def test_ricerca_dedicata_senza_mappa_e_risultati_selezionabili(self):
        # "CERCA DESTINAZIONE" apre una vista tutta schermo: la mappa viene
        # nascosta e la lista dei risultati vive in un pannello disegnato
        # dopo di essa, con righe tappabili e dialogo sopra a tutto.
        self.assertIn("Instance.EnterSearchOnly();", MAP)
        self.assertIn("private GameObject _soPanel;", MAP)
        self.assertIn("private bool _searchOnly;", MAP)
        self.assertIn("private void EnterSearchOnly()", MAP)
        self.assertIn("private void ExitSearchOnly()", MAP)
        self.assertIn("if (_mapGo != null) _mapGo.SetActive(false);", MAP)
        self.assertIn("if (_mapGo != null) _mapGo.SetActive(true);", MAP)
        self.assertIn("BuildSearchOnlyPanel(root.transform);", MAP)
        self.assertIn("BuildAskDialog(root.transform);", MAP)
        self.assertIn("public static void OpenSearch(string query)", MAP)
        self.assertIn("Instance.RunSearch();", MAP)
        self.assertIn("MapSelectUI.OpenSearch(sm.Groups[1].Value);", MANAGER)
        self.assertIn("private InputField ActiveField", MAP)
        self.assertIn("private Text ActiveStatus", MAP)
        self.assertIn("private RectTransform ActiveContent", MAP)
        self.assertIn("item.transform.SetParent(ActiveContent, false);", MAP)
        self.assertIn(
            'string q = ActiveField != null ? ActiveField.text.Trim() : "";', MAP)
        self.assertIn(
            "() => SelectCategoryPoi(d.name, d.kind, d.lat, d.lng)", MAP)
        self.assertIn("if (_searchOnly) return;", MAP)
        # La mappa normale resta invariata: aperta dalla minimappa torna
        # visibile e mostra le categorie come prima.
        self.assertIn("ExitSearchOnly();", MAP)
        self.assertIn("ShowCategories();", MAP)

    def test_risultati_ricerca_visibili_e_contrasto(self):
        # Il QA 26/11 ha segnalato lo stato "trovati N luoghi" senza righe
        # visibili. Le righe usano una maschera senza alpha, testo bianco su
        # sfondo pieno e vengono scritte in entrambe le viste: se il bridge
        # geocoding arriva durante un cambio vista i risultati non spariscono.
        self.assertIn("EventSystem.current.SetSelectedGameObject(field.gameObject);", MAP)
        # Il QA 26/11 ha segnalato "non riesco a scrivere": i campi creati a
        # runtime avevano shouldHideMobileInput=false (default uGUI), quindi su
        # Android il testo restava nella tastiera nativa e non compariva nel
        # campo Unity. Ora i due campi della ricerca mostrano caret e caratteri.
        self.assertIn("_searchField.shouldHideMobileInput = true;", MAP)
        self.assertIn("_soField.shouldHideMobileInput = true;", MAP)
        # L'attivazione resta esplicita e preliminare alla selezione: e' lei a
        # richiedere il TouchScreenKeyboard; la selezione successiva mantiene
        # il campo attivo nei tap seguenti senza aprire una seconda tastiera.
        activation = MAP.index("field.ActivateInputField();")
        selection = MAP.index(
            "EventSystem.current.SetSelectedGameObject(field.gameObject);",
            activation)
        self.assertLess(activation, selection)
        self.assertNotIn("field.Select();", MAP)
        self.assertIn("private void EnsureMapEventSystem()", MAP)
        self.assertIn("GetComponent<BaseInputModule>() == null", MAP)
        self.assertIn('OsmDiag.Log("[MapSelect][Focus]', MAP)
        self.assertIn("listGo.AddComponent<RectMask2D>();", MAP)
        self.assertIn("private void AddSearchResultRow(", MAP)
        self.assertIn("private void AddSearchRowTo(", MAP)
        self.assertIn("item.GetComponent<Image>().color = ColResultHi;", MAP)
        self.assertIn('NewText(item.transform, "L", 25, Color.white)', MAP)
        self.assertIn('OsmDiag.Log("[MapSelect][Search] righe="', MAP)

    def test_geocoding_pacato_e_gestito(self):
        self.assertIn("nominatim.openstreetmap.org/search", STORE)
        self.assertIn("GEOCODE_MIN_INTERVAL_MS", STORE)
        self.assertIn("User-Agent", STORE)
        self.assertIn('.put("ok", true)', STORE)
        self.assertIn('.put("ok", false)', STORE)
        self.assertIn('UnityIpc.sendToUnity("GameManager", "OnGeocodeResult", methodResult)', STORE)


if __name__ == "__main__":
    unittest.main(verbosity=2)

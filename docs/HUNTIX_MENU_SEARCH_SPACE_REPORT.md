# Report — Menu essenziale, ricerca destinazione e gestione spazio (25/11/2026)

Backup: tag **`pre-menu-search-space`** (pushato su `origin`), creato prima di
qualsiasi modifica. Nessuna voce del menu è stata eliminata: l'elenco esatto di
accorpamenti/spostamenti è stato approvato esplicitamente dal proprietario.

## 1. Menu hamburger: da 22 voci a 11

Primo livello (solo ciò che serve durante il gioco):

`Lavoro (guadagna €)` · `Mappa espansa` · `🔍 Cerca destinazione` ·
`Profilo (Sim)` · `Stile edifici` · `Negozio: Huntix` ·
`Eventi sportivi` · `Uova e ricette (3 in 1)` ·
`Impostazioni e strumenti` · `Esci dal gioco` · `Chiudi menu`.

- **Uova e ricette** apre un sotto-pannello con i tre pulsanti storici
  (`CraftRequest`, `InventoryViewRequest`, `RecipeRequest`).
- **Impostazioni e strumenti** apre un sotto-pannello con le 11 voci storiche
  (aspetto, note legali, qualità, input touch, mondo, animazione menu, console
  QA, audit open source, compagno NPC, cinematografica, dettagli grafica) più
  la nuova voce **💾 Gestisci spazio**.
- `MenuEntry.LegacyIndex` conserva l'indice storico usato dai toggle e dai test:
  nessuna preferenza o formato di salvataggio è cambiato.
- Nel pannello compare un blocco fisso **COME RAGGIUNGERE I LUOGHI** con il
  percorso operativo; la minimappa mostra
  `Mappa: CASA · STADIO · IPPODROMO · 🔍 Cerca destinazione`.

## 2. POI: come si raggiungono davvero

Percorso operativo (testi in italiano):

1. Menu hamburger → **Mappa espansa**.
2. Menu ☰ della mappa → **CASA**, **STADIO**, **IPPODROMO**, **SOTTERRANEO**.
3. Tocca il risultato → **TELEPORTA QUI** (arrivo reale) o **FRECCETTA**
   (solo bussola).

I "sottopassaggi" restano le rampe sintetiche etichettate `SOTTERRANEO`: non
sono ingressi verificati, e il testo del menu lo dichiara. I pulsanti Android
`CASA`/`STADIO`/`IPPODROMO` impostano solo la freccetta: i toast ora lo dicono
esplicitamente e invitano a premere TELEPORTA nella mappa di MiCittà.

Correzione scoperta durante l'integrazione: Android inviava
`UnitySendMessage("GameManager", "navigateToHouse"/"navigateToVenue", …)` ma
`GameManager` esponeva solo `OnEvent`, quindi gli eventi cadevano nel vuoto.
Sono stati aggiunti i ricevitori pubblici (anche per i tre nuovi eventi) che
confluiscono nello switch storico senza duplicare logica.

## 3. Ricerca destinazione

- **Fase A — POI noti**: campo di ricerca nella mappa espansa; incrocia
  `VenueRegistry` (stadi/ippodromi OSM), `VisitInteriorRegistry` (case
  visitabili), `VehiclePoiRegistry` (officine, concessionarie, rampe…).
  Ordinamento per distanza, max 14 risultati, deduplicazione.
- **Fase B — via + città**: Android interroga Nominatim
  (`format=jsonv2&limit=3`) su thread di background, con User-Agent esplicito,
  timeout 10/15 s, pacing minimo 1,1 s e cache in memoria 10 min/24 voci.
  La risposta torna sempre a Unity come `OnGeocodeResult` (anche in caso di
  errore o query vuota) e si unisce ai risultati locali.
- Teleport robusto: `Game.TeleportToSearchedPoi` reinizializza l'origine sul
  POI (`CityChunkedWorld.Teleport`, prefetch del settore), usa
  `TryGroundColumn` (mai l'hit più alto, mai proxy altimetrico) e ri-aggancia
  il player dopo il fade, quando i chunk sono arrivati.
- Snapshot `huntix_last_searched_destination` e log Android `Ricerca`:
  nessun formato nuovo, solo il canale bridge esistente.
- Dal chip Android `🔍 CERCA DESTINAZIONE` arriva `openDestinationSearch`:
  Unity apre la mappa espansa col campo già selezionato, senza nuova Activity.

## 4. Il ">1 GB" e la pulizia selettiva

Colpevole: `Android/data/com.intelligame.huntix/files/huntix_tiles`, file
`{key}.v5.{graph,geo}.json` **non compressi**, senza cap/eviction lato client.
Il pulsante Android "Cancella cache" svuotava `/cache`, non quella cartella:
per questo l'unico rimedio era "Cancella dati", che distruggeva profilo e uova.

Nuova schermata **💾 Gestisci spazio** (in `OfflinePacksActivity`):

- mostra byte, numero file, versioni correnti v5, vecchie v1-v4 e `.tmp`;
- **Pulisci solo cache tile** cancella *solo* i file regolari che matchano
  `*.v[1-9].{graph,geo}.json(.tmp)` dentro `files/huntix_tiles`;
- conferma esplicita con elenco di ciò che viene cancellato e di ciò che resta;
- dopo la pulizia invia `TileCacheCleared` a Unity, che svuota il prefetch in
  memoria (`TileClient.TrimPrefetch(0)`);
- **non tocca** profilo, uova, ricette, preferenze, snapshot, log o pacchetti
  offline. Non è stata introdotta alcuna cancellazione automatica.

## 5. Riflesso della caccia alle uova

- **Uovo della ricerca**: alla prima visita a una destinazione trovata dalla
  ricerca (`ricerca→<kind>→<nome>`), piazzato dopo il fade e con chiave
  `huntix_search_egg_*` per evitare duplicati.
- **Archivista del Goblin**: alla prima pulizia spazio completata, con chiave
  `huntix_space_cleanup_egg` una sola volta.
- Nessun premio duplicato: l'uovo proposto "Cacciatore di Indirizzi" non è
  stato aggiunto perché la stessa arrivo-da-ricerca è già coperta.
- **Uovo della metropolitana** (filone Foggia successivo): prima entrata in
  una rampa Metro, chiave `huntix_metro_egg_*`, luogo testuale + coordinate nel
  solito canale `FeatureEggHidden`. Dettagli in
  `docs/HUNTIX_CASE_VISITABILI_REPORT.md` §21.3.

## 6. Bridge e verifiche

| Direzione | Evento | Uso |
|---|---|---|
| Unity → Android | `StorageSettingsRequest` | apre Gestisci spazio |
| Unity → Android | `geocodeAddress` | ricerca via + città |
| Android → Unity | `OnGeocodeResult` | risultati/errore Nominatim |
| Android → Unity | `openDestinationSearch` | apre la ricerca nella mappa |
| Android → Unity | `TileCacheCleared` | svuota prefetch + uovo |

Verifiche della prima pubblicazione: `cscheck` **OK**; 289 test Python OK;
build riuscita in 4m17s, APK SHA-256 `a434d1bf…`.

## 7. Fix 26/11: risultati di ricerca coperti dalla mappa

**Causa**: il canvas della mappa espansa (`Root`, sorting order 100) veniva
disegnato *dopo* il pannello di ricerca e la sua `RawImage` copriva l'elenco:
la query "Foggia" restituiva 8 risultati ma le righe non erano visibili né
tappabili (riprodotto su emulator `emulator-5556`: OCR mostrava solo
`MENU POI DELLA CITTA'`).

**Fix** (`MapSelectUI.cs`): modalità dedicata `search-only`. Dal chip Android
`🔍 CERCA DESTINAZIONE` la mappa viene nascosta (`_mapGo.SetActive(false)`) e
viene creato un pannello a schermo intero separato; il dialogo di conferma
viene spostato sotto la root del pannello di ricerca, così `TELEPORTA QUI` e la
freccetta restano sopra i risultati. `FocusSearch`/`RunSearch`/`ShowSearchResults`
scrivono nei riferimenti attivi (`ActiveField`/`ActiveStatus`/`ActiveContent`),
quindi la ricerca normale dentro la mappa continua a funzionare. Uscita(conferma)
ripristina la mappa. Nessuna funzione o voce rimossa, Roma intatta.

Verifiche dopo il fix: **296 test Python OK**; `./check.sh` (`HUNTIX_FULL`)
**CSCHECK OK (215 warning)**. Il comportamento visivo è stato riprodotto e
verificato su emulator Android (`CASA`, `STADIO`, `IPPODROMO` e ricerca). Dati
build finali e SHA-256 in calce al processo di pubblicazione.

---

## Estensione 2026-11-26 — Restyling condiviso e prestazioni adattive

Dopo il fix della ricerca e' stato esteso a tutta l'app un tema coerente e un
livello di ottimizzazione **additivo**, senza rimuovere nessuna funzione ne'
voce di menu.

- **Tema Android**: token semantici in `res/values/colors.xml` (`huntix_*`,
  valori visivi identici ai vecchi hardcoded) e helper centralizzati in
  `UiKit.kt` (`themed`, `topBar`, `backButton`, `chip`). Applicati a
  `PlayerProfileActivity`, `OfflinePacksActivity`, `ShopActivity` e alla status
  bar. Il resto delle schermate continua a usare i propri colori storici:
  nessun comportamento o navigazione e' stato cambiato.
- **Profilo prestazioni** (`AdaptivePerformanceProfile.cs`): tier Low/Medium/
  High derivato da RAM, qualita' salvata e `PerformanceGovernor`. Regola solo
  budget build per frame, prefetch tile, densita' veicoli e ombre dei palazzi
  LOD. **Intatti** `LoadRadius=3`, `UnloadRadius=4`, `TerrainColliderLod=0`,
  passo mesh/DEM, kit edifici, `CarsPerTile=22`, `MaxCars=180`,
  `TileClient.CacheVersion=5`. Nuova voce menu "Prestazioni adattive", senza
  sostituire la qualita' grafica storica.
- **Riflesso uova**: un solo uovo dedicato
  (`performance->adattivo->recupero`) con posizione testuale e coordinate,
  concesso solo al primo miglioramento; nessun duplicato rispetto al profilo
  qualita' gia' esistente.
- **Bridge bidirezionale**: Unity invia `CityPerfTierChanged` (Android specchia
  tier e toggle in `huntix_prefs`); Android rimanda `adaptivePerformance`
  dentro `setMode` e Unity lo applica come ripristino passivo (`ApplyRestored`),
  senza uova ne' eventi di ritorno.
- **Micro-ottimizzazioni Android**: `UnityBridgeService` riutilizza un solo
  `mainHandler`; `OutdoorManager.stop()` annulla i callback della camera.
- **UI open source**: FancyScrollView (MIT) importato con licenza e usato via
  `EasingCore` per lo scorrimento morbido dello shop; attribuzione in
  `THIRD_PARTY_NOTICES.md` e nel README della libreria.

Verifiche statiche dopo queste modifiche: **307 test Python OK** (11 nuovi in
`backend/test_unity_adaptive_performance.py`), `./check.sh` **CSCHECK OK** e
`:app:compileReleaseKotlin`/`:app:compileReleaseJavaWithJavac` **BUILD
SUCCESSFUL**. La build release e il collaudo su device sono documentati nel
processo di pubblicazione in calce.

### Pubblicazione e collaudo 26/11/2026

- Build: `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 SKIP_MAP_UPDATE=1 ./build_release.sh --auto`,
  AAR Unity ricreato, **BUILD SUCCESSFUL** (6m 38s export + 6m 55s app).
- APK: `app/build/outputs/apk/release/app-release-signed.apk`, **204853873 byte**,
  SHA-256 `b6e90748b95d7b726e445f874801c1e1b7940d542ebca8e849df1d8e33203ec6`,
  firma valida (`CN=Huntix`, certificato SHA-256
  `7089056d70807fb282a9d997f78c4f1b037c885b862f79c6531b8a8c5dd2239c`).
- Primo tentativo di build interrotto da un errore reale Unity non coperto dagli
  stub (`OsmDiag` non qualificato in `AdaptivePerformanceProfile`): corretto con
  `City.OSM.OsmDiag`, poi build verde. Churn della build (`link.xml.meta`,
  `PlayerLocomotion.controller`, `PlayerHero.prefab`) ripristinato.
- Collaudo su `emulator-5556`: APK reinstallato; home e profilo raggiungibili,
  MiCittà caricata con menu ☰. La nuova voce **“Prestazioni adattive: Attive
  (Low)”** è presente e mostra il tier derivato dalla RAM emulata. Dal menu,
  **“Cerca destinazione”** apre il pannello a schermo intero (`CERCA
  DESTINAZIONE`, campo e risultati), con dialogo `TELEPORTA QUI` e `CHIUDI`
  sopra l'elenco: il bug della mappa sovrapposta non si riproduce.
- Nota emulator: durante la digitazione da soft keyboard Unity InputSystem ha
  loggato una `NullReferenceException` interna (`FireStateChangeNotifications`,
  stack interamente in `UnityEngine.InputSystem`), con overlay di log a schermo.
  Non riguarda i file modificati in questa sessione (nessun innesto InputSystem
  nuovo); da verificare su device fisico con tastiera reale. La ricerca ha
  comunque prodotto risultati e dialogo di conferma.

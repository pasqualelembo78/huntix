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

Verifiche: `cscheck` **OK**; `python3 -m unittest discover -s backend`
**289 test OK**. Build release con `SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1
./build_release.sh --auto`: **riuscita** in 4m17s (`BUILD SUCCESSFUL`), APK
firmato e verificato in `app/build/outputs/apk/release/app-release-signed.apk`
(**204.792.433 byte**, SHA-256
`a434d1bf9af25f743f0eaaf8c89b52e9c746adf04c1abaf0843af8dfcf917f43`).

Limite: non c'è un device/emulatore Android in questo ambiente, quindi il
comportamento visivo (tap, tastiera, FPS, RAM reale) non è verificabile qui;
sono stati verificati codice, compilazione e test statici.

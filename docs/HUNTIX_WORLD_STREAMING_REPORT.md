# World Streaming additivo di MiCittà — report

Data: 25/11/2026. Universo: un solo gioco/APK Huntix (Unity + Android), UI e
messaggi in italiano. Backup prima dei lavori: tag `pre-world-streaming` sul
commit `8150407a` (offline su richiesta, build firmata).

## Obiettivo e scelta architetturale

Caricare e scaricare il mondo **in automatico** senza riscrivere il sistema
chunk esistente. Il settore è **una tile OSM** (10×10 chunk da 1 km): riutilizza
la griglia `CityGrid`, la cache v5 di `TileClient`, i grafi e i pacchetti offline
già funzionanti.

Lo streaming è **additivo**: `ChunkManager` resta il proprietario dei 49 chunk
(`LoadRadius=3`, `UnloadRadius=4`) e del tick di costruzione; il nuovo
`WorldStreamingManager` viene agganciato dal suo `Start()` sullo stesso
GameObject, senza modificare scene, prefab o sistemi esistenti. Non è stata
fatta alcuna migrazione Addressables: nel progetto gli Addressables sono usati
solo per asset non correlati alle tile OSM, quindi spostarle avrebbe rotto la
cache e il preloader senza guadagni.

## Cosa è stato aggiunto

### Unity (`unity-project/Assets/City/Scripts/OSM/`)

- `WorldStreamingCatalog.cs` + `Assets/Resources/world_sectors.json`: 3451
  settori generati da `backend/preprocessing/world_sector_catalog.py` a partire
  da `tiles/index.json`. Ogni record ha `key`, centro, bbox, numero edifici e
  byte: non contiene cartografia e non richiede rete.
- `WorldStreamingManager.cs`: rileva il settore corrente dalla posizione del
  player, notifica Android, avvia prefetch, rileva teleport (salto ≥6 chunk),
  aggiorna skyline/culling, riduce il budget su device low-memory e compatta le
  risorse con `Resources.UnloadUnusedAssets()` solo dopo un teleport (con
  cooldown di 20 s).
- `WorldStreamingSkyline.cs`: un solo GameObject, una mesh e un materiale
  condiviso per settore lontano; zero collider, zero luci, zero prefab, zero
  edifici giocabili. Se la geo è in cache usa i suoi edifici veri (campionati),
  altrimenti genera una silhouette deterministica dal catalogo. Segue il
  floating origin via `OnRebased`.
- `WorldStreamingBridge.cs`: ponte Unity→Android (`WorldStreamingReady`,
  `WorldSectorChanged`) e Android→Unity (`getWorldStreamingStatus`,
  `requestWorldSector`, `getDeviceMemoryInfo`), più il riflesso uova.
- `ChunkManager.cs`: solo un hook in `Start()` e un hint di direzione
  (`SetDirectionalHint`) che dà priorità ai chunk davanti al player con un bonus
  di 3 celle; senza hint il comportamento resta “più vicino prima”.
- `TileClient.cs`: prefetch **solo da cache disco/memoria** su thread di
  background, LRU di 8 file (4 tile graph+geo), `TrimPrefetch` per la memoria
  bassa. Nessun `UnityWebRequest` in questo percorso: la rete resta una scelta
  dell'utente nei pacchetti offline.
- `CityChunkedWorld.cs`: `Teleport(lat, lng)` avvisa lo streaming **prima** di
  `UnloadAll()`, così la tile di destinazione viene preparata in anticipo.
- `GameManager.cs`: callback `OnWorldStreamingStatus(string json)` dal processo
  Android.

### Android (`app/src/main/java/com/intelligame/huntix/bridge/`)

- `UnityIpc.kt`: `WorldStreamingReady` e `WorldSectorChanged` aggiunti agli
  eventi instradati al processo app.
- `UnityAppCallback.kt`: dispatcher dei tre metodi Unity→Android.
- `StoreUnityBridge.kt`: metodi statici richiamabili da Unity, con forwarding
  IPC quando Unity gira in `:unity`.
- `WorldStreamingMirror.kt`: pref **dedicati** `world_game_prefs`:
  `world_streaming_visited_v1` (visite, coordinate, chunk, teleport, timestamp),
  `world_streaming_requested` (richieste di prefetch, max 32) e
  `world_streaming_ready_v1` (catalogo/cache v5).stato rimandato a Unity con
  `OnWorldStreamingStatus`. `getDeviceMemoryInfo` usa `ActivityManager` e
  ritorna `low=true` quando la memoria disponibile è sotto soglia.
- `Bridge.kt`: router dei due eventi verso lo specchio Android.

### Riflesso della caccia alle uova

Il gioco è una caccia alle uova: ogni nuovo sistema deve avere il suo riflesso.
Il primo ingresso in un settore piazza **“Esploratore del Settore”**
(`world_sector_discovered`) tramite `Game.GrantAuditEgg(...)`, quindi sul canale
canonico `FeatureEggHidden` → `Bridge.handleFeatureEggHidden`, che registra su
Android `feature`, `rarity`, `lat`, `lng` e timestamp. Nessun premio duplicato:
il flag `_eggGranted` è una tantum per sessione e la cattura resta quella
canonica del gioco.

## Invarianti preservati

- `TileClient.CacheVersion = 5`, `ChunkManager.LoadRadius = 3`,
  `UnloadRadius = 4`, `ChunkData.TerrainColliderLod = 0`.
- Mesh terreno a 45 m, DEM a 250 m: lo streaming non tocca terreno, strade,
  snap altimetrico (`TryGroundColumn`) né floating origin.
- Nessun “hit più alto” per il terreno e nessun proxy altimetrico dagli edifici.
- Nessun accesso a profilo, inventario, valuta, veicoli, proprietà o
  `CityStateSync` nei file dello streaming: scaricare un settore non può
  cancellare dati persistenti.
- Nessun download automatico dal percorso streaming: il prefetch legge solo la
  cache v5; se la tile non c'è, resta visibile solo lo skyline.

## Verifiche eseguite

- `cd backend/preprocessing/cscheck && ./check.sh`: **CSCHECK OK** (compilazione
  di tutti gli script City con mcs + stub; aggiunti gli stub per
  `Resources.UnloadUnusedAssets`, `UnityEngine.AsyncOperation` e
  `Vector2Int.zero`).
- `cd backend && python3 -m unittest discover`: **275 test OK**, di cui 17
  nuovi in `backend/test_unity_world_streaming.py` (invarianti mondo, catalogo,
  prefetch cache-only, teleport prima dello scarico, culling/skyline senza
  collider, ponte bidirezionale, pref dedicati, uovo).
- Build release: eseguita con `SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1
  ./build_release.sh --auto`; esito e SHA nel paragrafo finale.

## Limitazioni dichiarate

- Non c'è device o emulatore Android in questa macchina: **non sono stati
  verificati aspetto visivo, FPS, RAM reale, teleport visivo né LOD percepito**.
  Le verifiche sono statiche e di compilazione/build.
- Lo skyline è una silhouette schematica (scatole), non una ricostruzione
  fotorealistica: serve a non lasciare il vuoto oltre il raggio dei chunk.
- I settori non ancora scaricati restano visibili ma non esplorabili, come
  richiesto; per esplorarli serve il download esplicito dei pacchetti offline.

## Estensione con nuovi quartieri/settori

1. Aggiornare i tile/POI con i comandi convenzionali (`aggiorna città/regione/
   nazione`).
2. Rigenerare il catalogo: `python3 backend/preprocessing/world_sector_catalog.py`
   (aggiorna `Assets/Resources/world_sectors.json` e il suo `.meta`).
3. Non serve toccare il codice: le nuove tile diventano settori riconosciuti,
   con skyline e prefetch automatici. I numeri `SkylineRing`,
   `MaxSkylineSectors`, `MaxSkylineBuildings` in `WorldStreamingManager.cs`
   sono le leve per cambiare la distanza/qualità dello skyline; `LowMemSkylineBuildings`
   è il budget sui device con poca memoria.

## Esito build

`SKIP_MAP_UPDATE=1 BUILD_UNITY_AAR=1 ./build_release.sh --auto`: **BUILD
SUCCESSFUL** (export Unity + Il2Cpp 4m33s, APK 4m22s). APK firmato
`app/build/outputs/apk/release/app-release-signed.apk`, 204.771.953 byte,
SHA-256 `bdbda48ca0b4559b3a756ad33a838854ffc39828725225347d2d8dc844344267`.
Gli asset riserializzati dalla build non attinenti al streaming (`link.xml.meta`,
`PlayerLocomotion.controller`, `PlayerHero.prefab`) sono stati ripristinati.

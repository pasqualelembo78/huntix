# AGENTS.md

> [!IMPORTANT]
> **LEGGERE SEMPRE, PRIMA DI QUALSIASI RICHIESTA:** il file
> **`HUNTIX_MANDATO.md`** (nella stessa cartella di questo file) è la
> Costituzione del gioco: HUNTIX è una **caccia alle uova AR**, universo unico
> Unity+Android, con **bridge bidirezionale obbligatorio** da/per Android per
> ogni componente Unity e il **"Riflesso della caccia alle uova"** (ogni nuova
> funzionalità va proposta con la sua integrazione uova). In caso di conflitto,
> il Mandato prevale sul resto di questo file.

## Repository
- Root: `/root/giochi/huntix` (git). Backend preprocessing in `backend/preprocessing`,
  tile/POI server in `backend/traffic/tile_server.py`, app Android in `app/`,
  Unity in `unity-project/`.
- UI/messaggi in italiano. `./check.sh` in `backend/preprocessing/cscheck` (modalità
  completa con `-define:HUNTIX_FULL`) — mai usare gli stub per il progetto Unity.

## Comandi convenzionali (frasi dell'utente → azione)
- **"aggiorna tile italia"** (o "fai l'aggiornamento completo dei POI") → esegui:
  `cd backend/preprocessing && nohup ./aggiorna_italia.sh > /tmp/aggiorna_italia.log 2>&1 &`
  **Incrementale di default** (così la build non rifà tutto a ogni giro, pochi
  secondi se è tutto a posto): scarica il dump OSM **solo se manca**, ri-estrae
  i PBF filtrati **solo se la configurazione `FILTERS` è cambiata** (o un
  filtrato è mancante/troncato) e rigenera **solo le geo mancanti/obsolete**
  (`geo-todo` su `ver` vecchio o dati sorgente più recenti, 4 worker,
  `gen-tile --skip-graph --no-index`, grafi stradali intatti). Variante forzata
  (riscarica se obsoleto >12h con `HUNTIX_STALE_HOURS`, ri-estrazione e tutte
  le geo): `./aggiorna_italia.sh refresh`. Alla fine sconsigliare/eseguire
  `curl -X POST <server>/api/tiles/cache/clear`.
  File chiave: `aggiorna_italia.sh`, `aggiorna_puglia.sh`, `puglia_keys.txt`.
- **"aggiorna tile puglia"** → stesso flusso ma con `aggiorna_puglia.sh` (solo 412 tile di terra).
- **"aggiorna regione <nome>"** (anche "aggiorna tile della regione <nome>" o
  "aggiorna la regione <nome>", es. molise, lombardia, sicilia...) → esegui
  `nohup ./aggiorna_regione.sh <nome> > /tmp/aggiorna_regione.log 2>&1 &`; seleziona le
  tile di terra per centroide dal bbox in `osm_regions.py` (tutte le 20 regioni;
  puglia molise verificati, gli altri bbox generosi/indicativi), rigenera le geo
  mancanti, ricostruisce `index.json`. Endpoint opzionale: `<server>/api/tiles/cache/clear`.
- **"aggiorna città <nome>"** (anche "aggiorna tile della città <nome>" o "aggiorna
  la città <nome>", es. foggia, bari, roma...) → esegui
  `nohup ./aggiorna_citta.sh <nome> > /tmp/aggiorna_citta.log 2>&1 &`; valida la città
  via Nominatim (deve essere un luogo in Italia, altrimenti "CITTÀ NON TROVATA IN
  ITALIA" e uscita senza errori), seleziona le tile di terra dal bbox restituito,
  rigenera le geo mancanti (`--skip-graph`, grafi intatti) e ricostruisce `index.json`.
- **"aggiorna mappa"** (o "aggiorna le mappe") → driver interattivo
  `./aggiorna_mappa.sh`: prima chiede l'area Italia (tutta italia / regione / città),
  poi l'area Mondo (intero mondo / una nazione). Autopilota non interattivo:
  `./aggiorna_mappa.sh italia | regione <slug> | citta <nome> | mondo | nazione <slug|nome>`.
  Tutte le operazioni sono idempotenti.
- **build_release.sh** → all'avvio, PRIMA della creazione AAR Unity, chiede
  "Vuoi aggiornare la mappa?" (sì → `aggiorna_mappa.sh` interattivo +
  `POST /api/tiles/cache/clear` se il server è attivo; invio → prosegue).
  Skip per build automatiche: `SKIP_MAP_UPDATE=1`.
- **"aggiorna nazione <slug>"** (anche "aggiorna il paese <slug>", es. germania,
  francia, spagna, austria, portogallo, usa...) → esegui l'autopilota
  `nohup ./mondo_tile.sh <slug> > /tmp/mondo_tile.log 2>&1 &`. In un colpo solo:
  scarica il dump Geofabrik se manca, `filter` (idempotente), `land_keys`, genera i
  **grafi stradali delle tile che non li hanno ancora** (`gen-tile` completo) e le
  geo mancanti (`--skip-graph`), ricostruisce `index.json`. **Idempotente**: se
  l'aggiornamento è già completo non scarica/rifa nulla, dice "tutto a posto" e
  ricostruisce solo `index.json`. Variante forzata (scarica se obsoleto >12h con
  `HUNTIX_STALE_HOURS`, ri-estrazione e ridà tutte le geo): `./mondo_tile.sh refresh <slug>`.
  Con bbox per una regione/distretto (una regione alla volta):
  `./mondo_tile.sh <slug> "latmin,lonmin,latmax,lonmax"`.
  Prep separata (opzionale): `./mondo_prepara.sh <slug> [--refresh] [--clean]`;
  elenco nazioni: `./mondo_prepara.sh --list`. NB: `world_countries.json` ha bbox
  generici; ordine = primo match sul centro tile (italy ha bbox stretto reale; tile
  al confine fra paesi esteri possono cadere nel vicino: irrilevante perché le geo
  vengono generate ai batch per ogni nazione).
- **"aggiorna tile regione estera <slug> <latmin,lonmin,latmax,lonmax>"** → esegui
  `nohup ./mondo_tile.sh <slug> "<bbox>" > /tmp/mondo_tile.log 2>&1 &`.

## Elevazione DEM (SRTM)
- La griglia altimetrica viene **cotta sul file geo alla generazione** (`_bake_dem` in
  `osm_italy_processor.py`): la `{key}_geo.json.gz` diventa autosufficiente con
  `ele`/`ele_nrow`/`ele_ncol`. Ordine fonti in `dem.py`: cache `dem_cache/{key}.json`
  → SRTM HGT locale (`HUNTIX_SRTM_DIR`, file `N45E007.hgt[.zip]`, coverage 60°N..60°S,
  void −32768→0, bilineare) → API OpenTopoData (pacing).
  **Regola: nei batch `gen-tile` mai rete**: si usa `dem.bake_offline_grid` (solo
  cache+HGT); se l'HGT manca la geo resta senza `ele` e lo provvede il backfill.
- **Griglia DEM: passo e allineamento (2026-09-30, fix del ramo `altitudine`).**
  `HUNTIX_DEM_SPACING_M` default **250 m** (era 1000) e i passi per riga/colonna
  sono arrotondati per eccesso a multipli di `HUNTIX_DEM_ALIGN_STEPS` (default 10,
  cioè i chunk da ~1 km del client). Risultato: **5x5 celle DEM per chunk**, con
  i nodi che cadono esattamente sui confini dei chunk. Con 1000 m una cella DEM
  coincideva con un chunk intero, quindi ogni chunk era un piano unico separato
  dagli altri da una **piega secca** al confine e la città era una scala di
  rampe da 1 km. Non alzare oltre 250 m senza rifare questa verifica.
- **Void SRTM: col valore valido più vicino, MAI 0.** I void (`−32768`, mare) non
  si scrivono più come 0 (= livello del mare): su ogni tile costiera quello
  produceva una scogliera artificiale fra città e mare (Bari: 129 celle su 132 a
  0.0 nella griglia vecchia, che al centro città dava 18 m anziché ~10 m reali).
  `dem.fill_voids` propaga il valore valido più vicino (BFS multi-sorgente): il
  mare aperto resta ~0 (il vicino è la costa), l'entroterra resta continuo.
  Sotto `MIN_VALID_RATIO` (0.25) di campioni validi la griglia **non viene
  prodotta** (tile senza `ele` → il client la tratta piatta): meglio piatto che
  inventato.
- La cache `dem_cache/{key}.json` è valida solo se la **firma** (versione formato,
  passo, allineamento, forma, bbox) coincide: cambiando `HUNTIX_DEM_SPACING_M` le
  griglie vecchie vengono ricalcolate invece di riusate in silenzio.
- Per **rigenerare le geo già scritte** con una griglia diversa dal formato
  corrente (cambio di passo/allineamento/void): `python3 dem_warm.py --regrid`
  (senza `--regrid` salta le tile che hanno già `ele`).
- Scaricare le celle SRTM locali per l'Italia: `./srtm_download.sh` (default bbox
  italia N35..N47 × E006..E018, idempotente, 4 download paralleli, le celle di mare
  danno 404 e si saltano; bbox custom: `./srtm_download.sh "latmin,lonmin,latmax,lonmax"`).
  `dem_warm.sh` avvisa se `srtm/` locale è vuota/inesistente ma prosegue con la rete.
- Backfill idempotente delle tile senza `ele` (rete ammessa come fallback, con
  pacing gentile): `./dem_warm.sh` (o `dem_warm.py --limit N --sleep 0.5 --dry-run`);
  budget per esecuzione: env `HUNTIX_DEM_WARM_TILES`. Gli `aggiorna_*` e `mondo_tile.sh`
  chiamano `dem_warm.sh` da soli a fine corsa.
- Serve il DEM reale anche per singole tile: `python3 -m preprocessing.dem <key>` via
  `elevation_grid` (cache→HGT→API). Disattivare il bake: `HUNTIX_DEM_BAKE=0`.
- Server: `_inject_dem` in `tile_server.py` usa `dem.elevation_grid` per le geo legacy
  senza `ele`; il caching negativo ora ha TTL (env `HUNTIX_ENRICH_TTL_S`, default 60s)
  così una tile piatta per un rate-limit viene ritestata, non cachata per sempre.

## Note operazioni tile/POI
- Tile: griglia 1 IIS; `tile_key(lat,lon)` in `osm_italy_processor.py`; le geo sono
  on-demand via `gen-tile --skip-graph` e cache su disco `tiles/{key}_geo.json.gz`
  (cap 3 GB, env `HUNTIX_GEO_CACHE_GB`). Le tile di mare non producono file.
- **Versione formato geo**: ogni `{key}_geo.json.gz` porta `ver`
  (`GEO_FORMAT_BASE` del processore + 8 char SHA1 dei `FILTERS`). Se aggiungi un
  nuovo POI/tag ai `FILTERS` la firma (per paese, salvata in
  `data/<paese>-filter.sig`) cambia e il prossimo `filter` si ri-estrae da solo; `cmd_geo_todo` (stdin→stdout, flag
  `--refresh`=tutte) elenca solo le tile geo mancanti o di `ver` obsoleto e gli
  `aggiorna_*`/`mondo_tile.sh` ne filtrano la lista con
  `osm_italy_processor.py geo-todo` prima di `gen-tile`. Per forzare la
  rigenerazione di tutte le geo senza toccare i grafi basta alzare
  `GEO_FORMAT_BASE`. `osm_italy_processor.py fmt-version` stampa `ver` corrente.
- **Bridge/tunnel nelle strade**: le `TileRoadRec` (`TileModels.cs`) portano
  `br`/`tu` (+ `dh`/`h0`/`h1`/`s0`/`s1` = retta di impalcato fra i capisaldi
  della way, frazioni globali di lunghezza). I tile con ponti/gallerie hanno
  `ver` con suffisso **`-bt`**: il prossimo aggiornamento rigenera SOLO loro
  (via `geo-todo` che confronta il marcatore, scan dei PBF cacheggiato in
  `data/<paese>-bt.json`), le altre tile restano byte-identiche e mai toccate.
  Nei batch mai rete: le quote capisaldi vengono da `dem.point_height` (cache
  + SRTM HGT; senza dati la strada resta drappata, mai rotta).
- `cmd_index` salva `ver` anche in index.json (`tiles[i].ver`).
- **Cache client Unity**: se cambi il formato geo/graph servito (es. aggiunta
  griglia `ele`/DEM), incrementa `TileClient.CacheVersion` in
  `unity-project/Assets/City/Scripts/OSM/TileClient.cs` (attuale: **5 = griglia
  DEM rigenerata** a 250 m allineata ai chunk con i void riempiti col vicino;
  4 = strade con bridge/tunnel, deck sospesi/tunnelizzati; 3 = geos con `ele`),
  altrimenti i device riusano le vecchie `.v*.geo.json` (strade drappate sui
  rialzi, niente fisica altimetrica).
- Processore/world: env `HUNTIX_COUNTRY` (default `italy`) seleziona i dataset
  `data/<paese>-{roads,areas,points}.pbf` (`Ctx.pbf()`); `gen-tile` on-demand per un
  paese richiede che i suoi PBF filtrati esistano (altrimenti la tile risulta vuota).
- POI vehicle wire: `dealer|repair|garage|hospital|school|bar|rampa`, record `{"k":"i",...}`
  nel campo `pois` della geo; `cmd_index` NON deve contare i file `*_geo.json.gz` come tile
  duplicate (fix già applicato).
- `cmd_gen_tile` con `--no-index` per i batch (l'indice va ricostruito una volta sola a fine
  corsa con `osm_italy_processor.py index`).
- **Fail-fast e sicurezza dati**: gli `aggiorna_*`/`mondo_tile.sh` usano `set -euo pipefail`
  (un download/filter/interruzione ferma lo script; tutto è idempotente, basta rilanciarlo).
  I PBF filtrati vengono scritti in `*.pbf.tmp` e spostati **atomicamente** solo se
  l'estrazione è completa (`tmp.replace`); un filtrato <1 MB (env `HUNTIX_MIN_PBF_MB`)
  è giudicato troncato e viene rigenerato, mai riusato. Su `wget` fallito il file parziale
  viene cancellato (un `-c` su un parziale corrotto produrrebbe un dump inutilizzabile).
  Nome PBF sorgente canonico: `data/<paese>-latest.osm.pbf` (`Ctx.pbf("latest.osm")`).
## Vincoli del mondo OSM lato client (le trappole che hanno rotto il ramo `altitudine`)
Regole non negoziabili emerse dal confronto `origin/unity` (funzionante, mondo
piatto) vs `origin/altitudine` (DEM, "player sospeso/sprofondato"). **Ogni volta
che si tocca altitudine, terreno o spawn, rileggere questa sezione.**

1. **Mai prendere l'hit PIÙ ALTO di una colonna per "il terreno".** Il bug che ha
   rotto il gioco: `if (hit.point.y > bestY) bestY = hit.point.y` piazzava il
   player sul tetto dell'edificio più alto. Nel mondo piatto (tutto a quota 0) la
   differenza non si vedeva; col DEM la colonna contiene strade, marciapiedi e
   palazzi. Oggi l'unico snap consentito è
   `CityChunkedWorld.TryGroundColumn(pos, self, clearance, maxDist, out snapped,
   out collider)` (`Game.cs` e `CityChunkedWorld` lo usano). Regola: si prende
   l'hit **più vicino** e la mask esclude gli edifici (`GroundSnapper.WalkableMask`
   = tutto tranne layer 8). NB: `Physics.RaycastAll` **non** ordina i hit per
   distanza, quindi `hits[0]` non è "il primo": prendere il min per `distance`.
2. **Nessun "proxy altimetrico" dalle altezze edifici.** Una tile senza `ele` si
   tratta come **piatta** (`ChunkBuilder.CollectTerrainHeights` ritorna vuoto).
   Il proxy storico costruiva il collider del terreno con le alhezze dei palazzi
   (scala di 10-30 m = si cammina sui tetti) e rialzava ogni edificio di un'altezza
   di *edificio* (case sospese a 20-30 m).
3. **Il passo di `TerrainChunk` e `TerrainChunk.DemSampleStep` devono restare
   coerenti** (costante condivisa): il primo è il passo della mesh, il secondo il
   passo del dizionario quote che `BuildHeightMesh` ri-interlaccia. Se divergono,
   la mesh della mesh DEM si disallinea e il terreno scivola sotto l'asfalto o
   copre la strada.
4. **Il mesh passo del terreno non va alzato sopra la risoluzione del dato.** La
   mesh è a 45 m (costante `step` in `BuildHeightMesh`) e il DEM a 250 m: la mesh
   era a 10 m (1300× i triangoli del quad piatto del ramo unity, senza LOD →
   510k tri in PhysX e il mondo non si caricava in tempo). Non alzare il passo
   della mesh sopra quello del DEM: non aggiunge informazione, solo costo.
5. **Il collider del terreno si accende solo entro `ChunkData.TerrainColliderLod`.**
   Il chunk sotto i piedi è LOD0 (`UpdateLods`: `d<=1 ? 0`), quindi il suolo
   calpestabile è sempre attivo; il risparmio è sugli anelli esterni.
6. **`ChunkManager.LoadRadius` (7×7) è tornato a 3** dopo che il terreno è
   alleggerito: con raggio corto e mondo che non finisce di caricare, il player
   esce dal mondo caricato e cade nel vuoto.
7. **Campionare fuori dai bordi del chunk**: `TileElevation.HeightAtWorld` ritorna
   **0** se il punto non è coperto da una griglia caricata. I bordi del nastro
   stradale sporgono oltre i bounds: usare `TryHeightAtWorld` e, se non coperto,
   tenere la quota del centro (`RoadRenderer.SurfaceHeight`).
8. **`CityConfig.WorldHeights` è l'unico interruttore** del mondo piatto/DEM
   (default `Flat` = comportamento del ramo unity). `HeightAt` ritorna 0 in
   `Flat`; i chunk senza `ele` restano piatti in ogni modalità.

## Costi di rendering/fisica: cosa è cambiato rispetto al ramo `unity`
Il ramo `unity` girava senza lag. Tre differenze misurabili spiegano la
regressione; due sono già corrette nel codice, la terza è una **decisione di
prodotto** e va presa di proposito.

1. ~~`CollectJunctions` allocava un `Junction` per ogni nodo della TILE, per
   ogni chunk~~ **CORRETTO**: due passate, si alloca solo per i nodi condivisi
   (8× meno, risultato identico).
2. ~~Le sonde cordolo/ostacolo di ogni auto giravano ogni frame~~ **CORRETTO**:
   sfalsate 1 frame su 6 (`CarAgent.CurbProbeStagger`), 6× meno
   `OverlapSphere` per frame.
3. **Kit edifici predefinito cambiato.** `CityStyle.UseQuaternius` ha default
   `1`: gli edifici usano i 3 modelli Quaternius (con texture) invece dei 109
   prefab Kenney. Misurato: FBX 613 KB di mediana contro 52 KB (**11,7×**) e
   **29,1 MB di texture** che nel ramo `unity` non esistevano. Nel ramo
   `unity` i Quaternius non erano nemmeno nel progetto. Sono la causa più
   probabile del lag di rendering: sono anche l'unica voce che si cambia a caldo
   (hamburger menu → stile edifici, poi serve rigenerare i chunk).
   Costo per città, con 49 chunk: Roma ~15.700 edifici in scena,
   Foggia ~4.500, Perugia ~2.800.
4. **Traffico passato da 90 a ~400 auto.** `TileRoadNetwork.CarsPerTile`
   10 → 55 e `MaxCars` 150 → 400. Con `CheckObstacle` che scorre tutti gli NPC
   attivi è il secondo maggior costo per frame. Riportare `CarsPerTile` a 20-25
   riporta il traffico alla scala del ramo `unity` con un decimo delle sonde.
5. **Decorazione per ogni edificio.** In `unity` al massimo 8 porte per chunk
   (filtro area 40-350 m² + 1 su 3). Oggi il budget è tolto (`ResetChunkBudget`
   è un no-op): ogni edificio ha `Porta` + `PortaSegnaposto` (un Renderer) +
   `Interno` + `LuceInterna` (una Light), quindi **+4 GameObject, +1 Light,
   +1 Renderer per edificio**. A Roma sono ~63.000 GameObject in più.
   L'interno è lazy (non viene costruito se il player non entra), ma i quattro
   oggetti vengono creati subito.
6. `ChunkData.SetLod` chiama `GetComponentsInChildren<BoxCollider>(true)` su
   tutto l'albero edifici a ogni cambio LOD: con la decorazione di cui al
   punto 5 l'array da scansionare è ~2× più grande.

**Regola:** quando si tocca il kit edifici o il numero di veicoli, ricordare che
sono le due leve principali sul frame rate. Prima di alzare qualcosa, misurare
con i dati (`tools/`), non a occhio.

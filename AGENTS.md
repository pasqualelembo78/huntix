# AGENTS.md

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
  `unity-project/Assets/City/Scripts/OSM/TileClient.cs` (attuale: 4 = strade
  con bridge/tunnel, deck sospesi/tunnelizzati; 3 = geos con `ele`), altrimenti
  i device riusano le vecchie `.v*.geo.json` (strade drappate sui rialzi,
  niente fisica altimetrica).
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
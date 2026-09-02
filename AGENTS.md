# AGENTS.md

## Repository
- Root: `/root/giochi/huntix` (git). Backend preprocessing in `backend/preprocessing`,
  tile/POI server in `backend/traffic/tile_server.py`, app Android in `app/`,
  Unity in `unity-project/`.
- UI/messaggi in italiano. `./check.sh` in `backend/preprocessing/cscheck` (modalità
  completa con `-define:HUNTIX_FULL`) — mai usare gli stub per il progetto Unity.

## Comandi convenzionali (frasi dell'utente → azione)
- **"aggiorna tile italia"** (o "fai l'aggiornamento completo dei POI") → esegui:
  `cd backend/preprocessing && nohup ./aggiorna_italia.sh refresh > /tmp/aggiorna_italia.log 2>&1 &`
  Il frontend scarica l'ultimo dump OSM Geofabrik **solo se obsoleto (>12h, env
  `HUNTIX_STALE_HOURS`)**, ri-estrae con `filter --force`, rigenera **tutte** le geo
  (`gen-tile --skip-graph --no-index`, 4 worker, grafi stradali intatti) e ricostruisce
  `index.json`. Senza `refresh` (`./aggiorna_italia.sh`) è idempotente: genera solo il
  mancante e se è tutto a posto dice "tutto a posto". Alla fine sconsigliare/eseguire
  `curl -X POST <server>/api/tiles/cache/clear`.
  File chiave: `aggiorna_italia.sh`, `aggiorna_puglia.sh`, `puglia_keys.txt`.
- **"aggiorna tile puglia"** → stesso flusso ma con `aggiorna_puglia.sh` (solo 412 tile di terra).
- **"aggiorna regione <nome>"** (anche "aggiorna tile della regione <nome>" o
  "aggiorna la regione <nome>", es. molise, lombardia, sicilia...) → esegui
  `nohup ./aggiorna_regione.sh <nome> > /tmp/aggiorna_regione.log 2>&1 &`; seleziona le
  tile di terra per centroide dal bbox in `osm_regions.py` (tutte le 20 regioni;
  puglia molise verificati, gli altri bbox generosi/indicativi), rigenera le geo
  mancanti, ricostruisce `index.json`. Endpoint opzionale: `<server>/api/tiles/cache/clear`.
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

## Note operazioni tile/POI
- Tile: griglia 1 IIS; `tile_key(lat,lon)` in `osm_italy_processor.py`; le geo sono
  on-demand via `gen-tile --skip-graph` e cache su disco `tiles/{key}_geo.json.gz`
  (cap 3 GB, env `HUNTIX_GEO_CACHE_GB`). Le tile di mare non producono file.
- Processore/world: env `HUNTIX_COUNTRY` (default `italy`) seleziona i dataset
  `data/<paese>-{roads,areas,points}.pbf` (`Ctx.pbf()`); `gen-tile` on-demand per un
  paese richiede che i suoi PBF filtrati esistano (altrimenti la tile risulta vuota).
- POI vehicle wire: `dealer|repair|garage|hospital|school|bar|rampa`, record `{"k":"i",...}`
  nel campo `pois` della geo; `cmd_index` NON deve contare i file `*_geo.json.gz` come tile
  duplicate (fix già applicato).
- `cmd_gen_tile` con `--no-index` per i batch (l'indice va ricostruito una volta sola a fine
  corsa con `osm_italy_processor.py index`).
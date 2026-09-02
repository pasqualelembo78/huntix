#!/usr/bin/env bash
# Aggiorna in un colpo solo UNA nazione (o una regione/distretto via bbox):
# scarica/prepara i dati, genera i grafi stradali (solo se mancano), rigenera
# le geo (edifici + POI) e ricostruisce index.json.
#
# Idempotente: se un aggiornamento completo è già stato fatto, NON riscarica
# né ri-estrae nulla: controlla che ci sia tutto (contando ciò che manca) e
# genera soltanto il mancante. I grafi già esistenti non vengono mai toccati.
#
# Uso:
#   ./mondo_tile.sh germania                    # tutta la nazione (bbox registry)
#   ./mondo_tile.sh germania "50.0,6.0,51.0,7.0"  # una regione/distretto
#   ./mondo_tile.sh refresh spagna              # forzato: ri-estrae e ridà tutte le geo
#
# Suggerito:
#   nohup ./mondo_tile.sh germania > /tmp/mondo_tile.log 2>&1 &
#   tail -f /tmp/mondo_tile.log
set -u
cd "$(dirname "$0")" || exit 1
PY=./venv/bin/python
START=$(date +%s)
PAR="${HUNTIX_PAR:-4}"
REFRESH=""

if [ "${1:-}" = "--list" ]; then
  "$PY" -c "import json; d=json.load(open('world_countries.json')); print('Nazioni disponibili:'); [print(f'  {c[\"slug\"]:20s} {c[\"name\"]}') for c in d]"
  exit 0
fi
[ "${1:-}" = "refresh" ] && { REFRESH=1; shift; }
SLUG="${1:-}"
[ -z "$SLUG" ] && { echo "uso: $0 [refresh] <slug> [latmin,lonmin,latmax,lonmax]"; exit 1; }
BBOX="${2:-}"
[ -n "$REFRESH" ] && echo "[$(date +%H:%M:%S)] MODALITA' REFRESH (ri-estrazione + ridefinizione completa geo)"

DBBOX=$("$PY" -c "
import json, sys
for x in json.load(open('world_countries.json')):
    if x['slug'] == sys.argv[1]:
        print(','.join(str(v) for v in x['bbox'])); break
else:
    sys.exit('slug non trovato; usa --list')
" "$SLUG") || { echo "slug non trovato"; exit 1; }
[ -z "$BBOX" ] && BBOX="$DBBOX"
echo "[$(date +%H:%M:%S)] nazione=$SLUG bbox=[$BBOX] (worker=$PAR)"

GEO=$("$PY" -c "
import json, sys
for x in json.load(open('world_countries.json')):
    if x['slug'] == sys.argv[1]: print(x.get('geofabrik') or ''); break
" "$SLUG")

# ── 1. PREP: scarica il PBF grezzo se manca.
RAW="data/${SLUG}-latest.osm.pbf"
if [ -n "$GEO" ] && [ ! -f "$RAW" ]; then
  URL="https://download.geofabrik.de/${GEO}-latest.osm.pbf"
  echo "[$(date +%H:%M:%S)] scarico $URL (una tantum)"
  mkdir -p data
  wget -c -q --show-progress "$URL" -O "$RAW"
fi

# ── 2. FILTER: ri-estrazione dei PBF filtrati (idempotente, salta se prsenti).
FLAGS=""
[ -n "$REFRESH" ] && FLAGS="--force"
HUNTIX_COUNTRY="$SLUG" "$PY" osm_italy_processor.py filter $FLAGS || exit 1

# ── 3. LAND: lista tile di terra (una tantum).
if [ ! -f "data/${SLUG}-land_keys.txt" ]; then
  echo "[$(date +%H:%M:%S)] scan tile di terra ('$SLUG') ..."
  HUNTIX_COUNTRY="$SLUG" "$PY" osm_italy_processor.py land || exit 1
fi

# ── 4. SPLIT: tile senza grafo (full) vs tile da geöticare (geo).
FULL=/tmp/mondo_full_$$.txt
GEO_T=/tmp/mondo_geo_$$.txt
: > "$FULL"; : > "$GEO_T"
HUNTIX_COUNTRY="$SLUG" "$PY" - "$BBOX" "$REFRESH" "$FULL" "$GEO_T" <<'PYEOF'
import os, sys
from math import floor
sys.path.insert(0, ".")
from tile_builder import ORIGIN_LAT, ORIGIN_LON, LAT_STEP, LON_STEP, tile_key_from_idx

latmin, lonmin, latmax, lonmax = map(float, sys.argv[1].split(","))
refresh = sys.argv[2] == "1"
full_out, geo_out = sys.argv[3], sys.argv[4]
slug = os.environ["HUNTIX_COUNTRY"]
land = set()
with open(f"data/{slug}-land_keys.txt") as f:
    for line in f:
        land.add(line.strip())

rows = []
for ilat in range(floor((latmin - ORIGIN_LAT) / LAT_STEP) - 1,
                  floor((latmax - ORIGIN_LAT) / LAT_STEP) + 1):
    for ilon in range(floor((lonmin - ORIGIN_LON) / LON_STEP) - 1,
                      floor((lonmax - ORIGIN_LON) / LON_STEP) + 1):
        k = tile_key_from_idx(ilat, ilon)
        plat = ORIGIN_LAT + (ilat + 0.5) * LAT_STEP
        plon = ORIGIN_LON + (ilon + 0.5) * LON_STEP
        if latmin <= plat <= latmax and lonmin <= plon <= lonmax and k in land:
            rows.append(k)
rows.sort()
full = [k for k in rows if not os.path.exists(f"tiles/{k}.json.gz")]
if refresh:
    geo = [k for k in rows if os.path.exists(f"tiles/{k}.json.gz")]
else:
    geo = [k for k in rows
           if os.path.exists(f"tiles/{k}.json.gz")
           and not os.path.exists(f"tiles/{k}_geo.json.gz")]
with open(full_out, "w") as f:
    f.write("\n".join(full) + ("\n" if full else ""))
with open(geo_out, "w") as f:
    f.write("\n".join(geo) + ("\n" if geo else ""))
print(f"tile di terra: {len(rows)} | senza grafo (full): {len(full)} | da geogenerare: {len(geo)}")
PYEOF

NF=$(wc -l < "$FULL"); NG=$(wc -l < "$GEO_T")
echo "[$(date +%H:%M:%S)] tile senza grafo (full: grafo+geo da zero) = $NF  |  tile da geogenerare = $NG"

if [ "$NF" -gt 0 ]; then
  echo "[$(date +%H:%M:%S)] pass finale grafi stradali su $NF tile (pesante, una tantum) ..."
  xargs -a "$FULL" -P "$PAR" -I{} sh -c \
    'HUNTIX_COUNTRY='"$SLUG"' ./venv/bin/python osm_italy_processor.py gen-tile {} --no-index >> mondo_gen.log 2>&1'
fi
if [ "$NG" -gt 0 ]; then
  echo "[$(date +%H:%M:%S)] rigenerazione geo (edifici+POI) su $NG tile ..."
  xargs -a "$GEO_T" -P "$PAR" -I{} sh -c \
    'HUNTIX_COUNTRY='"$SLUG"' ./venv/bin/python osm_italy_processor.py gen-tile {} --skip-graph --no-index >> mondo_gen.log 2>&1'
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
HUNTIX_COUNTRY="$SLUG" "$PY" osm_italy_processor.py index

DUR=$(( $(date +%s) - START ))
if [ "$NF" = "0" ] && [ "$NG" = "0" ]; then
  echo "[$(date +%H:%M:%S)] tutto a posto: nessuna tile da aggiornare (${DUR}s). Controllo completato."
else
  echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s ($((DUR/60))min) — $SLUG: $NF full + $NG geo"
fi
echo "Poi svuota la cache del server: curl -X POST http://<HOST>:<PORTA>/api/tiles/cache/clear"
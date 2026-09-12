#!/usr/bin/env bash
# Rigenera la geo (edifici + POI vehicle) di tutte le tile di Puglia
# dai dati OSM correnti. I grafi stradali non vengono toccati (--skip-graph).
#
# Uso:
#   nohup ./aggiorna_puglia.sh > /tmp/aggiorna_puglia.log 2>&1 &
#   tail -f /tmp/aggiorna_puglia.log
#
# Al termine la cache del tile server viene svuotata automaticamente
# (./cache_clear.sh).
set -euo pipefail
cd "$(dirname "$0")" || exit 1
START=$(date +%s)

echo "[$(date +%H:%M:%S)] estrazione PBF filtrati (auto se config. filtri cambiata) ..."
./venv/bin/python osm_italy_processor.py filter

TODO=/tmp/puglia_todo_$$.txt
grep -v '^#' puglia_keys.txt | \
  ./venv/bin/python osm_italy_processor.py geo-todo > "$TODO"
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile geo da (ri)generare: $N su $(grep -cv '^#' puglia_keys.txt)"

if [ "$N" -gt 0 ]; then
  HUNTIX_LOG_FILE="puglia_gen.log" ./venv/bin/python tile_worker.py --skip-graph --no-index < "$TODO"
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
./venv/bin/python osm_italy_processor.py index

echo "[$(date +%H:%M:%S)] backfill DEM sulle geo senza elevazione (idempotente) ..."
./dem_warm.sh ${HUNTIX_DEM_WARM_TILES:+"--limit" "$HUNTIX_DEM_WARM_TILES"} || true

DUR=$(( $(date +%s) - START ))
GEO=$(ls tiles/IT_*_geo.json.gz | wc -l)
echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s — tile geo totali: ${GEO}"
./cache_clear.sh

#!/usr/bin/env bash
# Aggiornamento POI/geo di tutta l'Italia (grafi stradali intatti).
#
# Incrementale per default: scarica il dump OSM solo se MANCA, ri-estrae i
# PBF filtrati solo se la configurazione filtri è cambiata (o un filtrato è
# mancante/troncato) e rigenera SOLO le geo mancanti/obsolete
# (geo-todo: formato `ver` vecchio o dati sorgente più recenti). Se tutto è
# già a posto dice "tutto a posto" in pochi secondi.
#
# Uso:
#   ./aggiorna_italia.sh            # incrementale (il normale flusso di build)
#   ./aggiorna_italia.sh refresh    # forzato: riscarica se obsoleto >12h,
#                                   # ri-estrae tutto e rigenera TUTTE le geo
#                                   # (i grafi stradali restano intatti)
#
# Suggerito:  nohup ./aggiorna_italia.sh > /tmp/aggiorna_italia.log 2>&1 &
#             tail -f /tmp/aggiorna_italia.log
set -euo pipefail
cd "$(dirname "$0")" || exit 1
START=$(date +%s)
REFRESH="${1:-}"
PY=./venv/bin/python

if [ "$REFRESH" = "refresh" ]; then
  echo "[$(date +%H:%M:%S)] MODALITA' REFRESH: ri-estrazione completa e "
  echo "  rigenerazione di TUTTE le geo (i grafi stradali restano intatti)"
fi

# ── dump OSM: scarica solo se manca (default) o se obsoleto (>12h, refresh)
RAW=data/italy-latest.osm.pbf
NEED_DL=$("$PY" -c "
import os, sys, time
p = sys.argv[1]
refresh = sys.argv[2] == 'refresh'
if not os.path.exists(p):
    print('1'); sys.exit()
if not refresh:
    print('0'); sys.exit()
age_h = (time.time() - os.path.getmtime(p)) / 3600
print('1' if age_h > float(os.environ.get('HUNTIX_STALE_HOURS', '12')) else '0')
" "$RAW" "$REFRESH")
if [ "$NEED_DL" = "1" ]; then
  echo "[$(date +%H:%M:%S)] scarico OSM aggiornato ..."
  "$PY" osm_italy_processor.py download
else
  echo "[$(date +%H:%M:%S)] dump OSM gia' presente/fresco, salto il download"
fi

echo "[$(date +%H:%M:%S)] estrazione PBF filtrati (--force solo in refresh, altrimenti auto) ..."
"$PY" osm_italy_processor.py filter ${REFRESH:+"--force"}

echo "[$(date +%H:%M:%S)] calcolo tile di terra da index.json ..."
"$PY" - <<'PYEOF'
import json
idx = json.load(open("tiles/index.json"))
land = sorted(t["key"] for t in idx["tiles"] if t.get("nodes", 0) > 0)
print(f"  -> {len(land)} tile di terra")
open("/tmp/italia_keys_tmp.txt", "w").write("\n".join(land) + "\n")
PYEOF

TODO="/tmp/italia_todo_$$.txt"
"$PY" osm_italy_processor.py geo-todo ${REFRESH:+"--refresh"} \
  < /tmp/italia_keys_tmp.txt > "$TODO"
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile geo da generare: $N"
if [ "$N" -gt 0 ]; then
  HUNTIX_LOG_FILE="italia_gen.log" "$PY" tile_worker.py --skip-graph --no-index < "$TODO"
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
"$PY" osm_italy_processor.py index

echo "[$(date +%H:%M:%S)] backfill DEM sulle geo senza elevazione (idempotente) ..."
./dem_warm.sh ${HUNTIX_DEM_WARM_TILES:+"--limit" "$HUNTIX_DEM_WARM_TILES"} || true

DUR=$(( $(date +%s) - START ))
if [ "$N" -eq 0 ]; then
  echo "[$(date +%H:%M:%S)] tutto a posto: nessuna tile da aggiornare (${DUR}s)"
else
  echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s ($((DUR/60))min) — $N tile aggiornate"
fi
./cache_clear.sh

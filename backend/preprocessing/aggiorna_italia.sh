#!/usr/bin/env bash
# Aggiornamento POI/geo di tutta l'Italia (grafi stradali intatti).
#
# Uso:
#   ./aggiorna_italia.sh            # idempotente: genera solo il mancante
#   ./aggiorna_italia.sh refresh    # scarica OSM (se obsoleto >12h), ri-estrae
#                                   # e rigenera TUTTE le geo (i grafi restano)
#
# Suggerito:  nohup ./aggiorna_italia.sh refresh > /tmp/aggiorna_italia.log 2>&1 &
#             tail -f /tmp/aggiorna_italia.log
set -u
cd "$(dirname "$0")" || exit 1
START=$(date +%s)
REFRESH="${1:-}"
PY=./venv/bin/python

if [ "$REFRESH" = "refresh" ]; then
  # --- scarica il dump aggiornato solo se manca o è vecchio (>12h) ---
  RAW=data/italy-latest.osm.pbf
  STALE=$("$PY" -c "
import os, sys, time
p = sys.argv[1]
if not os.path.exists(p): print('1'); sys.exit()
age_h = (time.time() - os.path.getmtime(p)) / 3600
print('1' if age_h > float(os.environ.get('HUNTIX_STALE_HOURS', '12')) else '0')
" "$RAW")
  if [ "$STALE" = "1" ]; then
    echo "[$(date +%H:%M:%S)] scarico OSM aggiornato ..."
    "$PY" osm_italy_processor.py download
  else
    echo "[$(date +%H:%M:%S)] dump già fresco, salto il download"
  fi
  echo "[$(date +%H:%M:%S)] ri-estrazione (filter --force) ..."
  "$PY" osm_italy_processor.py filter --force
fi

echo "[$(date +%H:%M:%S)] calcolo tile di terra da index.json ..."
"$PY" - <<'PYEOF'
import json
idx = json.load(open("tiles/index.json"))
land = sorted(t["key"] for t in idx["tiles"] if t.get("nodes", 0) > 0)
print(f"  -> {len(land)} tile di terra")
open("/tmp/italia_keys_tmp.txt", "w").write("\n".join(land) + "\n")
PYEOF

TODO="/tmp/italia_todo_$$.txt"
: > "$TODO"
if [ "$REFRESH" = "refresh" ]; then
  for k in $(cat /tmp/italia_keys_tmp.txt); do
    echo "$k" >> "$TODO"
  done
else
  for k in $(cat /tmp/italia_keys_tmp.txt); do
    [ -f "tiles/${k}_geo.json.gz" ] || echo "$k" >> "$TODO"
  done
fi
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile geo da generare: $N"
if [ "$N" -gt 0 ]; then
  xargs -a "$TODO" -P 4 -I{} sh -c \
    './venv/bin/python osm_italy_processor.py gen-tile {} --skip-graph --no-index >> italia_gen.log 2>&1'
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
"$PY" osm_italy_processor.py index

DUR=$(( $(date +%s) - START ))
if [ "$N" -eq 0 ]; then
  echo "[$(date +%H:%M:%S)] tutto a posto: nessuna tile da aggiornare (${DUR}s)"
else
  echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s ($((DUR/60))min) — $N tile aggiornate"
fi
echo "Poi svuota la cache del server: curl -X POST http://<HOST>:<PORTA>/api/tiles/cache/clear"
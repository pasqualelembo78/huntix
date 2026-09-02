#!/usr/bin/env bash
# Rigenera la geo (edifici + POI vehicle) di tutte le tile di Puglia
# dai dati OSM correnti. I grafi stradali non vengono toccati (--skip-graph).
#
# Uso:
#   nohup ./aggiorna_puglia.sh > /tmp/aggiorna_puglia.log 2>&1 &
#   tail -f /tmp/aggiorna_puglia.log
#
# Al termine, se il tile server è in esecuzione:
#   curl -X POST http://<HOST>:<PORTA>/api/tiles/cache/clear
set -u
cd "$(dirname "$0")" || exit 1
START=$(date +%s)

TODO=/tmp/puglia_todo.txt
: > "$TODO"
for k in $(grep -v '^#' puglia_keys.txt); do
  [ -f "tiles/${k}_geo.json.gz" ] || echo "$k" >> "$TODO"
done
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile da generare: $N (già presenti: $(( $(wc -l < puglia_keys.txt) - N )))"

if [ "$N" -gt 0 ]; then
  xargs -a "$TODO" -P 4 -I{} sh -c \
    './venv/bin/python osm_italy_processor.py gen-tile {} --skip-graph --no-index >> puglia_gen.log 2>&1'
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
./venv/bin/python osm_italy_processor.py index

DUR=$(( $(date +%s) - START ))
GEO=$(ls tiles/IT_*_geo.json.gz | wc -l)
echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s — tile geo totali: ${GEO}"
echo "Ora, se il server è attivo: curl -X POST http://<HOST>:<PORTA>/api/tiles/cache/clear"
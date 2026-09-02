#!/usr/bin/env bash
# Aggiornamento POI/geo per una singola regione italiana (grafi stradali intatti).
#
# Uso:
#   ./aggiorna_regione.sh puglia
#   ./aggiorna_regione.sh molise
#   ./aggiorna_regione.sh --list            # elenca le regioni disponibili
#
# Suggerito:  nohup ./aggiorna_regione.sh molise > /tmp/aggiorna_regione.log 2>&1 &
#             tail -f /tmp/aggiorna_regione.log
set -u
cd "$(dirname "$0")" || exit 1
START=$(date +%s)
REG="${1:-}"

if [ "$REG" = "--list" ]; then
  ./venv/bin/python - <<'PYEOF'
import json
from osm_regions import REGIONS
print("Regioni disponibili:", ", ".join(sorted(REGIONS)))
PYEOF
  exit 0
fi
[ -z "$REG" ] && { echo "uso: $0 <regione> (o --list)"; exit 1; }

echo "[$(date +%H:%M:%S)] calcolo tile di terra di $REG ..."
./venv/bin/python - "$REG" <<'PYEOF'
import json, sys
sys.path.insert(0, ".")
from osm_regions import REGIONS
reg = sys.argv[1]
if reg not in REGIONS:
    sys.exit(f"Regione sconosciuta: {reg}. Disponibili: {', '.join(sorted(REGIONS))}")
latmin, lonmin, latmax, lonmax = REGIONS[reg]
idx = json.load(open("tiles/index.json"))
land = []
for t in idx["tiles"]:
    if t.get("nodes", 0) <= 0:
        continue
    _, ilat, ilon = t["key"].split("_")
    plat = 34.0 + (int(ilat) + 0.5) * 0.09
    plon = 5.0 + (int(ilon) + 0.5) * 0.121
    if latmin <= plat <= latmax and lonmin <= plon <= lonmax:
        land.append(t["key"])
print(f"  -> {len(land)} tile di terra")
open("/tmp/regione_keys.txt", "w").write("\n".join(sorted(land)) + "\n")
PYEOF

TODO="/tmp/regione_todo_$$.txt"
: > "$TODO"
for k in $(cat /tmp/regione_keys.txt); do
  [ -f "tiles/${k}_geo.json.gz" ] || echo "$k" >> "$TODO"
done
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile geo da generare per $REG: $N"
if [ "$N" -gt 0 ]; then
  xargs -a "$TODO" -P 4 -I{} sh -c \
    './venv/bin/python osm_italy_processor.py gen-tile {} --skip-graph --no-index >> regione_gen.log 2>&1'
fi

echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
./venv/bin/python osm_italy_processor.py index

DUR=$(( $(date +%s) - START ))
echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s ($((DUR/60))min)"
echo "Poi svuota la cache del server: curl -X POST http://<HOST>:<PORTA>/api/tiles/cache/clear"
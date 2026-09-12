#!/usr/bin/env bash
# Aggiorna la geo (edifici + POI) di UNA PROVINCIA INTERA dai dati OSM
# correnti. I grafi stradali non vengono toccati (--skip-graph).
#
# La citta' viene cercata prima nel database locale (city_to_province.json +
# italian_provinces.json): se esiste, usa la bbox dell'intera provincia.
# Se non e' nel DB, fa fallback a Nominatim (OpenStreetMap).
#
# Uso:
#   ./aggiorna_citta.sh foggia          # -> tutta la provincia di Foggia
#   ./aggiorna_citta.sh bari            # -> tutta la provincia di Bari
#   ./aggiorna_citta.sh torino          # -> tutta la provincia di Torino
#   ./aggiorna_citta.sh "san giovanni rotondo"  # -> provincia di Foggia
#
# Suggerito:  nohup ./aggiorna_citta.sh foggia > /tmp/aggiorna_citta.log 2>&1 &
#             tail -f /tmp/aggiorna_citta.log
set -euo pipefail
cd "$(dirname "$0")" || exit 1
START=$(date +%s)
CITY="${1:-}"
[ -z "$CITY" ] && { echo "uso: $0 <città>"; exit 1; }
PY=./venv/bin/python

# ── 1. Lookup citta' -> provincia (database locale, nessuna rete) ──
PROV_SLUG=""
BBOX=""
PROV_NAME=""

BBOX="$("$PY" - "$CITY" <<'PYEOF'
import json, sys, os
sys.path.insert(0, ".")
city = sys.argv[1].lower().strip()

# normalizza spazi e apostrofi
city_norm = city.replace("'", "'").replace("'", "'").replace("  ", " ")

# carica mapping citta' -> provincia
try:
    c2p = json.load(open("city_to_province.json"))
except FileNotFoundError:
    c2p = {}

slug = c2p.get(city_norm) or c2p.get(city.replace(" ", " "))
if not slug:
    # prova anche senza punteggiatura
    import re
    city_clean = re.sub(r"[^a-z ]", "", city_norm).strip()
    slug = c2p.get(city_clean)

if not slug:
    sys.exit(1)

# carica province e restituisci bbox
try:
    provs = json.load(open("italian_provinces.json"))
except FileNotFoundError:
    sys.exit(1)

bbox = provs.get(slug)
if not bbox:
    sys.exit(1)

prov_names = {
    "barletta-andria-trani": "Barletta-Andria-Trani",
    "forli-cesena": "Forlì-Cesena",
    "la-spezia": "La Spezia",
    "massa-carrara": "Massa-Carrara",
    "pesaro-e-urbino": "Pesaro e Urbino",
    "reggio-calabria": "Reggio Calabria",
    "reggio-emilia": "Reggio Emilia",
    "sud-sardegna": "Sud Sardegna",
    "verbano-cusio-ossola": "Verbano-Cusio-Ossola",
}
name = prov_names.get(slug, slug.replace("-", " ").title())
print(f"{name}|{slug}|{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}")
sys.exit(0)
PYEOF
)" || rc=$?
rc=${rc:-0}

if [ "$rc" -eq 0 ] && [ -n "$BBOX" ]; then
  PROV_NAME=$(echo "$BBOX" | cut -d'|' -f1)
  PROV_SLUG=$(echo "$BBOX" | cut -d'|' -f2)
  BBOX=$(echo "$BBOX" | cut -d'|' -f3)
  echo "[$(date +%H:%M:%S)] città '$CITY' -> provincia $PROV_NAME (database locale)"
else
  echo "[$(date +%H:%M:%S)] '$CITY' non trovata nel database province, fallback Nominatim ..."
  out="$( "$PY" - "$CITY" <<'PYEOF' 2>&1
import json, sys, time, urllib.parse, urllib.request
city = sys.argv[1]
url = ("https://nominatim.openstreetmap.org/search"
       "?format=json&limit=5&countrycodes=it&addressdetails=1&accept-language=it"
       f"&q={urllib.parse.quote(city)}")
last = None
for attempt in range(3):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "huntix-build/1.0 (tile-update)"})
        data = json.load(urllib.request.urlopen(req, timeout=30))
        for r in data:
            if (r.get("address") or {}).get("country_code") != "it":
                continue
            if r.get("addresstype") in ("city", "town", "village", "hamlet",
                                        "municipality", "locality", "county"):
                bb = r["boundingbox"]
                print(f"{bb[0]},{bb[2]},{bb[1]},{bb[3]}")
                sys.exit(0)
        sys.exit(1)
    except Exception as e:
        last = e
        time.sleep(2 * (attempt + 1))
print(f"GEOERR: {last}")
sys.exit(2)
PYEOF
  )" || rc=$?
  rc=${rc:-0}
  case "$rc" in
    0) BBOX="$out"; PROV_NAME="$CITY (Nominatim)" ;;
    1) echo "[$(date +%H:%M:%S)] CITTÀ NON TROVATA: $CITY"; exit 1 ;;
    *) echo "[$(date +%H:%M:%S)] errore geocoding: $out"; exit 1 ;;
  esac
fi

echo "[$(date +%H:%M:%S)] area: $PROV_NAME — bbox=[$BBOX]"

# ── 2. Calcolo tile di terra ──
echo "[$(date +%H:%M:%S)] calcolo tile di terra ..."
"$PY" - "$BBOX" <<'PYEOF'
import json, sys
sys.path.insert(0, ".")
latmin, lonmin, latmax, lonmax = map(float, sys.argv[1].split(","))
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
open("/tmp/citta_keys.txt", "w").write("\n".join(sorted(land)) + "\n")
PYEOF

# ── 3. Estrazione PBF filtrati (auto se config. filtri cambiata) ──
echo "[$(date +%H:%M:%S)] estrazione PBF filtrati (auto se config. filtri cambiata) ..."
"$PY" osm_italy_processor.py filter

# ── 4. Tile geo da (ri)generare ──
TODO="/tmp/citta_todo_$$.txt"
"$PY" osm_italy_processor.py geo-todo < /tmp/citta_keys.txt > "$TODO"
N=$(wc -l < "$TODO")
echo "[$(date +%H:%M:%S)] tile geo da (ri)generare per $PROV_NAME: $N"
if [ "$N" -gt 0 ]; then
  HUNTIX_LOG_FILE="citta_gen.log" "$PY" tile_worker.py --skip-graph --no-index < "$TODO"
fi

# ── 5. Ricostruzione index + backfill DEM ──
echo "[$(date +%H:%M:%S)] ricostruisco index.json ..."
"$PY" osm_italy_processor.py index

echo "[$(date +%H:%M:%S)] backfill DEM sulle geo senza elevazione (idempotente) ..."
./dem_warm.sh ${HUNTIX_DEM_WARM_TILES:+"--limit" "$HUNTIX_DEM_WARM_TILES"} || true

DUR=$(( $(date +%s) - START ))
GEO=$(ls tiles/IT_*_geo.json.gz 2>/dev/null | wc -l)
echo "[$(date +%H:%M:%S)] FATTO in ${DUR}s ($((DUR/60))min) — $PROV_NAME: $N geo — tile geo totali: $GEO"
./cache_clear.sh

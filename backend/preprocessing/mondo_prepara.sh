#!/usr/bin/env bash
# Prepara i dati di UNA nazione per la generazione tile: scarica il dump
# Geofabrik (se manca), filtra roads/areas/points (`filter`) e crea la lista
# delle tile di terra (`land`). Idempotente: salta i passi già fatti.
#
# Uso:
#   ./mondo_prepara.sh germania          # scarica/filtra/land (solo se manca)
#   ./mondo_prepara.sh germania --refresh   # ri-estrazione forzata (filter --force)
#   ./mondo_prepara.sh germania --clean     # elimina il PBF grezzo alla fine
#   ./mondo_prepara.sh --list               # nazioni disponibili
#
# Nota: normalmente non serve chiamarlo a mano, lo fa da solo `mondo_tile.sh`.
set -u
cd "$(dirname "$0")" || exit 1
PY=./venv/bin/python

if [ "${1:-}" = "--list" ]; then
  "$PY" -c "import json; d=json.load(open('world_countries.json')); print('Nazioni disponibili:'); [print(f'  {c[\"slug\"]:20s} {c[\"name\"]}') for c in d]"
  exit 0
fi
SLUG="${1:-}"
[ -z "$SLUG" ] && { echo "uso: $0 <slug> [--refresh] [--clean] | --list"; exit 1; }
REFRESH=""; CLEAN=""
case " $* " in *--refresh*) REFRESH=1;; esac
case " $* " in *--clean*) CLEAN=1;; esac

GEO=$("$PY" -c "
import json, sys
for x in json.load(open('world_countries.json')):
    if x['slug'] == sys.argv[1]:
        print(x.get('geofabrik') or ''); break
else:
    sys.exit('slug non trovato; usa --list')
" "$SLUG") || { echo "slug non trovato"; exit 1; }

echo "[$(date +%H:%M:%S)] nazione: $SLUG"

RAW="data/${SLUG}-latest.osm.pbf"
if [ -n "$GEO" ] && [ ! -f "$RAW" ]; then
  URL="https://download.geofabrik.de/${GEO}-latest.osm.pbf"
  echo "[$(date +%H:%M:%S)] scarico $URL"
  mkdir -p data
  wget -c -q --show-progress "$URL" -O "$RAW"
fi

echo "[$(date +%H:%M:%S)] filtro OSM (filter) per '$SLUG' ..."
[ -n "$REFRESH" ] && FLAGS="--force" || FLAGS=""
HUNTIX_COUNTRY="$SLUG" "$PY" osm_italy_processor.py filter $FLAGS || exit 1

echo "[$(date +%H:%M:%S)] scan tile di terra (land) ..."
HUNTIX_COUNTRY="$SLUG" "$PY" osm_italy_processor.py land || exit 1

if [ -n "$CLEAN" ] && [ -f "$RAW" ]; then
  sz=$(du -h "$RAW" | cut -f1)
  rm -f "$RAW"
  echo "[$(date +%H:%M:%S)] PBF grezzo eliminato ($sz)"
fi

echo "[$(date +%H:%M:%S)] preparazione completata per $SLUG"
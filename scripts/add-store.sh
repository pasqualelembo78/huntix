#!/usr/bin/env bash
#
# add-store.sh — aggiunge/aggiorna un negozio (POI) in assets/shops.json
#
# Uso (interattivo):
#   ./scripts/add-store.sh
#
# Uso (CLI, 1 riga):
#   ./scripts/add-store.sh --name "Caffè Luna" --lat 41.9035 --lng 12.4972 \
#       --buildingType "CAFE" --poiType "FOOD" \
#       --url "https://example.com/luna.json" --pageType json
#
# Uso (batch CSV):
#   ./scripts/add-store.sh --csv shops.csv
#   # format CSV header: id,name,lat,lng,buildingType,poiType,url,pageType
#
# NOTE per huntix-poi (github.com/pasqualelembo78/huntix-poi):
#   - questo script è identico in logica: basta puntare SHOPS_JSON alla
#     cartella del repo huntix-poi (export SHOPS_JSON=/percorso/shops.json).
#   - pageType accetta: json | url  (custom = json).
set -euo pipefail

SHOPS_JSON="${SHOPS_JSON:-huntix/src/main/assets/shops.json}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ensure_file() {
  if [[ ! -f "$SHOPS_JSON" ]]; then
    mkdir -p "$(dirname "$SHOPS_JSON")"
    echo '[]' > "$SHOPS_JSON"
  fi
}

validate_number() {
  local v="$1" name="$2"
  if ! awk -v x="$v" 'BEGIN{ if (x=="" || x+0==x && x!~/[a-zA-Z]/) exit 0; else exit 1 }' <<< "$v"; then
    echo "ERRORE: $name non è un numero ($v)" >&2
    exit 2
  fi
}

add_one() {
  local id name lat lng btype ptype url ptype_in page
  name="${NAME:-}"; lat="${LAT:-}"; lng="${LNG:-}"; btype="${BTYPE:-SHOP}"; ptype="${PTYPE:-FOOD}"; url="${URL:-}"; ptype_in="${PGPT:-url}"
  validate_number "$lat" "lat"; validate_number "$lng" "lng"
  [[ -z "$name" ]] && { echo "ERRORE: name obbligatorio" >&2; exit 2; }

  id="$(echo "${name}" | tr '[:upper:]' '[:lower:]' | tr -cs '[:alnum:]' '_' | sed 's/^_//;s/_$//')"
  page="$(tr '[:upper:]' '[:lower:]' <<< "$ptype_in")"
  case "$page" in json|custom) page="json";; url|*) page="url";; esac

  local exists
  exists=$(jq -r --arg id "$id" '.[].id == $id' "$SHOPS_JSON" 2>/dev/null)
  ensure_file

  if [[ "$exists" == "true" ]]; then
    echo "Aggiorno negozio esistente id=$id"
    jq --arg id "$id" --arg name "$name" --arg lat "$lat" --arg lng "$lng" \
       --arg b "$btype" --arg p "$ptype" --arg u "$url" --arg pg "$page" \
       'map(if .id == $id then
         {id:$id,name:$name,lat:($lat|tonumber),lng:($lng|tonumber),
          buildingType:$b,poiType:$p,url:$u,pageType:$pg} else . end)' "$SHOPS_JSON" > "$SHOPS_JSON.tmp"
  else
    echo "Aggiungo nuovo negozio id=$id"
    jq --arg id "$id" --arg name "$name" --arg lat "$lat" --arg lng "$lng" \
       --arg b "$btype" --arg p "$ptype" --arg u "$url" --arg pg "$page" \
       '. + [{id:$id,name:$name,lat:($lat|tonumber),lng:($lng|tonumber),
        buildingType:$b,poiType:$p,url:$u,pageType:$pg}]' "$SHOPS_JSON" > "$SHOPS_JSON.tmp"
  fi
  mv "$SHOPS_JSON.tmp" "$SHOPS_JSON"
  # Pretty-print
  jq '.' "$SHOPS_JSON" > "$SHOPS_JSON.tmp" && mv "$SHOPS_JSON.tmp" "$SHOPS_JSON"
  echo "Fatto. Totale negozi: $(jq 'length' "$SHOPS_JSON")"
}

batch_csv() {
  local csv="$CSV"
  [[ -f "$csv" ]] || { echo "ERRORE: CSV $csv non trovato" >&2; exit 2; }
  ensure_file
  local n=0
  while IFS=, read -r id name lat lng btype ptype url pg; do
    [[ "$id" == "id" || "$id" == "" || "$id" == \#* ]] && continue
    NAME="$name" LAT="$lat" LNG="$lng" BTYPE="$btype" PTYPE="$ptype" URL="$url" PGPT="$pg" add_one
    n=$((n+1))
  done < <(tail -n +2 "$csv")
  echo "Importati $n negozi da $csv"
}

usage() { echo "Usa --help"; }

# --- parse args ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2;; --lat) LAT="$2"; shift 2;; --lng) LNG="$2"; shift 2;;
    --buildingType) BTYPE="$2"; shift 2;; --poiType) PTYPE="$2"; shift 2;;
    --url) URL="$2"; shift 2;; --pageType) PGPT="$2"; shift 2;;
    --csv) CSV="$2"; shift 2;; --file) SHOPS_JSON="$2"; shift 2;;
    --help|-h) cat "$SCRIPT_DIR/../docs/add-store-help.txt" 2>/dev/null || usage; exit 0;;
    *) echo "Argomento sconosciuto: $1"; exit 2;;
  esac
done

if [[ "${CSV:-}" != "" ]]; then batch_csv; else add_one; fi

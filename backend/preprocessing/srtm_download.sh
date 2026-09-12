#!/usr/bin/env bash
# Scarica in locale i file SRTM3 (HGT) per il bake DEM offline
# (`HUNTIX_SRTM_DIR`, di default: srtm/ accanto a dem.py).
#
# Uso:
#   ./srtm_download.sh                  # Italia (default): celle N35..N47 x E006..E018
#   ./srtm_download.sh "35.5,6.5,47.2,18.6"   # bbox "latmin,lonmin,latmax,lonmax"
#   ./srtm_download.sh --list           # stampa l'elenco delle celle richieste e basta
#
# Idempotente: scarica solo i file mancanti. Fonte: mirror pubblico del dataset
# SRTM3 (NASA/USGS), copertura 60N..60S. File attesi: N40E015.hgt.zip.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRTM_DIR="${HUNTIX_SRTM_DIR:-$HERE/srtm}"
MIRROR_BASE="https://srtm.kurviger.de/SRTM3"
REGION="Eurasia"

usage() {
  sed -n '2,10p' "${BASH_SOURCE[0]}"
}

declare -a CELLS

int_part() {
  local v="$1" sgn=""
  if [[ "$v" == -* ]]; then sgn="-"; v="${v#-}"; fi
  echo "${sgn}$((10#${v%.*}))"
}

cells_from_bbox() {
  local latmin lonmin latmax lonmax
  latmin="$1"; lonmin="$2"; latmax="$3"; lonmax="$4"
  local la lo la0 la1 lo0 lo1
  la0="$(int_part "$latmin")"; la1="$(int_part "$latmax")"
  lo0="$(int_part "$lonmin")"; lo1="$(int_part "$lonmax")"
  for (( la = la0; la <= la1; la++ )); do
    for (( lo = lo0; lo <= lo1; lo++ )); do
      local ns ew
      if (( la >= 0 )); then ns=N; else ns=S; fi
      if (( lo >= 0 )); then ew=E; else ew=W; fi
      CELLS+=("$(printf '%s%02d%s%03d' "$ns" "${la#-}" "$ew" "${lo#-}")")
    done
  done
}

ARG="${1:-}"
BBOX_DEFAULT="35.5,6.5,47.2,18.6"

run_bbox() {
  local b="$1"
  IFS=, read -r la1 lo1 la2 lo2 <<< "$b"
  cells_from_bbox "$la1" "$lo1" "$la2" "$lo2"
}

if [[ "$ARG" == "--list" ]]; then
  run_bbox "${2:-$BBOX_DEFAULT}"
  printf '%s\n' "${CELLS[@]}"
  exit 0
elif [[ "$ARG" =~ ^-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+$ ]]; then
  run_bbox "$ARG"
else
  run_bbox "$BBOX_DEFAULT"
fi

mkdir -p "$SRTM_DIR"

pids=()
failed=0
for c in "${CELLS[@]}"; do
  if
    [[ -f "$SRTM_DIR/$c.hgt" ]] ||
    [[ -f "$SRTM_DIR/$c.hgt.zip" ]] ||
    [[ -f "$SRTM_DIR/$c.zip" ]]
  then
    echo "[srtm] $c gia' presente"
    continue
  fi
  url="$MIRROR_BASE/$REGION/$c.hgt.zip"
  (
    if wget -q --timeout=60 --tries=2 -O "$SRTM_DIR/$c.hgt.zip.tmp" "$url"; then
      mv "$SRTM_DIR/$c.hgt.zip.tmp" "$SRTM_DIR/$c.hgt.zip"
      echo "[srtm] $c ok"
    else
      rc=$?
      rm -f "$SRTM_DIR/$c.hgt.zip.tmp"
      if (( rc == 8 )); then
        echo "[srtm] $c non presente (mare/404), salto"
      else
        echo "[srtm] $c FALLITO (rc=$rc)" >&2
        exit 1
      fi
    fi
  ) &
  pids+=("$!")
  # max 4 download paralleli
  while (( ${#pids[@]} >= 4 )); do
    if ! kill -0 "${pids[0]}" 2>/dev/null; then
      if ! wait "${pids[0]}"; then failed=1; fi
      pids=("${pids[@]:1}")
    else
      sleep 0.2
    fi
  done
done

for p in "${pids[@]}"; do
  if ! wait "$p"; then failed=1; fi
done

yes=0; tot=0
for c in "${CELLS[@]}"; do
  tot=$((tot+1))
  [[ -f "$SRTM_DIR/$c.hgt" || -f "$SRTM_DIR/$c.hgt.zip" || -f "$SRTM_DIR/$c.zip" ]] && yes=$((yes+1))
done
echo "[srtm] celle: $yes/$tot presenti in $SRTM_DIR"
exit "$failed"
#!/usr/bin/env bash
# ======================================================================
# HUNTIX — Backfill DEM (SRTM/elevazione) sulle tile geo senza 'ele'.
# Idempotente: salta le tile che hanno gia' l'elevazione e quelle in cui
# il DEM non e' (ancora) ottenibile. Studiato per un uso serale/off-peak:
# le chiamate ride, se servono, sono date con pacing gentile.
#
# Uso:
#   ./dem_warm.sh                  # tutte le tile in attesa (default)
#   HUNTIX_DEM_WARM_TILES=300 ./dem_warm.sh
#   ./dem_warm.sh --limit 300 --sleep 0.5
# ----------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

PY=python3
LIMIT=()
if [[ -n "${HUNTIX_DEM_WARM_TILES:-}" ]]; then
  LIMIT=(--limit "$HUNTIX_DEM_WARM_TILES")
fi

SRTM_DIR="${HUNTIX_SRTM_DIR:-$PWD/srtm}"
echo "[dem_warm] inizio $(date '+%H:%M:%S') TZ=$(date +%z) SRTM=${HUNTIX_SRTM_DIR:-$PWD/srtm}"
if [[ ! -d "$SRTM_DIR" ]] || [[ -z "$(ls -A "$SRTM_DIR" 2>/dev/null)" ]]; then
  echo "[dem_warm] avviso: SRTM locale non disponibile (cartella $SRTM_DIR vuota/inesistente):"
  echo "[dem_warm]          scarica le celle con ./srtm_download.sh oppure il backfill usera' la rete come fallback."
fi

"$PY" dem_warm.py "${LIMIT[@]}" "$@"

echo "[dem_warm] fine $(date '+%H:%M:%S')"

# Le elevazioni appena scritte non devono servire versioni stale del tile
# server (cache RAM/enriched): svuota subito.
./cache_clear.sh
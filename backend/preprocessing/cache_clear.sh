#!/usr/bin/env bash
# ======================================================================
# HUNTIX — Svuota la cache del tile server dopo una rigenerazione tile.
# Idempotente e robusto: retry con backoff, verifica la risposta JSON
# ('ok': true) e fallisce MODO soft (avviso, exit 0) di default. Se vuoi
# che un fallimento interrompa lo script chiamante:
#     CACHE_CLEAR_FAIL_FATAL=1 ./cache_clear.sh
#
# Endpoint default: http://127.0.0.1:5100/api/tiles/cache/clear
# Override:         HUNTIX_TILES_CACHE_URL (o HOST/PORTA)
# ----------------------------------------------------------------------
set -u

HOST="${HUNTIX_TILES_CACHE_HOST:-127.0.0.1}"
PORT="${HUNTIX_TILES_CACHE_PORT:-5100}"
URL="${HUNTIX_TILES_CACHE_URL:-http://${HOST}:${PORT}/api/tiles/cache/clear}"
ATTEMPTS="${HUNTIX_TILES_CACHE_ATTEMPTS:-3}"

_clear_cache() {
  local attempt=0
  while [ "$attempt" -lt "$ATTEMPTS" ]; do
    attempt=$((attempt + 1))
    # -s silenzioso, -S mostra errori di rete, -o scarica il body
    body="$(curl -sS -m 15 -o /tmp/cache_clear_resp.json -w '%{http_code}' \
      -X POST "$URL" 2>/tmp/cache_clear_err.txt || true)"
    code="${body:-000}"
    if [ "$code" = "200" ] && grep -q '"ok"[[:space:]]*:[[:space:]]*true' \
      /tmp/cache_clear_resp.json 2>/dev/null; then
      echo "[$(date +%H:%M:%S)] cache tile server svuotata ($URL)"
      rm -f /tmp/cache_clear_resp.json /tmp/cache_clear_err.txt
      return 0
    fi
    if [ "$attempt" -lt "$ATTEMPTS" ]; then
      sleep "$((attempt * 2))"
    fi
  done

  rm -f /tmp/cache_clear_resp.json
  err="$(cat /tmp/cache_clear_err.txt 2>/dev/null || true)"
  rm -f /tmp/cache_clear_err.txt
  echo "[$(date +%H:%M:%S)] ATTENZIONE: impossibile svuotare la cache del tile server" \
    "(HTTP $code${err:+ — $err})" >&2
  echo "[$(date +%H:%M:%S)]    URL: $URL — se il server è spento, al prossimo avvio" \
    "rileggerà le tile nuove da disco." >&2
  if [ "${CACHE_CLEAR_FAIL_FATAL:-0}" = "1" ]; then
    return 1
  fi
  return 0
}

_clear_cache
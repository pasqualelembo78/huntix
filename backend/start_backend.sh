#!/usr/bin/env bash
# Avvia il backend Huntix in modo completamente staccato (nuova sessione),
# così non tiene aperta la pipe del terminale. Log su /tmp/huntix_backend.log.
set -u
cd "$(dirname "$0")"

PORT="${HUNTIX_BACKEND_PORT:-5100}"

# kill di un'eventuale backend huntix sulla STESSA porta (5100).
# NOTA: match sulla porta per NON uccidere altri backend (es. aria su 5000)
# che usano lo stesso nome modulo app:socket_app.
pkill -f "app:socket_app --host 0.0.0.0 --port $PORT" 2>/dev/null || true
sleep 1

exec setsid ./venv/bin/python3 -m uvicorn app:socket_app \
  --host 0.0.0.0 --port "$PORT" \
  --log-level info \
  >/tmp/huntix_backend.log 2>&1 </dev/null

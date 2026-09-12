#!/usr/bin/env bash
# Driver interattivo degli aggiornamenti mappa/tile. Viene chiamato da
# build_release.sh (fase "aggiorna mappa?") o a mano. Tutte le operazioni
# sono idempotenti: se la mappa è già a posto gli script sottostanti
# dicono "tutto a posto" e non rifanno nulla.
#
# Uso:
#   ./aggiorna_mappa.sh
#
# Passi:
#   1) ITALIA: tutta l'Italia | una regione | una città | nessuna
#   2) MONDO : tutto il mondo | una nazione  | nessuna
#
# Autopilota (nessuna interazione):
#   ./aggiorna_mappa.sh italia            # tutta l'Italia (incrementale)
#   ./aggiorna_mappa.sh regione puglia    # una regione italiana
#   ./aggiorna_mappa.sh citta foggia      # una città italiana
#   ./aggiorna_mappa.sh mondo             # tutte le nazioni del registry
#   ./aggiorna_mappa.sh nazione germania  # una nazione del mondo
set -euo pipefail
cd "$(dirname "$0")" || exit 1
PY=./venv/bin/python

norm_slug() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' \
    | sed "s/[ '’\"]\+/-/g; s/-\+/-/g; s/^-//; s/-$//"
}

nazione_valida() {
  "$PY" - "$1" <<'PYEOF' >/dev/null 2>&1
import json, sys
k = sys.argv[1].lower()
for x in json.load(open('world_countries.json')):
    if x['slug'].lower() == k or x['name'].lower().replace(" ", "-") == k:
        sys.exit(0)
sys.exit(1)
PYEOF
}

nazione_slug() {
  "$PY" - "$1" <<'PYEOF'
import json, sys
k = sys.argv[1].lower()
for x in json.load(open('world_countries.json')):
    if x['slug'].lower() == k or x['name'].lower().replace(" ", "-") == k:
        print(x['slug'])
        sys.exit(0)
sys.exit(1)
PYEOF
}

region_valida() {
  "$PY" - "$1" <<'PYEOF' >/dev/null 2>&1
import json, sys
from osm_regions import REGIONS
sys.exit(0 if sys.argv[1].lower() in REGIONS else 1)
PYEOF
}

aggiorna_italia() {
  echo ">> Aggiorno TUTTA l'Italia in modo incrementale"
  echo "   (scaricamento solo se manca, ri-estrazione solo se i filtri"
  echo "    cambiano, ri-generazione solo delle geo obsolete — grafi intatti)."
  echo "   Per forzare tutto: ./aggiorna_italia.sh refresh"
  ./aggiorna_italia.sh
}

aggiorna_regione_driver() {
  echo ">> Regioni disponibili (slug):"
  "$PY" - <<'PYEOF'
from osm_regions import REGIONS
print(", ".join(sorted(REGIONS)))
PYEOF
  REG=""
  while [ -z "$REG" ]; do
    printf "Quale regione? [invio per saltare] > "
    read -r REG_RAW || true
    [ -z "$REG_RAW" ] && { echo ">> nessuna regione selezionata."; return 0; }
    REG=$(norm_slug "$REG_RAW")
    if region_valida "$REG"; then
      echo ">> Aggiorno la regione $REG ..."
      ./aggiorna_regione.sh "$REG"
      return 0
    fi
    echo "!! Regione sconosciuta: '$REG_RAW'. Riprova."
    REG=""
  done
}

aggiorna_citta_driver() {
  while :; do
    printf "Città italiana? [invio per saltare] > "
    read -r CT || true
    [ -z "$CT" ] && { echo ">> nessuna città selezionata."; return 0; }
    if ./aggiorna_citta.sh "$CT"; then
      return 0
    fi
    echo "!! Città non esistente o non in Italia: '$CT'. Inserisci di nuovo."
  done
}

aggiorna_mondo() {
  echo ">> Aggiorno TUTTO il mondo (ogni nazione del registry) ..."
  echo "   NB: può richiedere MOLTO tempo; ogni nazione è idempotente."
  local slug
  while read -r slug; do
    echo "---+ nazione: $slug +---"
    ./mondo_tile.sh "$slug" || echo "!! problema su $slug (continuo)"
  done < <("$PY" -c "import json; [print(x['slug']) for x in json.load(open('world_countries.json'))]")
}

aggiorna_nazione_driver() {
  echo ">> Nazioni disponibili (slug — nome):"
  "$PY" - <<'PYEOF'
import json
for x in json.load(open('world_countries.json')):
    print(f"  {x['slug']:20s} {x['name']}")
PYEOF
  SLUG=""
  while [ -z "$SLUG" ]; do
    printf "Quale nazione? [invio per saltare] > "
    read -r SLUG_RAW || true
    [ -z "$SLUG_RAW" ] && { echo ">> nessuna nazione selezionata."; return 0; }
    SLUG=$(norm_slug "$SLUG_RAW")
    if nazione_valida "$SLUG"; then
      SLUG=$(nazione_slug "$SLUG")
      echo ">> Aggiorno la nazione $SLUG ..."
      ./mondo_tile.sh "$SLUG"
      return 0
    fi
    echo "!! Nazione sconosciuta: '$SLUG_RAW'. Riprova."
    SLUG=""
  done
}

# ── Autopilota (argomenti, non interattivo) ─────────────────────
if [ "${1:-}" = "italia" ]; then
  aggiorna_italia; exit $?
elif [ "${1:-}" = "mondo" ]; then
  aggiorna_mondo; exit $?
elif [ "${1:-}" = "regione" ]; then
  REG=$(norm_slug "${2:-}")
  [ -z "$REG" ] && { echo "uso: $0 regione <slug>"; exit 1; }
  if region_valida "$REG"; then
    ./aggiorna_regione.sh "$REG"
  else
    echo "!! Regione sconosciuta: '$2'."; echo "   Oggetti a seguire: veneto, puglia, molise, lombardia, sicilia, ..."; exit 1
  fi
  exit 0
elif [ "${1:-}" = "citta" ]; then
  CT="${2:-}"
  [ -z "$CT" ] && { echo "uso: $0 citta <nome>"; exit 1; }
  ./aggiorna_citta.sh "$CT"; exit $?
elif [ "${1:-}" = "nazione" ]; then
  SLUG=$(norm_slug "${2:-}")
  [ -z "$SLUG" ] && { echo "uso: $0 nazione <slug|nome>"; exit 1; }
  if nazione_valida "$SLUG"; then
    SLUG=$(nazione_slug "$SLUG")
    ./mondo_tile.sh "$SLUG"; exit $?
  else
    echo "!! Nazione sconosciuta: '$2'."; echo "   Elenco: ./mondo_tile.sh --list"; exit 1
  fi
elif [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  grep "^#" "$0" | grep -vE "^# *─" | sed 's/^# \{0,1\}//' | head -30
  exit 0
fi

# ── Interattivo ───────────────────────────────────────────────────
echo "============================================================"
echo " AGGIORNAMENTO MAPPA"
echo "============================================================"

echo "== 1/2 — ITALIA =="
echo "   1) tutta l'Italia (aggiornamento completo)"
echo "   2) una regione italiana"
echo "   3) una città italiana"
echo "   4) nessuna operazione Italia"
printf "Scelta [1-4, invio = 4] > "
read -r IT || true
case "${IT:-4}" in
  1) aggiorna_italia ;;
  2) aggiorna_regione_driver ;;
  3) aggiorna_citta_driver ;;
  *) echo ">> nessuna operazione Italia." ;;
esac

echo
echo "== 2/2 — MONDO =="
echo "   1) tutto il mondo (ogni nazione del registry)"
echo "   2) una nazione del mondo"
echo "   3) nessuna operazione Mondo"
printf "Scelta [1-3, invio = 3] > "
read -r MO || true
case "${MO:-3}" in
  1) aggiorna_mondo ;;
  2) aggiorna_nazione_driver ;;
  *) echo ">> nessuna operazione Mondo." ;;
esac

echo
echo "============================================================"
echo " Fase aggiornamento mappa terminata (idempotente)."
echo " La cache del tile server viene svuotata automaticamente"
echo " da ./cache_clear.sh al termine di ogni script di aggiornamento."
echo "============================================================"
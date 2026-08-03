#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
#  ricorrenze.sh — Controlla e risolve da solo le attività
#  ricorrenti di Huntix. Spiegazioni in ricorrenze.md.
#
#  Uso:
#    ./ricorrenze.sh          # backup + login + deploy regole
#    ./ricorrenze.sh --apk    # come sopra + rebuild APK debug
# ═══════════════════════════════════════════════════════════════
set -uo pipefail
cd "$(dirname "$0")"

NEED_APK=0
for a in "$@"; do
  case "$a" in
    --apk) NEED_APK=1 ;;
    *) echo "Opzione sconosciuta: $a"; exit 1 ;;
  esac
done

echo "════════════════════════════════════════════════════════════"
echo "  RICORRENZE — Huntix"
echo "════════════════════════════════════════════════════════════"

# ── 1) FILE DI BACKUP ──────────────────────────────────────────
echo ""
echo "1) VERIFICA FILE DI BACKUP"
BACKUP_FILES=(
  "huntix-release.keystore"
  "keystore.properties"
  "app/google-services.json"
  ".firebaserc"
  "firebase.json"
  "backend/.env"
)
missing=0
for f in "${BACKUP_FILES[@]}"; do
  if [ -f "$f" ]; then echo "   [OK]      $f"; else echo "   [MANCANTE] $f"; missing=1; fi
done
if [ "$missing" != "0" ]; then
  echo "   ⚠️  Copia i file MANCANTI in un backup esterno (chiavi firma/API)."
else
  echo "   [OK] Tutti i file di backup ci sono."
fi

# ── 2) LOGIN FIREBASE CLI ──────────────────────────────────────
echo ""
echo "2) LOGIN FIREBASE CLI"
if ! command -v firebase >/dev/null 2>&1; then
  [ -s "$HOME/.nvm/nvm.sh" ] && { # shellcheck disable=SC1091
    . "$HOME/.nvm/nvm.sh"; nvm use 20 >/dev/null 2>&1 || nvm use node >/dev/null 2>&1 || true
  }
fi
if command -v firebase >/dev/null 2>&1 && firebase projects:list --json 2>/dev/null | grep -q "easter-egg-hunt-ar"; then
  echo "   [OK] Login attivo per il progetto easter-egg-hunt-ar"
else
  echo "   [MANCANTE] Il login Firebase è scaduto. Fai così:"
  echo ""
  echo "   source ~/.nvm/nvm.sh && nvm use 20"
  echo "   firebase login --no-localhost"
  echo ""
  echo "   → apri l'URL nel browser del tuo PC, accedi con l'account,"
  echo "     incolla il codice nel terminale, poi rilancia ./ricorrenze.sh"
  exit 1
fi

# ── 3) DEPLOY REGOLE (Firestore + Realtime Database) ───────────
echo ""
echo "3) DEPLOY REGOLE (Firestore + RTDB)"
./deploy_rules.sh || { echo "   ERRORE nel deploy regole."; exit 1; }

# ── 4) (OPZIONALE) REBUILD APK ─────────────────────────────────
if [ "$NEED_APK" = "1" ]; then
  echo ""
  echo "4) REBUILD APK DEBUG"
  ./gradlew :app:assembleDebug -q || { echo "   ERRORE nella build."; exit 1; }
  ls -la app/build/outputs/apk/debug/app-debug.apk
fi

# ── RIEPILOGO AZIONI MANUALI ───────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════════"
echo "  FATTO. Restano SOLO queste attività manuali:"
echo "════════════════════════════════════════════════════════════"
echo "  [ ] OAuth client ID Android (una tantum) — Google Cloud"
echo "      Console → Credentials → OAuth client ID → Android"
echo "      package: com.intelligame.huntix"
echo "      SHA-1:   release  D3:53:83:7C:E8:CD:D4:B9:5C:C0:83:34:2E:CF:EE:67:9E:87:75:72"
echo "      SHA-1:   debug    9F:C8:3A:9A:45:7E:41:6B:FA:EA:4C:A9:A0:51:B4:E0:76:22:CA:7F"
echo "      Serve per le stanze AR keyless (persistenza 30+ gg)."
echo "  [ ] Installare app-debug.apk sul telefono e testare"
echo "      (ARCore non gira su emulatore)."
echo "  [ ] Pubblicazione Google Play (se prevista): upload AAB,"
echo "      Play App Signing, ecc."
echo ""
echo "  Dettagli: vedi ricorrenze.md"

#!/usr/bin/env bash
# Deploy delle Firestore security rules di Huntix.
#
#  1) Estrae la sezione Firestore dal documento master firebase_security_rules.txt
#     (sezione che inizia con `rules_version = '2';` alla riga 128) in firestore.rules.
#  2) Se firebase CLI è installato, esegue il deploy.
#  3) Altrimenti stampa le istruzioni per il deploy manuale dalla console.
set -e
cd "$(dirname "$0")/.."

# firebase-tools richiede Node >= 18: se c'è nvm, attiva una versione adeguata.
if ! command -v firebase >/dev/null 2>&1; then
  if [ -s "$HOME/.nvm/nvm.sh" ]; then
    # shellcheck disable=SC1091
    . "$HOME/.nvm/nvm.sh"
    nvm use 20 >/dev/null 2>&1 || nvm use node >/dev/null 2>&1 || true
  fi
fi

# Estrae la sezione Firestore dal documento master (firebase/firebase_security_rules.txt),
# partendo da `rules_version` e fermandosi alla chiusura di `service cloud.firestore`
# (conteggio bilanciato delle parentesi: il doc contiene anche le moderation rules RTDB).
awk '
  /^rules_version/ { start = 1 }
  start {
    print
    opens += gsub(/\{/, "{")
    closes += gsub(/\}/, "}")
    if (opens > 0 && opens == closes) exit
  }
' firebase/firebase_security_rules.txt > firebase/firestore.rules
if [ ! -s firebase/firestore.rules ]; then
  echo "ERRORE: sezione Firestore non trovata in firebase/firebase_security_rules.txt" >&2
  exit 1
fi
echo ">> Regole estratte in firebase/firestore.rules ($(wc -l < firebase/firestore.rules) righe)"

if ! command -v firebase >/dev/null 2>&1; then
  echo ""
  echo "⚠️  firebase CLI non installato. Due opzioni:"
  echo ""
  echo "  A) Installalo (serve Node >= 18) e rilancia:"
  echo "     source ~/.nvm/nvm.sh && nvm use 20"
  echo "     npm install -g firebase-tools"
  echo "     firebase login --no-localhost"
  echo "     ./deploy_rules.sh"
  echo ""
  echo "  B) Deploy manuale dalla console:"
  echo "     1) Vai su https://console.firebase.google.com/project/easter-egg-hunt-ar/firestore/rules"
  echo "     2) Incolla il contenuto di firebase/firestore.rules (o della sezione FIRESTORE di firebase/firebase_security_rules.txt)"
  echo "     3) Pubblica"
  exit 1
fi

echo ">> firebase CLI: $(firebase --version)"
firebase deploy --only firestore:rules

# ═══════════════════════════════════════════════════════════════
#  REGOLE REALTIME DATABASE (indoor_rooms, outdoor_rooms, chat…)
#  Fonte: firebase/rtdb.rules (stato di produzione — allineato al live).
# ═══════════════════════════════════════════════════════════════
if [ ! -f firebase/rtdb.rules ]; then
  echo "⚠️  firebase/rtdb.rules non trovato — salto il deploy RTDB." >&2
  exit 0
fi

# Ottiene un access token valido (rinnova via refresh_token se scaduto),
# letto dalla sessione di `firebase login` in ~/.config/configstore/firebase-tools.json
RTDB_TOKEN=$(node -e '
const https = require("https"), fs = require("fs"), os = require("os");
const p = os.homedir() + "/.config/configstore/firebase-tools.json";
let j;
try { j = JSON.parse(fs.readFileSync(p, "utf8")); } catch (e) { process.exit(1); }
const t = j.tokens || {};
if (t.access_token && Date.now() < Number(t.expires_at)) { process.stdout.write(t.access_token); process.exit(0); }
if (!t.refresh_token) process.exit(1);
const body = "client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com"
  + "&grant_type=refresh_token&refresh_token=" + encodeURIComponent(t.refresh_token);
const req = https.request("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded", "Content-Length": Buffer.byteLength(body) }
}, res => { let d = ""; res.on("data", c => d += c); res.on("end", () => {
  try { const o = JSON.parse(d); if (o.access_token) { process.stdout.write(o.access_token); process.exit(0); } } catch (e) {}
  process.exit(1);
}); });
req.write(body); req.end();
')

if [ -z "$RTDB_TOKEN" ]; then
  echo "⚠️  Impossibile ottenere l'access token (login Firebase scaduto?)." >&2
  echo "    Esegui: source ~/.nvm/nvm.sh && nvm use 20 && firebase login --no-localhost" >&2
  exit 1
fi

RTDB_URL="https://easter-egg-hunt-ar-default-rtdb.europe-west1.firebasedatabase.app"
echo ">> Deploy regole Realtime Database (firebase/rtdb.rules)..."
HTTP=$(curl -s -o /tmp/rtdb_rules_deploy_resp.json -w "%{http_code}" -X PUT \
  -H "Authorization: Bearer $RTDB_TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @firebase/rtdb.rules "$RTDB_URL/.settings/rules.json")
echo "   HTTP $HTTP — $(cat /tmp/rtdb_rules_deploy_resp.json)"
if [ "$HTTP" != "200" ]; then exit 1; fi

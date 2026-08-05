#!/usr/bin/env bash
#
# ensure_unity_license.sh — Verifica/ripristina la licenza Unity Personal
# prima di una build. Auto-sufficiente e idempotente.
#
# Cosa fa (in ordine):
#   1. Verifica che systemd esista; se il servizio unity-license manca,
#      lo REINSTALLA (unit + wrapper) e lo abilita.
#   2. Avvia il servizio (Xvfb :99 + Unity Hub) se non attivo, aspettando
#      che il CDP del Hub risponda su :9222.
#   3. CHECK VELOCE (ogni build): servizio attivo + XML licenza valida
#      (UpdateDate > ora) + Hub firmato. Se tutto OK -> exit 0.
#   4. CHECK PROFONDO (solo se il veloce fallisce): run batchmode
#      dell'editor -> "Successfully resolved entitlement details".
#      Questa run RIGENERA anche UnityEntitlementLicense.xml.
#   5. RE-LOGIN AUTONOMO (solo se anche il profondo fallisce): ri-login
#      al Unity Hub con le credenziali (ENV o file) e nuova run editor.
#      Se Unity chiede il codice 2FA via email, scrive un messaggio
#      chiaro e attende il codice nel file indicato.
#
# Env:
#   SKIP_UNITY_LICENSE_CHECK=1   salta tutto (per build senza Unity)
#   UNITY_EDITOR                 path editor (default: ultima in /root/Unity/Hub/Editor)
#   UNITY_PROJECT                path progetto Unity (default: <repo>/unity-project)
#   UNITY_HUB_CDP_PORT           porta CDP Hub (default 9222)
#   UNITY_EMAIL / UNITY_PASSWORD credenziali Unity ID (altrimenti lette da file)
#   UNITY_CREDENTIALS_FILE       file credenziali (default /root/.unity-credentials,
#                                formato "email=...\npassword=...", chmod 600)
#   UNITY_2FA_FILE               file dove scrivere il codice OTP (default
#                                /tmp/opencode/unity-lic/2fa.txt)
#   UNITY_LICENSE_LOG            file di log (default <repo>/unitylic/unity-license-check.log)
#   UNITY_LICENSE_XML            path XML licenza (default ~/.config/unity3d/Unity/licenses/UnityEntitlementLicense.xml)
#   UNITY_LICENSE_FORCE_REINSTALL=1  reinstalla sempre unit+wrapper
#
# Exit: 0 = licenza OK, 1 = licenza NON OK (dopo tutti i tentativi), 2 = errori
#       di configurazione che non bloccano la build (es. systemd assente).
#
set -u
cd "$(dirname "$0")/.."   # repo root

REPO_ROOT="$(pwd)"
TOOLS_DIR="$REPO_ROOT/unitylic/tools"

# ── Configurazione (con default) ─────────────────────────────
CDP_PORT="${UNITY_HUB_CDP_PORT:-9222}"
CDP_URL="http://localhost:${CDP_PORT}/json/version"
LICENSE_XML="${UNITY_LICENSE_XML:-$HOME/.config/unity3d/Unity/licenses/UnityEntitlementLicense.xml}"
LOG_FILE="${UNITY_LICENSE_LOG:-$REPO_ROOT/unitylic/unity-license-check.log}"
CREDS_FILE="${UNITY_CREDENTIALS_FILE:-/root/.unity-credentials}"
TFA_FILE="${UNITY_2FA_FILE:-/tmp/opencode/unity-lic/2fa.txt}"
NODE="${NODE:-/root/.nvm/versions/node/v20.20.2/bin/node}"
[ -x "$NODE" ] || NODE="$(command -v node || true)"
UNITY_PROJECT="${UNITY_PROJECT:-$REPO_ROOT/unity-project}"

log()  { echo "$(date '+%F %T') | $*"; echo "$(date '+%F %T') | $*" >> "$LOG_FILE"; }
warn() { echo "$(date '+%F %T') | WARN | $*"; echo "$(date '+%F %T') | WARN | $*" >> "$LOG_FILE"; }
die()  { echo "$(date '+%F %T') | FAIL | $*" | tee -a "$LOG_FILE" >&2; }

# ── 0) Skip totale ───────────────────────────────────────────
if [ "${SKIP_UNITY_LICENSE_CHECK:-0}" = "1" ]; then
    echo ">> [unity-lic] SKIP_UNITY_LICENSE_CHECK=1: verifica licenza saltata."
    exit 0
fi
mkdir -p "$(dirname "$LOG_FILE")"
echo "" >> "$LOG_FILE"
log "=== ensure_unity_license.sh ==="

# ── 1) systemd: esiste? ──────────────────────────────────────
have_systemd() {
    [ -d /run/systemd/system ] && command -v systemctl >/dev/null 2>&1
}

if ! have_systemd; then
    warn "systemd NON disponibile: impossibile gestire il servizio licenze."
    warn "Procedo con la sola validazione (nessuna garanzia di rinnovo automatico)."
    SYSTEMD=0
else
    SYSTEMD=1
fi

# ── 2) Servizio unity-license: installa/ripara/avvia ─────────
SERVICE_UNIT="/etc/systemd/system/unity-license.service"
KEEPER_SCRIPT="/usr/local/bin/unity-license-keeper.sh"

if [ "$SYSTEMD" = "1" ]; then
    # Self-heal: reinstalla unit + wrapper se mancanti o se richiesto
    NEED_INSTALL=0
    if [ ! -f "$SERVICE_UNIT" ] || [ ! -f "$KEEPER_SCRIPT" ]; then NEED_INSTALL=1; fi
    if [ "${UNITY_LICENSE_FORCE_REINSTALL:-0}" = "1" ]; then NEED_INSTALL=1; fi

    if [ "$NEED_INSTALL" = "1" ]; then
        log "Servizio unity-license mancante/obsoleto — reinstallo..."
        cat > "$KEEPER_SCRIPT" <<'KEEPER'
#!/bin/bash
# Unity license keeper: keeps Xvfb + Unity Hub alive so the
# Licensing.Client daemon keeps the session token and renews the
# UnityEntitlementLicense.xml (Personal license, ~30 day offline window).
export DISPLAY=:99
export HOME=/root

if ! pgrep -x Xvfb >/dev/null; then
  Xvfb :99 -screen 0 1280x1024x24 >/tmp/unity-xvfb.log 2>&1 &
  sleep 2
fi

exec /usr/bin/unityhub --no-sandbox --remote-debugging-port=9222 --disable-gpu
KEEPER
        chmod +x "$KEEPER_SCRIPT"

        cat > "$SERVICE_UNIT" <<'UNIT'
[Unit]
Description=Unity Hub under Xvfb (keeps Unity Personal license token alive)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/unity-license-keeper.sh
Restart=always
RestartSec=10
KillMode=control-group
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
UNIT
        systemctl daemon-reload
        log "Servizio unity-license reinstalled."
    fi

    # Avvia/abilita se non attivo
    if ! systemctl is-active --quiet unity-license; then
        log "Avvio servizio unity-license (systemctl enable --now)..."
        systemctl enable unity-license >/dev/null 2>&1 || true
        systemctl start unity-license || true
    fi

    # Attendi CDP (max ~90s)
    log "Attendo il Unity Hub sul CDP ($CDP_URL)..."
    ok=0
    for _ in $(seq 1 45); do
        if curl -s -o /dev/null -m 2 "$CDP_URL" 2>/dev/null; then ok=1; break; fi
        sleep 2
    done
    if [ "$ok" = "0" ]; then
        die "Unity Hub non raggiungibile dopo 90s. Verifica servizio: systemctl status unity-license"
        exit 1
    fi
    log "Unity Hub attivo (CDP ok)."
fi

# ── Helper: XML valida? ──────────────────────────────────────
xml_valid() {
    [ -f "$LICENSE_XML" ] || return 1
    local update_ts now_ts
    update_ts=$(grep -oE '<UpdateDate>[^<]+</UpdateDate>' "$LICENSE_XML" | head -1 | sed -E 's/<\/?UpdateDate>//g' | tr -d ' \n')
    if [ -z "$update_ts" ]; then
        update_ts=$(grep -oE 'IssueDate="[^"]+"' "$LICENSE_XML" | head -1 | cut -d'"' -f2)
    fi
    [ -z "$update_ts" ] && return 1
    now_ts=$(date -u +%s)
    upd_ts=$(date -u -d "$update_ts" +%s 2>/dev/null) || return 1
    [ "$upd_ts" -gt "$now_ts" ]
}

# ── Helper: editor valida la licenza (rigenera anche la XML) ─
editor_validates() {
    local editor="$1"
    local logf="/tmp/unity-license-editor-check.log"
    log "Check profondo: editor batchmode (può richiedere 1-3 min)..."
    env -u DISPLAY timeout 300 "$editor" -batchmode -quit -nographics \
        -projectPath "$UNITY_PROJECT" -logFile "$logf" >/dev/null 2>&1
    if grep -q "Successfully resolved entitlement details" "$logf"; then
        return 0
    fi
    return 1
}

# ── Helper: Hub firmato? ─────────────────────────────────────
hub_signed_in() {
    [ -x "$NODE" ] || return 2
    [ -f "$TOOLS_DIR/hub-probe.js" ] || return 2
    UNITY_HUB_CDP_PORT="$CDP_PORT" "$NODE" "$TOOLS_DIR/hub-probe.js" 2>/dev/null
}

# ── 3) CHECK VELOCE ──────────────────────────────────────────
log "Check veloce licenza..."
FAST_OK=1
if [ "$SYSTEMD" = "1" ] && ! systemctl is-active --quiet unity-license; then
    warn "servizio unity-license non attivo"; FAST_OK=0
fi
if ! xml_valid; then
    warn "XML licenza assente o scaduta: $LICENSE_XML"; FAST_OK=0
fi
# Il Hub appena riavviato impiega qualche secondo a ripristinare la sessione:
# ritenta il probe fino a ~15s prima di dichiararlo non firmato.
hub_state=""; hub_rc=2
for _ in $(seq 1 5); do
    hub_state="$(hub_signed_in)"; hub_rc=$?
    [ "$hub_rc" = "0" ] && break
    sleep 3
done
if [ "$hub_rc" != "0" ]; then
    warn "Hub non firmato (probe rc=$hub_rc)"; FAST_OK=0
else
    log "Hub firmato: $(echo "$hub_state" | grep -oE '"email":"[^"]*"' | cut -d'"' -f4)"
fi

if [ "$FAST_OK" = "1" ]; then
    log "LICENZA OK (check veloce)."
    exit 0
fi

# ── 4) CHECK PROFONDO (editor run: rigenera la XML) ──────────
EDITOR="${UNITY_EDITOR:-}"
if [ -z "$EDITOR" ]; then
    EDITOR=$(ls -d /root/Unity/Hub/Editor/*/Editor/Unity 2>/dev/null | sort -V | tail -1)
fi
if [ -z "$EDITOR" ] || [ ! -x "$EDITOR" ]; then
    die "Editor Unity non trovato (UNITY_EDITOR non impostato né rilevabile in /root/Unity/Hub/Editor)."
    exit 1
fi
log "Editor: $EDITOR"

if [ "$SYSTEMD" = "1" ]; then
    # riprova prima a far rigenerare la licenza col Hub già su
    if editor_validates "$EDITOR"; then
        log "LICENZA OK (rigenerata dal check profondo)."
        exit 0
    fi
fi

# ── 5) RE-LOGIN AUTONOMO ─────────────────────────────────────
log "Licenza non valida: tento il re-login automatico al Unity Hub..."

if [ -z "${UNITY_EMAIL:-}" ] || [ -z "${UNITY_PASSWORD:-}" ]; then
    if [ -f "$CREDS_FILE" ]; then
        UNITY_EMAIL="${UNITY_EMAIL:-$(grep '^email=' "$CREDS_FILE" | cut -d= -f2-)}"
        UNITY_PASSWORD="${UNITY_PASSWORD:-$(grep '^password=' "$CREDS_FILE" | cut -d= -f2-)}"
        log "Credenziali lette da $CREDS_FILE"
    fi
fi

if [ -z "${UNITY_EMAIL:-}" ] || [ -z "${UNITY_PASSWORD:-}" ]; then
    die "Re-login impossibile: nessuna credenziale (export UNITY_EMAIL/UNITY_PASSWORD o crea $CREDS_FILE con email=/password=, chmod 600)."
    exit 1
fi

[ -x "$NODE" ] || { die "node non disponibile per il re-login (NODE=$NODE)."; exit 1; }
[ -f "$TOOLS_DIR/hub-login.js" ] || { die "hub-login.js non trovato in $TOOLS_DIR."; exit 1; }
[ -d "$TOOLS_DIR/node_modules" ] || {
    log "Installo puppeteer-core in $TOOLS_DIR (npm install)..."
    npm_bin="$(dirname "$NODE")/npm"
    [ -x "$npm_bin" ] || npm_bin="$(command -v npm || true)"
    if [ -x "$npm_bin" ]; then
        ( cd "$TOOLS_DIR" && "$npm_bin" install puppeteer-core --no-audit --no-fund >/dev/null 2>&1 ) || true
    fi
    [ -d "$TOOLS_DIR/node_modules" ] || { die "npm install puppeteer-core fallito in $TOOLS_DIR."; exit 1; }
}

log "Eseguo hub-login.js (se richiede 2FA scriverà un messaggio)..."
UNITY_HUB_CDP_PORT="$CDP_PORT" \
UNITY_2FA_FILE="$TFA_FILE" \
UNITY_LOG_FILE="$LOG_FILE" \
UNITY_EMAIL="$UNITY_EMAIL" UNITY_PASSWORD="$UNITY_PASSWORD" \
"$NODE" "$TOOLS_DIR/hub-login.js" && RC_LOGIN=0 || RC_LOGIN=1

if [ "$RC_LOGIN" = "0" ]; then
    log "Re-login riuscito. Rigenero la licenza con l'editor..."
    if editor_validates "$EDITOR"; then
        log "LICENZA OK (rigenerata dopo re-login)."
        exit 0
    fi
    die "Re-login ok ma l'editor non ha ancora validato la licenza."
    exit 1
fi

die "Re-login fallito. Procedi manualmente: apri il Unity Hub, accedi," \
    "poi rilancia la build. Dettagli nel log: $LOG_FILE"
exit 1

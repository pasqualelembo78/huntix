#!/usr/bin/env bash
#
# build_release.sh — Compila e firma l'APK/AAB release di Huntix
#
# Logica di build/firma ripresa da build_app.sh (aria):
#   - rilevamento ANDROID_HOME
#   - creazione local.properties
#   - generazione keystore se assente (idempotente)
#   - assembleRelease + bundleRelease
#   - firma APK con apksigner / zipalign
#
# Uso:
#   ./build_release.sh            # interattivo (chiede password/alias)
#   ./build_release.sh --auto     # usa valori di default per il nuovo keystore
#
set -euo pipefail

cd "$(dirname "$0")"

KEYSTORE_FILE="huntix-release.keystore"
PROPS_FILE="keystore.properties"

# ── Rileva configurazione di firma ────────────────────────
# Carica i valori da keystore.properties se presente (anche parziale)
if [ -f "$PROPS_FILE" ]; then
    STORE_FILE_VAL=$(grep '^storeFile=' "$PROPS_FILE" | cut -d= -f2-)
    STORE_PASS=$(grep '^storePassword=' "$PROPS_FILE" | cut -d= -f2-)
    KEY_ALIAS=$(grep '^keyAlias=' "$PROPS_FILE" | cut -d= -f2-)
    KEY_PASS=$(grep '^keyPassword=' "$PROPS_FILE" | cut -d= -f2-)
    [ -n "$STORE_FILE_VAL" ] && KEYSTORE_FILE="$STORE_FILE_VAL"
fi

# Genera/ricrea il keystore se il file fisico non esiste
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo ">> Keystore $KEYSTORE_FILE assente — lo genero..."

    if [ "${1:-}" = "--auto" ]; then
        STORE_PASS="${STORE_PASS:-huntix123}"
        KEY_ALIAS="${KEY_ALIAS:-huntix}"
        KEY_PASS="${KEY_PASS:-huntix123}"
        CN="Huntix"
        OU="Huntix"
        O="Huntix"
        L="IT"
        S="IT"
        C="IT"
    else
        [ -z "${STORE_PASS:-}" ] && { read -rsp "Password keystore: " STORE_PASS; echo; }
        [ -z "${KEY_ALIAS:-}" ]  && { read -rp "Alias chiave: " KEY_ALIAS; }
        [ -z "${KEY_PASS:-}" ]  && { read -rsp "Password chiave: " KEY_PASS; echo; }
        read -rp "Nome e cognome (CN): " CN
        read -rp "Unità organizzativa (OU): " OU
        read -rp "Organizzazione (O): " O
        read -rp "Città/Località (L): " L
        read -rp "Stato/Provincia (S): " S
        read -rp "Paese (C, es. IT): " C
    fi

    keytool -genkeypair \
        -v \
        -keystore "$KEYSTORE_FILE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$STORE_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=${CN:-Huntix}, OU=${OU:-Huntix}, O=${O:-Huntix}, L=${L:-IT}, S=${S:-IT}, C=${C:-IT}"

    # Aggiorna/Riscrive keystore.properties con i parametri di firma effettivi
    if [ -f "$PROPS_FILE" ]; then
        grep -v '^storeFile=\|^storePassword=\|^keyAlias=\|^keyPassword=' "$PROPS_FILE" > "${PROPS_FILE}.tmp" || true
        mv "${PROPS_FILE}.tmp" "$PROPS_FILE"
    fi
    {
        echo "storeFile=$KEYSTORE_FILE"
        echo "storePassword=$STORE_PASS"
        echo "keyAlias=$KEY_ALIAS"
        echo "keyPassword=$KEY_PASS"
        [ -f "$PROPS_FILE" ] && cat "$PROPS_FILE"
    } > "${PROPS_FILE}.new"
    mv "${PROPS_FILE}.new" "$PROPS_FILE"
    echo ">> $PROPS_FILE aggiornato con i parametri di firma."
else
    echo ">> Keystore $KEYSTORE_FILE già presente."
fi

# ── Localizza gli strumenti Android (logica da build_app.sh) ──
if [ -z "${ANDROID_HOME:-}" ]; then
    for dir in "$HOME/Android/Sdk" /opt/android-sdk /usr/lib/android-sdk; do
        if [ -d "$dir" ]; then
            export ANDROID_HOME="$dir"
            break
        fi
    done
    if [ -z "${ANDROID_HOME:-}" ]; then
        echo "!! ANDROID_HOME non impostato e SDK non trovato nei path standard." >&2
        exit 1
    fi
fi

if [ ! -f "local.properties" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo ">> Creato local.properties (sdk.dir=$ANDROID_HOME)"
fi

echo ">> ANDROID_HOME=$ANDROID_HOME"

BUILD_TOOLS=$(ls -d "$ANDROID_HOME/build-tools"/* 2>/dev/null | sort -V | tail -1)
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

if [ ! -x "$APKSIGNER" ] || [ ! -x "$ZIPALIGN" ]; then
    echo "!! zipalign/apksigner non trovati in $BUILD_TOOLS" >&2
    exit 1
fi

# ── Build APK + AAB (logica da build_app.sh) ───────────────
chmod +x gradlew

APK_DIR="app/build/outputs/apk/release"
# Pulisci eventuali output di build precedenti (evita zipalign input==output)
rm -f "$APK_DIR"/*.apk

echo ">> Building APK (assembleRelease)..."
./gradlew assembleRelease -PkeystorePropsFile="$PROPS_FILE" --no-daemon --console=plain
UNSIGNED_APK="${APK_DIR}/app-release.apk"

echo ">> Building AAB (bundleRelease)..."
./gradlew bundleRelease -PkeystorePropsFile="$PROPS_FILE" --no-daemon --console=plain
AAB_FILE="app/build/outputs/bundle/release/app-release.aab"

if [ -f "$AAB_FILE" ]; then
    AAB_SIZE=$(stat -c%s "$AAB_FILE" 2>/dev/null || stat -f%z "$AAB_FILE" 2>/dev/null)
    AAB_SIZE_MB=$(echo "scale=1; $AAB_SIZE/1048576" | bc)
    echo ">> AAB: $AAB_FILE (${AAB_SIZE_MB}MB)"
else
    echo "!! AAB build failed!"
    exit 1
fi

if [ -z "$UNSIGNED_APK" ]; then
    echo "!! APK non trovato dopo la build." >&2
    exit 1
fi

# ── Verifica se è già firmato ──────────────────────────────
if "$APKSIGNER" verify "$UNSIGNED_APK" >/dev/null 2>&1; then
    echo ">> $UNSIGNED_APK è già firmato. Nessuna azione necessaria."
    exit 0
fi

# ── Allinea e firma a parte (logica originale huntix) ──────
ALIGNED_APK="${APK_DIR}/app-release-aligned.apk"
SIGNED_APK="${APK_DIR}/app-release-signed.apk"

echo ">> Allineo con zipalign..."
"$ZIPALIGN" -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

echo ">> Firma con apksigner..."
"$APKSIGNER" sign \
    --ks "$KEYSTORE_FILE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass pass:"$STORE_PASS" \
    --key-pass pass:"$KEY_PASS" \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

echo ">> Verifica firma..."
"$APKSIGNER" verify "$SIGNED_APK"

echo ">> APK firmato: $SIGNED_APK"
echo ">> AAB:          $AAB_FILE"

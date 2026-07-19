#!/usr/bin/env bash
#
# build_release.sh — Compila e firma l'APK release di Huntix
#
# Funziona in due modalità:
#   1. Se non esiste keystore.properties, ne genera uno nuovo (keystore + file props)
#   2. Se esiste, usa quello esistente per firmare l'APK
#
# Uso:
#   ./build_release.sh            # interattivo (chiede password/alias)
#   ./build_release.sh --auto     # usa valori di default per il nuovo keystore
#
set -euo pipefail

cd "$(dirname "$0")"

KEYSTORE_FILE="huntix-release.keystore"
PROPS_FILE="keystore.properties"

# ── Rileva keystore esistente ──────────────────────────────
if [ -f "$PROPS_FILE" ]; then
    echo ">> Trovato $PROPS_FILE esistente, carico la configurazione di firma."
    STORE_FILE_VAL=$(grep '^storeFile=' "$PROPS_FILE" | cut -d= -f2-)
    STORE_PASS=$(grep '^storePassword=' "$PROPS_FILE" | cut -d= -f2-)
    KEY_ALIAS=$(grep '^keyAlias=' "$PROPS_FILE" | cut -d= -f2-)
    KEY_PASS=$(grep '^keyPassword=' "$PROPS_FILE" | cut -d= -f2-)
    [ -n "$STORE_FILE_VAL" ] && KEYSTORE_FILE="$STORE_FILE_VAL"
else
    echo ">> Nessun $PROPS_FILE trovato. Genero un nuovo keystore..."

    if [ "${1:-}" = "--auto" ]; then
        STORE_PASS="huntix123"
        KEY_ALIAS="huntix"
        KEY_PASS="huntix123"
        CN="Huntix"
        OU="Huntix"
        O="Huntix"
        L="IT"
        S="IT"
        C="IT"
    else
        read -rsp "Password keystore: " STORE_PASS; echo
        read -rp "Alias chiave: " KEY_ALIAS
        read -rsp "Password chiave: " KEY_PASS; echo
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
        -dname "CN=$CN, OU=$OU, O=$O, L=$L, S=$S, C=$C"

    cat > "$PROPS_FILE" <<EOF
storeFile=$KEYSTORE_FILE
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS

sentryDsn=
webClientId=
admobAppId=
admobBannerId=
admobInterstitialId=
admobRewardedId=
arcoreApiKey=
mapboxToken=
mapboxDownloadsToken=
EOF

    echo ">> Creato $KEYSTORE_FILE e $PROPS_FILE."
    echo ">> IMPORTANTE: $PROPS_FILE è in .gitignore — conservalo e non perderlo!"
fi

# ── Localizza gli strumenti Android ─────────────────────────
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ -z "$ANDROID_HOME" ]; then
    echo "!! ANDROID_HOME/ANDROID_SDK_ROOT non impostato." >&2
    exit 1
fi
BUILD_TOOLS=$(ls -d "$ANDROID_HOME/build-tools"/* 2>/dev/null | sort -V | tail -1)
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

# ── Trova l'APK da firmare ─────────────────────────────────
APK_DIR="app/build/outputs/apk/release"
UNSIGNED_APK=$(ls "$APK_DIR"/*.apk 2>/dev/null | head -1 || true)

if [ -z "$UNSIGNED_APK" ]; then
    echo ">> Nessun APK trovato in $APK_DIR. Compilo e firmo con Gradle..."
    ./gradlew assembleRelease -PkeystorePropsFile="$PROPS_FILE"
    UNSIGNED_APK=$(ls "$APK_DIR"/*.apk 2>/dev/null | head -1)
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

# ── Allinea e firma a parte (senza ricompilare) ────────────
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

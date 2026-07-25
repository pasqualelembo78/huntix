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
    echo ">> Trovato $PROPS_FILE esistente, uso la configurazione di firma corrente."
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

# ── Compila e firma ────────────────────────────────────────
echo ">> Avvio assembleRelease..."
./gradlew clean assembleRelease -PkeystorePropsFile="$PROPS_FILE"

echo ">> Build completata. APK in app/build/outputs/apk/release/"

#!/usr/bin/env bash
#
# build_release.sh — Compila e firma l'APK/AAB release di Huntix
#
#Portabile da zero: funziona anche subito dopo `git clone`, senza
# keystore.properties. Se manca, genera keystore.properties (solo firma)
# e il keystore, e compila un APK firmato.
#
# Le API key delle funzionalità (AdMob, Sentry, ARCore, Mapbox, Firebase)
# si passano via ENVIRONMENT VARIABLES, NON più in keystore.properties:
#   export ADMOB_APP_ID=ca-app-pub-xxxxx~yyyy
#   export ADMOB_BANNER_ID=ca-app-pub-xxxxx/zzzz
#   export ADMOB_REWARDED_ID=ca-app-pub-xxxxx/wwww
#   export SENTRY_DSN=https://...
#   export ARCORE_API_KEY=AIza...
#   export MAPBOX_TOKEN=pk....
#   export WEB_CLIENT_ID=....apps.googleusercontent.com
# Se non settate, build.gradle usa i default hardcoded (AdMob di Huntix).
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

# ── Configurazione feature (override via ENV, altrimenti da keystore.properties) ──
# Web Client ID per Google Sign-In: priorità ENV > keystore.properties > default
if [ -z "${WEB_CLIENT_ID:-}" ] && [ -f "$PROPS_FILE" ]; then
    WEB_CLIENT_ID=$(grep '^webClientId=' "$PROPS_FILE" | cut -d= -f2-)
fi
WEB_CLIENT_ID="${WEB_CLIENT_ID:-418980419674-mq5d7a5jmbpujj4gfpitngobjcg17km5.apps.googleusercontent.com}"

# ── Assicura un keystore.properties di solo firma (idempotente) ──
# Se non esiste, ne creo uno vuoto: le API key arrivano da ENV (vedi build.gradle).
if [ ! -f "$PROPS_FILE" ]; then
    cat > "$PROPS_FILE" <<EOF
# Generato automaticamente da build_release.sh (solo firma).
# Le API key delle funzionalità vanno via ENV, non qui.
storeFile=$KEYSTORE_FILE
storePassword=huntix123
keyAlias=huntix
keyPassword=huntix123
EOF
    echo ">> Creato $PROPS_FILE (solo firma). Le API key sono lette da ENV."
fi

# ── Rileva configurazione di firma da keystore.properties ──
STORE_FILE_VAL=$(grep '^storeFile=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
STORE_PASS=$(grep '^storePassword=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
KEY_ALIAS=$(grep '^keyAlias=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
KEY_PASS=$(grep '^keyPassword=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
[ -n "$STORE_FILE_VAL" ] && KEYSTORE_FILE="$STORE_FILE_VAL"

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

    echo ">> Keystore $KEYSTORE_FILE generato (alias=$KEY_ALIAS)."
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

# ── Genera app/google-services.json da ENV se mancante ──────
# Evita di usare un google-services.json di un progetto sbagliato.
# ENV richieste: FIREBASE_PROJECT_ID, FIREBASE_APP_ID, FIREBASE_API_KEY,
#                FIREBASE_SENDER_ID (project number)
if [ ! -f "app/google-services.json" ]; then
    if [ -n "${FIREBASE_PROJECT_ID:-}" ] && [ -n "${FIREBASE_APP_ID:-}" ] \
       && [ -n "${FIREBASE_API_KEY:-}" ] && [ -n "${FIREBASE_SENDER_ID:-}" ]; then
        echo ">> Genero app/google-services.json da ENV Firebase..."
        mkdir -p app
        cat > app/google-services.json <<JSON
{
  "project_info": {
    "project_number": "${FIREBASE_SENDER_ID}",
    "project_id": "${FIREBASE_PROJECT_ID}",
    "storage_bucket": "${FIREBASE_PROJECT_ID}.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "${FIREBASE_APP_ID}",
        "android_client_info": { "package_name": "com.intelligame.huntix" }
      },
      "oauth_client": [
        {
          "client_id": "${WEB_CLIENT_ID}",
          "client_type": 3
        }
      ],
      "api_key": [ { "current_key": "${FIREBASE_API_KEY}" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
JSON
        echo ">> app/google-services.json creato."
    else
        echo "!! app/google-services.json ASSENTE. Scaricalo dalla Firebase Console"
        echo "   (Project Settings > app Android com.intelligame.huntix) e mettilo in app/."
        echo "   Oppure esporta FIREBASE_PROJECT_ID, FIREBASE_APP_ID, FIREBASE_API_KEY, FIREBASE_SENDER_ID."
        exit 1
    fi
else
    echo ">> app/google-services.json presente."
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

# Passa le ENV delle feature come -P al gradle (build.gradle dà loro priorità).
# Se non settate, build.gradle usa i default hardcoded.
GRADLE_ENV_PROPS=""
for pair in \
    "SENTRY_DSN:sentryDsn" \
    "ADMOB_APP_ID:admobAppId" \
    "ADMOB_BANNER_ID:admobBannerId" \
    "ADMOB_REWARDED_ID:admobRewardedId" \
    "ADMOB_INTERSTITIAL_ID:admobInterstitialId" \
    "ARCORE_API_KEY:arcoreApiKey" \
    "MAPBOX_TOKEN:mapboxToken" \
    "WEB_CLIENT_ID:webClientId" ; do
    env_name="${pair%%:*}"
    prop_name="${pair##*:}"
    if [ -n "${!env_name:-}" ]; then
        GRADLE_ENV_PROPS="$GRADLE_ENV_PROPS -P$prop_name=${!env_name}"
    fi
done

APK_DIR="app/build/outputs/apk/release"
# Pulisci eventuali output di build precedenti (evita zipalign input==output)
rm -f "$APK_DIR"/*.apk

echo ">> Building APK (assembleRelease)..."
./gradlew assembleRelease -PkeystorePropsFile="$PROPS_FILE" $GRADLE_ENV_PROPS --no-daemon --console=plain
UNSIGNED_APK="${APK_DIR}/app-release-unsigned.apk"

echo ">> Building AAB (bundleRelease)..."
./gradlew bundleRelease -PkeystorePropsFile="$PROPS_FILE" $GRADLE_ENV_PROPS --no-daemon --console=plain
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

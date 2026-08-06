#!/usr/bin/env bash
#
# build_release.sh — Compila e firma l'APK/AAB release di Huntix
#
#Portabile da zero: funziona anche subito dopo `git clone`, senza
# keystore.properties. Se manca, genera keystore.properties (solo firma)
# e il keystore, e compila un APK firmato.
#
# Le API key delle funzionalità (AdMob, Sentry, ARCore, Firebase)
# si passano via ENVIRONMENT VARIABLES, NON più in keystore.properties:
#   export ADMOB_APP_ID=ca-app-pub-xxxxx~yyyy
#   export ADMOB_BANNER_ID=ca-app-pub-xxxxx/zzzz
#   export ADMOB_REWARDED_ID=ca-app-pub-xxxxx/wwww
#   export SENTRY_DSN=https://...
#   export ARCORE_API_KEY=AIza...
#   export WEB_CLIENT_ID=....apps.googleusercontent.com
# Se non settate, build.gradle usa i default hardcoded (AdMob di Huntix).
#
# LICENZA UNITY (automatica):
#   Ad ogni build lo script verifica systemd + servizio unity-license
#   (Xvfb + Unity Hub) + validità della UnityEntitlementLicense.xml.
#   Se qualcosa non va, avvia/ripara il servizio e rigenera la licenza
#   da solo (unitylic/ensure_unity_license.sh). Solo se anche il
#   re-login automatico fallisce, la build si ferma con un messaggio.
#   Per saltare il check: SKIP_UNITY_LICENSE_CHECK=1
#   Credenziali per il re-login automatico (se la sessione è scaduta):
#   export UNITY_EMAIL=... UNITY_PASSWORD=...
#   oppure file /root/.unity-credentials (email=/password=, chmod 600).
#
# INTEGRAZIONE UNITY → AAR (facoltativa, prima dell'APK):
#   All'avvio lo script chiede se serve creare/ricreare il file AAR Unity.
#   Se sì: export del gradle project da Unity (batchmode), build del modulo
#   :unityLibrary (Il2Cpp) con Gradle 7.5.1, copia degli AAR Unity in
#   app/libs/ e wiring automatico del gradle (app dipende dagli AAR Unity,
#   stub unityLibrary disattivato). Se no, l'APK viene compilato contro gli
#   stub unityLibrary (nessuna dipendenza da Unity).
#   Idempotente: se le fonti Unity sono invariate e l'AAR c'è già, salta.
#   ENV utili:
#     BUILD_UNITY_AAR=1   -> risponde "sì" senza chiedere
#     SKIP_UNITY_AAR=1    -> risponde "no" senza chiedere (usa gli stub)
#     FORCE_UNITY_AAR=1   -> forza la ricreazione anche se già aggiornato
#     UNITY_EDITOR=...    -> percorso dell'editor Unity (auto-rilevato)
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

# ── Scelta autorizzazione ARCore Cloud Anchor ──────────────
# api     -> API key nel manifest, persistenza max 24h
# keyless -> OAuth client ID (package + SHA-1), persistenza 30+ giorni
echo "============================================================"
echo " Autorizzazione ARCore Cloud Anchor"
echo "   1) api     -> API key nel manifest  (persistenza max 24h)"
echo "   2) keyless -> OAuth client ID       (persistenza 30+ giorni)"
echo "============================================================"
AUTH_MODE="${AUTH_MODE:-api}"
while [ -z "$AUTH_MODE" ]; do
    read -rp "Vuoi usare 'api' o 'keyless'? [default: api]: " choice
    case "$choice" in
        keyless|Keyless|KEYLESS|2) AUTH_MODE="keyless" ;;
        api|Api|API|1|"")          AUTH_MODE="api" ;;
        *) echo "Inserisci 'api' o 'keyless'." ;;
    esac
done
echo ">> Modalita' scelta: $AUTH_MODE"

# ── Verifica/ripristino licenza Unity (auto-sufficiente) ─────
# Ad ogni build: controlla systemd, avvia il servizio unity-license
# (Xvfb + Unity Hub) se assente/non attivo, valida la licenza e, se ci
# sono problemi, la rigenera in automatico (vedi unitylic/ensure_unity_license.sh).
# Per saltare del tutto: SKIP_UNITY_LICENSE_CHECK=1
echo "============================================================"
echo " Verifica licenza Unity (systemd + Hub + XML)"
echo "============================================================"
UNITY_LICENSE_SCRIPT="$(dirname "$0")/unitylic/ensure_unity_license.sh"
if [ "${SKIP_UNITY_LICENSE_CHECK:-0}" = "1" ]; then
    echo ">> SKIP_UNITY_LICENSE_CHECK=1 — verifica licenza Unity saltata."
elif [ -x "$UNITY_LICENSE_SCRIPT" ]; then
    if "$UNITY_LICENSE_SCRIPT"; then
        echo ">> Licenza Unity: OK."
    else
        echo "!! Licenza Unity NON valida dopo tutti i tentativi automatici." >&2
        echo "   Log: unitylic/unity-license-check.log" >&2
        echo "   Per forzare la build senza check: SKIP_UNITY_LICENSE_CHECK=1" >&2
        exit 1
    fi
else
    echo ">> ensure_unity_license.sh non trovato — verifica licenza saltata."
fi

KEYSTORE_FILE="huntix-release.keystore"
PROPS_FILE="keystore.properties"

# ── Configurazione feature (override via ENV, altrimenti da keystore.properties) ──
# Web Client ID per Google Sign-In: priorità ENV > keystore.properties > default
if [ -z "${WEB_CLIENT_ID:-}" ] && [ -f "$PROPS_FILE" ]; then
    WEB_CLIENT_ID=$(grep '^webClientId=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-) || true
fi
WEB_CLIENT_ID="${WEB_CLIENT_ID:-418980419674-mq5d7a5jmbpujj4gfpitngobjcg17km5.apps.googleusercontent.com}"

# API key Firebase (Android key auto-created, non bloccata). Override via ENV FIREBASE_API_KEY.
FIREBASE_API_KEY="${FIREBASE_API_KEY:-AIzaSyAUghQDddae_P_OFCHVqPCZO8I4q-JJhYE}"

# ── Assicura un keystore.properties di firma (idempotente) ──
# Contiene chiavi di firma + webClientId per Google Sign-In.
# Le API key delle funzionalità vanno via ENV (vedi build.gradle).
if [ ! -f "$PROPS_FILE" ]; then
    cat > "$PROPS_FILE" <<EOF
# Generato automaticamente da build_release.sh (firma + webClientId).
storeFile=$KEYSTORE_FILE
storePassword=huntix123
keyAlias=huntix
keyPassword=huntix123
webClientId=$WEB_CLIENT_ID
EOF
    echo ">> Creato $PROPS_FILE (firma + webClientId)."
fi

# ── Rileva configurazione di firma da keystore.properties ──
STORE_FILE_VAL=$(grep '^storeFile=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
STORE_PASS=$(grep '^storePassword=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
KEY_ALIAS=$(grep '^keyAlias=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
KEY_PASS=$(grep '^keyPassword=' "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
[ -n "$STORE_FILE_VAL" ] && KEYSTORE_FILE="$STORE_FILE_VAL"

# ── File keystore.properties effettivo passato a gradle ──
GRADLE_KEYSTORE_PROPS="$PROPS_FILE"
if [ "$AUTH_MODE" = "keyless" ]; then
    echo ">> Keyless: escludo arcoreApiKey dal manifest (uso OAuth client ID Android)."
    TMP_PROPS="$(dirname "$PROPS_FILE")/keystore_properties_keyless.tmp"
    grep -v '^arcoreApiKey=' "$PROPS_FILE" > "$TMP_PROPS"
    GRADLE_KEYSTORE_PROPS="$TMP_PROPS"
    unset ARCORE_API_KEY
fi

# Genera/ricrea il keystore se il file fisico non esiste
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo ">> Keystore $KEYSTORE_FILE assente — lo genero..."

    if [ ! -f "$KEYSTORE_FILE" ] || [ ! -f "$PROPS_FILE" ]; then
        STORE_PASS="${STORE_PASS:-huntix123}"
        KEY_ALIAS="${KEY_ALIAS:-huntix}"
        KEY_PASS="${KEY_PASS:-huntix123}"
        CN="${CN:-Huntix}"
        OU="${OU:-Huntix}"
        O="${O:-Huntix}"
        L="${L:-IT}"
        S="${S:-IT}"
        C="${C:-IT}"
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

# ── Forza sempre la current_key corretta (evita key bloccate) ──
if [ -f "app/google-services.json" ]; then
    if grep -q '"current_key"' app/google-services.json; then
        sed -i "s/\"current_key\": *\"[^\"]*\"/\"current_key\": \"${FIREBASE_API_KEY}\"/" app/google-services.json
        echo ">> Forzata current_key Firebase in app/google-services.json."
    else
        echo "!! current_key non trovata in app/google-services.json" >&2
        exit 1
    fi
fi

echo ">> ANDROID_HOME=$ANDROID_HOME"

BUILD_TOOLS=$(ls -d "$ANDROID_HOME/build-tools"/* 2>/dev/null | sort -V | tail -1)
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

if [ ! -x "$APKSIGNER" ] || [ ! -x "$ZIPALIGN" ]; then
    echo "!! zipalign/apksigner non trovati in $BUILD_TOOLS" >&2
    exit 1
fi

# ════════════════════════════════════════════════════════════════
#  INTEGRAZIONE UNITY → AAR (facoltativa, prima dell'APK)
#  Chiede se serve creare/ricreare il file AAR Unity. Se sì:
#    1) export del gradle project da Unity (batchmode, licenza verificata sopra)
#    2) build :unityLibrary:assembleRelease (Il2Cpp) con Gradle 7.5.1
#    3) copia AAR + dipendenze Unity in app/libs/
#    4) wiring gradle: app dipende dagli AAR Unity, stub unityLibrary disattivato
#  Idempotente: se le fonti Unity sono invariate e l'AAR c'è già, non rifà
#  nulla. Forzare con FORCE_UNITY_AAR=1; saltare il prompt con
#  SKIP_UNITY_AAR=1 (usa lo stub unityLibrary se non c'è l'AAR).
# ════════════════════════════════════════════════════════════════
UNITY_PROJECT_DIR="$(dirname "$0")/unity-project"
UNITY_EXPORT_DIR="$(dirname "$0")/unity-export.gradle"
UNITY_AAR_DEST="$(dirname "$0")/app/libs"
UNITY_AAR_FILE="app/libs/unityLibrary-release.aar"
UNITY_GRADLE_HOME="$(dirname "$0")/unitylic/tools/gradle-7.5.1"

# Trova l'editor Unity (più recente in $HOME/Unity/Hub/Editor), o usa UNITY_EDITOR.
find_unity_editor() {
    if [ -n "${UNITY_EDITOR:-}" ] && [ -x "$UNITY_EDITOR" ]; then
        UNITY_EDITOR_BIN="$UNITY_EDITOR"
        return 0
    fi
    local cand
    cand=$(ls -d "$HOME/Unity/Hub/Editor"/*/Editor/Unity 2>/dev/null | sort -V | tail -1)
    if [ -n "${cand:-}" ] && [ -x "$cand" ]; then
        UNITY_EDITOR_BIN="$cand"
        return 0
    fi
    echo "!! Editor Unity non trovato (esporta UNITY_EDITOR=/path/to/Unity)." >&2
    return 1
}

# Assicura Gradle 7.5.1 (toolchain dell'export Unity, AGP 7.4.2). Idempotente.
ensure_gradle_7_5_1() {
    if [ -x "$UNITY_GRADLE_HOME/bin/gradle" ]; then
        GRADLE_UNITY="$UNITY_GRADLE_HOME/bin/gradle"
        return 0
    fi
    if [ -x "/opt/gradle/gradle-7.5.1/bin/gradle" ]; then
        GRADLE_UNITY="/opt/gradle/gradle-7.5.1/bin/gradle"
        return 0
    fi
    echo ">> Scarico Gradle 7.5.1 (toolchain dell'export Unity) in unitylic/tools/..."
    mkdir -p "$(dirname "$UNITY_GRADLE_HOME")"
    local zip_tmp="$UNITY_GRADLE_HOME.zip"
    curl -fsSL -o "$zip_tmp" "https://services.gradle.org/distributions/gradle-7.5.1-bin.zip" \
        || { echo "!! Download Gradle 7.5.1 fallito." >&2; return 1; }
    unzip -q "$zip_tmp" -d "$(dirname "$UNITY_GRADLE_HOME")" \
        || { echo "!! Estrazione Gradle 7.5.1 fallita." >&2; rm -f "$zip_tmp"; return 1; }
    rm -f "$zip_tmp"
    GRADLE_UNITY="$UNITY_GRADLE_HOME/bin/gradle"
    echo ">> Gradle 7.5.1 pronto: $GRADLE_UNITY"
}

# Fingerprint delle fonti Unity (Assets + ProjectSettings + Packages).
unity_sources_fingerprint() {
    if [ -d "$UNITY_PROJECT_DIR" ]; then
        find "$UNITY_PROJECT_DIR/Assets" "$UNITY_PROJECT_DIR/ProjectSettings" \
             "$UNITY_PROJECT_DIR/Packages" -type f -printf '%P %T@ %s\n' 2>/dev/null \
            | sort | md5sum | awk '{print $1}'
    else
        echo ""
    fi
}

# Attiva la modalità AAR Unity: app dipende dagli AAR in app/libs/.
enable_unity_aar_mode() {
    local F="app/build.gradle"
    if [ -f "$F" ]; then
        python3 - "$F" <<'PY' || { echo "!! [wiring] aggiornamento app/build.gradle fallito." >&2; return 1; }
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
s = re.sub(r"(?m)^\s*implementation project\(':unityLibrary'\)\s*$", "", s)
if "useLegacyPackaging" not in s:
    s = s.replace("    lint {\n",
        "    packaging {\n"
        "        // [build_release.sh] Unity AAR: .so non compressi (richiesto da Unity)\n"
        "        jniLibs {\n"
        "            useLegacyPackaging = true\n"
        "        }\n"
        "    }\n\n"
        "    lint {\n", 1)
marker = "    // \u2500\u2500 Unity AAR (hybrid integration) \u2500\u2500\n"
if "implementation files('libs/unityLibrary-release.aar')" not in s:
    add = (marker
           + "    implementation files('libs/unityLibrary-release.aar')\n"
           + "    implementation files('libs/unityandroidpermissions.aar')\n"
           + "    implementation files('libs/xrmanifest.androidlib-release.aar')\n")
    if marker in s:
        s = s.replace(marker, add, 1)
open(p, "w", encoding="utf-8").write(s)
PY
        echo ">> [wiring] app/build.gradle: dipende dagli AAR Unity."
    fi
    if [ -f "settings.gradle" ]; then
        sed -i "/^include ':unityLibrary'$/d" settings.gradle || { echo "!! [wiring] settings.gradle fallito." >&2; return 1; }
        echo ">> [wiring] settings.gradle: stub unityLibrary rimosso."
    fi
}

# Torna alla modalità stub unityLibrary (nessuna dipendenza da Unity).
disable_unity_aar_mode() {
    local F="app/build.gradle"
    if [ -f "$F" ]; then
        python3 - "$F" <<'PY' || { echo "!! [wiring] ripristino app/build.gradle fallito." >&2; return 1; }
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
for line in [
    "    implementation files('libs/unityLibrary-release.aar')\n",
    "    implementation files('libs/unityandroidpermissions.aar')\n",
    "    implementation files('libs/xrmanifest.androidlib-release.aar')\n",
]:
    s = s.replace(line, "")
marker = "    // \u2500\u2500 Unity AAR (hybrid integration) \u2500\u2500\n"
if "implementation project(':unityLibrary')" not in s and marker in s:
    s = s.replace(marker, marker + "    implementation project(':unityLibrary')\n", 1)
open(p, "w", encoding="utf-8").write(s)
PY
        echo ">> [wiring] app/build.gradle: torna allo stub unityLibrary."
    fi
    if [ -f "settings.gradle" ] && ! grep -q "^include ':unityLibrary'$" settings.gradle; then
        sed -i "s/^include ':app'$/include ':app'\ninclude ':unityLibrary'/" settings.gradle || { echo "!! [wiring] settings.gradle fallito." >&2; return 1; }
        echo ">> [wiring] settings.gradle: stub unityLibrary riattivato."
    fi
}

# Procedura completa di creazione AAR (export Unity + build Il2Cpp + copia + wiring).
build_unity_aar() {
    find_unity_editor || return 1

    echo ">> [Unity] Export del gradle project in $UNITY_EXPORT_DIR (può richiedere minuti)..."
    rm -rf "$UNITY_EXPORT_DIR"
    local JDKROOT="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
    local JDK_ARGS=""
    [ -d "$JDKROOT" ] && JDK_ARGS="-jdkRoot $JDKROOT"
    local SDK_ARGS=""
    [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] && SDK_ARGS="-sdkRoot $ANDROID_HOME"
    "$UNITY_EDITOR_BIN" -batchmode -nographics -quit \
        -projectPath "$UNITY_PROJECT_DIR" \
        $SDK_ARGS $JDK_ARGS \
        -executeMethod Huntix.EditorTools.HuntixBuild.ExportAndroidGradleProject \
        -logFile "$(dirname "$0")/unitylic/unity-export.log" \
        || { echo "!! Export Unity fallito (log: unitylic/unity-export.log)." >&2; return 1; }
    if [ ! -d "$UNITY_EXPORT_DIR" ]; then
        echo "!! Export Unity non ha prodotto $UNITY_EXPORT_DIR." >&2
        return 1
    fi

    ensure_gradle_7_5_1 || return 1
    echo ">> [Gradle] :unityLibrary:assembleRelease (Il2Cpp: 20-40 min alla prima volta)..."
    ( cd "$UNITY_EXPORT_DIR" && "$GRADLE_UNITY" :unityLibrary:assembleRelease :unityLibrary:xrmanifest.androidlib:assembleRelease --console=plain ) \
        || { echo "!! Build AAR Unity fallita." >&2; return 1; }

    mkdir -p "$UNITY_AAR_DEST"
    cp "$UNITY_EXPORT_DIR/unityLibrary/build/outputs/aar/"*release*.aar "$UNITY_AAR_DEST/" 2>/dev/null || true
    cp "$UNITY_EXPORT_DIR/unityLibrary/xrmanifest.androidlib/build/outputs/aar/"*release*.aar "$UNITY_AAR_DEST/" 2>/dev/null || true
    cp "$UNITY_EXPORT_DIR/unityLibrary/libs/"*.aar "$UNITY_AAR_DEST/" 2>/dev/null || true

    if [ ! -f "$UNITY_AAR_FILE" ]; then
        echo "!! AAR Unity non trovato dopo la build ($UNITY_AAR_FILE)." >&2
        return 1
    fi

    echo "$(unity_sources_fingerprint)" > "$UNITY_EXPORT_DIR/.huntix.fingerprint"
    enable_unity_aar_mode || return 1
    echo ">> AAR Unity aggiornato: $UNITY_AAR_FILE (+ dipendenze Unity in app/libs/)."
}

# ── Prompt AAR (idempotente: niente lavoro se fonti invariate) ──
AAR_PRESENT=0
[ -f "$UNITY_AAR_FILE" ] && AAR_PRESENT=1
AAR_UP_TO_DATE=0
FP_AAR="$(unity_sources_fingerprint)"
if [ "$AAR_PRESENT" = "1" ] && [ -n "$FP_AAR" ] \
   && [ -f "$UNITY_EXPORT_DIR/.huntix.fingerprint" ] \
   && [ "$(cat "$UNITY_EXPORT_DIR/.huntix.fingerprint")" = "$FP_AAR" ]; then
    AAR_UP_TO_DATE=1
fi

BUILD_UNITY_AAR=""
if [ "${SKIP_UNITY_AAR:-0}" = "1" ]; then
    BUILD_UNITY_AAR="0"
    echo ">> SKIP_UNITY_AAR=1 — passo oltre senza creare AAR."
elif [ "${FORCE_UNITY_AAR:-0}" = "1" ] || [ "$AAR_UP_TO_DATE" != "1" ]; then
    while [ -z "$BUILD_UNITY_AAR" ]; do
        def="N"; [ "$AAR_PRESENT" = "0" ] && def="Y"
        read -rp "Vuoi creare/ricreare l'AAR Unity prima dell'APK? [y/N] (default $def): " choice
        case "$choice" in
            y|Y|s|S|si|yes) BUILD_UNITY_AAR="1" ;;
            n|N|no|"")       BUILD_UNITY_AAR="0" ;;
            *) echo "Rispondi 'y' o 'n'." ;;
        esac
    done
else
    echo ">> AAR Unity già aggiornato (fonti invariate): nessuna ricreazione."
    BUILD_UNITY_AAR="0"
fi

if [ "$BUILD_UNITY_AAR" = "1" ]; then
    echo "============================================================"
    echo " Creazione AAR Unity (export + Il2Cpp + wiring)"
    echo "============================================================"
    build_unity_aar || { echo "!! Creazione AAR Unity fallita." >&2; exit 1; }
else
    if [ "$AAR_PRESENT" = "1" ]; then
        echo ">> Modalità AAR Unity attiva (AAR già presenti in app/libs/)."
        enable_unity_aar_mode || exit 1
    else
        echo ">> Modalità stub unityLibrary (nessun AAR Unity: APK contro gli stub)."
        disable_unity_aar_mode || exit 1
    fi
fi

# ── Build APK + AAB (logica da build_app.sh) ───────────────
chmod +x gradlew

# Passa le chiavi delle feature come -P al gradle.
# Priorità: ENV var > keystore.properties > default hardcoded in build.gradle.
# Così basta lanciare ./build_release.sh: le chiavi sono lette da keystore.properties,
# senza bisogno di esportare nessuna ENV.
GRADLE_ENV_PROPS=""
for pair in \
    "SENTRY_DSN:sentryDsn" \
    "ADMOB_APP_ID:admobAppId" \
    "ADMOB_BANNER_ID:admobBannerId" \
    "ADMOB_REWARDED_ID:admobRewardedId" \
    "ADMOB_INTERSTITUAL_ID:admobInterstitialId" \
    "ARCORE_API_KEY:arcoreApiKey" \
    "WEB_CLIENT_ID:webClientId" ; do
    env_name="${pair%%:*}"
    prop_name="${pair##*:}"
    if [ "$AUTH_MODE" = "keyless" ] && [ "$prop_name" = "arcoreApiKey" ]; then
        continue
    fi
    val=""
    if [ -n "${!env_name:-}" ]; then
        val="${!env_name}"
    elif [ -f "$PROPS_FILE" ]; then
        val=$(grep "^${prop_name}=" "$PROPS_FILE" 2>/dev/null | cut -d= -f2-) || true
    fi
    if [ -n "${val:-}" ]; then
        GRADLE_ENV_PROPS="$GRADLE_ENV_PROPS -P$prop_name=$val"
    fi
done

APK_DIR="app/build/outputs/apk/release"
# Pulisci eventuali output di build precedenti (evita zipalign input==output)
rm -f "$APK_DIR"/*.apk

echo ">> Building APK (assembleRelease)..."
./gradlew assembleRelease -PkeystorePropsFile="$GRADLE_KEYSTORE_PROPS" $GRADLE_ENV_PROPS --console=plain
UNSIGNED_APK="${APK_DIR}/app-release-unsigned.apk"

echo ">> Building AAB (bundleRelease)..."
./gradlew bundleRelease -PkeystorePropsFile="$GRADLE_KEYSTORE_PROPS" $GRADLE_ENV_PROPS --console=plain
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

# Pulisci il file temporaneo keyless (se creato)
[ -n "${TMP_PROPS:-}" ] && rm -f "$TMP_PROPS"

# ════════════════════════════════════════════════════════════════
#  FASE BACKEND HUNTIX (Real Life)
#  Copiata/ispirata dalla logica di avvio backend di build_app.sh (aria),
#  ma ricondotta al backend PROPRIO di Huntix in `backend/`.
#  Crea il venv, installa le dipendenze e avvia uvicorn su PORT (5100).
# ════════════════════════════════════════════════════════════════
# ── Verifica/apre la porta nel firewall (ufw) ──────────────
# Il backend Real Life ascolta su PORT (default 5100). Se il firewall (ufw)
# è attivo e NON ha una regola per quella porta, né emulatore né dispositivi
# reali sulla LAN possono raggiungerla. Verifichiamo e, se serve, apriamo.
ensure_fw_port() {
    local PORT="$1"
    if ! command -v ufw >/dev/null 2>&1; then
        echo ">> ufw non presente: impossibile gestire il firewall (ignoro)."
        return 0
    fi
    if ! ufw status 2>/dev/null | grep -qi "Status: active"; then
        echo ">> ufw NON attivo: porta $PORT raggiungibile senza regole."
        return 0
    fi
    if ufw status 2>/dev/null | grep -qw "$PORT/tcp"; then
        echo ">> Firewall: porta $PORT/tcp già aperta. OK."
    else
        echo ">> Firewall: porta $PORT/tcp CHIUSA. Apro (ufw allow)..."
        ufw allow "$PORT/tcp" comment "Huntix Real Life backend (auto build_release.sh)" \
            && echo ">> Firewall: porta $PORT/tcp aperta." \
            || echo "!! Firewall: impossibile aprire $PORT/tcp (esegui lo script come root?)."
    fi
}

# ─────────────────────────────────────────────────────────────────────────
#  Dipendenze backend: check → install → activate (idempotente)
#  Garantisce che PostgreSQL (+DB huntix), Redis e (opzionale) Ollama siano
#  presenti e attivi. Se mancano, li installa e avvia. Così build_release.sh
#  è auto-sufficiente anche dopo un format/nuova macchina.
# ─────────────────────────────────────────────────────────────────────────
have_root() { [ "$(id -u)" -eq 0 ]; }

ensure_postgres() {
    echo ">> [dep] PostgreSQL..."
    if ! command -v psql >/dev/null 2>&1; then
        echo "   PostgreSQL assente — installo (apt-get)..."
        have_root || { echo "!! serve root per installare PostgreSQL"; return 1; }
        apt-get update -qq && apt-get install -y -qq postgresql postgresql-contrib
    fi
    # avvio servizio se non attivo
    if ! pg_isready -q 2>/dev/null; then
        echo "   Avvio PostgreSQL..."
        if command -v systemctl >/dev/null 2>&1; then
            systemctl enable postgresql >/dev/null 2>&1 || true
            systemctl start postgresql >/dev/null 2>&1 || service postgresql start >/dev/null 2>&1 || true
        else
            service postgresql start >/dev/null 2>&1 || true
        fi
        sleep 2
    fi
    pg_isready -q 2>/dev/null && echo "   PostgreSQL ATTIVO." || echo "!! PostgreSQL NON raggiungibile."
}

ensure_redis() {
    echo ">> [dep] Redis..."
    if ! command -v redis-server >/dev/null 2>&1; then
        echo "   Redis assente — installo (apt-get)..."
        have_root || { echo "!! serve root per installare Redis"; return 1; }
        apt-get update -qq && apt-get install -y -qq redis-server
    fi
    if ! redis-cli ping >/dev/null 2>&1; then
        echo "   Avvio Redis..."
        if command -v systemctl >/dev/null 2>&1; then
            systemctl enable redis-server >/dev/null 2>&1 || true
            systemctl start redis-server >/dev/null 2>&1 || service redis-server start >/dev/null 2>&1 || true
        fi
        # fallback: daemonize diretto
        redis-cli ping >/dev/null 2>&1 || redis-server --daemonize yes >/dev/null 2>&1 || true
        sleep 1
    fi
    redis-cli ping >/dev/null 2>&1 && echo "   Redis ATTIVO." || echo "!! Redis NON raggiungibile."
}

ensure_ollama() {
    # Opzionale: condiviso con aria. Se non presente, il backend usa i provider cloud.
    echo ">> [dep] Ollama (opzionale)..."
    if [ -n "${OLLAMA_BASE:-}" ] && curl -s -o /dev/null -m 3 "$OLLAMA_BASE"; then
        echo "   Ollama già raggiungibile (${OLLAMA_BASE})."
        return 0
    fi
    if command -v ollama >/dev/null 2>&1; then
        if ! curl -s -o /dev/null -m 3 "http://localhost:11434"; then
            echo "   Avvio Ollama..."
            (ollama serve >/tmp/ollama.log 2>&1 &) || true
            sleep 2
        fi
        echo "   Ollama gestito (modelli locali opzionali)."
    else
        echo "   Ollama non installato — skip (il backend userà i provider cloud)."
    fi
}

# Esegue psql come utente admin postgres (root via su, altrimenti sudo).
pg_exec() {
    local sql="$1"
    if [ "$(id -u)" -eq 0 ]; then
        su postgres -c "psql -tAc $(printf '%q' "$sql")"
    else
        sudo -u postgres psql -tAc "$sql"
    fi
}
pg_createdb() {
    local owner="$1" db="$2"
    if [ "$(id -u)" -eq 0 ]; then
        su postgres -c "createdb -O '$owner' '$db'"
    else
        sudo -u postgres createdb -O "$owner" "$db"
    fi
}

# Crea ruolo + database Postgres se non esistono, coerenti con DATABASE_URL.
ensure_pg_database() {
    local URL="$1"
    [ -z "$URL" ] && return 0
    # parse postgresql://user:pass@host:port/db
    local rest="${URL#postgresql://}"
    local user="${rest%%:*}"
    rest="${rest#*:}"; local pass="${rest%%@*}"
    rest="${rest#*@}"; local host="${rest%%:*}"
    rest="${rest#*:}"; local port="${rest%%/*}"
    local db="${rest#*/}"
    echo ">> [dep] Database Postgres '$db' (utente '$user')..."
    if ! pg_isready -q 2>/dev/null; then echo "!! Postgres non attivo: impossibile creare il DB."; return 1; fi
    if pg_exec "SELECT 1 FROM pg_roles WHERE rolname='$user'" | grep -q 1; then
        echo "   Ruolo '$user' già esistente."
    else
        if [ "$(id -u)" -eq 0 ]; then
            su postgres -c "psql -c \"CREATE ROLE $user LOGIN PASSWORD '$pass';\""
        else
            sudo -u postgres psql -c "CREATE ROLE $user LOGIN PASSWORD '$pass';"
        fi
        echo "   Ruolo '$user' creato."
    fi
    if pg_exec "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1; then
        echo "   Database '$db' già esistente."
    else
        pg_createdb "$user" "$db"
        echo "   Database '$db' creato (owner '$user')."
    fi
}

# Prepara backend/.env se mancante: da ENV (HUNTIX_DOTENV) o da .env.example.
ensure_backend_env() {
    if [ -f .env ]; then echo ">> [dep] backend/.env presente."; return 0; fi
    echo ">> [dep] backend/.env assente — tento di ricrearlo..."
    if [ -n "${HUNTIX_DOTENV:-}" ]; then
        printf '%s\n' "$HUNTIX_DOTENV" > .env
        echo "   .env creato da ENV HUNTIX_DOTENV."
    elif [ -f .env.example ]; then
        cp .env.example .env
        echo "   .env creato da .env.example (controlla le chiavi!)."
    else
        # template minimo: il backend PARTE ma i provider cloud senza chiavi falliranno.
        cat > .env <<'EOF'
DATABASE_URL=postgresql://chatai:chatai_secret@localhost:5432/huntix
REDIS_URL=redis://:f436958355c2375ee687c7d6ebe79c13b1fc978dd02f9ef5@localhost:6379/1
JWT_SECRET=changeme_genera_un_secret_random
PORT=5100
CHAT_PROVIDER=groq
GROQ_API_KEY=
GEMINI_API_KEY=
OLLAMA_BASE=http://localhost:11434
FORCE_LOCAL_FIRST=0
ENSEMBLE_ENABLED=1
EOF
        echo "!! .env di DEFAULT creato (SENZA API key): imposta le chiavi o esporta HUNTIX_DOTENV."
    fi
}

start_huntix_backend() {
    echo "============================================================"
    echo " Backend Huntix (Real Life) — setup & avvio"
    echo "============================================================"
    local BDIR="backend"
    if [ ! -d "$BDIR" ]; then
        echo "!! Backend dir '$BDIR' non trovata, salto la fase backend."; return 0
    fi

    # ── 1) Dipendenze di sistema (installa/avvia se mancano) ──
    ensure_postgres
    ensure_redis
    ensure_ollama

    cd "$BDIR"

    # ── 2) .env (ricrea da ENV/.example se assente) ──
    ensure_backend_env

    # ── 3) Database Postgres coerente con DATABASE_URL ──
    local DB_URL
    DB_URL=$(grep '^DATABASE_URL=' .env 2>/dev/null | cut -d= -f2-)
    ensure_pg_database "$DB_URL"

    # ── 4) venv + dipendenze Python ──
    if [ ! -d venv ]; then
        echo ">> Creo venv backend..."
        python3 -m venv venv
    fi
    if [ -f requirements.txt ]; then
        echo ">> pip install -r requirements.txt ..."
        ./venv/bin/pip install --upgrade pip >/dev/null 2>&1 || true
        ./venv/bin/pip install -r requirements.txt 2>&1 | tail -8
    fi

    local PORT
    PORT=$(grep '^PORT=' .env 2>/dev/null | cut -d= -f2-) || true
    PORT="${PORT:-5100}"

    # ── 5) Firewall ──
    ensure_fw_port "$PORT"

    # ── 6) Avvio backend ──
    echo ">> Avvio backend huntix su porta $PORT ..."
    HUNTIX_BACKEND_PORT="$PORT" ./start_backend.sh >/dev/null 2>&1 &
    sleep 4
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/docs" 2>/dev/null | grep -q 200; then
        echo ">> Backend huntix ATTIVO su http://localhost:$PORT"
    else
        echo "!! Backend non raggiungibile subito; controlla /tmp/huntix_backend.log"
    fi
    cd ..
}
start_huntix_backend || echo "!! Fase backend terminata con errori (vedi /tmp/huntix_backend.log)."
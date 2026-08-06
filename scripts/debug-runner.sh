#!/usr/bin/env bash
#
# debug-runner.sh — build debug, install su device, live-logcat filtrato
#
# Uso: ./scripts/debug-runner.sh [uninstall]
#   senza arg: build + install + logcat tags: OutdoorManager|CatchEngine|PoiBridge|PoiUnityBridge|Bridge|UnityPlayer
#   uninstall: rimuove l'app dal device (idempo) prima di installare.
set -eu

PKG="com.intelligame.huntix"
APK="app/build/outputs/apk/debug/app-debug.apk"
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "uninstall" ]]; then
  echo "→ adb uninstall $PKG"
  adb uninstall "$PKG" 2>/dev/null || true
fi

echo "→ ./gradlew :app:assembleDebug"
./gradlew :app:assembleDebug --quiet

echo "→ adb install -r $APK"
adb install -r "$APK"

echo "→ logcat (premi Ctrl-C per fermare):"
echo "   tag: OutdoorManager|CatchEngine|PoiBridge|PoiUnityBridge|Bridge|UnityPlayer|GameManager"
adb logcat -s OutdoorManager:* CatchEngine:* PoiBridge:* PoiUnityBridge:* Bridge:* UnityPlayer:* GameManager:* *:E 2>/dev/null \
  | grep -E "OutdoorManager|Catch|Poi|Bridge|GameManager\|CatchResult|LevelUp" | head -200 || true

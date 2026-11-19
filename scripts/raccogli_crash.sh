#!/usr/bin/env bash
#
# raccogli_crash.sh — raccoglie in una cartella tutto cio' che serve per
# diagnosticare un crash/ANR di Huntix su un device collegato.
#
# Uso:
#   ./scripts/raccogli_crash.sh              # cartella in /tmp/opencode
#   ./scripts/raccogli_crash.sh /tmp/mia      # cartella scelta
#   ./scripts/raccogli_crash.sh --bugreport   # aggiunge adb bugreport (lento, ~10-50 MB)
#
# Prima di riprodurre il crash e' utile azzerare i buffer:
#   adb logcat -c && adb logcat -b crash -c
#
# Lo script e' tollerante ai fallimenti: su device non rooted i tombstone
# non sono leggibili e si limita a salvare l'errore in tombstone_list.txt.
set -uo pipefail

PKG="${PKG:-com.intelligame.huntix}"
BUGREPORT=0
OUT=""

for arg in "$@"; do
    case "$arg" in
        --bugreport) BUGREPORT=1 ;;
        -h|--help)
            sed -n '2,14p' "$0"
            exit 0
            ;;
        *) OUT="$arg" ;;
    esac
done

if [ -z "$OUT" ]; then
    OUT="/tmp/opencode/huntix_crash_$(date +%Y%m%d_%H%M%S)"
fi
mkdir -p "$OUT"

echo ">> Package: $PKG"
echo ">> Output:  $OUT"

if ! command -v adb >/dev/null 2>&1; then
    echo "!! adb non trovato" >&2
    exit 1
fi

if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
    echo "!! Nessun device autorizzato (adb get-state != device)." >&2
    adb devices -l > "$OUT/devices.txt" 2>&1 || true
    cat "$OUT/devices.txt" >&2
    exit 1
fi

adb devices -l > "$OUT/devices.txt" 2>&1
adb shell dumpsys package "$PKG" > "$OUT/package.txt" 2>&1
grep -aE 'versionName|versionCode|firstInstallTime|lastUpdateTime' \
    "$OUT/package.txt" > "$OUT/versione.txt" 2>&1 || true

echo ">> Versione APK sul device:"
cat "$OUT/versione.txt" 2>/dev/null || echo "   (non disponibile)"

# Il buffer crash e' la fonte decisiva: qui finiscono SIGSEGV/SIGABRT nativi
# quando il processo Unity muore e non resta nulla nel log principale.
adb logcat -b crash -d > "$OUT/crash_buffer.txt" 2>&1

# Log completo (limitato) + vista filtrata dei tag Huntix e dei segnali di errore.
adb logcat -d -v threadtime 2>/dev/null | tail -20000 > "$OUT/logcat_full.txt"
grep -aiE '\[AR\]|\[FootIK\]|\[ChunkManager\]|\[ChunkBuilder\]|\[VehicleInterior\]|\[NPCPopulator\]|\[ChunkVehiclePopulator\]|\[Building\]|FATAL|SIGSEGV|SIGABRT|SIGBUS|LOW_MEMORY|ANR|ActivityManager|Force finishing|OutOfMemory|GC overhead|tombstone|abort' \
    "$OUT/logcat_full.txt" > "$OUT/huntix_filtrato.txt" 2>&1 || true

# PSS reale del processo (il "ram=/peak=" del beacon e' memoria riservata Unity,
# NON il numero che usa l'OOM killer di Android).
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1

# Tombstone: su device non rooted l'accesso e' negato, va benissimo cosi'.
adb shell ls -l /data/tombstones/ > "$OUT/tombstone_list.txt" 2>&1
mkdir -p "$OUT/tombstones"
adb pull /data/tombstones/ "$OUT/tombstones/" > "$OUT/tombstone_pull.txt" 2>&1 || true

if [ "$BUGREPORT" = "1" ]; then
    echo ">> bugreport (puo' volerci qualche minuto)..."
    adb bugreport "$OUT/bugreport.zip" > "$OUT/bugreport.log" 2>&1 || true
fi

# Riepilogo rapido: segnale e ultimo beacon utile.
{
    echo "=== SEGNALI NEL BUFFER CRASH ==="
    grep -aiE 'signal|SIGSEGV|SIGABRT|SIGBUS|backtrace|pid:|Process|Cmdline' \
        "$OUT/crash_buffer.txt" | tail -40 || true
    echo
    echo "=== ULTIMO BEACON [AR] ==="
    grep -a '\[AR\]' "$OUT/huntix_filtrato.txt" | tail -3 || true
    echo
    echo "=== TOMBSTONE DISPONIBILI ==="
    tail -20 "$OUT/tombstone_list.txt" || true
} > "$OUT/RIEPILOGO.txt"

echo ">> Fatto. Riepilogo:"
cat "$OUT/RIEPILOGO.txt"
echo ">> Cartella completa: $OUT"

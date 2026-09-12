#!/usr/bin/env bash
# citystyle.sh — Sceglie lo stile degli edifici di DEFAULT per il prossimo build.
#
#   ./citystyle.sh quaternius   -> default = edifici Quaternius (facciate texturizzate)
#   ./citystyle.sh kenney       -> default = edifici Kenney (low-poly flat)
#
# Nota: all'interno del gioco puoi cambiare stile a caldo dall'hamburger menu
# (voce "Stile edifici"). Questo script fissa solo il valore presente alla
# prima installazione / dopo un reset dei dati.
#
# Per applicare al device serve ricreare l'APK:
#   FORCE_UNITY_AAR=1 ./build_release.sh --auto
set -euo pipefail
cd "$(dirname "$0")"

FILE=unity-project/Assets/City/Scripts/UI/CityStyle.cs
STYLE="$1"
case "$STYLE" in
  quaternius) DEFAULT=1 ;;
  kenney)     DEFAULT=0 ;;
  *)
    echo "uso: ./citystyle.sh {quaternius|kenney}" >&2
    exit 1 ;;
esac

# sostituisce il default nel PlayerPrefs.GetInt
grep -q 'GetInt(Key, '"$DEFAULT"')' "$FILE" && {
  echo ">> Stile edifici: gia' impostato su $STYLE."
  exit 0
}
sed -i "s/GetInt(Key, [01])/GetInt(Key, $DEFAULT)/" "$FILE"
echo ">> Stile edifici default impostato su: $STYLE"
echo "   Per applicarlo al device: FORCE_UNITY_AAR=1 ./build_release.sh --auto"

#!/usr/bin/env bash
# Verifica sintassi/tipi degli script City con mcs + stub (MAI includere gli
# stub nel progetto Unity).
# Uso:
#   check.sh                 -> tutti gli script City escluse le note sotto
#   check.sh File1.cs ...    -> verifica mirata sui soli file indicati
#
# Esclusioni permanenti (compilano in Unity ma non con mcs):
#  - RoadGraphBuilder.cs      : local functions non supportate da mcs
#  - Game.cs / VehicleShopUI.cs / UIManager.cs / NPCMission.cs /
#    InteriorGenerator.cs / LegalManager.cs / DrivePedal.cs /
#    DynamicJoystick.cs / OrbitZone.cs / ScreenFader.cs : TMPro/EventSystems/
#    InputSystem/Image non stubbati
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
PROJ="$ROOT/../../../unity-project"
EXCL="Scripts/Game\.cs$|CityOSMWorld\.cs$|RoadGraphBuilder\.cs$|NPCMission\.cs$|InteriorGenerator\.cs$|DrivePedal\.cs$|DynamicJoystick\.cs$|LegalManager\.cs$|OrbitZone\.cs$|UIManager\.cs$|ScreenFader\.cs$|EggController\.cs$|MissionManager\.cs$|InteractDoor\.cs$|EggSpawnManager\.cs$"
if [ $# -gt 0 ]; then
  # Modalita' mirata: solo i file richiesti. Se dipendono da membri veicolo
  # dello stub Game passare anche i file City/Vehicle e DEFINES=-DHUNTIX_FULL.
  FILES="$*"
  DEFS="${DEFINES:-}"
else
  FILES=$(find "$PROJ/Assets/City/Scripts" -name "*.cs" | grep -vE "$EXCL" | sort)
  DEFS="-define:HUNTIX_FULL"
fi
OUT=$(mktemp -d)/check.dll
mcs $DEFS -target:library -out:"$OUT" \
  "$ROOT/UnityStubs.cs" "$ROOT/HuntixStubs.cs" "$ROOT/HuntixStubsGame.cs" $FILES
echo "CSCHECK OK ($OUT)"

#!/usr/bin/env bash
# Giorno 10 - gate STATICO di integrazione (senza build e senza device).
# Verifica che i fix di camminata/camera siano ancora cablati fra loro e che
# il type-check ufficiale passi. NON misura fps, camGap ne' uova: quelli sono
# gate device, nel RUNBOOK sez. 7.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WALKER="unity-project/Assets/City/Scripts/Player/CharacterWalker.cs"
PLAYER="unity-project/Assets/City/Scripts/Player/PlayerController.cs"
CAMERA="unity-project/Assets/City/Scripts/Player/CameraRig.cs"
EGG="unity-project/Assets/City/Scripts/Interior/ContainerEgg.cs"
CTRL="unity-project/Assets/Resources/Mixamo/PlayerLocomotion.controller"
NPC="unity-project/Assets/City/Scripts/NPC/NPCController.cs"
WORLD="unity-project/Assets/City/Scripts/OSM/CityChunkedWorld.cs"
FOOTIK="unity-project/Assets/City/Scripts/Player/FootIK.cs"
ARMS="unity-project/Assets/City/Scripts/Player/ArmsPoseFallback.cs"

FAIL=0
ok()   { printf 'OK   %s\n' "$1"; }
bad()  { printf 'FAIL %s  ->  %s : %s\n' "$1" "$2" "$3"; FAIL=1; }

# pattern presente almeno una volta
has() {
    local desc="$1" pattern="$2" file="$3"
    if grep -Fq -- "$pattern" "$file" 2>/dev/null; then ok "$desc"
    else bad "$desc" "$file" "$pattern"; fi
}
# pattern ASSENTE (regressioni gia' corrette)
hasnt() {
    local desc="$1" pattern="$2" file="$3"
    if grep -Fq -- "$pattern" "$file" 2>/dev/null; then bad "$desc" "$file" "NON deve esserci: $pattern"
    else ok "$desc"; fi
}
# pattern ASSENTE nel codice attivo: le righe di commento non contano, cosi'
# i commenti che spiegano perche' una guardia e' stata rimossa non falsano
# il gate.
hasnt_code() {
    local desc="$1" pattern="$2" file="$3"
    if grep -F -- "$pattern" "$file" 2>/dev/null | grep -qv '^[[:space:]]*//'; then
        bad "$desc" "$file" "NON deve esserci nel codice: $pattern"
    else
        ok "$desc"
    fi
}
# occurrences minime
atleast() {
    local desc="$1" pattern="$2" file="$3" min="$4" n
    n="$(grep -Fc -- "$pattern" "$file" 2>/dev/null || true)"
    if [ "${n:-0}" -ge "$min" ]; then ok "$desc ($n)"
    else bad "$desc" "$file" "serve >=$min occorrenze di: $pattern"; fi
}

echo "=== Giorno 10 - integrazione statica ==="

# Errore rosso: IndexOutOfRangeException in NPCController.Update()
has  "indice NPC normalizzato in Init" \
     "((startIndex % waypoints.Length + waypoints.Length) % waypoints.Length)" "$NPC"
has  "invariante indice in Update" \
     "if (currentTarget < 0 || currentTarget >= waypoints.Length)" "$NPC"

# Fluttuazione iniziale: pivot calcolato dalla capsula, non 1,5 m fissi
has  "pivot da CharacterController" \
     "private static float FeetFromPivot(City.Player.PlayerController player)" "$WORLD"
hasnt "spawn a 1,5 m rimosso"       "new Vector3(0f, 1.5f, 0f)"       "$WORLD"
atleast "snap DEM prima e dopo il gate" "SnapPlayerToTerrain(Manager" "$WORLD" 2
has  "spawn su collider con pivot reale" "p.y = hit.point.y + feetFromPivot;" "$WORLD"
has  "spawn su DEM con pivot reale"      "p.y = elev + feetFromPivot;"      "$WORLD"

# Commit C: un solo owner IK per piedi/pelvis (entrambi i componenti restano)
has  "coordinatore IK presente"        "internal static class PlayerIkCoordinator" "$FOOTIK"
has  "owner DedicatedFootIK"           "Owner.DedicatedFootIK"                    "$FOOTIK"
has  "owner Walker"                    "Owner.Walker"                             "$WALKER"
has  "walker cede piedi/pelvis"        "PlayerIkCoordinator.Resolve(animator) !="  "$WALKER"
has  "sensore cede se non e' owner"    "PlayerIkCoordinator.Resolve(_anim) !="     "$FOOTIK"
# Latch braccia: il falso negativo hasTransformHierarchy e' rimosso
has  "latch Animator guida il rig"     "animatorSeenDriving"                      "$ARMS"
hasnt_code "falso negativo hasTransformHierarchy" "!animator.hasTransformHierarchy" "$ARMS"
hasnt_code "fullPathHash non e' piu' usato" "fullPathHash"                        "$ARMS"

# Fix A: stick analogico, una sola magnitudo, rampa continua
has  "deadzone condivisa"            "moveInput.sqrMagnitude > MoveDeadzoneSqr" "$PLAYER"
has  "rampa Speed continua"          "runSpeed * Mathf.InverseLerp("            "$PLAYER"
atleast "dir normalizzato (terra+volo)" "if (dir.sqrMagnitude > 0.0001f) dir.Normalize();" "$PLAYER" 2

# Fix B: Animator hero, Jump solo reale, nessun parametro morto
has  "Animator risolto sull'hero"    "animator = ResolveAnimator();"            "$WALKER"
has  "fallback legacy/NPC"           "return GetComponentInChildren<Animator>(true);" "$WALKER"
hasnt "IsMoving rimosso"             "SetBool(\"IsMoving\""                     "$WALKER"
has  "hold del salto reale"          "_jumpAnimHoldUntil = Time.time + JumpAnimHoldS;" "$PLAYER"

# Giorno 9: collisione a sfera, smoothing rotazione, gyro separato
has  "spring arm a sfera"            "Physics.SphereCastNonAlloc("              "$CAMERA"
hasnt "vecchio raycast rimosso"      "Physics.Raycast(start, offset.normalized" "$CAMERA"
has  "smoothing rotazione LerpAngle" "Mathf.LerpAngle(_viewYaw"                 "$CAMERA"
has  "gyro solo vista"               "_gyroYawOffset -="                        "$CAMERA"
has  "base di marcia esposta"        "public Quaternion MovementBasis"          "$CAMERA"
has  "snap al riattivamento"         "private void OnEnable()"                  "$CAMERA"
atleast "marcia/dot/dash usano MovementBasis" "rig.MovementBasis"               "$PLAYER" 3
hasnt "nessuna marcia dalla vista"   "rig.transform.forward"                    "$PLAYER"

# Riflesso uova: sola lettura, nessun collegamento al comportamento uovo
has  "API uova read-only"            "public static float DistanceToChosen(Vector3 point)" "$EGG"
has  "uovo protegge la camera"       "DistanceToChosen(hitInfo.point)"          "$CAMERA"
has  "eggD nel log camminata"        "eggD="                                    "$PLAYER"

# Asset animazione: i threshold devono restare allineati alla nuova rampa
has  "threshold Walk"                "m_Threshold: 3.2"                         "$CTRL"
has  "threshold Run"                 "m_Threshold: 7.5"                         "$CTRL"
has  "blend parameter Speed"         "m_BlendParameter: Speed"                  "$CTRL"

# Type-check ufficiale (modalita' completa, mai gli stub ridotti)
LOG="$(mktemp)"
if (cd backend/preprocessing/cscheck && ./check.sh) >"$LOG" 2>&1; then
    ok "cscheck -define:HUNTIX_FULL ($(grep -m1 'Compilation succeeded' "$LOG" | tr -d '\r'))"
else
    bad "cscheck" "backend/preprocessing/cscheck/check.sh" "ultime righe: $(tail -3 "$LOG" | tr '\n' ' ')"
fi
rm -f "$LOG"

if [ "$FAIL" -ne 0 ]; then
    echo "INTEGRAZIONE KO - correggere i FAIL prima della build"
    exit 1
fi
echo "INTEGRAZIONE OK (statico). Gate device: vedere docs/RUNBOOK.md sez. 7."

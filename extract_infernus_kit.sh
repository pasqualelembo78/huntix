#!/usr/bin/env bash
# Estrae i decori PVGames Inferno scelti in Assets/Resources/InfernusKit/.
# Fonte: kit/afterlife/PVGames_Infernus_Free.zip (fuori dall'Assets, non committato
# nel progetto Unity). Idempotente: riscrive solo i file che mancano.
set -euo pipefail
cd "$(dirname "$0")"
ZIP=kit/afterlife/PVGames_Infernus_Free.zip
DST=unity-project/Assets/Resources/InfernusKit
mkdir -p "$DST"
TMP=$(mktemp -d)

extract() { # extract <base> <count> <outdir> <outprefix>
  local base=$1 count=$2 outdir=$3 outprefix=$4
  mkdir -p "$DST/$outdir"
  for i in $(seq 1 "$count"); do
    local out="$DST/$outdir/${outprefix}_${i}.png"
    [ -f "$out" ] && continue
    unzip -j -o -q "$ZIP" "Infernus_Tiles/Infernus_${base}_${i}.png" -d "$TMP" 2>/dev/null || true
    [ -f "$TMP/Infernus_${base}_${i}.png" ] && mv "$TMP/Infernus_${base}_${i}.png" "$out"
  done
}

extract_single() { # extract_single <src> <outdir> <outname>
  local src=$1 outdir=$2 outname=$3
  mkdir -p "$DST/$outdir"
  local out="$DST/$outdir/$outname.png"
  [ -f "$out" ] && return 0
  unzip -j -o -q "$ZIP" "Infernus_Tiles/$src" -d "$TMP" 2>/dev/null || true
  [ -f "$TMP/$(basename "$src")" ] && mv "$TMP/$(basename "$src")" "$out"
}

extract_anim() { # extract_anim <srcbase> <count> <outdir> <outprefix> — le strisce Lightsource si rinominano in lightsource_N
  local srcbase=$1 count=$2 outdir=$3 outprefix=$4
  mkdir -p "$DST/$outdir"
  for i in $(seq 1 "$count"); do
    local out="$DST/$outdir/${outprefix}_${i}.png"
    [ -f "$out" ] && continue
    unzip -j -o -q "$ZIP" "Infernus_Tiles/${srcbase}_${i}.png" -d "$TMP" 2>/dev/null || true
    [ -f "$TMP/${srcbase}_${i}.png" ] && mv "$TMP/${srcbase}_${i}.png" "$out"
  done
}

# --- rovine/argilla a terra ---
extract Hellscape_Rocks 6 Rocks rock
extract Hellscape_Pile1 6 Piles pile
extract Skull1 4 Skulls skull
extract Skull3 2 Skulls skull
extract Bones1 4 Bones bones
extract DragonBones1 4 Bones dragonbones
extract Grave1 4 Graves grave
extract Candles 7 Candles candles

# --- pezzi scenografici ---
extract Hellscape_StoneSpire 4 Spires spire
extract Hellscape_BrokenGiant 4 Giants giant
extract Hellscape_BrokenHand2 4 Hands hand
extract Throne 4 Throne throne
extract Altar1 4 Altar altar

# --- fuoco e luci ---
extract BurnerColumn 2 Fire burnercolumn
extract Candelabra 4 Fire candelabra
extract_single Infernus_Brasero.png Fire brasero
extract_anim Anim_Infernus_Lightsources 9 Fire lightsource

# --- ornamento da parete ---
extract WallSword 3 Wall wallsword
extract WallSpear 3 Wall wallspear
extract WallShield 3 Wall wallshield
extract WallCandles 5 Wall wallcandles
extract WallLantern 3 Wall walllantern

rm -rf "$TMP"
echo "InfernusKit estratto in $DST:"
find "$DST" -name '*.png' | wc -l
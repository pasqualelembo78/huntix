#!/usr/bin/env bash
for k in IT_081_086 IT_081_087 IT_081_088 IT_082_086 IT_082_087 IT_082_088 IT_083_086 IT_083_087 IT_083_088; do
  if [ ! -f tiles/${k}_geo.json.gz ]; then
    ./venv/bin/python osm_italy_processor.py gen-tile $k --skip-graph
  fi
done
echo WARMUP_COMPLETATO

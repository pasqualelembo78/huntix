#!/usr/bin/env python3
"""Genera il catalogo dei settori del World Streaming per Unity.

Un settore = una tile OSM (10x10 chunk da 1 km). Il catalogo e' un TextAsset
in Assets/Resources e serve a Unity per:
  - sapere quali settori esistono anche quando la loro geo non e' in cache;
  - costruire lo skyline lontano (solo silhouette, nessun collider) con un
    numero di edifici noto a priori, senza scaricare nulla di nuovo;
  - aggiungere nuovi quartieri: basta rigenerare questo file con le nuove tile.

Nessuna rete, nessuna modifica a geo/graph o alla cache v5.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
INDEX = HERE / "tiles" / "index.json"
OUT = ROOT / "unity-project/Assets/Resources/world_sectors.json"
META = ROOT / "unity-project/Assets/Resources/world_sectors.json.meta"


def main() -> int:
    tiles = json.loads(INDEX.read_text(encoding="utf-8"))["tiles"]
    sectors = []
    for t in tiles:
        b = t["bbox"]
        sectors.append({
            "key": t["key"],
            "lat": round((b[0] + b[2]) / 2.0, 6),
            "lng": round((b[1] + b[3]) / 2.0, 6),
            "bbox": [round(v, 6) for v in b],
            "buildings": int(t.get("buildings", 0)),
            "bytes": int(t.get("bytes", 0)) + int(t.get("geo_bytes", 0)),
        })
    sectors.sort(key=lambda s: s["key"])
    payload = {"version": 1, "sectors": sectors}
    OUT.write_text(json.dumps(payload, separators=(",", ":"), ensure_ascii=False),
                   encoding="utf-8")

    guid = hashlib.md5(b"huntix:world_sectors.json").hexdigest()
    META.write_text(
        "fileFormatVersion: 2\n"
        f"guid: {guid}\n"
        "TextScriptImporter:\n"
        "  externalObjects: {}\n"
        "  userData: \n"
        "  assetBundleName: \n"
        "  assetBundleVariant: \n",
        encoding="utf-8")
    print(f"{OUT}: {len(sectors)} settori, {OUT.stat().st_size} byte")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Genera il manifest dei pacchetti offline per l'app Android.

Il manifest e' un asset dell'app (app/src/main/assets/offline_packs.json) e
contiene, per ogni citta' selezionabile nello spawn e per l'intera Italia,
l'elenco delle chiavi tile da scaricare. Android lo usa per:

  - mostrare la sezione "Mondo offline" con stato/installazione;
  - decidere, dopo lo spawn, se la citta' e' gia' disponibile su disco;
  - scaricare SOLO le tile mancanti, scrivendo nella cache v5 di Unity.

Nessuna rete: tutto deriva da index.json, city_to_province.json,
italian_provinces.json e dall'elenco citta' del selettore di spawn (Kotlin).
"""
from __future__ import annotations

import datetime as _dt
import json
import math
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
INDEX = HERE / "tiles" / "index.json"
CITY_TO_PROV = HERE / "city_to_province.json"
PROVINCES = HERE / "italian_provinces.json"
SPAWN_CHOOSER = (
    ROOT / "app/src/main/java/com/intelligame/huntix/bridge/WorldPosCloud.kt"
)
OUT = ROOT / "app/src/main/assets/offline_packs.json"

CACHE_VERSION = 5
POINT_PAD_LAT = 0.22   # ~24 km: due/tre tile intorno alla citta'
POINT_PAD_LON = 0.30

# Rapporti misurati sui .gz presenti nel repo: il client scrive JSON non
# compresso, quindi l'UI deve mostrare lo spazio REALE occupato in cache.
GRAPH_EXPAND = 6.84   # 2,94 GB JSON / 0,43 GB gz
GEO_EXPAND = 5.55     # 1,61 GB JSON / 0,29 GB gz
# Geo non ancora generate: il server le crea al primo download del pacchetto.
GEO_MISSING_EST_BYTES = 229_000


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.lower().strip())
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def slug(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", norm(s)).strip("-") or "italia"


def load_json(path: Path):
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def spawn_cities() -> list[tuple[str, float, float]]:
    """Legge le citta' dell'elenco SPAWN dal file Kotlin, senza duplicarle."""
    text = SPAWN_CHOOSER.read_text(encoding="utf-8")
    pat = re.compile(
        r'arrayOf\(\s*"([^"]+)"\s*,\s*"(-?[\d.]+)"\s*,\s*"(-?[\d.]+)"\s*\)'
    )
    out: list[tuple[str, float, float]] = []
    for name, lat, lng in pat.findall(text):
        if (name, float(lat), float(lng)) not in out:
            out.append((name, float(lat), float(lng)))
    if not out:
        raise SystemExit("nessuna citta' trovata in WorldPosCloud.kt")
    return out


def keys_overlapping(bbox: list[float], tiles: dict[str, dict]) -> list[str]:
    latmin, lonmin, latmax, lonmax = bbox
    out = []
    for key, t in tiles.items():
        a, b, c, d = t["bbox"]  # latmin, lonmin, latmax, lonmax
        if c < latmin or a > latmax or d < lonmin or b > lonmax:
            continue
        out.append(key)
    return sorted(out)


def keys_around(lat: float, lng: float, tiles: dict[str, dict]) -> list[str]:
    bbox = [
        lat - POINT_PAD_LAT, lng - POINT_PAD_LON,
        lat + POINT_PAD_LAT, lng + POINT_PAD_LON,
    ]
    return keys_overlapping(bbox, tiles)


def make_entry(
    pack_id: str, label: str, kind: str, bbox: list[float],
    keys: list[str], tiles: dict[str, dict],
) -> dict:
    graph_c = 0
    geo_c = 0
    geo_missing = 0
    for k in keys:
        t = tiles.get(k)
        if not t:
            continue
        graph_c += int(t.get("bytes", 0))
        gb = int(t.get("geo_bytes", 0))
        if gb > 0:
            geo_c += gb
        else:
            geo_missing += 1
    # La geo mancante verra' generata dal server durante il download.
    geo_est = geo_c + geo_missing * GEO_MISSING_EST_BYTES
    return {
        "id": pack_id,
        "label": label,
        "kind": kind,
        "bbox": [round(x, 6) for x in bbox],
        "keys": keys,
        "bytes": graph_c + geo_est,
        "installedBytes": int(graph_c * GRAPH_EXPAND + geo_est * GEO_EXPAND),
    }


def main() -> int:
    tiles = {t["key"]: t for t in load_json(INDEX)["tiles"]}
    c2p = {norm(k): v for k, v in load_json(CITY_TO_PROV).items()}
    provs = load_json(PROVINCES)

    packs: list[dict] = []
    all_keys = sorted(tiles)
    packs.append(make_entry("italia", "Tutta Italia", "world", [35.4, 6.6, 47.1, 18.6],
                            all_keys, tiles))

    for name, lat, lng in spawn_cities():
        slug_name = slug(name)
        prov_slug = c2p.get(norm(name))
        bbox = provs.get(prov_slug) if prov_slug else None
        if bbox and len(bbox) == 4:
            keys = keys_overlapping(bbox, tiles)
            kind = "province"
        else:
            bbox = [lat - POINT_PAD_LAT, lng - POINT_PAD_LON,
                    lat + POINT_PAD_LAT, lng + POINT_PAD_LON]
            keys = keys_around(lat, lng, tiles)
            kind = "city"
        if not keys:
            print(f"ATTENZIONE: nessuna tile per {name}", file=sys.stderr)
            continue
        packs.append(make_entry(slug_name, name, kind, bbox, keys, tiles))

    payload = {
        "version": 1,
        "generated": _dt.datetime.now(_dt.timezone.utc).isoformat(timespec="seconds"),
        "cacheVersion": CACHE_VERSION,
        "tileCount": len(all_keys),
        "packs": packs,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    total = sum(p["bytes"] for p in packs if p["id"] != "italia")
    print(f"{OUT}: {len(packs)} pacchetti "
          f"({len(all_keys)} tile Italia, {total / 1e6:.1f} MB somma citta')")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

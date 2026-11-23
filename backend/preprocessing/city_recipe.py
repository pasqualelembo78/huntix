"""Estrae una *ricetta* di città fittizia da PBF OSM reali.

Il file prodotto contiene SOLO statistiche (larghezza strade, passo degli
isolati, quota parchi, mix di POI): nessuna geometria, nessun DEM, nessun
tile. A runtime Unity genera una città fittizia da questi parametri + un seed,
senza cartografia reale. Vedi HUNTIX_MANDATO.md §8 "Città fittizia".

Uso (venv con pyosmium):
    venv/bin/python -m preprocessing.city_recipe \
        --bbox 41.44,15.51,41.49,15.58 \
        --out data/foggia_city_recipe.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))

from tile_builder import (ROAD_HIGHWAYS, haversine_m, path_length_m,  # noqa: E402
                          road_width, shoelace_area_m2, to_local_m)

RECIPE_VERSION = 1

PARK_TAGS = {
    "leisure": {"park", "garden", "playground", "recreation_ground"},
    "landuse": {"forest", "grass", "meadow", "recreation_ground", "village_green"},
    "natural": {"wood", "grassland", "scrub"},
}

# Categorie POI del wire Huntix (dealer|repair|garage|hospital|school|bar|rampa).
POI_RULES = (
    ("bar", lambda t: t.get("amenity") in
     {"bar", "cafe", "pub", "fast_food", "restaurant"}),
    ("school", lambda t: t.get("amenity") in
     {"school", "kindergarten", "college", "university"}),
    ("hospital", lambda t: t.get("amenity") in {"hospital", "clinic"}),
    ("dealer", lambda t: t.get("shop") == "car"),
    ("repair", lambda t: t.get("shop") == "car_repair"),
    ("garage", lambda t: t.get("amenity") == "parking" and
     t.get("parking") in {"multi-storey", "underground", "shed"}),
)


def parse_bbox(text: str) -> Tuple[float, float, float, float]:
    parts = [p.strip() for p in text.split(",")]
    if len(parts) != 4:
        raise SystemExit("--bbox deve essere latmin,lonmin,latmax,lonmax")
    try:
        latmin, lonmin, latmax, lonmax = [float(p) for p in parts]
    except ValueError:
        raise SystemExit("--bbox contiene valori non numerici")
    if not (-90 <= latmin < latmax <= 90 and -180 <= lonmin < lonmax <= 180):
        raise SystemExit("--bbox non valido")
    return latmin, lonmin, latmax, lonmax


def bbox_area_m2(bbox: Tuple[float, float, float, float]) -> float:
    latmin, lonmin, latmax, lonmax = bbox
    w = haversine_m((latmin, lonmin), (latmin, lonmax))
    h = haversine_m((latmin, lonmin), (latmax, lonmin))
    return max(1.0, w * h)


def _is_park(tags) -> bool:
    for key, values in PARK_TAGS.items():
        if tags.get(key) in values:
            return True
    return False


def _poi_category(tags) -> Optional[str]:
    for name, match in POI_RULES:
        if match(tags):
            return name
    return None


def _polygon_area_m2(pts: Sequence[Tuple[float, float]]) -> float:
    if len(pts) < 3:
        return 0.0
    xs, zs, _, _ = to_local_m(pts)
    return shoelace_area_m2(xs, zs)


def _make_recipe_scan(osmium):
    """Costruisce l'handler solo quando pyosmium è disponibile."""

    class RecipeScan(osmium.SimpleHandler):
        """Passa sui tre PBF filtrati; raccoglie solo contatori, mai geometrie."""

        def __init__(self, bbox: Tuple[float, float, float, float]):
            super().__init__()
            self.bbox = bbox
            self.source = ""
            self.road_len = 0.0
            self.road_width_sum = 0.0
            self.road_count = 0
            self.road_classes: Counter = Counter()
            self.buildings = 0
            self.building_area = 0.0
            self.park_area = 0.0
            self.pois: Counter = Counter()

        def _inside(self, lat: float, lon: float) -> bool:
            latmin, lonmin, latmax, lonmax = self.bbox
            return latmin <= lat <= latmax and lonmin <= lon <= lonmax

        def node(self, n):
            if not n.location.valid():
                return
            loc = n.location
            if not self._inside(loc.lat, loc.lon):
                return
            cat = _poi_category(n.tags)
            if cat:
                self.pois[cat] += 1

        def way(self, w):
            pts: List[Tuple[float, float]] = []
            for nd in w.nodes:
                if not nd.location.valid():
                    continue
                loc = nd.location
                if self._inside(loc.lat, loc.lon):
                    pts.append((loc.lat, loc.lon))
            if not pts:
                return

            tags = w.tags
            hw = tags.get("highway")
            if hw in ROAD_HIGHWAYS and len(pts) >= 2:
                length = path_length_m(pts)
                self.road_len += length
                self.road_width_sum += road_width(hw) * length
                self.road_count += 1
                self.road_classes[hw] += 1

            if tags.get("building"):
                self.buildings += 1
                self.building_area += _polygon_area_m2(pts)
            if _is_park(tags):
                self.park_area += _polygon_area_m2(pts)

            cat = _poi_category(tags)
            if cat:
                self.pois[cat] += 1

    return RecipeScan


def _clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def derive_recipe(stats: Any, bbox: Tuple[float, float, float, float],
                  sources: Sequence[str]) -> dict:
    """Trasforma i contatori in parametri di generazione, senza PBF."""
    area = bbox_area_m2(bbox)
    area_km2 = area / 1_000_000.0
    density = stats.road_len / area  # metri di strada per m²

    # Griglia schematica: lunghezza stradale/area ~= 2 / passo.
    spacing = 2.0 / density if density > 0 else 160.0
    avg_width = (stats.road_width_sum / stats.road_len
                 if stats.road_len > 0 else 6.0)
    spacing = _clamp(spacing, 70.0, 400.0)
    avg_width = _clamp(avg_width, 5.0, 14.0)
    block = _clamp(spacing - avg_width, 40.0, 350.0)
    park_ratio = _clamp(stats.park_area / area, 0.02, 0.25)

    total_poi = sum(stats.pois.values())
    mix = {}
    if total_poi:
        for name, count in stats.pois.most_common(8):
            mix[name] = round(count / total_poi, 4)

    seed_src = ",".join(f"{v:.5f}" for v in bbox) + "|" + "|".join(sources)
    seed = int.from_bytes(hashlib.sha1(seed_src.encode("utf-8")).digest()[:4],
                          "big")

    return {
        "version": RECIPE_VERSION,
        "generated_by": "city_recipe.py (Huntix, statistiche OSM locali)",
        "source": {
            "bbox": list(bbox),
            "pbf": list(sources),
            "note": "solo parametri estratti una volta; nessuna geometria, DEM o tile a runtime",
        },
        "seed": seed,
        "grid": {
            "road_spacing_m": round(spacing, 1),
            "road_width_m": round(avg_width, 1),
            "block_m": round(block, 1),
            "park_ratio": round(park_ratio, 4),
            "buildings_per_km2": round(stats.buildings / area_km2, 1) if area_km2 else 0.0,
        },
        "poi_mix": mix,
        "road_classes": dict(stats.road_classes.most_common(6)),
        "raw": {
            "road_segments": stats.road_count,
            "road_length_m": round(stats.road_len, 1),
            "buildings": stats.buildings,
            "poi": total_poi,
            "bbox_area_m2": round(area, 1),
        },
    }


def extract(bbox: Tuple[float, float, float, float], pbf_dir: Path) -> dict:
    try:
        import osmium as osmium_mod
    except ImportError as exc:
        raise SystemExit("city_recipe richiede pyosmium: usare backend/preprocessing/venv/bin/python") from exc

    # Accetta sia i filtrati nazionali (italy-roads.pbf) sia un extract
    # locale già tagliato con `osmium extract --bbox` (roads.pbf): quest'ultimo
    # è molto più veloce da analizzare e resta fuori dal repository.
    files = []
    for name in ("roads", "areas", "points"):
        candidates = [pbf_dir / f"{name}.pbf", pbf_dir / f"italy-{name}.pbf"]
        path = next((p for p in candidates if p.exists()), None)
        if path is None:
            raise SystemExit(f"PBF mancante in {pbf_dir}: {name}.pbf")
        files.append((name, path))
    scan = _make_recipe_scan(osmium_mod)(bbox)
    used = []
    for name, path in files:
        print(f"[city-recipe] scansione {path.name}...", file=sys.stderr)
        scan.source = name
        # Le way dei PBF filtrati non portano le coordinate: serve l'indice
        # dei nodi, esattamente come in osm_italy_processor.py.
        scan.apply_file(str(path), locations=True, idx="sparse_mem_array")
        used.append(path.name)
    return derive_recipe(scan, bbox, used)


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--bbox", required=True,
                    help="latmin,lonmin,latmax,lonmax (es. 41.44,15.51,41.49,15.58)")
    ap.add_argument("--out", default=str(BASE / "data" / "city_recipe.json"))
    ap.add_argument("--pbf-dir", default=str(BASE / "data"))
    args = ap.parse_args(argv)

    bbox = parse_bbox(args.bbox)
    recipe = extract(bbox, Path(args.pbf_dir))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(recipe, indent=2, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    print(f"[city-recipe] scritto {out}")
    print(json.dumps(recipe["grid"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

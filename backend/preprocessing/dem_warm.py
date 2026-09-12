#!/usr/bin/env python3
"""HUNTIX — Backfill DEM sulle tile geo esistenti senza 'ele'.

Le geo scritte prima del bake non hanno la griglia elevazione: questo script
le riapre, calcola il DEM (ordine fonti: cache `dem_cache` -> SRTM HGT locale
-> API OpenTopoData con pacing) e riscrive il file con 'ele'/'ele_nrow'/
'ele_ncol'. Idempotente e riprudente: salta le tile che hanno gia' 'ele' e
quelle per cui il DEM non e' ottenibile (le riprova al giro successivo).

Uso:
    python3 dem_warm.py                          # tutte le tile in attesa
    python3 dem_warm.py --limit 300              # budget a passaggio (progressivo)
    python3 dem_warm.py --dry-run                # conta soltanto
    python3 dem_warm.py --sleep 0.5              # pacing extra tra le tile (API)
    python3 dem_warm.py --geo-dir ../tiles       # cartella tile alternativa

Suggerito per un primo giro completo:
    nohup ./dem_warm.sh > /tmp/dem_warm.log 2>&1 &
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import sys
import time
from pathlib import Path
from typing import List, Optional

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))


def _tile_key_from_name(fname: str) -> str:
    return fname[: -len("_geo.json.gz")]


def _load_geo(path: Path) -> Optional[dict]:
    try:
        with gzip.open(path, "rt", encoding="utf-8") as gz:
            return json.load(gz)
    except Exception:
        return None


def _save_geo(path: Path, doc: dict) -> None:
    tmp = path.with_suffix(".tmp.gz")
    with gzip.open(tmp, "wt", encoding="utf-8", compresslevel=6) as gz:
        gz.write(json.dumps(doc, separators=(",", ":")))
    tmp.replace(path)


def main() -> int:
    ap = argparse.ArgumentParser(description="Backfill DEM sulle tile geo senza elevazione")
    ap.add_argument("--geo-dir", default=str(BASE / "tiles"),
                    help="cartella delle tile geo (default: tiles/)")
    ap.add_argument("--limit", type=int, default=0,
                    help="numero massimo di tile da elaborare in questo passaggio (0=tutte)")
    ap.add_argument("--sleep", type=float, default=0.0,
                    help="pacing extra (s) tra una tile e l'altra (gentile con l'API)")
    ap.add_argument("--dry-run", action="store_true",
                    help="conta e elenca le tile in attesa senza scrivere")
    args = ap.parse_args()

    geo_dir = Path(args.geo_dir)
    if not geo_dir.is_dir():
        print(f"errore: cartella {geo_dir} non esistente")
        return 2

    from dem import bake_offline_grid, elevation_grid, grid_shape

    # tile di terra (= hanno un file geo), senza elevazione nel documento
    todo: List[Path] = []
    for p in sorted(geo_dir.glob("IT_*_geo.json.gz")):
        if p.name.startswith("IT_"):  # evita .tmp ecc
            doc = _load_geo(p)
            if doc and isinstance(doc, dict) and not doc.get("ele"):
                todo.append(p)

    n = len(todo)
    if n == 0:
        print("[dem_warm] nessuna tile da aggiornare: tutte le geo hanno gia' l'elevazione")
        return 0

    done = 0
    failed = 0
    st = time.time()
    batch = todo if args.limit <= 0 else todo[: args.limit]
    print(f"[dem_warm] {n} tile senza DEM | questo passaggio: {len(batch)}"
          + (" | DRY-RUN (nessuna scrittura)" if args.dry_run else ""))

    for i, p in enumerate(batch, 1):
        key = _tile_key_from_name(p.name)
        doc = _load_geo(p)
        if not doc or not isinstance(doc, dict) or doc.get("ele"):
            continue  # gia' provvista o irrecuperabile
        bbox = doc.get("bbox")
        if not bbox or len(bbox) != 4:
            print(f"[dem_warm] {key}: bbox mancante, salto")
            failed += 1
            continue

        # offline-first: la rete e' l'ultima scelta (e la cache ne trattiene
        # il risultato per sempre, quindi il costo e' una sola volta per tile)
        try:
            if not args.dry_run:
                grid = elevation_grid(key, bbox)
                if not grid:
                    raise ValueError("griglia vuota")
                nrow, ncol = grid_shape(bbox)
                doc["ele_nrow"] = nrow
                doc["ele_ncol"] = ncol
                doc["ele"] = [round(float(v), 1) for v in grid]
                _save_geo(p, doc)
        except Exception as e:  # noqa: BLE001
            print(f"[dem_warm] {key}: DEM non disponibile ({e}) — riprovero' al prossimo giro")
            failed += 1
            continue

        done += 1
        if i % 25 == 0 or i == len(batch):
            ms = time.time() - st
            per = ms / i
            print(f"[dem_warm] {i}/{len(batch)}  ({done} ok, {failed} mancanti)  "
                  f"{per:.2f}s/tile — restano {max(0, n - i)}")
        if args.sleep and not args.dry_run:
            time.sleep(args.sleep)

    rem = max(0, n - len(batch))
    print(f"[dem_warm] FATTO: {done} tile provviste, {failed} non disponibili, "
          f"{rem} rimandate al prossimo giro")
    if rem:
        print("[dem_warm] riavvia ./dem_warm.sh per proseguire (idempotente)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
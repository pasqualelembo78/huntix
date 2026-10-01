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


def _current_ver() -> Optional[str]:
    """Versione formato geo corrente, dal processore (None se non importabile:
    il backfill non deve fallire solo perche' manca osmium nel python usato).

    Serve perche' `--regrid` riscrive il CONTENUTO della geo nel formato nuovo:
    senza aggiornare `ver`, `geo-todo` continuerebbe a listare tutte le tile come
    obsolete e il prossimo `aggiorna_*` rigenererebbe 1265 geo inutili.
    """
    try:
        from osm_italy_processor import _geo_format_version
        return _geo_format_version()
    except Exception:
        return None


def _stamp_ver(doc: dict, new_ver: str) -> None:
    """Aggiorna `ver` preservando il suffisso `-bt` (tile con viadotti/
    gallerie): quello marcatore serve a far rigenerare SOLO loro al prossimo
    cambio di formato, e perderlo cambierebbe quali tile vengono toccate."""
    old = str(doc.get("ver") or "")
    suffix = "-bt" if old.endswith("-bt") else ""
    doc["ver"] = new_ver + suffix


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
    ap.add_argument("--regrid", action="store_true",
                    help="rigenera ANCHE le tile che hanno gia' 'ele' ( cambio "
                         "di passo/allineamento/riempimento void: la griglia "
                         "vecchia non e' piu' valida)")
    args = ap.parse_args()

    geo_dir = Path(args.geo_dir)
    if not geo_dir.is_dir():
        print(f"errore: cartella {geo_dir} non esistente")
        return 2

    from dem import bake_offline_grid, elevation_grid, grid_shape

    # tile di terra (= hanno un file geo), senza elevazione nel documento.
    # Con --regrid passano anche quelle che ne hanno una: dopo un cambio di
    # passo (1000 -> 250 m), di allineamento ai chunk o di riempimento void, la
    # griglia gia' scritta e' geometricamente sbagliata e va rifatta.
    todo: List[Path] = []
    for p in sorted(geo_dir.glob("IT_*_geo.json.gz")):
        if not p.name.startswith("IT_"):  # evita .tmp ecc
            continue
        doc = _load_geo(p)
        if not doc or not isinstance(doc, dict):
            continue
        if doc.get("ele") and not args.regrid:
            continue
        todo.append(p)

    n = len(todo)
    if n == 0:
        print("[dem_warm] nessuna tile da aggiornare: tutte le geo hanno gia' l'elevazione")
        return 0

    done = 0
    failed = 0
    stamped = 0
    st = time.time()
    batch = todo if args.limit <= 0 else todo[: args.limit]
    new_ver = _current_ver()
    if args.regrid and new_ver is None:
        print("[dem_warm] avviso: non riesco a leggere la versione formato geo "
              "(processore non importabile): i `ver` NON verranno aggiornati e "
              "geo-todo continuera' a listare queste tile come obsolete.")
    print(f"[dem_warm] {n} tile da aggiornare"
          + (" (REGRID: anche quelle con ele gia' presente)" if args.regrid else "")
          + f" | questo passaggio: {len(batch)}"
          + (" | DRY-RUN (nessuna scrittura)" if args.dry_run else ""))

    for i, p in enumerate(batch, 1):
        key = _tile_key_from_name(p.name)
        doc = _load_geo(p)
        if not doc or not isinstance(doc, dict):
            continue
        if doc.get("ele") and not args.regrid:
            continue  # gia' provvista e non richiesta la rigenerazione
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
                # Il contenuto e' ora nel formato corrente: allineo `ver` cosi'
                # geo-todo non chiede di rigenerare questi file al prossimo
                # aggiornamento. Solo con --regrid: un backfill "solo mancanti"
                # non deve cambiare la versione dichiarata di una geo.
                if args.regrid and new_ver is not None:
                    _stamp_ver(doc, new_ver)
                    stamped += 1
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
          f"{rem} rimandate al prossimo giro"
          + (f", {stamped} con `ver` aggiornato a {new_ver}" if stamped else ""))
    if rem:
        print("[dem_warm] riavvia ./dem_warm.sh per proseguire (idempotente)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
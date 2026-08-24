#!/usr/bin/env python3
"""HUNTIX Fase 1 — Pre-processing OSM Italia.

Pipeline (strategia on-demand, vedi italia.txt):
  filter    italy-latest.osm.pbf -> italy-roads/areas/points.pbf (permanenti)
  graph     grafi stradali pre-generati per TUTTA Italia -> tiles/*.json.gz
  gen-tile  genera ON-DEMAND la geometry (_geo) di una tile dai permanenti
  index     ricostruisce tiles/index.json
  info      ispeziona una tile
  download  scarica/riprende il PBF Geofabrik

Uso:
  python3 osm_italy_processor.py filter --clean
  python3 osm_italy_processor.py graph
  python3 osm_italy_processor.py gen-tile IT_087_061
  python3 osm_italy_processor.py index
"""

from __future__ import annotations

import argparse
import datetime as _dt
import gzip
import json
import shutil
import subprocess
import sys
import time
from collections import Counter, defaultdict, OrderedDict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tile_builder import (LAT_STEP, LON_STEP, ORIGIN_LAT, ORIGIN_LON,
                          ROAD_HIGHWAYS, is_real_junction, local_bearing_deg,
                          m_per_lon_at, oriented_bbox, path_length_m,
                          road_width, parse_speed_kmh, simplify_polyline,
                          shoelace_area_m2, split_way_by_tile, tile_bbox,
                          tile_key)

import osmium  # noqa: E402  (venv: backend/preprocessing/venv)

BASE = Path(__file__).resolve().parent
MIN_BUILDING_AREA_M2 = 30.0
PARK_SIMPLIFY_TOL_M = 4.0

# POI veicoli: concessionarie, officine e garage (estratti da OSM).
# In Unity sono gli unici luoghi dove comprare/vendere, riparare,
# mettere antifurti e parcheggiare al coperto.
SHOP_POI_TYPES = {"car": "dealer", "car_repair": "repair"}
GARAGE_PARKING_TYPES = {"multi-storey", "underground", "shed"}


def log(msg: str) -> None:
    print(f"[{_dt.datetime.now():%H:%M:%S}] {msg}", flush=True)


# ── Spill writer ─────────────────────────────────────────────────

class SpillWriter:
    """Buffer su disco: un .jsonl per tile, righe JSON compatte.
    LRU sugli handle (max_open) per non superare il limite di fd."""

    MAX_OPEN = 128

    def __init__(self, root: Path):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._fh: "OrderedDict[str, object]" = OrderedDict()
        self.counts: Counter = Counter()

    def _handle(self, key: str):
        fh = self._fh.get(key)
        if fh is not None:
            self._fh.move_to_end(key)
            return fh
        while len(self._fh) >= self.MAX_OPEN:
            old_key, old_fh = self._fh.popitem(last=False)
            old_fh.close()
        fh = open(self.root / f"{key}.jsonl", "a", encoding="utf-8")
        self._fh[key] = fh
        return fh

    def write(self, key: str, rec: dict) -> None:
        self._handle(key).write(json.dumps(rec, separators=(",", ":")) + "\n")
        self.counts[key] += 1

    def close(self) -> None:
        for fh in self._fh.values():
            fh.close()
        self._fh.clear()


# ── Handler pyosmium ─────────────────────────────────────────────

class JunctionScan(osmium.SimpleHandler):
    """Pass 1 (senza location): trova i nodi condivisi da >=2 way."

    seen  = nodi incontrati una volta
    shared= nodi confermati condivisi (junction candidate)
    """

    def __init__(self):
        super().__init__()
        self.seen: set = set()
        self.shared: set = set()
        self.n_ways = 0

    def way(self, w):
        if w.tags.get("highway") not in ROAD_HIGHWAYS:
            return
        self.n_ways += 1
        if self.n_ways % 500000 == 0:
            log(f"  scan: {self.n_ways} way, {len(self.seen)} nodi visti")
        for nd in w.nodes:
            r = nd.ref
            if r in self.seen:
                self.shared.add(r)
            else:
                self.seen.add(r)


def _way_attrs(tags) -> Optional[dict]:
    hw = tags.get("highway")
    if hw not in ROAD_HIGHWAYS:
        return None
    try:
        lanes = int(tags.get("lanes") or 0)
    except ValueError:
        lanes = 0
    oneway = tags.get("oneway") == "yes" or (
        hw in ("motorway", "motorway_link") and tags.get("oneway") != "no")
    return {
        "hw": hw, "nm": tags.get("name", ""),
        "tu": tags.get("tunnel") == "yes", "br": tags.get("bridge") == "yes",
        "ms": parse_speed_kmh(tags.get("maxspeed"), hw),
        "ow": oneway, "ln": lanes, "wd": road_width(hw),
    }


class RoadsEmit(osmium.SimpleHandler):
    """Pass 2 (con location): spezza le way per tile e scrive record
    graph ('g') e road-geometry ('r')."""

    def __init__(self, shared: set, spill_graph: SpillWriter,
                 spill_geo: SpillWriter):
        super().__init__()
        self.shared = shared
        self.graph = spill_graph
        self.geo = spill_geo
        self.n_way = 0
        self.n_rec = 0

    def way(self, w):
        a = _way_attrs(w.tags)
        if a is None:
            return
        pts: List[Tuple[float, float]] = []
        ids: List[int] = []
        try:
            for nd in w.nodes:
                loc = nd.location
                if not loc.valid():
                    return
                pts.append((round(loc.lat, 5), round(loc.lon, 5)))
                ids.append(nd.ref)
        except Exception:
            return
        if len(pts) < 2:
            return
        self.n_way += 1
        if self.n_way % 200000 == 0:
            log(f"  roads: {self.n_way} way processate, "
                f"{sum(self.graph.counts.values())} record")
        for run in split_way_by_tile(pts):
            if len(run) < 2:
                continue
            key = tile_key(*pts[run[0]])
            seg_pts = [list(p) for p in pts[run[0]:run[-1] + 1]]
            seg_ids = ids[run[0]:run[-1] + 1]
            self.graph.write(key, {"k": "g", **a, "nids": seg_ids,
                                   "pts": seg_pts})
            self.geo.write(key, {"k": "r", "nm": a["nm"], "hw": a["hw"],
                                 "pts": seg_pts})
            self.n_rec += 1


class AreasEmit(osmium.SimpleHandler):
    """Edifici come placement record + parchi semplificati + aeroporti.
    slice_ilat: se imposto, scrive solo record il cui centroide cade in
    quella fascia di latitudine (evita duplicati tra slice adiacenti)."""

    def __init__(self, spill_geo: SpillWriter, slice_ilat: Optional[int] = None):
        super().__init__()
        self.geo = spill_geo
        self.slice_ilat = slice_ilat
        self.n_bld = self.n_park = self.n_air = self.n_skip = 0
        self.n_poi = 0

    def _emit_poi_way(self, w_id: int, t, pts: List[Tuple[float, float]],
                      closed: bool) -> None:
        """POI veicolo da way (concessionaria/officina/garage poligono o linea):
        emette il centroide come punto."""
        shop = t.get("shop")
        if shop in SHOP_POI_TYPES:
            ptype = SHOP_POI_TYPES[shop]
        elif t.get("amenity") == "parking" and \
                t.get("parking") in GARAGE_PARKING_TYPES:
            ptype = "garage"
        else:
            return
        if closed:
            clat = sum(p[0] for p in pts[:-1]) / (len(pts) - 1)
            clon = sum(p[1] for p in pts[:-1]) / (len(pts) - 1)
        else:
            mid = len(pts) // 2
            clat, clon = pts[mid]
        if self.slice_ilat is not None and \
                tile_idx_lat(clat) != self.slice_ilat:
            return
        self.geo.write(tile_key(clat, clon),
                       {"k": "i", "id": w_id, "t": ptype,
                        "p": [round(clat, 5), round(clon, 5)],
                        "nm": t.get("name", "")})
        self.n_poi += 1

    def way(self, w):
        t = w.tags
        ids = [nd.ref for nd in w.nodes]
        closed = len(ids) >= 4 and ids[0] == ids[-1]
        pts: List[Tuple[float, float]] = []
        try:
            for nd in w.nodes:
                loc = nd.location
                if not loc.valid():
                    return
                pts.append((round(loc.lat, 5), round(loc.lon, 5)))
        except Exception:
            return
        name = t.get("name", "")

        # POI veicoli: emessi IN AGGIUNTA al resto (un edificio può essere
        # sia building=* che shop=car: vogliamo l'edificio E la concessionaria)
        self._emit_poi_way(w.id, t, pts, closed)

        kind = None
        kd = ""
        bval = t.get("building")
        if bval and bval != "no":
            kind = "b"
        elif t.get("natural") == "wood":
            kind, kd = "p", "wood"
        elif t.get("landuse") == "forest":
            kind, kd = "p", "forest"
        elif t.get("leisure") in ("park", "garden"):
            kind, kd = "p", t.get("leisure")
        elif t.get("aeroway") == "aerodrome":
            kind = "a"
        if kind is None:
            return

        if kind == "b":
            if not closed:
                self.n_skip += 1
                return
            cent, dims, rot = oriented_bbox(pts)
            clat, clon = cent
            mpl = m_per_lon_at(clat)
            xs = [(lon - clon) * mpl for _, lon in pts]
            zs = [(lat - clat) * 110540.0 for lat, _ in pts]
            area = shoelace_area_m2(xs, zs)
            if area < MIN_BUILDING_AREA_M2 or dims[0] < 1.5 or dims[1] < 1.5:
                self.n_skip += 1
                return
            if self.slice_ilat is not None and \
                    tile_idx_lat(clat) != self.slice_ilat:
                return
            self.geo.write(tile_key(clat, clon),
                           {"k": "b", "id": w.id, "c": cent, "d": dims,
                            "r": rot, "t": bval, "nm": name})
            self.n_bld += 1
        elif kind == "p":
            ring = pts[:-1] if closed else pts
            simp = simplify_polyline(ring, PARK_SIMPLIFY_TOL_M)
            if len(simp) < 3:
                self.n_skip += 1
                return
            clat = sum(p[0] for p in simp) / len(simp)
            clon = sum(p[1] for p in simp) / len(simp)
            if self.slice_ilat is not None and \
                    tile_idx_lat(clat) != self.slice_ilat:
                return
            self.geo.write(tile_key(clat, clon),
                           {"k": "p", "id": w.id, "kd": kd, "nm": name,
                            "poly": [list(p) for p in simp]})
            self.n_park += 1
        else:  # aerodromo
            if not closed:
                self.n_skip += 1
                return
            cent, dims, rot = oriented_bbox(pts)
            if self.slice_ilat is not None and \
                    tile_idx_lat(cent[0]) != self.slice_ilat:
                return
            self.geo.write(tile_key(*cent),
                           {"k": "a", "id": w.id, "nm": name, "c": cent,
                            "d": dims, "r": rot})
            self.n_air += 1


def tile_idx_lat(lat: float) -> int:
    return int((lat - ORIGIN_LAT) // LAT_STEP)


class PointsEmit(osmium.SimpleHandler):
    """Alberi, semafori, aeroporti puntiformi e POI veicoli (nodi)."""

    def __init__(self, spill_geo: SpillWriter):
        super().__init__()
        self.geo = spill_geo
        self.n_tree = self.n_sig = self.n_air = 0
        self.n_poi = 0

    def node(self, n):
        t = n.tags
        loc = n.location
        if not loc.valid():
            return
        p = [round(loc.lat, 5), round(loc.lon, 5)]
        if t.get("natural") == "tree":
            self.geo.write(tile_key(*p), {"k": "t", "p": p})
            self.n_tree += 1
        elif t.get("highway") == "traffic_signals":
            self.geo.write(tile_key(*p), {"k": "s", "p": p})
            self.n_sig += 1
        elif t.get("aeroway") == "aerodrome":
            self.geo.write(tile_key(*p),
                           {"k": "a", "id": n.id, "nm": t.get("name", ""),
                            "c": p, "d": [0, 0], "r": 0})
            self.n_air += 1
        elif t.get("shop") in SHOP_POI_TYPES:
            self.geo.write(tile_key(*p),
                           {"k": "i", "id": n.id, "t": SHOP_POI_TYPES[t["shop"]],
                            "p": p, "nm": t.get("name", "")})
            self.n_poi += 1
        elif t.get("amenity") == "parking" and \
                t.get("parking") in GARAGE_PARKING_TYPES:
            self.geo.write(tile_key(*p),
                           {"k": "i", "id": n.id, "t": "garage",
                            "p": p, "nm": t.get("name", "")})
            self.n_poi += 1


# ── Merge ────────────────────────────────────────────────────────

def _read_jsonl(path: Path) -> List[dict]:
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def merge_graph_tile(spill_file: Path, out_dir: Path) -> dict:
    key = spill_file.stem
    recs = _read_jsonl(spill_file)
    if not recs:
        return {}
    latmin, lonmin, latmax, lonmax = tile_bbox(key)
    center_lat = (latmin + latmax) / 2
    mpl = m_per_lon_at(center_lat)

    cnt: Counter = Counter()
    bear: Dict[int, List[float]] = defaultdict(list)
    for g in recs:
        nids, pts = g["nids"], g["pts"]
        for nid in nids:
            cnt[nid] += 1
        for i in range(len(pts) - 1):
            b = local_bearing_deg(pts[i], pts[i + 1], mpl)
            bear[nids[i]].append(b)
            bear[nids[i + 1]].append(b)

    nodes: Dict[int, dict] = {}

    def junction_type(nid: int) -> str:
        c = cnt.get(nid, 0)
        if c <= 1:
            return "DeadEnd"
        return "Real" if is_real_junction(bear.get(nid, [])) else "Simple"

    def touch(nid: int, pt: List) -> None:
        if nid not in nodes:
            nodes[nid] = {"id": nid, "lat": pt[0], "lon": pt[1],
                          "junction": junction_type(nid)}

    arcs = []
    used_pairs: set = set()
    for g in recs:
        nids, pts = g["nids"], g["pts"]
        imp = [i for i, nid in enumerate(nids)
               if i == 0 or i == len(nids) - 1 or cnt[nid] > 1]
        for j in range(len(imp) - 1):
            si, ei = imp[j], imp[j + 1]
            a, b = nids[si], nids[ei]
            if a == b:
                continue
            pair = (a, b) if a < b else (b, a)
            if pair in used_pairs:
                continue
            wp = pts[si:ei + 1]
            length = path_length_m([(p[0], p[1]) for p in wp])
            if length < 0.5:
                continue
            used_pairs.add(pair)
            touch(a, pts[si])
            touch(b, pts[ei])
            arcs.append({"id": len(arcs), "from": a, "to": b,
                         "waypoints": wp, "road_name": g["nm"],
                         "highway": g["hw"], "width": g["wd"],
                         "length_m": round(length, 1),
                         "tunnel": g["tu"], "bridge": g["br"],
                         "maxspeed": g["ms"], "oneway": g["ow"],
                         "lanes": g["ln"]})

    doc = {"tile": key, "bbox": [latmin, lonmin, latmax, lonmax],
           "nodes": list(nodes.values()), "arcs": arcs}
    # formato wire compatibile JsonUtility (Unity): coppie come {"a":lat,"o":lon}
    for arc in arcs:
        arc["waypoints"] = [{"a": p[0], "o": p[1]} for p in arc["waypoints"]]
    out_path = out_dir / f"{key}.json.gz"
    with gzip.open(out_path, "wt", encoding="utf-8", compresslevel=6) as gz:
        gz.write(json.dumps(doc, separators=(",", ":")))
    return {"key": key, "nodes": len(nodes), "arcs": len(arcs)}


def merge_geo_tile(spill_files: List[Path], out_dir: Path) -> dict:
    if not spill_files:
        return {}
    key = spill_files[0].stem
    recs: List[dict] = []
    for f in spill_files:
        recs.extend(_read_jsonl(f))
    if not recs:
        return {}
    latmin, lonmin, latmax, lonmax = tile_bbox(key)
    geo = {"tile": key, "bbox": [latmin, lonmin, latmax, lonmax],
           "roads": [], "buildings": [], "parks": [], "trees": [],
           "signals": [], "airports": [], "pois": []}
    for r in recs:
        k = r.pop("k")
        if k == "r":
            r["pts"] = [{"a": p[0], "o": p[1]} for p in r["pts"]]
            geo["roads"].append(r)
        elif k == "b":
            geo["buildings"].append(r)
        elif k == "p":
            r["poly"] = [{"a": p[0], "o": p[1]} for p in r["poly"]]
            geo["parks"].append(r)
        elif k == "t":
            geo["trees"].append({"a": r["p"][0], "o": r["p"][1]})
        elif k == "s":
            geo["signals"].append({"a": r["p"][0], "o": r["p"][1]})
        elif k == "i":
            geo["pois"].append(r)
        elif k == "a":
            geo["airports"].append(r)
    with gzip.open(out_dir / f"{key}_geo.json.gz", "wt",
                   encoding="utf-8", compresslevel=6) as gz:
        gz.write(json.dumps(geo, separators=(",", ":")))
    return {"key": key,
            "roads": len(geo["roads"]), "buildings": len(geo["buildings"]),
            "parks": len(geo["parks"]), "trees": len(geo["trees"]),
            "signals": len(geo["signals"]), "airports": len(geo["airports"]),
            "pois": len(geo["pois"])}


# ── Comandi ──────────────────────────────────────────────────────

class Ctx:
    def __init__(self, args):
        self.base = BASE
        self.data = Path(args.data_dir) if args.data_dir else BASE / "data"
        self.tiles = Path(args.tiles_dir) if args.tiles_dir else BASE / "tiles"
        self.work = Path(args.work_dir) if args.work_dir else BASE / "work"
        self.tiles.mkdir(parents=True, exist_ok=True)


def run_osmium(cmd: List[str]) -> None:
    log("$ " + " ".join(cmd[:6]) + (" ..." if len(cmd) > 6 else ""))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr[-3000:])
        raise SystemExit(f"osmium fallito: {' '.join(cmd[:3])}")


def cmd_filter(ctx: Ctx, src: Path, clean: bool, force: bool) -> None:
    targets = {
        "roads": ctx.data / "italy-roads.pbf",
        "areas": ctx.data / "italy-areas.pbf",
        "points": ctx.data / "italy-points.pbf",
    }
    filters = {
        "roads": ["w/highway=" + ",".join(sorted(ROAD_HIGHWAYS))],
        "areas": ["w/building", "w/natural=wood", "w/landuse=forest",
                  "w/leisure=park", "w/leisure=garden", "w/aeroway=aerodrome",
                  "w/shop=car", "w/shop=car_repair",
                  "w/amenity=parking AND parking=multi-storey",
                  "w/amenity=parking AND parking=underground",
                  "w/amenity=parking AND parking=shed"],
        "points": ["n/natural=tree", "n/highway=traffic_signals",
                   "n/aeroway=aerodrome",
                   "n/shop=car", "n/shop=car_repair",
                   "n/amenity=parking AND parking=multi-storey",
                   "n/amenity=parking AND parking=underground",
                   "n/amenity=parking AND parking=shed"],
    }
    missing = [t for t in targets.values() if force or not t.exists()]
    if not missing:
        log("estratti gia presenti (usa --force per rifare)")
    else:
        for name, path in targets.items():
            if path.exists() and not force:
                continue
            log(f"filtro {name} ...")
            run_osmium(["osmium", "tags-filter", str(src)] + filters[name]
                       + ["-o", str(path), "--overwrite"])
            size = path.stat().st_size / 1e6
            log(f"OK {path.name}: {size:.1f} MB")

    if all(t.exists() for t in targets.values()):
        total = sum(t.stat().st_size for t in targets.values())
        log(f"estratti completi: {total/1e9:.2f} GB totali")
        if clean and src.exists():
            freed = src.stat().st_size
            src.unlink()
            log(f"PBF originale eliminato: liberati {freed/1e9:.2f} GB")


def cmd_graph(ctx: Ctx) -> None:
    roads = ctx.data / "italy-roads.pbf"
    if not roads.exists():
        raise SystemExit(f"manca {roads}: esegui prima 'filter'")
    spill_g = SpillWriter(ctx.work / "spill" / "graph")
    spill_r = SpillWriter(ctx.work / "spill" / "geo")

    t0 = time.time()
    scan = JunctionScan()
    scan.apply_file(str(roads))
    log(f"pass1 ok: {scan.n_ways} way, {len(scan.shared)} nodi junction, "
        f"{len(scan.seen)} nodi totali ({time.time()-t0:.0f}s)")

    t0 = time.time()
    emit = RoadsEmit(scan.shared, spill_g, spill_r)
    emit.apply_file(str(roads), locations=True, idx="sparse_mem_array")
    spill_g.close()
    spill_r.close()
    del scan
    log(f"pass2 ok: {emit.n_way} way -> {sum(spill_g.counts.values())} record "
        f"in {time.time()-t0:.0f}s")

    keys = sorted(spill_g.counts.keys())
    log(f"merge di {len(keys)} tile ...")
    tot_n = tot_a = 0
    for i, key in enumerate(keys):
        st = merge_graph_tile(ctx.work / "spill" / "graph" / f"{key}.jsonl",
                              ctx.tiles)
        tot_n += st.get("nodes", 0)
        tot_a += st.get("arcs", 0)
        (ctx.work / "spill" / "graph" / f"{key}.jsonl").unlink()
        if (i + 1) % 100 == 0:
            log(f"  merge {i+1}/{len(keys)}")
    shutil.rmtree(ctx.work / "spill", ignore_errors=True)
    log(f"grafi completati: {len(keys)} tile, {tot_n} nodi, {tot_a} archi")


def cmd_gen_tile(ctx: Ctx, key: str, skip_graph: bool) -> None:
    latmin, lonmin, latmax, lonmax = tile_bbox(key)
    bbox = f"{lonmin},{latmin},{lonmax},{latmax}"
    tmp = ctx.work / f"tmp_{key}"
    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir(parents=True)

    try:
        extracted = {}
        for name in ("roads", "areas", "points"):
            srcp = ctx.data / f"italy-{name}.pbf"
            dstp = tmp / f"x-{name}.pbf"
            if not srcp.exists():
                log(f"avviso: manca {srcp.name}, salto {name}")
                continue
            run_osmium(["osmium", "extract", "--bbox", bbox,
                        "-s", "complete_ways", "-f", "pbf",
                        "-o", str(dstp), str(srcp), "--overwrite"])
            extracted[name] = dstp

        sg = SpillWriter(tmp / "sg")
        sr = SpillWriter(tmp / "sr")
        stats = {"key": key}

        if "roads" in extracted and not skip_graph:
            scan = JunctionScan()
            scan.apply_file(str(extracted["roads"]))
            emit = RoadsEmit(scan.shared, sg, sr)
            emit.apply_file(str(extracted["roads"]), locations=True,
                            idx="sparse_mem_array")
            del scan, emit
        elif "roads" in extracted:
            emit = RoadsEmit(set(), sg, sr)
            emit.apply_file(str(extracted["roads"]), locations=True,
                            idx="sparse_mem_array")
            del emit
        sg.close()

        if "areas" in extracted:
            ae = AreasEmit(sr)
            ae.apply_file(str(extracted["areas"]), locations=True,
                          idx="sparse_mem_array")
            stats["buildings"] = ae.n_bld
            stats["parks"] = ae.n_park
            stats["airports"] = ae.n_air
            stats["pois_way"] = ae.n_poi
            del ae
        if "points" in extracted:
            pe = PointsEmit(sr)
            pe.apply_file(str(extracted["points"]))
            stats["trees"] = pe.n_tree
            stats["signals"] = pe.n_sig
            stats["pois"] = pe.n_poi
            del pe
        sr.close()

        g_spill = tmp / "sg" / f"{key}.jsonl"
        r_spills = sorted((tmp / "sr").glob(f"{key}*.jsonl"))
        if g_spill.exists() and not (ctx.tiles / f"{key}.json.gz").exists():
            st = merge_graph_tile(g_spill, ctx.tiles)
            stats.update(nodes=st.get("nodes"), arcs=st.get("arcs"))
        if r_spills:
            st = merge_geo_tile(r_spills, ctx.tiles)
            stats.update({k: v for k, v in st.items()
                          if k in ("roads", "buildings", "parks", "trees",
                                   "signals", "airports", "pois")})
        log(f"gen-tile {key}: " + json.dumps(stats))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def cmd_index(ctx: Ctx) -> None:
    files = sorted(ctx.tiles.glob("IT_*.json.gz"))
    tiles = []
    tot_bytes = 0
    for f in files:
        key = f.name.replace(".json.gz", "").replace("_geo", "")
        try:
            with gzip.open(f, "rt", encoding="utf-8") as gz:
                d = json.load(gz)
        except Exception as e:
            log(f"avviso: {f.name} illeggibile ({e})")
            continue
        entry = {"key": key, "bbox": d["bbox"], "bytes": f.stat().st_size}
        for field, alias in (("nodes", "nodes"), ("arcs", "arcs"),
                             ("roads", "roads"), ("buildings", "buildings"),
                             ("trees", "trees"), ("signals", "signals"),
                             ("parks", "parks"), ("airports", "airports"),
                             ("pois", "pois")):
            if field in d:
                entry[alias] = len(d[field])
        tiles.append(entry)
        tot_bytes += entry["bytes"]
    idx = {
        "generated": _dt.datetime.now().isoformat(timespec="seconds"),
        "grid": {"origin": [ORIGIN_LAT, ORIGIN_LON],
                 "steps_deg": [LAT_STEP, LON_STEP]},
        "totals": {"tiles": len(tiles), "bytes": tot_bytes},
        "tiles": tiles,
    }
    out = ctx.tiles / "index.json"
    out.write_text(json.dumps(idx, separators=(",", ":")), encoding="utf-8")
    log(f"index.json: {len(tiles)} tile, {tot_bytes/1e6:.1f} MB")


def cmd_info(path: Path) -> None:
    with gzip.open(path, "rt", encoding="utf-8") as gz:
        d = json.load(gz)
    summary = {k: (len(v) if isinstance(v, list) else v) for k, v in d.items()}
    log(f"{path.name}: " + json.dumps(summary, ensure_ascii=False))
    for field in ("nodes", "arcs", "buildings", "roads"):
        if isinstance(d.get(field), list) and d[field]:
            log(f"  esempio {field}[0]: "
                + json.dumps(d[field][0], ensure_ascii=False)[:300])


# ── CLI ──────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("filter")
    f.add_argument("--input", default=None, help="PBF sorgente (default data/italy-latest.osm.pbf)")
    f.add_argument("--clean", action="store_true", help="elimina il PBF originale al termine")
    f.add_argument("--force", action="store_true")
    sub.add_parser("graph")
    gt = sub.add_parser("gen-tile")
    gt.add_argument("tile_key")
    gt.add_argument("--skip-graph", action="store_true")
    sub.add_parser("index")
    inf = sub.add_parser("info")
    inf.add_argument("file")
    dl = sub.add_parser("download")
    dl.add_argument("--force", action="store_true")
    ap.add_argument("--data-dir", default=None)
    ap.add_argument("--tiles-dir", default=None)
    ap.add_argument("--work-dir", default=None)
    args = ap.parse_args()

    ctx = Ctx(args)
    if args.cmd == "filter":
        src = Path(args.input) if args.input else \
            ctx.data / "italy-latest.osm.pbf"
        cmd_filter(ctx, src, args.clean, args.force)
    elif args.cmd == "graph":
        cmd_graph(ctx)
        cmd_index(ctx)
    elif args.cmd == "gen-tile":
        cmd_gen_tile(ctx, args.tile_key, args.skip_graph)
        cmd_index(ctx)
    elif args.cmd == "index":
        cmd_index(ctx)
    elif args.cmd == "info":
        cmd_info(Path(args.file))
    elif args.cmd == "download":
        dst = ctx.data / "italy-latest.osm.pbf"
        ctx.data.mkdir(parents=True, exist_ok=True)
        subprocess.run(["wget", "-c", "-q", "--show-progress",
                        "https://download.geofabrik.de/europe/italy-latest"
                        ".osm.pbf", "-O", str(dst)], check=True)


if __name__ == "__main__":
    main()

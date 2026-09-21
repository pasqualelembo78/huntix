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
import hashlib
import json
import math
import os
import random
import shutil
import subprocess
import sys
import time
import zlib
from collections import Counter, defaultdict, OrderedDict
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tile_builder import (LAT_STEP, LON_STEP, ORIGIN_LAT, ORIGIN_LON,
                          ROAD_HIGHWAYS, clip_ring_to_bbox, haversine_m,
                          is_real_junction, local_bearing_deg, m_per_lon_at,
                          oriented_bbox, path_length_m, road_width,
                          parse_speed_kmh, simplify_polyline,
                          shoelace_area_m2, split_way_by_tile, tile_bbox,
                          tile_key)

import osmium  # noqa: E402  (venv: backend/preprocessing/venv)

BASE = Path(__file__).resolve().parent
MIN_BUILDING_AREA_M2 = 30.0
PARK_SIMPLIFY_TOL_M = 4.0

# POI veicoli: concessionarie, officine e garage (estratti da OSM).
# In Unity sono gli unici luoghi dove comprare/vendere, riparare,
# mettere antifurti e parcheggiare al coperto.
# Ospedali/cliniche: destinazioni di navigazione sulla mappa.
SHOP_POI_TYPES = {"car": "dealer", "car_repair": "repair"}
GARAGE_PARKING_TYPES = {"multi-storey", "underground", "shed"}
HOSPITAL_AMENITIES = {"hospital", "clinic"}
# Destinazioni di navigazione aggiuntive (menu POI della mappa): scuole e bar
SCHOOL_AMENITIES = {"school", "kindergarten", "college", "university"}
BAR_AMENITIES = {"bar", "cafe", "pub", "fast_food", "restaurant"}
BANK_AMENITIES = {"bank", "atm"}

# Filtri OSM (una lista = un osmium tags-filter per PBF "permanente").
# Ogni cambiamento qui DEVE comportare una ri-estrazione automatica dei
# filtrati: la firma SHA1 viene salvata in data/filter.sig e confrontata
# a ogni esecuzione (se cambia, il filtro si rifà da solo, senza --force).
FILTERS = {
    "roads": ["w/highway=" + ",".join(sorted(ROAD_HIGHWAYS))],
    "areas": ["w/building",
              "w/natural=wood", "w/natural=water", "w/natural=wetland",
              "w/natural=sand", "w/natural=beach", "w/natural=scrub",
              "w/natural=grassland",
              "w/landuse=forest", "w/landuse=farmland", "w/landuse=grass",
              "w/landuse=meadow", "w/landuse=vineyard", "w/landuse=orchard",
              "w/landuse=residential", "w/landuse=commercial",
              "w/landuse=industrial", "w/landuse=retail",
              "w/landuse=cemetery", "w/landuse=construction",
              "w/leisure=park", "w/leisure=garden",
              "w/leisure=golf_course", "w/leisure=playground",
              "w/aeroway=aerodrome",
              "w/shop=car", "w/shop=car_repair",
              "w/amenity=parking AND parking=multi-storey",
              "w/amenity=parking AND parking=underground",
              "w/amenity=parking AND parking=shed",
              "w/amenity=hospital", "w/amenity=clinic",
              "w/amenity=school", "w/amenity=kindergarten",
              "w/amenity=college", "w/amenity=university",
              "w/amenity=bar", "w/amenity=cafe", "w/amenity=pub",
              "w/amenity=fast_food", "w/amenity=restaurant",
              "w/amenity=bank", "w/amenity=atm"],
    "points": ["n/natural=tree", "n/highway=traffic_signals",
               "n/aeroway=aerodrome",
               "n/shop=car", "n/shop=car_repair",
               "n/amenity=parking AND parking=multi-storey",
               "n/amenity=parking AND parking=underground",
               "n/amenity=parking AND parking=shed",
               "n/amenity=hospital", "n/amenity=clinic",
               "n/amenity=school", "n/amenity=kindergarten",
               "n/amenity=college", "n/amenity=university",
               "n/amenity=bar", "n/amenity=cafe", "n/amenity=pub",
               "n/amenity=fast_food", "n/amenity=restaurant",
               "n/amenity=bank", "n/amenity=atm"],
}

# Versione del FORMATO GEOMETRIA/POI scritto nei {key}_geo.json.gz.
# Incrementa questo numero a mano quando cambia il processore (nuovo POI,
# nuovo campo, nuova geometria, nuovo bake con elevazione diversa...):
# alla prossima aggiorna_* le tile esistenti verranno rigenerate SOLO nella
# parte geo (grafi, dump e PBF filtrati restano intatti). La versione reale
# incorpora anche la firma dei filtri: se aggiungi un tag ai FILTERS qui
# sopra, cambia da sola.
GEO_FORMAT_BASE = 1


def _filter_sig() -> str:
    """Firma SHA1 della configurazione FILTERS (ri-estrazione automatica)."""
    return hashlib.sha1(json.dumps(FILTERS, sort_keys=True).encode()).hexdigest()


def _geo_format_version() -> str:
    """Versione corrente del formato geo: base del processore + sig filtri."""
    return f"{GEO_FORMAT_BASE}-{_filter_sig()[:8]}"


def _poi_type_for(t) -> Optional[str]:
    """Tipo POI (stringa wire) per i tag OSM, o None se non e' un POI.

    Condiviso tra way e node cosi' le due categorie restano coerenti.
    """
    shop = t.get("shop")
    if shop in SHOP_POI_TYPES:
        return SHOP_POI_TYPES[shop]
    if t.get("amenity") in HOSPITAL_AMENITIES:
        return "hospital"
    if t.get("amenity") in SCHOOL_AMENITIES:
        return "school"
    if t.get("amenity") in BAR_AMENITIES:
        return "bar"
    if t.get("amenity") in BANK_AMENITIES:
        return "bank"
    if t.get("amenity") == "parking" and \
            t.get("parking") in GARAGE_PARKING_TYPES:
        return "garage"
    return None


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

        # Viadotto/galleria: il deck e' la retta fra i due capisaldi (portali/
        # impalcature) della WAY INTERA, con frazioni di lunghezza GLOBALI
        # `s0`/`s1` per ogni tratto: la quota resta continua anche su ponti
        # lunghi che tagliano piu' tile. Senza dati DEM ai capisaldi la strada
        # resta classica (drape sul terreno), mai rotta.
        is_elev = a["tu"] or a["br"]
        h0 = h1 = None
        cum = None
        if is_elev:
            cum = [0.0]
            for k in range(1, len(pts)):
                cum.append(cum[-1] + haversine_m(pts[k - 1], pts[k]))
            if cum[-1] > 1.0:
                try:
                    from dem import point_height
                    ph0 = point_height(*pts[0])
                    ph1 = point_height(*pts[-1])
                    if ph0 is not None and ph1 is not None:
                        h0, h1 = round(ph0, 1), round(ph1, 1)
                except Exception:
                    h0 = h1 = None

        for run in split_way_by_tile(pts):
            if len(run) < 2:
                continue
            key = tile_key(*pts[run[0]])
            seg_pts = [list(p) for p in pts[run[0]:run[-1] + 1]]
            seg_ids = ids[run[0]:run[-1] + 1]
            # Profilo di impalcato (dh): IDENTICO sui record grafo ("g",
            # traffico auto) e ribbon ("r", mesh stradale) dello stesso tratto,
            # cosi' le auto guidano alla stessa quota del deck disegnato.
            selev = None
            if is_elev and h0 is not None and h1 is not None and \
                    cum is not None and cum[-1] > 0.0:
                L = cum[-1]
                selev = {"dh": True, "h0": h0, "h1": h1,
                         "s0": round(cum[run[0]] / L, 6),
                         "s1": round(cum[run[-1]] / L, 6)}
            grec = {"k": "g", **a, "nids": seg_ids, "pts": seg_pts}
            if selev:
                grec.update(selev)
            self.graph.write(key, grec)
            rec = {"k": "r", "nm": a["nm"], "hw": a["hw"], "pts": seg_pts}
            if a["tu"]:
                rec["tu"] = True
            if a["br"]:
                rec["br"] = True
            if selev:
                rec.update(selev)
            self.geo.write(key, rec)
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
        """POI da way (concessionaria/officina/garage/ospedale/scuola/bar
        poligono o linea): emette il centroide come punto."""
        ptype = _poi_type_for(t)
        if ptype is None:
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
        elif t.get("natural") == "water":
            kind, kd = "p", "water"
        elif t.get("natural") == "wetland":
            kind, kd = "p", "wetland"
        elif t.get("natural") == "sand":
            kind, kd = "p", "sand"
        elif t.get("natural") == "beach":
            kind, kd = "p", "beach"
        elif t.get("natural") == "scrub":
            kind, kd = "p", "scrub"
        elif t.get("natural") == "grassland":
            kind, kd = "p", "grassland"
        elif t.get("landuse") == "forest":
            kind, kd = "p", "forest"
        elif t.get("landuse") == "farmland":
            kind, kd = "p", "farmland"
        elif t.get("landuse") == "grass":
            kind, kd = "p", "grass"
        elif t.get("landuse") == "meadow":
            kind, kd = "p", "meadow"
        elif t.get("landuse") == "vineyard":
            kind, kd = "p", "vineyard"
        elif t.get("landuse") == "orchard":
            kind, kd = "p", "orchard"
        elif t.get("landuse") == "residential":
            kind, kd = "p", "residential"
        elif t.get("landuse") == "commercial":
            kind, kd = "p", "commercial"
        elif t.get("landuse") == "industrial":
            kind, kd = "p", "industrial"
        elif t.get("landuse") == "retail":
            kind, kd = "p", "retail"
        elif t.get("landuse") == "cemetery":
            kind, kd = "p", "cemetery"
        elif t.get("landuse") == "construction":
            kind, kd = "p", "construction"
        elif t.get("leisure") in ("park", "garden"):
            kind, kd = "p", t.get("leisure")
        elif t.get("leisure") == "golf_course":
            kind, kd = "p", "golf_course"
        elif t.get("leisure") == "playground":
            kind, kd = "p", "playground"
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
            # Poligono semplificato (0.5 m tolleranza) per merge preciso
            # degli edifici adiacenti in Unity.
            ring = pts[:-1] if closed else pts
            bpts = simplify_polyline(ring, 0.5)
            self.geo.write(tile_key(clat, clon),
                           {"k": "b", "id": w.id, "c": cent, "d": dims,
                            "r": rot, "t": bval, "nm": name,
                            "pts": [list(p) for p in bpts]})
            self.n_bld += 1
        elif kind == "p":
            ring = pts[:-1] if closed else pts
            simp = simplify_polyline(ring, PARK_SIMPLIFY_TOL_M)
            if len(simp) < 3:
                self.n_skip += 1
                return
            clat = sum(p[0] for p in simp) / len(simp)
            clon = sum(p[1] for p in simp) / len(simp)
            idx = tile_idx_lat(clat)
            if self.slice_ilat is not None and idx != self.slice_ilat:
                return
            key = tile_key(clat, clon)
            # Taglio al bbox della tile del centroide: i poligoni OSM possono
            # estendersi ben oltre (foreste/boschi di montagna) e senza clip
            # generano lastre verdi fuori griglia + duplicati fra tile.
            t = tile_bbox(key)
            simp = clip_ring_to_bbox(simp, t[0], t[1], t[2], t[3])
            # Il taglio crea punti sintetici: ri-semplifica ai bordi.
            simp = simplify_polyline(simp, PARK_SIMPLIFY_TOL_M) if len(simp) >= 3 else []
            if len(simp) < 3:
                self.n_skip += 1
                return
            self.geo.write(key,
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
        else:
            ptype = _poi_type_for(t)
            if ptype is not None:
                self.geo.write(tile_key(*p),
                               {"k": "i", "id": n.id, "t": ptype,
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
                         "lanes": g["ln"],
                         "dh": g.get("dh", False),
                         "h0": g.get("h0", 0.0), "h1": g.get("h1", 0.0),
                         "s0": g.get("s0", 0.0), "s1": g.get("s1", 0.0)})

    doc = {"tile": key, "bbox": [latmin, lonmin, latmax, lonmax],
           "nodes": list(nodes.values()), "arcs": arcs}
    # formato wire compatibile JsonUtility (Unity): coppie come {"a":lat,"o":lon}
    for arc in arcs:
        arc["waypoints"] = [{"a": p[0], "o": p[1]} for p in arc["waypoints"]]
    out_path = out_dir / f"{key}.json.gz"
    with gzip.open(out_path, "wt", encoding="utf-8", compresslevel=6) as gz:
        gz.write(json.dumps(doc, separators=(",", ":")))
    return {"key": key, "nodes": len(nodes), "arcs": len(arcs)}


def _load_poi_overrides(data_dir: Path) -> dict:
    path = data_dir / "poi_overrides.json"
    if not path.exists():
        return {}
    try:
        with open(path, "r", encoding="utf-8") as fh:
            doc = json.load(fh)
        return doc if isinstance(doc, dict) else {}
    except Exception:
        log(f"avviso: {path.name} illeggibile, ignorato")
        return {}


_SYNTH_GARAGE_NAMES = [
    "Autorimessa Comunale", "Garage Centrale", "Parcheggio Stazione",
    "Autorimessa Garibaldi", "Garage Vittorio Emanuele",
    "Parcheggio del Mercato", "Autorimessa Dante", "Garage Roma",
    "Parcheggio Villa Comunale", "Autorimessa Italia",
]

_MAJOR_HW = {"primary", "secondary", "tertiary"}


def _synth_garages(key: str, geo: dict) -> list:
    """Garage sintetici per tile urbane senza parcheggi coperti OSM.

    Posizioni deterministiche lungo le strade principali, sfalsate ~12 m
    dal centrostrada; nomi plausibili ruotati. Stessa tile = stessi POI.
    """
    buildings = len(geo.get("buildings") or [])
    if buildings < 400:
        return []
    pois = geo.get("pois") or []
    if any(p.get("t") == "garage" for p in pois):
        return []

    want = 3 + min(3, buildings // 5000)
    mlat = 1.0 / 111320.0
    candidates = []
    for r in geo.get("roads") or []:
        if r.get("hw") not in _MAJOR_HW:
            continue
        pts = r.get("pts") or []
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            dlat = b["a"] - a["a"]
            dlon = (b["o"] - a["o"]) * math.cos(math.radians(a["a"]))
            seg_m = math.hypot(dlat / mlat, dlon / 111320.0)
            if seg_m < 120.0:
                continue
            mlon = 111320.0 * math.cos(math.radians(a["a"]))
            ux, uy = dlon * mlon / seg_m, dlat / mlat / seg_m
            px, py = -uy, ux
            lat = (a["a"] + b["a"]) / 2.0 + py * 12.0 * mlat
            lon = (a["o"] + b["o"]) / 2.0 + px * 12.0 / mlon
            candidates.append((lat, lon, r.get("nm") or ""))
            break

    rng = random.Random(zlib.crc32(key.encode("utf-8")))
    rng.shuffle(candidates)
    picked = []
    min_sep_deg = 800.0 * mlat
    for cand in candidates:
        if len(picked) >= want:
            break
        if all(abs(cand[0] - q[0]) > min_sep_deg
               or abs(cand[1] - q[1]) > min_sep_deg for q in picked):
            picked.append(cand)

    base_id = -(zlib.crc32(("garage_" + key).encode("utf-8")) % 10**8) * 10
    out = []
    for i, (lat, lon, street) in enumerate(picked):
        nm = (_SYNTH_GARAGE_NAMES[i % len(_SYNTH_GARAGE_NAMES)]
              if not street else f"Garage {street}")
        out.append({"id": base_id - i, "t": "garage",
                    "p": [round(lat, 6), round(lon, 6)], "nm": nm})
    return out


_RAMP_NAMES = [
    "Entrata Parcheggio Nord", "Entrata Parcheggio Sud",
    "Entrata Parcheggio Est", "Entrata Parcheggio Ovest",
    "Ingresso Sotterraneo", "Rampa Sotterranea",
]


def _synth_ramps(key: str, geo: dict) -> list:
    """Rampe di ingresso al parcheggio sotterraneo nelle tile urbane.
    2-4 rampe per tile, ogni ~1.5 km, lungo le strade principali.
    Deterministiche: stessa tile = stessi POI.
    """
    buildings = len(geo.get("buildings") or [])
    if buildings < 400:
        return []

    want = 2 + min(2, buildings // 8000)
    mlat = 1.0 / 111320.0
    candidates = []
    for r in geo.get("roads") or []:
        if r.get("hw") not in _MAJOR_HW:
            continue
        pts = r.get("pts") or []
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            dlat = b["a"] - a["a"]
            dlon = (b["o"] - a["o"]) * math.cos(math.radians(a["a"]))
            seg_m = math.hypot(dlat / mlat, dlon / 111320.0)
            if seg_m < 200.0:
                continue
            mlon = 111320.0 * math.cos(math.radians(a["a"]))
            ux, uy = dlon * mlon / seg_m, dlat / mlat / seg_m
            px, py = -uy, ux
            lat = (a["a"] + b["a"]) / 2.0 + py * 15.0 * mlat
            lon = (a["o"] + b["o"]) / 2.0 + px * 15.0 / mlon
            candidates.append((lat, lon))
            break

    rng = random.Random(zlib.crc32(("ramp_" + key).encode("utf-8")))
    rng.shuffle(candidates)
    picked = []
    min_sep = 1500.0 * mlat
    for cand in candidates:
        if len(picked) >= want:
            break
        if all(abs(cand[0] - q[0]) > min_sep or
               abs(cand[1] - q[1]) > min_sep for q in picked):
            picked.append(cand)

    base_id = -(zlib.crc32(("ramp_" + key).encode("utf-8")) % 10**8) * 10
    out = []
    for i, (lat, lon) in enumerate(picked):
        out.append({"id": base_id - i, "t": "rampa",
                    "p": [round(lat, 6), round(lon, 6)],
                    "nm": _RAMP_NAMES[i % len(_RAMP_NAMES)]})
    return out


def _augment_geo(key: str, geo: dict, overrides: dict) -> None:
    extra = overrides.get(key) or []
    oid = -1_000_000_000
    for e in extra:
        geo.setdefault("pois", []).append(
            {"id": oid, "t": e.get("t", "garage"),
             "p": [e["p"][0], e["p"][1]], "nm": e.get("nm", "")})
        oid -= 1
    synth = _synth_garages(key, geo)
    if synth:
        geo["pois"].extend(synth)
    ramps = _synth_ramps(key, geo)
    if ramps:
        geo["pois"].extend(ramps)


def merge_geo_tile(spill_files: List[Path], out_dir: Path,
                   overrides: dict | None = None) -> dict:
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
            # Serializza poligono semplificato in formato GeoLL (a=lat, o=lon)
            if "pts" in r:
                r["pts"] = [{"a": p[0], "o": p[1]} for p in r["pts"]]
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
    # I tile con almeno una strada su viadotto/galleria portano il marcatore
    # `-bt` nella `ver`: il prossimo aggiornamento rigenera SOLO quelli (via
    # geo-todo), le altre tile (senza bridge/tunnel) restano byte-identiche
    # e non vengono mai toccate.
    if any(r.get("tu") or r.get("br") for r in geo["roads"]):
        geo["ver"] = _geo_format_version() + "-bt"
    else:
        geo["ver"] = _geo_format_version()
    if overrides is None:
        overrides = _load_poi_overrides(out_dir.parent / "data")
    _augment_geo(key, geo, overrides or {})
    _bake_dem(key, geo)
    with gzip.open(out_dir / f"{key}_geo.json.gz", "wt",
                   encoding="utf-8", compresslevel=6) as gz:
        gz.write(json.dumps(geo, separators=(",", ":")))
    return {"key": key,
            "roads": len(geo["roads"]), "buildings": len(geo["buildings"]),
            "parks": len(geo["parks"]), "trees": len(geo["trees"]),
            "signals": len(geo["signals"]), "airports": len(geo["airports"]),
            "pois": len(geo["pois"])}


def _bake_dem(key: str, geo: dict) -> None:
    """Inietta la griglia DEM ('ele'/'ele_nrow'/'ele_ncol') dentro la geo AL
    MOMENTO della generazione, cosi' il file su disco HA per sempre
    l'elevazione e il tile server e' solo un lettore.

    SOLO fonti deterministiche locali (cache `dem_cache` o SRTM HGT):
    MAI rete qui — i batch `gen-tile` girano in 4 processi paralleli e
    un accesso all'API OpenTopoData contemporaneo causerebbe 429 a raffica.
    Le tile senza DEM locale restano senza 'ele': il backfill `dem_warm.py`
    (pacing serale, rete ammessa) le provvede subito dopo.

    Disattivabile (per bachi che non ne hanno bisogno): HUNTIX_DEM_BAKE=0.
    """
    if os.environ.get("HUNTIX_DEM_BAKE", "1") != "1":
        return
    if not isinstance(geo, dict) or geo.get("ele") is not None:
        return
    bbox = geo.get("bbox")
    if not bbox or len(bbox) != 4:
        return
    try:
        from dem import bake_offline_grid, grid_shape
        grid = bake_offline_grid(key, bbox)
        if not grid:
            log(f"avviso: DEM offline non disponibile per {key} (cache/HGT mancanti: "
                f"lo provvedera' dem_warm.py)")
            return
        nrow, ncol = grid_shape(bbox)
        geo["ele_nrow"] = nrow
        geo["ele_ncol"] = ncol
        geo["ele"] = [round(float(v), 1) for v in grid]
        log(f"DEM {key}: {nrow}x{ncol} iniettata (offline)")
    except Exception as e:  # noqa: BLE001
        log(f"avviso: DEM bake fallito per {key}: {e}")


# ── Comandi ──────────────────────────────────────────────────────

class Ctx:
    def __init__(self, args):
        self.base = BASE
        self.data = Path(args.data_dir) if args.data_dir else BASE / "data"
        self.tiles = Path(args.tiles_dir) if args.tiles_dir else BASE / "tiles"
        self.work = Path(args.work_dir) if args.work_dir else BASE / "work"
        self.tiles.mkdir(parents=True, exist_ok=True)
        # Paese attivo (env): default "italy". I file filtrati sono
        # data/<paese>-{roads,areas,points}.pbf; la chiave tile resta quella
        # posizionale globale IT_<ilat>_<ilon> (griglia tipo tile_builder).
        self.country = os.environ.get("HUNTIX_COUNTRY", "italy").strip()

    def pbf(self, name: str) -> Path:
        """Percorso data/<paese>-<name>.pbf del paese attivo."""
        return self.data / f"{self.country}-{name}.pbf"


def run_osmium(cmd: List[str]) -> None:
    log("$ " + " ".join(cmd[:6]) + (" ..." if len(cmd) > 6 else ""))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr[-3000:])
        raise SystemExit(f"osmium fallito: {' '.join(cmd[:3])}")


class _LandScan(osmium.SimpleHandler):
    """Rileva le tile che contengono almeno un oggetto (terra, non mare).
    Usato dai batch per generare solo le tile con contenuto.""" 

    def __init__(self):
        super().__init__()
        self.keys = set()

    def way(self, w):
        locs = w.nodes
        n = len(locs)
        if n == 0:
            return
        lat = sum(l.lat for l in locs) / n
        lon = sum(l.lon for l in locs) / n
        self.keys.add(tile_key(lat, lon))

    def node(self, n):
        try:
            self.keys.add(tile_key(n.location.lat, n.location.lon))
        except Exception:
            pass


def cmd_land(ctx: Ctx, with_areas: bool = False) -> None:
    """Elenco tile di terra del paese attivo: data/<paese>-land_keys.txt.
    Default: solo points+roads (veloce; le aree edifici raddoppiavano i tempi
    senza aumentare la copertura). Con --with-areas copre anche il verde.""" 
    out = ctx.data / f"{ctx.country}-land_keys.txt"
    keys = set()
    names = ["points", "roads"] + (["areas"] if with_areas else [])
    for name in names:
        srcp = ctx.pbf(name)
        if not srcp.exists():
            log(f"land: salto {name} (manca {srcp.name})")
            continue
        scan = _LandScan()
        scan.apply_file(str(srcp), locations=True, idx="sparse_mem_array")
        keys |= scan.keys
        log(f"land {name}: {len(keys)} tile")
    with open(out, "w") as f:
        f.write("\n".join(sorted(keys)) + "\n")
    log(f"land keys ({ctx.country}): {len(keys)} tile -> {out.name}")


def _min_pbf_bytes(min_pbf_mb: float | None = None) -> int:
    """Soglia minima (byte) oltre la quale un PBF filtrato è 'vivo'.
    Un file troncato/corrotto (es. estrazione interrotta o `--overwrite`
    fallito) resta quasi a zero byte: va rigenerato, non riusato."""
    if min_pbf_mb is None:
        min_pbf_mb = float(os.environ.get("HUNTIX_MIN_PBF_MB", "1"))
    return int(min_pbf_mb * 1_000_000)


def cmd_filter(ctx: Ctx, src: Path, clean: bool, force: bool,
               min_pbf_mb: float | None = None) -> None:
    if not src.exists() or src.stat().st_size < _min_pbf_bytes(min_pbf_mb):
        raise SystemExit(
            f"PBF sorgente non valido o mancante: {src} (dimensione minima "
            f"{_min_pbf_bytes(min_pbf_mb)/1e6:.0f} MB). Lancia prima 'download'.")
    minbytes = _min_pbf_bytes(min_pbf_mb)

    # Cambio di configurazione dei filtri (nuovo tag/POI) => ri-estrai da
    # solo, come se --force, ma idempotente: la firma viene aggiornata alla
    # fine e le estrazioni successive che la ritrovano identica saltano.
    # La firma e' PER PAESE: un update di FILTERS deve far ri-estrarre ogni
    # nazione a modo suo (mondo_*), non solo la prima che riaggancia.
    sig_path = ctx.data / f"{ctx.country}-filter.sig"
    sig_now = ""
    try:
        sig_now = sig_path.read_text().strip()
    except OSError:
        pass
    sig_cur = _filter_sig()
    if sig_now != sig_cur:
        log(f"configurazione filtri cambiata (firma {sig_now or 'assente'} -> "
            f"{sig_cur[:8]}): ri-estrazione dei filtrati")
        force = True

    def filtro(name: str, path: Path) -> None:
        if path.exists() and not force and path.stat().st_size >= minbytes:
            return
        tmp = path.with_name(path.name + ".tmp")
        if tmp.exists():
            tmp.unlink(missing_ok=True)
        try:
            log(f"filtro {name} ...")
            run_osmium(["osmium", "tags-filter", str(src)] + FILTERS[name]
                       + ["-f", "pbf", "-o", str(tmp), "--overwrite"])
            if not tmp.exists() or tmp.stat().st_size < minbytes:
                raise SystemExit(f"estrazione {name} troppo piccola ({tmp.stat().st_size} B)")
            tmp.replace(path)  # atomico: il vecchio PBF buono resta fino a fine estrazione
        except BaseException:
            tmp.unlink(missing_ok=True)
            raise
        size = path.stat().st_size / 1e6
        log(f"OK {path.name}: {size:.1f} MB")

    targets = {
        "roads": ctx.pbf("roads"),
        "areas": ctx.pbf("areas"),
        "points": ctx.pbf("points"),
    }

    missing = [t for t in targets.values() if force or not t.exists()
               or t.stat().st_size < minbytes]
    if not missing:
        log("estratti gia presenti (usa --force per rifare)")
    else:
        for name, path in targets.items():
            if path.exists() and not force and path.stat().st_size >= minbytes:
                continue
            filtro(name, path)

    if all(t.exists() and t.stat().st_size >= minbytes
           for t in targets.values()):
        total = sum(t.stat().st_size for t in targets.values())
        log(f"estratti completi: {total/1e9:.2f} GB totali")
        try:
            ctx.data.mkdir(parents=True, exist_ok=True)
            sig_path.write_text(sig_cur + "\n")
        except OSError as e:
            log(f"avviso: impossibile scrivere {sig_path.name}: {e}")
        if clean and src.exists():
            freed = src.stat().st_size
            src.unlink()
            log(f"PBF originale eliminato: liberati {freed/1e9:.2f} GB")
    else:
        raise SystemExit("estrazione incompleta: qualche filtrato mancante "
                         "o troppo piccolo; aggiornamento interrotto")


def cmd_graph(ctx: Ctx) -> None:
    roads = ctx.pbf("roads")
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
            srcp = ctx.pbf(name)
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
    geo_by_key = {}
    for gf in ctx.tiles.glob("IT_*_geo.json.gz"):
        geo_by_key[gf.name[: -len("_geo.json.gz")]] = gf
    files = sorted(ctx.tiles.glob("IT_*.json.gz"))
    tiles = []
    tot_bytes = 0
    for f in files:
        if f.name.endswith("_geo.json.gz"):
            continue
        key = f.name[: -len(".json.gz")]
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
        gf = geo_by_key.get(key)
        if gf:
            try:
                with gzip.open(gf, "rt", encoding="utf-8") as gz:
                    gd = json.load(gz)
            except Exception as e:
                log(f"avviso: {gf.name} illeggibile ({e})")
            else:
                entry["geo_bytes"] = gf.stat().st_size
                tot_bytes += gf.stat().st_size
                for field in ("roads", "buildings", "parks", "airports",
                              "trees", "signals", "pois"):
                    if field in gd:
                        entry[field] = len(gd[field])
                if gd.get("ver"):
                    entry["ver"] = gd["ver"]
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


def _bt_tile_keys(ctx: Ctx) -> Set[str]:
    """Chiavi tile con almeno una strada bridge/tunnel nei PBF roads del
    paese attivo. geo-todo le confronta col marcatore `-bt` in `ver`: cosi'
    il prossimo aggiornamento rigenera SOLO i tile interessati e non tutto
    il paese. Scansione leggera in una volta sola per contenuto dei PBF
    (cache data/<paese>-bt.json, invalidata su mtime+size del file)."""
    roads = ctx.pbf("roads")
    if not roads.exists():
        return set()
    st = roads.stat()
    sig = f"{st.st_mtime_ns}:{st.st_size}"
    cache = ctx.data / f"{ctx.country}-bt.json"
    if cache.exists():
        try:
            d = json.loads(cache.read_text(encoding="utf-8"))
            if d.get("sig") == sig:
                return set(d.get("keys") or [])
        except Exception:
            pass
    keys: Set[str] = set()

    class _BtScan(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.keys = keys

        def way(self, w):
            a = _way_attrs(w.tags)
            if a is None or not (a["tu"] or a["br"]):
                return
            pts = []
            for nd in w.nodes:
                loc = nd.location
                if not loc.valid():
                    return
                pts.append((round(loc.lat, 5), round(loc.lon, 5)))
            if len(pts) < 2:
                return
            for run in split_way_by_tile(pts):
                if len(run) < 2:
                    continue
                self.keys.add(tile_key(*pts[run[0]]))

    t0 = time.time()
    _BtScan().apply_file(str(roads), locations=True, idx="sparse_mem_array")
    # MAI su stdout: geo-todo lo usa per le chiavi tile (una per riga) e
    # qualsiasi riga di log qui sopravvive nel flusso -> tile_worker la
    # tratterebbe come una chiave fasulla e gen-tile esploderebbe.
    print(f"[{_dt.datetime.now():%H:%M:%S}] scan bridge/tunnel: "
          f"{len(keys)} tile ({time.time() - t0:.0f}s)", file=sys.stderr, flush=True)
    try:
        ctx.data.mkdir(parents=True, exist_ok=True)
        cache.write_text(json.dumps({"sig": sig, "keys": sorted(keys)}),
                         encoding="utf-8")
    except Exception:
        pass
    return keys


def cmd_geo_todo(ctx: Ctx, refresh: bool) -> None:
    """Legge chiavi tile da stdin e stampa su stdout quelle la cui GEO va
    generata o rigenerata: file geo mancante, formato `ver` obsoleto (base
    oppure base+`-bt` sui tile con viadotti/gallerie), oppure dati sorgente
    (dump/PBF filtrati) piu' recenti della geo stessa (il dump e' stato
    riscaricato o i PBF ri-estratti dopo la generazione).
    Con `--refresh` passa tutte le chiavi (rigenera ogni geo esistente).
    Il riepilogo va su stderr per non sporcare l'elenco su stdout."""
    cur = _geo_format_version()
    # Il marcatore -bt e' per-tile: solo i tile con bridge/tunnel devono
    # festeggiare il cambio di formato, gli altri restano per sempre. Lo scan
    # dei PBF e' cacheggiato; con --refresh tutto rigenera e non serve.
    bt = set() if refresh else _bt_tile_keys(ctx)
    minbytes = _min_pbf_bytes()
    srcs = []
    raw = ctx.pbf("latest.osm")
    if raw.exists() and raw.stat().st_size >= minbytes:
        srcs.append(raw)
    for name in ("roads", "areas", "points"):
        p = ctx.pbf(name)
        if p.exists() and p.stat().st_size >= minbytes:
            srcs.append(p)
    src_mtime = max((p.stat().st_mtime for p in srcs), default=0.0)
    keys = sorted({ln.strip() for ln in sys.stdin if ln.strip()})
    todo = []
    for k in keys:
        gp = ctx.tiles / f"{k}_geo.json.gz"
        if refresh or not gp.exists():
            todo.append(k)
            continue
        try:
            with gzip.open(gp, "rt", encoding="utf-8") as gz:
                ver = json.load(gz).get("ver")
        except Exception:
            ver = None
        expected = cur + ("-bt" if k in bt else "")
        if ver != expected or (src_mtime and gp.stat().st_mtime < src_mtime):
            todo.append(k)
    print(f"geo-todo: {len(todo)}/{len(keys)} chiavi "
          f"({'refresh' if refresh else 'formato ' + cur})", file=sys.stderr)
    for k in todo:
        print(k)


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
    f.add_argument("--min-pbf-mb", type=float, default=None,
                   help="dimensione minima filtrati in MB (default "
                        "HUNTIX_MIN_PBF_MB o 1 MB)")
    sub.add_parser("graph")
    gto = sub.add_parser("geo-todo",
                         help="chiavi geo da generare/rigenerare (stdin -> stdout; --refresh = tutte)")
    gto.add_argument("--refresh", action="store_true",
                     help="passa tutte le chiavi (rigenera le geo esistenti)")
    gt = sub.add_parser("gen-tile")
    gt.add_argument("tile_key")
    gt.add_argument("--skip-graph", action="store_true")
    gt.add_argument("--no-index", dest="no_index", action="store_true",
                    help="non ricostruire index.json (per batch)")
    land = sub.add_parser("land", help="tile di terra del paese attivo (HUNTIX_COUNTRY)")
    land.add_argument("--with-areas", action="store_true",
                      help="scan anche le aree (piu' lento)")
    sub.add_parser("index")
    inf = sub.add_parser("info")
    inf.add_argument("file")
    dl = sub.add_parser("download")
    dl.add_argument("--force", action="store_true")
    sub.add_parser("fmt-version",
                   help="stampa la versione corrente del formato geo (per gli aggiornamenti incrementali)")
    ap.add_argument("--data-dir", default=None)
    ap.add_argument("--tiles-dir", default=None)
    ap.add_argument("--work-dir", default=None)
    args = ap.parse_args()

    ctx = Ctx(args)
    if args.cmd == "filter":
        src = Path(args.input) if args.input else ctx.pbf("latest.osm")
        cmd_filter(ctx, src, args.clean, args.force, args.min_pbf_mb)
    elif args.cmd == "graph":
        cmd_graph(ctx)
        cmd_index(ctx)
    elif args.cmd == "gen-tile":
        cmd_gen_tile(ctx, args.tile_key, args.skip_graph)
        if not args.no_index:
            cmd_index(ctx)
    elif args.cmd == "index":
        cmd_index(ctx)
    elif args.cmd == "geo-todo":
        cmd_geo_todo(ctx, args.refresh)
    elif args.cmd == "land":
        cmd_land(ctx, args.with_areas)
    elif args.cmd == "info":
        cmd_info(Path(args.file))
    elif args.cmd == "download":
        dst = ctx.pbf("latest.osm")
        ctx.data.mkdir(parents=True, exist_ok=True)
        url = "https://download.geofabrik.de/europe/italy-latest.osm.pbf"
        try:
            subprocess.run(["wget", "-c", "-q", "--show-progress",
                            url, "-O", str(dst)], check=True)
        except subprocess.CalledProcessError as e:
            # il file parziale (eventualmente corrotto) non deve restare:
            # un futuro `wget -c` lo riprenderebbe come se fosse valido.
            try:
                dst.unlink(missing_ok=True)
            except OSError:
                pass
            log(f"ERRORE: scarico {url} fallito (wget uscita {e.returncode}).")
            log("Nessun dato e' stato toccato; l'aggiornamento e' idempotente, " 
                "basta rilanciarlo.")
            raise SystemExit(1)
    elif args.cmd == "fmt-version":
        print(_geo_format_version())


if __name__ == "__main__":
    main()

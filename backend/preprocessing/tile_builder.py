"""HUNTIX Fase 1 — griglia tile 10km x 10km e utility geografiche.

Griglia fissa ancorata a (34N, 5E):
  passo latitudine  = 0.090 deg  (~10.0 km)
  passo longitudine = 0.121 deg  (~10.0 km alla latitudine media 42N)
Chiave tile: IT_<ilat:03d>_<ilon:03d>
"""

from __future__ import annotations

import math
from typing import Dict, List, Sequence, Tuple

ORIGIN_LAT = 34.0
ORIGIN_LON = 5.0
LAT_STEP = 0.090
LON_STEP = 0.121

M_PER_LAT = 110540.0

# larghezze strada (mirror di backend/traffic/road_graph.py)
HIGHWAY_WIDTH: Dict[str, float] = {
    "motorway": 12, "trunk": 11, "primary": 10, "secondary": 8, "tertiary": 7,
    "residential": 6, "service": 4, "living_street": 5, "pedestrian": 3,
    "unclassified": 6,
}

ROAD_HIGHWAYS = {
    "motorway", "motorway_link", "trunk", "trunk_link",
    "primary", "primary_link", "secondary", "secondary_link",
    "tertiary", "tertiary_link", "unclassified", "residential",
    "living_street", "service", "pedestrian",
}

DEFAULT_SPEED_KMH = {
    "motorway": 130, "motorway_link": 60, "trunk": 110, "trunk_link": 60,
    "primary": 90, "primary_link": 50, "secondary": 70, "secondary_link": 50,
    "tertiary": 50, "tertiary_link": 40, "residential": 50,
    "living_street": 30, "service": 30, "pedestrian": 10, "unclassified": 50,
}

_MPH = 1.609344
_KNOTS = 1.852


# ── Griglia ──────────────────────────────────────────────────────

def tile_idx(lat: float, lon: float) -> Tuple[int, int]:
    return (int(math.floor((lat - ORIGIN_LAT) / LAT_STEP)),
            int(math.floor((lon - ORIGIN_LON) / LON_STEP)))


def tile_key_from_idx(ilat: int, ilon: int) -> str:
    return f"IT_{ilat:03d}_{ilon:03d}"


def tile_key(lat: float, lon: float) -> str:
    return tile_key_from_idx(*tile_idx(lat, lon))


def tile_bbox(key: str) -> Tuple[float, float, float, float]:
    """(latmin, lonmin, latmax, lonmax)"""
    _, ilat, ilon = key.split("_")
    ilat, ilon = int(ilat), int(ilon)
    return (ORIGIN_LAT + ilat * LAT_STEP,
            ORIGIN_LON + ilon * LON_STEP,
            ORIGIN_LAT + (ilat + 1) * LAT_STEP,
            ORIGIN_LON + (ilon + 1) * LON_STEP)


def split_way_by_tile(points: Sequence[Tuple[float, float]]) -> List[List[int]]:
    """Indici dei nodi raggruppati per tile contigua; il nodo di confine viene
    duplicato come chiusura della run precedente e apertura della successiva."""
    runs: List[List[int]] = []
    if len(points) < 2:
        return runs
    cur = [0]
    cur_key = tile_key(*points[0])
    for i in range(1, len(points)):
        k = tile_key(*points[i])
        if k != cur_key:
            runs.append(cur)
            cur = [i - 1]
            cur_key = k
        cur.append(i)
    runs.append(cur)
    return runs


# ── Geodesia ─────────────────────────────────────────────────────

def m_per_lon_at(lat: float) -> float:
    return 111320.0 * math.cos(math.radians(lat))


def haversine_m(a: Tuple[float, float], b: Tuple[float, float]) -> float:
    lat1, lon1 = math.radians(a[0]), math.radians(a[1])
    lat2, lon2 = math.radians(b[0]), math.radians(b[1])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(h))


def path_length_m(pts: Sequence[Tuple[float, float]]) -> float:
    return sum(haversine_m(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


def local_bearing_deg(a: Tuple[float, float], b: Tuple[float, float],
                      mpl: float) -> float:
    dx = (b[1] - a[1]) * mpl
    dz = (b[0] - a[0]) * M_PER_LAT
    return math.degrees(math.atan2(dz, dx)) % 180.0


def is_real_junction(bearings: List[float], tol: float = 20.0) -> bool:
    """Mirror di _is_real_junction in backend/traffic/road_graph.py"""
    if len(bearings) < 2:
        return False
    ref = bearings[0]
    for b in bearings:
        diff = abs(((b - ref) + 180) % 360 - 180)
        diff = min(diff, 180 - diff)
        if diff > tol:
            return True
    return False


# ── Tag parsing ──────────────────────────────────────────────────

def parse_speed_kmh(raw: str, highway: str) -> float:
    if not raw:
        return float(DEFAULT_SPEED_KMH.get(highway, 50))
    v = raw.strip().lower()
    try:
        num = float(v.split()[0].replace(",", "."))
    except (ValueError, IndexError):
        return float(DEFAULT_SPEED_KMH.get(highway, 50))
    if "mph" in v:
        return round(num * _MPH)
    if "knot" in v:
        return round(num * _KNOTS)
    return num


def road_width(highway: str) -> float:
    return HIGHWAY_WIDTH.get(highway, 5.0)


# ── Geometria locale (proiezione equirettangolare attorno al centroide) ──

def to_local_m(pts: Sequence[Tuple[float, float]]
               ) -> Tuple[List[float], List[float], float, float]:
    """-> (xs, zs, clat, clon) in metri rispetto al centroide."""
    clat = sum(p[0] for p in pts) / len(pts)
    clon = sum(p[1] for p in pts) / len(pts)
    mpl = m_per_lon_at(clat)
    xs = [(p[1] - clon) * mpl for p in pts]
    zs = [(p[0] - clat) * M_PER_LAT for p in pts]
    return xs, zs, clat, clon


def shoelace_area_m2(xs: Sequence[float], zs: Sequence[float]) -> float:
    s = 0.0
    n = len(xs)
    for i in range(n):
        j = (i + 1) % n
        s += xs[i] * zs[j] - xs[j] * zs[i]
    return abs(s) * 0.5


def oriented_bbox(pts: Sequence[Tuple[float, float]]
                  ) -> Tuple[Tuple[float, float], Tuple[float, float], int]:
    """(centro [lat,lon], dimensioni [w,l] lungo l'asse maggiore, rotazione deg).
    La rotazione e' quella da applicare a un prefab orientato lungo X locale
    per allinearlo al lato lungo dell'impronta."""
    xs, zs, clat, clon = to_local_m(pts)
    best_d2, theta = -1.0, 0.0
    for i in range(len(xs) - 1):
        dx = xs[i + 1] - xs[i]
        dz = zs[i + 1] - zs[i]
        d2 = dx * dx + dz * dz
        if d2 > best_d2:
            best_d2 = d2
            theta = math.atan2(dz, dx)
    c, s = math.cos(theta), math.sin(theta)
    xr = [x * c + z * s for x, z in zip(xs, zs)]
    zr = [-x * s + z * c for x, z in zip(xs, zs)]
    w = max(xr) - min(xr)
    d = max(zr) - min(zr)
    rot = int(round(-math.degrees(theta))) % 360
    return ([round(clat, 5), round(clon, 5)], [round(w, 1), round(d, 1)], rot)


def simplify_polyline(pts: Sequence[Tuple[float, float]], tol_m: float
                      ) -> List[Tuple[float, float]]:
    """Douglas-Peucker con tolleranza in metri (proiezione locale)."""
    if len(pts) < 3:
        return list(pts)
    xs, zs, clat, clon = to_local_m(pts)

    def seg_dist(i: int, a: int, b: int) -> float:
        x, z = xs[i], zs[i]
        ax, az = xs[a], zs[a]
        bx, bz = xs[b], zs[b]
        dx, dz = bx - ax, bz - az
        l2 = dx * dx + dz * dz
        if l2 == 0.0:
            return math.hypot(x - ax, z - az)
        t = max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / l2))
        return math.hypot(x - ax - t * dx, z - az - t * dz)

    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        imax, dmax = -1, -1.0
        for i in range(a + 1, b):
            dd = seg_dist(i, a, b)
            if dd > dmax:
                dmax, imax = dd, i
        if dmax > tol_m:
            keep[imax] = True
            stack.append((a, imax))
            stack.append((imax, b))
    return [pts[i] for i in range(len(pts)) if keep[i]]

"""Road graph — server-side mirror of Unity RoadGraph + RoadGraphBuilder + NodeSnap."""

from __future__ import annotations
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple


# ── Node & Arc ───────────────────────────────────────────────────

@dataclass
class RoadNode:
    id: int
    x: float
    z: float
    junction: str  # "DeadEnd" | "Simple" | "Real"
    name: str = ""
    arc_ids: List[int] = field(default_factory=list)


@dataclass
class RoadArc:
    id: int
    from_node: int
    to_node: int
    waypoints: List[Tuple[float, float]]
    road_name: str
    highway: str
    width: float
    length: float
    tunnel: bool = False
    bridge: bool = False
    maxspeed: float = 0.0


# ── Graph ────────────────────────────────────────────────────────

class RoadGraph:
    def __init__(self):
        self.nodes: List[RoadNode] = []
        self.arcs: List[RoadArc] = []
        self.node_map: Dict[int, RoadNode] = {}
        self.arc_map: Dict[int, RoadArc] = {}

    def add_node(self, node_id: int, x: float, z: float,
                 junction: str, name: str = "") -> RoadNode:
        if node_id in self.node_map:
            return self.node_map[node_id]
        n = RoadNode(id=node_id, x=x, z=z, junction=junction, name=name)
        self.nodes.append(n)
        self.node_map[node_id] = n
        return n

    def add_arc(self, from_id: int, to_id: int,
                waypoints: List[Tuple[float, float]],
                road_name: str, highway: str, width: float,
                tunnel: bool = False, bridge: bool = False,
                maxspeed: float = 0.0) -> RoadArc:
        arc_id = len(self.arcs)
        length = sum(
            math.hypot(waypoints[i+1][0] - waypoints[i][0],
                       waypoints[i+1][1] - waypoints[i][1])
            for i in range(len(waypoints) - 1)
        )
        a = RoadArc(id=arc_id, from_node=from_id, to_node=to_id,
                     waypoints=waypoints, road_name=road_name,
                     highway=highway, width=width, length=length,
                     tunnel=tunnel, bridge=bridge, maxspeed=maxspeed)
        self.arcs.append(a)
        self.arc_map[arc_id] = a
        self.node_map[from_id].arc_ids.append(arc_id)
        self.node_map[to_id].arc_ids.append(arc_id)
        return a

    def get_neighbors(self, node_id: int) -> List[int]:
        node = self.node_map.get(node_id)
        if not node:
            return []
        result = []
        for aid in node.arc_ids:
            arc = self.arc_map[aid]
            if arc.from_node == node_id:
                result.append(arc.to_node)
            elif arc.to_node == node_id:
                result.append(arc.from_node)
        return result

    def get_arc_between(self, from_id: int, to_id: int) -> Optional[int]:
        node = self.node_map.get(from_id)
        if not node:
            return None
        for aid in node.arc_ids:
            arc = self.arc_map[aid]
            if (arc.from_node == from_id and arc.to_node == to_id) or \
               (arc.from_node == to_id and arc.to_node == from_id):
                return aid
        return None

    def get_arc_to(self, to_id: int, from_id: int) -> Optional[int]:
        node = self.node_map.get(to_id)
        if not node:
            return None
        for aid in node.arc_ids:
            arc = self.arc_map[aid]
            if arc.to_node == to_id and arc.from_node == from_id:
                return aid
        return None

    def build_waypoints(self, arc_id: int, reverse: bool) -> List[Tuple[float, float]]:
        arc = self.arc_map.get(arc_id)
        if not arc:
            return []
        wp = arc.waypoints
        if not reverse:
            return wp
        return list(reversed(wp))

    def find_closest_node(self, x: float, z: float) -> int:
        best_id = -1
        best_d = float("inf")
        for n in self.nodes:
            d = (n.x - x) ** 2 + (n.z - z) ** 2
            if d < best_d:
                best_d = d
                best_id = n.id
        return best_id

    def to_dict(self) -> dict:
        return {
            "nodes": [{"id": n.id, "x": n.x, "z": n.z,
                        "junction": n.junction, "name": n.name}
                       for n in self.nodes],
            "arcs": [{"id": a.id, "from": a.from_node, "to": a.to_node,
                       "waypoints": a.waypoints, "road_name": a.road_name,
                       "highway": a.highway, "width": a.width, "length": a.length,
                       "tunnel": a.tunnel, "bridge": a.bridge,
                       "maxspeed": a.maxspeed}
                      for a in self.arcs],
        }

    @classmethod
    def from_dict(cls, data: dict) -> "RoadGraph":
        graph = cls()
        for nd in data.get("nodes", []):
            graph.add_node(nd["id"], nd["x"], nd["z"],
                           nd.get("junction", "Simple"), nd.get("name", ""))
        for ad in data.get("arcs", []):
            wp = [tuple(p) for p in ad.get("waypoints", [])]
            graph.add_arc(ad["from"], ad["to"], wp,
                          ad.get("road_name", ""), ad.get("highway", ""),
                          ad.get("width", 5.0),
                          tunnel=ad.get("tunnel", False),
                          bridge=ad.get("bridge", False),
                          maxspeed=ad.get("maxspeed", 0.0))
        return graph


# ── Snap helpers ─────────────────────────────────────────────────

SNAP_TOLERANCE = 3.0
SNAP_GRID = 5.0

_HIGHWAY_WIDTH = {
    "motorway": 12, "primary": 10, "secondary": 8, "tertiary": 7,
    "residential": 6, "service": 4, "footway": 2, "pedestrian": 3,
    "unclassified": 6,
}

_MINOR = {"footway", "path", "cycleway", "corridor", "proposed",
          "construction", "raceway", "steps"}


def _road_width(hw: str) -> float:
    return _HIGHWAY_WIDTH.get(hw, 5.0)


def _is_minor(hw: str) -> bool:
    return hw in _MINOR


def _snap_key(x: float, z: float) -> int:
    rx = round(x * 2)
    rz = round(z * 2)
    return rx * 100007 + rz


def _geo_key(lng: float, lat: float) -> int:
    return (round(lng * 1000000) << 32) | (round(lat * 1000000) & 0xFFFFFFFF)


def _local(pt: dict, c_lat: float, c_lng: float, m_per_lon: float) -> Tuple[float, float]:
    return ((pt["lng"] - c_lng) * m_per_lon, (pt["lat"] - c_lat) * 110540.0)


def _build_snap_map(roads: list, c_lat: float, c_lng: float,
                    m_per_lon: float) -> Dict[int, Tuple[float, float]]:
    result: Dict[int, Tuple[float, float]] = {}
    grid: Dict[Tuple[int, int], List[Tuple[float, float]]] = {}

    for road in roads:
        pts = road.get("points", [])
        for pt in pts:
            wp = _local(pt, c_lat, c_lng, m_per_lon)
            gk = _geo_key(pt["lng"], pt["lat"])
            if gk in result:
                continue
            gc = (int(math.floor(wp[0] / SNAP_GRID)),
                  int(math.floor(wp[1] / SNAP_GRID)))
            rep = wp
            found = False
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    ngc = (gc[0] + dx, gc[1] + dz)
                    for existing in grid.get(ngc, []):
                        if (wp[0]-existing[0])**2 + (wp[1]-existing[1])**2 < SNAP_TOLERANCE**2:
                            rep = existing
                            found = True
                            break
                    if found:
                        break
                if found:
                    break
            result[gk] = rep
            grid.setdefault(gc, [])
            if rep not in grid[gc]:
                grid[gc].append(rep)
    return result


def _snapped(pt: dict, snap: Dict[int, Tuple[float, float]],
             c_lat: float, c_lng: float, m_per_lon: float) -> Tuple[float, float]:
    gk = _geo_key(pt["lng"], pt["lat"])
    if gk in snap:
        return snap[gk]
    return _local(pt, c_lat, c_lng, m_per_lon)


def _is_real_junction(bearings: List[float], tol: float = 20.0) -> bool:
    if len(bearings) < 2:
        return False
    ref = bearings[0]
    for b in bearings:
        diff = abs(((b - ref) + 180) % 360 - 180)
        diff = min(diff, 180 - diff)
        if diff > tol:
            return True
    return False


# ── Builder ──────────────────────────────────────────────────────

def build_graph(envelope: dict) -> RoadGraph:
    roads = envelope.get("roads", [])
    if not roads:
        return RoadGraph()

    c_lat = envelope["centerLat"]
    c_lng = envelope["centerLng"]
    m_per_lon = 111320.0 * math.cos(c_lat * math.pi / 180)

    snap = _build_snap_map(roads, c_lat, c_lng, m_per_lon)

    j_count: Dict[int, int] = {}
    j_bearings: Dict[int, List[float]] = {}

    for road in roads:
        pts = road.get("points", [])
        if len(pts) < 2:
            continue
        hw = road.get("highway", "")
        if _is_minor(hw):
            continue

        for pt in pts:
            pos = _snapped(pt, snap, c_lat, c_lng, m_per_lon)
            k = _snap_key(*pos)
            j_count[k] = j_count.get(k, 0) + 1

        for i in range(len(pts) - 1):
            a = _snapped(pts[i], snap, c_lat, c_lng, m_per_lon)
            b = _snapped(pts[i+1], snap, c_lat, c_lng, m_per_lon)
            dx, dz = b[0]-a[0], b[1]-a[1]
            if dx*dx + dz*dz < 0.01:
                continue
            bearing = math.degrees(math.atan2(dz, dx)) % 180
            kA = _snap_key(*a)
            kB = _snap_key(*b)
            j_bearings.setdefault(kA, []).append(bearing)
            j_bearings.setdefault(kB, []).append(bearing)

    graph = RoadGraph()
    key_to_id: Dict[int, int] = {}
    next_id = 0
    processed: set = set()

    def get_or_create(snap_key: int, pos: Tuple[float, float], name: str = "") -> int:
        nonlocal next_id
        if snap_key in key_to_id:
            return key_to_id[snap_key]
        cnt = j_count.get(snap_key, 0)
        bears = j_bearings.get(snap_key, [])
        if cnt <= 1:
            jtype = "DeadEnd"
        elif _is_real_junction(bears):
            jtype = "Real"
        else:
            jtype = "Simple"
        nid = next_id
        next_id += 1
        graph.add_node(nid, pos[0], pos[1], jtype, name)
        key_to_id[snap_key] = nid
        return nid

    for road in roads:
        pts = road.get("points", [])
        if len(pts) < 2:
            continue
        hw = road.get("highway", "")
        if _is_minor(hw):
            continue
        width = _road_width(hw)
        name = road.get("name", "")

        jpts = []
        for i, pt in enumerate(pts):
            pos = _snapped(pt, snap, c_lat, c_lng, m_per_lon)
            k = _snap_key(*pos)
            if j_count.get(k, 0) >= 2:
                jpts.append(i)

        if len(jpts) < 2:
            jpts = [0, len(pts) - 1]

        for j in range(len(jpts) - 1):
            si, ei = jpts[j], jpts[j+1]
            if si == ei:
                continue
            sp = _snapped(pts[si], snap, c_lat, c_lng, m_per_lon)
            ep = _snapped(pts[ei], snap, c_lat, c_lng, m_per_lon)
            dx, dz = ep[0]-sp[0], ep[1]-sp[1]
            if dx*dx + dz*dz < 0.01:
                continue

            sk = _snap_key(*sp)
            ek = _snap_key(*ep)
            agk = (sk << 32) | (ek & 0xFFFFFFFF)
            rgk = (ek << 32) | (sk & 0xFFFFFFFF)
            if agk in processed or rgk in processed:
                continue

            sn_id = get_or_create(sk, sp, name)
            en_id = get_or_create(ek, ep, name)

            waypoints = []
            for w in range(si, ei + 1):
                waypoints.append(_snapped(pts[w], snap, c_lat, c_lng, m_per_lon))

            graph.add_arc(sn_id, en_id, waypoints, name, hw, width)
            processed.add(agk)

    return graph

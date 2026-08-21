"""Traffic simulation — server-side authoritative tick loop.

Features:
- Server-authoritative car positions
- Car-following with leader detection
- Spatial repulsion + hard collision
- Traffic lights at Real junctions (procedural placement)
- Speed zones (bumps, humps) on road segments
- Road-edge clamping, building avoidance
"""

from __future__ import annotations
import math
import random
import time
import asyncio
import logging
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, field

from .road_graph import RoadGraph, RoadNode, _snap_key
from .pathfinder import find_path

logger = logging.getLogger(__name__)

MIN_SPEED = 4.0
MAX_SPEED = 14.0
RIGHT_OFFSET = 0.15
MAX_OFFSET = 2.0
BUILDING_PUSH_DIST = 2.5
BUILDING_PUSH_FORCE = 4.0
JUNCTION_BLEND = 6.0
LEADER_SAFE_DIST_BASE = 4.0
LEADER_SAFE_DIST_FACTOR = 0.3
TURN_SPEED = 4.5
FOLLOW_DOT_THRESHOLD = 0.5
FOLLOW_RANGE = 20.0
REPULSION_RANGE = 3.0
REPULSION_FORCE = 12.0
HARD_COLLISION_RANGE = 1.8
HARD_COLLISION_FORCE = 30.0
TICK_RATE = 10

TRAFFIC_LIGHT_GREEN = 25.0
TRAFFIC_LIGHT_YELLOW = 4.0
TRAFFIC_LIGHT_RED = 29.0
TRAFFIC_LIGHT_STOP_DIST = 8.0

SPEED_BUMP_FACTOR = 0.35
SPEED_BUMP_ZONE = 12.0


@dataclass
class TrafficLight:
    id: int
    x: float
    z: float
    node_id: int
    state: str = "green"
    timer: float = 0.0
    cycle: float = 58.0

    def tick(self, dt: float):
        self.timer += dt
        t = self.timer % self.cycle
        if t < TRAFFIC_LIGHT_GREEN:
            self.state = "green"
        elif t < TRAFFIC_LIGHT_GREEN + TRAFFIC_LIGHT_YELLOW:
            self.state = "yellow"
        else:
            self.state = "red"

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "x": round(self.x, 2),
            "z": round(self.z, 2),
            "state": self.state,
        }


@dataclass
class SpeedZone:
    node_id: int
    x: float
    z: float
    zone_type: str  # "bump" | "hump"
    factor: float = SPEED_BUMP_FACTOR


@dataclass
class CarState:
    id: str
    x: float = 0.0
    z: float = 0.0
    ry: float = 0.0
    speed: float = 8.0
    max_speed: float = 10.0
    model: str = "sedan"
    color_seed: int = 0
    node_path: List[int] = field(default_factory=list)
    waypoint_path: List[Tuple[float, float]] = field(default_factory=list)
    wp_index: int = 0
    home_node: int = -1
    work_node: int = -1
    active: bool = True

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "x": round(self.x, 2),
            "z": round(self.z, 2),
            "ry": round(self.ry, 1),
            "speed": round(self.speed, 1),
            "model": self.model,
            "color": self.color_seed,
        }


class TrafficSimulation:
    def __init__(self, graph: RoadGraph, building_bounds: Optional[List[dict]] = None):
        self.graph = graph
        self.building_bounds = building_bounds or []
        self.cars: Dict[str, CarState] = {}
        self.traffic_lights: Dict[int, TrafficLight] = {}
        self.speed_zones: Dict[int, SpeedZone] = {}
        self._rng = random.Random(42)
        self._next_id = 0
        self._tick_count = 0
        self._running = False
        self._light_next_id = 0

        self._model_pool = [
            "sedan", "suv", "van", "taxi",
            "hatchback-sports", "delivery",
        ]

    def rebuild_infrastructure(self):
        self.traffic_lights.clear()
        self.speed_zones.clear()
        if not self.graph.nodes:
            return

        for node in self.graph.nodes:
            if node.junction == "Real" and len(node.arc_ids) >= 3:
                self._place_traffic_light(node)
            if node.junction == "Simple" and len(node.arc_ids) == 2:
                self._maybe_place_speed_zone(node)

        logger.info(f"[Traffic] Infrastructure: {len(self.traffic_lights)} lights, {len(self.speed_zones)} speed zones")

    def _place_traffic_light(self, node: RoadNode):
        tl = TrafficLight(
            id=self._light_next_id,
            x=node.x,
            z=node.z,
            node_id=node.id,
            timer=self._rng.random() * 58.0,
        )
        self._light_next_id += 1
        self.traffic_lights[node.id] = tl

    def _maybe_place_speed_zone(self, node: RoadNode):
        if self._rng.random() < 0.3:
            zone = SpeedZone(
                node_id=node.id,
                x=node.x,
                z=node.z,
                zone_type="bump" if self._rng.random() < 0.6 else "hump",
            )
            self.speed_zones[node.id] = zone

    @property
    def alive_nodes(self) -> List[RoadNode]:
        return [n for n in self.graph.nodes
                if n.junction != "DeadEnd" and n.arc_ids]

    def get_nearby_building_count(self, x: float, z: float, radius: float = 100.0) -> int:
        count = 0
        for b in self.building_bounds:
            bx, bz = b["cx"], b["cz"]
            if (bx - x)**2 + (bz - z)**2 < radius**2:
                count += 1
        return count

    def target_car_count(self, x: float = 0.0, z: float = 0.0) -> int:
        nb = self.get_nearby_building_count(x, z)
        by_density = max(1, math.ceil(nb / 3))
        return min(by_density, len(self.alive_nodes))

    def init_cars(self, player_x: float = 0.0, player_z: float = 0.0,
                  count: Optional[int] = None) -> List[CarState]:
        alive = self.alive_nodes
        if len(alive) < 2:
            return []

        if count is None:
            count = max(20, len(alive) // 5)

        existing = [c for c in self.cars.values() if c.active]
        needed = max(0, count - len(existing))
        spawned = []

        for _ in range(needed):
            car = self._spawn_spread()
            if car:
                spawned.append(car)

        self.rebuild_infrastructure()
        return spawned

    def _spawn_spread(self) -> Optional[CarState]:
        alive = self.alive_nodes
        if len(alive) < 2:
            return None

        sn = self._rng.choice(alive)
        en = self._pick_random_far(sn.id, sn.x, sn.z)

        car = CarState(
            id=f"car_{self._next_id}",
            model=self._rng.choice(self._model_pool),
            color_seed=self._rng.randint(0, 999),
            max_speed=MIN_SPEED + self._rng.random() * (MAX_SPEED - MIN_SPEED),
        )
        car.speed = car.max_speed * 0.5
        self._next_id += 1

        self._assign_route(car, sn.id, en.id)
        self.cars[car.id] = car
        return car

    def restore_cars(self, car_dicts: List[dict]) -> int:
        restored = 0
        for cd in car_dicts:
            cid = cd["id"]
            if cid in self.cars:
                continue
            car = CarState(
                id=cid,
                x=cd.get("x", 0),
                z=cd.get("z", 0),
                ry=cd.get("ry", 0),
                speed=cd.get("speed", 8),
                max_speed=cd.get("max_speed", 10),
                model=cd.get("model", "sedan"),
                color_seed=cd.get("color_seed", 0),
            )

            wp = cd.get("waypoint_path")
            ni = cd.get("node_path")
            if wp and ni and len(ni) >= 2:
                car.node_path = ni
                car.waypoint_path = [(p[0], p[1]) for p in wp]
                car.wp_index = cd.get("wp_index", 0)
            else:
                sn = self._find_closest_alive(car.x, car.z)
                en = self._pick_random_far(sn, car.x, car.z)
                if sn is not None and en is not None:
                    self._assign_route(car, sn, en)

            self.cars[cid] = car
            restored += 1

        if restored > 0:
            self.rebuild_infrastructure()

        return restored

    def tick(self) -> List[dict]:
        self._tick_count += 1
        dt = 1.0 / TICK_RATE
        updates = []

        for tl in self.traffic_lights.values():
            tl.tick(dt)

        active_cars = [c for c in self.cars.values() if c.active]

        for car in active_cars:
            if not car.waypoint_path or len(car.waypoint_path) < 2:
                self._reassign(car)
                continue

            if car.wp_index >= len(car.waypoint_path):
                self._reassign(car)
                continue

            target = car.waypoint_path[car.wp_index]
            dx = target[0] - car.x
            dz = target[1] - car.z
            dist = (dx*dx + dz*dz) ** 0.5

            if dist < 1.0:
                car.wp_index += 1
                if car.wp_index >= len(car.waypoint_path):
                    self._reassign(car)
                    continue
                target = car.waypoint_path[car.wp_index]
                dx = target[0] - car.x
                dz = target[1] - car.z
                dist = (dx*dx + dz*dz) ** 0.5

            if dist < 0.01:
                continue

            move_x = dx / dist
            move_z = dz / dist

            rep_x, rep_z = self._repulsion(car, active_cars)
            move_x += rep_x
            move_z += rep_z
            mag = (move_x**2 + move_z**2) ** 0.5
            if mag > 0.001:
                move_x /= mag
                move_z /= mag

            new_x = car.x + move_x * car.speed * dt
            new_z = car.z + move_z * car.speed * dt

            car.ry = math.degrees(math.atan2(move_x, move_z))
            car.x = new_x
            car.z = new_z

            self._adjust_speed(car, active_cars)
            self._apply_traffic_lights(car)
            self._apply_speed_zones(car)

            updates.append(car.to_dict())

        return updates

    def _repulsion(self, car: CarState, all_cars: List[CarState]) -> Tuple[float, float]:
        rx, rz = 0.0, 0.0
        for other in all_cars:
            if other.id == car.id:
                continue
            dx = car.x - other.x
            dz = car.z - other.z
            d = (dx*dx + dz*dz) ** 0.5
            if d < HARD_COLLISION_RANGE and d > 0.01:
                f = (1.0 - d / HARD_COLLISION_RANGE) * HARD_COLLISION_FORCE
                rx += (dx / d) * f
                rz += (dz / d) * f
            elif d < REPULSION_RANGE and d > 0.01:
                f = (1.0 - d / REPULSION_RANGE) * REPULSION_FORCE
                rx += (dx / d) * f
                rz += (dz / d) * f
        return rx, rz

    def _adjust_speed(self, car: CarState, all_cars: List[CarState]):
        best_dot = -1.0
        best_leader = None
        best_dist = float("inf")

        fdx = math.sin(math.radians(car.ry))
        fdz = math.cos(math.radians(car.ry))

        for other in all_cars:
            if other.id == car.id or not other.active:
                continue
            dx = other.x - car.x
            dz = other.z - car.z
            d = (dx*dx + dz*dz) ** 0.5
            if d > FOLLOW_RANGE:
                continue
            ndx = dx / d if d > 0.01 else 0
            ndz = dz / d if d > 0.01 else 0
            dot = fdx * ndx + fdz * ndz
            if dot > FOLLOW_DOT_THRESHOLD and d < best_dist:
                best_dist = d
                best_leader = other
                best_dot = dot

        if best_leader is None:
            car.speed = min(car.speed + 3.0 * 0.1, car.max_speed)
            return

        safe_follow = LEADER_SAFE_DIST_BASE + car.speed * LEADER_SAFE_DIST_FACTOR
        if best_dist < safe_follow:
            brake_target = min(best_leader.speed, car.max_speed * 0.5)
            car.speed = max(car.speed - 8.0 * 2.0 * 0.1, brake_target)
        elif best_dist < safe_follow * 2:
            car.speed = min(car.speed + 3.0 * 0.5 * 0.1, best_leader.speed)
        else:
            car.speed = min(car.speed + 3.0 * 0.1, car.max_speed)

    def _apply_traffic_lights(self, car: CarState):
        if not self.traffic_lights:
            return
        for tl in self.traffic_lights.values():
            if tl.state == "green":
                continue
            dx = tl.x - car.x
            dz = tl.z - car.z
            d = (dx*dx + dz*dz) ** 0.5
            if d > TRAFFIC_LIGHT_STOP_DIST:
                continue
            ndx = dx / d if d > 0.01 else 0
            ndz = dz / d if d > 0.01 else 0
            fdx = math.sin(math.radians(car.ry))
            fdz = math.cos(math.radians(car.ry))
            dot = fdx * ndx + fdz * ndz
            if dot > 0.3:
                slow_factor = max(0.0, d / TRAFFIC_LIGHT_STOP_DIST)
                target_speed = car.max_speed * slow_factor * 0.1
                if tl.state == "red":
                    target_speed = 0.0
                elif tl.state == "yellow":
                    target_speed = car.max_speed * 0.2
                car.speed = max(car.speed - 10.0 * 0.1, target_speed)

    def _apply_speed_zones(self, car: CarState):
        if not self.speed_zones:
            return
        for sz in self.speed_zones.values():
            dx = sz.x - car.x
            dz = sz.z - car.z
            d = (dx*dx + dz*dz) ** 0.5
            if d < SPEED_BUMP_ZONE:
                zone_slow = car.max_speed * sz.factor
                blend = max(0.0, d / SPEED_BUMP_ZONE)
                target = zone_slow + (car.max_speed - zone_slow) * blend
                if car.speed > target:
                    car.speed = max(car.speed - 6.0 * 0.1, target)

    def _reassign(self, car: CarState):
        alive = self.alive_nodes
        if len(alive) < 2:
            return
        sn = self._find_closest_alive(car.x, car.z)
        en = self._pick_random_far(sn, car.x, car.z)
        if sn is not None and en is not None:
            self._assign_route(car, sn, en)

    def _spawn_random(self) -> Optional[CarState]:
        alive = self.alive_nodes
        if len(alive) < 2:
            return None

        sn = self._rng.choice(alive)
        en = self._pick_random_far(sn.id, sn.x, sn.z)

        car = CarState(
            id=f"car_{self._next_id}",
            model=self._rng.choice(self._model_pool),
            color_seed=self._rng.randint(0, 999),
            max_speed=MIN_SPEED + self._rng.random() * (MAX_SPEED - MIN_SPEED),
        )
        car.speed = car.max_speed * 0.5
        self._next_id += 1

        self._assign_route(car, sn.id, en.id)
        self.cars[car.id] = car
        return car

    def _assign_route(self, car: CarState, start_id: int, end_id: int):
        path = find_path(self.graph, start_id, end_id)
        if not path or len(path) < 2:
            return

        wp = self._build_waypoints(path)
        if len(wp) < 2:
            return

        car.node_path = path
        car.waypoint_path = wp
        car.wp_index = 0
        car.x = wp[0][0]
        car.z = wp[0][1]
        car.home_node = start_id
        car.work_node = end_id

    def _build_waypoints(self, node_path: List[int]) -> List[Tuple[float, float]]:
        result: List[Tuple[float, float]] = []

        for i in range(len(node_path) - 1):
            from_id = node_path[i]
            to_id = node_path[i + 1]
            arc_id = self.graph.get_arc_between(from_id, to_id)
            if arc_id is None:
                continue

            arc = self.graph.arc_map[arc_id]
            reverse = arc.from_node != from_id
            raw_wp = self.graph.build_waypoints(arc_id, reverse)

            offset = min(arc.width * RIGHT_OFFSET, MAX_OFFSET)
            offset_wp = self._lane_offset(raw_wp, offset, reverse)
            offset_wp = self._clamp_to_road_edges(offset_wp, arc.width)

            start_idx = 0 if i == 0 else 1
            end_idx = len(offset_wp) if i == len(node_path) - 2 else len(offset_wp) - 1

            for w in range(start_idx, end_idx):
                result.append(offset_wp[w])

        if len(result) >= 2:
            result = self._smooth_junctions(node_path, result)

        if len(result) >= 2 and self.building_bounds:
            result = self._avoid_buildings(result)

        return result

    @staticmethod
    def _lane_offset(waypoints: List[Tuple[float, float]],
                     offset: float, reverse: bool) -> List[Tuple[float, float]]:
        if len(waypoints) < 2:
            return waypoints

        result = []
        for i, wp in enumerate(waypoints):
            if i == 0:
                nx, nz = waypoints[1][0] - wp[0], waypoints[1][1] - wp[1]
            elif i == len(waypoints) - 1:
                px, pz = waypoints[i-1]
                nx, nz = wp[0] - px, wp[1] - pz
            else:
                nx = waypoints[i+1][0] - waypoints[i-1][0]
                nz = waypoints[i+1][1] - waypoints[i-1][1]

            mag = (nx*nx + nz*nz) ** 0.5
            if mag < 0.001:
                result.append(wp)
                continue
            nx /= mag
            nz /= mag

            rx, rz = -nz, nx
            sign = 1.0 if not reverse else -1.0
            result.append((wp[0] + rx * offset * sign, wp[1] + rz * offset * sign))

        return result

    def _smooth_junctions(self, node_path: List[int],
                          raw_wp: List[Tuple[float, float]]) -> List[Tuple[float, float]]:
        result: List[Tuple[float, float]] = []
        wp_idx = 0

        for n_idx, nid in enumerate(node_path):
            node = self.graph.node_map.get(nid)
            if not node:
                continue

            is_junction = len(node.arc_ids) >= 3

            if n_idx == 0 or not is_junction:
                if wp_idx < len(raw_wp):
                    result.append(raw_wp[wp_idx])
                    wp_idx += 1
                continue

            if wp_idx > 0 and wp_idx < len(raw_wp):
                entry = raw_wp[wp_idx - 1]
                exit_wp = raw_wp[wp_idx]
                junction_pt = ((entry[0]+exit_wp[0])/2, (entry[1]+exit_wp[1])/2)
                result.append(junction_pt)
                wp_idx += 1
            elif wp_idx < len(raw_wp):
                result.append(raw_wp[wp_idx])
                wp_idx += 1

        while wp_idx < len(raw_wp):
            result.append(raw_wp[wp_idx])
            wp_idx += 1

        return result

    @staticmethod
    def _clamp_to_road_edges(waypoints: List[Tuple[float, float]],
                             road_width: float) -> List[Tuple[float, float]]:
        if len(waypoints) < 2:
            return waypoints
        half_w = road_width * 0.5
        result = []
        for i, wp in enumerate(waypoints):
            if i == 0:
                dx = waypoints[1][0] - wp[0]
                dz = waypoints[1][1] - wp[1]
            elif i == len(waypoints) - 1:
                dx = wp[0] - waypoints[i-1][0]
                dz = wp[1] - waypoints[i-1][1]
            else:
                dx = waypoints[i+1][0] - waypoints[i-1][0]
                dz = waypoints[i+1][1] - waypoints[i-1][1]
            mag = (dx*dx + dz*dz) ** 0.5
            if mag < 0.001:
                result.append(wp)
                continue
            rx, rz = -dz/mag, dx/mag
            lat = (wp[0]-waypoints[0][0])*rx + (wp[1]-waypoints[0][1])*rz
            if abs(lat) > half_w:
                sign = 1 if lat > 0 else -1
                push = sign * (abs(lat) - half_w)
                result.append((wp[0] - rx*push, wp[1] - rz*push))
            else:
                result.append(wp)
        return result

    def _avoid_buildings(self, waypoints: List[Tuple[float, float]]) -> List[Tuple[float, float]]:
        result = []
        for wx, wz in waypoints:
            px, pz = wx, wz
            for b in self.building_bounds:
                bx, bz, bw, bd = b["cx"], b["cz"], b["hw"], b["hd"]
                closest_x = max(bx - bw, min(px, bx + bw))
                closest_z = max(bz - bd, min(pz, bz + bd))
                dx, dz = px - closest_x, pz - closest_z
                dist_sq = dx*dx + dz*dz
                if dist_sq < 0.01:
                    away_x = px - bx
                    away_z = pz - bz
                    mag = (away_x**2 + away_z**2) ** 0.5
                    if mag < 0.01:
                        away_x, away_z = 1.0, 0.0
                    else:
                        away_x /= mag
                        away_z /= mag
                    px += away_x * BUILDING_PUSH_FORCE
                    pz += away_z * BUILDING_PUSH_FORCE
                elif dist_sq < BUILDING_PUSH_DIST**2:
                    dist = dist_sq ** 0.5
                    push_dir_x = dx / dist
                    push_dir_z = dz / dist
                    push_amt = (BUILDING_PUSH_DIST - dist) * 0.5
                    px += push_dir_x * push_amt
                    pz += push_dir_z * push_amt
            result.append((px, pz))
        return result

    def _find_closest_alive(self, x: float, z: float) -> Optional[int]:
        alive = self.alive_nodes
        if not alive:
            return None
        best = None
        best_d = float("inf")
        for n in alive:
            d = (n.x - x)**2 + (n.z - z)**2
            if d < best_d:
                best_d = d
                best = n.id
        return best

    def _pick_random_far(self, exclude_id: int, px: float = 0, pz: float = 0) -> Optional[int]:
        alive = self.alive_nodes
        candidates = [n for n in alive
                      if n.id != exclude_id
                      and ((n.x-px)**2 + (n.z-pz)**2)**0.5 > 30]
        if not candidates:
            candidates = [n for n in alive if n.id != exclude_id]
        if not candidates:
            return None
        return self._rng.choice(candidates).id

    def ensure_density(self, player_x: float, player_z: float) -> List[dict]:
        alive = self.alive_nodes
        if len(alive) < 2:
            return []

        target = max(20, len(alive) // 5)
        spawned = []
        safety = 0
        while len(self.cars) < target and safety < 50:
            safety += 1
            car = self._spawn_spread()
            if car:
                spawned.append(car.to_dict())
            else:
                break

        return spawned

    def get_all_car_dicts(self) -> List[dict]:
        return [c.to_dict() for c in self.cars.values() if c.active]

    def get_traffic_light_dicts(self) -> List[dict]:
        return [tl.to_dict() for tl in self.traffic_lights.values()]

    def get_speed_zone_dicts(self) -> List[dict]:
        return [{"id": sz.node_id, "x": round(sz.x, 2), "z": round(sz.z, 2),
                 "type": sz.zone_type} for sz in self.speed_zones.values()]

    def get_full_state(self) -> dict:
        return {
            "cars": self.get_all_car_dicts(),
            "traffic_lights": self.get_traffic_light_dicts(),
            "speed_zones": self.get_speed_zone_dicts(),
            "graph": self.graph.to_dict(),
        }

    def export_persistence(self) -> List[dict]:
        result = []
        for car in self.cars.values():
            if not car.active:
                continue
            result.append({
                "id": car.id,
                "x": car.x,
                "z": car.z,
                "ry": car.ry,
                "speed": car.speed,
                "max_speed": car.max_speed,
                "model": car.model,
                "color_seed": car.color_seed,
                "node_path": car.node_path,
                "waypoint_path": car.waypoint_path,
                "wp_index": car.wp_index,
                "home_node": car.home_node,
                "work_node": car.work_node,
            })
        return result

"""REST API endpoints for traffic system — HTTP fallback for Unity polling."""

from __future__ import annotations
import logging
from fastapi import APIRouter, Request
from pydantic import BaseModel
from typing import Optional, List

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/traffic", tags=["traffic"])


class JoinRequest(BaseModel):
    game_id: str = "default"
    player_x: float = 0.0
    player_z: float = 0.0
    building_bounds: Optional[List[dict]] = None
    road_graph: Optional[dict] = None


class PlayerPosRequest(BaseModel):
    game_id: str = "default"
    x: float = 0.0
    z: float = 0.0


class AreaRequest(BaseModel):
    game_id: str = "default"
    player_x: float = 0.0
    player_z: float = 0.0
    building_bounds: Optional[List[dict]] = None
    road_graph: Optional[dict] = None


def _get_traffic(request: Request):
    sim = getattr(request.app, "_traffic_sim", None)
    db = getattr(request.app, "_traffic_db", None)
    fs = getattr(request.app, "_traffic_firestore", None)
    return sim, db, fs


async def _load_cars(game_id: str, db, fs) -> list:
    existing = await db.load_cars(game_id) if db and db.enabled else []
    if not existing and fs and fs.enabled:
        data = await fs.load(game_id)
        if data:
            existing = data.get("cars", [])
            if existing:
                logger.info(f"[TrafficAPI] Fallback: loaded {len(existing)} cars from Firestore")
    return existing


async def _load_graph(game_id: str, db, fs) -> Optional[dict]:
    saved_graph = None
    if db and db.enabled:
        saved_graph = await db.load_graph(game_id)
    if not saved_graph and fs and fs.enabled:
        data = await fs.load(game_id)
        if data:
            saved_graph = data.get("graph")
            if saved_graph:
                logger.info(f"[TrafficAPI] Fallback: loaded graph from Firestore")
    return saved_graph


@router.post("/join")
async def traffic_join(body: JoinRequest, request: Request):
    sim, db, fs = _get_traffic(request)
    if sim is None:
        return {"error": "Traffic system not initialized"}

    if body.building_bounds:
        sim.building_bounds = body.building_bounds

    graph_loaded = len(sim.graph.nodes) > 0

    if not graph_loaded and body.road_graph:
        from .road_graph import RoadGraph
        sim.graph = RoadGraph.from_dict(body.road_graph)
        graph_loaded = len(sim.graph.nodes) > 0
        if graph_loaded:
            logger.info(f"[TrafficAPI] Loaded graph from client: {len(sim.graph.nodes)} nodes, {len(sim.graph.arcs)} arcs")
            if db and db.enabled:
                await db.save_graph(body.game_id, {}, body.road_graph)
            if fs and fs.enabled:
                await fs.save(body.game_id, [], body.road_graph)

    if not graph_loaded:
        saved_graph = await _load_graph(body.game_id, db, fs)
        if saved_graph:
            from .road_graph import RoadGraph
            sim.graph = RoadGraph.from_dict(saved_graph)
            graph_loaded = len(sim.graph.nodes) > 0
            if graph_loaded:
                logger.info(f"[TrafficAPI] Restored graph: {len(sim.graph.nodes)} nodes")

    existing = await _load_cars(body.game_id, db, fs)
    if existing:
        restored = sim.restore_cars(existing)
        logger.info(f"[TrafficAPI] Restored {restored} cars for {body.game_id}")
    elif graph_loaded:
        sim.init_cars(body.player_x, body.player_z)
        logger.info(f"[TrafficAPI] Pre-spawned {len(sim.cars)} cars for {body.game_id}")

    return {
        "game_id": body.game_id,
        "car_count": len(sim.cars),
        "cars": sim.get_all_car_dicts(),
    }


@router.get("/state")
async def traffic_state(game_id: str = "default", request: Request = None):
    sim, _, _ = _get_traffic(request)
    if sim is None:
        return {"cars": [], "traffic_lights": [], "speed_zones": []}

    sim.tick()
    return {
        "cars": sim.get_all_car_dicts(),
        "traffic_lights": sim.get_traffic_light_dicts(),
        "speed_zones": sim.get_speed_zone_dicts(),
    }


@router.post("/player-pos")
async def traffic_player_pos(body: PlayerPosRequest, request: Request):
    sim, _, _ = _get_traffic(request)
    if sim is None:
        return {"ok": False}

    return {"ok": True}


@router.post("/area")
async def traffic_area(body: AreaRequest, request: Request):
    sim, db, fs = _get_traffic(request)
    if sim is None:
        return {"error": "Traffic system not initialized"}

    if body.building_bounds:
        sim.building_bounds = body.building_bounds

    graph_loaded = len(sim.graph.nodes) > 0

    if not graph_loaded and body.road_graph:
        from .road_graph import RoadGraph
        sim.graph = RoadGraph.from_dict(body.road_graph)
        graph_loaded = len(sim.graph.nodes) > 0

    if not graph_loaded:
        saved_graph = await _load_graph(body.game_id, db, fs)
        if saved_graph:
            from .road_graph import RoadGraph
            sim.graph = RoadGraph.from_dict(saved_graph)
            graph_loaded = len(sim.graph.nodes) > 0

    existing = await _load_cars(body.game_id, db, fs)
    if existing:
        sim.restore_cars(existing)
    elif graph_loaded:
        sim.init_cars(body.player_x, body.player_z)

    return {
        "game_id": body.game_id,
        "car_count": len(sim.cars),
        "cars": sim.get_all_car_dicts(),
    }

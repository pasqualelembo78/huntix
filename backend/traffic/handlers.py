"""Socket.IO traffic handlers — integrates with existing app_socket.py."""

from __future__ import annotations
import asyncio
import json
import logging
import time
from typing import Dict, Optional, Callable

logger = logging.getLogger(__name__)


def register_traffic_handlers(sio, sim, db):
    """Register Socket.IO event handlers for traffic system."""

    _sessions: Dict[str, dict] = {}

    @sio.event
    async def connect(sid, environ):
        logger.info(f"[Traffic] Client connected: {sid}")

    @sio.event
    async def disconnect(sid):
        session = _sessions.pop(sid, None)
        if session:
            gid = session.get("game_id", "?")
            logger.info(f"[Traffic] Client disconnected: {sid} (game {gid})")

    @sio.event
    async def traffic_join(sid, data):
        game_id = data.get("game_id", "default")
        envelope = data.get("envelope")
        player_x = data.get("player_x", 0)
        player_z = data.get("player_z", 0)

        if not envelope:
            await sio.emit("traffic_error",
                           {"message": "No envelope"}, to=sid)
            return

        _sessions[sid] = {
            "game_id": game_id,
            "player_x": player_x,
            "player_z": player_z,
        }
        await sio.enter_room(sid, f"traffic_{game_id}")

        existing = await db.load_cars(game_id)
        if existing:
            restored = sim.restore_cars(existing)
            logger.info(f"[Traffic] Restored {restored} cars for {game_id}")
        else:
            sim.init_cars(player_x, player_z)

        await sio.emit("traffic_join_ack", {
            "game_id": game_id,
            "car_count": len(sim.cars),
            "cars": sim.get_all_car_dicts(),
        }, to=sid)
        logger.info(f"[Traffic] {sid} → game {game_id}: {len(sim.cars)} cars")

    @sio.event
    async def traffic_player_pos(sid, data):
        session = _sessions.get(sid)
        if not session:
            return
        session["player_x"] = data.get("x", 0)
        session["player_z"] = data.get("z", 0)

    @sio.event
    async def traffic_request_area(sid, data):
        game_id = data.get("game_id", "default")
        envelope = data.get("envelope")
        player_x = data.get("player_x", 0)
        player_z = data.get("player_z", 0)

        if not envelope:
            return

        old = _sessions.get(sid)
        if old:
            await sio.leave_room(sid, f"traffic_{old['game_id']}")

        _sessions[sid] = {
            "game_id": game_id,
            "player_x": player_x,
            "player_z": player_z,
        }
        await sio.enter_room(sid, f"traffic_{game_id}")

        existing = await db.load_cars(game_id)
        if existing:
            sim.restore_cars(existing)
        else:
            sim.init_cars(player_x, player_z)

        await sio.emit("traffic_join_ack", {
            "game_id": game_id,
            "car_count": len(sim.cars),
            "cars": sim.get_all_car_dicts(),
        }, to=sid)

    return _sessions


async def tick_loop(sim, db, sio, sessions_ref, firestore_backup=None, interval=0.1, persist_interval=30.0):
    """Background: tick simulation, broadcast, persist."""
    last_persist = time.time()

    while True:
        try:
            t0 = time.time()

            updates = sim.tick()

            game_groups: Dict[str, list] = {}
            for sid, sess in sessions_ref.items():
                gid = sess.get("game_id", "default")
                game_groups.setdefault(gid, []).append(sid)

            for gid, sids in game_groups.items():
                for sid in sids:
                    try:
                        await sio.emit("traffic_update",
                                       {
                                           "cars": updates,
                                           "traffic_lights": sim.get_traffic_light_dicts(),
                                       }, to=sid)
                    except Exception:
                        pass

            if sim.graph.nodes:
                sim.ensure_density(0, 0)

            await asyncio.sleep(max(0, interval - (time.time() - t0)))

            now = time.time()
            if now - last_persist >= persist_interval:
                last_persist = now
                data = sim.export_persistence()
                gids = set(s.get("game_id", "default")
                           for s in sessions_ref.values())
                if not gids:
                    gids = {"huntix_main"}
                for gid in gids:
                    if data:
                        if db.enabled:
                            await db.save_cars(gid, data)
                        if firestore_backup and firestore_backup.enabled:
                            await firestore_backup.save_if_due(gid, data, sim.graph.to_dict())

        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"[Traffic tick] {e}")
            await asyncio.sleep(1)

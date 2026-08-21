"""PostgreSQL persistence for traffic simulation state."""

from __future__ import annotations
import json
import logging
import os
from typing import List, Optional

logger = logging.getLogger(__name__)

_DDL = """
CREATE TABLE IF NOT EXISTS traffic_cars (
    id          TEXT PRIMARY KEY,
    game_id     TEXT NOT NULL,
    x           REAL NOT NULL DEFAULT 0,
    z           REAL NOT NULL DEFAULT 0,
    ry          REAL NOT NULL DEFAULT 0,
    speed       REAL NOT NULL DEFAULT 8,
    max_speed   REAL NOT NULL DEFAULT 10,
    model       TEXT NOT NULL DEFAULT 'sedan',
    color_seed  INTEGER NOT NULL DEFAULT 0,
    node_path   INTEGER[] NOT NULL DEFAULT '{}',
    waypoint_path JSONB NOT NULL DEFAULT '[]',
    wp_index    INTEGER NOT NULL DEFAULT 0,
    home_node   INTEGER NOT NULL DEFAULT -1,
    work_node   INTEGER NOT NULL DEFAULT -1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_traffic_cars_game ON traffic_cars(game_id);

CREATE TABLE IF NOT EXISTS traffic_graphs (
    game_id     TEXT PRIMARY KEY,
    envelope    JSONB NOT NULL,
    graph       JSONB NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
"""


class TrafficDB:
    def __init__(self, dsn: Optional[str] = None):
        self._dsn = dsn or os.environ.get("DATABASE_URL")
        self._pool = None

    async def init(self):
        if not self._dsn:
            logger.warning("[TrafficDB] No DATABASE_URL, persistence disabled")
            return

        try:
            import asyncpg
            self._pool = await asyncpg.create_pool(self._dsn, min_size=1, max_size=3)
            async with self._pool.acquire() as conn:
                await conn.execute(_DDL)
            logger.info("[TrafficDB] Initialized (PostgreSQL)")
        except ImportError:
            logger.warning("[TrafficDB] asyncpg not installed, persistence disabled")
            self._pool = None
        except Exception as e:
            logger.error(f"[TrafficDB] Init failed: {e}")
            self._pool = None

    @property
    def enabled(self) -> bool:
        return self._pool is not None

    async def save_cars(self, game_id: str, cars: List[dict]):
        if not self.enabled or not cars:
            return

        try:
            async with self._pool.acquire() as conn:
                await conn.execute("DELETE FROM traffic_cars WHERE game_id = $1", game_id)

                for c in cars:
                    wp_path = json.dumps(c.get("waypoint_path", []))
                    await conn.execute("""
                        INSERT INTO traffic_cars
                            (id, game_id, x, z, ry, speed, max_speed, model,
                             color_seed, node_path, waypoint_path, wp_index,
                             home_node, work_node)
                        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
                    """,
                        c["id"], game_id,
                        c.get("x", 0), c.get("z", 0), c.get("ry", 0),
                        c.get("speed", 8), c.get("max_speed", 10),
                        c.get("model", "sedan"), c.get("color_seed", 0),
                        c.get("node_path", []),
                        wp_path,
                        c.get("wp_index", 0),
                        c.get("home_node", -1),
                        c.get("work_node", -1),
                    )

                logger.info(f"[TrafficDB] Saved {len(cars)} cars for game {game_id}")
        except Exception as e:
            logger.error(f"[TrafficDB] Save failed: {e}")

    async def load_cars(self, game_id: str) -> List[dict]:
        if not self.enabled:
            return []

        try:
            async with self._pool.acquire() as conn:
                rows = await conn.fetch(
                    "SELECT * FROM traffic_cars WHERE game_id = $1", game_id)

                if not rows:
                    return []

                result = []
                for r in rows:
                    wp_raw = r["waypoint_path"]
                    if isinstance(wp_raw, str):
                        wp_raw = json.loads(wp_raw)

                    result.append({
                        "id": r["id"],
                        "x": r["x"],
                        "z": r["z"],
                        "ry": r["ry"],
                        "speed": r["speed"],
                        "max_speed": r["max_speed"],
                        "model": r["model"],
                        "color_seed": r["color_seed"],
                        "node_path": list(r["node_path"]) if r["node_path"] else [],
                        "waypoint_path": wp_raw if wp_raw else [],
                        "wp_index": r["wp_index"],
                        "home_node": r["home_node"],
                        "work_node": r["work_node"],
                    })

                logger.info(f"[TrafficDB] Loaded {len(result)} cars for game {game_id}")
                return result
        except Exception as e:
            logger.error(f"[TrafficDB] Load failed: {e}")
            return []

    async def save_graph(self, game_id: str, envelope: dict, graph: dict):
        if not self.enabled:
            return
        try:
            async with self._pool.acquire() as conn:
                await conn.execute("""
                    INSERT INTO traffic_graphs (game_id, envelope, graph)
                    VALUES ($1, $2, $3)
                    ON CONFLICT (game_id) DO UPDATE SET
                        envelope = EXCLUDED.envelope,
                        graph = EXCLUDED.graph,
                        updated_at = NOW()
                """, game_id, json.dumps(envelope), json.dumps(graph))
        except Exception as e:
            logger.error(f"[TrafficDB] Save graph failed: {e}")

    async def load_graph(self, game_id: str) -> Optional[dict]:
        if not self.enabled:
            return None
        try:
            async with self._pool.acquire() as conn:
                row = await conn.fetchrow(
                    "SELECT graph FROM traffic_graphs WHERE game_id = $1", game_id)
                if row:
                    return json.loads(row["graph"])
        except Exception as e:
            logger.error(f"[TrafficDB] Load graph failed: {e}")
        return None

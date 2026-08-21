"""Firestore monthly backup for traffic persistence.

Requires GOOGLE_APPLICATION_CREDENTIALS env var pointing to a service account JSON.
Get one from: https://console.cloud.google.com/iam-admin/serviceaccounts
  → Create Key → JSON
Enable Firestore API: https://console.cloud.google.com/apis/library/firestore.googleapis.com
"""

from __future__ import annotations
import json
import logging
import os
import time
from typing import Optional

logger = logging.getLogger(__name__)

MONTHLY_INTERVAL = 30 * 24 * 3600  # 30 days in seconds


class TrafficFirestore:
    def __init__(self):
        self._client = None
        self._collection = "traffic_cars"
        self._last_save: dict[str, float] = {}  # game_id → timestamp

    async def init(self):
        creds_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
        if not creds_path:
            logger.warning("[TrafficFirestore] No GOOGLE_APPLICATION_CREDENTIALS, Firestore backup disabled")
            return

        try:
            from google.cloud import firestore
            self._client = firestore.AsyncClient()
            logger.info("[TrafficFirestore] Initialized")
        except ImportError:
            logger.warning("[TrafficFirestore] google-cloud-firestore not installed, backup disabled")
        except Exception as e:
            logger.error(f"[TrafficFirestore] Init failed: {e}")

    @property
    def enabled(self) -> bool:
        return self._client is not None

    async def save_if_due(self, game_id: str, cars: list[dict], graph: dict) -> bool:
        if not self.enabled or not cars:
            return False

        now = time.time()
        last = self._last_save.get(game_id, 0)
        if now - last < MONTHLY_INTERVAL and last > 0:
            return False

        return await self.save(game_id, cars, graph)

    async def save(self, game_id: str, cars: list[dict], graph: dict) -> bool:
        if not self.enabled:
            return False

        try:
            doc_ref = self._client.collection(self._collection).document(game_id)
            await doc_ref.set({
                "cars": cars,
                "graph": graph,
                "updated_at": firestore.SERVER_TIMESTAMP,
            })
            self._last_save[game_id] = time.time()
            logger.info(f"[TrafficFirestore] Saved {len(cars)} cars + graph for {game_id}")
            return True
        except Exception as e:
            logger.error(f"[TrafficFirestore] Save failed: {e}")
            return False

    async def load(self, game_id: str) -> Optional[dict]:
        if not self.enabled:
            return None

        try:
            doc = await self._client.collection(self._collection).document(game_id).get()
            if doc.exists:
                data = doc.to_dict()
                logger.info(f"[TrafficFirestore] Loaded {game_id}")
                return data
        except Exception as e:
            logger.error(f"[TrafficFirestore] Load failed: {e}")
        return None

    async def close(self):
        if self._client:
            self._client.close()
            self._client = None

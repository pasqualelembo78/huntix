"""Registro di proprieta' dei veicoli del mondo (Kenney car kit).

Ogni veicolo del mondo ha un codice STABILE e DETERMINISTICO
(es. "V081006120042") derivato dal chunk e dallo slot: tutti i giocatori
generano gli stessi codici. L'acquisto trasferisce il veicolo nel registro:
da quel momento nessun altro giocatore puo' comprarlo, il proprietario puo'
lasciarlo parcheggiato dove vuole (posizione salvata qui) e rivenderlo.

Storage: file JSON (vehicles_state.json) con lock; sostituibile con Firestore.
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/vehicles", tags=["vehicles"])

_STATE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "vehicles_state.json")
_lock = threading.Lock()


def _load() -> dict:
    try:
        with open(_STATE_PATH, "r") as f:
            return json.load(f)
    except Exception:
        return {}


def _save(state: dict) -> None:
    tmp = _STATE_PATH + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f)
    os.replace(tmp, _STATE_PATH)


def _code_ok(code: str) -> bool:
    return isinstance(code, str) and 3 <= len(code) <= 64 and \
        all(c.isalnum() or c == "_" for c in code)


class BuyBody(BaseModel):
    code: str
    player: str
    model: str = ""
    price: int = 0
    lat: float = 0.0
    lon: float = 0.0


class SellBody(BaseModel):
    code: str
    player: str


class ParkBody(BaseModel):
    code: str
    player: str
    lat: float
    lon: float
    heading: float = 0.0
    odometer_m: int = 0


@router.get("/state")
async def vehicles_state(player: str = ""):
    """Tutti i veicoli posseduti, come lista (parse diretto con JsonUtility).
    Prima della risposta avanza la simulazione furti/recuperi (lazy engine):
    le auto parcheggiate fuori possono essere rubate, quelle in garage no.
    Se `player` e' passato, ogni veicolo include anche lo stato garage
    personale (protetto ora si/no)."""
    now = time.time()
    with _lock:
        state = _load()
        try:
            from vehicle_services import evaluate_all, _garage_protected
            evaluate_all(state, now)
            if state:
                _save(state)
        except Exception as _e:
            logger.warning("theft engine skip: %s", _e)
        owned = []
        for code, v in state.items():
            if code == "_meta":
                continue
            entry = {
                "code": code,
                "owner": v.get("owner", ""),
                "model": v.get("model", ""),
                "lat": v.get("lat", 0.0),
                "lon": v.get("lon", 0.0),
                "heading": v.get("heading", 0.0),
                "price": int(v.get("price") or 0),
                "condition": round(float(v.get("condition", 100.0)), 1),
                "odometer_m": int(v.get("odometer_m") or 0),
                "damage": v.get("damage", ""),
                "anti_theft": v.get("anti_theft") or [],
                "in_garage": bool(v.get("garage_id")),
                "garage_id": v.get("garage_id") or "",
                "stolen": bool(v.get("stolen")),
                "ransom": int(v.get("ransom") or 0),
                "ransom_deadline": float(v.get("ransom_deadline") or 0),
                "found_abandoned": bool(v.get("found_abandoned")),
                "abandoned_at_lat": (v.get("abandoned_at") or [0, 0])[0],
                "abandoned_at_lon": (v.get("abandoned_at") or [0, 0])[1],
                "lost_forever": bool(v.get("lost_forever")),
                "parked_ts": float(v.get("parked_ts") or 0),
            }
            if player:
                entry["garage_protected"] = bool(
                    v.get("garage_id") and
                    _garage_protected(v.get("owner", ""),
                                      v.get("garage_id"), now))
            owned.append(entry)
    return {"ok": True, "owned": owned}


@router.post("/buy")
async def vehicles_buy(body: BuyBody):
    """Regole di business come JSON 200 (il client Unity parsa sempre
    {ok,error} con JsonUtility); 400 solo per input malformato.
    Il veicolo entra nel registro con condizione piena e senza antifurti."""
    if not _code_ok(body.code) or not body.player:
        raise HTTPException(400, "codice o player non validi")
    with _lock:
        state = _load()
        cur = state.get(body.code)
        if cur is not None and cur.get("owner") != body.player:
            return {"ok": False, "error": "owned_by_other"}
        now = time.time()
        prev = cur or {}
        state[body.code] = {
            "owner": body.player,
            "model": body.model,
            "price": body.price,
            "ts": now,
            # stato servizi (conservato se il proprietario riacquista la
            # stessa auto, es. dopo vendita/riacquisto rapido)
            "lat": prev.get("lat", body.lat),
            "lon": prev.get("lon", body.lon),
            "heading": prev.get("heading", 0.0),
            "condition": float(prev.get("condition", 100.0)),
            "odometer_m": int(prev.get("odometer_m") or 0),
            "anti_theft": list(prev.get("anti_theft") or []),
            "garage_id": None,
            "stolen": False,
            "ransom": 0,
            "ransom_deadline": 0,
            "parked_ts": now,
            "last_eval_ts": now,
            "driving_ts": 0,
        }
        _save(state)
    return {"ok": True}


@router.post("/sell")
async def vehicles_sell(body: SellBody):
    if not _code_ok(body.code) or not body.player:
        raise HTTPException(400, "codice o player non validi")
    with _lock:
        state = _load()
        cur = state.get(body.code)
        if cur is None or cur.get("owner") != body.player:
            return {"ok": False, "error": "not_owner"}
        del state[body.code]
        _save(state)
    return {"ok": True}


@router.post("/park")
async def vehicles_park(body: ParkBody):
    """Il proprietario lascia il veicolo dove si trova: la posizione resta
    visibile a tutti gli altri giocatori. Da qui in poi riparte il rischio
    furto (a meno che il client non parcheggi in garage subito dopo).
    `odometer_m` aggiorna usura e condizione."""
    if not _code_ok(body.code) or not body.player:
        raise HTTPException(400, "codice o player non validi")
    with _lock:
        state = _load()
        cur = state.get(body.code)
        if cur is None or cur.get("owner") != body.player:
            return {"ok": False, "error": "not_owner"}
        try:
            from vehicle_services import _apply_odometer
            _apply_odometer(cur, body.odometer_m)
        except Exception as _e:
            logger.warning("odometer skip: %s", _e)
        now = time.time()
        cur["lat"] = round(body.lat, 7)
        cur["lon"] = round(body.lon, 7)
        cur["heading"] = round(body.heading, 1)
        cur["garage_id"] = None          # uscito dal garage (se c'era)
        cur["parked_ts"] = now
        cur["last_eval_ts"] = now
        _save(state)
    return {"ok": True}

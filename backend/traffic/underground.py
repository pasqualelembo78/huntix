"""Parcheggio sotterraneo — sistema parallelo ai garage comunali.

Ogni città con tile urbane ha un proprio parcheggio interrato con stalli
numerati. Flusso:
  1. Il giocatore entra da una rampa in superficie (libero).
  2. Si muove all'interno e trova un posto vuoto.
  3. Se il posto è libero: può acquistarlo (prezzo fisso, €10 test).
     Se è già suo: parcheglia gratuitamente.
     Se è di un altro: non può parcheggiare.
  4. Uscita dall'unica rampa di uscita → torna su a caso tra le
     entrate della stessa città.

La protezione antifurto è attiva quando il veicolo è dentro (campo
'vehicle.protected' usato da vehicle_services.evaluate_vehicle).
Stato separato: underground_state.json (non tocca garages_state.json).
"""

from __future__ import annotations

import json
import os
import random
import threading
import time
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api/underground", tags=["underground"])

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
_STATE_PATH = os.path.join(_BASE_DIR, "underground_state.json")
_lock = threading.Lock()

SPOT_PRICE_EUR = 10          # prezzo test acquisto posto
LEVELS = 3                   # livelli del parcheggio sotterraneo
LEVEL_HEIGHT_M = 4.0         # altezza tra livelli
BAY_WIDTH_M = 2.50           # larghezza stalli
BAY_LENGTH_M = 5.00          # profondità stalli
AISLE_WIDTH_M = 6.00         # corsie di manovra
COL_SPACING_BAYS = 3         # pilastri ogni N stalli


def _load() -> dict:
    try:
        with open(_STATE_PATH, "r") as f:
            return json.load(f)
    except Exception:
        return {}


def _save(state: dict) -> None:
    tmp = _STATE_PATH + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f, indent=2)
    os.replace(tmp, _STATE_PATH)


def _ensure_city(state: dict, city: str) -> dict:
    cities = state.setdefault("cities", {})
    if city not in cities:
        cities[city] = {"next_spot": 1, "entrances": []}
    return cities[city]


# ── Modelli richiesta ────────────────────────────────────────────

class EnterBody(BaseModel):
    player: str
    city: str
    lat: float
    lon: float


class ParkBody(BaseModel):
    player: str
    code: str          # vehicle code
    city: str
    spot: int
    lat: float = 0.0
    lon: float = 0.0


class ExitBody(BaseModel):
    player: str


class StatusBody(BaseModel):
    player: str


# ── Endpoints ────────────────────────────────────────────────────

@router.post("/enter")
async def underground_enter(body: EnterBody):
    """Entra nel parcheggio sotterraneo da una rampa in superficie.
    La rampa viene registrata come entrata nota per questa città (usata
    poi come possibile uscita casuale)."""
    with _lock:
        state = _load()
        city = _ensure_city(state, body.city)
        known = {(e["lat"], e["lon"]) for e in city["entrances"]}
        if (round(body.lat, 6), round(body.lon, 6)) not in known:
            city["entrances"].append({
                "lat": round(body.lat, 6),
                "lon": round(body.lon, 6),
            })
        _save(state)
    return {"ok": True, "levels": LEVELS}


@router.post("/buy_spot")
async def underground_buy_spot(body: ParkBody):
    """Acquista uno stallo libero e ci parcheggi la macchina.
    Il wallet viene scalato lato client PRIMA di questa chiamata.
    Se lo stallo è già tuo → parcheglia direttamente (gratuito).
    Se è di un altro → errore."""
    if body.spot < 1:
        raise HTTPException(400, "spot non valido")
    with _lock:
        from vehicles import _load as vload, _save as vsave
        state = _load()
        vstate = vload()
        city = _ensure_city(state, body.city)
        ownerships = state.setdefault("ownership", {})

        # controlla che il giocatore abbia già un posto da qualche parte
        if body.player in ownerships:
            existing = ownerships[body.player]
            if existing["city"] != body.city or existing["spot"] != body.spot:
                return {"ok": False, "error": "already_own_spot",
                        "your_city": existing["city"],
                        "your_spot": existing["spot"]}

        # controlla che lo stallo sia libero
        for pid, o in ownerships.items():
            if o["city"] == body.city and o["spot"] == body.spot:
                return {"ok": False, "error": "spot_taken"}

        # acquista
        ownerships[body.player] = {
            "city": body.city,
            "spot": body.spot,
            "ts": time.time(),
        }
        city["next_spot"] = max(city.get("next_spot", 1), body.spot + 1)

        # parcheggi il veicolo
        v = vstate.get(body.code)
        if v and v.get("owner") == body.player and not v.get("stolen"):
            v["garage_id"] = f"underground:{body.city}:{body.spot}"
            v["protected"] = True
            v["lat"] = round(body.lat, 7)
            v["lon"] = round(body.lon, 7)
            v["heading"] = 0.0
            v["last_eval_ts"] = time.time()
            vsave(vstate)

        _save(state)
    return {"ok": True, "action": "bought", "spot": body.spot,
            "cost": SPOT_PRICE_EUR}


@router.post("/park")
async def underground_park(body: ParkBody):
    """Parcheggia il veicolo in uno stallo che possiedi già.
    Se non lo possiedi → usa /buy_spot.
    Se è di un altro → errore."""
    with _lock:
        from vehicles import _load as vload, _save as vsave
        state = _load()
        vstate = vload()
        ownerships = state.get("ownership", {})

        my_spot = ownerships.get(body.player)
        if not my_spot or my_spot["city"] != body.city or \
                my_spot["spot"] != body.spot:
            return {"ok": False, "error": "not_your_spot"}

        v = vstate.get(body.code)
        if not v or v.get("owner") != body.player:
            return {"ok": False, "error": "not_owner"}
        if v.get("stolen"):
            return {"ok": False, "error": "stolen"}

        v["garage_id"] = f"underground:{body.city}:{body.spot}"
        v["protected"] = True
        v["lat"] = round(body.lat, 7)
        v["lon"] = round(body.lon, 7)
        v["heading"] = 0.0
        v["last_eval_ts"] = time.time()
        vsave(vstate)
        _save(state)
    return {"ok": True, "action": "parked", "spot": body.spot}


@router.post("/exit")
async def underground_exit(body: ExitBody):
    """Esci dal parcheggio sotterraneo: l'auto appare su una rampa
    casuale della stessa città di provenienza."""
    with _lock:
        from vehicles import _load as vload, _save as vsave
        state = _load()
        vstate = vload()
        ownerships = state.get("ownership", {})
        my_spot = ownerships.get(body.player)

        # trova il veicolo nel sotterraneo
        found_code = None
        for code, v in vstate.items():
            if isinstance(v, dict) and v.get("owner") == body.player and \
                    v.get("protected") and v.get("garage_id", "").startswith("underground:"):
                found_code = code
                break

        if found_code:
            v = vstate[found_code]
            v["garage_id"] = None
            v["protected"] = False
            v["last_eval_ts"] = time.time()
            vsave(vstate)

        # scegli un'uscita casuale tra le entrate note della città
        city_key = my_spot["city"] if my_spot else None
        if not city_key:
            # fallback: prova a estrarre città dal garage_id
            if found_code:
                gid = vstate.get(found_code, {}).get("garage_id", "")
                if gid.startswith("underground:"):
                    city_key = gid.split(":")[1]

        exit_lat, exit_lon = 41.4627, 15.5454  # fallback Foggia
        if city_key:
            cities = state.get("cities", {})
            entrances = cities.get(city_key, {}).get("entrances", [])
            if entrances:
                e = random.choice(entrances)
                exit_lat, exit_lon = e["lat"], e["lon"]

        _save(state)
    return {"ok": True, "lat": exit_lat, "lon": exit_lon,
            "city": city_key}


@router.get("/status")
async def underground_status(player: str):
    """Stato del sotterraneo per il giocatore: posto posseduto, auto
    parcheggiata, prezzo."""
    with _lock:
        state = _load()
        ownerships = state.get("ownership", {})
        my_spot = ownerships.get(player)

        car_parked = False
        from vehicles import _load as vload
        vstate = vload()
        for code, v in vstate.items():
            if isinstance(v, dict) and v.get("owner") == player and \
                    v.get("protected") and \
                    v.get("garage_id", "").startswith("underground:"):
                car_parked = True
                break

    return {
        "owned_spot": my_spot["spot"] if my_spot else None,
        "owned_city": my_spot["city"] if my_spot else None,
        "car_parked": car_parked,
        "price": SPOT_PRICE_EUR,
        "levels": LEVELS,
    }


@router.get("/city_status")
async def underground_city_status(city: str):
    """Stato di un sotterraneo città: posti totali, occupati, prossimo."""
    with _lock:
        state = _load()
        city_data = _ensure_city(state, city)
        ownerships = state.get("ownership", {})
        occupied = sum(1 for o in ownerships.values()
                       if o["city"] == city)
        next_spot = city_data.get("next_spot", 1)
        entrances = len(city_data.get("entrances", []))
        _save(state)
    return {
        "city": city,
        "next_spot": next_spot,
        "occupied": occupied,
        "levels": LEVELS,
        "entrances": entrances,
    }

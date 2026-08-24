"""Servizi veicoli del mondo: furti, garage a pagamento, officina.

Estende il registro di traffic/vehicles.py con:
  • condizione + odometro: l'auto si usura coi chilometri, in officina si ripara
  • antifurti (blocco motore/shaft/ruote/pedali): acquistabili in officina,
    riducono la probabilita' di furto
  • garage: affitto giornaliero (valido fino alla mezzanotte reale) oppure
    acquisto permanente (uno solo per giocatore). Auto dentro il garage =
    immunità totale dai furti.
  • motore furti LAZY: valutato a ogni lettura dello stato per intervalli di
    30 minuti mai riprocessati (last_eval_ts), quindi nessun cron necessario.
    Parcheggiata di giorno rischia poco, di notte molto; in uso (drive-ping
    recente) e' intoccabile.
  • "cavallo di ritorno": l'auto rubata genera una telefonata del ladro con
    richiesta di riscatto. Accetti -> auto consegnata all'officina indicata
    (con danni extra). Rifiuti (o scadenza) -> dopo un delay c'e' una chance
    che venga ritrovata abbandonata da qualche parte, altrimenti e' persa.

Storage: stessi file JSON di traffic/vehicles.py + garages_state.json.
Modello fiducia: il wallet e' client-side (PlayerPrefs), il server registra
stati ed esclusive; coerente col resto del modulo veicoli.
"""

from __future__ import annotations

import json
import logging
import os
import random
import threading
import time
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/vehicles", tags=["vehicle-services"])

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
_GARAGES_PATH = os.path.join(_BASE_DIR, "garages_state.json")
_lock = threading.Lock()

# ── Costanti di bilanciamento ────────────────────────────────────
THEFT_EVAL_INTERVAL = 30 * 60          # slot di valutazione: 30 min
DRIVING_GRACE_SEC = 5 * 60             # drive-ping recente = auto in uso
THEFT_RATE_DAY = 0.005                 # 0.5%/h  (07-22)
THEFT_RATE_NIGHT = 0.015               # 1.5%/h  (22-07)
NIGHT_START, NIGHT_END = 22, 7         # ora locale Italia
RANSOM_DEADLINE_H = 48.0               # ore prima dell'auto-rifiuto
RECOVERY_DELAY_MIN_H, RECOVERY_DELAY_MAX_H = 6.0, 24.0
RECOVERY_CHANCE = 0.40                 # chance di ritrovarla abbandonata
ABANDONED_CONDITION = (10.0, 30.0)     # condizione se ritrovata
STOLEN_EXTRA_DAMAGE = (10.0, 25.0)     # danni extra se riscattata
CONDITION_PER_KM = 100.0 / 150.0       # 150 km per arrivare a 0%
REPAIR_PRICE_FACTOR = 0.004            # costo riparazione = pct*prezzo*fattore
RENTAL_PRICE_EUR = 10                  # affitto garage per giornata
GARAGE_BUY_PRICE_EUR = 500             # acquisto garage permanente

ANTI_THEFT_DEVICES = {
    "engine": {"name": "Blocco motore", "price": 120, "mult": 0.65},
    "shaft":  {"name": "Block shaft",   "price": 90,  "mult": 0.75},
    "wheels": {"name": "Blocco ruote",  "price": 70,  "mult": 0.80},
    "pedals": {"name": "Blocco pedali", "price": 50,  "mult": 0.85},
}

_TZ_ROME = ZoneInfo("Europe/Rome")


def _load_garages() -> dict:
    try:
        with open(_GARAGES_PATH, "r") as f:
            return json.load(f)
    except Exception:
        return {}


def _save_garages(state: dict) -> None:
    tmp = _GARAGES_PATH + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f)
    os.replace(tmp, _GARAGES_PATH)


def _rome_now() -> datetime:
    return datetime.now(_TZ_ROME)


def _is_night_hour(hour: int) -> bool:
    return hour >= NIGHT_START or hour < NIGHT_END


def _today_rome() -> str:
    return _rome_now().strftime("%Y-%m-%d")


def _garage_protected(player: str, garage_id: str | None, now: float) -> bool:
    """True se l'auto nel garage indicato e' protetta ORA."""
    if not garage_id:
        return False
    g = _load_garages()
    own = g.get("owned", {}).get(player)
    if isinstance(own, dict) and own.get("garage_id") == garage_id:
        return True
    rent = g.get("rentals", {}).get(player)
    return bool(rent and rent.get("garage_id") == garage_id and
                rent.get("day") == _today_rome())


# ── Motore furti (lazy) ──────────────────────────────────────────
def evaluate_all(state: dict, now: float | None = None) -> None:
    """Valuta furto/recupero per TUTTI i veicoli e rimuove quelli persi
    per sempre (tornano auto del mondo, ricomprabili da chiunque).
    Chiamare sotto lock."""
    now = now or time.time()
    lost = []
    for code, v in state.items():
        if code == "_meta" or not isinstance(v, dict):
            continue
        evaluate_vehicle(v, code, now)
        if v.get("lost_forever"):
            lost.append(code)
    for code in lost:
        del state[code]
        logger.info("veicolo %s rimosso dal registro (perso)", code)


def evaluate_vehicle(v: dict, code: str, now: float) -> None:
    """Avanza la simulazione di furto/riscatto/abbandono per un veicolo.

    Chiamare SEMPRE sotto lock prima di qualunque lettura/scrittura.
    """
    if not isinstance(v, dict) or not v.get("owner"):
        return

    # ── gia' rubata: gestisci deadline e recupero abbandono ──
    if v.get("stolen"):
        deadline = v.get("ransom_deadline", 0)
        if deadline and now > deadline and not v.get("refused_ts"):
            _resolve_refusal(v, code, now)
            return
        refused_at = v.get("refused_ts")
        if refused_at and not v.get("_recovery_done"):
            resolve_at = v.get("recovery_resolve_ts", 0)
            if now >= resolve_at:
                v["_recovery_done"] = True
                if random.random() < RECOVERY_CHANCE:
                    slat = v.get("lat", 0.0)
                    slon = v.get("lon", 0.0)
                    v["found_abandoned"] = True
                    v["abandoned_at"] = [
                        round(slat + random.uniform(-0.006, 0.006), 6),
                        round(slon + random.uniform(-0.008, 0.008), 6),
                    ]
                    v["condition"] = round(random.uniform(*ABANDONED_CONDITION), 1)
                    v["abandoned_found_ts"] = now
                    logger.info("veicolo %s ritrovato abbandonato", code)
                else:
                    v["lost_forever"] = True
                    logger.info("veicolo %s perso definitivamente", code)

    protection_valid = v.get("garage_id") and \
        _garage_protected(v["owner"], v.get("garage_id"), now)
    driving_recently = (now - v.get("driving_ts", 0)) < DRIVING_GRACE_SEC

    if v.get("stolen") or protection_valid or driving_recently:
        v["last_eval_ts"] = now
        return

    last = v.get("last_eval_ts") or v.get("parked_ts") or now
    if now <= last:
        return

    mult = 1.0
    for dev in v.get("anti_theft") or []:
        mult *= ANTI_THEFT_DEVICES.get(dev, {}).get("mult", 1.0)

    slot = THEFT_EVAL_INTERVAL
    t = float(last)
    while t < now:
        nxt = min(t + slot, now)
        hours = (nxt - t) / 3600.0
        hour = datetime.fromtimestamp((t + nxt) / 2.0, _TZ_ROME).hour
        rate = (THEFT_RATE_NIGHT if _is_night_hour(hour) else THEFT_RATE_DAY) * mult
        # probabilita' per-spanna: rate e' oraria, basta moltiplicare per le ore
        if hours > 0 and random.random() < rate * hours:
            _steal(v, code, (t + nxt) / 2.0)
            v["last_eval_ts"] = nxt
            return
        t = nxt
    v["last_eval_ts"] = now


def _steal(v: dict, code: str, when: float) -> None:
    price = max(int(v.get("price") or 50), 50)
    ransom = max(20, round(price * random.uniform(0.30, 0.45)))
    v.update({
        "stolen": True,
        "stolen_ts": when,
        "ransom": ransom,
        "ransom_deadline": when + RANSOM_DEADLINE_H * 3600,
        "garage_id": None,
        "theft_lat": v.get("lat"),
        "theft_lon": v.get("lon"),
    })
    logger.info("FURTO veicolo %s: riscatto %s EUR", code, ransom)


def _resolve_refusal(v: dict, code: str, now: float) -> None:
    """Rifiuto implicito (deadline scaduta): parte il countdown recupero."""
    v["refused_ts"] = now
    delay_h = random.uniform(RECOVERY_DELAY_MIN_H, RECOVERY_DELAY_MAX_H)
    v["recovery_resolve_ts"] = now + delay_h * 3600
    logger.info("riscatto scaduto per %s: recupero tra %.1fh", code, delay_h)


# ── Modelli richiesta ────────────────────────────────────────────
class PlayerBody(BaseModel):
    player: str


class GarageParkBody(BaseModel):
    code: str = ""
    player: str
    garage_id: str
    lat: float = 0.0
    lon: float = 0.0


class GarageBuyBody(BaseModel):
    player: str
    garage_id: str
    lat: float = 0.0
    lon: float = 0.0


class RepairBody(BaseModel):
    code: str
    player: str
    odometer_m: int = 0


class AntitheftBody(BaseModel):
    code: str
    player: str
    device: str


class RansomBody(BaseModel):
    code: str
    player: str
    accept: bool
    officina_lat: float = 0.0
    officina_lon: float = 0.0


class OdometerBody(BaseModel):
    code: str
    player: str
    odometer_m: int
    condition: float | None = None


# ── Helper comuni ────────────────────────────────────────────────
def _get_owned(state: dict, code: str, player: str) -> dict:
    cur = state.get(code)
    if cur is None or cur.get("owner") != player:
        raise HTTPException(403, "non proprietario del veicolo")
    return cur


def _apply_odometer(v: dict, odometer_m: int) -> None:
    prev = int(v.get("odometer_m") or 0)
    if odometer_m > prev:
        delta_km = (odometer_m - prev) / 1000.0
        cond = float(v.get("condition", 100.0)) - delta_km * CONDITION_PER_KM
        v["odometer_m"] = int(odometer_m)
        v["condition"] = round(max(0.0, min(100.0, cond)), 1)


# ── Endpoint garage ──────────────────────────────────────────────
@router.post("/garage/buy")
async def garage_buy(body: GarageBuyBody):
    """Acquisto permanente di un garage (massimo UNO per giocatore).
    Il wallet viene scalato lato client PRIMA di chiamare."""
    if not body.player or not body.garage_id:
        raise HTTPException(400, "player o garage_id mancanti")
    with _lock:
        g = _load_garages()
        owned = g.setdefault("owned", {})
        if body.player in owned:
            return {"ok": False, "error": "already_own_garage"}
        owned[body.player] = {
            "garage_id": body.garage_id,
            "lat": round(body.lat, 7),
            "lon": round(body.lon, 7),
            "ts": time.time(),
        }
        _save_garages(g)
    return {"ok": True}


@router.post("/garage/rent")
async def garage_rent(body: GarageParkBody):
    """Affitto giornaliero di un garage: valido fino alla mezzanotte reale
    d'Italia del giorno corrente (affittare alle 23:55 non regala niente).
    Il wallet viene scalato lato client."""
    if not body.player or not body.garage_id:
        raise HTTPException(400, "player o garage_id mancanti")
    with _lock:
        g = _load_garages()
        rentals = g.setdefault("rentals", {})
        existing = rentals.get(body.player)
        today = _today_rome()
        if existing and existing.get("day") == today and \
                existing.get("garage_id") != body.garage_id:
            return {"ok": False, "error": "rented_elsewhere_today"}
        rentals[body.player] = {"garage_id": body.garage_id, "day": today}
        _save_garages(g)
    midnight = (_rome_now().replace(hour=0, minute=0, second=0,
                                    microsecond=0) + timedelta(days=1))
    return {"ok": True, "day": today, "expires_ts": midnight.timestamp()}


@router.post("/garage/exit")
async def garage_exit(body: PlayerBody):
    """Uscita dell'auto dal garage: il client la riposiziona davanti e poi
    guida. Da quel momento riparte il rischio furto."""
    with _lock:
        from vehicles import _load, _save
        state = _load()
        for code, v in state.items():
            if isinstance(v, dict) and v.get("owner") == body.player and \
                    v.get("garage_id"):
                v["garage_id"] = None
                v["last_eval_ts"] = time.time()
                _save(state)
                return {"ok": True, "code": code}
    return {"ok": False, "error": "no_car_in_garage"}


@router.post("/garage/park")
async def garage_park(body: GarageParkBody):
    """Ricovero dell'auto nel garage (proprio o affittato oggi).
    Finche' resta li' non puo' essere rubata."""
    with _lock:
        from vehicles import _load, _save
        state = _load()
        v = _get_owned(state, body.code, body.player)
        if not _garage_protected(body.player, body.garage_id, time.time()):
            return {"ok": False, "error": "no_garage_access"}
        if v.get("stolen"):
            return {"ok": False, "error": "stolen"}
        v["garage_id"] = body.garage_id
        v["lat"] = round(body.lat, 7)
        v["lon"] = round(body.lon, 7)
        v["heading"] = 0.0
        v["last_eval_ts"] = time.time()
        _save(state)
    return {"ok": True}


@router.get("/garage/status")
async def garage_status(player: str):
    """Stato garage del giocatore: proprieta', affitto corrente, validita'."""
    g = _load_garages()
    own = g.get("owned", {}).get(player)
    rent = g.get("rentals", {}).get(player)
    today = _today_rome()
    return {
        "owned": {
            "garage_id": own["garage_id"], "lat": own.get("lat"),
            "lon": own.get("lon"),
        } if own else None,
        "rental": {
            "garage_id": rent["garage_id"],
            "day": rent["day"],
            "valid": rent.get("day") == today,
        } if rent else None,
        "prices": {"rent_day": RENTAL_PRICE_EUR, "buy": GARAGE_BUY_PRICE_EUR},
    }


# ── Endpoint officina ────────────────────────────────────────────
@router.get("/service/catalog")
async def service_catalog():
    """Catalogo servizi officina: prezzi antifurti e formula riparazione."""
    return {
        "antitheft": [
            {"id": k, "name": d["name"], "price": d["price"]}
            for k, d in ANTI_THEFT_DEVICES.items()
        ],
        "repair_price_factor": REPAIR_PRICE_FACTOR,
    }


@router.post("/service/repair")
async def service_repair(body: RepairBody):
    """Riparazione completa in officina: condizione torna a 100.
    Il client scala il costo = pct_mancante * prezzo_veicolo * fattore."""
    with _lock:
        from vehicles import _load, _save
        state = _load()
        v = _get_owned(state, body.code, body.player)
        if v.get("stolen"):
            return {"ok": False, "error": "stolen"}
        _apply_odometer(v, body.odometer_m)
        before = float(v.get("condition", 100.0))
        price = max(int(v.get("price") or 50), 50)
        cost = round((100.0 - before) * price * REPAIR_PRICE_FACTOR)
        v["condition"] = 100.0
        _save(state)
    return {"ok": True, "cost": cost, "condition_before": round(before, 1),
            "condition_after": 100.0}


@router.post("/service/antitheft")
async def service_antitheft(body: AntitheftBody):
    """Installazione di un dispositivo antifurto in officina."""
    dev = ANTI_THEFT_DEVICES.get(body.device or "")
    if not dev:
        raise HTTPException(400, "dispositivo sconosciuto")
    with _lock:
        from vehicles import _load, _save
        state = _load()
        v = _get_owned(state, body.code, body.player)
        devices = list(v.get("anti_theft") or [])
        if body.device in devices:
            return {"ok": False, "error": "already_installed"}
        devices.append(body.device)
        v["anti_theft"] = devices
        _save(state)
    return {"ok": True, "device": body.device, "devices": devices}


# ── Odometro / telemetria ────────────────────────────────────────
@router.post("/drive-ping")
async def drive_ping(body: OdometerBody):
    """Heartbeat mentre l'auto e' in uso: aggiorna odometro/condizione e
    rende il veicolo immune ai furti per DRIVING_GRACE_SEC."""
    with _lock:
        from vehicles import _load, _save
        state = _load()
        v = state.get(body.code)
        if v is None or v.get("owner") != body.player:
            return {"ok": False, "error": "not_owner"}
        if v.get("stolen"):
            return {"ok": False, "error": "stolen"}
        _apply_odometer(v, body.odometer_m)
        if body.condition is not None:
            v["condition"] = round(max(0.0, min(100.0, body.condition)), 1)
        v["driving_ts"] = time.time()
        _save(state)
    return {"ok": True}


# ── Cavallo di ritorno ───────────────────────────────────────────
@router.post("/ransom/respond")
async def ransom_respond(body: RansomBody):
    """Risposta alla telefonata del ladro.

    accept=True : paghi il riscatto (wallet lato client) e l'auto viene
                  consegnata all'officina indicata con danni extra.
    accept=False: rifiuti; dopo qualche ora c'e' una chance di ritrovarla
                  abbandonata, altrimenti e' persa per sempre.
    """
    now = time.time()
    with _lock:
        from vehicles import _load, _save
        state = _load()
        v = _get_owned(state, body.code, body.player)
        if not v.get("stolen"):
            return {"ok": False, "error": "not_stolen"}
        if body.accept:
            if now > v.get("ransom_deadline", 0):
                _resolve_refusal(v, body.code, now)
                _save(state)
                return {"ok": False, "error": "expired"}
            dmg = random.uniform(*STOLEN_EXTRA_DAMAGE)
            v.update({
                "stolen": False,
                "ransom": 0,
                "ransom_deadline": 0,
                "lat": round(body.officina_lat, 7) or v.get("lat"),
                "lon": round(body.officina_lon, 7) or v.get("lon"),
                "heading": 0.0,
                "parked_ts": now,
                "last_eval_ts": now,
                "condition": round(max(5.0, float(
                    v.get("condition", 100.0)) - dmg), 1),
            })
            _save(state)
            return {"ok": True, "outcome": "returned_to_officina",
                    "lat": v["lat"], "lon": v["lon"],
                    "condition": v["condition"]}
        _resolve_refusal(v, body.code, now)
        _save(state)
        return {"ok": True, "outcome": "refused"}


@router.post("/abandoned/recover")
async def abandoned_recover(body: PlayerBody):
    """Recupero di un'auto ritrovata abbandonata: torna tua, dove si trova."""
    now = time.time()
    with _lock:
        from vehicles import _load, _save
        state = _load()
        found = None
        for code, v in state.items():
            if isinstance(v, dict) and v.get("owner") == body.player and \
                    v.get("found_abandoned"):
                found = code
                break
        if not found:
            return {"ok": False, "error": "nothing_recovered"}
        v = state[found]
        v.update({
            "stolen": False, "found_abandoned": False, "refused_ts": 0,
            "ransom": 0, "ransom_deadline": 0,
            "parked_ts": now, "last_eval_ts": now,
        })
        _save(state)
        return {"ok": True, "code": found, "lat": v.get("lat"),
                "lon": v.get("lon"), "condition": v.get("condition", 20)}

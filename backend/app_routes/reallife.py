"""
app_routes/reallife.py — Endpoint Fase B (Real Life) di Huntix.

  GET  /reallife/world            -> stato del mondo (data/ora/stagione/meteo)
  GET  /reallife/needs            -> bisogni Sims del personaggio (decay)
  POST /reallife/interact         -> ricarica bisogni + XP skill (dopo una chat)
  GET  /reallife/skills           -> skill dell'utente + catalogo
  GET  /reallife/map              -> posizioni NPC sulla mappa 2D

Auth: jwt_optional. Se il token manca si usa `user_id` passato nel body/query
(il client Real Life usa un'identità anonima per-device).
"""
import logging
from typing import Optional

from fastapi import APIRouter, Request, Query, Body, Depends, HTTPException
from pydantic import BaseModel

from slowapi import Limiter
from slowapi.util import get_remote_address

from reallife.store import (
    get_world_state, get_needs, recharge_needs, get_skills, add_skill_xp, get_map_state,
)
from auth_fastapi import jwt_optional, jwt_required, AuthUser

logger = logging.getLogger(__name__)
router = APIRouter()
limiter = Limiter(key_func=get_remote_address)


def _user_id(user: AuthUser) -> str:
    if not user or not getattr(user, "user_id", None):
        raise HTTPException(401, "autenticazione richiesta")
    return user.user_id


class InteractRequest(BaseModel):
    character_id: str
    user_id: Optional[str] = None
    character_tags: list = []
    interaction: str = "chat"  # "chat" | "activity"


@router.get("/reallife/world")
async def api_world(user: AuthUser = Depends(jwt_optional)):
    return get_world_state()


@router.get("/reallife/needs")
async def api_needs(
    character_id: str = Query(...),
    user_id: Optional[str] = Query(None),
    user: AuthUser = Depends(jwt_optional),
):
    uid = _user_id(user)
    return get_needs(uid, character_id)


@router.post("/reallife/interact")
@limiter.limit("10/minute")
async def api_interact(
    request: Request,
    body: InteractRequest,
    user: AuthUser = Depends(jwt_optional),
):
    uid = _user_id(user)
    needs = recharge_needs(uid, body.character_id, body.interaction)
    leveled = add_skill_xp(uid, body.character_tags, amount=20)
    skills = get_skills(uid)
    return {"needs": needs, "skills_leveled_up": leveled, "skills": skills}


@router.get("/reallife/skills")
async def api_skills(
    user_id: Optional[str] = Query(None),
    user: AuthUser = Depends(jwt_optional),
):
    uid = _user_id(user)
    return get_skills(uid)


@router.get("/reallife/map")
async def api_map(user: AuthUser = Depends(jwt_optional)):
    return get_map_state()


# ── Venue character assignment ────────────────────────────────────

@router.get("/reallife/venue-character")
async def api_venue_character(
    venue_id: str = Query(...),
    venue_name: str = Query(""),
    building_type: str = Query("RESTAURANT"),
    lat: float = Query(0.0),
    lng: float = Query(0.0),
    user: AuthUser = Depends(jwt_optional),
):
    """
    Ritorna il personaggio assegnato al locale specificato.
    Se non ancora assegnato, lo assegna ora con l'esclusione 1km.
    """
    from reallife.venue_assignment import get_venue_character
    char = get_venue_character(venue_id, venue_name, building_type, lat, lng)
    if not char:
        raise HTTPException(404, "Nessun personaggio disponibile per questo locale")
    safe_fields = [
        "id", "name", "full_name", "age", "role", "category",
        "avatar", "description", "tags", "essence", "knowledge_domains",
    ]
    return {k: char.get(k) for k in safe_fields if k in char}


@router.post("/reallife/rotate-venue-staff")
async def api_rotate_venue_staff(user: AuthUser = Depends(jwt_required)):
    """
    Licenzia tutto il personale dei locali e lo riassegna.
    Uso previsto: chiamata automatica mensile da cron.
    """
    from reallife.venue_assignment import fire_all_staff
    count = fire_all_staff()
    return {"status": "ok", "venues_cleared": count, "message": f"Personale licenziato da {count} locali. Al prossimo accesso verranno riassegnati."}


# ── Venue orders ─────────────────────────────────────────────────

class OrderRequest(BaseModel):
    venue_id: str
    venue_name: str = ""
    building_type: str = "RESTAURANT"
    lat: float = 0.0
    lng: float = 0.0
    items: list = []
    user_id: Optional[str] = None


class OrderCompleteRequest(BaseModel):
    order_id: int
    character_id: str = ""
    user_id: Optional[str] = None


@router.post("/reallife/order")
async def api_create_order(body: OrderRequest, user: AuthUser = Depends(jwt_optional)):
    """Crea un ordine per un locale."""
    from reallife.orders import create_order, get_user_balance
    from reallife.venue_assignment import get_venue_character
    uid = _user_id(user)
    char = get_venue_character(body.venue_id, body.venue_name, body.building_type, body.lat, body.lng)
    if not char:
        raise HTTPException(404, "Nessun personaggio disponibile per questo locale")
    result = create_order(uid, body.venue_id, char["id"], body.items, body.building_type)
    if isinstance(result, dict) and result.get("error") == "insufficient_funds":
        return {
            "status": "error",
            "error": "insufficient_funds",
            "message": "Non hai abbastanza MVC! Vai a lavorare prima.",
            "balance": result["balance"],
            "cost": result["cost"],
        }
    return {
        "status": "ok",
        "order_id": result["order_id"],
        "cost": result["cost"],
        "balance_after": result["balance_after"],
        "character_id": char["id"],
        "character_name": char.get("name", "Sconosciuto"),
        "character_avatar": char.get("avatar", ""),
    }


@router.get("/reallife/orders")
async def api_get_orders(
    venue_id: Optional[str] = Query(None),
    user: AuthUser = Depends(jwt_optional),
):
    """Ottiene gli ordini pendenti."""
    from reallife.orders import get_pending_orders
    uid = _user_id(user)
    return get_pending_orders(uid, venue_id)


@router.post("/reallife/order/complete")
async def api_complete_order(body: OrderCompleteRequest, user: AuthUser = Depends(jwt_optional)):
    """Completa un ordine e applica i gain."""
    from reallife.orders import complete_order
    uid = _user_id(user)
    result = complete_order(body.order_id, uid)
    if not result:
        raise HTTPException(404, "Ordine non trovato o già completato")
    return {"status": "ok", **result}


@router.get("/reallife/balance")
async def api_balance(user: AuthUser = Depends(jwt_optional)):
    """Restituisce il saldo MVC dell'utente."""
    from reallife.orders import get_user_balance
    uid = _user_id(user)
    return {"balance": get_user_balance(uid)}


@router.get("/reallife/skills")
async def api_user_skills(user: AuthUser = Depends(jwt_optional)):
    """Restituisce le capacità dell'utente."""
    from reallife.skills import get_user_skills, BUILDING_SKILLS
    uid = _user_id(user)
    skills = get_user_skills(uid)
    return {
        "skills": skills,
        "building_requirements": BUILDING_SKILLS,
    }


class WorkRequest(BaseModel):
    user_id: Optional[str] = None
    building_type: str = "RESTAURANT"


@router.post("/reallife/work")
async def api_work(body: WorkRequest, user: AuthUser = Depends(jwt_optional)):
    """Guadagna MVC lavorando. La paga dipende da energia, umore e capacità."""
    from reallife.orders import work, get_user_balance
    uid = _user_id(user)
    result = work(uid, body.building_type)
    return {
        "status": "ok",
        "earned": result["reward"],
        "balance": get_user_balance(uid),
        "is_trainee": result["is_trainee"],
        "skill_name": result["skill_name"],
        "skill_level": result["skill_level"],
        "base_reward": result["base_reward"],
        "skill_bonus": result["skill_bonus"],
        "energy_penalty": result["energy_penalty"],
        "mood_penalty": result["mood_penalty"],
        "message": f"Hai guadagnato {result['reward']} MVC! " +
                   ("(tirocinio)" if result["is_trainee"] else ""),
    }

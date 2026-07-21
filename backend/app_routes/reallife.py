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

from fastapi import APIRouter, Request, Query, Body, Depends
from pydantic import BaseModel

from reallife.store import (
    get_world_state, get_needs, recharge_needs, get_skills, add_skill_xp, get_map_state,
)
from auth_fastapi import jwt_optional, AuthUser

logger = logging.getLogger(__name__)
router = APIRouter()


def _user_id(user: AuthUser, fallback: Optional[str]) -> str:
    if user and getattr(user, "user_id", None):
        return user.user_id
    return fallback or "anon"


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
    uid = _user_id(user, user_id)
    return get_needs(uid, character_id)


@router.post("/reallife/interact")
async def api_interact(
    body: InteractRequest,
    user: AuthUser = Depends(jwt_optional),
):
    uid = _user_id(user, body.user_id)
    needs = recharge_needs(uid, body.character_id, body.interaction)
    leveled = add_skill_xp(uid, body.character_tags, amount=20)
    skills = get_skills(uid)
    return {"needs": needs, "skills_leveled_up": leveled, "skills": skills}


@router.get("/reallife/skills")
async def api_skills(
    user_id: Optional[str] = Query(None),
    user: AuthUser = Depends(jwt_optional),
):
    uid = _user_id(user, user_id)
    return get_skills(uid)


@router.get("/reallife/map")
async def api_map(user: AuthUser = Depends(jwt_optional)):
    return get_map_state()

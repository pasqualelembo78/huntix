"""
reallife/skills.py — Gestione capacità lavorative.

Ogni tipo di locale richiede una capacità (skill) minima. Se l'utente
ha una capacità bassa, lavora in tirocinio con paga ridotta. Più alta
la capacità, più alta la paga (fino al 100%).
"""
import logging
from db import get_conn, put_conn

logger = logging.getLogger(__name__)

# BuildingType → skill richiesta, livello minimo, paga base
BUILDING_SKILLS = {
    "RESTAURANT": {"skill": "cucina", "min_level": 1, "work_reward": 25},
    "SUPERMARKET": {"skill": "business", "min_level": 1, "work_reward": 20},
    "HOSPITAL": {"skill": "medicina", "min_level": 1, "work_reward": 35},
    "GYM": {"skill": "sport", "min_level": 1, "work_reward": 20},
    "HOUSE": {"skill": "quotidiano", "min_level": 1, "work_reward": 15},
    "MONUMENT": {"skill": "storia", "min_level": 1, "work_reward": 20},
    "MUSEUM": {"skill": "creativi", "min_level": 1, "work_reward": 25},
}

# Costi ordini per tipo di locale (MVC)
ORDER_COSTS = {
    "RESTAURANT": 50,
    "SUPERMARKET": 30,
    "HOSPITAL": 80,
    "GYM": 40,
    "HOUSE": 20,
    "MONUMENT": 10,
    "MUSEUM": 15,
}


def init_skills_tables():
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_skills (
                user_id TEXT NOT NULL,
                skill_id TEXT NOT NULL,
                level INTEGER DEFAULT 0,
                PRIMARY KEY (user_id, skill_id)
            )
        """)
        conn.commit()
    finally:
        put_conn(conn)


def get_user_skills(user_id):
    """Restituisce le capacità dell'utente come dict {skill_id: level}."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT skill_id, level FROM user_skills WHERE user_id=%s", (user_id,))
        rows = cur.fetchall()
        return {r["skill_id"]: r["level"] for r in rows}
    finally:
        put_conn(conn)


def add_skill_xp(user_id, skill_id, amount):
    """Aggiunge XP alla skill. Ritorna il nuovo livello."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT level FROM user_skills WHERE user_id=%s AND skill_id=%s", (user_id, skill_id))
        row = cur.fetchone()
        level = row["level"] if row else 0
        level += amount
        cur.execute(
            "INSERT INTO user_skills (user_id, skill_id, level) VALUES (%s, %s, %s) "
            "ON CONFLICT (user_id, skill_id) DO UPDATE SET level=%s",
            (user_id, skill_id, level, level),
        )
        conn.commit()
        return level
    finally:
        put_conn(conn)


def calculate_work_reward(user_id, building_type):
    """Calcola la paga considerando energia, umore e capacità."""
    from reallife.store import get_needs
    skills = get_user_skills(user_id)

    skill_info = BUILDING_SKILLS.get(building_type, {"skill": "quotidiano", "min_level": 1, "work_reward": 15})
    skill_name = skill_info["skill"]
    base_reward = skill_info["work_reward"]
    min_level = skill_info["min_level"]

    user_level = skills.get(skill_name, 0)
    is_trainee = user_level < min_level

    # Bonus capacità (fino al 100%)
    skill_bonus = min(1.0, user_level / 100.0) if user_level > 0 else 0.1

    # Penalty energia (usa "sleep" come energia)
    try:
        needs = get_needs(user_id, building_type)
        energy = needs.get("sleep", 50)
    except Exception:
        energy = 50
    energy_penalty = max(0.3, energy / 100.0)

    # Penalty umore (usa "fun" come proxy umore)
    try:
        mood = needs.get("fun", 50)
    except Exception:
        mood = 50
    mood_penalty = max(0.5, mood / 100.0)

    multiplier = skill_bonus * energy_penalty * mood_penalty
    if is_trainee:
        multiplier *= 0.3

    final_reward = max(1, int(base_reward * multiplier))
    return {
        "reward": final_reward,
        "base_reward": base_reward,
        "is_trainee": is_trainee,
        "skill_name": skill_name,
        "skill_level": user_level,
        "skill_bonus": round(skill_bonus, 2),
        "energy_penalty": round(energy_penalty, 2),
        "mood_penalty": round(mood_penalty, 2),
    }

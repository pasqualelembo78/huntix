"""
reallife/store.py — Persistenza e logica della Fase B di Huntix "Real Life".

Gestisce (tutto lato backend, DB Postgres condiviso):
  • world_state vivo: data/ora/stagione/meteo che avanzano nel tempo reale
  • bisogni stile Sims (fame/sonno/igiene/socialità/divertimento) con decay
    temporale e ricarica interagendo (chat) con i personaggi
  • skill dell'utente che sbloccano dialoghi/attività speciali con certi NPC
  • mappa/città 2D: posizioni dei personaggi su una griglia
"""
import logging
import random
from datetime import datetime, date, time, timedelta
from db import get_conn, put_conn

logger = logging.getLogger(__name__)

# Velocità del tempo di gioco: 1 secondo reale = N minuti di gioco.
# Con 6 -> un giorno di gioco passa ogni 4 minuti reali (città "viva").
GAME_MIN_PER_REAL_SEC = 6

SEASONS = ["Primavera", "Estate", "Autunno", "Inverno"]
WEATHERS = ["Soleggiato", "Nuvoloso", "Pioggia", "Neve", "Nebbia", " Temporale".strip()]

# Tasso di decay dei bisogni per ora reale (punti/ore). Clamp 0..100.
NEED_DECAY_PER_HOUR = {
    "hunger": 8.0,
    "sleep": 6.0,
    "hygiene": 4.0,
    "social": 5.0,
    "fun": 5.0,
}
NEED_START = 70.0

# Catalogo skill: ogni skill sblocca dialoghi/attività speciali con i personaggi
# i cui tag contengono `tag`. L'XP cresce interagendo con NPC affini.
SKILL_CATALOG = {
    "umorismo": {"name": "Umorismo", "desc": "Sblocca battute e prendere in giro affettuoso.", "tag": "Umorismo"},
    "empatia":  {"name": "Empatia", "desc": "Sblocca confidenze profonde e supporto.", "tag": "Lealtà"},
    "gaming":   {"name": "Gaming", "desc": "Sblocca sessioni di gioco e trash talk.", "tag": "Gaming"},
    "cucina":   {"name": "Cucina", "desc": "Sblocca ricette e pranzi insieme.", "tag": "Cucina"},
    "musica":   {"name": "Musica", "desc": "Sblocca concerti e playlist condivise.", "tag": "Musica"},
    "intelligenza": {"name": "Intelligenza", "desc": "Sblocca dibattiti e puzzle.", "tag": "Intelligenza"},
}
SKILL_XP_PER_LEVEL = 100


def init_reallife_tables():
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS reallife_world (
                id INTEGER PRIMARY KEY DEFAULT 1,
                game_date DATE NOT NULL,
                game_time TIME NOT NULL,
                season TEXT NOT NULL,
                weather TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cur.execute("""
            INSERT INTO reallife_world (id, game_date, game_time, season, weather)
            VALUES (1, %s, %s, %s, %s)
            ON CONFLICT (id) DO NOTHING
        """, (date(2025, 1, 1), time(8, 0), "Inverno", "Soleggiato"))

        cur.execute("""
            CREATE TABLE IF NOT EXISTS reallife_needs (
                user_id TEXT NOT NULL,
                character_id TEXT NOT NULL,
                hunger REAL DEFAULT 70,
                sleep REAL DEFAULT 70,
                hygiene REAL DEFAULT 70,
                social REAL DEFAULT 70,
                fun REAL DEFAULT 70,
                last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, character_id)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS reallife_skills (
                user_id TEXT NOT NULL,
                skill_id TEXT NOT NULL,
                level INTEGER DEFAULT 1,
                xp INTEGER DEFAULT 0,
                PRIMARY KEY (user_id, skill_id)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS reallife_map (
                character_id TEXT PRIMARY KEY,
                x REAL NOT NULL,
                y REAL NOT NULL,
                zone TEXT DEFAULT ''
            )
        """)
        conn.commit()
    finally:
        put_conn(conn)


# ── World State ──────────────────────────────────────────────────────────────
def _season_for_month(month: int) -> str:
    if 3 <= month <= 5:
        return "Primavera"
    if 6 <= month <= 8:
        return "Estate"
    if 9 <= month <= 11:
        return "Autunno"
    return "Inverno"


def get_world_state() -> dict:
    """Ritorna lo stato del mondo avanzando il tempo in base al reale trascorso."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT game_date, game_time, season, weather, updated_at FROM reallife_world WHERE id=1")
        row = cur.fetchone()
        if not row:
            init_reallife_tables()
            cur.execute("SELECT game_date, game_time, season, weather, updated_at FROM reallife_world WHERE id=1")
            row = cur.fetchone()

        gdate = row["game_date"]
        gtime = row["game_time"]
        season = row["season"]
        weather = row["weather"]
        last = row["updated_at"] or datetime.now()

        now = datetime.now()
        elapsed_sec = max(0.0, (now - last).total_seconds())
        add_min = int(elapsed_sec * GAME_MIN_PER_REAL_SEC)

        if add_min > 0:
            base_dt = datetime.combine(gdate, gtime)
            new_dt = base_dt + timedelta(minutes=add_min)
            new_date = new_dt.date()
            new_time = new_dt.time()
            rolled_day = new_date != gdate
            new_season = _season_for_month(new_dt.month)
            if rolled_day:
                weather = random.choice(WEATHERS)
                season = new_season
            elif new_season != season:
                season = new_season
            cur.execute(
                "UPDATE reallife_world SET game_date=%s, game_time=%s, season=%s, weather=%s, updated_at=%s WHERE id=1",
                (new_date, new_time, season, weather, now),
            )
            conn.commit()
            gdate, gtime, season, weather = new_date, new_time, season, weather

        return {
            "date": gdate.isoformat(),
            "time": gtime.strftime("%H:%M"),
            "season": season,
            "weather": weather,
        }
    finally:
        put_conn(conn)


# ── Needs (Sims) ─────────────────────────────────────────────────────────────
def _decay_needs(hunger, sleep, hygiene, social, fun, last: datetime) -> tuple:
    now = datetime.now()
    hours = (now - last).total_seconds() / 3600.0
    if hours <= 0:
        return hunger, sleep, hygiene, social, fun
    out = []
    for val, key in zip((hunger, sleep, hygiene, social, fun),
                        ("hunger", "sleep", "hygiene", "social", "fun")):
        val = val - NEED_DECAY_PER_HOUR[key] * hours
        out.append(max(0.0, min(100.0, val)))
    return tuple(out)


def get_needs(user_id: str, character_id: str) -> dict:
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT hunger, sleep, hygiene, social, fun, last_update FROM reallife_needs WHERE user_id=%s AND character_id=%s",
            (user_id, character_id),
        )
        row = cur.fetchone()
        if not row:
            cur.execute(
                "INSERT INTO reallife_needs (user_id, character_id) VALUES (%s, %s) "
                "ON CONFLICT (user_id, character_id) DO NOTHING",
                (user_id, character_id),
            )
            conn.commit()
            return {"user_id": user_id, "character_id": character_id,
                    "hunger": NEED_START, "sleep": NEED_START, "hygiene": NEED_START,
                    "social": NEED_START, "fun": NEED_START}
        hunger, sleep, hygiene, social, fun, last = row["hunger"], row["sleep"], row["hygiene"], row["social"], row["fun"], row["last_update"]
        hunger, sleep, hygiene, social, fun = _decay_needs(hunger, sleep, hygiene, social, fun, last)
        cur.execute(
            "UPDATE reallife_needs SET hunger=%s, sleep=%s, hygiene=%s, social=%s, fun=%s, last_update=%s "
            "WHERE user_id=%s AND character_id=%s",
            (hunger, sleep, hygiene, social, fun, datetime.now(), user_id, character_id),
        )
        conn.commit()
        return {"user_id": user_id, "character_id": character_id,
                "hunger": round(hunger, 1), "sleep": round(sleep, 1), "hygiene": round(hygiene, 1),
                "social": round(social, 1), "fun": round(fun, 1)}
    finally:
        put_conn(conn)


def recharge_needs(user_id: str, character_id: str, interaction: str = "chat") -> dict:
    """Ricarica i bisogni interagendo (parlare fa salire social/fun, un po' gli altri)."""
    gains = {"social": 15.0, "fun": 12.0, "hygiene": 3.0, "sleep": 2.0, "hunger": 5.0}
    if interaction == "activity":
        gains = {k: v * 1.6 for k, v in gains.items()}
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT hunger, sleep, hygiene, social, fun, last_update FROM reallife_needs WHERE user_id=%s AND character_id=%s",
            (user_id, character_id),
        )
        row = cur.fetchone()
        if not row:
            cur.execute(
                "INSERT INTO reallife_needs (user_id, character_id) VALUES (%s, %s) "
                "ON CONFLICT (user_id, character_id) DO NOTHING",
                (user_id, character_id),
            )
            conn.commit()
            cur.execute(
                "SELECT hunger, sleep, hygiene, social, fun, last_update FROM reallife_needs WHERE user_id=%s AND character_id=%s",
                (user_id, character_id),
            )
            row = cur.fetchone()
        hunger, sleep, hygiene, social, fun, last = row["hunger"], row["sleep"], row["hygiene"], row["social"], row["fun"], row["last_update"]
        hunger, sleep, hygiene, social, fun = _decay_needs(hunger, sleep, hygiene, social, fun, last)
        hunger = min(100.0, hunger + gains["hunger"])
        sleep = min(100.0, sleep + gains["sleep"])
        hygiene = min(100.0, hygiene + gains["hygiene"])
        social = min(100.0, social + gains["social"])
        fun = min(100.0, fun + gains["fun"])
        cur.execute(
            "UPDATE reallife_needs SET hunger=%s, sleep=%s, hygiene=%s, social=%s, fun=%s, last_update=%s "
            "WHERE user_id=%s AND character_id=%s",
            (hunger, sleep, hygiene, social, fun, datetime.now(), user_id, character_id),
        )
        conn.commit()
        return {"user_id": user_id, "character_id": character_id,
                "hunger": round(hunger, 1), "sleep": round(sleep, 1), "hygiene": round(hygiene, 1),
                "social": round(social, 1), "fun": round(fun, 1)}
    finally:
        put_conn(conn)


# ── Skills ─────────────────────────────────────────────────────────────────
def get_skills(user_id: str) -> dict:
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT skill_id, level, xp FROM reallife_skills WHERE user_id=%s", (user_id,))
        rows = cur.fetchall()
        owned = {r["skill_id"]: {"level": r["level"], "xp": r["xp"]} for r in rows}
        catalog = []
        for sid, meta in SKILL_CATALOG.items():
            o = owned.get(sid, {"level": 0, "xp": 0})
            catalog.append({
                "id": sid, "name": meta["name"], "desc": meta["desc"],
                "tag": meta["tag"], "level": o["level"], "xp": o["xp"],
                "unlocked": o["level"] > 0,
            })
        return {"user_id": user_id, "skills": catalog}
    finally:
        put_conn(conn)


def add_skill_xp(user_id: str, character_tags: list, amount: int = 20) -> list:
    """Aggiunge XP alle skill il cui tag appare nei tag del personaggio.
    Ritorna la lista di skill che sono salite di livello."""
    conn = get_conn()
    leveled_up = []
    try:
        cur = conn.cursor()
        tags = set(t.lower() for t in (character_tags or []))
        matched = [sid for sid, m in SKILL_CATALOG.items() if m["tag"].lower() in tags]
        if not matched:
            matched = ["intelligenza"]  # fallback generico
        for sid in matched:
            cur.execute(
                "SELECT level, xp FROM reallife_skills WHERE user_id=%s AND skill_id=%s",
                (user_id, sid),
            )
            row = cur.fetchone()
            if row:
                level, xp = row["level"], row["xp"]
            else:
                level, xp = 0, 0
            xp += amount
            while xp >= SKILL_XP_PER_LEVEL:
                xp -= SKILL_XP_PER_LEVEL
                level += 1
                leveled_up.append(sid)
            cur.execute(
                "INSERT INTO reallife_skills (user_id, skill_id, level, xp) VALUES (%s, %s, %s, %s) "
                "ON CONFLICT (user_id, skill_id) DO UPDATE SET level=%s, xp=%s",
                (user_id, sid, level, xp, level, xp),
            )
        conn.commit()
        return leveled_up
    finally:
        put_conn(conn)


# ── Map (città 2D) ────────────────────────────────────────────────────────
def _seed_map():
    """Assegna posizioni deterministiche ai personaggi su una griglia 100x100."""
    from characters import list_characters
    chars = list_characters()
    if not chars:
        return
    conn = get_conn()
    try:
        cur = conn.cursor()
        cats = []
        for c in chars:
            cat = (c.get("category") or "altro")
            if cat not in cats:
                cats.append(cat)
        n = max(1, len(cats))
        for c in chars:
            cid = c.get("id")
            cat = c.get("category") or "altro"
            zone_idx = cats.index(cat)
            # zona = fascia verticale; posizione pseudo-casuale ma stabile (hash id)
            h = abs(hash(cid)) % 10000
            x = 8 + (h % 84)
            y = 8 + (((zone_idx + 1) * (h % 84)) % 84)
            cur.execute(
                "INSERT INTO reallife_map (character_id, x, y, zone) VALUES (%s, %s, %s, %s) "
                "ON CONFLICT (character_id) DO NOTHING",
                (cid, x, y, cat),
            )
        conn.commit()
    finally:
        put_conn(conn)


def get_map_state() -> dict:
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) AS n FROM reallife_map")
        if (cur.fetchone() or {}).get("n", 0) == 0:
            _seed_map()
        cur.execute("SELECT character_id, x, y, zone FROM reallife_map")
        rows = cur.fetchall()
        from characters import get_character
        nodes = []
        for r in rows:
            ch = get_character(r["character_id"]) or {}
            nodes.append({
                "id": r["character_id"], "x": float(r["x"]), "y": float(r["y"]),
                "zone": r["zone"],
                "name": ch.get("name", r["character_id"]),
                "avatar": ch.get("avatar", "🙂"),
                "category": ch.get("category", r["zone"]),
            })
        return {"width": 100, "height": 100, "nodes": nodes}
    finally:
        put_conn(conn)

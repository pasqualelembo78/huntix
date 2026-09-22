"""
City Realtime Server — multiplayer real-time per MiaCitta.

Gestisce il namespace Socket.IO /city:
  - Riceve posizioni dai client
  - Mantiene lo stato di tutti i player connessi
  - Relay le posizioni ai client della stessa zona
  - Cleanup automatico dei player inattivi

Usage in app.py:
    from city_realtime import register_city_realtime_handlers, city_http_router
    register_city_realtime_handlers(sio)
    app.include_router(city_http_router)
"""

import asyncio
import logging
import time
from collections import defaultdict

from fastapi import APIRouter, Query

from auth_fastapi import socket_authenticate
from db import get_conn, put_conn

logger = logging.getLogger(__name__)

# ─── Costanti ────────────────────────────────────────────────────

GRID_SIZE = 100.0          # metri per lato della zona
TIMEOUT_SEC = 30.0         # secondi senza update prima del disconnect
CLEANUP_INTERVAL = 10.0    # secondi tra un cleanup e l'altro
VISIBLE_RADIUS_ZONES = 1   # 1 = zona corrente + 1 anello = 3x3 = 9 zone

# Coordinate di riferimento (WorldOrigin del progetto Unity).
# Qui si puo' modificare per puntare al centro della mappa che si usa.
LAT_ORIGIN = 41.12
LON_ORIGIN = 16.87

# ─── Stato in memoria ────────────────────────────────────────────

# user_id -> stato player
city_players = {}

# sid -> user_id (per questo namespace)
_city_sessions = {}

# zone_key -> set di user_id
_zone_members = defaultdict(set)


# ─── Zone ─────────────────────────────────────────────────────────

def zone_key(lat, lon):
    """Converte coordinate GPS in chiave zona (stringa 'x_y')."""
    x = int((lon - LON_ORIGIN) * 111000.0 / GRID_SIZE)
    y = int((lat - LAT_ORIGIN) * 111000.0 / GRID_SIZE)
    return f"{x}_{y}"


def visible_zones(center_zone):
    """Ritorna il set di zone visibili dalla zona centrale (3x3)."""
    parts = center_zone.split("_")
    cx, cy = int(parts[0]), int(parts[1])
    zones = set()
    for dx in range(-VISIBLE_RADIUS_ZONES, VISIBLE_RADIUS_ZONES + 1):
        for dy in range(-VISIBLE_RADIUS_ZONES, VISIBLE_RADIUS_ZONES + 1):
            zones.add(f"{cx + dx}_{cy + dy}")
    return zones


def _players_in_zones(zones):
    """Ritorna i player che si trovano nelle zone date (dict uid -> stato)."""
    result = {}
    for z in zones:
        for pid in _zone_members.get(z, set()):
            if pid in city_players and pid not in result:
                result[pid] = city_players[pid]
    return result


def _player_payload(pid, p):
    """Costruisce il payload di un player per il client."""
    now = time.time()
    return {
        "id": pid,
        "username": p.get("username", "Giocatore"),
        "level": p.get("level", 1),
        "skin": p.get("skin", "humanMaleA"),
        "lat": p.get("lat", 0.0),
        "lon": p.get("lon", 0.0),
        "heading": p.get("heading", 0.0),
        "anim_state": p.get("anim_state", "idle"),
        "vehicle_code": p.get("vehicle_code", ""),
        "pet": p.get("pet", "none"),
        # "x secondi fa" come presenza (0 = ora): per il cliente "in linea".
        "last_seen_sec": max(0, int(now - p.get("last_update", now))),
    }


# ─── Helpers DB ──────────────────────────────────────────────────

def _init_city_table():
    """Crea la tabella city_active_players se non esiste."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_active_players (
                    user_id      TEXT PRIMARY KEY,
                    username     TEXT NOT NULL DEFAULT 'Giocatore',
                    level        INT DEFAULT 1,
                    skin         TEXT DEFAULT 'humanMaleA',
                    pet          TEXT DEFAULT 'none',
                    lat          DOUBLE PRECISION,
                    lon          DOUBLE PRECISION,
                    heading      REAL DEFAULT 0,
                    zone         TEXT,
                    last_update  TIMESTAMP DEFAULT NOW(),
                    connected_at TIMESTAMP DEFAULT NOW()
                )
            """)
            cur.execute("ALTER TABLE city_active_players ADD COLUMN IF NOT EXISTS pet TEXT DEFAULT 'none'")
            conn.commit()
        finally:
            put_conn(conn)
        logger.info("[CityRealtime] Tabella city_active_players pronta")
    except Exception as e:
        logger.error(f"[CityRealtime] Errore creazione tabella: {e}")
def _init_wallet_table():
    """Crea la tabella city_wallet per il saldo server-side (FASE 6)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_wallet (
                    user_id     TEXT PRIMARY KEY,
                    money       INT DEFAULT 300,
                    updated_at  TIMESTAMP DEFAULT NOW()
                )
            """)
            conn.commit()
        finally:
            put_conn(conn)
        logger.info("[CityRealtime] Tabella city_wallet pronta")
    except Exception as e:
        logger.error(f"[CityRealtime] Errore creazione tabella wallet: {e}")


def _db_get_wallet_money(user_id):
    """Ritorna il saldo wallet del player (seed 300 se assente), None su errore DB."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT money FROM city_wallet WHERE user_id = %s", (user_id,))
            row = cur.fetchone()
            if row is None:
                cur.execute(
                    "INSERT INTO city_wallet (user_id, money, updated_at) VALUES (%s, 300, NOW()) "
                    "ON CONFLICT (user_id) DO NOTHING", (user_id,))
                conn.commit()
                return 300
            return int(row["money"])
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB get wallet fallito per {user_id}: {e}")
        return None


def _db_set_wallet_money(user_id, money):
    """Scrive il saldo wallet del player (best-effort)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO city_wallet (user_id, money, updated_at)
                VALUES (%s, %s, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    money = EXCLUDED.money,
                    updated_at = NOW()
            """, (user_id, int(max(0, money))))
            conn.commit()
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB set wallet fallito per {user_id}: {e}")


def _init_chat_table():
    """Crea la tabella city_msgs per la chat tra giocatori (relay P2P)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_msgs (
                    id          BIGSERIAL PRIMARY KEY,
                    from_user   TEXT NOT NULL,
                    to_user     TEXT NOT NULL,
                    text        TEXT NOT NULL,
                    read        BOOLEAN DEFAULT FALSE,
                    created_at  TIMESTAMP DEFAULT NOW()
                )
            """)
            cur.execute("CREATE INDEX IF NOT EXISTS idx_city_msgs_to ON city_msgs(to_user, read)")
            conn.commit()
        finally:
            put_conn(conn)
        logger.info("[CityRealtime] Tabella city_msgs pronta")
    except Exception as e:
        logger.error(f"[CityRealtime] Errore creazione tabella chat: {e}")


def _db_send_msg(from_user, to_user, text):
    """Inserisce un messaggio di chat (best-effort). True su ok."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute(
                "INSERT INTO city_msgs (from_user, to_user, text, created_at) "
                "VALUES (%s, %s, %s, NOW())",
                (from_user, to_user, text[:500]))
            conn.commit()
            return True
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB send msg fallito: {e}")
        return False


def _db_pull_inbox(user_id, limit=50, peek=False):
    """Ritorna i messaggi non ancora letti indirizzati a user_id. Con
    peek=False (default) li marca come letti (pull-and-clear, adatto al
    polling HTTP consumante); con peek=True li lascia leggibili anche a un
    secondo consumatore (il client Unity notifica in citta' senza rubarli
    all'activity di chat). Include il nome visuale del mittente quando
    presente."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT m.id, m.from_user, m.text, m.created_at, a.username
                FROM city_msgs m
                LEFT JOIN city_active_players a ON a.user_id = m.from_user
                WHERE m.to_user = %s AND m.read = FALSE
                ORDER BY m.id ASC
                LIMIT %s
            """, (user_id, limit))
            rows = cur.fetchall()
            if not peek:
                ids = [r["id"] for r in rows]
                if ids:
                    cur.execute(
                        "UPDATE city_msgs SET read = TRUE WHERE id = ANY(%s)",
                        (ids,))
            conn.commit()
            return [
                {"from": r["from_user"],
                 "from_name": r["username"] or "",
                 "text": r["text"],
                 "ts": str(r["created_at"]) if r["created_at"] else ""}
                for r in rows
            ]
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB inbox fallito per {user_id}: {e}")
        return []


def _db_chat_history(user_id, other_user, limit=50):
    """Storia della conversazione tra due giocatori (entrambe le direzioni,
    anche i gia' letti), dal piu' vecchio al piu' recente. Ogni messaggio
    indica se e' "mine" (per renderizzarlo correttamente nella chat)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT m.from_user, m.text, m.created_at, a.username
                FROM (
                    SELECT from_user, text, created_at, to_user
                    FROM city_msgs
                    WHERE (from_user = %s AND to_user = %s)
                       OR (from_user = %s AND to_user = %s)
                    ORDER BY id DESC
                    LIMIT %s
                ) m
                LEFT JOIN city_active_players a ON a.user_id = m.from_user
                ORDER BY m.created_at ASC, m.text ASC
            """, (user_id, other_user, other_user, user_id, limit))
            rows = cur.fetchall()
            # Aprire la conversazione = leggere i messaggi dell'altro (badge rubrica).
            cur.execute(
                "UPDATE city_msgs SET read = TRUE WHERE to_user = %s AND from_user = %s",
                (user_id, other_user))
            conn.commit()
            return [
                {"from": r["from_user"],
                 "from_name": r["username"] or "",
                 "mine": r["from_user"] == user_id,
                 "text": r["text"],
                 "ts": str(r["created_at"]) if r["created_at"] else ""}
                for r in rows
            ]
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB storia chat fallita: {e}")
        return []


# Rate-limit chat: massimo un messaggio ogni 2s per coppia (from,to).
_CHAT_RATE_WINDOW = 2.0
_chat_rate = {}


def _chat_rate_ok(from_user, to_user):
    now = time.time()
    key = (from_user, to_user)
    try:
        last = _chat_rate.get(key, 0.0)
        if now - last < _CHAT_RATE_WINDOW:
            return False
        _chat_rate[key] = now
        # pulizia pigra: cancella voci vecchie oltre la finestra
        if len(_chat_rate) > 500:
            for k in list(_chat_rate):
                if now - _chat_rate[k] > _CHAT_RATE_WINDOW * 4:
                    del _chat_rate[k]
        return True
    except Exception:
        return True


# ─── Rubrica / presenza ─────────────────────────────────────────

def _db_conversations(user_id, limit=50):
    """Rubrica: per ogni altro giocatore con cui si e' parlato, l'ultimo
    messaggio, il timestamp e il numero di non letti."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT CASE WHEN from_user = %s THEN to_user ELSE from_user END AS other,
                       MAX(id) AS last_id,
                       COUNT(*) FILTER (WHERE to_user = %s AND read = FALSE) AS unread
                FROM city_msgs
                WHERE from_user = %s OR to_user = %s
                GROUP BY CASE WHEN from_user = %s THEN to_user ELSE from_user END
                ORDER BY last_id DESC
                LIMIT %s
            """, (user_id, user_id, user_id, user_id, user_id, limit))
            rows = cur.fetchall()
            contacts = []
            for r in rows:
                other = r["other"]
                cur.execute(
                    "SELECT username FROM city_active_players WHERE user_id = %s",
                    (other,))
                name_row = cur.fetchone()
                name = (name_row["username"] or "") if name_row else ""
                last_text, last_ts = "", ""
                cur.execute("SELECT text, created_at FROM city_msgs WHERE id = %s",
                            (r["last_id"],))
                m = cur.fetchone()
                if m:
                    last_text = m["text"]
                    last_ts = str(m["created_at"]) if m["created_at"] else ""
                contacts.append({
                    "other_id": other,
                    "other_name": name or "Giocatore",
                    "last_text": last_text,
                    "last_ts": last_ts,
                    "unread": int(r["unread"] or 0),
                })
            conn.commit()
            return contacts
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB rubrica fallita per {user_id}: {e}")
        return []


def _db_had_thread(user_id, other):
    """True se esiste almeno un messaggio tra i due giocatori (qualsiasi lato)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT 1 FROM city_msgs
                WHERE (from_user = %s AND to_user = %s)
                   OR (from_user = %s AND to_user = %s)
                LIMIT 1
            """, (user_id, other, other, user_id))
            return cur.fetchone() is not None
        finally:
            put_conn(conn)
    except Exception:
        return False


def _db_player_row(user_id):
    """Riga city_active_players (presenza offline), best-effort."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT user_id, username, level, skin, lat, lon, last_update
                FROM city_active_players WHERE user_id = %s
            """, (user_id,))
            return cur.fetchone()
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB player row fallita per {user_id}: {e}")
        return None


# ─── Party / gruppi di amici ────────────────────────────────────

def _init_party_tables():
    """Crea le tabelle per il party (gruppo di amici in citta')."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_parties (
                    id         BIGSERIAL PRIMARY KEY,
                    leader     TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT NOW()
                )
            """)
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_party_members (
                    party_id  BIGINT NOT NULL,
                    user_id   TEXT NOT NULL,
                    joined_at TIMESTAMP DEFAULT NOW(),
                    PRIMARY KEY (party_id, user_id)
                )
            """)
            # un giocatore puo' stare in una sola party
            cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_party_member_user "
                        "ON city_party_members(user_id)")
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_party_invites (
                    id         BIGSERIAL PRIMARY KEY,
                    party_id   BIGINT NOT NULL,
                    from_user  TEXT NOT NULL,
                    to_user    TEXT NOT NULL,
                    status     TEXT DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT NOW()
                )
            """)
            cur.execute("CREATE INDEX IF NOT EXISTS idx_party_invites_to "
                        "ON city_party_invites(to_user, status)")
            conn.commit()
        finally:
            put_conn(conn)
        logger.info("[CityRealtime] Tabelle party pronte")
    except Exception as e:
        logger.error(f"[CityRealtime] Errore creazione tabelle party: {e}")


def _db_create_party(user_id):
    """Crea una party con user_id come leader. Se e' gia' in una, la riusa."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT party_id FROM city_party_members WHERE user_id = %s",
                        (user_id,))
            row = cur.fetchone()
            if row:
                return row["party_id"]
            cur.execute(
                "INSERT INTO city_parties (leader, created_at) VALUES (%s, NOW()) RETURNING id",
                (user_id,))
            nid = cur.fetchone()["id"]
            cur.execute(
                "INSERT INTO city_party_members (party_id, user_id) VALUES (%s, %s)",
                (nid, user_id))
            conn.commit()
            return nid
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB crea party fallita per {user_id}: {e}")
        return None


def _db_get_party_by_user(user_id):
    """party_id della party di user_id (o None)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT party_id FROM city_party_members WHERE user_id = %s",
                        (user_id,))
            row = cur.fetchone()
            return row["party_id"] if row else None
        finally:
            put_conn(conn)
    except Exception:
        return None


def _db_is_member(party_id, user_id):
    """True se user_id e' membro della party."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute(
                "SELECT 1 FROM city_party_members WHERE party_id = %s AND user_id = %s",
                (party_id, user_id))
            return cur.fetchone() is not None
        finally:
            put_conn(conn)
    except Exception:
        return False


def _db_invite_to_party(party_id, from_user, to_user):
    """Crea un invito in sospeso per to_user."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO city_party_invites (party_id, from_user, to_user, status)
                VALUES (%s, %s, %s, 'pending')
            """, (party_id, from_user, to_user))
            conn.commit()
            return True
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB invite party fallita: {e}")
        return False


def _db_pending_invites(user_id):
    """Inviti in sospeso ricevuti da user_id."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT i.party_id, i.from_user, a.username AS from_name, i.created_at
                FROM city_party_invites i
                LEFT JOIN city_active_players a ON a.user_id = i.from_user
                WHERE i.to_user = %s AND i.status = 'pending'
                ORDER BY i.id DESC
                LIMIT 20
            """, (user_id,))
            rows = cur.fetchall()
            conn.commit()
            return [
                {"party_id": r["party_id"],
                 "from_user": r["from_user"],
                 "from_name": r["from_name"] or "Giocatore",
                 "ts": str(r["created_at"]) if r["created_at"] else ""}
                for r in rows
            ]
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB pending invites fallita: {e}")
        return []


def _db_decline_party(party_id, user_id):
    """Rifiuta un invito (lo smaltisce, senza spam futuro)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                UPDATE city_party_invites SET status = 'declined'
                WHERE party_id = %s AND to_user = %s AND status = 'pending'
            """, (party_id, user_id))
            conn.commit()
            return True
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB decline party fallita: {e}")
        return False


def _db_accept_party(party_id, user_id):
    """Entra nella party (lasciando un'eventuale altra). Consuma gli inviti."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT party_id FROM city_party_members WHERE user_id = %s",
                        (user_id,))
            row = cur.fetchone()
            if row and row["party_id"] != party_id:
                # lascia la party attuale (se leader: la dissolvera' _db_leave_party)
                _db_leave_party(user_id)
            cur.execute("""
                INSERT INTO city_party_members (party_id, user_id)
                VALUES (%s, %s) ON CONFLICT DO NOTHING
            """, (party_id, user_id))
            cur.execute("""
                UPDATE city_party_invites SET status = 'accepted'
                WHERE party_id = %s AND to_user = %s AND status = 'pending'
            """, (party_id, user_id))
            conn.commit()
            return True
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB accept party fallita: {e}")
        return False


def _db_leave_party(user_id):
    """Esce dalla party; se il leader esce, la party si scioglie."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT party_id FROM city_party_members WHERE user_id = %s",
                        (user_id,))
            row = cur.fetchone()
            if row is None:
                return True
            party_id = row["party_id"]
            cur.execute("SELECT leader FROM city_parties WHERE id = %s", (party_id,))
            p = cur.fetchone()
            if p and p["leader"] == user_id:
                cur.execute("DELETE FROM city_party_members WHERE party_id = %s", (party_id,))
                cur.execute("DELETE FROM city_party_invites WHERE party_id = %s", (party_id,))
                cur.execute("DELETE FROM city_parties WHERE id = %s", (party_id,))
            else:
                cur.execute("DELETE FROM city_party_members WHERE user_id = %s", (user_id,))
            conn.commit()
            return True
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB leave party fallita per {user_id}: {e}")
        return False


def _db_party_state(user_id):
    """Stato della party di user_id (membri + leader), o None se non in una."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("SELECT party_id FROM city_party_members WHERE user_id = %s",
                        (user_id,))
            row = cur.fetchone()
            if row is None:
                return None
            party_id = row["party_id"]
            cur.execute("SELECT leader FROM city_parties WHERE id = %s", (party_id,))
            p = cur.fetchone()
            now = time.time()
            cur.execute("""
                SELECT m.user_id, a.username, a.level, a.skin, a.lat, a.lon, a.last_update
                FROM city_party_members m
                LEFT JOIN city_active_players a ON a.user_id = m.user_id
                WHERE m.party_id = %s
            """, (party_id,))
            members = []
            for m in cur.fetchall():
                lu = m["last_update"]
                last_seen = max(0, int(now - lu.timestamp())) if lu else 0
                members.append({
                    "id": m["user_id"],
                    "username": m["username"] or "Giocatore",
                    "level": m["level"] or 1,
                    "skin": m["skin"] or "humanMaleA",
                    "lat": m["lat"],
                    "lon": m["lon"],
                    "last_seen_sec": last_seen,
                })
            conn.commit()
            return {
                "party_id": party_id,
                "leader": p["leader"] if p else "",
                "members": members,
            }
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB party state fallita per {user_id}: {e}")
        return None


# ─── Regali fra giocatori (uova e gemme) ───────────────────────

def _init_gift_table():
    """Crea la tabella city_gifts (ledger P2P: uova e gemme)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                CREATE TABLE IF NOT EXISTS city_gifts (
                    id         BIGSERIAL PRIMARY KEY,
                    from_user  TEXT NOT NULL,
                    to_user    TEXT NOT NULL,
                    kind       TEXT NOT NULL,
                    egg_id     TEXT,
                    rarity     TEXT,
                    egg_name   TEXT,
                    amount     INT,
                    status     TEXT DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT NOW(),
                    claimed_at TIMESTAMP
                )
            """)
            cur.execute("CREATE INDEX IF NOT EXISTS idx_city_gifts_to "
                        "ON city_gifts(to_user, status)")
            conn.commit()
        finally:
            put_conn(conn)
        logger.info("[CityRealtime] Tabella city_gifts pronta")
    except Exception as e:
        logger.error(f"[CityRealtime] Errore creazione tabella gifts: {e}")


def _db_send_gift(from_user, to_user, kind, egg_id, rarity, egg_name, amount):
    """Registra un regalo in sospeso. Ritorna l'id o None."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO city_gifts
                    (from_user, to_user, kind, egg_id, rarity, egg_name, amount, status)
                VALUES (%s, %s, %s, %s, %s, %s, %s, 'pending')
                RETURNING id
            """, (from_user, to_user, kind, egg_id, rarity, egg_name, amount))
            gid = cur.fetchone()["id"]
            conn.commit()
            return gid
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB send gift fallito: {e}")
        return None


def _db_pending_gifts(user_id, limit=50):
    """Regali in sospeso indirizzati a user_id (nome mittente incluso)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT g.id, g.from_user, g.kind, g.egg_id, g.rarity, g.egg_name,
                       g.amount, g.created_at, a.username
                FROM city_gifts g
                LEFT JOIN city_active_players a ON a.user_id = g.from_user
                WHERE g.to_user = %s AND g.status = 'pending'
                ORDER BY g.id DESC
                LIMIT %s
            """, (user_id, limit))
            rows = cur.fetchall()
            conn.commit()
            return [
                {"id": r["id"],
                 "from_user": r["from_user"],
                 "from_name": r["username"] or "Giocatore",
                 "kind": r["kind"],
                 "egg_id": r["egg_id"],
                 "rarity": r["rarity"],
                 "egg_name": r["egg_name"],
                 "amount": r["amount"],
                 "ts": str(r["created_at"]) if r["created_at"] else ""}
                for r in rows
            ]
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB pending gifts fallita per {user_id}: {e}")
        return []


def _db_claim_gift(gift_id, user_id):
    """Riscatta un regalo (solo se pendente e destinato a user_id).
    Ritorna il dettaglio del regalo, o None se gia' riscattato/estraneo."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                UPDATE city_gifts SET status = 'claimed', claimed_at = NOW()
                WHERE id = %s AND to_user = %s AND status = 'pending'
                RETURNING id, from_user, kind, egg_id, rarity, egg_name, amount, created_at
            """, (gift_id, user_id))
            row = cur.fetchone()
            conn.commit()
            if row is None:
                return None
            return {
                "id": row["id"],
                "from_user": row["from_user"],
                "kind": row["kind"],
                "egg_id": row["egg_id"],
                "rarity": row["rarity"],
                "egg_name": row["egg_name"],
                "amount": row["amount"],
                "ts": str(row["created_at"]) if row["created_at"] else "",
            }
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB claim gift fallito: {e}")
        return None




def _db_upsert_player(user_id, username, level, skin, pet, lat, lon, heading, zone):
    """Inserisce o aggiorna un player nella tabella (best-effort)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO city_active_players
                    (user_id, username, level, skin, pet, lat, lon, heading, zone, last_update)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    username = EXCLUDED.username,
                    level = EXCLUDED.level,
                    skin = EXCLUDED.skin,
                    pet = EXCLUDED.pet,
                    lat = EXCLUDED.lat,
                    lon = EXCLUDED.lon,
                    heading = EXCLUDED.heading,
                    zone = EXCLUDED.zone,
                    last_update = NOW()
            """, (user_id, username, level, skin, pet, lat, lon, heading, zone))
            conn.commit()
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB upsert fallito per {user_id}: {e}")


def _db_remove_player(user_id):
    """Rimuove un player dalla tabella (best-effort)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("DELETE FROM city_active_players WHERE user_id = %s", (user_id,))
            conn.commit()
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB remove fallito per {user_id}: {e}")


# ─── Cleanup periodico ───────────────────────────────────────────

async def _cleanup_loop(sio):
    """Rimuove i player inattivi ogni CLEANUP_INTERVAL secondi."""
    while True:
        await asyncio.sleep(CLEANUP_INTERVAL)
        now = time.time()
        stale = [
            pid for pid, p in city_players.items()
            if now - p.get("last_update", 0) > TIMEOUT_SEC
        ]
        for pid in stale:
            p = city_players.pop(pid, None)
            if not p:
                continue
            old_zone = p.get("zone")
            if old_zone:
                _purge_zones(pid, old_zone)
                _notify_zone(sio, old_zone, "city:player_leave",
                             {"id": pid}, exclude_pid=pid)
            _city_sessions.pop(p.get("sid"), None)
            _db_remove_player(pid)
            logger.info(f"[CityRealtime] Player timeout: {p.get('username', pid)}")


def _purge_zones(pid, zone):
    """Rimuove un player dalle zone (usato al leave/timeout)."""
    for z in list(_zone_members.keys()):
        _zone_members[z].discard(pid)
        if not _zone_members[z]:
            del _zone_members[z]


def _notify_zone(sio, zone, event, data, exclude_pid=None):
    """Manda un evento a tutti i player che vedono la zona."""
    vis = visible_zones(zone)
    for z in vis:
        for pid in _zone_members.get(z, set()):
            if pid == exclude_pid:
                continue
            p = city_players.get(pid)
            if p:
                try:
                    sio.emit(event, data, room=p["sid"], namespace="/city")
                except Exception:
                    pass


# ─── Registrazione handler Socket.IO ─────────────────────────────

def register_city_realtime_handlers(sio):
    """Registra tutti gli handler Socket.IO per il namespace /city."""

    _init_city_table()
    _init_wallet_table()
    _init_chat_table()
    _init_party_tables()
    _init_gift_table()

    # Avvia il cleanup loop all'avvio del server
    try:
        loop = asyncio.get_event_loop()
        loop.create_task(_cleanup_loop(sio))
    except Exception:
        pass

    # ── connect / disconnect ──────────────────────────────────

    @sio.on("connect", namespace="/city")
    async def connect(sid, environ, auth):
        """Client si connette al namespace /city. L'autenticazione
        avviene esplicitamente con city:join (qui solo accettiamo)."""
        logger.debug(f"[CityRealtime] Client connesso al namespace /city: {sid}")

    @sio.on("disconnect", namespace="/city")
    async def disconnect(sid):
        """Client disconnesso: pulisci stato."""
        pid = _city_sessions.pop(sid, None)
        if not pid:
            return
        p = city_players.pop(pid, None)
        if not p:
            return
        old_zone = p.get("zone")
        if old_zone:
            _purge_zones(pid, old_zone)
            _notify_zone(sio, old_zone, "city:player_leave",
                         {"id": pid}, exclude_pid=pid)
        _db_remove_player(pid)
        logger.info(f"[CityRealtime] Player disconnesso: {p.get('username', pid)}")

    # ── city:join ─────────────────────────────────────────────

    @sio.on("city:join", namespace="/city")
    async def on_city_join(sid, data):
        """Il client entra nel mondo citta'. Richiede JWT valido."""
        if not isinstance(data, dict):
            data = {}

        token = data.get("token", "")
        payload = socket_authenticate(token)
        if not payload:
            await sio.emit("city:error",
                           {"message": "Token non valido"},
                           room=sid, namespace="/city")
            return

        user_id = payload["user_id"]
        username = data.get("username", "Giocatore")
        level = data.get("level", 1)
        skin = data.get("skin", "humanMaleA")
        pet = data.get("pet", "none")
        lat = float(data.get("lat", LAT_ORIGIN))
        lon = float(data.get("lon", LON_ORIGIN))
        heading = float(data.get("heading", 0.0))

        # Se il player era gia' connesso (reconnect), pulisci il vecchio
        if user_id in city_players:
            old = city_players[user_id]
            _city_sessions.pop(old.get("sid"), None)
            _purge_zones(user_id, old.get("zone"))

        # Registra il player
        new_zone = zone_key(lat, lon)
        city_players[user_id] = {
            "sid": sid,
            "username": username,
            "level": level,
            "skin": skin,
            "pet": pet,
            "lat": lat,
            "lon": lon,
            "heading": heading,
            "anim_state": "idle",
            "vehicle_code": "",
            "zone": new_zone,
            "last_update": time.time(),
        }
        _city_sessions[sid] = user_id
        _zone_members[new_zone].add(user_id)

        # Invia la lista dei player visibili al giocatore appena entrato
        vis_zones = visible_zones(new_zone)
        players_here = _players_in_zones(vis_zones)
        players_list = [
            _player_payload(pid, p)
            for pid, p in players_here.items()
            if pid != user_id
        ]

        await sio.emit("city:welcome", {
            "player_id": user_id,
            "players": players_list,
        }, room=sid, namespace="/city")

        # Notifica gli altri player nella zona
        _notify_zone(sio, new_zone, "city:player_join",
                     _player_payload(user_id, city_players[user_id]),
                     exclude_pid=user_id)

        # Salva su DB
        _db_upsert_player(user_id, username, level, skin, pet, lat, lon, heading, new_zone)

        logger.info(f"[CityRealtime] {username} ({user_id}) entrato in zona {new_zone}")

    # ── city:move ─────────────────────────────────────────────

    @sio.on("city:move", namespace="/city")
    async def on_city_move(sid, data):
        """Il client aggiorna la propria posizione."""
        pid = _city_sessions.get(sid)
        if not pid or pid not in city_players:
            return
        if not isinstance(data, dict):
            return

        p = city_players[pid]
        lat = float(data.get("lat", p["lat"]))
        lon = float(data.get("lon", p["lon"]))
        heading = float(data.get("heading", p["heading"]))
        anim_state = data.get("anim_state", p.get("anim_state", "idle"))

        old_zone = p["zone"]
        new_zone = zone_key(lat, lon)

        # Aggiorna stato
        p["lat"] = lat
        p["lon"] = lon
        p["heading"] = heading
        p["anim_state"] = anim_state
        p["last_update"] = time.time()

        # Se la zona e' cambiata, gestisci subscribe/unsubscribe
        if new_zone != old_zone:
            old_vis = visible_zones(old_zone)
            new_vis = visible_zones(new_zone)

            to_join = new_vis - old_vis
            to_leave = old_vis - new_vis

            # Notifica uscita alle zone che non vediamo piu'
            if to_leave:
                leave_data = {"id": pid}
                for z in to_leave:
                    _notify_zone(sio, z, "city:player_leave",
                                 leave_data, exclude_pid=pid)

            # Rimuovi il player dalla vecchia zona fisica, aggiungi alla nuova
            _zone_members[old_zone].discard(pid)
            if not _zone_members[old_zone]:
                del _zone_members[old_zone]
            p["zone"] = new_zone
            _zone_members[new_zone].add(pid)

            # Notifica ingresso nelle zone nuove
            if to_join:
                join_data = _player_payload(pid, p)
                for z in to_join:
                    _notify_zone(sio, z, "city:player_join",
                                 join_data, exclude_pid=pid)

                # Invia anche i player gia' presenti nelle zone nuove
                new_players = _players_in_zones(to_join)
                for npid, np in new_players.items():
                    if npid != pid:
                        await sio.emit("city:player_join",
                                       _player_payload(npid, np),
                                       room=sid, namespace="/city")

        # Relay posizione ai vicini
        move_data = {
            "id": pid,
            "lat": lat,
            "lon": lon,
            "heading": heading,
            "anim_state": anim_state,
        }
        _notify_zone(sio, new_zone, "city:player_move",
                     move_data, exclude_pid=pid)

        # Aggiorna DB (throttle: ogni 5 secondi al massimo)
        now = time.time()
        if now - p.get("_last_db_update", 0) > 5.0:
            p["_last_db_update"] = now
            _db_upsert_player(pid, p["username"], p["level"],
                              p["skin"], p.get("pet", "none"), lat, lon, heading, new_zone)

    # ── city:vehicle ──────────────────────────────────────────

    @sio.on("city:vehicle", namespace="/city")
    async def on_city_vehicle(sid, data):
        """Il client entra/esce/parkeggia un veicolo."""
        pid = _city_sessions.get(sid)
        if not pid or pid not in city_players:
            return
        if not isinstance(data, dict):
            return

        p = city_players[pid]
        vehicle_code = data.get("vehicle_code", "")
        action = data.get("action", "enter")  # enter / exit / park

        p["vehicle_code"] = vehicle_code if action == "enter" else ""
        p["last_update"] = time.time()

        vehicle_data = {
            "id": pid,
            "vehicle_code": vehicle_code,
            "action": action,
        }
        _notify_zone(sio, p["zone"], "city:vehicle_update",
                     vehicle_data, exclude_pid=pid)

    # ── city:chat ─────────────────────────────────────────────

    @sio.on("city:chat", namespace="/city")
    async def on_city_chat(sid, data):
        """Messaggio chat globale (opzionale)."""
        pid = _city_sessions.get(sid)
        if not pid or pid not in city_players:
            return
        if not isinstance(data, dict):
            return

        text = str(data.get("text", ""))[:200]
        if not text:
            return

        p = city_players[pid]
        chat_data = {
            "id": pid,
            "username": p.get("username", "Giocatore"),
            "text": text,
        }
        _notify_zone(sio, p["zone"], "city:chat", chat_data)

    # ── city:leave ────────────────────────────────────────────

    @sio.on("city:leave", namespace="/city")
    async def on_city_leave(sid, data):
        """Il client lascia deliberatamente la citta'."""
        pid = _city_sessions.pop(sid, None)
        if not pid:
            return
        p = city_players.pop(pid, None)
        if not p:
            return
        old_zone = p.get("zone")
        if old_zone:
            _purge_zones(pid, old_zone)
            _notify_zone(sio, old_zone, "city:player_leave",
                         {"id": pid}, exclude_pid=pid)
        _db_remove_player(pid)
        logger.info(f"[CityRealtime] {p.get('username', pid)} ha lasciato la citta'")


# ─── Router HTTP (endpoint di debug/overlay) ─────────────────────

city_http_router = APIRouter(prefix="/api/city", tags=["city"])


@city_http_router.get("/players")
async def api_city_players(
    lat: float = Query(default=LAT_ORIGIN),
    lon: float = Query(default=LON_ORIGIN),
    radius: float = Query(default=500.0),
):
    """Ritorna i player attivi vicini a una posizione."""
    center = zone_key(lat, lon)
    vis = visible_zones(center)
    players = _players_in_zones(vis)
    return {
        "count": len(players),
        "center_zone": center,
        "players": [_player_payload(pid, p) for pid, p in players.items()],
    }


@city_http_router.post("/heartbeat")
async def api_city_heartbeat(req: dict):
    """Registra o aggiorna la posizione di un player arrivato via HTTP
    (client Unity senza client Socket.IO). Usa lo stesso stato condiviso
    di city_players, cosi' i client WebSocket e HTTP convivono nella stessa
    citta'."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON object richiesto"}

    pid = str(req.get("id", "")).strip()
    if not pid:
        return {"ok": False, "error": "campo 'id' mancante"}

    # Identita' preferita dal JWT (Fase 3): se il token e' valido si usa il
    # reale user_id invece dell'id device anonimo. username/level vengono presi
    # dal payload quando disponibili.
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if payload:
        pid = str(payload.get("user_id", pid))
        if not req.get("username"):
            req = dict(req)
            req["username"] = payload.get("username", "") or ""
        if "level" not in req:
            req = dict(req)
            req["level"] = payload.get("level", 1)

    lat = float(req.get("lat", LAT_ORIGIN))
    lon = float(req.get("lon", LON_ORIGIN))
    heading = float(req.get("heading", 0.0))
    anim_state = str(req.get("anim_state", "idle"))[:16]
    username = str(req.get("username", "Giocatore"))[:40]
    level = int(req.get("level", 1))
    skin = str(req.get("skin", "humanMaleA"))[:40]
    pet = str(req.get("pet", "none"))[:40]
    vehicle_code = str(req.get("vehicle_code", ""))[:40]
    last_seen = time.time()

    existing = city_players.get(pid)
    if existing is None:
        new_zone = zone_key(lat, lon)
        city_players[pid] = {
            "sid": None,          # non e' un client WebSocket
            "username": username,
            "level": level,
            "skin": skin,
            "pet": pet,
            "lat": lat,
            "lon": lon,
            "heading": heading,
            "anim_state": anim_state,
            "vehicle_code": vehicle_code,
            "zone": new_zone,
            "last_update": last_seen,
        }
        _zone_members[new_zone].add(pid)
        _db_upsert_player(pid, username, level, skin, pet, lat, lon, heading, new_zone)
        return {"ok": True, "new": True, "zone": new_zone, "id": pid}

    p = existing
    old_zone = p["zone"]
    p["lat"] = lat
    p["lon"] = lon
    p["heading"] = heading
    p["anim_state"] = anim_state
    p["username"] = username
    p["level"] = level
    p["skin"] = skin
    p["pet"] = pet
    p["vehicle_code"] = vehicle_code
    p["last_update"] = last_seen

    new_zone = zone_key(lat, lon)
    if new_zone != old_zone:
        _zone_members[old_zone].discard(pid)
        if not _zone_members[old_zone]:
            del _zone_members[old_zone]
        p["zone"] = new_zone
        _zone_members[new_zone].add(pid)

    # Throttle DB a 5s come per i client WebSocket
    if last_seen - p.get("_last_db_update", 0) > 5.0:
        p["_last_db_update"] = last_seen
        _db_upsert_player(pid, username, level, skin, pet, lat, lon, heading, new_zone)

    return {"ok": True, "new": False, "zone": new_zone, "id": pid}


@city_http_router.get("/stats")
async def api_city_stats():
    """Statistiche sulla citta' multiplayer."""
    return {
        "total_players": len(city_players),
        "zones_active": len([z for z, m in _zone_members.items() if m]),
        "players": [
            {"id": pid, "username": p.get("username"), "zone": p.get("zone")}
            for pid, p in city_players.items()
        ],
    }


@city_http_router.get("/wallet")
async def api_city_wallet_get(token: str = Query(default="")):
    """Saldo del wallet server-side del player autenticato (FASE 6)."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    money = _db_get_wallet_money(user_id)
    if money is None:
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True, "money": money}


@city_http_router.post("/wallet/spend")
async def api_city_wallet_spend(req: dict):
    """Addebita un importo sul wallet server-side. Il server e' authoritative:
    restituisce il nuovo saldo reale. L'importo non puo' portare il saldo
    sotto zero (si limita a 0)."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    try:
        amount = int(req.get("amount", 0))
    except (TypeError, ValueError):
        return {"ok": False, "error": "amount non valido"}
    if amount <= 0:
        return {"ok": False, "error": "amount deve essere > 0"}

    money = _db_get_wallet_money(user_id)
    if money is None:
        return {"ok": False, "error": "db non disponibile"}
    new_money = max(0, money - amount)
    _db_set_wallet_money(user_id, new_money)
    return {"ok": True, "money": new_money}


@city_http_router.post("/wallet/earn")
async def api_city_wallet_earn(req: dict):
    """Accredita un importo sul wallet server-side. Ritorna il nuovo saldo."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    try:
        amount = int(req.get("amount", 0))
    except (TypeError, ValueError):
        return {"ok": False, "error": "amount non valido"}
    if amount <= 0:
        return {"ok": False, "error": "amount deve essere > 0"}

    money = _db_get_wallet_money(user_id)
    if money is None:
        return {"ok": False, "error": "db non disponibile"}
    new_money = money + amount
    _db_set_wallet_money(user_id, new_money)
    return {"ok": True, "money": new_money}


@city_http_router.post("/chat/send")
async def api_city_chat_send(req: dict):
    """Invia un messaggio di chat a un altro giocatore della citta' (relay).
    Il mittente e' ricavato dal JWT (token); il destinatario da 'to_user'."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    from_user = str(payload.get("user_id", "")).strip()
    if not from_user:
        return {"ok": False, "error": "user_id assente nel token"}
    to_user = str(req.get("to_user", "")).strip()
    text = str(req.get("text", "")).strip()
    if not to_user or not text:
        return {"ok": False, "error": "'to_user' e 'text' richiesti"}
    if to_user == from_user:
        return {"ok": False, "error": "non puoi parlarti da solo"}
    if not _chat_rate_ok(from_user, to_user):
        return {"ok": False, "error": "troppo veloce: aspetta un attimo tra un messaggio e l'altro"}
    if _db_send_msg(from_user, to_user, text):
        return {"ok": True}
    return {"ok": False, "error": "db non disponibile"}


@city_http_router.get("/chat/inbox")
async def api_city_chat_inbox(
    token: str = Query(default=""),
    peek: bool = Query(default=False),
):
    """Ritorna i messaggi NON letti arrivati per me (dal mio JWT). Default:
    pull-and-clear (li segna come letti). Con peek=1 li restituisce SENZA
    consumarli, cosi' il client Unity puo' notificarli in citta' senza farli
    sparire dalla chat aperta sul telefono."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    msgs = _db_pull_inbox(user_id, peek=peek)
    return {"ok": True, "count": len(msgs), "messages": msgs}


@city_http_router.get("/chat/history")
async def api_city_chat_history(
    token: str = Query(default=""),
    with_id: str = Query(default=""),
):
    """Storia della conversazione con un altro giocatore (anche i messaggi
    gia' letti, entrambe le direzioni, dal piu' vecchio al piu' recente).
    Il campo 'mine' indica se il messaggio e' mio, per renderizzarlo nel
    verso giusto nella chat."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    other = str(with_id or "").strip()
    if not user_id or not other:
        return {"ok": False, "error": "user_id e with assenti"}
    if other == user_id:
        return {"ok": False, "error": "conversa con un ALTRO giocatore"}
    msgs = _db_chat_history(user_id, other)
    return {"ok": True, "count": len(msgs), "messages": msgs}


@city_http_router.get("/chat/conversations")
async def api_city_chat_conversations(token: str = Query(default="")):
    """Rubrica: elenco dei giocatori con cui si e' parlato, con l'ultimo
    messaggio e il numero di non letti (per i badge)."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    convs = _db_conversations(user_id)
    return {"ok": True, "count": len(convs), "conversations": convs}


@city_http_router.get("/presence")
async def api_city_presence(
    token: str = Query(default=""),
    with_id: str = Query(default=""),
):
    """Presenza e posizione di un altro giocatore. Le coordinate reali vengono
    esposte solo agli utenti che hanno gia' una conversazione con lui (privacy:
    si vede chi si conosce), e solo se e' online nel town server."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    other = str(with_id or "").strip()
    if not user_id or not other:
        return {"ok": False, "error": "user_id e with_id richiesti"}
    if other == user_id:
        return {"ok": False, "error": "guarda la TUA posizione, non quella altrui"}

    known = _db_had_thread(user_id, other)
    target = city_players.get(other)
    if target is not None:
        if known:
            return {"ok": True, "online": True, **_player_payload(other, target)}
        return {"ok": True, "online": True,
                "id": other,
                "username": target.get("username", "Giocatore"),
                "level": target.get("level", 1),
                "last_seen_sec": 0}

    row = _db_player_row(other)
    if row is None:
        return {"ok": True, "online": False}
    lu = row["last_update"]
    last_seen = max(0, int(time.time() - lu.timestamp())) if lu else 0
    return {"ok": True, "online": False,
            "id": other,
            "username": row["username"] or "Giocatore",
            "level": row["level"] or 1,
            "last_seen_sec": last_seen}


# ─── Party (gruppo di amici) ─────────────────────────────────────

@city_http_router.post("/party/create")
async def api_city_party_create(req: dict):
    """Crea (o restituisce) la party del giocatore autenticato."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    nid = _db_create_party(user_id)
    if nid is None:
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True, "party_id": nid}


@city_http_router.post("/party/invite")
async def api_city_party_invite(req: dict):
    """Invita un giocatore nella propria party."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    party_id = _db_get_party_by_user(user_id)
    if not party_id:
        return {"ok": False, "error": "prima crea o entra in una party"}
    to_user = str(req.get("to_user", "")).strip()
    if not to_user:
        return {"ok": False, "error": "'to_user' richiesto"}
    if to_user == user_id:
        return {"ok": False, "error": "non puoi invitarti da solo"}
    if not _db_is_member(party_id, user_id):
        return {"ok": False, "error": "non sei piu' membro della party"}
    if not _chat_rate_ok(user_id, to_user):
        return {"ok": False, "error": "troppo veloce: ritenta tra un attimo"}
    if not _db_invite_to_party(party_id, user_id, to_user):
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True, "party_id": party_id}


@city_http_router.get("/party/invites")
async def api_city_party_invites(token: str = Query(default="")):
    """Inviti in sospeso ricevuti."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    invites = _db_pending_invites(user_id)
    return {"ok": True, "count": len(invites), "invites": invites}


@city_http_router.post("/party/accept")
async def api_city_party_accept(req: dict):
    """Accetta un invito ed entra nella party."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    try:
        party_id = int(req.get("party_id", 0))
    except (TypeError, ValueError):
        return {"ok": False, "error": "party_id non valido"}
    if party_id <= 0:
        return {"ok": False, "error": "party_id non valido"}
    if not _db_accept_party(party_id, user_id):
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True, "party_id": party_id}


@city_http_router.post("/party/leave")
async def api_city_party_leave(req: dict):
    """Esce dalla party (il leader la scioglie)."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    _db_leave_party(user_id)
    return {"ok": True}


@city_http_router.post("/party/decline")
async def api_city_party_decline(req: dict):
    """Rifiuta (e smaltisce) un invito in sospeso."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    try:
        party_id = int(req.get("party_id", 0))
    except (TypeError, ValueError):
        return {"ok": False, "error": "party_id non valido"}
    if party_id <= 0:
        return {"ok": False, "error": "party_id non valido"}
    if not _db_decline_party(party_id, user_id):
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True}


@city_http_router.get("/party/state")
async def api_city_party_state(token: str = Query(default="")):
    """Stato della propria party (membri con presenza e posizione)."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    state = _db_party_state(user_id)
    if state is None:
        return {"ok": True, "party_id": None, "members": []}
    return {"ok": True, **state}


# ─── Regali fra giocatori (uova e gemme) ─────────────────────────

@city_http_router.post("/gift/send")
async def api_city_gift_send(req: dict):
    """Invia un regalo (uovo o gemme) a un altro giocatore. Il trasferimento
    reale avviene localmente su entrambi i lati (Android ricevente riscatta e
    lo aggiunge all'inventario); qui c'e' il ledger di recapito."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    from_user = str(payload.get("user_id", "")).strip()
    if not from_user:
        return {"ok": False, "error": "user_id assente nel token"}
    to_user = str(req.get("to_user", "")).strip()
    if to_user == from_user:
        return {"ok": False, "error": "non puoi regalarti qualcosa da solo"}
    if not to_user:
        return {"ok": False, "error": "'to_user' richiesto"}
    kind = str(req.get("kind", "")).strip().lower()
    if kind not in ("egg", "gem"):
        return {"ok": False, "error": "kind deve essere 'egg' o 'gem'"}
    if not _chat_rate_ok(from_user, to_user):
        return {"ok": False, "error": "troppo veloce: ritenta tra un attimo"}

    if kind == "gem":
        try:
            amount = int(req.get("amount", 0))
        except (TypeError, ValueError):
            return {"ok": False, "error": "amount non valido"}
        if amount <= 0 or amount > 9999:
            return {"ok": False, "error": "amount fuori range (1-9999)"}
        gid = _db_send_gift(from_user, to_user, "gem", None, None, None, amount)
    else:
        egg_id = str(req.get("egg_id", "")).strip()[:40]
        rarity = str(req.get("rarity", "")).strip()[:32]
        egg_name = str(req.get("egg_name", "")).strip()[:80]
        if not egg_id or not rarity:
            return {"ok": False, "error": "egg_id e rarity richiesti per l'uovo"}
        gid = _db_send_gift(from_user, to_user, "egg", egg_id, rarity, egg_name, None)

    if gid is None:
        return {"ok": False, "error": "db non disponibile"}
    return {"ok": True, "gift_id": gid}


@city_http_router.get("/gift/inbox")
async def api_city_gift_inbox(
    token: str = Query(default=""),
    peek: bool = Query(default=False),
):
    """Regali in arrivo (restano pendenti finche' non vengono riscattati;
    'peek' non consuma nulla: i regali si riscattano esplicitamente)."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    gifts = _db_pending_gifts(user_id)
    return {"ok": True, "count": len(gifts), "gifts": gifts}


@city_http_router.post("/gift/claim")
async def api_city_gift_claim(req: dict):
    """Riscatta un regalo. L'Android ricevente lo accredita poi nel profilo
    (inventario uova / gemme + Firestore)."""
    if not isinstance(req, dict):
        return {"ok": False, "error": "body JSON richiesto"}
    token = req.get("token", "") or ""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    try:
        gift_id = int(req.get("gift_id", 0))
    except (TypeError, ValueError):
        return {"ok": False, "error": "gift_id non valido"}
    if gift_id <= 0:
        return {"ok": False, "error": "gift_id non valido"}
    gift = _db_claim_gift(gift_id, user_id)
    if gift is None:
        return {"ok": False, "error": "regalo non trovato o gia' riscattato"}
    return {"ok": True, "gift": gift}


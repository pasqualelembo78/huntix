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
                    lat          DOUBLE PRECISION,
                    lon          DOUBLE PRECISION,
                    heading      REAL DEFAULT 0,
                    zone         TEXT,
                    last_update  TIMESTAMP DEFAULT NOW(),
                    connected_at TIMESTAMP DEFAULT NOW()
                )
            """)
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


def _db_pull_inbox(user_id, limit=50):
    """Ritorna i messaggi non ancora letti indirizzati a user_id e li marca
    come letti (pull-and-clear, adatto al polling HTTP)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                SELECT id, from_user, text, created_at
                FROM city_msgs
                WHERE to_user = %s AND read = FALSE
                ORDER BY id ASC
                LIMIT %s
            """, (user_id, limit))
            rows = cur.fetchall()
            ids = [r["id"] for r in rows]
            if ids:
                cur.execute(
                    "UPDATE city_msgs SET read = TRUE WHERE id = ANY(%s)",
                    (ids,))
            conn.commit()
            return [
                {"from": r["from_user"], "text": r["text"],
                 "ts": str(r["created_at"]) if r["created_at"] else ""}
                for r in rows
            ]
        finally:
            put_conn(conn)
    except Exception as e:
        logger.warning(f"[CityRealtime] DB inbox fallito per {user_id}: {e}")
        return []




def _db_upsert_player(user_id, username, level, skin, lat, lon, heading, zone):
    """Inserisce o aggiorna un player nella tabella (best-effort)."""
    try:
        conn = get_conn()
        try:
            cur = conn.cursor()
            cur.execute("""
                INSERT INTO city_active_players
                    (user_id, username, level, skin, lat, lon, heading, zone, last_update)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    username = EXCLUDED.username,
                    level = EXCLUDED.level,
                    skin = EXCLUDED.skin,
                    lat = EXCLUDED.lat,
                    lon = EXCLUDED.lon,
                    heading = EXCLUDED.heading,
                    zone = EXCLUDED.zone,
                    last_update = NOW()
            """, (user_id, username, level, skin, lat, lon, heading, zone))
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
        _db_upsert_player(user_id, username, level, skin, lat, lon, heading, new_zone)

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
                              p["skin"], lat, lon, heading, new_zone)

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
            "lat": lat,
            "lon": lon,
            "heading": heading,
            "anim_state": anim_state,
            "vehicle_code": vehicle_code,
            "zone": new_zone,
            "last_update": last_seen,
        }
        _zone_members[new_zone].add(pid)
        _db_upsert_player(pid, username, level, skin, lat, lon, heading, new_zone)
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
        _db_upsert_player(pid, username, level, skin, lat, lon, heading, new_zone)

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
    if _db_send_msg(from_user, to_user, text):
        return {"ok": True}
    return {"ok": False, "error": "db non disponibile"}


@city_http_router.get("/chat/inbox")
async def api_city_chat_inbox(token: str = Query(default="")):
    """Ritorna i messaggi NON letti arrivati per me (dal mio JWT) e li segna
    come letti (pull-and-clear). Chiamato periodicamente dal client."""
    payload = socket_authenticate(token) if token else None
    if not payload:
        return {"ok": False, "error": "autenticazione richiesta"}
    user_id = str(payload.get("user_id", "")).strip()
    if not user_id:
        return {"ok": False, "error": "user_id assente nel token"}
    msgs = _db_pull_inbox(user_id)
    return {"ok": True, "count": len(msgs), "messages": msgs}


"""
reallife/orders.py — Gestione ordini ai personaggi dei locali.

Un utente può ordinare prodotti/servizi da un locale. Il personaggio
assegnato al locale "prende" l'ordine e lo completa. I gain vengono
applicati ai bisogni Sims del personaggio del locale.
"""
import json
import logging
from datetime import datetime
from db import get_conn, put_conn

logger = logging.getLogger(__name__)

# BuildingType → bisogni soddisfatti e gain
ORDER_EFFECTS = {
    "RESTAURANT": {"hunger": 30, "thirst": 15, "fun": 5},
    "SUPERMARKET": {"hunger": 20, "thirst": 10, "fun": 3},
    "HOSPITAL": {"hygiene": 25, "sleep": 5, "fun": 2},
    "GYM": {"fun": 15, "sleep": 5},
    "HOUSE": {"hunger": 20, "sleep": 10, "fun": 5},
    "MONUMENT": {"fun": 20},
    "MUSEUM": {"fun": 25},
}

# Importa costi e funzione calcolo paga da skills.py
from reallife.skills import ORDER_COSTS, calculate_work_reward, add_skill_xp, BUILDING_SKILLS

# Tempo di attesa simulato (secondi) per completare un ordine
ORDER_WAIT_SECONDS = 120


def init_order_tables():
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS venue_orders (
                id SERIAL PRIMARY KEY,
                venue_id TEXT NOT NULL,
                character_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                items TEXT NOT NULL,
                status TEXT DEFAULT 'pending',
                total_gain TEXT DEFAULT '{}',
                cost INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP NULL
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_balances (
                user_id TEXT PRIMARY KEY,
                balance INTEGER DEFAULT 100
            )
        """)
        conn.commit()
    finally:
        put_conn(conn)


def get_user_balance(user_id):
    """Restituisce il saldo MVC dell'utente."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT balance FROM user_balances WHERE user_id=%s", (user_id,))
        row = cur.fetchone()
        if row:
            return row["balance"]
        cur.execute(
            "INSERT INTO user_balances (user_id, balance) VALUES (%s, %s) RETURNING balance",
            (user_id, 100),
        )
        conn.commit()
        return cur.fetchone()["balance"]
    finally:
        put_conn(conn)


def deduct_balance(user_id, amount):
    """Preleva soldi dal saldo. Ritorna True se ok, False se fondi insufficienti."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "UPDATE user_balances SET balance = balance - %s WHERE user_id = %s AND balance >= %s",
            (amount, user_id, amount)
        )
        if cur.rowcount == 0:
            conn.rollback()
            return False
        conn.commit()
        return True
    finally:
        put_conn(conn)


def add_balance(user_id, amount):
    """Aggiunge soldi al saldo."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO user_balances (user_id, balance) VALUES (%s, %s) "
            "ON CONFLICT (user_id) DO UPDATE SET balance=user_balances.balance+%s",
            (user_id, amount, amount),
        )
        conn.commit()
    finally:
        put_conn(conn)


def work(user_id, building_type="RESTAURANT"):
    """Guadagna MVC lavorando. Ritorna dict con dettagli."""
    result = calculate_work_reward(user_id, building_type)
    add_balance(user_id, result["reward"])
    
    # Aggiungi XP alla skill
    skill_info = BUILDING_SKILLS.get(building_type, {"skill": "quotidiano"})
    add_skill_xp(user_id, skill_info["skill"], result["reward"])
    
    return result


def create_order(user_id, venue_id, character_id, items, building_type):
    """Crea un ordine. Ritorna dict con order_id o errore."""
    from reallife.venue_assignment import get_venue_knowledge
    vk = get_venue_knowledge(venue_id) or {}
    bt = vk.get("building_type", building_type)
    gains = ORDER_EFFECTS.get(bt, {"fun": 5})
    cost = ORDER_COSTS.get(bt, 25)

    balance = get_user_balance(user_id)
    if balance < cost:
        return {"error": "insufficient_funds", "balance": balance, "cost": cost}

    if not deduct_balance(user_id, cost):
        return {"error": "insufficient_funds", "balance": balance, "cost": cost}

    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO venue_orders "
            "(venue_id, character_id, user_id, items, total_gain, cost) "
            "VALUES (%s, %s, %s, %s, %s, %s) RETURNING id",
            (venue_id, character_id, user_id, json.dumps(items), json.dumps(gains), cost),
        )
        order_id = cur.fetchone()[0]
        conn.commit()
        return {"order_id": order_id, "cost": cost, "balance_after": balance - cost}
    finally:
        put_conn(conn)


def get_pending_orders(user_id, venue_id=None):
    """Restituisce gli ordini pendenti o confermati."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        if venue_id:
            cur.execute(
                "SELECT id, venue_id, character_id, items, status, total_gain, created_at "
                "FROM venue_orders WHERE user_id=%s AND venue_id=%s "
                "AND status IN ('pending', 'confirmed') ORDER BY created_at DESC",
                (user_id, venue_id),
            )
        else:
            cur.execute(
                "SELECT id, venue_id, character_id, items, status, total_gain, created_at "
                "FROM venue_orders WHERE user_id=%s AND status IN ('pending', 'confirmed') "
                "ORDER BY created_at DESC",
                (user_id,),
            )
        rows = cur.fetchall()
        return [{
            "id": r["id"],
            "venue_id": r["venue_id"],
            "character_id": r["character_id"],
            "items": json.loads(r["items"]),
            "status": r["status"],
            "total_gain": json.loads(r["total_gain"]),
            "created_at": r["created_at"].isoformat() if r["created_at"] else None,
            "wait_seconds": ORDER_WAIT_SECONDS,
        } for r in rows]
    finally:
        put_conn(conn)


def complete_order(order_id, user_id):
    """Completa un ordine. Ritorna i gain applicati o None se non trovato."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT venue_id, character_id, total_gain FROM venue_orders "
            "WHERE id=%s AND user_id=%s AND status IN ('pending', 'confirmed')",
            (order_id, user_id),
        )
        row = cur.fetchone()
        if not row:
            return None

        gains = json.loads(row["total_gain"])
        character_id = row["character_id"]

        # Applica i gain ai bisogni Sims del personaggio del locale
        from reallife.store import recharge_needs
        try:
            needs = recharge_needs(user_id, character_id, "order")
        except Exception as e:
            logger.warning(f"recharge_needs failed for order {order_id}: {e}")
            needs = None

        cur.execute(
            "UPDATE venue_orders SET status='completed', completed_at=%s WHERE id=%s",
            (datetime.now(), order_id),
        )
        conn.commit()
        return {"gains": gains, "needs": needs}
    finally:
        put_conn(conn)


def get_order_status(order_id, user_id):
    """Restituisce lo stato di un ordine specifico."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT id, venue_id, character_id, items, status, total_gain, created_at, completed_at "
            "FROM venue_orders WHERE id=%s AND user_id=%s",
            (order_id, user_id),
        )
        row = cur.fetchone()
        if not row:
            return None
        return {
            "id": row["id"],
            "venue_id": row["venue_id"],
            "character_id": row["character_id"],
            "items": json.loads(row["items"]),
            "status": row["status"],
            "total_gain": json.loads(row["total_gain"]),
            "created_at": row["created_at"].isoformat() if row["created_at"] else None,
            "completed_at": row["completed_at"].isoformat() if row["completed_at"] else None,
            "wait_seconds": ORDER_WAIT_SECONDS,
        }
    finally:
        put_conn(conn)

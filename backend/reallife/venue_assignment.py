"""
reallife/venue_assignment.py — Assegnamento personaggi AI ai locali/negozi.

Ogni locale (ristorante, supermercato, ospedale...) riceve un personaggio AI
unico. Stesso personaggio non puo' lavorare in due locali entro 1 km di distanza.
L'assegnamento e' fisso ma puo' cambiare al mese (licenziamento/riassunzione).

Knowledge injection:
  - Il personaggio "dimentica" le sue competenze originali
  - Acquisisce le conoscenze specifiche del locale (pizzeria → pizze/ristorazione)
  - Al licenziamento, recupera le competenze originali
"""

import json
import logging
import random
import time
from datetime import datetime
from math import cos, radians
from db import get_conn, put_conn

logger = logging.getLogger(__name__)

# Mappatura BuildingType → categorie di personaggi preferite (in ordine)
BUILDING_TYPE_CATEGORIES = {
    "RESTAURANT": ["cucina", "quotidiano", "business", "intrattenimento"],
    "SUPERMARKET": ["quotidiano", "business", "tecnici"],
    "HOSPITAL": ["medicina", "esperti", "quotidiano"],
    "GYM": ["sport", "motivazione", "quotidiano"],
    "HOUSE": ["quotidiano", "amicizia", "intrattenimento"],
    "MONUMENT": ["storia", "viaggi", "creativi"],
    "MUSEUM": ["creativi", "storia", "sci_fi"],
}

# Conoscenze "ignoranza" per ogni tipo di locale (cosa NON deve sapere affatto)
VENUE_IGNORANCE = {
    "RESTAURANT": [
        "medicina", "chirurgia", "farmaci", "diagnosi",
        "programmazione", "codice", "sviluppo software",
        "meccanica auto", "riparazione veicoli",
    ],
    "SUPERMARKET": [
        "medicina", "chirurgia", "diagnosi",
        "programmazione software", "meccanica",
        "storia dell'arte", "restauro",
    ],
    "HOSPITAL": [
        "pizza", "vino", "ristorazione", "cucina",
        "programmazione", "meccanica", "sport professionistico",
    ],
    "GYM": [
        "medicina", "chirurgia", "farmaci",
        "programmazione", "finanza", "investimenti",
        "storia dell'arte", "letteratura classica",
    ],
    "HOUSE": [
        "medicina", "chirurgia",
        "programmazione", "ingegneria aerospaziale",
        "finanza avanzata", "investimenti",
    ],
    "MONUMENT": [
        "cucina", "pizza", "ristorazione",
        "programmazione", "meccanica",
        "sport professionistico", "medicina",
    ],
    "MUSEUM": [
        "cucina", "pizza", "ristorazione",
        "programmazione", "meccanica",
        "sport professionistico", "medicina",
    ],
}

# Conoscenze "expertise" per ogni tipo di locale
VENUE_EXPERTISE = {
    "RESTAURANT": [
        "pizza", "pasta", "cucina italiana", "vini",
        "ristorazione", "menu", "accoglienza clienti",
        "gastronomia", "prodotti tipici",
    ],
    "SUPERMARKET": [
        "supermercato", "prodotti alimentari",
        "offerte", "spesa", "organizzazione scaffali",
        "conservazione cibi", "igiene alimentare",
    ],
    "HOSPITAL": [
        "pronto soccorso", "medicina generale",
        "assistenza sanitaria", "esami medici",
        "reparti ospedalieri", "farmaci comuni",
        "prevenzione", "igiene ospedaliera",
    ],
    "GYM": [
        "fitness", "allenamento", "pesi",
        "esercizi cardiovascolari", "stretching",
        "nutrizione sportiva", "recupero muscolare",
        "schede allenamento",
    ],
    "HOUSE": [
        "casa", "famiglia", "gestione domestica",
        "pulizie", "manutenzione casa",
        "giardinaggio", "organizzazione familiare",
        "fai da te base",
    ],
    "MONUMENT": [
        "storia", "monumenti", "architettura",
        "arte", "cultura", "turismo",
        "patrimonio UNESCO", "storia locale",
    ],
    "MUSEUM": [
        "arte", "musei", "mostre", "cultura",
        "pittura", "scultura", "collezioni",
        "restauro", "storia dell'arte",
    ],
}

# Conoscenze "familiarity" (conoscenza generica, risposte non esperte)
VENUE_FAMILIARITY = {
    "RESTAURANT": [
        "enogastronomia", "cucina internazionale",
        "dolci", "bevande", "cocktail",
    ],
    "SUPERMARKET": [
        "sconti", "carte fedelta", "marchi",
        "prodotti locali", "biologico",
    ],
    "HOSPITAL": [
        "benessere", "salute", "alimentazione sana",
        "primo soccorso base",
    ],
    "GYM": [
        "alimentazione", "benessere", "salute",
        "yoga", "meditazione",
    ],
    "HOUSE": [
        "cucina base", "bricolage", "decorazione",
        "piante", "animali domestici",
    ],
    "MONUMENT": [
        "fotografia", "turismo", "viaggi",
        "curiosita storiche", "leggende",
    ],
    "MUSEUM": [
        "fotografia", "turismo", "viaggi",
        "curiosita artistiche", "collezionismo",
    ],
}


def init_venue_tables():
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS venue_assignments (
                venue_id TEXT NOT NULL,
                venue_name TEXT NOT NULL,
                building_type TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                character_id TEXT NOT NULL,
                character_name TEXT NOT NULL,
                character_avatar TEXT DEFAULT '',
                original_knowledge TEXT DEFAULT '{}',
                assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (venue_id, character_id)
            )
        """)
        conn.commit()
    finally:
        put_conn(conn)


def _character_fits_venue(char: dict, building_type: str) -> bool:
    """Returns True if the character's category matches the building type preferences."""
    cat = char.get("category", "")
    preferred = BUILDING_TYPE_CATEGORIES.get(building_type, ["quotidiano"])
    return cat in preferred


def _venue_distance_ok(
    candidate_char_id: str,
    lat: float,
    lng: float,
    exclude_venue_id: str | None = None,
) -> bool:
    """Check if candidate_char_id is NOT assigned to any venue within 1km."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        if exclude_venue_id:
            cur.execute(
                "SELECT venue_id, lat, lng FROM venue_assignments "
                "WHERE character_id=%s AND venue_id!=%s",
                (candidate_char_id, exclude_venue_id),
            )
        else:
            cur.execute(
                "SELECT venue_id, lat, lng FROM venue_assignments WHERE character_id=%s",
                (candidate_char_id,),
            )
        rows = cur.fetchall()
        for r in rows:
            vlat, vlng = r["lat"], r["lng"]
            cos_lat = cos(radians((lat + vlat) / 2))
            dy = (lat - vlat) * 111_000
            dx = (lng - vlng) * 111_000 * cos_lat
            dist = (dx * dx + dy * dy) ** 0.5
            if dist < 1000:
                return False
        return True
    finally:
        put_conn(conn)


def _build_venue_knowledge(building_type: str) -> dict:
    """Build a knowledge_domains dict for the given building type."""
    return {
        "expertise": VENUE_EXPERTISE.get(building_type, []),
        "familiarity": VENUE_FAMILIARITY.get(building_type, []),
        "ignorance": VENUE_IGNORANCE.get(building_type, []),
    }


def _pick_character_for_venue(
    building_type: str,
    lat: float,
    lng: float,
) -> dict | None:
    """Pick the best available character for a venue type, respecting 1km exclusivity."""
    from characters import list_characters
    chars = list_characters()
    random.shuffle(chars)

    # Preferisci personaggi della categoria giusta
    preferred_cats = BUILDING_TYPE_CATEGORIES.get(building_type, ["quotidiano"])
    best = None

    for cat in preferred_cats:
        for c in chars:
            if c.get("category", "") != cat:
                continue
            cid = c.get("id", "")
            if not _venue_distance_ok(cid, lat, lng):
                continue
            best = c
            break
        if best:
            break

    if not best:
        # Fallback: qualsiasi personaggio non assegnato
        for c in chars:
            cid = c.get("id", "")
            if _venue_distance_ok(cid, lat, lng):
                best = c
                break

    if not best:
        # Crea personaggio ad-hoc (estremo fallback)
        best = _create_ad_hoc_character(building_type)

    return best


def _create_ad_hoc_character(building_type: str) -> dict:
    """Create a minimal character for a venue when no predefined chars available."""
    from characters import list_characters
    existing = list_characters()
    if existing:
        base = random.choice(existing)
    else:
        base = {}
    names_map = {
        "RESTAURANT": ("Gino", "Rosa", "Luigi", "Maria", "Franco", "Anna"),
        "SUPERMARKET": ("Marco", "Elena", "Paolo", "Sofia", "Giorgio", "Chiara"),
        "HOSPITAL": ("Dott.", "Dott.ssa", "Prof.", "Dott. Luca", "Dott.ssa Marta"),
        "GYM": ("Alex", "Sara", "Luca", "Giulia", "Marco", "Elena"),
        "HOUSE": ("Signora", "Signor", "Maria", "Antonio"),
        "MONUMENT": ("Guida", "Cicerone", "Prof.", "Dott."),
        "MUSEUM": ("Guida", "Curatore", "Dott.", "Dott.ssa"),
    }
    names = names_map.get(building_type, ("Signore", "Signora"))
    name = random.choice(names) if names else "Assistente"
    cid = f"venue_adhoc_{building_type}_{name}_{int(time.time())}"
    return {
        "id": cid,
        "name": name,
        "full_name": name,
        "age": random.randint(25, 60),
        "role": f"addetto/a {building_type.lower()}",
        "category": "quotidiano",
        "avatar": random.choice(["👨‍🍳", "🧑‍💼", "👩‍⚕️", "🏋️", "👩‍🌾", "🧑‍🏫"]),
        "description": f"Sono {name}, lavoro qui.",
        "tags": [building_type.lower(), "lavoro", "servizio"],
        "essence": f"Sei {name}, addetto/a al locale.",
    }


def _store_assignment(
    venue_id: str,
    venue_name: str,
    building_type: str,
    lat: float,
    lng: float,
    char: dict,
    original_knowledge: dict | None = None,
):
    """Store or update a venue assignment in the DB."""
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO venue_assignments "
            "(venue_id, venue_name, building_type, lat, lng, character_id, character_name, character_avatar, original_knowledge, assigned_at) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) "
            "ON CONFLICT (venue_id, character_id) DO UPDATE SET "
            "venue_name=EXCLUDED.venue_name, assigned_at=EXCLUDED.assigned_at",
            (
                venue_id,
                venue_name,
                building_type,
                lat,
                lng,
                char.get("id", ""),
                char.get("name", "Sconosciuto"),
                char.get("avatar", "🙂"),
                json.dumps(original_knowledge or {}) if original_knowledge else "{}",
                datetime.now(),
            ),
        )
        conn.commit()
    finally:
        put_conn(conn)


def get_venue_knowledge(venue_id: str) -> dict | None:
    """
    Quick lookup: given a venue_id, return only the venue knowledge + building_type
    without doing a full character assignment.
    Returns None if no assignment exists yet.
    """
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT building_type FROM venue_assignments WHERE venue_id=%s ORDER BY assigned_at DESC LIMIT 1",
            (venue_id,),
        )
        row = cur.fetchone()
        if not row:
            return None
        bt = row["building_type"]
        return {"building_type": bt, "knowledge_domains": _build_venue_knowledge(bt)}
    finally:
        put_conn(conn)


def get_venue_character(
    venue_id: str,
    venue_name: str,
    building_type: str,
    lat: float,
    lng: float,
) -> dict | None:
    """
    Ritorna il personaggio assegnato al locale, completo di knowledge_domains
    specifici per il tipo di locale. Se non ancora assegnato, lo assegna ora.
    """
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT character_id, building_type, venue_name, original_knowledge "
            "FROM venue_assignments WHERE venue_id=%s ORDER BY assigned_at DESC LIMIT 1",
            (venue_id,),
        )
        row = cur.fetchone()
    finally:
        put_conn(conn)

    assigned_char = None
    original_knowledge = None
    effective_building_type = building_type
    effective_venue_name = venue_name

    if row:
        from characters import get_character
        char = get_character(row["character_id"])
        if char:
            assigned_char = dict(char)
            effective_building_type = row["building_type"]
            effective_venue_name = row.get("venue_name", venue_name)
            original_knowledge = row.get("original_knowledge", "{}")
            if isinstance(original_knowledge, str):
                original_knowledge = json.loads(original_knowledge)
        else:
            row = None

    if not assigned_char:
        char = _pick_character_for_venue(building_type, lat, lng)
        if not char:
            return None
        assigned_char = dict(char)
        effective_building_type = building_type
        original_knowledge = assigned_char.get("knowledge_domains", {})
        _store_assignment(
            venue_id, venue_name, building_type, lat, lng,
            assigned_char, original_knowledge,
        )

    # Inject venue knowledge into the character
    venue_knowledge = _build_venue_knowledge(effective_building_type)
    assigned_char["knowledge_domains"] = venue_knowledge
    # Override system_prompt with venue-aware one
    venue_name_display = effective_venue_name or effective_building_type.lower()
    worker_name = assigned_char.get("name", "l'addetto/a")
    assigned_char["system_prompt"] = (
        f"Sei {worker_name}, lavori presso {venue_name_display}. "
        f"Conosci tutto cio' che riguarda {venue_name_display} e il settore {effective_building_type.lower()}. "
        f"Non hai alcuna competenza al di fuori di questo ambito. "
        f"Se ti chiedono di argomenti che non c'entrano con {effective_building_type.lower()}, "
        f"ammetti di non saperne e declina gentilmente."
    )
    # Tag the character as venue worker
    assigned_char["tags"] = list(set(
        (assigned_char.get("tags", []) or []) +
        [effective_building_type.lower(), effective_venue_name.lower(), "lavoro"]
    ))
    return assigned_char


def fire_from_venue(venue_id: str) -> bool:
    """Fire the character from a venue, restoring their original knowledge."""
    import json

    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT character_id, original_knowledge FROM venue_assignments WHERE venue_id=%s",
            (venue_id,),
        )
        row = cur.fetchone()
        if row:
            cur.execute("DELETE FROM venue_assignments WHERE venue_id=%s", (venue_id,))
            conn.commit()
            return True
        return False
    finally:
        put_conn(conn)


def fire_all_staff() -> int:
    """
    Fire all venue staff (monthly rotation).
    Returns the count of venues that had staff fired.
    """
    conn = get_conn()
    try:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) AS n FROM venue_assignments")
        count = cur.fetchone().get("n", 0) if cur.fetchone() else 0
        if count:
            cur.execute("DELETE FROM venue_assignments")
            conn.commit()
        return count
    finally:
        put_conn(conn)


def get_reassignment_schedule() -> dict:
    """
    Returns info about when the next automatic reassignment should happen.
    Currently suggests monthly on the 1st.
    """
    now = datetime.now()
    next_month = now.month % 12 + 1
    next_year = now.year + (1 if now.month == 12 else 0)
    return {
        "last_reassignment": None,  # TODO: track in DB
        "next_scheduled": f"{next_year}-{next_month:02d}-01",
        "interval_days": 30,
    }

"""Tile server HUNTIX (FASE 3) — serve le tile al client Unity.

Endpoints (prefix /api/tiles):
  GET /ping               -> stato del servizio
  GET /{key}/graph        -> grafo stradale pre-generato (tiles/{key}.json.gz)
  GET /{key}/geo          -> geometria on-demand (cache disco tiles/{key}_geo.json.gz,
                             altrimenti generata da osm_italy_processor.py gen-tile --skip-graph)
  GET /{key}/status       -> quali parti esistono (debug, senza payload)

Le tile vuote (mare) non producono file: la risposta geo e' un documento
sintetico con liste vuote, compatibile con TileGeoDoc di Unity.
"""

from __future__ import annotations

import asyncio
import gzip
import json
import logging
import os
import re
import time
from collections import OrderedDict
from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import Response

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/tiles", tags=["tiles"])

_PREPROC = Path(__file__).resolve().parent.parent / "preprocessing"
TILES_DIR = Path(os.environ.get("HUNTIX_TILES_DIR") or (_PREPROC / "tiles"))
VENV_PY = Path(os.environ.get("HUNTIX_PREPROC_PY") or (_PREPROC / "venv/bin/python"))
PROCESSOR = _PREPROC / "osm_italy_processor.py"
MANIFEST_PATH = TILES_DIR / "geo_manifest.json"

GEO_DISK_CAP_BYTES = int(float(os.environ.get("HUNTIX_GEO_CACHE_GB", "3")) * 1_000_000_000)
RAM_CACHE_BYTES = int(os.environ.get("HUNTIX_TILE_RAM_MB", "512")) * 1_000_000
GEN_TIMEOUT_S = int(os.environ.get("HUNTIX_GEN_TIMEOUT", "600"))

_KEY_RE = re.compile(r"^IT_(-?\d{1,5})_(-?\d{1,5})$")

# ── registry paesi (HUNTIX_COUNTRY): bbox per la geo on-demand ───
# Determinato dal data/italy-land? No: bbox. Un tile e' assegnato al PRIMO
# paese registrato il cui bbox contiene il centro della tile. Tenere l'Italia
# per prima: il suo bbox (33.9..48.1 lat, 3.9..19.1 lon) riproduce il vecchio
# envelope. Per i paesi confinanti usare bbox il piu' stretti possibile.
_WC_FILE = _PREPROC / "world_countries.json"


def _load_countries() -> list[dict]:
    try:
        with open(_WC_FILE, encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list) or not all(
                isinstance(c, dict) and "slug" in c and "bbox" in c
                for c in data):
            logger.warning("[TileServer] world_countries.json malformato")
            return []
        return data
    except Exception as e:
        logger.warning("[TileServer] world_countries.json: %s", e)
        return [{"slug": "italy",
                 "bbox": [33.9, 3.9, 48.1, 19.1]}]


_countries = _load_countries()


def _country_for(ilat: int, ilon: int) -> str | None:
    """Slug del paese che contiene il centro della tile (primo match)."""
    clat = 34.0 + (ilat + 0.5) * 0.090
    clon = 5.0 + (ilon + 0.5) * 0.121
    for c in _countries:
        a, b, cc, d = c["bbox"]
        if a <= clat <= cc and b <= clon <= d:
            return c["slug"]
    return None

# ── cache RAM LRU (JSON decompressi) ─────────────────────────────
_ram: "OrderedDict[str, bytes]" = OrderedDict()
_ram_bytes = 0

# ── generazione concorrente: un lock per chiave ──────────────────
_gen_locks: dict[str, asyncio.Lock] = {}

# ── manifest LRU disco per le tile geo generate ───────────────────
_manifest: dict[str, list] = {}   # key -> [bytes, last_access_epoch]
_manifest_dirty = False


def _validate_key(key: str) -> None:
    if not _KEY_RE.match(key):
        raise HTTPException(status_code=400, detail="tile key non valida")


def _ram_get(cache_key: str) -> bytes | None:
    global _ram_bytes
    data = _ram.get(cache_key)
    if data is not None:
        _ram.move_to_end(cache_key)
    return data


def _ram_put(cache_key: str, data: bytes) -> None:
    global _ram_bytes
    if cache_key in _ram:
        return
    _ram[cache_key] = data
    _ram_bytes += len(data)
    while _ram_bytes > RAM_CACHE_BYTES and len(_ram) > 1:
        _, old = _ram.popitem(last=False)
        _ram_bytes -= len(old)


def _load_gz(path: Path, cache_key: str) -> bytes:
    cached = _ram_get(cache_key)
    if cached is not None:
        return cached
    with gzip.open(path, "rb") as gz:
        data = gz.read()
    _ram_put(cache_key, data)
    return data


def _manifest_load() -> None:
    global _manifest
    if _manifest or not MANIFEST_PATH.exists():
        return
    try:
        _manifest = json.loads(MANIFEST_PATH.read_text())
    except Exception:
        _manifest = {}


def _manifest_save() -> None:
    global _manifest_dirty
    if not _manifest_dirty:
        return
    tmp = MANIFEST_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(_manifest))
    tmp.replace(MANIFEST_PATH)
    _manifest_dirty = False



# civici OSM (addr:housenumber) iniettati nelle tile geo
_ADDRS_FILE = TILES_DIR / "addrs_foggia.json.gz"
_addrs_cache = {"list": None}


def _load_addrs() -> list:
    if _addrs_cache["list"] is None:
        try:
            with gzip.open(_ADDRS_FILE, "rt") as f:
                _addrs_cache["list"] = json.load(f)
        except Exception:
            logger.warning("[TileServer] addrs non disponibili: %s", _ADDRS_FILE)
            _addrs_cache["list"] = []
    return _addrs_cache["list"]


_enriched_cache = {}


def _enrich_geo(key: str, data: bytes) -> bytes:
    """Aggiunge l'array dei civici dentro il bbox della tile (idempotente)."""
    if key in _enriched_cache:
        return _enriched_cache[key]
    try:
        doc = json.loads(data)
    except Exception:
        return data
    if isinstance(doc, dict) and not doc.get("addrs"):
        bbox = doc.get("bbox") or []
        if len(bbox) == 4:
            la, lo, lb, lob = bbox
            sel = [q for q in _load_addrs()
                   if la <= q["a"] <= lb and lo <= q["o"] <= lob]
            if sel:
                doc["addrs"] = sel
                data = json.dumps(doc, separators=(",", ":")).encode()
    _enriched_cache[key] = data
    return data


def _geo_touch(key: str) -> None:
    """Registra accesso LRU per una tile geo su disco."""
    p = TILES_DIR / f"{key}_geo.json.gz"
    if not p.exists():
        return
    _manifest_load()
    entry = _manifest.get(key)
    now = time.time()
    if entry is None:
        _manifest[key] = [p.stat().st_size, now]
    else:
        entry[1] = now
    global _manifest_dirty
    _manifest_dirty = True
    _evict_geo()
    _manifest_save()


def _evict_geo() -> None:
    """Cap disco sulle tile geo generate (non tocca i grafi pre-generati)."""
    total = sum(e[0] for e in _manifest.values())
    if total <= GEO_DISK_CAP_BYTES:
        return
    for key, (size, _) in sorted(_manifest.items(), key=lambda kv: kv[1][1]):
        if total <= GEO_DISK_CAP_BYTES:
            break
        p = TILES_DIR / f"{key}_geo.json.gz"
        if key in _gen_locks and _gen_locks[key].locked():
            continue
        try:
            p.unlink(missing_ok=True)
            total -= size
            _manifest.pop(key, None)
            logger.info(f"[TileServer] evict geo {key} ({size} B)")
        except OSError:
            continue


_EMPTY_GEO = ('{"tile":"%s","bbox":[%s],"roads":[],"buildings":[],"parks":[],'
              '"trees":[],"signals":[],"airports":[]}')


@router.get("/ping")
async def tiles_ping():
    n_graph = len(list(TILES_DIR.glob("IT_*.json.gz"))) - \
        len(list(TILES_DIR.glob("IT_*_geo.json.gz")))
    return {"ok": True, "graph_tiles": max(n_graph, 0),
            "geo_cached": len(_manifest),
            "disk_cap_gb": round(GEO_DISK_CAP_BYTES / 1e9, 1)}


@router.post("/cache/clear")
async def tiles_cache_clear():
    """Svuota la cache RAM delle tile (per sviluppo)."""
    global _ram, _ram_bytes
    _ram = OrderedDict()
    _ram_bytes = 0
    return {"ok": True}


@router.get("/{key}/status")
async def tiles_status(key: str):
    _validate_key(key)
    graph_p = TILES_DIR / f"{key}.json.gz"
    geo_p = TILES_DIR / f"{key}_geo.json.gz"
    return {
        "tile": key,
        "graph": graph_p.exists(),
        "graph_bytes": graph_p.stat().st_size if graph_p.exists() else 0,
        "geo": geo_p.exists(),
        "geo_bytes": geo_p.stat().st_size if geo_p.exists() else 0,
    }


@router.get("/{key}/graph")
async def tiles_graph(key: str):
    _validate_key(key)
    path = TILES_DIR / f"{key}.json.gz"
    if not path.exists():
        raise HTTPException(status_code=404, detail=f"grafo {key} non presente")
    data = _load_gz(path, f"g:{key}")
    return Response(content=data, media_type="application/json",
                    headers={"Cache-Control": "public, max-age=604800"})


async def _generate_geo(key: str, country: str) -> bool:
    """Lancia gen-tile --skip-graph nel venv di preprocessing. True se ok."""
    cmd = [str(VENV_PY), str(PROCESSOR), "gen-tile", key, "--skip-graph"]
    env = {**os.environ, "HUNTIX_COUNTRY": country}
    proc = await asyncio.create_subprocess_exec(
        *cmd, cwd=str(_PREPROC), env=env,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    try:
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=GEN_TIMEOUT_S)
    except asyncio.TimeoutError:
        proc.kill()
        raise HTTPException(status_code=504, detail=f"generazione {key} timeout")
    if proc.returncode != 0:
        tail = (out or b"")[-500:].decode(errors="replace")
        logger.error(f"[TileServer] gen-tile {key} fallito:\n{tail}")
        raise HTTPException(status_code=500, detail=f"generazione {key} fallita")
    logger.info(f"[TileServer] gen-tile {key}: {(out or b'')[-200:].decode(errors='replace')}")
    return True


@router.get("/{key}/geo")
async def tiles_geo(key: str):
    _validate_key(key)
    path = TILES_DIR / f"{key}_geo.json.gz"

    m = _KEY_RE.match(key)
    ilat, ilon = int(m.group(1)), int(m.group(2))
    # paese di riferimento per la generazione on-demand; nessun paese
    # registrato (mare aperto / nazione non preparata) -> documento vuoto
    country = _country_for(ilat, ilon)
    if country is None:
        bbox = _tile_bbox_from_key(key)
        body = _EMPTY_GEO % (key, ",".join(str(v) for v in bbox))
        return Response(content=body, media_type="application/json",
                        headers={"Cache-Control": "public, max-age=3600"})

    if not path.exists():
        lock = _gen_locks.setdefault(key, asyncio.Lock())
        async with lock:
            if not path.exists():
                await _generate_geo(key, country)

    if not path.exists():
        # tile senza record (mare/aree remote): documento sintetico vuoto
        bbox = _tile_bbox_from_key(key)
        body = _EMPTY_GEO % (key, ",".join(str(v) for v in bbox))
        return Response(content=body, media_type="application/json",
                        headers={"Cache-Control": "public, max-age=3600"})

    _geo_touch(key)
    data = _load_gz(path, f"geo:{key}")
    data = _enrich_geo(key, data)
    return Response(content=data, media_type="application/json",
                    headers={"Cache-Control": "public, max-age=3600"})


def _tile_bbox_from_key(key: str) -> list:
    m = _KEY_RE.match(key)
    ilat, ilon = int(m.group(1)), int(m.group(2))
    latmin = 34.0 + ilat * 0.090
    lonmin = 5.0 + ilon * 0.121
    return [round(latmin, 6), round(lonmin, 6),
            round(latmin + 0.090, 6), round(lonmin + 0.121, 6)]

#!/usr/bin/env python3
"""HUNTIX — Elevazione reale (DEM) per le tile.

Strategia a livelli (dal piu' deterministico al fallback):

  1) Cache su disco `dem_cache/{key}.json`: ogni tile ha la sua griglia,
     calcolata UNA volta sola e riusabile per sempre (idempotenza).
  2) SRTM HGT locali (`HUNTIX_SRTM_DIR`, di default `srtm/`): file
     1°x1° (SRTM3 ~90 m o SRTM1 ~30 m) letti in locale, totalmente offline,
     senza quota/rate-limit. Il bay della cartella basta: es. `N45E007.hgt`
     o `N45E007.hgt.zip`. Su mari/buchi (void=-32768) vale 0.
  3) API OpenTopoData (default): solo quando locale+cache sono assenti.
     Backoff esponenziale sul 429 e retry; e' l'unica fonte "fresca" ma la
     leghiamo alla quota gratuita, quindi il batch la usa con prudenza.

`elevation_grid()` e' la funzione pubblica e usa tutta la catena.
`bake_offline_grid()` usata in fase di bake/generazione tile (MAI rete:
solo cache + HGT) per non stallare i batch paralleli del gen-tile.

Uso:
    from dem import elevation_grid, grid_shape, bake_offline_grid
    elev = elevation_grid("IT_020_064", bbox)   # list[float] row-major (lat dec)
    nrow, ncol = grid_shape(bbox)               # quanti punti per asse
"""

from __future__ import annotations

import gzip
import json
import math
import os
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import OrderedDict
from pathlib import Path
from typing import List, Optional, Tuple

BASE = Path(__file__).resolve().parent
CACHE_DIR = Path(os.environ.get("HUNTIX_DEM_CACHE") or (BASE / "dem_cache"))
# Cartella SRTM HGT locale (offline totale). Default `srtm/` accanto a questo
# file. File attesi (piatto): `N45E007.hgt`, `N45E007.hgt.zip`, `N45E012.zip`.
SRTM_DIR = Path(os.environ.get("HUNTIX_SRTM_DIR") or (BASE / "srtm"))

# Fonte API di fall back. srtm30m = risoluzione ~30 m, copre l'Europa e gran
# parte del mondo.
DEM_API = os.environ.get(
    "HUNTIX_DEM_API",
    "https://api.opentopodata.org/v1/srtm30m")
DEM_TIMEOUT_S = int(os.environ.get("HUNTIX_DEM_TIMEOUT", "30"))
DEM_RETRY = int(os.environ.get("HUNTIX_DEM_RETRY", "3"))

# Spaziatura griglia di campionamento (metri), PASSO MASSIMO: vedi
# `grid_shape`, che arrotonda per eccesso a multipli interi di
# `HUNTIX_DEM_ALIGN_STEPS` (di default 10 = i chunk da ~1 km del client).
# Perche' l'allineamento conta: i nodi DEM devono TUMBARE sulle giunzioni fra
# chunk, altrimenti ogni chunk taglia la superficie a meta' cella e i due
# lati della stessa strada/terreno usano piani diversi (spigolo visibile a ogni
# confine di chunk, auto e pedoni che "sbattono" sul gradino).
#
# Storico dei valori (e cosa rompevano):
#  - 1000 m: una cella DEM == un chunk (~1 km). Ogni chunk diventava un piano
#    perfetto con una PIEGA secca al confine, e la citta' diventava una scala di
#    rampe da 1 km. Inoltre il risparmio di punti non esisteva piu': la mesh
#    del terreno lato client e' molto piu' fine della griglia.
#  - 250 m: 4-5 celle per chunk, piege 4-5 volte meno ampie, e la risoluzione
#    si avvicina alla spaziatura reale dei chunk cosi' l'errore di pendenza
#    sui bordi scende sotto quello della griglia di sampling del terreno.
SAMPLE_SPACING_M = float(os.environ.get("HUNTIX_DEM_SPACING_M", "250"))

# La griglia viene allineata ai confini dei chunk del client: i passi per riga
# e per colonna diventano multipli di questo valore (in gradi di tile).
HUNTIX_DEM_ALIGN_STEPS = int(os.environ.get("HUNTIX_DEM_ALIGN_STEPS", "10"))

# Soglia di copertura sotto la quale una griglia e' considerata "nessun dato"
# (tutto void): meglio nessuna `ele` (mondo piatto, che si cammina) che una
# griglia inventata. 0.25 = almeno un quarto dei campioni con dato reale.
MIN_VALID_RATIO = float(os.environ.get("HUNTIX_DEM_MIN_VALID", "0.25"))

# OpenTopoData free API: max ~100 locations richiesti ma con rate limit
# (~1 req/s). Usiamo batch piu' piccoli e backoff per evitare il 429.
_MAX_PER_REQ = 80
_RATE_DELAY_S = 1.0

# SRTM HGT: coverage 60°N..60°S; valore void (nessun dato / mare) = -32768.
_HGT_NORTH = 60
_HGT_VOID = -32768
# Memoria corta per i file HGT gia' letti (n x 2 byte, ~2.8 MB a file SRTM3).
_HGT_MEM: "OrderedDict[str, Tuple[int, bytes]]" = OrderedDict()
_HGT_MEM_MAX = 8


def geo_delta_m_per_deg(lat: float) -> Tuple[float, float]:
    """Metri per grado di latitudine/longitudine alla latitudine data."""
    lat_m = 111_320.0
    lon_m = 111_320.0 * math_cos(lat)
    return lat_m, lon_m


def math_cos(lat: float) -> float:
    import math
    return math.cos(math.radians(lat))


def grid_shape(bbox: List[float]) -> Tuple[int, int]:
    """Righe, colonne della griglia DEM per il bbox [latmin,lonmin,latmax,lonmax].

    NOSA: la griglia e' regolare in coordinate DEGREE; nrow lungo lat, ncol lon.

    `SAMPLE_SPACING_M` e' un PASSO MASSIMO: il numero di celle viene
    arrotondato per eccesso a un multiplo di `HUNTIX_DEM_ALIGN_STEPS` (di
    default 10), cosi' i nodi DEM allineano con i confini dei chunk da ~1 km
    del client (`CityGrid.ChunkLatStep`/`ChunkLngStep`). Allineati, i nodi
    cadono esattamente sugli angoli dei chunk e non li tagliano a meta'.
    """
    latmin, lonmin, latmax, lonmax = bbox
    lat_m, lon_m = geo_delta_m_per_deg((latmin + latmax) * 0.5)
    align = max(1, HUNTIX_DEM_ALIGN_STEPS)

    def steps(span_m: float) -> int:
        want = span_m / SAMPLE_SPACING_M
        # arrotonda per eccesso, poi riporta al multiplo di `align`
        return max(align, int(math.ceil(want / align)) * align)

    nrow = steps((latmax - latmin) * lat_m)
    ncol = steps((lonmax - lonmin) * lon_m)
    # numero punti (estremi inclusi)
    return nrow + 1, ncol + 1


def fill_voids(grid: List[Optional[float]], nrow: int, ncol: int) -> List[float]:
    """Riempi i buchi (void/mare, `None`) col valore valido piu' vicino.

    Motivo (storico): i void SRTM venivano scritti come 0 = livello del mare.
    Su una tile costiera il risultato era una scogliera artificiale: la citta'
    (es. Bari a 10-25 m) affiancava campioni a 0 e il terreno scivolava al mare
    entro ~2 km, con pendenze assurde. Riempiendo col vicino piu' vicino, il
    mare aperto resta ~0 (il vicino e' la costa, che e' a 0) e l'entroterra
    resta continuo: la griglia smette di avere discontinuita'.

    Voce non riempita (nessun valido raggiungibile) resta 0, che e' comunque
    la quota giusta per il mare aperto.
    """
    n = nrow * ncol
    if n == 0:
        return []
    out: List[float] = [0.0] * n
    seen = bytearray(n)
    queue: List[int] = []
    valid = 0
    for i, v in enumerate(grid):
        if v is None:
            continue
        valid += 1
        out[i] = float(v)
        seen[i] = 1
        queue.append(i)
    if not queue:
        return out
    # BFS multi-sorgente: propaga i valori validi ai vicini, anello per anello.
    # Copre l'intera griglia perche' il BFS non si ferma: anche i bordi lontani
    # ricevono il valore del valido piu' vicino.
    head = 0
    while head < len(queue):
        i = queue[head]
        head += 1
        r, c = divmod(i, ncol)
        if r > 0:
            j = i - ncol
            if not seen[j]:
                seen[j] = 1
                out[j] = out[i]
                queue.append(j)
        if r + 1 < nrow:
            j = i + ncol
            if not seen[j]:
                seen[j] = 1
                out[j] = out[i]
                queue.append(j)
        if c > 0:
            j = i - 1
            if not seen[j]:
                seen[j] = 1
                out[j] = out[i]
                queue.append(j)
        if c + 1 < ncol:
            j = i + 1
            if not seen[j]:
                seen[j] = 1
                out[j] = out[i]
                queue.append(j)
    _ = valid
    return out


def _cache_path(key: str) -> Path:
    return CACHE_DIR / f"{key}.json"


# Firma del formato di griglia: entra nella cache cosi' un cambio di passo, di
# allineamento o di bbox invalida le griglie vecchie invece di riusarle in
# silenzio (era esattamente il motivo per cui 1000 -> 250 m non si vedeva).
GRID_SIG_VERSION = 2   # 2 = riempimento void col vicino (prima: void = 0)


def _grid_sig(bbox: List[float]) -> str:
    nrow, ncol = grid_shape(bbox)
    return (f"v{GRID_SIG_VERSION}|{SAMPLE_SPACING_M:g}|"
            f"{HUNTIX_DEM_ALIGN_STEPS}|{nrow}x{ncol}|"
            f"{round(bbox[0], 6)},{round(bbox[1], 6)},"
            f"{round(bbox[2], 6)},{round(bbox[3], 6)}")


def _load_cache(key: str, bbox: List[float]) -> Optional[List[float]]:
    """Griglia in cache, ma SOLO se e' ancora valida per questo bbox.

    La validita' e' data da una FIRMA: bbox arrotondato + passo richiesto +
    versione del formato di riempimento void. Senza, cambiando
    `HUNTIX_DEM_SPACING_M` (storicamente 1000 -> 250 m) la cache riusava le
    griglie vecchie a risoluzione sbagliata e la nuova spaziatura non si
    vedeva mai, nemmeno dopo un bump di GEO_FORMAT_BASE.
    """
    p = _cache_path(key)
    if not p.exists():
        return None
    try:
        with open(p, encoding="utf-8") as f:
            data = json.load(f)
        if data.get("sig") != _grid_sig(bbox):
            return None
        return data.get("grid")
    except Exception:
        return None


def _save_cache(key: str, grid: List[float], bbox: List[float]) -> None:
    try:
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with open(_cache_path(key), "w", encoding="utf-8") as f:
            json.dump({"key": key, "bbox": bbox, "sig": _grid_sig(bbox),
                       "grid": grid}, f, separators=(",", ":"))
    except Exception:
        pass


# ── SRTM HGT locale (offline) ───────────────────────────────────────────

def _hgt_name(lat: float, lon: float) -> Optional[str]:
    """Nome file SRTM della cella 1°x1° che contiene il punto, o None se il
    punto e' fuori dal coverage (60°N..60°S)."""
    if not (-_HGT_NORTH <= lat <= _HGT_NORTH):
        return None
    ilat = int(math.floor(lat))
    ilon = int(math.floor(lon))
    if not (-180 <= ilon <= 179):
        return None
    ns = "N" if ilat >= 0 else "S"
    ew = "E" if ilon >= 0 else "W"
    return f"{ns}{abs(ilat):02d}{ew}{abs(ilon):03d}"


def _hgt_paths(lat: float, lon: float) -> List[Path]:
    name = _hgt_name(lat, lon)
    if name is None:
        return []
    return [p for p in (
        SRTM_DIR / f"{name}.hgt",
        SRTM_DIR / f"{name}.hgt.zip",
        SRTM_DIR / f"{name}.zip",
    ) if p.exists()]


def _square_root(v: int) -> Optional[int]:
    """Radice intera esatta di v (lato di una griglia HGT), o None."""
    if v < 4:
        return None
    s = int(math.sqrt(v))
    while (s + 1) * (s + 1) <= v:
        s += 1
    while s * s > v:
        s -= 1
    return s if s * s == v else None


def _load_hgt_file(path: Path) -> Optional[Tuple[int, bytes]]:
    """Ritorna (n, raw) con n = campioni per lato (1201 SRTM3 / 3601 SRTM1).
    Raw = big-endian int16 row-major, riga 0 = lat max (nord)."""
    try:
        if (path.suffix.lower() == ".zip" or str(path).lower().endswith(".hgt.zip")):
            with zipfile.ZipFile(path) as z:
                inner = next((nm for nm in z.namelist()
                              if nm.lower().endswith(".hgt")), None)
                if inner is None:
                    return None
                raw = z.read(inner)
        else:
            raw = path.read_bytes()
    except Exception:
        return None
    n = _square_root(len(raw) // 2)
    if n is None or n < 2:
        return None
    return n, raw


def _hgt_for_point(lat: float, lon: float) -> Optional[Tuple[int, bytes]]:
    """File HGT della cella del punto (con LRU corta)."""
    if not (-_HGT_NORTH <= lat <= _HGT_NORTH):
        return None
    paths = _hgt_paths(lat, lon)
    if not paths:
        return None
    key = str(paths[0])
    if key in _HGT_MEM:
        _HGT_MEM.move_to_end(key)
        return _HGT_MEM[key]
    grid = _load_hgt_file(paths[0])
    if grid is None:
        return None
    _HGT_MEM[key] = grid
    while len(_HGT_MEM) > _HGT_MEM_MAX:
        _HGT_MEM.popitem(last=False)
    return grid


def _hgt_sample_ll(lat: float, lon: float) -> Optional[float]:
    """Quota s.l.m. (m) al punto dalla griglia HGT della sua cella, con
    bilineare sui 4 campioni circostanti. Void/mare -> None. Fuori coverage
    della cella o file assente -> None."""
    grid = _hgt_for_point(lat, lon)
    if grid is None:
        return None
    n, raw = grid
    ilat = int(math.floor(lat))
    ilon = int(math.floor(lon))

    # posizione normalizzata dentro la riga/colonna (0..n-1)
    fy = (ilat + 1 - lat) * (n - 1)   # nord (riga 0) -> sud
    fx = (lon - ilon) * (n - 1)       # ovest -> est
    y0 = int(min(max(math.floor(fy), 0), n - 1))
    x0 = int(min(max(math.floor(fx), 0), n - 1))
    y1 = min(y0 + 1, n - 1)
    x1 = min(x0 + 1, n - 1)
    dy = fy - y0
    dx = fx - x0

    def sample(y: int, x: int) -> Optional[float]:
        off = (y * n + x) * 2
        v = struct.unpack_from(">h", raw, off)[0]
        return None if v == _HGT_VOID else float(v)

    v00, v01, v10, v11 = sample(y0, x0), sample(y0, x1), sample(y1, x0), sample(y1, x1)
    valid = [v for v in (v00, v01, v10, v11) if v is not None]
    if len(valid) == 4:
        return (v00 * (1 - dx) * (1 - dy) + v01 * dx * (1 - dy) +
                v10 * (1 - dx) * dy + v11 * dx * dy)
    if valid:
        # bordo della cella o dato mancante ai vertici: media dei validi
        return sum(valid) / len(valid)
    # nessun campione valido nei 4 angoli: cerca il piu' vicino in 5x5
    for r in range(1, 3):
        for yy in range(max(0, y0 - r), min(n, y0 + r + 1)):
            for xx in range(max(0, x0 - r), min(n, x0 + r + 1)):
                v = sample(yy, xx)
                if v is not None:
                    return v
    return None


def point_height(lat: float, lon: float) -> Optional[float]:
    """Quota s.l.m. (m) nel punto [lat, lon], SOLO fonti locali (cache/SRTM
    HGT offline, come i batch `gen-tile`). None se la cella manca o e' mare."""
    return _hgt_sample_ll(lat, lon)


def _hgt_covers(bbox: List[float]) -> bool:
    """True se esiste il file HGT della cella del CENTRO del bbox (il tile
    montano tipico e' tutto nella stessa cella 1°). Se il centro manca non
    forziamo il locale per l'intera tile."""
    latmin, lonmin, latmax, lonmax = bbox
    return bool(_hgt_paths((latmin + latmax) * 0.5, (lonmin + lonmax) * 0.5))


def _compute_local_grid(bbox: List[float]) -> Optional[List[float]]:
    """Griglia DEM del bbox SOLO da fonti locali (HGT).

    I punti void/mare diventano il valore valido PIU' VICINO (`fill_voids`):
    prima erano 0, cioe' livello del mare, e su ogni tile costiera quello
    significava una scogliera artificiale fra citta' e mare. Se la copertura
    valida e' troppo bassa (< MIN_VALID_RATIO) si ritorna None: meglio nessuna
    `ele` (mondo piatto) che una griglia inventata.
    """
    nrow, ncol = grid_shape(bbox)
    latmin, lonmin, latmax, lonmax = bbox
    if not _hgt_covers(bbox):
        return None
    raw: List[Optional[float]] = [None] * (nrow * ncol)
    valid = 0
    for i in range(nrow * ncol):
        lat = latmin + (latmax - latmin) * (i // ncol) / (nrow - 1)
        lon = lonmin + (lonmax - lonmin) * (i % ncol) / (ncol - 1)
        v = _hgt_sample_ll(lat, lon)
        if v is not None:
            raw[i] = round(v, 2)
            valid += 1
    if valid == 0 or valid < MIN_VALID_RATIO * (nrow * ncol):
        return None
    grid = fill_voids(raw, nrow, ncol)
    return [round(v, 2) for v in grid]


# ── API OpenTopoData (fallback rete) ───────────────────────────────────

def _request(locations: str) -> List[float]:
    """Una richiesta OpenTopoData con locations `|`-separate; ritorna le elevation."""
    url = f"{DEM_API}?locations={urllib.parse.quote(locations, safe='')}"
    last = None
    for attempt in range(DEM_RETRY + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "HUNTIX/1.0"})
            with urllib.request.urlopen(req, timeout=DEM_TIMEOUT_S) as r:
                doc = json.loads(r.read().decode())
            if doc.get("status") == "OK" and doc.get("results"):
                out = []
                for res in doc["results"]:
                    if "elevation" in res:
                        out.append(float(res["elevation"]))
                    else:
                        out.append(None)
                return out
            last = doc
        except urllib.error.HTTPError as e:
            # 429 = rate limit: backoff piu' lungo a ogni tentativo
            if e.code == 429:
                last = "429 rate-limit"
                time.sleep(_RATE_DELAY_S * (attempt + 5))
                continue
            last = repr(e)
        except Exception as e:  # noqa: BLE001
            last = repr(e)
        time.sleep(_RATE_DELAY_S * (attempt + 1))
    raise RuntimeError(f"DEM fallito per {locations}: {last}")


# ── API pubblica ───────────────────────────────────────────────────────

def bake_offline_grid(key: str, bbox: List[float]) -> Optional[List[float]]:
    """Griglia DEM SOLO da fonti deterministiche locali (cache disco o SRTM
    HGT): MAI rete. Usata dal bake in `gen-tile`/merge_geo_tile per non
    stallare i batch paralleli sull'API (rate-limit). None se non disponibile."""
    nrow, ncol = grid_shape(bbox)
    npts = nrow * ncol

    cached = _load_cache(key, bbox)
    if cached is not None and len(cached) == npts:
        return cached

    local = _compute_local_grid(bbox)
    if local is None:
        return None
    _save_cache(key, local, bbox)
    return local


def elevation_grid(key: str, bbox: List[float]) -> List[float]:
    """Altitudini (m) su una griglia regolare in gradi sul bbox della tile.

    Il ritorno e' row-major (lat esterna), ciascuna riga percorre la
    longitudine da min a max, con ncol = grid_shape(...)[1] colonne.
    I punti mancanti (mare/senza dato) vengono riempiti col valore valido
    PIU' VICINO (`fill_voids`), non lasciati a 0: 0 significa mare e produceva
    discontinuita' artificiali terra-mare.

    Ordine delle fonti: cache disco -> SRTM HGT locale -> API OpenTopoData.
    """
    nrow, ncol = grid_shape(bbox)
    npts = nrow * ncol

    cached = _load_cache(key, bbox)
    if cached is not None and len(cached) == npts:
        return cached

    local = _compute_local_grid(bbox)
    if local is not None:
        _save_cache(key, local, bbox)
        return local

    latmin, lonmin, latmax, lonmax = bbox
    raw: List[Optional[float]] = [None] * npts
    # Per le richieste generiamo i punti fino a _MAX_PER_REQ a chiamata.
    locs = [
        f"{latmin + (latmax - latmin) * (i // ncol) / (nrow - 1)},"
        f"{lonmin + (lonmax - lonmin) * (i % ncol) / (ncol - 1)}"
        for i in range(npts)
    ]
    valid = 0
    for chunk_start in range(0, npts, _MAX_PER_REQ):
        chunk = locs[chunk_start:chunk_start + _MAX_PER_REQ]
        vals = _request("|".join(chunk))
        for k, v in enumerate(vals):
            if v is not None:
                raw[chunk_start + k] = v
                valid += 1
        time.sleep(0.05)

    if valid < MIN_VALID_RATIO * npts:
        # copertura insufficiente: niente griglia inventata. Il tile resta
        # senza `ele` e il client lo tratta come mondo piatto (camminabile).
        grid = [0.0] * npts
    else:
        grid = fill_voids(raw, nrow, ncol)

    _save_cache(key, grid, bbox)
    return grid


if __name__ == "__main__":
    import sys
    k = sys.argv[1] if len(sys.argv) > 1 else "IT_020_064"
    bb = [35.8, 12.744, 35.89, 12.865]
    g = elevation_grid(k, bb)
    nr, nc = grid_shape(bb)
    print(f"{k}: griglia {nr}x{nc} = {len(g)} punti")
    print("min %.1f  max %.1f  mean %.1f" % (min(g), max(g), sum(g) / len(g)))
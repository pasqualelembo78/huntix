# Bounding box approssimativi (latmin, lonmin, latmax, lonmax) delle 20 regioni
# italiane. Usati da `aggiorna_regione.sh` per selezionare le tile di terra
# (per centroidi → tile appena fuori bordo possono essere incluse: innocuo).
# puglia e molise sono verificati sui conteggi reali; gli altri sono indicativi
# (generosi): eventuali tile oltre confine sono comunque dati validi, e quelle
# mancanti vengono generate on-demand dal server al primo accesso.
REGIONS = {
    # ── nord ───────────────────────────────────────────────
    "piemonte":              (44.00,  6.55, 46.60,  9.10),
    "valle-d-aosta":         (45.35,  6.60, 46.25,  8.05),
    "lombardia":             (44.60,  8.30, 46.70, 11.40),
    "trentino-alto-adige":   (45.55, 10.30, 47.20, 12.65),
    "veneto":                (44.65, 10.20, 46.75, 13.15),
    "friuli-venezia-giulia": (45.45, 12.35, 46.75, 13.95),
    "liguria":               (43.80,  7.45, 44.75, 10.35),
    "emilia-romagna":        (43.70,  9.20, 45.20, 12.85),
    # ── centro ─────────────────────────────────────────────
    "toscana":               (42.30,  9.80, 44.50, 12.30),
    "umbria":                (42.30, 11.80, 43.65, 13.05),
    "marche":                (42.30, 12.70, 44.05, 14.25),
    "lazio":                 (40.90, 11.60, 42.95, 13.85),
    "abruzzo":               (41.55, 13.00, 42.95, 14.85),
    "molise":                (41.20, 13.78, 42.05, 15.20),  # verificato: 99 tile
    # ── sud ────────────────────────────────────────────────
    "campania":              (39.90, 13.30, 41.90, 15.70),
    "puglia":                (39.78, 15.10, 42.05, 18.55),  # verificato: 412 tile
    "basilicata":            (39.85, 15.45, 41.35, 16.95),
    "calabria":              (37.80, 15.60, 40.30, 17.35),
    # ── isole ──────────────────────────────────────────────
    "sicilia":               (36.60, 11.90, 38.40, 15.70),
    "sardegna":              (38.70,  8.10, 41.30,  9.90),
}
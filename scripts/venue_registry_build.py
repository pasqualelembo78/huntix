#!/usr/bin/env python3
"""Genera il registro degli impianti sportivi REALI da OpenStreetMap.

Il registro e' un asset statico (Unity Resources/TextAsset): nessuna rete a
runtime, nessuna modifica ai wire geo/graph e nessun cambio di
TileClient.CacheVersion. La sorgente e' il dump OSM locale gia' usato dal
preprocessing (data/<paese>-latest.osm.pbf), filtrato in sola lettura.

Regole applicate:

* stadio = `leisure=stadium` con sport calcio/soccer: per ogni citta' viene
  scelto UN solo impianto principale (il piu' grande/documentato), non uno
  a caso per chunk;
* ippodromo = `sport=horse_racing` con leisure da pista/centro sportivo o
  nome che dichiara l'ippodromo: anche qui un solo impianto per citta';
* coordinate = baricentro dell'impronta OSM (non il centro del chunk);
* l'indirizzo viene dai tag addr:*; per i due casi guida (Foggia e
  Castelluccio dei Sauri) e' arricchito con l'indirizzo verificato via
  Nominatim e conservato in OVERRIDES.

Uso:
    python3 scripts/venue_registry_build.py [--country italy]
"""
import argparse
import hashlib
import json
import math
import os
import subprocess
import tempfile
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_PBF = os.path.join(
    ROOT, "backend", "preprocessing", "data", "italy-latest.osm.pbf")
DEFAULT_OUT = os.path.join(
    ROOT, "unity-project", "Assets", "Resources", "Venues", "italy_venues.json")

# Casi guida richiesti esplicitamente: l'indirizzo e' verificato su Nominatim
# (ODbL) e la priority garantisce che siano le prime destinazioni nei menu.
OVERRIDES = {
    69969260: {
        "city": "Foggia",
        "address": "Via Vincenzo Gioberti, 71122 Foggia",
        "priority": 1000,
    },
    1350956337: {
        "city": "Castelluccio dei Sauri",
        "address": "Strada Provinciale 110 di Ortanova, 71025 Castelluccio dei Sauri (FG)",
        "priority": 1000,
    },
}

BAD_STADIUM_WORDS = (
    "tennis", "palasport", "basket", "hockey", "calcetto", "campetto",
    "ingresso", "biglietteria", "tribuna", "settore", "rugby")


def run(cmd):
    subprocess.check_call(cmd, stdout=subprocess.DEVNULL)


def osmium_extract(pbf, tmp):
    """Estrae solo gli oggetti che interessano, senza toccare i PBF filtrati."""
    venues = os.path.join(tmp, "venues.osm.pbf")
    places = os.path.join(tmp, "places.osm.pbf")
    run([
        "osmium", "tags-filter", "-o", venues, pbf,
        "w/leisure=stadium", "r/leisure=stadium", "n/leisure=stadium",
        "w/leisure=racetrack", "r/leisure=racetrack", "n/leisure=racetrack",
        "w/sport=horse_racing", "r/sport=horse_racing", "n/sport=horse_racing",
    ])
    run([
        "osmium", "tags-filter", "-o", places, pbf,
        "n/place=city,town,village", "r/place=city,town,village",
    ])
    return venues, places


def export_geojson(pbf, tmp, name):
    out = os.path.join(tmp, name + ".geojsonl")
    run(["osmium", "export", "-u", "type_id", "-f", "geojsonseq",
         "-o", out, pbf])
    return out


def read_features(path):
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip("\x1e\n\r ")
            if not line:
                continue
            try:
                yield json.loads(line)
            except ValueError:
                continue


def coordinates(geom):
    """Appiattisce le coordinate di una geometria GeoJSON."""
    out = []
    if not geom:
        return out

    def walk(value):
        if isinstance(value, list) and len(value) == 2 and \
                all(isinstance(v, (int, float)) for v in value):
            out.append((float(value[0]), float(value[1])))
        elif isinstance(value, list):
            for item in value:
                walk(item)

    walk(geom.get("coordinates"))
    return out


def ring_area(points):
    total = 0.0
    for i, (x1, y1) in enumerate(points):
        x2, y2 = points[(i + 1) % len(points)]
        total += x1 * y2 - x2 * y1
    return total * 0.5


def ring_centroid(points):
    area2 = 0.0
    cx = cy = 0.0
    for i, (x1, y1) in enumerate(points):
        x2, y2 = points[(i + 1) % len(points)]
        cross = x1 * y2 - x2 * y1
        area2 += cross
        cx += (x1 + x2) * cross
        cy += (y1 + y2) * cross
    if abs(area2) < 1e-12:
        n = float(len(points))
        return (sum(p[0] for p in points) / n, sum(p[1] for p in points) / n)
    return (cx / (3.0 * area2), cy / (3.0 * area2))


def representative(geom):
    """Centro, ingombro in metri e asse principale dell'impronta OSM."""
    kind_name = geom.get("type")
    pts = coordinates(geom)
    if not pts:
        return None
    if kind_name == "Point":
        lon, lat = pts[0]
        return lat, lon, 0.0, 0.0, 0.0, 0.0

    if kind_name == "Polygon":
        rings = geom.get("coordinates") or []
        outer = rings[0] if rings else []
        shape = [(float(p[0]), float(p[1])) for p in outer]
    elif kind_name == "MultiPolygon":
        polys = geom.get("coordinates") or []
        shape = []
        best = None
        for poly in polys:
            ring = [(float(p[0]), float(p[1])) for p in (poly[0] if poly else [])]
            if not ring:
                continue
            if best is None or abs(ring_area(ring)) > abs(ring_area(best)):
                best = ring
        shape = best or []
    else:
        shape = pts

    if not shape:
        return None
    lon, lat = ring_centroid(shape)
    lons = [p[0] for p in shape]
    lats = [p[1] for p in shape]
    span_lat = (max(lats) - min(lats)) * 110540.0
    span_lon = (max(lons) - min(lons)) * 111320.0 * \
        math.cos(math.radians(lat))

    # Asse principale (PCA): solo metadati, il client non ruota il modello.
    mx = 111320.0 * math.cos(math.radians(lat))
    my = 110540.0
    xs = [(p[0] - lon) * mx for p in shape]
    ys = [(p[1] - lat) * my for p in shape]
    n = float(len(xs))
    if n > 2:
        sxx = sum(x * x for x in xs) / n
        syy = sum(y * y for y in ys) / n
        sxy = sum(x * y for x, y in zip(xs, ys)) / n
        axis = math.degrees(0.5 * math.atan2(2.0 * sxy, sxx - syy))
    else:
        axis = 0.0
    if axis > 90.0:
        axis -= 180.0
    if axis < -90.0:
        axis += 180.0
    return lat, lon, span_lat, span_lon, axis, abs(ring_area(shape))


def osm_id(feature):
    """`osmium export -u type_id` mette l'id nel campo top-level `id`."""
    raw = str(feature.get("id", ""))
    if raw and raw[0] in "wrn":
        raw = raw[1:]
    try:
        return int(raw)
    except (TypeError, ValueError):
        return 0


def has_sport(tags, *wanted):
    text = str(tags.get("sport", "")).lower()
    return any(word in text.split(";") for word in wanted)


def classify(tags):
    name = str(tags.get("name", ""))
    low = name.lower()
    if tags.get("leisure") == "stadium" and \
            has_sport(tags, "soccer", "football") and \
            not any(word in low for word in BAD_STADIUM_WORDS):
        return "stadio"
    if has_sport(tags, "horse_racing") and (
            tags.get("leisure") in ("racetrack", "track", "sports_centre") or
            any(word in low for word in
                ("ippodromo", "hippodrome", "racecourse", "racetrack"))):
        return "ippodromo"
    return None


def candidate_score(kind, tags, area_m2):
    name = str(tags.get("name", "")).lower()
    score = min(area_m2 / 20000.0, 40.0)
    if tags.get("wikidata") or tags.get("wikipedia"):
        score += 30.0
    if "stadio" in name or "ippodromo" in name:
        score += 25.0
    if tags.get("addr:street"):
        score += 15.0
    if tags.get("start_date"):
        score += 5.0
    for bad in BAD_STADIUM_WORDS if kind == "stadio" else ("scuola", "riding"):
        if bad in name:
            score -= 50.0
    return score


def load_places(path):
    places = []
    for feature in read_features(path):
        tags = feature.get("properties") or {}
        rep = representative(feature.get("geometry"))
        if rep is None or not tags.get("name"):
            continue
        lat, lon = rep[0], rep[1]
        places.append((lat, lon, str(tags["name"])))
    return places


def nearest_city(lat, lon, places):
    best = None
    best_d = 0.25 ** 2  # ~25 km: oltre, la citta' non e' affidabile
    coslat = math.cos(math.radians(lat))
    for plat, plon, name in places:
        dlat = plat - lat
        dlon = (plon - lon) * coslat
        d2 = dlat * dlat + dlon * dlon
        if d2 < best_d:
            best_d = d2
            best = name
    return best or "Italia"


def address_of(tags, city):
    parts = []
    street = tags.get("addr:street")
    if street:
        parts.append(str(street))
        if tags.get("addr:housenumber"):
            parts[-1] += ", " + str(tags["addr:housenumber"])
    if tags.get("addr:postcode"):
        parts.append(str(tags["addr:postcode"]))
    if city:
        parts.append(city)
    return ", ".join(parts) if parts else city


def build(country, pbf, out_path):
    with tempfile.TemporaryDirectory(prefix="huntix-venues-") as tmp:
        venues_pbf, places_pbf = osmium_extract(pbf, tmp)
        venues_json = export_geojson(venues_pbf, tmp, "venues")
        places_json = export_geojson(places_pbf, tmp, "places")
        places = load_places(places_json)

        candidates = []
        for feature in read_features(venues_json):
            tags = feature.get("properties") or {}
            kind = classify(tags)
            if kind is None:
                continue
            rep = representative(feature.get("geometry"))
            if rep is None:
                continue
            lat, lon, span_lat, span_lon, axis, area = rep
            oid = osm_id(feature)
            override = OVERRIDES.get(oid, {})
            city = override.get("city") or tags.get("addr:city") or \
                tags.get("addr:town") or tags.get("addr:village") or \
                nearest_city(lat, lon, places)
            candidates.append({
                "kind": kind,
                "osmId": oid,
                "osmType": (str(feature.get("id", ""))[:1] or "w"),
                "name": str(tags.get("name") or city),
                "city": str(city),
                "address": override.get("address") or address_of(tags, city),
                "lat": round(lat, 7),
                "lng": round(lon, 7),
                "spanLatM": round(span_lat, 1),
                "spanLonM": round(span_lon, 1),
                "axisDeg": round(axis, 1),
                "_area": area,
                "_score": candidate_score(kind, tags, area) +
                          float(override.get("priority", 0)),
            })

    # Lo stesso impianto puo' avere piu' way/node: tiene il piu' documentato.
    deduped = {}
    for item in candidates:
        key = (item["kind"], round(item["lat"] / 0.002),
               round(item["lng"] / 0.002))
        old = deduped.get(key)
        if old is None or item["_score"] > old["_score"]:
            deduped[key] = item

    # Una citta', un impianto per tipo: niente stadi sparsi per hash di chunk.
    groups = defaultdict(list)
    for item in deduped.values():
        groups[(item["kind"], item["city"].casefold())].append(item)
    selected = []
    for items in groups.values():
        selected.append(max(items, key=lambda i: (i["_score"], i["osmId"])))

    selected.sort(key=lambda i: (-i["_score"], i["kind"], i["city"], i["name"]))
    venues = []
    for item in selected:
        item.pop("_area", None)
        priority = item.pop("_score")
        item["priority"] = round(priority, 2)
        item["id"] = item["kind"] + "-osm-" + str(item["osmId"])
        venues.append(item)

    doc = {
        "schema": 1,
        "source": "OpenStreetMap contributors (ODbL) — dump locale " +
                  os.path.basename(pbf),
        "country": country,
        "rule": "un solo impianto principale per citta'; coordinate prese "
                "dall'impronta OSM reale",
        "venues": venues,
    }
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    meta = out_path + ".meta"
    if not os.path.isfile(meta):
        guid = hashlib.md5(
            ("Venues/" + os.path.basename(out_path)).encode()).hexdigest()
        with open(meta, "w", encoding="utf-8") as handle:
            handle.write(
                "fileFormatVersion: 2\nguid: " + guid + "\n"
                "TextScriptImporter:\n"
                "  externalObjects: {}\n"
                "  userData: \n"
                "  assetBundleName: \n"
                "  assetBundleVariant: \n")

    stadia = [v for v in venues if v["kind"] == "stadio"]
    races = [v for v in venues if v["kind"] == "ippodromo"]
    print("Scritto", out_path)
    print("Stadi:", len(stadia), "Ippodromi:", len(races))
    for kind, rows in (("stadio", stadia), ("ippodromo", races)):
        for item in rows:
            if item["osmId"] in OVERRIDES:
                print("  guida", kind, item["name"], item["lat"], item["lng"],
                      item["address"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--country", default="italy")
    parser.add_argument("--pbf", default=DEFAULT_PBF)
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()
    build(args.country, args.pbf, args.out)


if __name__ == "__main__":
    main()

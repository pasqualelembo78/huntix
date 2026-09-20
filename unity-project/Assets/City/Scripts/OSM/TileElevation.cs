using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Servizio centralizzato per l'ELEVAZIONE REALE (DEM/SRTM) del terreno.
    /// Le tile geo (iniettate dal server con la griglia 'ele') vengono registrate
    /// qui quando caricano; chiunque (terreno, strade, spawn giocatore, acqua)
    /// chiede l'altitudine in metri s.l.m. a una data lat/lon con interpolazione
    /// bilineare sulla griglia DEM della tile piu' vicina.
    ///
    /// Se nessuna tile DEM copre il punto, ritorna 0 (vedi: di default piatto).
    /// Il registro e' pensato per il main thread (sistemi di build/gioco).
    /// </summary>
    public static class TileElevation
    {
        private class Entry
        {
            public double latMin, lonMin, latMax, lonMax;
            public int nrow, ncol;
            public float[] ele;
        }

        private static readonly Dictionary<string, Entry> _entries =
            new Dictionary<string, Entry>();

        // Il MISS sul DEM (quei punti che nessuna tile copre ancora, es. il
        // terreno/strade costruite prima dell'arrivo delle geo) NON puo' usare
        // LogThrottled per-chiave: le lat/lon sono uniche e ogni riga passerebbe
        // il throttle -> flood della logcat. Limitiamo per finestra temporale e
        // teniamo il contatore cumulativo.
        private static float _nextMissLogTime = 0f;
        private static int _missCount = 0;

        /// <summary>Svuota il registro (change of city/reload).</summary>
        public static void Reset()
        {
            _entries.Clear();
            _missCount = 0;
            _nextMissLogTime = 0f;
        }

        /// <summary>Registra la griglia DEM di una geo doc (idempotente per tile).
        /// Ritorna true SOLO se la tile e' stata effettivamente aggiunta adesso:
        /// chi chiama puo' usarlo per rimettere in coda i layer gia' costruiti
        /// che ora hanno il DEM di questa tile campionabile (strade sepolte).</summary>
        public static bool Register(TileGeoDoc geo)
        {
            if (geo == null || string.IsNullOrEmpty(geo.tile) ||
                geo.ele == null || geo.ele.Length == 0 ||
                geo.ele_nrow <= 1 || geo.ele_ncol <= 1 ||
                geo.bbox == null || geo.bbox.Length < 4)
                return false;

            if (_entries.ContainsKey(geo.tile)) return false;
            _entries[geo.tile] = new Entry
            {
                latMin = geo.bbox[0], lonMin = geo.bbox[1],
                latMax = geo.bbox[2], lonMax = geo.bbox[3],
                nrow = geo.ele_nrow, ncol = geo.ele_ncol,
                ele = geo.ele
            };
            OsmDiag.Log("[DEM] Register tile=" + geo.tile +
                " rows=" + geo.ele_nrow + " cols=" + geo.ele_ncol +
                " ele=" + geo.ele.Length + " totali=" + _entries.Count);
            return true;
        }

        /// <summary>Rimuove una tile dal registro (unload).</summary>
        public static void Unregister(string tileKey)
        {
            if (tileKey != null) _entries.Remove(tileKey);
        }

        /// <summary>Altezza (m s.l.m.) alla lat/lon data, interpolata sulla tile
        /// la cui bbox contiene il punto. 0 se nessuna copre il punto o non c'e' DEM.</summary>
        public static float HeightAt(double lat, double lon)
        {
            foreach (var e in _entries.Values)
            {
                if (lat < e.latMin || lat > e.latMax ||
                    lon < e.lonMin || lon > e.lonMax)
                    continue;
                return SampleBilinear(e.ele, e.nrow, e.ncol,
                    e.latMin, e.lonMin, e.latMax, e.lonMax, lat, lon);
            }
            _missCount++;
            float now = UnityEngine.Time.time;
            if (now >= _nextMissLogTime)
            {
                _nextMissLogTime = now + 2f;
                OsmDiag.LogThrottled("DEM",
                    "[DEM] MISS nessuna tile DEM copre (tile registrate=" +
                    _entries.Count + ", miss totali=" + _missCount +
                    ") ultimo=" + lat.ToString("F4") + "," +
                    lon.ToString("F4"),
                    2f);
            }
            return 0f;
        }

        /// <summary>Altezza in coordinate world date (x=lon, z=lat).</summary>
        public static float HeightAtWorld(Vector3 world)
        {
            var g = WorldOrigin.ToGeo(world);
            return HeightAt(g.lat, g.lng);
        }

        /// <summary>Normale (lato su) del terreno in un punto world, calcolata
        /// con le derivate finite della griglia DEM campionate in +-step metri
        /// lungo est (x) e nord (z). Torna Vector3.up se il DEM non copre il
        /// punto o se le altezze sono costanti.</summary>
        public static Vector3 SurfaceNormalWorld(Vector3 world, float step = 2f)
        {
            if (step <= 0f) return Vector3.up;
            float hL = HeightAtWorld(world - Vector3.right * step);
            float hR = HeightAtWorld(world + Vector3.right * step);
            float hD = HeightAtWorld(world - Vector3.forward * step);
            float hU = HeightAtWorld(world + Vector3.forward * step);
            Vector3 n = new Vector3(hL - hR, 2f * step, hD - hU);
            if (n.sqrMagnitude < 1e-8f) return Vector3.up;
            return n.normalized;
        }

        /// <summary>"Up" di appoggio per i corpi mobili (auto, pedoni, player):
        /// la normale del pendio DEM ma limitata a maxDeg dalla verticale, cosi'
        /// un dosso o un pendio ripido fa inclinare l'oggetto (sospensioni /
        /// sbilanciamento) senza mai accappottarlo.</summary>
        public static Vector3 SlopeUpAtWorld(Vector3 world, float maxDeg = 25f,
            float step = 2f)
        {
            Vector3 n = SurfaceNormalWorld(world, step);
            float ang = Vector3.Angle(Vector3.up, n);
            if (ang <= 0.01f) return Vector3.up;
            float k = Mathf.Clamp01(maxDeg / ang);
            return Vector3.Slerp(Vector3.up, n, k).normalized;
        }

        private static float SampleBilinear(float[] ele, int nrow, int ncol,
            double latMin, double lonMin, double latMax, double lonMax,
            double lat, double lon)
        {
            double fy = (lat - latMin) / (latMax - latMin) * (nrow - 1);
            double fx = (lon - lonMin) / (lonMax - lonMin) * (ncol - 1);
            fy = fy < 0 ? 0 : (fy > nrow - 1 ? nrow - 1 : fy);
            fx = fx < 0 ? 0 : (fx > ncol - 1 ? ncol - 1 : fx);
            int y0 = (int)fy, x0 = (int)fx;
            int y1 = y0 + 1 < nrow ? y0 + 1 : y0;
            int x1 = x0 + 1 < ncol ? x0 + 1 : x0;
            float dy = (float)(fy - y0);
            float dx = (float)(fx - x0);

            float v00 = ele[y0 * ncol + x0];
            float v10 = ele[y1 * ncol + x0];
            float v01 = ele[y0 * ncol + x1];
            float v11 = ele[y1 * ncol + x1];
            return Mathf.Lerp(Mathf.Lerp(v00, v01, dx),
                             Mathf.Lerp(v10, v11, dx), dy);
        }
    }
}

using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Riempie ogni chunk con veicoli Kenney: parcheggiati ai lati della strada
    /// e traffico in movimento. TUTTO e' DETERMINISTICO: il seed deriva solo
    /// dall'indice del chunk, quindi due giocatori qualsiasi vedono esattamente
    /// le stesse auto (stesso modello, stesso posto) sin dal primo avvio.
    /// I codici veicolo sono stabili ("V{cx}{cy}{slot}") e sono la chiave di
    /// proprieta' lato server: comprare un'auto su un device la rende non
    /// acquistabile sugli altri. Culling a distanza per tenere basso il costo.
    /// </summary>
    public class ChunkVehiclePopulator : MonoBehaviour
    {
        // Densita' parcheggio: slot ogni N metri PER LATO. In centro/residenziale
        // si riempie quasi tutto (righe continue), sulle arterie piu' rado.
        private const float SpacingUrban = 6f;
        private const float SpacingMain = 11f;
        private const double FillUrban = 0.85;
        private const double FillMain = 0.45;
        private const float LaneOffset = 2.7f;      // distanza dal centro strada
        private const float MarginFromJunction = 5f;  // niente auto sull'incrocio
        private const int MaxParkedPerChunk = 100;
        // densita' urbana (edifici/km2 della tile come proxy): limiti del
        // fattore e lato della tile in chunk (le tile sono 10x10 chunk)
        private const float MinDensityFactor = 0.35f;
        private const float MaxDensityFactor = 1.4f;
        private const float TileChunksPerSide = 10f;
        // 170 m tagliava troppo aggressive: a media 77 auto/chunk sparse su
        // 1 km2 si vedevano poche auto alla volta. 260 m tiene il costo
        // render basso (Kenney low-poly) senza strade deserte.
        private const float CullRadius = 500f;
        private const float CullInterval = 0.6f;
        private const int GLOBAL_VEHICLE_CAP = 20000;

        public static ChunkVehiclePopulator Instance { get; private set; }

        private readonly List<GameObject> tracked = new List<GameObject>();
        private Transform player;
        private float nextCull;
        private int _cullTicks;

        public static ChunkVehiclePopulator Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("ChunkVehiclePopulator");
            DontDestroyOnLoad(go);
            return go.AddComponent<ChunkVehiclePopulator>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        /// <summary>Seed stabile da coordinate chunk (indipendente da piattaforma).</summary>
        public static int SeedFor(Vector2Int c)
        {
            unchecked
            {
                uint h = 2166136261u ^ (uint)c.x;
                h = (h ^ (uint)c.y) * 16777619u;
                h ^= h >> 13; h *= 0x5bd1e995u; h ^= h >> 15;
                return (int)h;
            }
        }

        /// <summary>
        /// Chiamato da ChunkBuilder per ogni chunk costruito. toLocal converte
        /// geo -> coordinate LOCALI del chunk root (rebase-safe).
        /// </summary>
        public static void Populate(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            var host = Ensure();
            if (host == null || chunk?.geo?.roads == null || chunk.root == null)
                return;

            var rng = new System.Random(SeedFor(chunk.index));
            var spawner = City.Vehicle.VehicleSpawnManager.Instance;
            if (spawner == null)
            {
                // non deve mai succedere (lo spawner nasce prima del primo
                // tick del manager): se capita lo vediamo nei log invece di
                // perdere le auto del chunk in silenzio
                OsmDiag.Log("[ChunkVehiclePopulator] chunk " + chunk.index +
                    ": VehicleSpawnManager assente, niente auto");
                return;
            }

            // ── densita' urbana: auto proporzionali alla citta' ──
            // Proxy senza dati esterni: edifici/km2 della tile. Foggia ~33 ->
            // f=1.0 (calibrazione attuale); Roma ~320 -> saturazione 1.4;
            // paese ~3/km2 -> minimo 0.35. La radice comprime le differenze
            // (Roma +40% di auto, non x10). La tile e' identica su tutti i
            // client quindi il fattore non rompe la deterministica dei codici.
            // NB: cambiare la formula invalida i codici gia' venduti.
            float blds = chunk.geo.buildings != null ? chunk.geo.buildings.Length : 0;
            float density = Mathf.Clamp(
                Mathf.Sqrt(blds / (TileChunksPerSide * TileChunksPerSide * 30f)),
                MinDensityFactor, MaxDensityFactor);
            int maxParked = (int)Mathf.Clamp(
                MaxParkedPerChunk * density, 40f, 260f);
            // Global cap: prevent memory/perf issues across many chunks
            int globalTracked = host.tracked.Count;
            if (globalTracked >= GLOBAL_VEHICLE_CAP)
            {
                maxParked = 0;
                OsmDiag.Log("[ChunkVehiclePopulator] chunk " + chunk.index +
                    ": GLOBAL CAP reached (" + globalTracked + "/" + GLOBAL_VEHICLE_CAP + "), skipping");
            }

            // distribuzione uniforme: se le prime vie della lista basterebbero
            // a saturare il budget, gli slot finirebbero tutti li' (spesso su
            // arterie periferiche) col centro deserto. Misuro la lunghezza
            // guidabile totale e allargo lo spacing per ripartire il budget
            // su tutte le vie. Deterministico: geometria + seed del chunk.
            float totLen = 0f;
            foreach (var rd in chunk.geo.roads)
            {
                if (rd?.pts == null || rd.pts.Length < 2) continue;
                if (!IsDrivable(rd.hw ?? "")) continue;
                for (int i = 1; i < rd.pts.Length; i++)
                {
                    Vector3 a = toLocal(rd.pts[i - 1]);
                    Vector3 b = toLocal(rd.pts[i]);
                    a.y = 0f; b.y = 0f;
                    totLen += Vector3.Distance(a, b);
                }
            }
            float fillU = (float)(FillUrban * density);
            float spacingScale = (totLen > 1f && maxParked > 0)
                ? Mathf.Clamp(totLen * fillU / (maxParked * SpacingUrban),
                    1f, 10f)
                : 1f;

            int parked = 0, slot = 0;
            string prefix = "V" + chunk.index.x.ToString("0000") +
                            chunk.index.y.ToString("0000");

            foreach (var road in chunk.geo.roads)
            {
                if (road?.pts == null || road.pts.Length < 2) continue;
                string hw = road.hw ?? "";
                if (!IsDrivable(hw)) continue;

                // ── parcheggi lungo i lati ──
                bool main = IsMainRoad(hw);
                float spacing = (main ? SpacingMain : SpacingUrban) *
                    spacingScale;
                // l'occupazione scala con la densita' della citta'
                double fill = (main ? FillMain : FillUrban) * density;

                Vector3 prev = toLocal(road.pts[0]);
                float carry = rng.NextDouble() < 0.5 ? spacing * 0.5f : 0f;
                for (int i = 1; i < road.pts.Length &&
                        parked < maxParked; i++)
                {
                    Vector3 cur = toLocal(road.pts[i]);
                    Vector3 seg = cur - prev;
                    seg.y = 0f;
                    float segLen = seg.magnitude;
                    if (segLen > 0.5f)
                    {
                        Vector3 dir = seg / segLen;
                        // destra della direzione di marcia (guida a destra:
                        // auto orientate come il flusso del lato corrispondente)
                        Vector3 right = Vector3.Cross(Vector3.up, dir);

                        float d = carry;
                        while (d <= segLen && parked < maxParked)
                        {
                            float t = d / segLen;
                            // margine dagli incroci (inizio/fine segmento)
                            if (t * segLen >= MarginFromJunction &&
                                (1f - t) * segLen >= MarginFromJunction &&
                                rng.NextDouble() < fill)
                            {
                                bool leftSide = rng.Next(2) == 0;
                                Vector3 pos = prev + seg * t +
                                    right * LaneOffset * (leftSide ? -1f : 1f);
                                pos.y = 0.02f;
                                if (bounds.Contains(new Vector2(pos.x, pos.z)))
                                {
                                    float angle = Mathf.Atan2(dir.x, dir.z) *
                                        Mathf.Rad2Deg + (leftSide ? 180f : 0f);
                                    int idx = rng.Next(
                                        VehicleSpawnManager.Catalogue.Length);
                                    // i pullman non si parcheggiano sul ciglio
                                    // delle strade residenziali: se il modello
                                    // scelto e' un pullman lo sostituisco con
                                    // una berlina (stesso slot, RNG invariato,
                                    // quindi i codici venduti restano validi)
                                    if (VehicleSpawnManager.IsStreetBus(idx))
                                        idx = 0;
                                    // Il codice include l'indice del catalogo
                                    // (ultime 2 cifre) e avanza con uno slot
                                    // counter SEPARATO da "parked": cosi' il
                                    // codice di ogni slot non dipende da
                                    // quante auto sono gia' state comprate ->
                                    // identico su tutti i client.
                                    string code = prefix + "_" +
                                        slot.ToString("000") + "_" +
                                        idx.ToString("00");
                                    slot++;
                                    // Slot venduto = l'auto esce dal pool
                                    // deterministico: non va creata qui, la
                                    // sua istanza vera nasce al punto di
                                    // parcheggio salvato sul server.
                                    if (!VehicleOwnershipApi.IsSold(code))
                                    {
                                        var go = City.Vehicle.VehicleSpawnManager
                                            .BuildVehicle(chunk.root.transform,
                                                VehicleSpawnManager.Catalogue[idx],
                                                pos, angle, code);
                                        if (go != null)
                                        {
                                            host.Track(go); parked++;
                                            if (parked == 1)
                                                OsmDiag.Log("[VehPop] " + chunk.key +
                                                    " primo localPos=" + pos.ToString("F1") +
                                                    " root=" + chunk.root.transform.position.ToString("F1") +
                                                    " attivo=" + go.activeInHierarchy);
                                        }
                                    }
                                }
                            }
                            d += spacing;
                        }
                        carry = d - segLen;
                    }
                    prev = cur;
                }
                if (parked >= maxParked)
                    break;
            }

            // Il traffico in MOVIMENTO non nasce più qui (percorsi avanti-indietro
            // su strada singola): ora segue la rete stradale unificata via
            // TileRoadNetwork (grafo dalle tile, pathfinding A*, svolte agli
            // incroci, cross-tile). Qui restano solo parcheggi e veicoli dal server.
            SpawnParkedFromServer(chunk, toLocal, bounds, host);

            OsmDiag.Log("[ChunkVehiclePopulator] chunk " + chunk.index +
                ": parcheggi=" + parked +
                " (movimento su rete in TileRoadNetwork)" +
                " densita=" + density.ToString("F2"));
        }

        /// <summary>
        /// Fa nascere nel chunk i veicoli VENDUTI il cui parcheggio (lat/lon
        /// salvato sul server al momento dell'uscita) cade dentro i confini
        /// del chunk. Il modello e le dimensioni tornano dalle ultime cifre
        /// del codice ("..._NN"), quindi nessun dato extra sul server.
        /// </summary>
        private static void SpawnParkedFromServer(ChunkData chunk,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds,
            ChunkVehiclePopulator host)
        {
            int restored = 0;
            foreach (var kv in VehicleOwnershipApi.SoldSnapshot())
            {
                // nel garage = non e' in strada; rubata = il ladro la sta
                // spostando (ricompare solo se ritrovata abbandonata)
                if (kv.Value.inGarage || kv.Value.stolen) continue;

                // gia' materializzata fuori dal flusso (es. auto rimorchiata
                // all'officina): niente duplicati
                if (VehicleSpawnManager.IsActiveOwned(kv.Key)) continue;

                Vector3 p = toLocal(new GeoLL { a = kv.Value.lat, o = kv.Value.lon });
                p.y = 0.02f;
                if (!bounds.Contains(new Vector2(p.x, p.z))) continue;

                // modello: prima il nome salvato dal server, poi il vecchio
                // suffisso "_NN" (le auto da concessionaria hanno codici
                // "D<poi>_<hex>" dove NN non e' un indice catalogo)
                City.Vehicle.VehicleSpawnManager.VehicleDef def;
                if (string.IsNullOrEmpty(kv.Value.model) ||
                    !VehicleSpawnManager.TryGetDef(kv.Value.model, out def))
                {
                    int idx = ParseCatalogueIndex(kv.Key);
                    def = idx >= 0 ? VehicleSpawnManager.Catalogue[idx]
                                   : VehicleSpawnManager.Catalogue[0];
                }
                var go = City.Vehicle.VehicleSpawnManager.BuildVehicle(
                    chunk.root.transform, def, p, (float)kv.Value.heading, kv.Key);
                if (go != null)
                {
                    host.Track(go);
                    VehicleOwnershipApi.Instance?.ApplyOwnedState(go, kv.Key);
                    restored++;
                }
            }
            if (restored > 0)
                OsmDiag.Log("[ChunkVehiclePopulator] chunk " + chunk.index +
                    ": ripristinati " + restored + " veicoli posseduti");
        }

        /// <summary>Estrae l'indice catalogo dal suffisso "_NN" del codice.</summary>
        private static int ParseCatalogueIndex(string code)
        {
            if (string.IsNullOrEmpty(code)) return -1;
            int us = code.LastIndexOf('_');
            if (us < 0 || us == code.Length - 1) return -1;
            return int.TryParse(code.Substring(us + 1), out int idx) &&
                idx < VehicleSpawnManager.Catalogue.Length ? idx : -1;
        }

        /// <summary>Registra un veicolo per il culling a distanza.</summary>
        private void Track(GameObject go)
        {
            if (go != null) tracked.Add(go);
        }

        /// <summary>Culling periodico: attiva solo i veicoli vicini al player.</summary>
        private void Update()
        {
            if (Time.unscaledTime < nextCull) return;
            nextCull = Time.unscaledTime + CullInterval;

            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            player = null;
            if (mgr != null) player = mgr.target;

            tracked.RemoveAll(v => v == null);
            if (player == null) return;
            Vector3 pp = player.position;
            float r2 = CullRadius * CullRadius;

            for (int i = 0; i < tracked.Count; i++)
            {
                var v = tracked[i];
                bool want = (v.transform.position - pp).sqrMagnitude < r2;
                if (v.activeSelf != want) v.SetActive(want);
            }

            // diagnostica "auto invisibili": ogni 4 s fotografa lo stato del
            // culling (quante auto tracciate/quante accese, dove pensa di
            // stare il riferimento e un esempio con posizione mondiale e
            // radice) per capire se le auto sono spente dal culling, in una
            // posizione errata o distrutte
            _cullTicks++;
            if (_cullTicks % 10 == 1 && tracked.Count > 0)
            {
                int on = 0, near = 0, entro500 = 0;
                float minD2 = float.MaxValue;
                GameObject best = null;
                for (int i = 0; i < tracked.Count; i++)
                {
                    var v = tracked[i];
                    if (v.activeSelf) on++;
                    float d2 = (v.transform.position - pp).sqrMagnitude;
                    if (d2 < minD2) { minD2 = d2; best = v; }
                    if (d2 < r2) near++;
                    if (d2 < 250000f) entro500++;
                }
                var v0 = tracked[0];
                OsmDiag.Log("[ChunkVehiclePopulator] cull#" + _cullTicks +
                    ": tracked=" + tracked.Count + " accese=" + on +
                    " vicinoRaggio=" + near +
                    " entro500=" + entro500 + 
                    " minDist=" + Mathf.Sqrt(minD2).ToString("F1") +
                    " piuVicina='" + (best != null ? best.name : "?") +
                    "' bpos=" + (best != null
                        ? best.transform.position.ToString("F1") : "?") +
                    " player=" + pp.ToString("F1") +
                    " esempio='" + v0.name + "' vpos=" +
                    v0.transform.position.ToString("F1") +
                    " attiva=" + v0.activeSelf +
                    " radice=" + (v0.transform.parent != null
                        ? v0.transform.parent.name : "null"));
            }
        }

        private static bool IsDrivable(string hw)
        {
            switch (hw)
            {
                case "footway": case "path": case "steps":
                case "cycleway": case "pedestrian": case "track":
                case "bridleway": case "corridor":
                    return false;
                default:
                    return true;
            }
        }

        private static bool IsMainRoad(string hw)
        {
            switch (hw)
            {
                case "motorway": case "motorway_link": case "trunk": case "trunk_link":
                case "primary": case "primary_link": case "secondary": case "secondary_link":
                    return true;
                default:
                    return false;
            }
        }
    }
}

using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Text;
using UnityEngine;
using City.Vehicle.Traffic;

namespace City.OSM
{
    /// <summary>
    /// Streaming del mondo a chunk 1x1 km: carica i chunk nel raggio di carico,
    /// scarica oltre il raggio di scarico, gestisce il rebase del floating
    /// origin e i tre livelli di LOD. Le tile (10 km) sono condivise tra i 100
    /// chunk che le compongono con refcount.
    /// </summary>
    public class ChunkManager : MonoBehaviour
    {
        public const int LoadRadius = 2;    // chunk in ogni direzione (5x5, default conservativo)
        public const int UnloadRadius = 3;  // isteresi: un anello oltre il carico
        private const float TickIntervalS = 0.25f;
        private const long BuildBudgetMs = 12;

        /// <summary>Chunk totali dentro il raggio di carico (5x5) a fine load.</summary>
        public const int ExpectedChunkCount = (2 * LoadRadius + 1) * (2 * LoadRadius + 1);

        /// <summary>Build coroutine attive: max 1 per volta. Senza serializzazione
        /// N chunk in volo consumano ognuno fino a BuildBudgetMs di main-thread
        /// (25 x 12ms = 300ms/frame) -> frame da 2115ms e fps=0 durante la build.</summary>
        private int _activeBuilds;

        /// <summary>Intervallo dei report di avanzamento "CityProgress" allo
        /// splash Android (Mappa 0-40%, Costruzione 40-95%, Pronto 100%).</summary>
        private const float ProgressIntervalS = 0.5f;
        private const float TilePhaseFallbackS = 60f; // se una tile manca davvero, non bloccarsi <40%

        public Transform target;

        // false = tick e pompa di build sospesi: l'health-check crea il
        // manager mentre aspettiamo il fix GPS (origine ancora = default
        // Roma) e senza questo gate streamava 49 chunk verso Roma, buttati
        // al purge dell'origine definitiva (memoria sprecata, rischio
        // LOW_MEMORY su device limitati)
        public bool StreamingEnabled = true;

        /// <summary>True finche' lo splash deve ricevere gli aggiornamenti di
        /// progresso; CityChunkedWorld lo spegne appena manda CityReady.</summary>
        public bool ProgressReporting = true;
        private float _progressStart;
        private float _nextProgressAt;

        public Transform ChunkRootParent => _rootParent;

        private Transform _rootParent;
        private readonly Dictionary<Vector2Int, ChunkData> _chunks =
            new Dictionary<Vector2Int, ChunkData>();
        private readonly HashSet<Vector2Int> _inFlight = new HashSet<Vector2Int>();
        private readonly List<Vector2Int> _pending = new List<Vector2Int>();

        private class TileBundle { public TileGraphDoc graph; public TileGeoDoc geo; public int refs; }
        private readonly Dictionary<string, TileBundle> _tiles =
            new Dictionary<string, TileBundle>();
        private readonly HashSet<string> _loading = new HashSet<string>();

        // ── retry chunk con tile/geo non disponibili ──
        // Senza questo meccanismo un chunk con tile fallita (rete giu', server
        // ancora in generazione) restava in _chunks col solo terreno e NON
        // veniva piu' ri-accodato: la citta' restava piatta/grigia per tutta
        // la sessione. Ora viene riprovato con backoff 5s..120s.
        private const float RetryBaseDelayS = 5f;
        private const float RetryMaxDelayS = 120f;
        private readonly Dictionary<Vector2Int, float> _retryAt =
            new Dictionary<Vector2Int, float>();
        private readonly Dictionary<Vector2Int, int> _retryCount =
            new Dictionary<Vector2Int, int>();

        // tile nota-mancante: cooldown per non martellare il server con fetch
        // a catena (prima ogni chunk della stessa tile ripeteva l'intero
        // scaricamento, fino a ~100 download seriali per una tile morta)
        private const float TileCooldownS = 60f;
        private readonly Dictionary<string, float> _tileCooldownAt =
            new Dictionary<string, float>();

        // ── riallineamento strade al nuovo DEM ──
        // Quando arriva una tile DEM nuova (TileElevation.Register), le strade
        // dei chunk gia' costruiti che ricadono nella zona campionano le
        // altezze appena diventate disponibili: erano state costruite con
        // quei punti a MISSA (y=0) e restavano sepolte sotto un terreno
        // rialzato. Qui teniamo la coda dei chunk da ricostruire e la
        // svuotiamo nei tick (budget per frame, deduplicata per chunk).
        private readonly HashSet<Vector2Int> _roadsRebuild =
            new HashSet<Vector2Int>();

        private Material _roadMat;
        private Material _sidewalkMat;
        private Huntix.Core.CityKitAssetRegistry _registry;

        public Material SharedRoadMaterial
        {
            get { if (_roadMat == null) _roadMat = TerrainChunk.RoadMaterial(); return _roadMat; }
        }

        public Material SharedSidewalkMaterial
        {
            get { if (_sidewalkMat == null) _sidewalkMat = TerrainChunk.SidewalkMaterial(); return _sidewalkMat; }
        }

        public Huntix.Core.CityKitAssetRegistry Registry
        {
            get
            {
                if (_registry == null && Huntix.Core.GameManager.Instance != null)
                    _registry = Huntix.Core.GameManager.Instance.cityKitRegistry;
                return _registry;
            }
        }

        private Coroutine _tickLoop;

        private void Start()
        {
            // I collider di chunk generati a runtime entrano in PhysX con un
            // piccolo ritardo di registrazione. autoSyncTransforms=on (come nel
            // world legacy) fa registrare subito i nuovi MeshCollider al
            // CharacterController: senza, il player puo' attraversare il terreno
            // appena costruito e precipitare (mesh one-sided).
            Physics.autoSyncTransforms = true;
            // persistente: sopravvive ai reload della scena City (il double-load
            // di GameManager distruggeva questo oggetto prima del primo tick)
            DontDestroyOnLoad(gameObject);
            _progressStart = Time.unscaledTime;
            var rootGo = new GameObject("Chunks");
            rootGo.transform.SetParent(transform, false);
            _rootParent = rootGo.transform;
            if (!WorldOrigin.Initialized) WorldOrigin.Init(41.9028, 12.4964);
            WorldOrigin.OnRebased += OnRebased;
            OsmDiag.Log("[DiagShader] Lit=" +
                (Shader.Find("Universal Render Pipeline/Lit") != null) +
                " Unlit=" + (Shader.Find("Universal Render Pipeline/Unlit") != null) +
                " Standard=" + (Shader.Find("Standard") != null) +
                " SpritesDefault=" + (Shader.Find("Sprites/Default") != null) +
                " LegacyDiffuse=" + (Shader.Find("Legacy Shaders/Diffuse") != null) +
                " UnlitColor=" + (Shader.Find("Unlit/Color") != null));
            OsmDiag.Log("[ChunkManager] avvio tick (timeScale=" +
                Time.timeScale + ")");

            // lo streaming gira in una coroutine dedicata (TickLoop) cosi' che
            // UnloadAll() (teletrasporto) puo' fermare le build in volo senza
            // uccidere definitivamente il loop (il blu-screen dopo CENTRA GPS).
            EnsureStreamingLoop();
        }

        /// <summary>Avvia il tick loop se non gia' attivo. Chiamato da Start e
        /// riavviato da UnloadAll() dopo che StopAllCoroutines lo ha spento.</summary>
        private void EnsureStreamingLoop()
        {
            if (_tickLoop != null || !gameObject.activeInHierarchy) return;
            _tickLoop = StartCoroutine(TickLoop());
        }

        private IEnumerator TickLoop()
        {
            try
            {
                // NB: WaitForSecondsRealtime, non scalata: se il gioco blocca
                // Time.timeScale lo streaming deve continuare comunque
                var wait = new WaitForSecondsRealtime(TickIntervalS);
                float heartbeat = 0f;
                while (true)
                {
                    try { Tick(); }
                    catch (Exception e)
                    {
                        UnityEngine.Debug.LogError("[ChunkManager] errore tick: " + e);
                    }
                heartbeat += TickIntervalS;
                if (heartbeat >= 4f)
                {
                    heartbeat = 0f;
                    // Riepilogo aggregato delle mesh strade: le righe
                    // [Builder] dei chunk centrali cadono nel troncamento del
                    // log, qui invece lo stato e' sempre visibile.
                    int stradeChunks = 0, stradeVertsSum = 0;
                    foreach (var kv in _chunks)
                    {
                        var cd = kv.Value;
                        var mf = cd != null && cd.roadsGo != null
                            ? cd.roadsGo.GetComponent<MeshFilter>() : null;
                        var m = mf != null ? mf.sharedMesh : null;
                        if (m != null)
                        {
                            stradeChunks++;
                            stradeVertsSum += m.vertexCount;
                        }
                    }
                    OsmDiag.Log("[ChunkManager] stato: chunk=" + _chunks.Count +
                        " pending=" + _pending.Count + " tiles=" + _tiles.Count +
                        " target=" + (target != null) +
                        " Lit=" + (Shader.Find("Universal Render Pipeline/Lit") != null) +
                        " stradeMesh=" + stradeChunks + "/" + _chunks.Count +
                        " vSum=" + stradeVertsSum);
                    if (target != null && _chunks.Count == 0 &&
                        Time.realtimeSinceStartup >= _nextEmptyWorldToast)
                    {
                        _nextEmptyWorldToast = Time.realtimeSinceStartup + 25f;
                        Toast("Sto costruendo il mondo intorno a te: ancora qualche istante...");
                    }
                }
                yield return wait;
                }
            }
            finally
            {
                _tickLoop = null;
            }
        }

        private void OnDestroy()
        {
            WorldOrigin.OnRebased -= OnRebased;
        }

        private static void Toast(string msg)
        {
            var g = City.Game.Instance;
            if (g != null && g.ui != null) g.ui.ShowToast(msg);
        }

        // ── tick principale ─────────────────────────────────────

        private bool _warnedNoTarget;
        private bool _loggedFirstTick;
        private static bool _geoWaitToasted;
        private float _nextEmptyWorldToast;

        private void Tick()
        {
            if (!StreamingEnabled) return;
            if (target == null && !ResolveTarget())
            {
                if (!_warnedNoTarget)
                {
                    _warnedNoTarget = true;
                    UnityEngine.Debug.LogWarning("[ChunkManager] nessun target: Game.Instance=" +
                        (City.Game.Instance != null) + " player=" +
                        (City.Game.Instance != null && City.Game.Instance.player != null) +
                        " Camera.main=" + (Camera.main != null));
                }
                return;
            }

            GeoCoord g = WorldOrigin.ToGeo(target.position);
            Vector2Int cur = CityGrid.ChunkIndexOf(g.lat, g.lng);
            if (!_loggedFirstTick)
            {
                _loggedFirstTick = true;
                OsmDiag.Log("[ChunkManager] primo tick: chunk " +
                    cur.x + "," + cur.y + " (timeScale=" + Time.timeScale + ")");
            }

            EnqueueMissing(cur);
            UnloadFar(cur);
            RetryMissingTiles();
            RebuildRoadsForNewDem();
            UpdateLods(cur);
            WorldOrigin.TryRebase(target.position);
            MaybeReportCityProgress();
        }

        /// <summary>Riporta in coda di build i chunk con tile fallita, dopo il
        /// backoff crescente. Il vecchio percorso non lo faceva MAI: il chunk
        /// restava in _chunks e EnqueueMissing lo saltava per sempre.</summary>
        private void RetryMissingTiles()
        {
            float now = Time.realtimeSinceStartup;

            // pulizia cooldown tile scaduti: altrimenti crescono per tutta la
            // sessione con tile fallite in zone diverse
            if (_tileCooldownAt.Count > 0)
            {
                List<string> expired = null;
                foreach (var kv in _tileCooldownAt)
                    if (now >= kv.Value)
                    {
                        if (expired == null) expired = new List<string>();
                        expired.Add(kv.Key);
                    }
                if (expired != null)
                    foreach (var k in expired) _tileCooldownAt.Remove(k);
            }

            if (_retryAt.Count == 0) return;
            List<Vector2Int> ready = null;
            foreach (var kv in _retryAt)
            {
                if (now < kv.Value) continue;
                if (_inFlight.Contains(kv.Key)) continue;    // gia' in costruzione
                if (!_chunks.ContainsKey(kv.Key)) continue;  // scaricato nel mentre
                if (ready == null) ready = new List<Vector2Int>();
                ready.Add(kv.Key);
            }
            if (ready == null) return;
            foreach (var c in ready)
            {
                if (_activeBuilds >= 1) break;
                _retryAt.Remove(c);
                _inFlight.Add(c);
                _activeBuilds++;
                OsmDiag.Log("[ChunkManager] retry build chunk " + c.x + "," + c.y);
                StartCoroutine(BuildChunkCoroutine(c));
            }
        }

        private bool ResolveTarget()
        {
            var game = City.Game.Instance;
            if (game != null && game.player != null) target = game.player.transform;
            else if (Camera.main != null) target = Camera.main.transform;
            return target != null;
        }

        private void EnqueueMissing(Vector2Int cur)
        {
            for (int dx = -LoadRadius; dx <= LoadRadius; dx++)
            {
                for (int dz = -LoadRadius; dz <= LoadRadius; dz++)
                {
                    var c = new Vector2Int(cur.x + dx, cur.y + dz);
                    if (_chunks.ContainsKey(c) || _inFlight.Contains(c)) continue;
                    _pending.Add(c);
                    _inFlight.Add(c);
                }
            }
            if (_pending.Count > 1)
                _pending.Sort((a, b) => Dist2(a, cur).CompareTo(Dist2(b, cur)));
        }

        private static int Dist2(Vector2Int a, Vector2Int b)
        {
            int dx = a.x - b.x, dz = a.y - b.y;
            return dx * dx + dz * dz;
        }

        /// <summary>Pompa di costruzione con budget per frame (chiamata da Update).</summary>
        private void Update()
        {
            if (!StreamingEnabled || _pending.Count == 0) return;
            int building = _pending.Count;
            var clock = Stopwatch.StartNew();
            while (_activeBuilds < 1 && _pending.Count > 0 &&
                clock.ElapsedMilliseconds < BuildBudgetMs)
            {
                var c = _pending[0];
                _pending.RemoveAt(0);
                _activeBuilds++;
                OsmDiag.Log("[ChunkManager] === BUILD START === chunk " + c.x + "," + c.y + " pending=" + _pending.Count);
                StartCoroutine(BuildChunkCoroutine(c));
            }
        }

        private IEnumerator BuildChunkCoroutine(Vector2Int c)
        {
            string tileKey = CityGrid.TileKeyOfChunk(c);
            // l'epoca identifica l'origine con cui questo chunk e' nato: se
            // cambia mentre aspettiamo la tile o a build sospesa (es. fix GPS
            // tardivo che sposta WorldOrigin su un'altra citta'), il chunk e'
            // orfano e va buttato invece di costruirlo a chilometri dal player
            int epoch = WorldOrigin.Epoch;
            yield return EnsureTile(tileKey);

            if (epoch != WorldOrigin.Epoch)
            {
                OsmDiag.Log("[ChunkManager] chunk " + c.x + "," + c.y +
                    " scartato: origine cambiata durante il download");
                ReleaseTile(tileKey);
                _inFlight.Remove(c);
                _activeBuilds--;
                yield break;
            }

            // riusa il chunk se gia' presente (retry dopo una tile fallita):
            // pulisce la root residua e ricostruisce da zero
            ChunkData chunk;
            if (!_chunks.TryGetValue(c, out chunk))
            {
                // Il chunk non esiste ancora: registro appena ne arriva il
                // turno. Precedentemente il ramo "else" interrompeva qui, ma
                // nessun altro creava piu' il chunk: _chunks restava vuoto e
                // ogni chunk veniva rimesso in coda all'infinito (la mappa
                // "spariva" con stradeMesh=0/0 e pending mai in calo).
                chunk = new ChunkData
                {
                    index = c,
                    key = ChunkData.KeyOf(c),
                    center = CityGrid.ChunkCenter(c),
                };
                _chunks[c] = chunk;
            }
            else
            {
                // retry dopo tile fallita: il chunk esiste gia' (magari con la
                // geo mai assegnata). Verifichiamo che non sia stato
                // scaricato (UnloadFar) durante il fetch della tile: in tal
                // caso non va ricreato come ghost vuoto.
                if (!_chunks.ContainsKey(c))
                {
                    _inFlight.Remove(c);
                    _activeBuilds--;
                    yield break;
                }
                if (chunk.root != null)
                {
                    chunk.Destroy();
                    chunk.root = null;
                }
            }

            TileBundle bundle;
            if (!_tiles.TryGetValue(tileKey, out bundle) || bundle.geo == null)
            {
                // tile non disponibile (offline e non in cache, o server ancora
                // in generazione): il chunk resta vuoto e viene RITENTATO con
                // backoff crescente invece di essere abbandonato per la sessione
                int attempts;
                _retryCount.TryGetValue(c, out attempts);
                float delay = Mathf.Min(
                    RetryBaseDelayS * Mathf.Pow(2f, attempts), RetryMaxDelayS);
                _retryCount[c] = attempts + 1;
                _retryAt[c] = Time.realtimeSinceStartup + delay;
                UnityEngine.Debug.LogWarning("[ChunkManager] tile " + tileKey +
                    " non disponibile: prossimo tentativo per " +
                    chunk.key + " tra " + (int)delay + "s (n." +
                    (attempts + 1) + ")");
            }
            else
            {
                // retry riuscito (o primo tentativo): nessuna attesa residua
                _retryAt.Remove(c);
                _retryCount.Remove(c);
                if (chunk.root != null)
                {
                    chunk.Destroy();
                    chunk.root = null;
                }
                // CRITICO per LocationHud/MinimapHud: BuiltChunks() filtra su
                // geo != null, senza questo assegnamento gli HUD restano muti
                chunk.geo = bundle.geo;
                var clock = Stopwatch.StartNew();
                // C# vieta yield dentro try/catch: pompiamo la coroutine
                // esterna a mano per intercettare le eccezioni di build
                IEnumerator build = ChunkBuilder.Build(this, chunk, bundle.geo, clock, BuildBudgetMs);
                bool completed = false;
                while (true)
                {
                    bool more;
                    try { more = build.MoveNext(); }
                    catch (Exception e)
                    {
                        UnityEngine.Debug.LogError("[ChunkManager] errore build chunk " +
                            c.x + "," + c.y + ": " + e);
                        break;
                    }
                    if (!more) { completed = true; break; }

                    // Guardie DOPO il primo MoveNext: l'iteratore e' lazy e
                    // chunk.root viene assegnato proprio dentro la prima
                    // MoveNext, controllarlo prima abortirebbe ogni chunk
                    // all'istante. Qui la build e' sospesa su uno yield:
                    // l'epoca puo' essere cambiata (fix GPS tardivo) oppure
                    // UnloadFar puo' aver gia' scaricato il chunk.
                    if (epoch != WorldOrigin.Epoch)
                    {
                        OsmDiag.Log("[ChunkManager] chunk " + c.x + "," + c.y +
                            " scartato: origine cambiata durante la build");
                        if (chunk.root != null)
                        {
                            // nessun altro l'ha ancora tolto: pulizia completa
                            _chunks.Remove(c);
                            ReleaseTile(tileKey);
                            chunk.Destroy();
                        }
                        // else: UnloadFar/UnloadAll hanno gia' rimosso il chunk
                        // da _chunks e liberato la tile, non toccare nulla
                        break;
                    }
                    if (chunk.root == null)
                    {
                        OsmDiag.Log("[ChunkManager] chunk " + c.x + "," + c.y +
                            " build interrotta (chunk scaricato)");
                        break;
                    }
                    yield return build.Current;
                }
                if (completed)
                    OsmDiag.Log("[ChunkManager] chunk " + c.x + "," + c.y + " costruito");
            }

            _inFlight.Remove(c);
            _activeBuilds--;
        }

        private IEnumerator EnsureTile(string tileKey)
        {
            // un solo download per tile anche se piu' chunk la richiedono insieme
            while (_loading.Contains(tileKey))
                yield return null;

            if (_tiles.ContainsKey(tileKey))
            {
                _tiles[tileKey].refs++;
                yield break;
            }

            // tile nota-mancante di poco fa: non rifare subito l'intero fetch
            // (evita la catena di download seriali a vuoto sugli stessi chunk)
            float cd;
            if (_tileCooldownAt.TryGetValue(tileKey, out cd) &&
                Time.realtimeSinceStartup < cd)
                yield break;

            var bundle = new TileBundle { refs = 1 };
            _loading.Add(tileKey);
            _tiles[tileKey] = bundle;

            TileGraphDoc graph = null;
            TileGeoDoc geo = null;
            yield return TileClient.FetchGraph(tileKey, r => graph = r);

            // la prima generazione lato server di una zona mai visitata puo'
            // richiedere minuti: avvisiamo l'utente e ritentiamo la geo,
            // altrimenti la tile resta mezza vuota e il player cade nel vuoto
            bool needsNet = !System.IO.File.Exists(TileClient.TileGraphCachePath(tileKey)) ||
                            !System.IO.File.Exists(TileClient.TileGeoCachePath(tileKey));
            if (needsNet && !_geoWaitToasted)
            {
                _geoWaitToasted = true;
                Toast("Preparo la mappa di questa zona: la prima volta puo' richiedere 1-2 minuti...");
            }
            var retryWait = new WaitForSecondsRealtime(5f);
            for (int attempt = 0; attempt < 3 && geo == null; attempt++)
            {
                if (attempt > 0)
                {
                    OsmDiag.Log("[ChunkManager] geo " + tileKey +
                        " assente, ritento (" + (attempt + 1) + "/3)");
                    yield return retryWait;
                }
                yield return TileClient.FetchGeo(tileKey, r => geo = r);
            }
            _loading.Remove(tileKey);

            if (geo == null)
            {
                // senza geo i chunk non si costruiscono: scartiamo la tile e
                // mettiamo un cooldown per non riscaricarla a vuoto; il retry
                // dei chunk che la usano e' gestito da _retryAt/_retryCount
                OsmDiag.Log("[ChunkManager] tile " + tileKey + " NON disponibile (graph=" +
                    (graph != null) + ")");
                _tiles.Remove(tileKey);   // libera il posto, retry al prossimo giro
                _tileCooldownAt[tileKey] = Time.realtimeSinceStartup + TileCooldownS;
                yield break;
            }
            _tileCooldownAt.Remove(tileKey);
            bundle.graph = graph;
            bundle.geo = geo;
            if (graph != null)
                TileRoadNetwork.Ensure().AddTile(tileKey, graph);
            // una tile DEM nuova puo' rialzare strade gia' costruite che la
            // build aveva campionato a y=0 (punti fuori dal registro di allora)
            if (geo != null && TileElevation.Register(geo))
                ScheduleRoadsRebuildsForTile(geo);
            OsmDiag.Log("[ChunkManager] tile " + tileKey + " caricata (graph=" +
                (graph != null) + ", geo=" + (geo != null) + ")");
        }

        private void UnloadFar(Vector2Int cur)
        {
            List<Vector2Int> toRemove = null;
            foreach (var kv in _chunks)
            {
                int dx = Mathf.Abs(kv.Key.x - cur.x);
                int dz = Mathf.Abs(kv.Key.y - cur.y);
                if (Mathf.Max(dx, dz) <= UnloadRadius) continue;
                if (toRemove == null) toRemove = new List<Vector2Int>();
                toRemove.Add(kv.Key);
            }
            if (toRemove == null) return;
            foreach (var key in toRemove)
            {
                var chunk = _chunks[key];
                _chunks.Remove(key);
                _retryAt.Remove(key);
                _retryCount.Remove(key);
                ReleaseTile(CityGrid.TileKeyOfChunk(key));
                chunk.Destroy();
            }
        }

        private void ReleaseTile(string tileKey)
        {
            TileBundle bundle;
            if (!_tiles.TryGetValue(tileKey, out bundle)) return;
            bundle.refs--;
            if (bundle.refs <= 0)
            {
                _tiles.Remove(tileKey);
                TileElevation.Unregister(tileKey);
                if (TileRoadNetwork.Instance != null)
                    TileRoadNetwork.Instance.RemoveTile(tileKey);
            }
        }

        /// <summary>
        /// Mete in coda di ricalcolo le strade dei chunk gia' costruiti i cui
        /// bounds (con margine strade) cadono nel bbox della tile DEM appena
        /// registrata: alla build originale quei punti erano MISS (nessuna tile
        /// copriva) e le strade sono finite a y=0 sotto il terreno rialzato.
        /// Ora che il DEM c'e', vanno riallineate (solo layer strade).
        /// </summary>
        private void ScheduleRoadsRebuildsForTile(TileGeoDoc geo)
        {
            if (geo == null || geo.bbox == null || geo.bbox.Length < 4)
                return;
            double latMin = geo.bbox[0], lonMin = geo.bbox[1];
            double latMax = geo.bbox[2], lonMax = geo.bbox[3];

            // Le strade estendono il nastro fino a ~60 m oltre i bounds del
            // chunk (RoadRenderer margin): sopradimensioniamo i margini per
            // essere sicuri di ricostruire anche i soli vertici di bordo.
            const double marginLat = 0.0020;   // ~220 m
            const double marginLon = 0.0027;   // ~220 m

            int scheduled = 0;
            foreach (var kv in _chunks)
            {
                ChunkData cd = kv.Value;
                if (cd == null || !cd.built || cd.root == null) continue;
                GeoCoord sw = CityGrid.ChunkCorner(cd.index);
                GeoCoord ne = CityGrid.ChunkCorner(
                    new Vector2Int(cd.index.x + 1, cd.index.y + 1));
                // nessuna intersezione col bbox (espanso)? salta
                if (ne.lat < latMin - marginLat || sw.lat > latMax + marginLat) continue;
                if (ne.lng < lonMin - marginLon || sw.lng > lonMax + marginLon) continue;
                if (_roadsRebuild.Add(kv.Key)) scheduled++;
            }
            if (scheduled > 0)
                OsmDiag.Log("[DEM] tile " + geo.tile +
                    " registrata: strade di " + scheduled +
                    " chunk da riallineare all'altimetria");
        }

        /// <summary>Pompa del riallineamento strade: svuota _roadsRebuild con un
        /// massimo di pezzi per tick (il meshing e' CPU-only, ma resta dentro i
        /// guard). La deduplicazione per chunk e' data dall'HashSet.</summary>
        private void RebuildRoadsForNewDem()
        {
            if (_roadsRebuild.Count == 0) return;
            const int maxPerTick = 4;
            int done = 0;
            var ready = new List<Vector2Int>(_roadsRebuild);
            foreach (var c in ready)
            {
                if (done >= maxPerTick) break;
                _roadsRebuild.Remove(c);
                if (!_chunks.ContainsKey(c) || _inFlight.Contains(c)) continue;
                done++;
                StartCoroutine(RebuildRoadsCoroutine(c));
            }
        }

        private IEnumerator RebuildRoadsCoroutine(Vector2Int c)
        {
            int epoch = WorldOrigin.Epoch;
            ChunkData chunk;
            if (!_chunks.TryGetValue(c, out chunk) || chunk == null) yield break;
            if (!chunk.built || chunk.root == null || chunk.geo == null) yield break;
            if (epoch != WorldOrigin.Epoch) yield break;
            try
            {
                ChunkBuilder.BuildRoadsOnly(this, chunk);
            }
            catch (Exception e)
            {
                UnityEngine.Debug.LogError("[ChunkManager] roads-only " +
                    c.x + "," + c.y + ": " + e);
            }
        }

        /// <summary>True se il chunk e' stato costruito (terreno e contenuti
        /// pronti). Usato dal gate di spawn del primo chunk in CityChunkedWorld.</summary>
        public bool IsChunkBuilt(Vector2Int idx)
        {
            ChunkData cd;
            return _chunks.TryGetValue(idx, out cd) && cd != null && cd.built;
        }

        /// <summary>
        /// Diagnostica del terreno sotto un punto mondo: stato del chunk, del GO
        /// terreno e del SUO MeshCollider (attivo? mesh valida e con bounds
        /// finiti?), poi un raycast dall'alto che elenca i collider realmente
        /// registrati nella fisica nella colonna. Serve a capire i "nessun
        /// terreno sotto il player": se i chunk sono 'built' ma la fisica non
        /// vede i collider, la frase raccolta qui dice esattamente cosa manca.
        /// </summary>
        public bool DiagnoseTerrain(Vector3 worldPos)
        {
            try
            {
                if (!WorldOrigin.Initialized)
                {
                    OsmDiag.Log("[ChunkManager] DiagnoseTerrain: WorldOrigin non inizializzato");
                    return false;
                }
                GeoCoord g = WorldOrigin.ToGeo(worldPos);
                Vector2Int c = CityGrid.ChunkIndexOf(g.lat, g.lng);
                ChunkData chunk;
                bool present = _chunks.TryGetValue(c, out chunk) && chunk != null;
                OsmDiag.Log("[ChunkManager] DiagnoseTerrain terreno sotto (" +
                    worldPos.x.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) + "," +
                    worldPos.z.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) +
                    ",y=" + worldPos.y.ToString("F1", System.Globalization.CultureInfo.InvariantCulture) +
                    ") -> chunk " + c.x + "," + c.y +
                    " presente=" + present +
                    (present ? " built=" + chunk.built + " lod=" + chunk.lod : ""));

                if (!present || chunk.terrainGo == null)
                {
                    OsmDiag.Log("[ChunkManager] DiagnoseTerrain: terrainGo NULL per il chunk " +
                        c.x + "," + c.y);
                }
                else
                {
                    var go = chunk.terrainGo;
                    var col = go.GetComponent<MeshCollider>();
                    var mf = go.GetComponent<MeshFilter>();
                    if (mf == null)
                        OsmDiag.Log("[ChunkManager] DiagnoseTerrain: NESSUN MeshFilter sul GO terreno");
                    var sb = new StringBuilder();
                    sb.Append("[ChunkManager] DiagnoseTerrain terrainGo=" + go.name +
                        " active=" + go.activeInHierarchy + " layer=" + go.layer);
                    if (col == null)
                    {
                        sb.Append(" NO-MeshCollider");
                    }
                    else
                    {
                        var mesh = col.sharedMesh;
                        sb.Append(" collider.enabled=" + col.enabled +
                            " convex=" + col.convex);
                        if (mesh == null)
                        {
                            sb.Append(" sharedMesh=NULL");
                        }
                        else
                        {
                            Vector3 bmin = mesh.bounds.min;
                            Vector3 bmax = mesh.bounds.max;
                            bool finito = !float.IsNaN(bmax.y) && !float.IsInfinity(bmax.y);
                            sb.Append(" sharedMeshVerts=" + mesh.vertexCount +
                                " tris=" + (mesh.triangles.Length / 3) +
                                " boundsValide=" + finito +
                                " worldBoundsMin=" + bmin +
                                " worldBoundsMax=" + bmax + " posY=" +
                                go.transform.position.y.ToString("F2",
                                    System.Globalization.CultureInfo.InvariantCulture));
                        }
                    }
                    OsmDiag.Log(sb.ToString());
                }

                // quali collider ESISTONO davvero nella colonna sopra il punto?
                // Se il terreno e' SOTTO il player i raycast verso il basso dal
                // player lo vedono; se manca anche dall'alto, la fisica non lo
                // contiene e il problema e' il collider, non la quota
                var seen = new System.Collections.Generic.HashSet<string>();
                // Origine ancorata alla superficie DEM (zone di montagna): il
                // terreno assoluto sta anche a 1200+ m, oltre i 600 fissi.
                Vector3 probeFrom = worldPos + Vector3.up * 600f;
                float probeDem = TileElevation.HeightAtWorld(worldPos);
                if (probeDem + 700f > probeFrom.y) probeFrom.y = probeDem + 700f;
                RaycastHit[] above = Physics.RaycastAll(
                    probeFrom, Vector3.down, 1400f);
                for (int i = 0; i < above.Length &&
                    i < ChunkManager.DiagnosticsMaxHits; i++)
                    seen.Add(above[i].collider.name + "@y" +
                        above[i].point.y.ToString("F1",
                            System.Globalization.CultureInfo.InvariantCulture));
                var hitList = new StringBuilder();
                hitList.Append("[ChunkManager] DiagnoseTerrain colonna (ancorata DEM) 1400m: " +
                    above.Length + " hit [");
                int n = 0;
                foreach (var s in seen)
                {
                    if (n++ > 0) hitList.Append(", ");
                    hitList.Append(s);
                }
                hitList.Append("]");
                OsmDiag.Log(hitList.ToString());
                return above.Length > 0;
            }
            catch (System.Exception e)
            {
                OsmDiag.Log("[ChunkManager] DiagnoseTerrain errore: " + e.Message);
                return false;
            }
        }

        
        /// <summary>Restituisce true se il terreno di questo chunk e' pronto
        /// per la fisica: collider attivo, mesh valida con bounds finiti.
        /// Il chiamante (CityChunkedWorld) puo' usarlo prima di aprire lo spawn:
        /// se false, il ponte di sicurezza va mantenuto e il player non deve
        /// cadere nel vuoto.</summary>


        /// <summary>Restituisce true se il terreno di questo chunk e' pronto
        /// per la fisica: collider attivo, mesh valida con bounds finiti.
        /// Il chiamante (CityChunkedWorld) puo' usarlo prima di aprire lo spawn:
        /// se false, il ponte di sicurezza va mantenuto e il player non deve
        /// cadere nel vuoto.</summary>
        public bool VerifyTerrainReady(Vector3 worldPos)
        {
            try
            {
                if (!WorldOrigin.Initialized) return false;
                GeoCoord g = WorldOrigin.ToGeo(worldPos);
                Vector2Int c = CityGrid.ChunkIndexOf(g.lat, g.lng);
                ChunkData chunk;
                if (!_chunks.TryGetValue(c, out chunk) || chunk == null || !chunk.built)
                    return false;
                if (chunk.terrainGo == null) return false;
                var go = chunk.terrainGo;
                if (!go.activeInHierarchy) return false;
                var col = go.GetComponent<MeshCollider>();
                if (col == null) return false;
                if (!col.enabled) return false;
                var mesh = col.sharedMesh;
                if (mesh == null) return false;
                if (mesh.vertexCount == 0) return false;
                Vector3 b = mesh.bounds.size;
                return !float.IsNaN(b.y) && !float.IsInfinity(b.y) && b.x > 0f && b.z > 0f;
            }
            catch (System.Exception e)
            {
                OsmDiag.Log("[ChunkManager] VerifyTerrainReady errore: " + e.Message);
                return false;
            }
        }

private const int DiagnosticsMaxHits = 8;

        /// <summary>Ferma i report di avanzamento (dopo CityReady lo splash
        /// non ha piu' bisogno di essere aggiornato).</summary>
        public void StopProgressReporting()
        {
            ProgressReporting = false;
        }

        // ── progresso caricamento città (splash Miacitta) ─────────────
        // Invia a Bridge ("CityProgress") un JSON con fase, percentuale e
        // byte letti. Fasi: Mappa (tile graph+geo) 0-40%, Costruzione
        // (chunk buildati) 40-95%; CityChunkedWorld chiude con il CityReady.
        private void MaybeReportCityProgress()
        {
            if (!ProgressReporting) return;
            if (Time.unscaledTime < _nextProgressAt) return;
            _nextProgressAt = Time.unscaledTime + ProgressIntervalS;

            try
            {
                GeoCoord g = WorldOrigin.ToGeo(target.position);
                Vector2Int cur = CityGrid.ChunkIndexOf(g.lat, g.lng);

                // tile attese: chiavi uniche dei chunk nel raggio di carico (5x5)
                var seen = new HashSet<string>();
                for (int dx = -LoadRadius; dx <= LoadRadius; dx++)
                    for (int dz = -LoadRadius; dz <= LoadRadius; dz++)
                        seen.Add(CityGrid.TileKeyOfChunk(
                            new Vector2Int(cur.x + dx, cur.y + dz)));
                int expectedTiles = Mathf.Max(1, seen.Count);

                // avanzamento tile: ogni tile pesa 1.5 (graph 0.5 + geo 1.0)
                double tileP = 0.0;
                foreach (var kv in _tiles)
                {
                    var b = kv.Value;
                    if (b.graph != null) tileP += 0.5;
                    if (b.geo != null) tileP += 1.0;
                }
                tileP = Math.Min(1.0, tileP / (expectedTiles * 1.5));
                // se una tile manca davvero (offline/server giu') il progresso
                // non deve restare bloccato per sempre sotto il 40%
                if (tileP < 1.0 && Time.unscaledTime - _progressStart > TilePhaseFallbackS)
                    tileP = 0.99;

                double buildP = Math.Min(1.0, (double)BuiltCount / ExpectedChunkCount);
                double raw = tileP < 1.0
                    ? tileP * 40.0
                    : 40.0 + buildP * 55.0;
                int percent = Mathf.Clamp((int)Math.Round(raw, MidpointRounding.AwayFromZero), 0, 95);

                string phase = tileP < 1.0 ? "Mappa"
                    : buildP < 1.0 ? "Costruzione"
                    : "Quasi pronto";
                string section = ChunkBuilder.CurrentBuildSection;
                long bytes = TileClient.TotalBytes;

                string json = "{\"phase\":\"" + phase + "\",\"percent\":" + percent +
                    ",\"bytes\":" + bytes + ",\"section\":\"" + section + "\"}";
                try
                {
                    Huntix.Bridge.UnityBridge.SendMessageToAndroid("CityProgress", json);
                }
                catch (Exception e)
                {
                    UnityEngine.Debug.LogWarning("[ChunkManager] CityProgress: " + e.Message);
                }
            }
            catch (Exception e)
            {
                UnityEngine.Debug.LogWarning("[ChunkManager] report progresso: " + e.Message);
            }
        }

        // ── LOD ─────────────────────────────────────────────────

        private void UpdateLods(Vector2Int cur)
        {
            foreach (var kv in _chunks)
            {
                int d = Mathf.Max(Mathf.Abs(kv.Key.x - cur.x), Mathf.Abs(kv.Key.y - cur.y));
                int lod = d <= 1 ? 0 : d <= LoadRadius ? 1 : 2;
                kv.Value.SetLod(lod);
                kv.Value.lastTouch = Time.time;
            }
        }

        // ── rebase ──────────────────────────────────────────────

        private void OnRebased(Vector3 delta)
        {
            foreach (var kv in _chunks)
            {
                if (kv.Value.root != null)
                    kv.Value.root.transform.position -= delta;
            }
            if (target != null)
            {
                // Muovere direttamente un CharacterController abilitato senza
                // ri-registrarlo desincronizza la sweep interna dal transform
                // (il player puo' passare attraverso i collider appena
                // ri-ribasato). Spegni-trasloca-riaccendi + sync esplicito.
                var cc = target.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                target.position -= delta;
                var rb = target.GetComponentInParent<Rigidbody>();
                if (rb == null) rb = target.GetComponentInChildren<Rigidbody>();
                if (rb != null) rb.position -= delta;
                Physics.SyncTransforms();
                if (cc != null) cc.enabled = true;
            }
        }

        // ── API pubbliche ───────────────────────────────────────

        /// <summary>Ferma lo streaming in vista della chiusura dell'Activity Unity
        /// (uscita alla Home). Disattiva lo streaming, ferma il tick loop e le
        /// coroutine di build in volo PRIMA che il teardown dell'engine distrugga
        /// il runtime: in gara con lo smontaggio un TileClient/thread di build che
        /// richiama Unity puo' provocare un crash nativo (SIGSEGV) del processo,
        /// che pero' resta vivo per mostrare la Home nativa.</summary>
        public void StopStreaming()
        {
            StreamingEnabled = false;
            try
            {
                StopAllCoroutines();
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[ChunkManager] StopStreaming coroutine: " + e.Message);
            }
            _tickLoop = null;
            OsmDiag.Log("[ChunkManager] streaming fermato (uscita Activity Unity)");
        }

        public void UnloadAll()
        {
            // StopAllCoroutines ferma le build in volo E il tick loop dedicato.
            // Il tick loop viene subito riavviato (EnsureStreamingLoop) per la
            // nuova origine: senza questo il teletrasporto (CENTRA GPS) lasciava
            // il mondo blu e vuoto all'infinito (chunks=0 costruiti=0).
            StopAllCoroutines();
            _tickLoop = null;
            _activeBuilds = 0;
            _pending.Clear();
            _inFlight.Clear();
            _retryAt.Clear();
            _retryCount.Clear();
            _tileCooldownAt.Clear();
            _roadsRebuild.Clear();
            _loading.Clear();
            foreach (var kv in _chunks) kv.Value.Destroy();
            _chunks.Clear();
            _tiles.Clear();
            TileElevation.Reset();
            EnsureStreamingLoop();
        }

        /// <summary>Scarica l'INTERO mondo di gioco (chunk, veicoli, NPC,
        /// edifici, materiali) SENZA riavviare lo streaming: serve UNA SOLA volta
        /// prima di chiudere l'Activity Unity da Android. Il teardown dell'engine
        /// (mUnityPlayer.destroy) con una scena ancora gigante (15 chunk, 1200+
        /// veicoli, 5800 edifici/chunk) ci mette ~10s e durante lo smontaggio un
        /// sistema in distruzione crashe in SIGSEGV (log: Exit -> ~10s -> SIGNALED
        /// signal=11, trace nullo). Svuotando la scena qui l'engine smonta quasi
        /// nulla e il processo resta vivo pulito sulla Home nativa. NON chiama
        /// EnsureStreamingLoop (il tick loop non deve ripartire in uscita).</summary>
        public void UnloadWorldForExit()
        {
            try
            {
                StopAllCoroutines();
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[ChunkManager] UnloadWorldForExit coroutine: " + e.Message);
            }
            _tickLoop = null;
            _pending.Clear();
            _inFlight.Clear();
            _retryAt.Clear();
            _retryCount.Clear();
            _tileCooldownAt.Clear();
            _roadsRebuild.Clear();
            _loading.Clear();
            foreach (var kv in _chunks) kv.Value.Destroy();
            _chunks.Clear();
            _tiles.Clear();
            TileElevation.Reset();
            OsmDiag.Log("[ChunkManager] mondo scaricato prima del teardown (uscita Activity Unity)");
        }

        public int LoadedCount => _chunks.Count;

        /// <summary>Chunk effettivamente costruiti (terreno e collider pronti).</summary>
        public int BuiltCount
        {
            get
            {
                int n = 0;
                foreach (var kv in _chunks)
                    if (kv.Value != null && kv.Value.built) n++;
                return n;
            }
        }

        /// <summary>Chunk costruiti (con documento tile) per HUD/minimap.</summary>
        public List<ChunkData> BuiltChunks()
        {
            var list = new List<ChunkData>(_chunks.Count);
            foreach (var kv in _chunks)
                if (kv.Value != null && kv.Value.built && kv.Value.geo != null)
                    list.Add(kv.Value);
            return list;
        }
    }
}

using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using UnityEngine;

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
        public const int LoadRadius = 3;    // chunk in ogni direzione (7x7)
        public const int UnloadRadius = 5;
        private const float TickIntervalS = 0.25f;
        private const long BuildBudgetMs = 8;

        public Transform target;

        // false = tick e pompa di build sospesi: l'health-check crea il
        // manager mentre aspettiamo il fix GPS (origine ancora = default
        // Roma) e senza questo gate streamava 49 chunk verso Roma, buttati
        // al purge dell'origine definitiva (memoria sprecata, rischio
        // LOW_MEMORY su device limitati)
        public bool StreamingEnabled = true;

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

        private IEnumerator Start()
        {
            // persistente: sopravvive ai reload della scena City (il double-load
            // di GameManager distruggeva questo oggetto prima del primo tick)
            DontDestroyOnLoad(gameObject);
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
            UpdateLods(cur);
            WorldOrigin.TryRebase(target.position);
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
            var clock = Stopwatch.StartNew();
            while (_pending.Count > 0 && clock.ElapsedMilliseconds < BuildBudgetMs)
            {
                var c = _pending[0];
                _pending.RemoveAt(0);
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
                yield break;
            }

            var chunk = new ChunkData
            {
                index = c,
                key = ChunkData.KeyOf(c),
                center = CityGrid.ChunkCenter(c),
            };
            _chunks[c] = chunk;

            TileBundle bundle;
            if (!_tiles.TryGetValue(tileKey, out bundle) || bundle.geo == null)
            {
                // tile non disponibile (offline e non in cache): chunk vuoto col solo terreno
                UnityEngine.Debug.LogWarning("[ChunkManager] tile " + tileKey + " non disponibile");
            }
            else
            {
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
                // senza geo i chunk non si costruiscono: scartiamo tutto e
                // riproviamo al prossimo giro invece di tenere una tile cieca
                OsmDiag.Log("[ChunkManager] tile " + tileKey + " NON disponibile (graph=" +
                    (graph != null) + ")");
                _tiles.Remove(tileKey);   // libera il posto, retry al prossimo giro
                yield break;
            }
            bundle.graph = graph;
            bundle.geo = geo;
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
                ReleaseTile(CityGrid.TileKeyOfChunk(key));
                chunk.Destroy();
            }
        }

        private void ReleaseTile(string tileKey)
        {
            TileBundle bundle;
            if (!_tiles.TryGetValue(tileKey, out bundle)) return;
            bundle.refs--;
            if (bundle.refs <= 0) _tiles.Remove(tileKey);
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
                target.position -= delta;
                var rb = target.GetComponentInParent<Rigidbody>();
                if (rb == null) rb = target.GetComponentInChildren<Rigidbody>();
                if (rb != null) rb.position -= delta;
            }
        }

        // ── API pubbliche ───────────────────────────────────────

        public void UnloadAll()
        {
            _pending.Clear();
            _inFlight.Clear();
            foreach (var kv in _chunks) kv.Value.Destroy();
            _chunks.Clear();
            _tiles.Clear();
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

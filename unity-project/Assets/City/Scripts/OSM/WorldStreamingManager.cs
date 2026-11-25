using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// World Streaming additivo: affianca il ChunkManager esistente senza
    /// sostituirlo. ChunkManager continua a caricare/scaricare i 49 chunk con
    /// LoadRadius=3 e UnloadRadius=4; qui aggiungiamo:
    ///
    ///  - il concetto di SETTORE (una tile OSM 10x10 chunk) con stato, visite,
    ///    prefetch su disco e notifica Android;
    ///  - il precaricamento DIREZIONALE dei chunk (hint passato a ChunkManager);
    ///  - la gestione dei teleport con prefetch della tile di destinazione;
    ///  - skyline dei settori lontani (una mesh per settore, nessun collider);
    ///  - culling per distanza/direzione e rilascio delle risorse obsolete.
    ///
    /// Inventario, progressi, veicoli, proprieta' e snapshot NON sono toccati:
    /// questo manager gestisce solo GameObject/mesh creati da lui e pref dedicati.
    /// </summary>
    public class WorldStreamingManager : MonoBehaviour
    {
        private const float SectorTickS = 0.5f;
        private const int SkylineRing = 2;
        private const int MaxSkylineSectors = 12;
        private const int MaxSkylineBuildings = 90;
        private const int LowMemSkylineBuildings = 36;
        private const float SkylineDistanceM = 14000f;
        private const float SkylineNearAlwaysM = 5000f;
        private const int TeleportChunkThreshold = 6;
        private const float MemoryCheckS = 10f;
        private const float UnloadUnusedCooldownS = 20f;

        private class SectorState
        {
            public string key;
            public WorldStreamingSkyline skyline;
            public bool usedRealGeo;
            public int visits;
            public float lastSeen;
        }

        public static WorldStreamingManager Instance { get; private set; }
        private static bool _eggGranted;

        private ChunkManager _manager;
        private Transform _root;
        private readonly Dictionary<string, SectorState> _states =
            new Dictionary<string, SectorState>();

        private Vector2Int _lastChunk;
        private bool _hasLastChunk;
        private string _currentSector;
        private bool _teleportPending;
        private double _teleportLat;
        private double _teleportLng;
        private bool _lowMemory;
        private int _skylineBudget = MaxSkylineBuildings;
        private float _nextTick;
        private float _nextMemCheck;
        private float _nextUnloadUnused;
        private bool _unloadingUnused;

        /// <summary>Aggiunto da ChunkManager.Start(): nessuna scena da riscrivere.</summary>
        public static WorldStreamingManager Ensure(ChunkManager manager)
        {
            if (manager == null) return null;
            WorldStreamingManager inst = Instance;
            if (inst == null)
            {
                var existing = manager.gameObject.GetComponent<WorldStreamingManager>();
                inst = existing != null
                    ? existing
                    : manager.gameObject.AddComponent<WorldStreamingManager>();
            }
            inst._manager = manager;
            return inst;
        }

        /// <summary>Chiamato da CityChunkedWorld.Teleport PRIMA di UnloadAll.</summary>
        public static void NotifyTeleport(double lat, double lng)
        {
            if (Instance == null) return;
            Instance.OnTeleport(lat, lng);
        }

        private void Awake()
        {
            Instance = this;
        }

        private void Start()
        {
            if (_manager == null)
            {
                var mgr = FindObjectOfType<ChunkManager>();
                if (mgr != null) _manager = mgr;
            }
            var rootGo = new GameObject("StreamingSectors");
            if (_manager != null) rootGo.transform.SetParent(_manager.transform, false);
            _root = rootGo.transform;
            WorldOrigin.OnRebased += OnRebased;
            _nextMemCheck = Time.unscaledTime + 1f;
            try
            {
                WorldStreamingBridge.NotifyReady(WorldStreamingCatalog.Count);
                OsmDiag.Log("[WorldStreaming] avviato: " + WorldStreamingCatalog.Count +
                    " settori, raggio chunk " + ChunkManager.LoadRadius + "/" +
                    ChunkManager.UnloadRadius + " (invariato)");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] avvio: " + e.Message);
            }
        }

        private void OnDestroy()
        {
            WorldOrigin.OnRebased -= OnRebased;
            foreach (var kv in _states)
                if (kv.Value.skyline != null) kv.Value.skyline.Destroy();
            _states.Clear();
            if (Instance == this) Instance = null;
        }

        private void Update()
        {
            if (_manager == null || !_manager.StreamingEnabled) return;
            if (City.Fictional.FictionalCityWorld.Enabled) return;
            if (Time.unscaledTime < _nextTick) return;
            _nextTick = Time.unscaledTime + SectorTickS;

            CheckMemory();
            var target = _manager.target;
            if (target == null || !WorldOrigin.IsFinite(target.position)) return;

            var g = WorldOrigin.ToGeo(target.position);
            Vector2Int cur = CityGrid.ChunkIndexOf(g.lat, g.lng);
            if (_hasLastChunk)
            {
                var d = new Vector2Int(cur.x - _lastChunk.x, cur.y - _lastChunk.y);
                int jump = Mathf.Max(Mathf.Abs(d.x), Mathf.Abs(d.y));
                if (jump >= TeleportChunkThreshold)
                    OnTeleport(g.lat, g.lng);
                else if (jump > 0)
                    _manager.SetDirectionalHint(new Vector2Int(Math.Sign(d.x), Math.Sign(d.y)));
            }
            _lastChunk = cur;
            _hasLastChunk = true;

            string sector = CityGrid.TileKeyOfChunk(cur);
            if (sector != _currentSector)
            {
                string previous = _currentSector;
                _currentSector = sector;
                bool wasTeleport = _teleportPending;
                OnSectorEntered(sector, previous, cur, g, wasTeleport);
                _teleportPending = false;
                if (wasTeleport) RequestResourceCompaction();
            }
            UpdateSkylines(cur, target);
        }

        private void OnSectorEntered(string key, string previous, Vector2Int chunk,
                                     GeoCoord g, bool teleport)
        {
            SectorState st;
            if (!_states.TryGetValue(key, out st))
            {
                st = new SectorState { key = key };
                _states.Add(key, st);
            }
            st.visits++;
            st.lastSeen = Time.unscaledTime;

            string json = "{\"sector\":\"" + key + "\",\"previous\":\"" +
                (previous ?? "") + "\",\"chunkX\":" + chunk.x + ",\"chunkY\":" + chunk.y +
                ",\"lat\":\"" + g.lat.ToString("R", CultureInfo.InvariantCulture) +
                "\",\"lng\":\"" + g.lng.ToString("R", CultureInfo.InvariantCulture) +
                "\",\"teleport\":" + (teleport ? "true" : "false") + ",\"tsMt\":" +
                DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
            try { WorldStreamingBridge.NotifySectorChanged(json); }
            catch (Exception e) { Debug.LogWarning("[WorldStreaming] notifica settore: " + e.Message); }

            // Il flag si alza solo se l'uovo e' stato davvero piazzato: se il
            // player non e' ancora pronto, riproveremo al settore successivo.
            if (!_eggGranted && WorldStreamingBridge.GrantDiscoveryEgg())
                _eggGranted = true;
            // Prefetch SOLO da cache: se il pacchetto offline non c'e' non si
            // apre alcuna connessione (il download resta una scelta dell'utente).
            try { StartCoroutine(TileClient.PrefetchSector(key)); }
            catch (Exception e) { Debug.LogWarning("[WorldStreaming] prefetch: " + e.Message); }

            OsmDiag.Log("[WorldStreaming] settore " + key + " attivo (visita " +
                st.visits + ", teleport=" + teleport + ")");
        }

        private void OnTeleport(double lat, double lng)
        {
            _teleportPending = true;
            _teleportLat = lat;
            _teleportLng = lng;
            _manager.SetDirectionalHint(Vector2Int.zero);
            foreach (var kv in _states)
                if (kv.Value.skyline != null) kv.Value.skyline.Destroy();
            _states.Clear();
            string key = CityGrid.TileKeyOfChunk(CityGrid.ChunkIndexOf(lat, lng));
            try { StartCoroutine(TileClient.PrefetchSector(key)); }
            catch (Exception e) { Debug.LogWarning("[WorldStreaming] prefetch teleport: " + e.Message); }
            OsmDiag.Log("[WorldStreaming] teleport verso " + key +
                " (tile pronta in cache: " + TileClient.HasCachedGeo(key) + ")");
        }

        private void UpdateSkylines(Vector2Int cur, Transform target)
        {
            // Il settore del player e' coperto dai chunk veri: qui costruiamo
            // solo l'anello di settori lontani, non esplorabili.
            int tx = FloorDiv(cur.x, CityGrid.ChunksPerTile) * CityGrid.ChunksPerTile;
            int ty = FloorDiv(cur.y, CityGrid.ChunksPerTile) * CityGrid.ChunksPerTile;
            var wanted = new List<KeyValuePair<int, string>>();
            for (int dx = -SkylineRing; dx <= SkylineRing; dx++)
            {
                for (int dy = -SkylineRing; dy <= SkylineRing; dy++)
                {
                    if (dx == 0 && dy == 0) continue;
                    var ci = new Vector2Int(tx + dx * CityGrid.ChunksPerTile,
                                            ty + dy * CityGrid.ChunksPerTile);
                    string key = CityGrid.TileKeyOfChunk(ci);
                    WorldSectorInfo info;
                    if (!WorldStreamingCatalog.TryGet(key, out info) &&
                        !TileClient.HasCachedGeo(key)) continue;
                    wanted.Add(new KeyValuePair<int, string>(Math.Abs(dx) + Math.Abs(dy), key));
                }
            }
            wanted.Sort((a, b) => a.Key.CompareTo(b.Key));
            var desired = new HashSet<string>();
            for (int i = 0; i < wanted.Count && i < MaxSkylineSectors; i++)
                desired.Add(wanted[i].Value);

            // Rilascio i settori usciti dall'anello (mesh + GameObject, non le
            // tile condivise: i referee dei chunk restano in ChunkManager).
            var toRemove = new List<string>();
            foreach (var kv in _states)
                if (kv.Value.skyline != null && kv.Key != _currentSector && !desired.Contains(kv.Key))
                    toRemove.Add(kv.Key);
            foreach (var key in toRemove)
            {
                _states[key].skyline.Destroy();
                _states[key].skyline = null;
            }

            Vector3 fwd = Camera.main != null
                ? Camera.main.transform.forward : target.forward;
            foreach (var key in desired)
            {
                SectorState st;
                if (!_states.TryGetValue(key, out st))
                {
                    st = new SectorState { key = key };
                    _states.Add(key, st);
                }
                string prefetched;
                if (st.skyline == null || (!st.usedRealGeo &&
                        TileClient.TryGetGeoJson(key, out prefetched) &&
                        !string.IsNullOrEmpty(prefetched)))
                    BuildSkyline(st, key);
                if (st.skyline == null) continue;

                Vector3 d = st.skyline.Position - target.position;
                float dist = d.magnitude;
                bool visible = dist <= SkylineDistanceM &&
                    (dist <= SkylineNearAlwaysM ||
                     Vector3.Dot(fwd, dist > 0.01f ? d / dist : Vector3.forward) > -0.25f);
                st.skyline.SetVisible(visible);
            }
        }

        private void BuildSkyline(SectorState st, string key)
        {
            WorldSectorInfo info;
            WorldStreamingCatalog.TryGet(key, out info);
            Vector3 center;
            float lat, lng;
            if (info != null && info.bbox != null && info.bbox.Length >= 4)
            {
                lat = (float)info.lat;
                lng = (float)info.lng;
            }
            else
            {
                var baseChunk = CityGrid.ChunkIndexOf(_teleportLat != 0 ? _teleportLat : 41.9028,
                                                      _teleportLng != 0 ? _teleportLng : 12.4964);
                var c = CityGrid.ChunkCenter(new Vector2Int(
                    FloorDiv(baseChunk.x, CityGrid.ChunksPerTile) * CityGrid.ChunksPerTile + 5,
                    FloorDiv(baseChunk.y, CityGrid.ChunksPerTile) * CityGrid.ChunksPerTile + 5));
                lat = (float)c.lat;
                lng = (float)c.lng;
            }
            // Centro del settore in coordinate mondo: segue il floating origin
            // esattamente come i root dei chunk.
            center = WorldOrigin.ToWorld(lat, lng);

            TileGeoDoc geo = null;
            string json = null;
            if (TileClient.TryGetGeoJson(key, out json) && !string.IsNullOrEmpty(json))
            {
                try { geo = JsonUtility.FromJson<TileGeoDoc>(json); }
                catch (Exception e) { Debug.LogWarning("[WorldStreaming] geo skyline " + key + ": " + e.Message); }
            }
            st.skyline = new WorldStreamingSkyline();
            st.skyline.Build(key, center, geo, info, _skylineBudget, _root);
            st.usedRealGeo = st.skyline.UsedRealGeo;
        }

        private void CheckMemory()
        {
            if (Time.unscaledTime < _nextMemCheck) return;
            _nextMemCheck = Time.unscaledTime + MemoryCheckS;
            string info = WorldStreamingBridge.GetMemoryInfo();
            _lowMemory = !string.IsNullOrEmpty(info) &&
                (info.IndexOf("\"low\":true", StringComparison.Ordinal) >= 0 ||
                 info.IndexOf("\"low\": true", StringComparison.Ordinal) >= 0);
            int budget = _lowMemory ? LowMemSkylineBuildings : MaxSkylineBuildings;
            if (budget != _skylineBudget)
            {
                _skylineBudget = budget;
                // Cambia il dettaglio solo ai settori non ancora costruiti:
                // riallocare subito tutte le mesh peggiorerebbe il frame.
                OsmDiag.Log("[WorldStreaming] memoria " + (_lowMemory ? "bassa" : "normale") +
                    ", budget edifici skyline=" + budget);
            }
            if (_lowMemory) TileClient.TrimPrefetch(2);
        }

        private void OnRebased(Vector3 delta)
        {
            if (!WorldOrigin.IsFinite(delta)) return;
            foreach (var kv in _states)
                if (kv.Value.skyline != null) kv.Value.skyline.Shift(delta);
        }

        /// <summary>Compatta le risorse solo dopo un teleport, non ogni frame.</summary>
        public void RequestResourceCompaction()
        {
            if (_unloadingUnused || Time.unscaledTime < _nextUnloadUnused) return;
            _unloadingUnused = true;
            _nextUnloadUnused = Time.unscaledTime + UnloadUnusedCooldownS;
            StartCoroutine(CompactRoutine());
        }

        private IEnumerator CompactRoutine()
        {
            yield return Resources.UnloadUnusedAssets();
            _unloadingUnused = false;
            OsmDiag.Log("[WorldStreaming] risorse inutilizzate rilasciate");
        }

        /// <summary>Stato letto da Android tramite getWorldStreamingStatus.</summary>
        public string StatusJson()
        {
            int skylines = 0;
            foreach (var kv in _states)
                if (kv.Value.skyline != null) skylines++;
            string json = "{\"sector\":\"" + (_currentSector ?? "") +
                "\",\"catalog\": " + WorldStreamingCatalog.Count +
                ",\"skylines\":" + skylines +
                ",\"prefetch\":" + TileClient.PrefetchCount +
                ",\"lowMemory\":" + (_lowMemory ? "true" : "false") + "}";
            return json;
        }

        private static int FloorDiv(int a, int b)
        {
            int q = a / b;
            if ((a % b != 0) && ((a < 0) != (b < 0))) q--;
            return q;
        }
    }
}

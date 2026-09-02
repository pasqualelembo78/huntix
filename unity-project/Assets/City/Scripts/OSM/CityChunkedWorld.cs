using System;
using System.Collections;
using System.Globalization;
using System.Text.RegularExpressions;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace City.OSM
{
    /// <summary>
    /// Entry point del mondo chunked (FASE 2). Istanziato da GameManager quando
    /// si entra nella modalita' miacitta: inizializza il floating origin,
    /// disattiva il quartiere finto della scena City e il polling legacy
    /// Android->Overpass, poi avvia lo streaming dei chunk attorno al player.
    /// </summary>
    public class CityChunkedWorld : MonoBehaviour
    {
        public static CityChunkedWorld Instance { get; private set; }

        [Header("Posizione iniziale (default: Roma)")]
        public double startLat = 41.9028;
        public double startLng = 12.4964;

        [Header("GPS")]
        [Tooltip("Su mobile usa il GPS del dispositivo come posizione iniziale")]
        public bool useDeviceGps = true;
        [Tooltip("Secondi di attesa massima del primo fix GPS prima di usare il default")]
        public float gpsWaitSeconds = 12f;

        [Header("Debug")]
        [Tooltip("HUD on-screen con GPS live, posizione player e yaw camera (verifica sul campo)")]
        public bool enableGpsHud = true;
        [Header("HUD")]
        [Tooltip("Indirizzo live (via + civico) in basso al centro")]
        public bool enableAddressHud = true;
        [Tooltip("Minimap nord-su ~10 km2 in alto a destra con pallino rosso del player")]
        public bool enableMinimap = true;

        [Header("Opzioni")]
        public bool disableLegacyCityOsmWorld = true;

        [Header("Quartiere finto della scena City")]
        public string legacyQuarterRootName = "Citta";

        public ChunkManager Manager { get; private set; }
        private bool _inCityScene;
        private bool _hasDeviceFix;
        private float _nextHealthCheck;
        private float _nextGroundProbe;
        private GameObject _spawnBridge;

        /// <summary>
        /// Auto-riparazione: se qualcosa distrugge il ChunkManager (es. reload
        /// della scena) lo ricrea entro 5 secondi e logga lo stato. Un tempo era
        /// impossibile accorgersene: il mondo restava muto e senza chunk.
        /// Esegue anche la sonda del terreno con salvataggio anti-caduta.
        /// </summary>
        private void Update()
        {
            if (!_inCityScene) return;

            // ── sonda terreno + salvataggio ogni 2 s ──
            GroundProbeAndRescue();

            if (Time.unscaledTime < _nextHealthCheck) return;
            _nextHealthCheck = Time.unscaledTime + 5f;

            bool vivo = Manager != null;
            int chunks = -1;
            if (vivo) { try { chunks = Manager.LoadedCount; } catch { vivo = false; } }

            if (!vivo)
            {
                var go = new GameObject("ChunkManager");
                DontDestroyOnLoad(go);
                Manager = go.AddComponent<ChunkManager>();
                // congelato finche' EnterCityWhenReady non ha fissato
                // l'origine definitiva: senza fix GPS streamerebbe verso
                // l'origine di default (Roma) a centinaia di km dal player
                Manager.StreamingEnabled = false;
                Manager.target = ResolvePlayer();
                OsmDiag.Log("[CityChunkedWorld] health: managerVivo=False -> ricreato");
            }
            else
            {
                OsmDiag.Log("[CityChunkedWorld] health: managerVivo=True chunks=" + chunks +
                    " costruiti=" + Manager.BuiltCount);
                // abbastanza chunk COSTRUITI coprono l'area di gioco: il ponte
                // non serve piu' (LoadedCount conta anche chunk in costruzione
                // o scaricati: usarlo qui ha tolto il ponte sopra il vuoto)
                if (Manager.BuiltCount >= 25) RemoveSpawnBridge();
            }
        }

        /// <summary>
        /// Crea il mondo chunked se assente (chiamato da GameManager dentro
        /// LoadSceneForMode). CRITICO DontDestroyOnLoad: EnsureInstance e'
        /// chiamato mentre la scena precedente e' ancora caricata subito dopo
        /// SceneManager.LoadScene("City"); senza persistenza l'oggetto verrebbe
        /// distrutto dal cambio scena, lasciando il quartiere finto a schermo.
        /// </summary>
        public static CityChunkedWorld EnsureInstance()
        {
            if (Instance != null) return Instance;
            OsmDiag.Log("[CityChunkedWorld] === MIACITTA ENTRY === EnsureInstance called");
            var go = new GameObject("CityChunkedWorld");
            DontDestroyOnLoad(go);
            return go.AddComponent<CityChunkedWorld>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        private void OnEnable()
        {
            SceneManager.sceneLoaded += OnSceneLoaded;
        }

        private void OnDisable()
        {
            SceneManager.sceneLoaded -= OnSceneLoaded;
        }

        private void Start()
        {
            // caso raro: creato GIÀ dentro la scena City (reload della scena)
            if (SceneManager.GetActiveScene().name == "City")
                EnterCityScene();
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name == "City")
            {
                EnterCityScene();
            }
            else if (_inCityScene)
            {
                // usciti dalla citta': liberare i chunk streamati e la rete
                _inCityScene = false;
                RemoveSpawnBridge();
                if (Manager != null) Manager.UnloadAll();
                Debug.Log("[CityChunkedWorld] uscita dalla citta', chunk scaricati");
            }
        }

        /// <summary>Chiamato ogni volta che la scena City finisce di caricare.</summary>
        private void EnterCityScene()
        {
            if (_inCityScene) return;
            OsmDiag.Log("[CityChunkedWorld] === MIACITTA === EnterCityScene (Unity splash done)");
            _inCityScene = true;

            // CRITICO: colliders generati a runtime senza autoSyncTransforms non
            // vengono registrati in PhysX (vedi CityOSMWorld.Init).
            Physics.autoSyncTransforms = true;

            // il tracking Android parte poco prima del cambio scena: i primi
            // valori letti sono 0,0 (nessun fix). Attendiamo un fix valido.
            StartCoroutine(EnterCityWhenReady());
        }

        private IEnumerator EnterCityWhenReady()
        {
            OsmDiag.Log("[CityChunkedWorld] === MIACITTA === EnterCityWhenReady START");
            // NB: niente try/catch esterno (C# vieta yield dentro try-catch);
            // le sezioni a rischio sono protette singolarmente
            if (useDeviceGps && Application.isMobilePlatform)
            {
                // CRITICO: il tracking GPS Android era avviato solo dal legacy
                // CityOSMWorld; con il mondo chunked va avviato qui, altrimenti
                // currentLocationSync() resta null (0,0) per sempre. Il seed
                // Kotlin usa subito l'ultima posizione nota del sistema.
                _hasDeviceFix = false;
                OsmDiag.Log("[CityChunkedWorld] richiesto avvio tracking GPS");
                try { Huntix.Bridge.UnityBridge.StartLocationTracking(); }
                catch (Exception e) { Debug.LogWarning("[CityChunkedWorld] StartLocationTracking: " + e.Message); }

                float waited = 0f;
                while (waited < gpsWaitSeconds && !_hasDeviceFix)
                {
                    TryReadGpsOnce();
                    if (!_hasDeviceFix)
                    {
                        // realtime: immune da timeScale bloccato
                        yield return new WaitForSecondsRealtime(0.5f);
                        waited += 0.5f;
                    }
                }
                if (!_hasDeviceFix)
                    Debug.LogWarning("[CityChunkedWorld] nessun fix GPS entro " +
                        gpsWaitSeconds + "s, uso posizione default");
            }

            try
            {
                WorldOrigin.Init(startLat, startLng);
                CoordinateConverter.Init(startLat, startLng);

                if (disableLegacyCityOsmWorld && CityOSMWorld.Instance != null)
                {
                    CityOSMWorld.Instance.PrepareExit();
                    CityOSMWorld.Instance.enabled = false;
                    Debug.Log("[CityChunkedWorld] CityOSMWorld legacy disattivato");
                }

                // il quartiere finto pre-costruito della scena City lascia il posto
                // al mondo streamato dalle tile OSM (a ogni load torna attivo)
                if (!string.IsNullOrEmpty(legacyQuarterRootName))
                {
                    var quarter = GameObject.Find(legacyQuarterRootName);
                    if (quarter != null)
                    {
                        quarter.SetActive(false);
                        Debug.Log("[CityChunkedWorld] quartiere legacy '" +
                                  legacyQuarterRootName + "' disattivato");
                    }
                }

                // Riabilita il CharacterController che
                // CityOSMWorld.HideSeedCityAndFreezePlayer() aveva disabilitato.
                // Senza questo il player resta sospeso a mezz aria (no gravita,
                // no movimento).  Se il player era figlio del quartiere legacy
                // disattivato, lo stacca e lo riattiva.
                if (City.Game.Instance != null && City.Game.Instance.player != null)
                {
                    var pl = City.Game.Instance.player;
                    if (!pl.gameObject.activeSelf)
                    {
                        pl.transform.SetParent(null);
                        pl.gameObject.SetActive(true);
                        Debug.Log("[CityChunkedWorld] player riattivato (era figlio del quartiere legacy)");
                    }
                    var cc = pl.GetComponent<CharacterController>();
                    if (cc != null && !cc.enabled)
                    {
                        cc.enabled = true;
                        Debug.Log("[CityChunkedWorld] CharacterController riabilitato");
                    }
                }
            }
            catch (Exception e)
            {
                Debug.LogError("[CityChunkedWorld] errore init mondo: " + e);
            }

            if (Manager == null)
            {
                var go = new GameObject("ChunkManager");
                // persistente: un eventuale reload della scena City non deve
                // distruggere lo streaming (vedi GameManager double-load bug)
                DontDestroyOnLoad(go);
                Manager = go.AddComponent<ChunkManager>();
            }

            // Rete di sicurezza: con StreamingEnabled=false l'health-check non
            // puo' piu' aver costruito chunk sull'origine default, ma se un
            // percorso futuro ricreasse il manager attivo prima del fix GPS
            // l'epoca incrementata da Init() rende comunque orfani i chunk
            // prematuri: buttiamoli prima che il tick li costruisca a ±260 km.
            if (Manager.LoadedCount > 0)
            {
                OsmDiag.Log("[CityChunkedWorld] origine definitiva fissata: scarto " +
                    Manager.LoadedCount + " chunk prematuri");
                Manager.UnloadAll();
            }

            if (enableGpsHud && GameObject.Find("GpsDebugHud") == null)
            {
                GpsDebugHud.Create();
                OsmDiag.Log("[CityChunkedWorld] HUD diagnostico GPS attivo");
            }
            if (enableAddressHud && GameObject.Find("LocationHud") == null)
                LocationHud.Create();
            if (enableMinimap && GameObject.Find("MinimapHud") == null)
                MinimapHud.Create();
            if (GameObject.Find("CompassUI") == null)
                City.Vehicle.CompassUI.Create();
            if (GameObject.Find("MapSelectUI") == null)
                City.OSM.MapSelectUI.Ensure();
            if (GameObject.Find("OfferDialog") == null)
                City.UI.OfferDialog.Ensure();

            // ── veicoli: spawner, shop UI, popolatore chunk e API possesso ──
            if (City.Vehicle.VehicleSpawnManager.Instance == null)
            {
                var vgo = new GameObject("VehicleSpawnManager");
                DontDestroyOnLoad(vgo);
                vgo.AddComponent<City.Vehicle.VehicleSpawnManager>();
                OsmDiag.Log("[CityChunkedWorld] VehicleSpawnManager creato");
            }
            if (City.Economy.JobManager.Instance == null)
            {
                var jgo = new GameObject("JobManager");
                DontDestroyOnLoad(jgo);
                jgo.AddComponent<City.Economy.JobManager>();
            }
            if (City.Environment.ChaosManager.Instance == null)
            {
                var cgo = new GameObject("ChaosManager");
                DontDestroyOnLoad(cgo);
                cgo.AddComponent<City.Environment.ChaosManager>();
            }
            City.Vehicle.ChunkVehiclePopulator.Ensure();
            // registro dei veicoli venduti: serve prima che il player
            // raggiunga la prima auto (prompt corretto al primo contatto)
            City.Vehicle.VehicleOwnershipApi.Ensure().Refresh();

            // PONTE DI SPAWN: tra il teletrasporto del player e l'arrivo dei
            // primi chunk passano ~1-2s durante i quali NON esiste alcun
            // collider (il quartiere legacy e' disattivato): il player cadeva
            // nel vuoto sotto il mondo. Questo piano provvisorio garantisce
            // terra ferma finché i chunk non sono pronti.
            EnsureSpawnBridge();
            Manager.target = ResolvePlayer();
            // se il player non e' ancora pronto (Game.Instance popolato dopo
            // sceneLoaded) riprovare qualche istante prima di arrendersi
            float retry = 0f;
            while (Manager.target == null && retry < 5f)
            {
                yield return null;
                retry += Time.unscaledDeltaTime;
                Manager.target = ResolvePlayer();
            }
            if (Manager.target != null)
                Manager.target.position = new Vector3(0f, 1.5f, 0f);   // origine = punto GPS

            // Attendi il refresh dei veicoli posseduti prima di partire coi
            // chunk: altrimenti i primi chunk popolano il parcheggio con
            // l'auto "in vendita" dell'utente mentre lo stato reale (proprieta')
            // arriva in ritardo, e le auto possedute spariscono all'avvio.
            var vehApi = City.Vehicle.VehicleOwnershipApi.Instance;
            float vehWait = 0f;
            while (Manager.target != null && !vehApi.Refreshed && vehWait < 8f)
            {
                yield return null;
                vehWait += Time.unscaledDeltaTime;
            }

            // origine definitiva fissata e target agganciato: si puo' partire
            // (l'health-check puo' aver creato il manager congelato in attesa
            // del fix GPS)
            Manager.StreamingEnabled = true;
            OsmDiag.Log("[CityChunkedWorld] === MIACITTA === StreamingEnabled=true");

            OsmDiag.Log("[CityChunkedWorld] attivo su " +
                startLat.ToString(CultureInfo.InvariantCulture) + "," +
                startLng.ToString(CultureInfo.InvariantCulture) +
                " (timeScale=" + Time.timeScale +
                ", Game.Instance=" + (City.Game.Instance != null) +
                ", player=" + (City.Game.Instance != null && City.Game.Instance.player != null) +
                ", Camera.main=" + (Camera.main != null) + ")");
        }

        private Transform ResolvePlayer()
        {
            var game = City.Game.Instance;
            if (game != null && game.player != null) return game.player.transform;
            if (Camera.main != null) return Camera.main.transform;
            return null;
        }

        /// <summary>Legge il GPS del dispositivo (bridge Android): accetta il
        /// valore solo se lat E lng sono un fix reale (non 0,0), altrimenti
        /// mantiene il default/precedente in attesa del prossimo poll.</summary>
        private void TryReadGpsOnce()
        {
            if (!useDeviceGps || !Application.isMobilePlatform) return;
            try
            {
                string json = Huntix.Bridge.UnityBridge.GetCurrentLocation();
                double lat = startLat, lng = startLng;
                bool latOk = false, lngOk = false;
                if (!string.IsNullOrEmpty(json))
                {
                    Match m = Regex.Match(json, "\"lat\"\\s*:\\s*(-?[0-9.eE+-]+)");
                    if (m.Success)
                        latOk = double.TryParse(m.Groups[1].Value, NumberStyles.Float,
                            CultureInfo.InvariantCulture, out lat);
                    // NB: lng va sempre parsato INDEPENDENTEMENTE dal lat
                    // (bug precedente: "!parsed" saltava il lng se il lat era ok,
                    //  lasciando la longitudine di Roma col lat reale del GPS)
                    m = Regex.Match(json, "\"lng\"\\s*:\\s*(-?[0-9.eE+-]+)");
                    if (m.Success)
                        lngOk = double.TryParse(m.Groups[1].Value, NumberStyles.Float,
                            CultureInfo.InvariantCulture, out lng);
                }
                Debug.Log("[CityChunkedWorld] gps letto " +
                    lat.ToString(CultureInfo.InvariantCulture) + "," +
                    lng.ToString(CultureInfo.InvariantCulture) +
                    " (json: " + json + ")");
                // servono ENTRAMBE le coordinate valide: 0,0 e' nell'oceano
                if (latOk && lngOk &&
                    System.Math.Abs(lat) > 0.001 && System.Math.Abs(lng) > 0.001)
                {
                    startLat = lat;
                    startLng = lng;
                    _hasDeviceFix = true;
                    Debug.Log("[CityChunkedWorld] posizione iniziale " +
                        startLat.ToString(CultureInfo.InvariantCulture) + "," +
                        startLng.ToString(CultureInfo.InvariantCulture));
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityChunkedWorld] GPS non disponibile, uso default: " + e.Message);
            }
        }

        /// <summary>Teletrasporto geografico (es. scelta citta' dal menu).</summary>
        public void Teleport(double lat, double lng)
        {
            startLat = lat;
            startLng = lng;
            WorldOrigin.Init(lat, lng);
            CoordinateConverter.Init(lat, lng);
            if (Manager != null)
            {
                // UnloadAll distrugge i collider dei chunk: senza ponte il
                // player ricadrebbe nel vuoto durante il rebuild
                EnsureSpawnBridge();
                Manager.UnloadAll();
                if (Manager.target != null)
                    Manager.target.position = new Vector3(0f, 1.5f, 0f);
            }
        }

        /// <summary>
        /// Piano con collider 400x400 m a y=0 attivo fin dal primo frame:
        /// copre il periodo tra teletrasporto del player e arrivo dei primi
        /// chunk (1-2 s) in cui non esiste alcun terreno. Rimosso quando i
        /// chunk streamati sono sufficientemente densi.
        /// </summary>
        private void EnsureSpawnBridge()
        {
            if (_spawnBridge != null) return;
            _spawnBridge = GameObject.Find("SpawnBridge");
            if (_spawnBridge != null) return;

            const float half = 200f;
            var mesh = new Mesh { name = "SpawnBridgeMesh" };
            mesh.vertices = new[]
            {
                new Vector3(-half, 0f, -half), new Vector3(half, 0f, -half),
                new Vector3(-half, 0f, half), new Vector3(half, 0f, half),
            };
            mesh.triangles = new[] { 0, 2, 1, 2, 3, 1 };   // normale verso l'alto
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            _spawnBridge = new GameObject("SpawnBridge",
                typeof(MeshFilter), typeof(MeshCollider));
            _spawnBridge.GetComponent<MeshFilter>().sharedMesh = mesh;
            _spawnBridge.GetComponent<MeshCollider>().sharedMesh = mesh;
            DontDestroyOnLoad(_spawnBridge);
            OsmDiag.Log("[CityChunkedWorld] SpawnBridge attivo (rete di sicurezza)");
        }

        private void RemoveSpawnBridge()
        {
            if (_spawnBridge == null) return;
            Destroy(_spawnBridge);
            _spawnBridge = null;
            OsmDiag.Log("[CityChunkedWorld] SpawnBridge rimosso (chunk pronti)");
        }

        /// <summary>
        /// Sonda del terreno ogni 2 s: se il player risulta sotto il mondo
        /// (caduto attraverso un collider mancante o un buco tra chunk) lo
        /// riporta sulla superficie invece di lasciarlo precipitare. Logga
        /// anche cosa c'e' sotto i piedi: serve per diagnosticare cadute.
        /// </summary>
        private void GroundProbeAndRescue()
        {
            if (Time.unscaledTime < _nextGroundProbe) return;
            _nextGroundProbe = Time.unscaledTime + 2f;
            if (Manager == null || Manager.target == null) return;

            Vector3 p = Manager.target.position;

            // caduto sotto il mondo?
            if (p.y < -15f)
            {
                Vector3 from = new Vector3(p.x, p.y + 100f, p.z);
                Vector3 dest;
                string how;
                if (Physics.Raycast(from, Vector3.down, out RaycastHit hit, 300f))
                {
                    dest = hit.point + Vector3.up * 1.2f;
                    how = "superficie=" + hit.collider.name;
                }
                else
                {
                    // nessun collider sopra: centro del chunk corrente (dentro
                    // il raggio caricato) + riattiva il ponte come rete
                    GeoCoord g = WorldOrigin.ToGeo(p);
                    GeoCoord c = CityGrid.ChunkCenter(
                        CityGrid.ChunkIndexOf(g.lat, g.lng));
                    Vector3 w = WorldOrigin.ToWorld(c);
                    dest = new Vector3(w.x, 1.2f, w.z);
                    EnsureSpawnBridge();
                    how = "chunk-center+bridge";
                }
                var game = City.Game.Instance;
                Quaternion rot = Manager.target.rotation;
                if (game != null && game.player != null)
                    game.TeleportPlayer(dest, rot);
                else
                    Manager.target.position = dest;
                OsmDiag.Log("[CityChunkedWorld] SALVATAGGIO: player sotto il mondo " +
                    "(y=" + p.y.ToString("F1", CultureInfo.InvariantCulture) +
                    ") -> (" + dest.x.ToString("F1", CultureInfo.InvariantCulture) +
                    "," + dest.z.ToString("F1", CultureInfo.InvariantCulture) +
                    ") via " + how);
                return;
            }

            // diagnostica periodica: cosa c'e' sotto i piedi del player
            if (!Physics.Raycast(p + Vector3.up * 5f, Vector3.down,
                    out RaycastHit ground, 50f))
            {
                OsmDiag.Log("[CityChunkedWorld] ATTENZIONE: nessun terreno sotto " +
                    "il player (y=" + p.y.ToString("F1", CultureInfo.InvariantCulture) + ")");
            }
        }
    }
}

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

        /// <summary>True dopo il gate di spawn: il player e' sbloccato e puo'
        /// essere considerato "in citta'". I trigger porta ignorano il player
        /// finche' non e' spawnato: un edificio puo' coprire l'origine e il
        /// suo trigger d'ingresso "inglobare" lo spawn (finto ingresso
        /// automatico in una casa mentre si e' ancora in attesa dei chunk).</summary>
        public bool PlayerSpawned { get { return _spawnGateOpened; } }

        [Header("Posizione iniziale (default: Roma)")]
        public double startLat = 41.9028;
        public double startLng = 12.4964;

        [Header("GPS")]
        [Tooltip("DISATTIVATO: la posizione iniziale e' decisa dal selettore Android (citta' o ultima posizione salvata), mai dal GPS del dispositivo")]
        public bool useDeviceGps = false;
        [Tooltip("Rimasto per compatibilita': mai usato con useDeviceGps=false")]
        public float gpsWaitSeconds = 12f;

        [Header("Debug")]
        [Tooltip("HUD on-screen con GPS live, posizione player e yaw camera (verifica sul campo)")]
        public bool enableGpsHud = false;
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
        /// <summary>Timeout di sicurezza del gate di spawn: se il primo chunk
        /// non arriva (offline) il player viene comunque sbloccato dopo N s.</summary>
        private const float SpawnGateTimeoutS = 60f;
/// <summary>Timeout per la registrazione in PhysX del MeshCollider del
/// terreno creato a runtime: alla fine del build il collider non e'
/// ancora queryable (a Perugia la colonna diagnostica 600m vedeva solo
/// SpawnBridge nonostante chunk built=True e bounds validi).</summary>
private const float TerrainPhysxTimeoutS = 15f;
        private bool _inCityScene;
        private bool _hasDeviceFix;
        private float _nextHealthCheck;
        private float _nextGroundProbe;
        private float _nextTerrainDiag;
        private GameObject _spawnBridge;

        /// <summary>Pubblico: usato da Game.SnapToGround per escludere
        /// il ponte di sicurezza dal raycast di snap dopo l'uscita veicolo.</summary>
        public GameObject SpawnBridge { get { return _spawnBridge; } }

        // Punto di spawn DECISO da Android (selettore citta'/ultima posizione
        // salvata, arrivato via JSON setMode). Ha priorita' assoluta sulla
        // posizione persistita: il GPS del dispositivo non viene MAI usato.
        private static double? _spawnOverrideLat;
        private static double? _spawnOverrideLng;

        // Throttle dell'invio della posizione di gioco ad Android (che la
        // carica su Google): al massimo ogni 30 s oppure a ogni spostamento
        // superiore a 50 m, per non saturare la quota Firestore.
        private Vector3 _lastCloudPushWorld;
        private float _lastCloudPushTime = -100f;

        /// <summary>Chiamato da GameManager con lo spawn scelto da Android
        /// ({"spawnLat":..,"spawnLng":..} nel JSON setMode).</summary>
        public static void UseSpawnPoint(double lat, double lng)
        {
            _spawnOverrideLat = lat;
            _spawnOverrideLng = lng;
            Debug.Log("[CityChunkedWorld] UseSpawnPoint: " +
                lat.ToString(CultureInfo.InvariantCulture) + "," +
                lng.ToString(CultureInfo.InvariantCulture));
        }

        // L'overlay di caricamento nativo (BridgeActivity) viene chiuso quando
        // i primi chunk sono costruiti (CityReady): lo mandiamo UNA volta per
        // ingresso in citta' al raggiungimento della stessa soglia del ponte.
        private bool _cityReadyNotified;

        // Il gate di spawn (EnterCityWhenReady) si e' concluso: solo DOPO questo
        // punto l'health-check puo' chiudere lo splash e togliere il ponte di
        // sicurezza. Prima del gate lo splash deve restare aperto: chiudendolo
        // presto si vedeva la citta' bloccata (player ancora congelato) per
        // decine di secondi con l'overlay gia' sparito.
        private bool _spawnGateOpened;

        // Posizione ripresa dal profilo (world_game_prefs):
        //  - "worldPos"          : ultima posizione di gioco (string "lat,lng")
        //  - "spawnLat"/"spawnLng": scelta del selettore Android (citta' o
        //    ultima posizione), usata se worldPos manca (fallback al JSON).
        //  - "gpsLat"/"gpsLng"    : residuo ultima posizione, ultimo fallback.
        // Il GPS del dispositivo non viene MAI letto.
        private bool _hasSavedWorldPos;
        private float _nextWorldPosSave;

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

            // salva la posizione di gioco corrente circa ogni 3 s (ripartenza)
            if (Time.unscaledTime >= _nextWorldPosSave)
            {
                _nextWorldPosSave = Time.unscaledTime + 3f;
                SaveWorldPosition();
            }

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
                if (_spawnGateOpened && Manager.BuiltCount >= 25)
                {
                    bool ok = RealTerrainBelow(Manager.target);
                    if (ok)
                    {
                        RemoveSpawnBridge();
                        NotifyCityReady();
                        try
                        {
                            var telemetry = City.Diagnostics.SessionTelemetry.Instance;
                            if (telemetry != null)
                                telemetry.WorldReady(0, Manager.BuiltCount, 0, 0);
                        }
                        catch (System.Exception) {}
                    }
                    else
                    {
                        OsmDiag.Log("[CityChunkedWorld] health: BuiltCount>=25 ma terreno NON rilevato - ponte mantenuto per sicurezza");
                    }
                }
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
                // cattura l'ultima posizione prima di scaricare i chunk
                SaveWorldPosition();
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
            _cityReadyNotified = false;

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

            // CRITICO: nascondi la seed city E congela il player SUBITO, prima
            // dell'attesa GPS (fino a 12s).  Senza questo la seed city e'
            // visibile per diversi secondi prima che i chunk OSM arrivino.
            if (!string.IsNullOrEmpty(legacyQuarterRootName))
            {
                var quarter = GameObject.Find(legacyQuarterRootName);
                if (quarter != null)
                {
                    quarter.SetActive(false);
                    Debug.Log("[CityChunkedWorld] quartiere legacy nascosto SUBITO (pre-GPS)");
                }
            }
            if (City.Game.Instance != null && City.Game.Instance.player != null)
            {
                var pl = City.Game.Instance.player;
                // stacca il player dal quarter legacy (sarebbe distrutto col disable)
                pl.transform.SetParent(null);
                if (!pl.gameObject.activeSelf) pl.gameObject.SetActive(true);
                var cc = pl.GetComponent<CharacterController>();
                if (cc != null)
                {
                    cc.enabled = false;
                    Debug.Log("[CityChunkedWorld] CharacterController disabilitato (attesa GPS)");
                }
            }

// Ripresa posizione: PRIORITA' ASSOLUTA alla scelta dello spawn di
            // Android (citta' o ultima posizione salvata, via JSON setMode).
            // In mancanza si riparte dalla posizione persistita ("worldPos"),
            // mai dal GPS del telefono.
            ApplySpawnOrigin();

            // GPS del dispositivo: MAI usato. Il blocco sotto resta solo come
            // sicurezza esplicita: con useDeviceGps=false non entra mai.
            if (useDeviceGps && Application.isMobilePlatform && !_hasSavedWorldPos)
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

                // La seed city e' gia' stata nascosta all'inizio di EnterCityWhenReady.
                // Assicuriamoci che sia ancora spenta (potrebbe essere tornata attiva
                // se la scena e' stata ricaricata).
                if (!string.IsNullOrEmpty(legacyQuarterRootName))
                {
                    var quarter = GameObject.Find(legacyQuarterRootName);
                    if (quarter != null && quarter.activeSelf)
                    {
                        quarter.SetActive(false);
                        Debug.Log("[CityChunkedWorld] quartiere legacy ri-disattivato");
                    }
                }

                // Il CharacterController resta DISABILITATO fino al gate di
                // spawn (il player diventa attivo solo quando il primo chunk
                // attorno a lui e' costruito): riattivato da EnablePlayerController()
                // dopo il gate in EnterCityWhenReady. Sul ponte di sicurezza il
                // player non cade intanto.
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

            // ── GATE SPAWN (requisito #7/: il primo chunk PRIMA del player) ──
            // Il player resta congelato (CC spento, sul ponte di sicurezza)
            // finche' il chunk che contiene il suo punto di spawn non e'
            // costruito (= terreno, strade, prima cella di edifici/alberi/
            // pedoni gia' in memoria/scena). Solo a quel punto si abilita il
            // movimento, si chiude lo splash e la citta' e' giocabile.
            Vector2Int intro = CityGrid.ChunkIndexOf(startLat, startLng);
            float gateStart = Time.unscaledTime;
            while (Manager != null && !Manager.IsChunkBuilt(intro))
            {
                if (Time.unscaledTime - gateStart > SpawnGateTimeoutS) break;
                yield return new WaitForSecondsRealtime(0.2f);
            }
            if (Manager == null || !Manager.IsChunkBuilt(intro))
                OsmDiag.Log("[CityChunkedWorld] gate spawn: primo chunk (" + intro.x +
                    "," + intro.y + ") non pronto entro " + SpawnGateTimeoutS +
                    "s -> spawn su ponte di sicurezza");
            else
                OsmDiag.Log("[CityChunkedWorld] gate spawn: primo chunk (" + intro.x +
                    "," + intro.y + ") costruito: player abilitato, splash chiusa");
            _spawnGateOpened = true;
            // Perugia: il collider del terreno creato a runtime NON e' ancora
            // registrato in PhysX per qualche frame dalla BUILD DONE. Con il solo
            // VerifyTerrainReady il gate lo vede gia' pronto (collider enabed,
            // bounds validi) ma la colonna diagnostica 600m trova SOLO SpawnBridge:
            // il raycast non colpisce ancora il terreno reale. Retry con
            // sincronizzazione esplicita fino a timeout breve, senno' il player
            // nascerebbe sul ponte di sicurezza invece che sulla superficie vera
            // (a Perugia il terreno arriva a y~22 mentre il ponte resta a y=0).
            bool terrainReady = false;
            if (Manager != null && Manager.IsChunkBuilt(intro))
            {
                float physStart = Time.unscaledTime;
                while (Manager != null && !terrainReady &&
                       Time.unscaledTime - physStart < TerrainPhysxTimeoutS)
                {
                    Physics.SyncTransforms();
                    terrainReady = Manager.VerifyTerrainReady(Manager.target.position) &&
                        RealTerrainBelow(Manager.target);
                    if (!terrainReady)
                        yield return new WaitForSecondsRealtime(0.2f);
                }
            }
            if (!terrainReady)
            {
                OsmDiag.Log("[CityChunkedWorld] gate: terreno NON registrato in PhysX entro " +
                    TerrainPhysxTimeoutS + "s - ponte di sicurezza mantenuto (collider chunk attesi)");
            }
            // Elevazione reale del terreno: il player deve nascere SUL terreno e
            // non a y=0 (flat). Snap sul terreno appena i collider del chunk di
            // spawn esistono (dopo il gate). VA FATTO PRIMA di riabilitare il
            // CharacterController: con il CC attivo il raycast da y+200 parte
            // sopra il player ma colpisce per PRIMO il suo capsule ("collider
            // Player"), mai il terreno, e lo snap piazza il player sotto la
            // superficie DEM (mesh one-sided) facendolo sprofondare nel vuoto.
            SnapPlayerToTerrain(Manager != null ? Manager.target : null);
            EnablePlayerController();
            // Chunk del player costruito: il ponte di sicurezza non serve piu'
            // (terreno sotto i piedi). In caso di timeout resta attivo finche'
            // l'health-check non vede BuiltCount>=25 (soglia del ponte).
            if (terrainReady)
            {
                // rimuovi il ponte solo se c'e' davvero un collider terreno
                // fisico sotto i piedi del player; altrimenti il player cadrebbe
                // nel vuoto senza rete di sicurezza
                RemoveSpawnBridge();
            }
            if (Manager != null) Manager.StopProgressReporting();
            NotifyCityReady();

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

/// <summary>
        /// Decide l'origine di spawn. PRIORITA' 1: lo spawn scelto da Android
        /// (selettore citta'/ultima posizione, via JSON setMode -> UseSpawnPoint).
        /// In mancanza si usa la posizione persistita (mai il GPS del telefono).
        /// </summary>
        private void ApplySpawnOrigin()
        {
            if (_spawnOverrideLat.HasValue && _spawnOverrideLng.HasValue)
            {
                startLat = _spawnOverrideLat.Value;
                startLng = _spawnOverrideLng.Value;
                _spawnOverrideLat = null;
                _spawnOverrideLng = null;
                _hasSavedWorldPos = true;
                Debug.Log("[CityChunkedWorld] spawn scelto da Android: " +
                    startLat.ToString(CultureInfo.InvariantCulture) + "," +
                    startLng.ToString(CultureInfo.InvariantCulture));
                return;
            }
            ReadPersistedPosition();
        }

        /// <summary>
        /// Riporta startLat/startLng alla posizione persistita (world_game_prefs):
        /// priorita' a "worldPos" (ultima posizione di gioco), poi alla scelta
        /// del selettore persistita ("spawnLat"/"spawnLng"), infine al residuo
        /// "gpsLat"/"gpsLng". MAI dal GPS del dispositivo.
        /// </summary>
        private void ReadPersistedPosition()
        {
            if (!Application.isMobilePlatform) return;
            try
            {
                using (var playerClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = playerClass.GetStatic<AndroidJavaObject>("currentActivity"))
                using (var prefs = activity.Call<AndroidJavaObject>("getSharedPreferences",
                    "world_game_prefs", 0))
                {
                    // 1) ultima posizione di gioco (riparti da dove eri)
                    string worldPos = prefs.Call<string>("getString", "worldPos", "");
                    if (!string.IsNullOrEmpty(worldPos))
                    {
                        string[] parts = worldPos.Split(',');
                        if (parts.Length == 2 &&
                            double.TryParse(parts[0], NumberStyles.Float, CultureInfo.InvariantCulture, out double lat) &&
                            double.TryParse(parts[1], NumberStyles.Float, CultureInfo.InvariantCulture, out double lng) &&
                            System.Math.Abs(lat) > 0.001 && System.Math.Abs(lng) > 0.001)
                        {
                            startLat = lat;
                            startLng = lng;
                            _hasSavedWorldPos = true;
                            Debug.Log("[CityChunkedWorld] riparto dall'ultima posizione: " +
                                lat.ToString(CultureInfo.InvariantCulture) + "," +
                                lng.ToString(CultureInfo.InvariantCulture));
                            return;
                        }
                    }

                    // 2) scelta del selettore Android persistita (se il JSON setMode
                    // non e' arrivato, es. riapertura rapida senza cambio scena)
                    float slat = prefs.Call<float>("getFloat", "spawnLat", 0f);
                    float slng = prefs.Call<float>("getFloat", "spawnLng", 0f);
                    if (System.Math.Abs(slat) > 0.001f && System.Math.Abs(slng) > 0.001f)
                    {
                        startLat = slat;
                        startLng = slng;
                        _hasSavedWorldPos = true;
                        Debug.Log("[CityChunkedWorld] spawn dal selettore Android: " +
                            slat.ToString(CultureInfo.InvariantCulture) + "," +
                            slng.ToString(CultureInfo.InvariantCulture));
                        return;
                    }

                    // 3) residuo dell'ultima posizione (non piu' GPS di registrazione)
                    float glat = prefs.Call<float>("getFloat", "gpsLat", 0f);
                    float glng = prefs.Call<float>("getFloat", "gpsLng", 0f);
                    if (System.Math.Abs(glat) > 0.001f && System.Math.Abs(glng) > 0.001f)
                    {
                        startLat = glat;
                        startLng = glng;
                        _hasSavedWorldPos = true;
                        Debug.Log("[CityChunkedWorld] ultima posizione residua: " +
                            glat.ToString(CultureInfo.InvariantCulture) + "," +
                            glng.ToString(CultureInfo.InvariantCulture));
                    }
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityChunkedWorld] lettura posizione persistita: " + e.Message);
            }
        }

        /// <summary>
        /// Salva la posizione corrente del player (geo = WorldOrigin) in
        /// "worldPos" di world_game_prefs E la invia ad Android ("PlayerPosition")
        /// che la carica su Google (Firestore). Alla prossima apertura si
        /// riparte sempre dall'ultima posizione di gioco, mai dal GPS.
        /// </summary>
        private void SaveWorldPosition()
        {
            if (!Application.isMobilePlatform) return;
            if (Manager == null || Manager.target == null) return;
            if (!WorldOrigin.Initialized) return;
            try
            {
                var g = WorldOrigin.ToGeo(Manager.target.position);
                string pos = g.lat.ToString("R", CultureInfo.InvariantCulture) + "," +
                             g.lng.ToString("R", CultureInfo.InvariantCulture);
                using (var playerClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = playerClass.GetStatic<AndroidJavaObject>("currentActivity"))
                using (var prefs = activity.Call<AndroidJavaObject>("getSharedPreferences",
                    "world_game_prefs", 0))
                {
                    prefs.Call<AndroidJavaObject>("edit")
                        .Call<AndroidJavaObject>("putString", "worldPos", pos)
                        .Call<bool>("commit");
                }
                PushPositionToAndroid(Manager.target.position, g);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityChunkedWorld] salvataggio posizione: " + e.Message);
            }
        }

        /// <summary>Invia la posizione ad Android ("PlayerPosition") per il
        /// salvataggio su Google. Throttle: al massimo ogni 30 s, oppure
        /// subito quando il player si sposta oltre 50 m dal push precedente.</summary>
        private void PushPositionToAndroid(Vector3 world, GeoCoord g)
        {
            if (Time.unscaledTime < _lastCloudPushTime + 30f &&
                Vector3.Distance(world, _lastCloudPushWorld) < 50f)
                return;
            _lastCloudPushTime = Time.unscaledTime;
            _lastCloudPushWorld = world;
            string json = "{\"lat\":\"" +
                g.lat.ToString("R", CultureInfo.InvariantCulture) +
                "\",\"lng\":\"" +
                g.lng.ToString("R", CultureInfo.InvariantCulture) + "\"}";
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("PlayerPosition", json);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityChunkedWorld] invio posizione ad Android: " + e.Message);
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
            // persisti subito il nuovo punto: alla prossima apertura si riparte
            // da qui (es. geoport dopo CENTRA GPS / cambio citta' dal menu)
            SaveWorldPosition();
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

        /// <summary>Riabilita il CharacterController del player (si era spento
        /// all'ingresso in attesa GPS). Chiamato SOLO dal gate di spawn, quando
        /// il primo chunk e' costruito (o dopo il timeout di sicurezza).</summary>
        private void EnablePlayerController()
        {
            if (City.Game.Instance == null || City.Game.Instance.player == null) return;
            var pl = City.Game.Instance.player;
            var cc = pl.GetComponent<CharacterController>();
            if (cc != null && !cc.enabled)
            {
                cc.enabled = true;
                OsmDiag.Log("[CityChunkedWorld] CharacterController riabilitato (gate spawn)");
            }
        }

        /// <summary>Piazza il giocatore sul terreno alla sua posizione geografica,
        /// tenendo conto dell'elevazione reale (DEM). Prima prova un raycast sui
        /// collider (terreno), altrimenti usa l'altimetria via TileElevation.</summary>
        private void SnapPlayerToTerrain(Transform target)
        {
            if (target == null) return;
            var game = City.Game.Instance;
            var pl = game != null ? game.player : null;
            if (pl == null) return;

            Vector3 p = target.position;
            float yBefore = p.y;
            // prova il collider del terreno sotto il player: il raggio parte
            // 200 m SOPRA il player (per colpire la superficie dall'esterno e
            // non la faccia interna della mesh), e deve IGNORARE il collider
            // del player stesso (CharacterController / figli) E il ponte di
            // sicurezza (piano a y=0 sovrapposto al terreno piatto in costa):
            // altrimenti il player verrebbe lasciato sul ponte a y=1 invece di
            // salire sulla superficie reale (che in Bari arriva fino a y~16).
            RaycastHit hit;
            // mask WalkableMask (esclude gli edifici, layer 8): il raggio deve
            // agganciare la SUPERFICIE stradale/terreno, non il tetto di un
            // edificio che sorge sul punto GPS (il player nascerebbe sul tetto
            // e, essendo la mask di GroundSnapper priva del layer 8, non
            // potrebbe mai esserne risceso).
            if (FirstTerrainHitBelow(RayStartAboveSurface(p), 600f,
                GroundSnapper.WalkableMask, out hit))
            {
                p.y = hit.point.y + 1.0f;
                pl.transform.position = p;
                OsmDiag.Log("[CityChunkedWorld] spawn sul terreno: y=" +
                    p.y.ToString("F1", CultureInfo.InvariantCulture) +
                    " (prima " + yBefore.ToString("F1", CultureInfo.InvariantCulture) +
                    ", delta " + (p.y - yBefore).ToString("F1", CultureInfo.InvariantCulture) +
                    ") (collider " + hit.collider.name + ")");
                EvictPlayerFromBuildingFootprint(pl.transform, hit.point.y);
                return;
            }
            // fallback: altimetria da TileElevation (terra piena)
            if (Manager != null) Manager.DiagnoseTerrain(p);
            float elev = TileElevation.HeightAt(startLat, startLng);
            p.y = elev + 1.0f;
            pl.transform.position = p;
            OsmDiag.Log("[CityChunkedWorld] spawn su altimetria DEM: y=" +
                p.y.ToString("F1", CultureInfo.InvariantCulture) +
                " (prima " + yBefore.ToString("F1", CultureInfo.InvariantCulture) +
                ", delta " + (p.y - yBefore).ToString("F1", CultureInfo.InvariantCulture) +
                ")");
        }

        /// <summary>Se il punto di spawn (XZ) cade dentro l'impronta di un
        /// edificio, il player vi nascerebbe e il CharacterController verrebbe
        /// spinto sul tetto (o ci resterebbe dentro). Lo si sposta all'uscita
        /// XZ piu' vicina sul piano di appoggio gia' calcolato (surfaceY),
        /// come faceva EnsurePlayerOutsideBuildings nel mondo legacy.</summary>
        private void EvictPlayerFromBuildingFootprint(Transform target,
            float surfaceY)
        {
            if (target == null) return;
            var game = City.Game.Instance;
            var pl = game != null ? game.player : null;
            if (pl == null) return;

            Vector3 p = target.position;
            Bounds? inside = null;
            Collider[] cols = Physics.OverlapBox(
                new Vector3(p.x, surfaceY + 4f, p.z),
                new Vector3(8f, 8f, 8f), Quaternion.identity,
                1 << GroundSnapper.BuildingLayer,
                QueryTriggerInteraction.Ignore);
            for (int i = 0; i < cols.Length; i++)
            {
                if (cols[i] == null) continue;
                Bounds b = cols[i].bounds;
                if (p.x >= b.min.x && p.x <= b.max.x &&
                    p.z >= b.min.z && p.z <= b.max.z)
                {
                    inside = b;
                    break;
                }
            }
            if (inside == null) return;

            Bounds bd = inside.Value;
            Vector3 center = bd.center;
            Vector3 ext = bd.extents;
            Vector3 toP = p - center;

            float sx = toP.x >= 0f ? 1f : -1f;
            float sz = toP.z >= 0f ? 1f : -1f;
            if (sx == 0f) sx = 1f;
            if (sz == 0f) sz = 1f;

            Vector3 exitX = new Vector3(
                center.x + sx * (ext.x + 3f), surfaceY + 1f, p.z);
            Vector3 exitZ = new Vector3(
                p.x, surfaceY + 1f, center.z + sz * (ext.z + 3f));

            float dx = Vector3.Distance(p, exitX);
            float dz = Vector3.Distance(p, exitZ);
            Vector3 best = dx < dz ? exitX : exitZ;

            // ri-controlla che l'uscita non ricada in un altro edificio
            int safety = 0;
            while (safety < 6)
            {
                bool still = false;
                Collider[] re = Physics.OverlapBox(
                    new Vector3(best.x, surfaceY + 4f, best.z),
                    new Vector3(8f, 8f, 8f), Quaternion.identity,
                    1 << GroundSnapper.BuildingLayer,
                    QueryTriggerInteraction.Ignore);
                for (int i = 0; i < re.Length; i++)
                {
                    if (re[i] == null) continue;
                    Bounds b2 = re[i].bounds;
                    if (best.x >= b2.min.x && best.x <= b2.max.x &&
                        best.z >= b2.min.z && best.z <= b2.max.z)
                    {
                        still = true;
                        break;
                    }
                }
                if (!still) break;
                best = best + (best - p).normalized * 5f;
                best.y = surfaceY + 1f;
                safety++;
            }

            OsmDiag.Log("[CityChunkedWorld] spawn dentro footprint edificio " +
                "(" + bd.center.x.ToString("F1", CultureInfo.InvariantCulture) +
                ", " + bd.center.z.ToString("F1", CultureInfo.InvariantCulture) +
                ") -> spostato a " +
                best.x.ToString("F1", CultureInfo.InvariantCulture) + "," +
                best.z.ToString("F1", CultureInfo.InvariantCulture));
            pl.transform.position = best;
        }

        /// <summary>Origine consapevole della quota DEM per i raycast di
        /// rilevamento terreno. In montagna il terreno (griglia 'ele' in metri
        /// assoluti s.l.m. sovrapposta al root chunk a y=0) puo' stare MOLTO
        /// sopra il player ancora sul ponte di sicurezza (y~0): un'origine
        /// fissa a y+220 verso il basso non lo colpirebbe mai. Ancorata a
        /// ~150 m sopra la superficie DEM recupera la colonna anche a
        /// 1200 m s.l.m. Con DEM assente o zero (costa, piana) la griglia
        /// ritorna 0 e il comportamento resta esattamente y+220 storico.</summary>
        private static Vector3 RayStartAboveSurface(Vector3 pos)
        {
            Vector3 from = pos + Vector3.up * 220f;
            float dem = TileElevation.HeightAtWorld(pos);
            if (dem + 150f > from.y) from.y = dem + 150f;
            return from;
        }

        /// <summary>Raycast verso il basso che restituisce il primo collider di
        /// TERRA sotto il punto, ignorando il player (e la sua gerarchia) e il
        /// ponte di sicurezza. A differenza di RayTerrainBelow (primo hit
        /// qualsiasi) esclude anche il ponte: quando il terreno e' piatto il
        /// ponte coplanare verrebbe colpito per primo e maschererebbe il vero
        /// terreno sottostante.</summary>
        private bool FirstTerrainHitBelow(Vector3 from, float maxDist,
            int mask, out RaycastHit result)
        {
            result = default(RaycastHit);
            Transform playerRoot = City.Game.Instance != null &&
                City.Game.Instance.player != null
                ? City.Game.Instance.player.transform : null;
            RaycastHit[] hits = Physics.RaycastAll(from, Vector3.down, maxDist, mask);
            for (int i = 0; i < hits.Length; i++)
            {
                if (hits[i].collider == null) continue;
                var t = hits[i].collider.transform;
                if (playerRoot != null && (t == playerRoot || t.IsChildOf(playerRoot)))
                    continue;
                if (_spawnBridge != null &&
                    (t == _spawnBridge.transform || t.IsChildOf(_spawnBridge.transform)))
                    continue;
                result = hits[i];
                return true;
            }
            return false;
        }

        /// <summary>Raycast verso il basso che salta il collider del player e di
        /// tutta la sua gerarchia (CharacterController, collider dei figli) e lo
        /// SpawnBridge: serve a trovare il TERRENO sotto i piedi anche quando il
        /// raggio parte da sopra il player e lo attraverserebbe per primo.</summary>
        private static bool RayTerrainBelow(Vector3 from, float maxDist,
            out RaycastHit result)
        {
            result = default(RaycastHit);
            Transform playerRoot = City.Game.Instance != null &&
                City.Game.Instance.player != null
                ? City.Game.Instance.player.transform : null;
            Transform bridge = Instance != null && Instance._spawnBridge != null
                ? Instance._spawnBridge.transform : null;
            RaycastHit[] hits = Physics.RaycastAll(from, Vector3.down, maxDist);
            for (int i = 0; i < hits.Length; i++)
            {
                if (playerRoot != null && hits[i].collider != null &&
                    (hits[i].collider.transform == playerRoot ||
                     hits[i].collider.transform.IsChildOf(playerRoot)))
                    continue;
                if (bridge != null && hits[i].collider != null &&
                    (hits[i].collider.transform == bridge ||
                     hits[i].collider.transform.IsChildOf(bridge)))
                    continue;
                result = hits[i];
                return true;
            }
            return false;
        }

        /// <summary>Controlla se c'e' un collider di TERRA sotto il target (escludendo
        /// il ponte di sicurezza e il player). True solo se nella colonna sotto il
        /// target esiste ALMENO un collider diverso da SpawnBridge/dal player,
        /// indicando che il terreno fisico e' pronto. NON ci si puo' fermare al
        /// primo hit: quando il terreno e' piatto (y~0, es. costa) il ponte (piano
        /// a y=0 sovrapposto) viene colpito per primo e il terreno sta poco sotto,
        /// quindi va scansionato tutto il RaycastAll.</summary>
        private bool RealTerrainBelow(Transform target)
        {
            if (target == null) return false;
            Transform playerRoot = City.Game.Instance != null &&
                City.Game.Instance.player != null
                ? City.Game.Instance.player.transform : null;
            // In montagna il terreno (quote DEM assolute, es. 1200 m a Cortina)
            // sta SOPRA il player ancora sul ponte a y~0: un raggio da y+220
            // verso il basso non lo colpirebbe mai. Ancoriamo l'origine ~150 m
            // sopra la superficie DEM cosi' la colonna la vede sempre.
            Vector3 from = RayStartAboveSurface(target.position);
            RaycastHit[] hits = Physics.RaycastAll(from, Vector3.down, 500f);
            for (int i = 0; i < hits.Length; i++)
            {
                if (hits[i].collider == null) continue;
                var t = hits[i].collider.transform;
                if (playerRoot != null && (t == playerRoot || t.IsChildOf(playerRoot)))
                    continue;
                if (_spawnBridge != null &&
                    (t == _spawnBridge.transform || t.IsChildOf(_spawnBridge.transform)))
                    continue;
                return true;
            }
            return false;
        }


        /// <summary>
        /// Chunk costruiti in numero sufficiente: lo splash di caricamento
        /// nativo (BridgeActivity) puo' sparire. Viene inviato una sola volta
        /// per ingresso in citta'; in assenza del bridge Android (editor) il
        /// messaggio e' semplicemente un no-op.
        /// </summary>
        private void NotifyCityReady()
        {
            if (_cityReadyNotified) return;
            _cityReadyNotified = true;
            OsmDiag.Log("[CityChunkedWorld] === MIACITTA === CityReady (chunk pronti, splash chiusa)");
            DumpGreenRenderers();
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("CityReady", "{}");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityChunkedWorld] NotifyCityReady: " + e.Message);
            }
        }

        /// <summary>Dump diagnostico (Bug "lastre verdi all'ingresso"): elenca
        /// SOLO i renderer verdi "sospetti" (spessore verticale > soglia, cioe'
        /// potenziali lastre/pareti, o bounds molto grandi) con un tetto al
        /// numero di righe, cosi' il log resta leggibile anche in citta' con
        /// centinaia di parchi. Da rimuovere a bug chiuso.</summary>
        private void DumpGreenRenderers()
        {
            try
            {
                const float wallT = 4f;   // spessore verticale sopra il quale un verde e' "sospetto"
                const int cap = 40;       // max righe dump
                Renderer[] all = FindObjectsOfType<Renderer>();
                int n = 0, dumped = 0;
                foreach (var r in all)
                {
                    if (r == null || !r.gameObject.activeInHierarchy) continue;
                    if (!r.enabled) continue;
                    Material m = null;
                    try { m = r.sharedMaterial; } catch (Exception) { continue; }
                    if (m == null) continue;
                    Color c = Color.white;
                    try
                    {
                        if (m.HasProperty("_BaseColor")) c = m.GetColor("_BaseColor");
                        else if (m.HasProperty("_Color")) c = m.GetColor("_Color");
                    }
                    catch (Exception) { continue; }
                    bool verde = c.g > 0.4f && c.g > c.r + 0.12f && c.g > c.b + 0.12f;
                    if (!verde) continue;
                    Bounds b;
                    try { b = r.bounds; } catch (Exception) { continue; }
                    if (b.size.magnitude < 5f) continue;
                    n++;
                    // lastre verticali o parchoni drappati sul DEM: i candidati bug.
                    if (b.size.y <= wallT && b.size.magnitude <= 60f) continue;
                    if (dumped >= cap) continue;
                    dumped++;
                    string parent = r.transform.parent != null
                        ? r.transform.parent.name : "-";
                    OsmDiag.Log(string.Format(
                        "[DumpGreen] #{0}/{1} name='{2}' parent='{3}' y={4:F1} size={5:F1}x{6:F1}x{7:F1} col=({8:F2},{9:F2},{10:F2}) mat={11}",
                        dumped, n, r.name, parent, b.center.y,
                        b.size.x, b.size.y, b.size.z,
                        c.r, c.g, c.b, m.name));
                }
                OsmDiag.Log("[DumpGreen] renderer verdi grandi: " + n +
                    " (sospetti spessore>" + wallT.ToString("F0",
                        System.Globalization.CultureInfo.InvariantCulture) +
                    "m dump: " + dumped + ")" + (n > 0 && dumped == 0 ? " - nessun sospetto" : ""));
            }
            catch (Exception e)
            {
                OsmDiag.Log("[DumpGreen] errore dump: " + e.Message);
            }
        }

        /// <summary>
        /// Sonda del terreno ogni 2 s: se il player risulta sotto il mondo
        /// (caduto attraverso un collider mancante o un buco tra chunk) lo
        /// riporta sulla superficie invece di lasciarlo precipitare. Logga
        /// anche cosa c'e' sotto i piedi: serve per diagnosticare cadute.
        /// </summary>
        private void GroundProbeAndRescue()
        {
            // Regni afterlife (3.4): l'arena sta a quota ~0 mentre la citta'
            // puo' stare a 100+ m, e le cadute nella lava sono previste dal
            // gioco. La sonda terreno NON deve riportare il player in citta'
            // durante i mini-giochi dei regni.
            var rsm = City.Afterlife.RealmSceneManager.Instance;
            if (rsm != null && rsm.ActiveRealm != null) return;

            if (Time.unscaledTime < _nextGroundProbe) return;
            _nextGroundProbe = Time.unscaledTime + 2f;
            if (Manager == null || Manager.target == null) return;

            Vector3 p = Manager.target.position;

            // Quota DEM sotto il player (0 se nessuna tile DEM copre il punto:
            // costa, o tile senza griglia DEM -> terreno proxy 0..30m). Il
            // "mondo" non sta a una quota fissa: in montagna e' a quota assoluta
            // s.l.m. (es. 1200m). "Caduto sotto il mondo" deve essere RELATIVO
            // alla superficie DEM, altrimenti si precipita per 1200m nel vuoto
            // prima del salvataggio.
            float demBelow = TileElevation.HeightAtWorld(p);
            float fallenThreshold = demBelow > 0f ? demBelow - 20f : -15f;

            // Sprofondato nel terreno fisico senza collider vicino ai piedi
            // (buchi/giunture della mesh DEM, chunk non ancora collidibile):
            // i piedi (pivot - mezzo'altezza capsula) sono finiti netti sotto
            // la quota DEM ma non c'e' un collider reale a sorreggerli, quindi
            // il player sta attraversando la superficie. In questo caso si
            // riporta subito sulla superficie (niente attesa da -20 m).
            bool fellThrough = demBelow > 0f
                && p.y - 1f < demBelow - 1.2f
                && City.Game.Instance != null && !City.Game.Instance.IsDriving;

            // caduto sotto il mondo, oppure attraversamento della superficie?
            if (p.y < fallenThreshold || fellThrough)
            {
                Vector3 from = RayStartAboveSurface(p);
                Vector3 dest;
                string how;
                // Un collare trovato molto lontano dalla quota DEM (es. le
                // piattaforme afterlife a y~0 quando la citta' e' a quota
                // 100+ m) non e' terreno: si usa direttamente la quota DEM.
                bool foundTerrain = RayTerrainBelow(from, 300f, out RaycastHit hit)
                    && Mathf.Abs(hit.point.y - demBelow) <= 30f;
                if (foundTerrain)
                {
                    dest = hit.point + Vector3.up * 1.0f;
                    how = "superficie=" + hit.collider.name;
                }
                else
                {
                    // nessun collider sopra: niente di fisico SOTTO ne' SOPRA.
                    // Prima la superficie DEM reale: in montagna il terreno sta
                    // a quota assoluta (es. 1200 m) e l'origine (ponte a y=0)
                    // sarebbe 1200 m piu' in BASSO del paese -> il player
                    // rimbalzerebbe per sempre. Se il DEM copre il punto
                    // piazziamo il player sopra la superficie reale (stessa
                    // griglia da cui e' generata la mesh), altrimenti fallback
                    // alla rete certa: il ponte 400x400 a y=0 all'origine.
                    if (demBelow > 0f)
                    {
                        dest = new Vector3(p.x, demBelow + 1.0f, p.z);
                        how = "DEM (quota " + demBelow.ToString("F0",
                            CultureInfo.InvariantCulture) + "m)";
                    }
                    else
                    {
                        dest = new Vector3(0f, 1.0f, 0f);
                        EnsureSpawnBridge();
                        how = "ponte-origine";
                    }
                    if (Time.unscaledTime >= _nextTerrainDiag)
                    {
                        _nextTerrainDiag = Time.unscaledTime + 10f;
                        if (Manager != null) Manager.DiagnoseTerrain(p);
                    }
                }
                var gc = City.Game.Instance;
                Quaternion rot = Manager.target.rotation;
                if (gc != null && gc.player != null)
                    gc.TeleportPlayer(dest, rot);
                else
                    Manager.target.position = dest;
                OsmDiag.Log("[CityChunkedWorld] SALVATAGGIO: player sotto il mondo " +
                    "(y=" + p.y.ToString("F1", CultureInfo.InvariantCulture) +
                    ") -> (" + dest.x.ToString("F1", CultureInfo.InvariantCulture) +
                    "," + dest.z.ToString("F1", CultureInfo.InvariantCulture) +
                    ") via " + how);
                return;
            }

            // diagnostica periodica: cosa c'e' sotto i piedi del player.
            // Salta durante la guida: il veicolo ha la sua fisica e non
            // serve il terreno sotto il player.  Anche qui si esclude lo
            // SpawnBridge per non generare falsi allarmi quando il player
            // e' sul ponte di sicurezza (y~0).
            var game = City.Game.Instance;
            if (game != null && game.IsDriving) return;
            if (!RayTerrainBelow(p + Vector3.up * 5f, 50f, out RaycastHit ground))
            {
                OsmDiag.Log("[CityChunkedWorld] ATTENZIONE: nessun terreno sotto " +
                    "il player (y=" + p.y.ToString("F1", CultureInfo.InvariantCulture) + ")");
            }
        }
    }
}

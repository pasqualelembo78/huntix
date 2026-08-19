using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;
using TMPro;
using City.World;
using City.Player;
using Huntix.Bridge;

namespace City.OSM
{
    /// <summary>
    /// Costruisce la città di MiAcitma con dati OSM reali.
    ///
    /// Sostituisce il quartiere finto (seed) della scena City con case, strade,
    /// negozi e alberi posizionati alle coordinate reali di OpenStreetMap.
    /// La geometria è generata a runtime (primitive + palette condivisa) a
    /// partire dall'envelope JSON ricevuto da Android (OsmCityJsonFactory).
    ///
    /// Streaming (fase 3): la mappa si ri-centra attorno al giocatore quando
    /// questo si allontana di oltre STREAM_THRESHOLD_M dal centro; il nuovo
    /// centro è la posizione GPS corrente. La cache disco di OsmClient rende
    /// il re-centraggio istantaneo per le zone già scaricate.
    /// </summary>
    public class CityOSMWorld : MonoBehaviour
    {
        public static CityOSMWorld Instance { get; private set; }

        /// <summary>True quando la scena City sta per essere chiusa (esci → Home):
        /// ferma ogni accesso JNI al bridge Android, che in gara con il teardown
        /// dell'engine Unity può causare un crash nativo del processo.</summary>
        public static bool Exiting { get; private set; }

        private const string SeedCityRoot = "Citta";
        private const string OsmRootName = "CittaOSM";

        private const int StreamRadiusM = 700;
        private const float StreamThresholdM = 380f;
        private const float PollIntervalS = 3f;
        private const float PendingWatchdogS = 12f;    // nessuna risposta da Android -> rispedisci
        private const float PendingAckTimeoutS = 60f;  // ACK ricevuto ma dati non arrivati -> rispedisci
        private const double DefaultLat = 41.9028;
        private const double DefaultLng = 12.4964;

        public double CenterLat { get; private set; } = DefaultLat;
        public double CenterLng { get; private set; } = DefaultLng;
        public int RadiusMeters { get; private set; } = StreamRadiusM;

        private Transform _root;
        private bool _pending;
        private bool _pendingAck;      // Android ha ricevuto la richiesta (fetch in corso)
        private double _requestLat;    // posizione della richiesta in volo (per il watchdog)
        private double _requestLng;
        private bool _building;
        private bool _firstBuildDone;
        private Huntix.Core.CityKitAssetRegistry _registry;
        private string _usedDefaultLocation; // non-null => GPS non disponibile (mappa fissa Roma)
        private float _pollTimer;
        private float _pendingTimer;
        private bool _paused;
        private readonly List<Bounds> _buildingBounds = new List<Bounds>();

        private readonly Dictionary<Color, Material> _materials = new Dictionary<Color, Material>();

        // ── bootstrap ────────────────────────────────────────────────

        public static void EnsureInstance()
        {
            if (Instance != null) return;
            var go = new GameObject("CityOSM");
            // CRITICO: EnsureInstance è chiamato da LoadSceneForMode mentre la scena
            // corrente è ancora quella precedente (es. Menu) subito dopo
            // SceneManager.LoadScene("City"). Senza DontDestroyOnLoad il GameObject
            // verrebbe distrutto allo sblocco della scena e i dati OSM che arrivano
            // dopo (la risposta è asincrona) verrebbero persi, lasciando la città
            // seed finta sullo schermo.
            UnityEngine.Object.DontDestroyOnLoad(go);
            Instance = go.AddComponent<CityOSMWorld>();
            Instance.Init();
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

        private void Init()
        {
            // CRITICO: in questo progetto ProjectSettings/DynamicsManager.asset ha
            // m_AutoSyncTransforms: 0. La città OSM è generata a runtime (colliders
            // creati via trasform.position = ...) e senza la sincronizzazione
            // automatica i collider non vengono registrati nel solver di PhysX:
            // il player (CharacterController) passa attraverso strade e terreno e
            // cade nel vuoto. Il seed serializzato nella scena funzionava perché
            // i suoi collider erano registrati al load. Forzare l'auto-sync è la
            // soluzione robusta (è anche il default di Unity).
            Physics.autoSyncTransforms = true;

            // Il tracking GPS parte PRIMA del bootstrap della mappa: lato Android
            // il seed con l'ultima posizione nota è sincrono, quindi LoadInitial
            // può leggere subito la posizione reale invece del default Roma.
            // StartLocationTracking è già protetto da try/catch: se il permesso
            // location è negato l'eccezione viene inghiottita e LoadInitial parte
            // comunque (fallback Roma).
            try
            {
                UnityBridge.StartLocationTracking();
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityOSMWorld] startLocationTracking: " + e.Message);
            }
            StartCoroutine(LoadInitial());
        }

        /// <summary>Da chiamare PRIMA di chiudere l'Activity Unity (bottone Esci):
        /// ferma subito il polling JNI e i fetch OSM in volo, così il teardown
        /// dell'engine non trova thread che chiamano il bridge Android.</summary>
        public void PrepareExit()
        {
            Exiting = true;
            Debug.Log("[CityOSMWorld] Uscita: polling e fetch OSM fermati");
        }

        private void OnApplicationPause(bool paused)
        {
            // Con l'Activity in pausa (es. tornare alla Home) il player non deve
            // toccare il bridge JNI: il teardown dell'engine Unity è in corso. Al
            // resume il flag si abbassa e il polling riprende normalmente.
            _paused = paused;
        }

        private void OnDestroy()
        {
            Exiting = true;
        }

        private IEnumerator LoadInitial()
        {
            // Niente mappa finta all'avvio: la seed city viene nascosta subito e il
            // player congelato (CharacterController disattivato = niente gravita),
            // cosi tra il bootstrap e la ricezione dei dati OSM non c'e' niente da
            // vedere ne' una caduta nel vuoto. Il player verra' posato sul terreno
            // OSM da PlacePlayerOnGround quando il build parte.
            yield return null;
            HideSeedCityAndFreezePlayer();
            var loc = ReadBridgeLocation();
            if (loc == null || IsZero(loc))
            {
                // Primo fix GPS: anche con il seed Android (last-known) un
                // dispositivo mai localizzato può non avere nulla. Si attende
                // fino a ~3s prima di ripiegare sul default Roma, così la città
                // iniziale è quasi sempre quella reale (evita il "salto" Roma→GPS).
                for (int i = 0; i < 6 && !Exiting && (loc == null || IsZero(loc)); i++)
                {
                    yield return new WaitForSeconds(0.5f);
                    loc = ReadBridgeLocation();
                }
            }

            double lat;
            double lng;
            if (loc != null && !IsZero(loc))
            {
                lat = loc.lat;
                lng = loc.lng;
            }
            else
            {
                lat = DefaultLat;
                lng = DefaultLng;
                _usedDefaultLocation = "default";
            }
            Debug.Log($"[CityOSMWorld] Centro iniziale ({lat},{lng}) raggio {StreamRadiusM}m");
            UnityBridge.LogToAndroid("CityOSMWorld", $"Bootstrap: centro iniziale ({lat},{lng}) raggio {StreamRadiusM}m" + (_usedDefaultLocation != null ? " [DEFAULT, GPS non disponibile]" : ""));
            if (Exiting) yield break;
            RequestArea(lat, lng);
            yield break;
        }

        // ── richieste / ricezione ────────────────────────────────────

        private void RequestArea(double lat, double lng)
        {
            if (Exiting || _pending || _building) return;
            _pending = true;
            _pendingAck = false;
            _requestLat = lat;
            _requestLng = lng;
            if (Huntix.Core.GameManager.Instance != null)
            {
                Huntix.Core.GameManager.Instance.RequestOsmCity(lat, lng, StreamRadiusM);
            }
            else
            {
                _pending = false;
            }
        }

        // Android conferma di aver ricevuto la richiesta e che il fetch è in corso
        // (Overpass/cache). Così il watchdog NON rispedisce la richiesta durante un
        // fetch lento, ma attende molto più a lungo prima di considerarlo perso.
        public void OnOsmCityFetchStarted(string data)
        {
            if (!_pending) return;
            _pendingAck = true;
            if (!string.IsNullOrEmpty(data))
            {
                var parts = data.Split('|');
                if (parts.Length == 2 &&
                    double.TryParse(parts[0], NumberStyles.Float, CultureInfo.InvariantCulture, out double ackLat) &&
                    double.TryParse(parts[1], NumberStyles.Float, CultureInfo.InvariantCulture, out double ackLng))
                {
                    // ACK di una richiesta diversa da quella in volo: ignora.
                    if (Math.Abs(ackLat - _requestLat) > 1e-6 || Math.Abs(ackLng - _requestLng) > 1e-6)
                    {
                        _pendingAck = false;
                        return;
                    }
                }
            }
            UnityBridge.LogToAndroid("CityOSMWorld", "Fetch OSM avviato su Android (attendo dati...)");
        }

        public void OnOsmCityReceived(string json)
        {
            _pending = false;
            if (string.IsNullOrEmpty(json)) return;
            UnityBridge.LogToAndroid("CityOSMWorld", "Dati OSM ricevuti (" + json.Length + " chars)");
            Debug.Log($"[CityOSMWorld] Dati OSM ricevuti ({json.Length} chars)");

            OsmCityEnvelope env;
            try
            {
                env = JsonUtility.FromJson<OsmCityEnvelope>(json);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityOSMWorld] Envelope non parsato: " + e.Message);
                return;
            }
            if (env == null || env.roads == null) return;
            if (_building) return; // un build alla volta: il prossimo stream riparte da qui

            _building = true;
            StartCoroutine(RunBuild(env));
        }

        public void OnOsmCityFailed(string message)
        {
            _pending = false;
            Debug.LogWarning("[CityOSMWorld] Mappa OSM non disponibile: " + message);
            UnityBridge.LogToAndroid("CityOSMWorld", "Mappa OSM non disponibile: " + message);
            ShowToast("Caricamento mappa OSM non riuscito");
            if (!_firstBuildDone) RestoreSeedFallback();
        }

        private IEnumerator RunBuild(OsmCityEnvelope env)
        {
            bool ok = false;
            try
            {
                // Nota: niente catch qui (C# non permette yield dentro try-catch),
                // l'eccezione esce e Unity la logga; il finally sblocca _building
                // così uno stream successivo può riprovare.
                yield return RebuildWorld(env);
                ok = true;
            }
            finally
            {
                _building = false;
                if (!ok)
                    Debug.LogError("[CityOSMWorld] Build città OSM fallita (vedi errore precedente).");
            }
        }

        // ── build mondo ──────────────────────────────────────────────

        private IEnumerator RebuildWorld(OsmCityEnvelope env)
        {
            CoordinateConverter.Init(env.centerLat, env.centerLng);
            CenterLat = env.centerLat;
            CenterLng = env.centerLng;
            RadiusMeters = Mathf.Max(env.radiusMeters, StreamRadiusM);
            _buildingBounds.Clear();

            ApplyKenneySkybox();

            var newRoot = new GameObject(OsmRootName);
            BuildGround(newRoot.transform, env);
            yield return null;

            PlacePlayerOnGround();
            yield return BuildRoadsCoroutine(newRoot.transform, env);
            yield return BuildBuildingsCoroutine(newRoot.transform, env);
            yield return BuildVegetationCoroutine(newRoot.transform, env);

            EnsurePlayerOutsideBuildings();

            var game = City.Game.Instance;
            if (game != null && game.player != null)
            {
                var cc = game.player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = true;
                game.player.Stop();
            }

            DestroySeedCity();
            DestroyExistingOsmRoot(newRoot.transform);
            _root = newRoot.transform;
            if (!_firstBuildDone)
            {
                _firstBuildDone = true;
                ShowToast(_usedDefaultLocation != null
                    ? "Mappa OSM: Roma (GPS non disponibile) · dati © OpenStreetMap (ODbL)"
                    : "Città reale OSM · dati © OpenStreetMap (ODbL)");
            }

            Debug.Log("[CityOSMWorld] Build completata: roads=" + (env.roads != null ? env.roads.Length : 0) +
                      " buildings=" + (env.buildings != null ? env.buildings.Length : 0) +
                      " trees=" + (env.trees != null ? env.trees.Length : 0) +
                      " parks=" + (env.parks != null ? env.parks.Length : 0));
            UnityBridge.LogToAndroid("CityOSMWorld", "Build completata: roads=" + (env.roads != null ? env.roads.Length : 0) +
                      " buildings=" + (env.buildings != null ? env.buildings.Length : 0) +
                      " trees=" + (env.trees != null ? env.trees.Length : 0) +
                      " parks=" + (env.parks != null ? env.parks.Length : 0));
        }

        // Posiziona il player IN PIEDI sul terreno OSM: il CharacterController
        // non deve mai essere spostato "dentro" il suolo (i controller incastrati
        // in un collider possono finire spinti sotto / far perdere il grounding,
        // e il player inizia a cadere nel vuoto). Qui il pivot viene messo esattamente
        // a piedi-a-suolo (altezza controller / 2) e un piccolo margine di sicurezza
        // sopra evita qualunque penetrazione.
        private void PlacePlayerOnGround()
        {
            var game = City.Game.Instance;
            if (game == null || game.player == null)
            {
                Debug.LogWarning("[CityOSMWorld] Game/player non disponibili, teleport saltato");
                UnityBridge.LogToAndroid("CityOSMWorld", "Teleport SALTATO: Game o player null");
                RestoreSeedFallback();
                return;
            }

            Physics.SyncTransforms();
            Vector3 pos = new Vector3(0f, -0.06f + 0.05f, 0f);
            var cc = game.player.GetComponent<CharacterController>();
            if (cc != null)
            {
                cc.includeLayers = (1 << 0) | (1 << 8);
                float feetFromPivot = cc.height * 0.5f - cc.center.y;
                pos.y = -0.06f + feetFromPivot + 0.05f;
                cc.enabled = false;
            }

            game.player.transform.position = pos;
            game.player.transform.rotation = Quaternion.LookRotation(Vector3.forward);
            game.player.Stop();
            if (game.rig != null) game.rig.SetYaw(Quaternion.LookRotation(Vector3.forward));

            Debug.Log($"[CityOSMWorld] Player posizionato a terra a {pos} (CC disabilitato durante build)");
            UnityBridge.LogToAndroid("CityOSMWorld", "Player posizionato a " + pos + " (CC disabilitato durante build)");
        }

        // Dopo aver piazzato tutti gli edifici, verifica che il player non sia
        // finito dentro un edificio (GPS centrato su un building footprint).
        // Se lo e', sposta il player fuori dal bounding box piu' vicino.
        private void EnsurePlayerOutsideBuildings()
        {
            if (_buildingBounds.Count == 0) return;
            var game = City.Game.Instance;
            if (game == null || game.player == null) return;

            Vector3 p = game.player.transform.position;
            bool inside = false;
            for (int i = 0; i < _buildingBounds.Count; i++)
            {
                if (IsInsideBuildingXZ(p, _buildingBounds[i]))
                {
                    inside = true;
                    break;
                }
            }
            if (!inside) return;

            Vector3 best = p;
            float bestDist = float.MaxValue;
            for (int i = 0; i < _buildingBounds.Count; i++)
            {
                Bounds b = _buildingBounds[i];
                Vector3 center = b.center;
                Vector3 ext = b.extents;

                Vector3 toP = p - center;

                float sx = Mathf.Sign(toP.x);
                float sz = Mathf.Sign(toP.z);
                if (sx == 0f) sx = 1f;
                if (sz == 0f) sz = 1f;

                Vector3 exitX = new Vector3(sx * (ext.x + 3f), 0f, toP.z);
                Vector3 exitZ = new Vector3(toP.x, 0f, sz * (ext.z + 3f));

                Vector3 ex = center + exitX;
                Vector3 ez = center + exitZ;

                float dx = Vector3.Distance(p, ex);
                float dz = Vector3.Distance(p, ez);

                Vector3 candidate = dx < dz ? ex : ez;
                float dist = dx < dz ? dx : dz;

                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = candidate;
                }
            }
            best.y = p.y;

            int safety = 0;
            while (safety < 10)
            {
                bool stillInside = false;
                for (int i = 0; i < _buildingBounds.Count; i++)
                {
                    if (IsInsideBuildingXZ(best, _buildingBounds[i]))
                    {
                        stillInside = true;
                        break;
                    }
                }
                if (!stillInside) break;
                best = best + (best - p).normalized * 5f;
                best.y = p.y;
                safety++;
            }

            var cc = game.player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            game.player.transform.position = best;
            if (cc != null) cc.enabled = true;
            game.player.Stop();

            UnityBridge.LogToAndroid("CityOSM", $"Player era dentro un edificio → spostato a ({best.x:F1},{best.y:F1},{best.z:F1}) safety={safety}");
        }

        private static bool IsInsideBuildingXZ(Vector3 p, Bounds b)
        {
            return Mathf.Abs(p.x - b.center.x) < b.extents.x
                && Mathf.Abs(p.z - b.center.z) < b.extents.z;
        }

        // Il mondo OSM è piatto (strade top y=0, terreno y=-0.06) e il teleport di
        // PlacePlayerOnGround ha già poggiato i piedi del player a -0.01: l'altezza
        // deterministica è corretta su qualunque superficie. Qui si attende solo che
        // il teleport asincrono (fader) sia davvero completato, poi si verifica che
        // la y non sia anomala (correzione deterministica se lo è) e si conferma il
        // suolo con OverlapSphere (affidabile nel build IL2CPP, a differenza di un
        // raycast lanciato dall'interno del CharacterController che non trovava mai
        // il terreno pur con i collider presenti).
        private IEnumerator SnapToGround(PlayerController player, CharacterController cc, Vector3 targetPos)
        {
            // Il fader viene disattivato in Game.DoTeleport DOPO SetPlayerPosition:
            // quando non è più attivo il player è già stato spostato. Non si richiede
            // che il player sia "arrivato" sul target: se l'utente cammina durante il
            // build la verifica del suolo non deve attendere invano 6 secondi.
            float waited = 0f;
            const float maxWait = 6f;
            while (waited < maxWait && player != null)
            {
                bool fading = false;
                if (City.Game.Instance != null && City.Game.Instance.fader != null)
                    fading = City.Game.Instance.fader.gameObject.activeSelf;
                if (!fading) break;
                waited += 0.25f;
                yield return new WaitForSeconds(0.25f);
            }
            if (player == null) yield break;

            Physics.SyncTransforms();
            Vector3 p = player.transform.position;
            if (cc != null)
            {
                float feetFromPivot = cc.height * 0.5f - cc.center.y;
                float feetY = p.y - feetFromPivot;
                if (feetY < -0.5f || feetY > 1f)
                {
                    p.y = -0.06f + feetFromPivot + 0.05f;
                    cc.enabled = false;
                    player.transform.position = p;
                    cc.enabled = true;
                }
            }
            Physics.SyncTransforms();
            int groundColliders = OverlapCount(player.transform.position, 1.5f);
            Debug.Log($"[CityOSMWorld] Player ancorato al suolo a y={player.transform.position.y} (colliders={groundColliders})");
            UnityBridge.LogToAndroid("CityOSMWorld", "Player ancorato al suolo a y=" + player.transform.position.y + " (colliders=" + groundColliders + ")");
        }

        private static int OverlapCount(Vector3 pos, float radius)
        {
            int count = 0;
            var hits = Physics.OverlapSphere(pos, radius);
            for (int i = 0; i < hits.Length; i++)
            {
                if (hits[i] != null && !hits[i].isTrigger) count++;
            }
            return count;
        }

        private void DestroySeedCity()
        {
            var seed = GameObject.Find(SeedCityRoot);
            if (seed != null) Destroy(seed);
        }

        private void HideSeedCityAndFreezePlayer()
        {
            var seed = GameObject.Find(SeedCityRoot);
            if (seed != null) seed.SetActive(false);
            if (City.Game.Instance != null && City.Game.Instance.player != null)
            {
                var cc = City.Game.Instance.player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                City.Game.Instance.player.Stop();
            }
            UnityBridge.LogToAndroid("CityOSMWorld", "Mappa seed nascosta, player congelato (in attesa OSM)");
        }

        private void RestoreSeedFallback()
        {
            var seed = GameObject.Find(SeedCityRoot);
            if (seed != null) seed.SetActive(true);
            if (City.Game.Instance != null && City.Game.Instance.player != null)
            {
                var cc = City.Game.Instance.player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = true;
            }
            UnityBridge.LogToAndroid("CityOSMWorld", "OSM non disponibile: mappa seed ripristinata (fallback)");
        }

        private void DestroyExistingOsmRoot(Transform newRoot)
        {
            var existing = GameObject.Find(OsmRootName);
            if (existing != null && existing.transform != newRoot) Destroy(existing);
        }

        // ── terreno ──────────────────────────────────────────────────

        private void BuildGround(Transform parent, OsmCityEnvelope env)
        {
            float half = RadiusMeters * 1.25f;
            var ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            ground.name = "Terreno";
            ground.transform.SetParent(parent, false);
            ground.transform.position = new Vector3(0f, -0.06f, 0f);
            ground.transform.localScale = new Vector3(half * 2f / 10f, 1f, half * 2f / 10f);
            ground.GetComponent<Renderer>().sharedMaterial = GetMaterial(GrassColor);
            // CreatePrimitive(Plane) aggiunge già un MeshCollider col mesh del piano:
            // un secondo MeshCollider (com'era prima) è un duplicato inutile e in
            // passato ha contribuito a collider non affidabili. Lo si lascia singolo.
            var c = ground.GetComponent<Collider>();
            if (c != null) c.enabled = true;
        }

        // ── strade ───────────────────────────────────────────────────

        private static readonly HashSet<string> MinorHighway = new HashSet<string>
        {
            "footway", "path", "cycleway", "corridor", "proposed", "construction", "raceway", "steps"
        };

        private static float RoadWidth(string highway)
        {
            switch (highway)
            {
                case "motorway": return 12f;
                case "primary": return 10f;
                case "secondary": return 8f;
                case "tertiary": return 7f;
                case "residential": return 6f;
                case "service": return 4f;
                case "footway": return 2f;
                case "pedestrian": return 3f;
                case "unclassified": return 6f;
                default: return 5f;
            }
        }

        private GameObject _roadStraight, _roadBend, _roadCrossroad, _roadIntersection, _roadEnd;
        private float _straightLen, _straightWid;

        private static int SnapKey(Vector3 p)
        {
            int x = Mathf.RoundToInt(p.x * 2f);
            int z = Mathf.RoundToInt(p.z * 2f);
            return x * 100007 + z;
        }

        private const float KENNEY_ROAD_SCALE = 8f;
        private const float KENNEY_TILE_LEN = 8f;

        private void LoadRoadPrefabs()
        {
            if (_roadStraight != null) return;
            _roadStraight = Resources.Load<GameObject>("Roads/road-straight");
            _roadBend = Resources.Load<GameObject>("Roads/road-bend");
            _roadCrossroad = Resources.Load<GameObject>("Roads/road-crossroad");
            _roadIntersection = Resources.Load<GameObject>("Roads/road-intersection");
            _roadEnd = Resources.Load<GameObject>("Roads/road-end");

            if (_roadStraight != null)
            {
                var probe = Instantiate(_roadStraight);
                probe.transform.localScale = Vector3.one * KENNEY_ROAD_SCALE;
                Bounds pb = new Bounds();
                bool any = false;
                foreach (Renderer r in probe.GetComponentsInChildren<Renderer>(true))
                {
                    if (r == null) continue;
                    if (!any) { pb = r.bounds; any = true; }
                    else pb.Encapsulate(r.bounds);
                }
                _straightLen = pb.size.z;
                _straightWid = pb.size.x;
                UnityBridge.LogToAndroid("CityOSM", "Road probe (8x): size=(" + pb.size.x + "," + pb.size.y + "," + pb.size.z + ") straightLen=" + _straightLen + " straightWid=" + _straightWid);
                DestroyImmediate(probe);
            }

            int loaded = (_roadStraight ? 1 : 0) + (_roadBend ? 1 : 0) + (_roadCrossroad ? 1 : 0)
                         + (_roadIntersection ? 1 : 0) + (_roadEnd ? 1 : 0);
            UnityBridge.LogToAndroid("CityOSM", "Kenney road tiles loaded: " + loaded + "/5 tileWid=" + _straightWid + " tileLen=" + _straightLen);
        }

        private GameObject InstantiateRoadTile(Transform parent, GameObject prefab, Vector3 pos, float angle, float width)
        {
            if (prefab == null) return null;
            var go = Instantiate(prefab, parent);
            go.name = prefab.name;
            go.transform.localPosition = pos + Vector3.up * -0.03f;
            go.transform.localRotation = Quaternion.Euler(0f, -angle * Mathf.Rad2Deg, 0f);
            if (_straightWid > 0f)
            {
                float s = (width / _straightWid) * KENNEY_ROAD_SCALE;
                go.transform.localScale = new Vector3(s, 1f, s);
            }
            foreach (MeshFilter mf in go.GetComponentsInChildren<MeshFilter>())
            {
                if (mf == null || mf.sharedMesh == null) continue;
                if (mf.GetComponent<Collider>() != null) continue;
                MeshCollider mc = mf.gameObject.AddComponent<MeshCollider>();
                mc.sharedMesh = mf.sharedMesh;
            }
            return go;
        }

        private IEnumerator BuildRoadsCoroutine(Transform parent, OsmCityEnvelope env)
        {
            if (env.roads == null) yield break;

            LoadRoadPrefabs();

            var sorted = new List<OsmRoad>(env.roads);
            sorted.Sort((a, b) =>
            {
                bool ma = MinorHighway.Contains(a.highway ?? "");
                bool mb = MinorHighway.Contains(b.highway ?? "");
                if (ma != mb) return ma ? 1 : -1;
                return RoadWidth(b.highway).CompareTo(RoadWidth(a.highway));
            });

            var junctionCount = new Dictionary<int, int>();
            if (env.roads != null)
            {
                foreach (var r in env.roads)
                {
                    if (r.points == null || r.points.Length < 2) continue;
                    foreach (var p in r.points)
                    {
                        int k = SnapKey(Local(p));
                        junctionCount[k] = junctionCount.GetValueOrDefault(k) + 1;
                    }
                }
            }

            var placedJunctions = new HashSet<int>();
            int steps = 0;
            foreach (var r in sorted)
            {
                if (r.points == null || r.points.Length < 2) continue;
                float width = RoadWidth(r.highway);
                bool centerLine = r.highway == "primary" || r.highway == "secondary" || r.highway == "tertiary";

                for (int i = 0; i < r.points.Length - 1; i++)
                {
                    Vector3 a = Local(r.points[i]);
                    Vector3 b = Local(r.points[i + 1]);
                    float length = (b - a).magnitude;
                    if (length < 0.5f) continue;

                    Vector3 mid = (a + b) * 0.5f;
                    float angle = Mathf.Atan2(b.z - a.z, b.x - a.x);

                    if (_roadStraight != null && _straightLen > 0f && _straightWid > 0f)
                    {
                        float sx = (width / _straightWid) * KENNEY_ROAD_SCALE;
                        int tileCount = Mathf.Max(1, Mathf.RoundToInt(length / KENNEY_TILE_LEN));
                        float tileStep = length / tileCount;
                        float sz = (tileStep / _straightLen) * KENNEY_ROAD_SCALE;
                        Vector3 dir = (b - a).normalized;

                        for (int t = 0; t < tileCount; t++)
                        {
                            Vector3 tileCenter = a + dir * ((t + 0.5f) * tileStep);
                            var go = Instantiate(_roadStraight, parent);
                            go.name = "Strada";
                            go.transform.localPosition = tileCenter + Vector3.up * -0.03f;
                            go.transform.localRotation = Quaternion.Euler(0f, -angle * Mathf.Rad2Deg, 0f);
                            go.transform.localScale = new Vector3(sx, 1f, sz);

                            foreach (MeshFilter mf in go.GetComponentsInChildren<MeshFilter>())
                            {
                                if (mf == null || mf.sharedMesh == null) continue;
                                if (mf.GetComponent<Collider>() != null) continue;
                                MeshCollider mc = mf.gameObject.AddComponent<MeshCollider>();
                                mc.sharedMesh = mf.sharedMesh;
                            }
                        }
                    }
                    else
                    {
                        CreateBox(parent, "Strada", mid + Vector3.up * -0.03f,
                            new Vector3(length, 0.06f, width), GetMaterial(RoadColor), angle, true);
                    }

                    if (centerLine && length > 5f)
                    {
                        CreateBox(parent, "Corsia", mid + Vector3.up * -0.005f,
                            new Vector3(length, 0.02f, 0.12f), GetMaterial(CenterLineColor), angle, false);
                    }

                    int vKey = SnapKey(b);
                    if (!placedJunctions.Contains(vKey) && junctionCount.GetValueOrDefault(vKey) >= 2)
                    {
                        int cnt = junctionCount[vKey];
                        GameObject jPrefab = cnt >= 3 ? _roadCrossroad : _roadIntersection;
                        InstantiateRoadTile(parent, jPrefab, b, angle, width);
                        UnityBridge.LogToAndroid("CityOSM", $"Road tile: {(cnt >= 3 ? "crossroad" : "intersection")} at SnapKey={vKey} roads={cnt}");
                        placedJunctions.Add(vKey);
                    }

                    if (i > 0 && i < r.points.Length - 1)
                    {
                        Vector3 prev = Local(r.points[i - 1]);
                        float inAngle = Mathf.Atan2(a.z - prev.z, a.x - prev.x);
                        float angleDiff = Mathf.Abs(Mathf.DeltaAngle(angle * Mathf.Rad2Deg, inAngle * Mathf.Rad2Deg));
                        if (Mathf.Abs(angleDiff - 90f) < 25f)
                        {
                            int bvKey = SnapKey(a);
                            if (!placedJunctions.Contains(bvKey))
                            {
                                InstantiateRoadTile(parent, _roadBend, a, inAngle, width);
                                UnityBridge.LogToAndroid("CityOSM", $"Road tile: bend at ({a.x:F1},{a.z:F1}) angleDiff={angleDiff:F0}");
                            }
                        }
                    }

                    if (++steps % 60 == 0) yield return null;
                }

                int startKey = SnapKey(Local(r.points[0]));
                int endKey = SnapKey(Local(r.points[r.points.Length - 1]));
                if (junctionCount.GetValueOrDefault(startKey) <= 1)
                {
                    Vector3 sp = Local(r.points[0]);
                    Vector3 sp2 = Local(r.points[1]);
                    float sAngle = Mathf.Atan2(sp2.z - sp.z, sp2.x - sp.x) + Mathf.PI;
                    InstantiateRoadTile(parent, _roadEnd, sp, sAngle, width);
                    UnityBridge.LogToAndroid("CityOSM", "Road tile: dead-end (start)");
                }
                if (junctionCount.GetValueOrDefault(endKey) <= 1)
                {
                    Vector3 ep = Local(r.points[r.points.Length - 1]);
                    Vector3 ep2 = Local(r.points[r.points.Length - 2]);
                    float eAngle = Mathf.Atan2(ep.z - ep2.z, ep.x - ep2.x);
                    InstantiateRoadTile(parent, _roadEnd, ep, eAngle, width);
                    UnityBridge.LogToAndroid("CityOSM", "Road tile: dead-end (end)");
                }

                if (steps % 180 == 0) yield return null;
                if (!string.IsNullOrEmpty(r.name) && r.points.Length >= 2)
                {
                    int mid = r.points.Length / 2;
                    Vector3 lp = Local(r.points[mid]);
                    int next = Mathf.Min(mid + 1, r.points.Length - 1);
                    Vector3 pn = Local(r.points[next]);
                    float la = Mathf.Atan2(pn.z - lp.z, pn.x - lp.x);
                    bool isMajor = r.highway == "primary" || r.highway == "secondary"
                        || r.highway == "tertiary" || r.highway == "residential"
                        || r.highway == "unclassified";
                    if (isMajor) SpawnStreetLabel(parent, r.name, lp, la);
                }
            }
        }

        private static readonly Color PoleColor = new Color(0.35f, 0.35f, 0.38f);
        private static readonly Color SignBg = new Color(0.15f, 0.40f, 0.70f);

        private void SpawnStreetLabel(Transform parent, string name, Vector3 pos, float angle)
        {
            if (string.IsNullOrEmpty(name)) return;
            var root = new GameObject("Via_" + name);
            root.transform.SetParent(parent, false);
            root.transform.position = pos;
            root.transform.rotation = Quaternion.Euler(0f, -angle * Mathf.Rad2Deg, 0f);

            // Palo (1.8m alto, 0.08m largo)
            CreateBox(root.transform, "Palo", new Vector3(0f, 0.9f, 0f),
                new Vector3(0.08f, 1.8f, 0.08f), GetMaterial(PoleColor), 0f, false);

            // Targa (0.9m x 0.28m, blu, a 1.7m da terra, sul lato della strada)
            CreateBox(root.transform, "Targa", new Vector3(0f, 1.72f, 0.35f),
                new Vector3(0.9f, 0.28f, 0.06f), GetMaterial(SignBg), 0f, false);

            // Testo sulla targa (TextMeshPro 3D, davanti alla targa)
            var textGo = new GameObject("NomeVia");
            textGo.transform.SetParent(root.transform, false);
            textGo.transform.localPosition = new Vector3(0f, 1.72f, 0.39f);
            textGo.transform.localRotation = Quaternion.identity;

            var tmp = textGo.AddComponent<TextMeshPro>();
            tmp.text = name.ToUpperInvariant();
            tmp.fontSize = 1.4f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            tmp.enableAutoSizing = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
            tmp.raycastTarget = false;
            tmp.characterSpacing = 1;
            tmp.fontSizeMin = 0.8f;
            tmp.fontSizeMax = 1.6f;

            // Limita a una strada su 3 per non ingombrare (solo strade principali)
        }

        // ── edifici ──────────────────────────────────────────────────

        private class Footprint
        {
            public float CenterX;
            public float CenterZ;
            public float Width;
            public float Depth;
            public float RotationRad;
        }

        private static Footprint CalcFootprint(GeoPoint[] pts)
        {
            if (pts == null || pts.Length < 3) return null;

            float minX = float.MaxValue, maxX = float.MinValue;
            float minZ = float.MaxValue, maxZ = float.MinValue;
            for (int i = 0; i < pts.Length; i++)
            {
                float x = CoordinateConverter.LonToX(pts[i].lng);
                float z = CoordinateConverter.LatToZ(pts[i].lat);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            float w = maxX - minX;
            float d = maxZ - minZ;
            if (w < 0.5f || d < 0.5f) return null;

            float rot = 0f;
            if (pts.Length >= 2)
            {
                float dx = CoordinateConverter.LonToX(pts[1].lng) - CoordinateConverter.LonToX(pts[0].lng);
                float dz = CoordinateConverter.LatToZ(pts[1].lat) - CoordinateConverter.LatToZ(pts[0].lat);
                rot = Mathf.Atan2(dz, dx);
            }

            return new Footprint
            {
                CenterX = (minX + maxX) * 0.5f,
                CenterZ = (minZ + maxZ) * 0.5f,
                Width = w,
                Depth = d,
                RotationRad = rot
            };
        }

        private IEnumerator BuildBuildingsCoroutine(Transform parent, OsmCityEnvelope env)
        {
            if (env.buildings == null) yield break;

            if (_registry == null && Huntix.Core.GameManager.Instance != null)
                _registry = Huntix.Core.GameManager.Instance.cityKitRegistry;

            int count = 0;
            foreach (var b in env.buildings)
            {
                var fp = CalcFootprint(b.points);
                if (fp == null) continue;
                if (fp.Width < 3f || fp.Depth < 3f) continue;

                float w = Mathf.Clamp(fp.Width, 3f, 40f);
                float d = Mathf.Clamp(fp.Depth, 3f, 40f);
                float h = InferHeight(b, w * d);

                // Case e negozi del gioco (Kenney City Kit) posizionati dove OSM
                // registra gli edifici reali, scalati sull'impronta e con l'altezza
                // OSM. Se il registry non e' disponibile si ripiega sui box colorati.
                if (TryPlaceKenneyBuilding(parent, b, fp, w, d, h))
                {
                    if (++count % 15 == 0) yield return null;
                    continue;
                }

                BStyle style = GetStyle(b);
                bool pitched = h < 7f;

                var bgo = new GameObject("Edificio " + b.id);
                bgo.layer = 8;
                bgo.transform.SetParent(parent, false);
                bgo.transform.position = new Vector3(fp.CenterX, 0f, fp.CenterZ);
                bgo.transform.rotation = Quaternion.Euler(0f, -fp.RotationRad * Mathf.Rad2Deg, 0f);

                CreateBox(bgo.transform, "Corpo", new Vector3(0f, h * 0.49f, 0f), new Vector3(w * 0.98f, h * 0.98f, d * 0.98f), GetMaterial(style.Body), 0f, false);

                float nwT = 0.3f;
                var ncL = bgo.AddComponent<BoxCollider>();
                ncL.size = new Vector3(nwT, h, d);
                ncL.center = new Vector3(-w * 0.5f, h * 0.5f, 0f);

                var ncR = bgo.AddComponent<BoxCollider>();
                ncR.size = new Vector3(nwT, h, d);
                ncR.center = new Vector3(w * 0.5f, h * 0.5f, 0f);

                var ncB = bgo.AddComponent<BoxCollider>();
                ncB.size = new Vector3(w, h, nwT);
                ncB.center = new Vector3(0f, h * 0.5f, -d * 0.5f);

                var ncT = bgo.AddComponent<BoxCollider>();
                ncT.size = new Vector3(w, nwT, d);
                ncT.center = new Vector3(0f, h, 0f);

                if (pitched)
                {
                    float eave = w * 0.5f + 0.15f;
                    const float slope = 0.5f;
                    float slopeAngle = Mathf.Atan(slope) * Mathf.Rad2Deg;
                    float panelLen = Mathf.Sqrt(eave * eave + (eave * slope) * (eave * slope));
                    float midY = h + eave * slope * 0.5f;
                    CreateBoxRot(bgo.transform, "Tetto S", new Vector3(0f, midY, eave * 0.5f),
                        new Vector3(w + 0.4f, 0.15f, panelLen * 2f), Quaternion.Euler(slopeAngle, 0f, 0f), GetMaterial(style.Roof), false);
                    CreateBoxRot(bgo.transform, "Tetto N", new Vector3(0f, midY, -eave * 0.5f),
                        new Vector3(w + 0.4f, 0.15f, panelLen * 2f), Quaternion.Euler(-slopeAngle, 0f, 0f), GetMaterial(style.Roof), false);
                }
                else
                {
                    CreateBox(bgo.transform, "Tetto", new Vector3(0f, h + 0.125f, 0f), new Vector3(w + 0.4f, 0.25f, d + 0.4f), GetMaterial(style.Roof), 0f, false);
                }

                CreateBox(bgo.transform, "Porta", new Vector3(0f, 1.2f, d * 0.5f + 0.05f), new Vector3(1.2f, 2.4f, 0.1f), GetMaterial(DoorColor), 0f, false);

                if (style.IsShop)
                {
                    string display = DisplayName(b);
                    CreateBox(bgo.transform, "Insegna", new Vector3(0f, Mathf.Min(2.6f, h * 0.35f), d * 0.5f + 0.3f),
                        new Vector3(Mathf.Min(w * 0.7f, 6f), 0.4f, 0.12f), GetMaterial(style.Sign), 0f, false);
                    AddShop(bgo.transform, display, d, b);
                }

                AddBuildingEntrance(bgo, b, w, d, h);
                _buildingBounds.Add(new Bounds(new Vector3(fp.CenterX, 0f, fp.CenterZ), new Vector3(w, h, d)));

                if (++count % 15 == 0) yield return null;
            }
        }

        private void AddBuildingEntrance(GameObject inst, OsmBuilding b, float w, float d, float h)
        {
            var entrance = new GameObject("Entrance");
            entrance.transform.SetParent(inst.transform, false);

            // Il trigger deve stare FUORI dal collider solido dell edificio,
            // abbastanza grande da attivarsi quando il player cammina vicino.
            // Divide per localScale: il collider del trigger (figlio) verrebbe
            // altrimenti dilatato dalla scala del parent, rendendo il trigger
            // enorme e posizionato lontanissimo dall'edificio in world space.
            Vector3 ls = inst.transform.localScale;
            var tc = entrance.AddComponent<BoxCollider>();
            tc.isTrigger = true;
            tc.size = new Vector3(Mathf.Min(w, 8f) / ls.x, 3f / ls.y, 4f / ls.z);
            tc.center = new Vector3(0f, 1.5f / ls.y, (d * 0.5f + 3.5f) / ls.z);

            var be = entrance.AddComponent<City.Interior.BuildingEntrance>();
            be.buildingName = DisplayName(b);
            be.buildingType = InferBuildingType(b);
            be.buildingWidth = w;
            be.buildingDepth = d;
            be.buildingHeight = h;
            be.floorCount = Mathf.Max(1, Mathf.RoundToInt(h / 3f));
        }

        private static string InferBuildingType(OsmBuilding b)
        {
            if (IsCommercial(b)) return "shop";
            string kind = (b.kind ?? "").ToLowerInvariant();
            if (kind.Contains("apartment") || (kind.Contains("residential") && (b.levels > 4 || b.height > 12)))
                return "apartment";
            return "house";
        }

        private void AddShop(Transform building, string name, float depth, OsmBuilding data)
        {
            var shop = building.gameObject.AddComponent<Shop>();
            shop.shopName = name;
            foreach (var item in ItemsFor(data)) shop.items.Add(item);

            var trigger = new GameObject("Parla");
            trigger.transform.SetParent(building, false);
            trigger.transform.localPosition = new Vector3(0f, 1.4f, depth * 0.5f + 3.0f);
            var col = trigger.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(4f, 3f, 3f);
            var door = trigger.AddComponent<InteractDoor>();
            door.label = name;
            door.opensShop = true;
            door.shop = shop;
        }

        // ── edifici del gioco (Kenney City Kit) sugli edifici reali OSM ──

        private static readonly string[] SuburbanHouses =
        {
            "building-type-a", "building-type-b", "building-type-c", "building-type-d",
            "building-type-e", "building-type-f", "building-type-g", "building-type-h",
            "building-type-i", "building-type-j", "building-type-k", "building-type-l",
            "building-type-m", "building-type-n", "building-type-o", "building-type-p",
            "building-type-q", "building-type-r", "building-type-s", "building-type-t"
        };

        private static readonly string[] CommercialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n"
        };

        private static readonly string[] IndustrialBuildings =
        {
            "building-a", "building-b", "building-c", "building-d", "building-e",
            "building-f", "building-g", "building-h", "building-i", "building-j",
            "building-k", "building-l", "building-m", "building-n", "building-o",
            "building-p", "building-q", "building-r", "building-s", "building-t"
        };

        private static readonly string[] Skyscrapers =
        {
            "building-skyscraper-a", "building-skyscraper-b", "building-skyscraper-c",
            "building-skyscraper-d", "building-skyscraper-e"
        };

        // ── altezze realistiche ──────────────────────────────

        private static float InferHeight(OsmBuilding b, float footprintArea)
        {
            // 1) altezza esplicita OSM
            if (b.height > 0.5) return Mathf.Clamp((float)b.height, 3f, 40f);

            // 2) stima dai piani (levels * 3m)
            if (b.levels > 0) return Mathf.Clamp(b.levels * 3f, 3f, 40f);

            // 3) stima dal tipo di edificio
            string kind = (b.kind ?? "").ToLowerInvariant();
            string amenity = (b.amenity ?? "").ToLowerInvariant();
            bool commercial = IsCommercial(b);

            if (kind.Contains("apartment") || kind.Contains("residential") && footprintArea > 100f)
                return 15f + Mathf.Clamp(footprintArea * 0.03f, 0f, 10f);

            if (kind.Contains("detached") || kind.Contains("house") || kind.Contains("bungalow"))
                return 6f;

            if (kind.Contains("garage") || kind.Contains("shed") || kind.Contains("carport"))
                return 3f;

            if (commercial)
                return 5f + Mathf.Clamp(footprintArea * 0.02f, 0f, 5f);

            if (kind.Contains("industrial") || kind.Contains("warehouse"))
                return 8f + Mathf.Clamp(footprintArea * 0.02f, 0f, 6f);

            // 4) default: dimensione del footprint
            if (footprintArea > 400f) return 12f;
            if (footprintArea > 150f) return 8f;
            return 6f;
        }

        private static bool IsCommercial(OsmBuilding b)
        {
            if (!string.IsNullOrEmpty(b.shop)) return true;
            string kind = b.kind ?? "";
            if (kind == "retail" || kind == "commercial") return true;
            switch (b.amenity ?? "")
            {
                case "restaurant": case "cafe": case "fast_food": case "bar": case "pub":
                case "school": case "hospital": case "library": case "gym": case "cinema":
                case "bank": case "hotel": case "pharmacy": case "post_office":
                case "marketplace": case "doctors": case "clinic": case "dentist":
                case "supermarket": case "mall": case "car_rental": case "fuel":
                    return true;
                default:
                    return false;
            }
        }

        private static bool IsIndustrial(OsmBuilding b)
        {
            string kind = (b.kind ?? "").ToLowerInvariant();
            if (kind.Contains("industrial") || kind.Contains("warehouse") || kind.Contains("factory"))
                return true;
            string amenity = (b.amenity ?? "").ToLowerInvariant();
            if (amenity == "fuel" || amenity == "car_repair" || amenity == "workshop")
                return true;
            return false;
        }

        private static string PickPrefabName(OsmBuilding b, float h)
        {
            bool commercial = IsCommercial(b);
            bool industrial = IsIndustrial(b);
            string kind = b.kind ?? "";
            bool tall = h >= 12f ||
                        kind.IndexOf("apartment", StringComparison.OrdinalIgnoreCase) >= 0 ||
                        kind.IndexOf("office", StringComparison.OrdinalIgnoreCase) >= 0;
            int idx = Math.Abs((int)(b.id % SuburbanHouses.Length));
            if (commercial && tall) return Skyscrapers[idx % Skyscrapers.Length];
            if (commercial) return CommercialBuildings[idx % CommercialBuildings.Length];
            if (industrial) return IndustrialBuildings[idx % IndustrialBuildings.Length];
            return SuburbanHouses[idx % SuburbanHouses.Length];
        }

        private bool TryPlaceKenneyBuilding(Transform parent, OsmBuilding b, Footprint fp, float w, float d, float h)
        {
            string prefabName = PickPrefabName(b, h);
            GameObject prefab = null;
            string source = "registry";
            if (_registry != null) prefab = _registry.Get(prefabName);
            if (prefab == null)
            {
                bool commercial = IsCommercial(b);
                bool industrial = IsIndustrial(b);
                source = industrial ? "Resources/Industrial" : commercial ? "Resources/Commercial" : "Resources/Suburban";
                if (industrial)
                    prefab = Resources.Load<GameObject>("Buildings/Industrial/" + prefabName);
                else if (commercial)
                    prefab = Resources.Load<GameObject>("Buildings/Commercial/" + prefabName);
                else
                    prefab = Resources.Load<GameObject>("Buildings/Suburban/" + prefabName);
            }
            if (prefab == null) return false;

            var inst = Instantiate(prefab, parent);
            inst.name = "Edificio " + b.id;
            inst.transform.position = new Vector3(fp.CenterX, 0f, fp.CenterZ);
            inst.transform.rotation = Quaternion.Euler(0f, -fp.RotationRad * Mathf.Rad2Deg, 0f);
            UnityBridge.LogToAndroid("CityOSM", $"Building placed: {prefabName} ({source}) id={b.id}");

            // Disabilita subito tutti i collider originali del prefab Kenney
            // (pareti, tetto, pavimento delle mesh). Usiamo enabled = false
            // invece di Destroy perche' Destroy e' deferred a fine frame:
            // il player e' gia' attivo durante il build e potrebbe camminare
            // vicino a un edificio i cui collider "vecchi" (inclusa la
            // facciata anteriore) non sono ancora stati rimossi dal solver.
            foreach (var origCol in inst.GetComponentsInChildren<Collider>(true))
            {
                origCol.enabled = false;
                Destroy(origCol);
            }

            // Scala il prefab sull'impronta reale (width x depth) e sull'altezza OSM.
            Bounds baseB = UnionBounds(inst);
            float sx = w / Mathf.Max(baseB.size.x, 0.1f);
            float sz = d / Mathf.Max(baseB.size.z, 0.1f);
            float s = Mathf.Max(sx, sz);
            float sy = Mathf.Clamp(h / Mathf.Max(baseB.size.y, 0.01f), s * 0.6f, s * 1.5f);
            inst.transform.localScale = new Vector3(sx, sy, sz);

            // Appoggia la base a terra e centra l'impronta sul footprint.
            Bounds wb = UnionBounds(inst);
            inst.transform.position += new Vector3(fp.CenterX - wb.center.x, -wb.min.y, fp.CenterZ - wb.center.z);

            // 4 collider su FIGLI di inst, tutti su layer 8 (Buildings):
            // il CharacterController IncludeLayers include sia layer 0 (Default)
            // che layer 8, cosi' collide con i muri. La camera raycast esclude
            // layer 8, cosi' i muri non schiacciano la camera verso il player.
            // La facciata anteriore (Z+) resta libera: il trigger dell'entrata
            // si attiva quando il player si avvicina alla porta.
            Vector3 ls = inst.transform.localScale;
            float wallT = 0.3f;
            int wallLayer = 8; // Buildings

            // Muro laterale sinistro
            var wallL = new GameObject("MuroL");
            wallL.layer = wallLayer;
            wallL.transform.SetParent(inst.transform, false);
            var colL = wallL.AddComponent<BoxCollider>();
            colL.size = new Vector3(wallT / ls.x, h / ls.y, d / ls.z);
            colL.center = new Vector3(-w * 0.5f / ls.x, h * 0.5f / ls.y, 0f);

            // Muro laterale destro
            var wallR = new GameObject("MuroR");
            wallR.layer = wallLayer;
            wallR.transform.SetParent(inst.transform, false);
            var colR = wallR.AddComponent<BoxCollider>();
            colR.size = new Vector3(wallT / ls.x, h / ls.y, d / ls.z);
            colR.center = new Vector3(w * 0.5f / ls.x, h * 0.5f / ls.y, 0f);

            // Muro posteriore
            var wallB = new GameObject("MuroB");
            wallB.layer = wallLayer;
            wallB.transform.SetParent(inst.transform, false);
            var colB = wallB.AddComponent<BoxCollider>();
            colB.size = new Vector3(w / ls.x, h / ls.y, wallT / ls.z);
            colB.center = new Vector3(0f, h * 0.5f / ls.y, -d * 0.5f / ls.z);

            // Soffitto
            var wallT_ = new GameObject("Soffitto");
            wallT_.layer = wallLayer;
            wallT_.transform.SetParent(inst.transform, false);
            var colT = wallT_.AddComponent<BoxCollider>();
            colT.size = new Vector3(w / ls.x, wallT / ls.y, d / ls.z);
            colT.center = new Vector3(0f, h / ls.y, 0f);

            if (IsCommercial(b)) AddShop(inst.transform, DisplayName(b), d, b);
            AddBuildingEntrance(inst, b, w, d, h);

            _buildingBounds.Add(new Bounds(
                new Vector3(fp.CenterX, 0f, fp.CenterZ),
                new Vector3(w, h, d)));

            return true;
        }

        private static Bounds UnionBounds(GameObject go)
        {
            var rends = go.GetComponentsInChildren<Renderer>();
            if (rends.Length == 0) return new Bounds(go.transform.position, Vector3.one);
            Bounds b = rends[0].bounds;
            for (int i = 1; i < rends.Length; i++) b.Encapsulate(rends[i].bounds);
            return b;
        }

        private static string DisplayName(OsmBuilding b)
        {
            if (!string.IsNullOrEmpty(b.name)) return b.name;
            string amenity = b.amenity ?? "";
            string shop = b.shop ?? "";
            string value = amenity.Length > 0 ? amenity : shop;
            if (value.Length == 0) return "Negozio";
            value = value.Replace('_', ' ');
            return char.ToUpper(value[0]) + value.Substring(1);
        }

        // ── skybox ────────────────────────────────────────────────────

        private void ApplyKenneySkybox()
        {
            try
            {
                var dayTex = Resources.Load<Texture2D>("Skyboxes/skybox-day");
                if (dayTex == null) return;
                var shader = Shader.Find("Skybox/Panoramic");
                if (shader == null) shader = Shader.Find("Skybox/Cubemap");
                if (shader == null) return;
                var mat = new Material(shader);
                mat.SetTexture("_MainTex", dayTex);
                RenderSettings.skybox = mat;
                RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Skybox;
                RenderSettings.ambientIntensity = 1f;
                UnityBridge.LogToAndroid("CityOSM", "Kenney skybox applied");
            }
            catch (Exception e)
            {
                UnityBridge.LogToAndroid("CityOSM", "Skybox error: " + e.Message);
            }
        }

        // ── vegetazione ──────────────────────────────────────────────

        private GameObject[] _treePrefabs;
        private GameObject[] _flowerPrefabs;
        private GameObject[] _grassPrefabs;

        private void LoadNaturePrefabs()
        {
            if (_treePrefabs != null) return;
            var trees = new List<GameObject>();
            string[] treeNames = { "tree_default", "tree_default_dark", "tree_default_fall",
                                   "tree_cone", "tree_cone_dark", "tree_cone_fall",
                                   "tree_oak", "tree_oak_dark", "tree_oak_fall",
                                   "tree_fat", "tree_detailed", "tree_blocks",
                                   "tree_palm" };
            foreach (var n in treeNames)
            {
                var p = Resources.Load<GameObject>("Nature/" + n);
                if (p != null) trees.Add(p);
            }
            if (trees.Count == 0)
            {
                var all = Resources.LoadAll<GameObject>("Nature/");
                foreach (var p in all)
                    if (p.name.StartsWith("tree_")) trees.Add(p);
            }
            _treePrefabs = trees.ToArray();

            var flowers = new List<GameObject>();
            string[] flowerNames = { "flower_redA", "flower_redB", "flower_purpleA", "flower_purpleB", "flower_yellowA", "flower_yellowB" };
            foreach (var n in flowerNames)
            {
                var p = Resources.Load<GameObject>("Nature/" + n);
                if (p != null) flowers.Add(p);
            }
            _flowerPrefabs = flowers.ToArray();

            var grass = new List<GameObject>();
            string[] grassNames = { "grass", "grass_large", "grass_leafs" };
            foreach (var n in grassNames)
            {
                var p = Resources.Load<GameObject>("Nature/" + n);
                if (p != null) grass.Add(p);
            }
            _grassPrefabs = grass.ToArray();

            UnityBridge.LogToAndroid("CityOSM", "Nature prefabs: trees=" + _treePrefabs.Length + " flowers=" + _flowerPrefabs.Length + " grass=" + _grassPrefabs.Length);
        }

        private IEnumerator BuildVegetationCoroutine(Transform parent, OsmCityEnvelope env)
        {
            LoadNaturePrefabs();
            int built = 0;

            if (env.trees != null)
            {
                foreach (var t in env.trees)
                {
                    SpawnKenneyTree(parent, new Vector2(CoordinateConverter.LonToX(t.lng), CoordinateConverter.LatToZ(t.lat)), Math.Abs((int)t.lat * 31 + (int)t.lng));
                    if (++built % 20 == 0) yield return null;
                }
            }

            if (env.parks != null)
            {
                int parkIndex = 0;
                foreach (var p in env.parks)
                {
                    var fp = CalcFootprint(p.points);
                    if (fp == null) continue;
                    int treeCount = Mathf.Clamp(Mathf.RoundToInt(fp.Width * fp.Depth / 100f), 3, 6);
                    for (int k = 0; k < treeCount; k++)
                    {
                        if (built >= 300) yield break;
                        int seed = parkIndex * 7 + k * 41;
                        float tx = fp.CenterX + (Frac(seed) - 0.5f) * fp.Width * 0.8f;
                        float tz = fp.CenterZ + (Frac(seed / 10) - 0.5f) * fp.Depth * 0.8f;
                        SpawnKenneyTree(parent, new Vector2(tx, tz), seed);
                        built++;
                    }
                    for (int g = 0; g < treeCount * 3; g++)
                    {
                        int seed = parkIndex * 13 + g * 37;
                        float gx = fp.CenterX + (Frac(seed) - 0.5f) * fp.Width * 0.9f;
                        float gz = fp.CenterZ + (Frac(seed / 10) - 0.5f) * fp.Depth * 0.9f;
                        SpawnKenneyFlower(parent, new Vector2(gx, gz), seed);
                    }
                    for (int gr = 0; gr < treeCount * 5; gr++)
                    {
                        int seed = parkIndex * 17 + gr * 29;
                        float gx = fp.CenterX + (Frac(seed) - 0.5f) * fp.Width * 0.9f;
                        float gz = fp.CenterZ + (Frac(seed / 10) - 0.5f) * fp.Depth * 0.9f;
                        SpawnKenneyGrass(parent, new Vector2(gx, gz), seed);
                    }
                    parkIndex++;
                    if (built % 20 == 0) yield return null;
                }
            }
        }

        private void SpawnKenneyTree(Transform parent, Vector2 pos, int seed)
        {
            if (_treePrefabs == null || _treePrefabs.Length == 0)
            {
                SpawnTree(parent, pos, seed);
                return;
            }
            var prefab = _treePrefabs[seed % _treePrefabs.Length];
            var go = Instantiate(prefab, parent);
            go.name = "Albero";
            go.transform.localPosition = new Vector3(pos.x, 0f, pos.y);
            float s = 0.8f + (seed % 4) * 0.1f;
            go.transform.localScale = Vector3.one * s;
            go.transform.localRotation = Quaternion.Euler(0f, (seed * 37) % 360, 0f);
            FixKenneyMaterials(go);
            foreach (var c in go.GetComponentsInChildren<Collider>())
                if (c != null) c.enabled = false;
        }

        private void SpawnKenneyFlower(Transform parent, Vector2 pos, int seed)
        {
            if (_flowerPrefabs == null || _flowerPrefabs.Length == 0) return;
            var prefab = _flowerPrefabs[seed % _flowerPrefabs.Length];
            var go = Instantiate(prefab, parent);
            go.name = "Fiore";
            go.transform.localPosition = new Vector3(pos.x, 0f, pos.y);
            float s = 0.6f + (seed % 3) * 0.15f;
            go.transform.localScale = Vector3.one * s;
            FixKenneyMaterials(go);
            foreach (var c in go.GetComponentsInChildren<Collider>())
                if (c != null) c.enabled = false;
        }

        private void SpawnKenneyGrass(Transform parent, Vector2 pos, int seed)
        {
            if (_grassPrefabs == null || _grassPrefabs.Length == 0) return;
            var prefab = _grassPrefabs[seed % _grassPrefabs.Length];
            var go = Instantiate(prefab, parent);
            go.name = "Erba";
            go.transform.localPosition = new Vector3(pos.x, 0f, pos.y);
            float s = 0.5f + (seed % 3) * 0.2f;
            go.transform.localScale = Vector3.one * s;
            FixKenneyMaterials(go);
            foreach (var c in go.GetComponentsInChildren<Collider>())
                if (c != null) c.enabled = false;
        }

        private void SpawnTree(Transform parent, Vector2 pos, int seed)
        {
            float h = 1.6f + (seed % 5) * 0.15f;
            CreateBox(parent, "Albero", new Vector3(pos.x, h * 0.5f, pos.y), new Vector3(0.18f, h, 0.18f), GetMaterial(TrunkColor), 0f, false);

            Material canopy = CanopyMat(seed % 3);
            SpawnSphere(parent, "Chioma", new Vector3(pos.x, h + 0.3f, pos.y), 0.55f + (seed % 3) * 0.08f, canopy);
            SpawnSphere(parent, "Chioma", new Vector3(pos.x + 0.2f, h + 0.15f, pos.y + 0.15f), 0.4f + (seed % 2) * 0.1f, canopy);
            SpawnSphere(parent, "Chioma", new Vector3(pos.x - 0.15f, h + 0.4f, pos.y - 0.1f), 0.35f + (seed % 2) * 0.05f, CanopyMat(2));
        }

        private void SpawnSphere(Transform parent, string name, Vector3 pos, float radius, Material mat)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.position = pos;
            go.transform.localScale = Vector3.one * radius * 2f;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            var c = go.GetComponent<Collider>();
            if (c != null) c.enabled = false;
        }

        // ── helper oggetti/materail ──────────────────────────────────

        private Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        private GameObject CreateBox(Transform parent, string name, Vector3 center, Vector3 scale, Material mat, float yawRad, bool collider)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = center;
            if (yawRad != 0f) go.transform.localRotation = Quaternion.Euler(0f, -yawRad * Mathf.Rad2Deg, 0f);
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            if (!collider)
            {
                var c = go.GetComponent<Collider>();
                if (c != null) c.enabled = false;
            }
            return go;
        }

        private GameObject CreateBoxRot(Transform parent, string name, Vector3 localPos, Vector3 scale, Quaternion localRot, Material mat, bool collider)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localRotation = localRot;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            if (!collider)
            {
                var c = go.GetComponent<Collider>();
                if (c != null) c.enabled = false;
            }
            return go;
        }

        private void FixKenneyMaterials(GameObject go)
        {
            var urp = Shader.Find("Universal Render Pipeline/Lit");
            if (urp == null) urp = Shader.Find("Standard");
            foreach (Renderer r in go.GetComponentsInChildren<Renderer>())
            {
                if (r == null) continue;
                Material[] mats = r.sharedMaterials;
                for (int i = 0; i < mats.Length; i++)
                {
                    Material m = mats[i];
                    if (m == null) { mats[i] = new Material(urp); continue; }
                    if (m.shader == null) { mats[i] = new Material(urp); continue; }
                    string sn = m.shader.name;
                    if (sn.StartsWith("Universal Render Pipeline") || sn.StartsWith("Hidden/Universal")) continue;
                    var lit = new Material(urp);
                    if (m.HasProperty("_MainTex")) lit.SetTexture("_BaseMap", m.GetTexture("_MainTex"));
                    if (m.HasProperty("_Color")) lit.SetColor("_BaseColor", m.GetColor("_Color"));
                    else lit.SetColor("_BaseColor", Color.white);
                    mats[i] = lit;
                }
                r.sharedMaterials = mats;
            }
        }

        private Material GetMaterial(Color color)
        {
            if (_materials.TryGetValue(color, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", color);
            else
                m.SetColor("_Color", color);
            _materials[color] = m;
            return m;
        }

        private static readonly Color GrassColor = new Color(0.38f, 0.62f, 0.30f);
        private static readonly Color RoadColor = new Color(0.27f, 0.27f, 0.30f);
        private static readonly Color CenterLineColor = new Color(1f, 0.75f, 0.04f);
        private static readonly Color DoorColor = new Color(0.18f, 0.14f, 0.10f);
        private static readonly Color TrunkColor = new Color(0.42f, 0.26f, 0.15f);

        private Material CanopyMat(int idx)
        {
            switch (idx)
            {
                case 0: return GetMaterial(new Color(0.18f, 0.49f, 0.20f));
                case 1: return GetMaterial(new Color(0.26f, 0.63f, 0.28f));
                default: return GetMaterial(new Color(0.11f, 0.38f, 0.13f));
            }
        }

        // ── palette / negozi ─────────────────────────────────────────

        private class BStyle
        {
            public Color Body;
            public Color Roof;
            public Color Sign;
            public bool IsShop;
        }

        private static BStyle GetStyle(OsmBuilding b)
        {
            string amenity = (b.amenity ?? "").ToLowerInvariant();
            string shop = (b.shop ?? "").ToLowerInvariant();
            string kind = b.kind ?? "";

            if (kind == "shop" || shop.Length > 0)
            {
                return new BStyle
                {
                    Body = new Color(1f, 0.97f, 0.86f),
                    Roof = new Color(0.72f, 0.53f, 0.04f),
                    Sign = new Color(0.12f, 0.53f, 0.90f),
                    IsShop = true
                };
            }
            if (amenity.Length > 0)
            {
                if (amenity.Contains("restaurant") || amenity.Contains("cafe") || amenity.Contains("bar") ||
                    amenity.Contains("fast_food") || amenity.Contains("pub") || amenity.Contains("food"))
                {
                    return new BStyle
                    {
                        Body = new Color(1f, 0.89f, 0.71f),
                        Roof = new Color(0.55f, 0f, 0f),
                        Sign = new Color(0.55f, 0f, 0f),
                        IsShop = true
                    };
                }
                return new BStyle
                {
                    Body = new Color(0.93f, 0.93f, 0.93f),
                    Roof = new Color(0.40f, 0.40f, 0.40f),
                    Sign = new Color(0.30f, 0.50f, 0.90f),
                    IsShop = true
                };
            }
            if (kind == "commercial")
            {
                return new BStyle
                {
                    Body = new Color(0.83f, 0.77f, 0.66f),
                    Roof = new Color(0.42f, 0.36f, 0.27f),
                    Sign = new Color(0.60f, 0.50f, 0.30f),
                    IsShop = false
                };
            }
            if (kind == "industrial")
            {
                return new BStyle
                {
                    Body = new Color(0.69f, 0.69f, 0.69f),
                    Roof = new Color(0.40f, 0.40f, 0.40f),
                    Sign = new Color(0.60f, 0.60f, 0.60f),
                    IsShop = false
                };
            }
            return new BStyle
            {
                Body = new Color(0.91f, 0.83f, 0.72f),
                Roof = new Color(0.55f, 0.27f, 0.08f),
                Sign = new Color(0.60f, 0.50f, 0.30f),
                IsShop = false
            };
        }

        private static List<ShopItem> ItemsFor(OsmBuilding b)
        {
            string amenity = (b.amenity ?? "").ToLowerInvariant();
            string shop = (b.shop ?? "").ToLowerInvariant();
            string cat = amenity.Length > 0 ? amenity : shop;

            if (ContainsAny(cat, "supermarket", "convenience", "groceries", "general", "market"))
                return new List<ShopItem> { new ShopItem("Pane", 2), new ShopItem("Latte", 1), new ShopItem("Acqua", 1), new ShopItem("Mele", 1) };
            if (cat.Contains("bakery"))
                return new List<ShopItem> { new ShopItem("Pane al Sesamo", 3), new ShopItem("Cornetto", 2), new ShopItem("Torta", 6) };
            if (cat.Contains("butcher"))
                return new List<ShopItem> { new ShopItem("Petto di pollo", 6), new ShopItem("Bistecca", 9), new ShopItem("Salsicce", 5) };
            if (cat.Contains("greengrocer"))
                return new List<ShopItem> { new ShopItem("Mele", 1), new ShopItem("Pomodori", 2), new ShopItem("Insalata", 1) };
            if (ContainsAny(cat, "restaurant", "fast_food", "food_court", "pub", "food"))
                return new List<ShopItem> { new ShopItem("Pizza", 8), new ShopItem("Pasta", 10), new ShopItem("Acqua", 1) };
            if (ContainsAny(cat, "cafe", "bar", "coffee"))
                return new List<ShopItem> { new ShopItem("Caffè", 1), new ShopItem("Cappuccino", 2), new ShopItem("Cornetto", 2) };
            if (ContainsAny(cat, "clothes", "fashion", "shoes", "boutique"))
                return new List<ShopItem> { new ShopItem("Maglietta", 15), new ShopItem("Jeans", 25), new ShopItem("Cappellino", 10) };
            if (ContainsAny(cat, "electronics", "computer", "mobile"))
                return new List<ShopItem> { new ShopItem("Cuffie", 25), new ShopItem("Cavo USB", 8), new ShopItem("Powerbank", 30) };
            if (ContainsAny(cat, "pharmacy", "chemist", "health"))
                return new List<ShopItem> { new ShopItem("Cerotti", 3), new ShopItem("Paracetamolo", 5), new ShopItem("Vitamine", 12) };
            if (ContainsAny(cat, "hardware", "diy"))
                return new List<ShopItem> { new ShopItem("Viti (10)", 4), new ShopItem("Martello", 12), new ShopItem("Nastro adesivo", 3) };
            if (ContainsAny(cat, "book", "stationery"))
                return new List<ShopItem> { new ShopItem("Libro", 12), new ShopItem("Quaderno", 3), new ShopItem("Penna", 1) };
            return new List<ShopItem> { new ShopItem("Articolo", 5) };
        }

        private static bool ContainsAny(string value, params string[] keys)
        {
            for (int i = 0; i < keys.Length; i++)
                if (value.Contains(keys[i])) return true;
            return false;
        }

        // ── streaming ────────────────────────────────────────────────

        private void Update()
        {
            // Uscita/pausa in corso: niente polling JNI durante il teardown
            // dell'engine Unity (in gara con Android può essere un crash nativo).
            if (Exiting || _paused) return;

            // Rientro nella scena City: la città OSM era nella scena (non
            // DontDestroyOnLoad) quindi è stata distrutta allo sblocco e il seed
            // è ricomparso. Ricostruisci attorno al centro corrente.
            if (_firstBuildDone && _root == null)
            {
                _firstBuildDone = false;
                _pending = false;
                // Rientro nella scena City: la seed e' ricomparsa col reload della
                // scena, la nascondo e congelo il player come all'avvio.
                HideSeedCityAndFreezePlayer();
                RequestArea(CenterLat, CenterLng);
                return;
            }

            // Watchdog: se la richiesta OSM non riceve risposta entro 12s (nemmeno
            // l'ACK Android) rispedisce la STESSA richiesta in volo (non il centro
            // vecchio). Se invece l'ACK è arrivato ma i dati non ancora, attende molto
            // di più: un fetch Overpass lento non deve essere duplicato (DoS/429).
            if (_pending)
            {
                _pendingTimer += Time.deltaTime;
                float limit = _pendingAck ? PendingAckTimeoutS : PendingWatchdogS;
                if (_pendingTimer >= limit)
                {
                    _pendingTimer = 0f;
                    Debug.LogWarning(_pendingAck
                        ? $"[CityOSMWorld] Dati OSM non arrivati dopo {PendingAckTimeoutS:F0}s dall'ACK, riprovo..."
                        : "[CityOSMWorld] Richiesta OSM senza risposta, riprovo...");
                    _pending = false;
                    _pendingAck = false;
                    RequestArea(_requestLat, _requestLng);
                }
                return;
            }
            _pendingTimer = 0f;
            _pendingAck = false;

            if (!_firstBuildDone || _building) return;

            _pollTimer += Time.deltaTime;
            if (_pollTimer < PollIntervalS) return;
            _pollTimer = 0f;

            var loc = ReadBridgeLocation();
            if (loc == null || IsZero(loc))
            {
                SetGpsStatus("GPS non disponibile · mappa fissa " +
                             CenterLat.ToString("F4", CultureInfo.InvariantCulture) + ", " +
                             CenterLng.ToString("F4", CultureInfo.InvariantCulture));
                return;
            }

            if (_usedDefaultLocation != null)
            {
                // GPS arrivato dopo il bootstrap su Roma: sblocca il streaming e
                // ri-centra sulla posizione reale invece di restare sulla mappa fissa.
                _usedDefaultLocation = null;
                Debug.Log($"[CityOSMWorld] GPS disponibile dopo il default: ri-centro su ({loc.lat},{loc.lng})");
                RequestArea(loc.lat, loc.lng);
                return;
            }

            double distance = DistanceMeters(CenterLat, CenterLng, loc.lat, loc.lng);
            SetGpsStatus(
                "GPS " + loc.lat.ToString("F5", CultureInfo.InvariantCulture) + ", " +
                loc.lng.ToString("F5", CultureInfo.InvariantCulture) +
                "  ·  centro " + CenterLat.ToString("F5", CultureInfo.InvariantCulture) + ", " +
                CenterLng.ToString("F5", CultureInfo.InvariantCulture) +
                "  ·  distanza " + Math.Round(distance) + " m");
            if (distance > StreamThresholdM)
            {
                Debug.Log($"[CityOSMWorld] Streaming: player a {distance:F0} m dal centro, ri-centro su ({loc.lat},{loc.lng})");
                RequestArea(loc.lat, loc.lng);
            }
        }

        private static BridgeLocation ReadBridgeLocation()
        {
            if (Exiting) return null;
            try
            {
                var json = UnityBridge.GetCurrentLocation();
                if (string.IsNullOrEmpty(json)) return null;
                return JsonUtility.FromJson<BridgeLocation>(json);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[CityOSMWorld] Posizione non leggibile: " + e.Message);
                return null;
            }
        }

        private static bool IsZero(BridgeLocation loc)
        {
            return loc.lat == 0.0 && loc.lng == 0.0;
        }

        private static double DistanceMeters(double lat1, double lng1, double lat2, double lng2)
        {
            const double r = 6371000.0;
            double dLat = (lat2 - lat1) * Mathf.Deg2Rad;
            double dLng = (lng2 - lng1) * Mathf.Deg2Rad;
            double a = Math.Sin(dLat / 2.0) * Math.Sin(dLat / 2.0) +
                       Math.Cos(lat1 * Mathf.Deg2Rad) * Math.Cos(lat2 * Mathf.Deg2Rad) *
                       Math.Sin(dLng / 2.0) * Math.Sin(dLng / 2.0);
            return r * 2.0 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1.0 - a));
        }

        private static float Frac(int v)
        {
            return Mathf.Abs(v) % 100 / 100f;
        }

        private void ShowToast(string message)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
            {
                City.Game.Instance.ui.ShowToast(message);
            }
        }

        private void SetGpsStatus(string text)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
            {
                City.Game.Instance.ui.SetGpsStatus(text);
            }
        }
    }
}

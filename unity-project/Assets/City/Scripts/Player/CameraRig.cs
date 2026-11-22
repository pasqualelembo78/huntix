using UnityEngine;
using City.OSM;
using City.UI;
using System.Collections.Generic;

namespace City.Player
{
    public class CameraRig : MonoBehaviour
    {
        public static CameraRig Instance;

        public Transform target;
        // Vista stile Brookhaven: camera quasi ORIZZONTALE (altezza torace) con
        // il player al centro della scena. Il vecchio assetto (height 3.2,
        // pitch 18) guardava troppo dall'alto e il player stava in basso.
        public float distance = 7f;
        public float height = 1.6f;
        public float pitch = 5f;
        public float smoothTime = 0.12f;
        /// <summary>Con frame da 400 ms SmoothDamp non converge e la camera
        /// resta indietro anche a player fermo (camGap=0,79 misurato): oltre
        /// questa distanza la agganciamo al punto desiderato.</summary>
        private const float MaxSmoothGap = 0.35f;
        public float orbitSpeed = 5f;
        // Giroscopio (Android): gira la visuale ruotando il telefono, in
        // aggiunta al drag sul lato destro. Se ruota al contrario inverti
        // il segno di GyroSensitivity. Il toggle GIRO in HUD lo spegne.
        public const float GyroSensitivity = 35f;   // rad/s -> gradi (ridotta: meno sensibile al movimento del device)
        public const float GyroDeadzone = 0.005f;   // ~0.3 gradi/s: ignora i tremori
        public const float GyroSmoothing = 0.25f;
        private bool _gyroEnabled = true;
        private float _gyroRateEma;
        // Pitch (verticale) dal giroscopio: alzi il telefono -> vedi il cielo,
        // lo abbassi -> vedi il terreno. Come lo yaw: effetto proporzionale,
        // riporti il telefono in linea e la vista torna all'orizzonte.
        public const float GyroPitchMax = 70f;
        private float _gyroPitchEma;
        private float _gyroPitchOffset;
        public bool GyroEnabled { get { return _gyroEnabled; } }
        /// <summary>Modalita' davvero attiva: in guida la camera resta terza
        /// persona anche se la preferenza e' impostata sulla prima.</summary>
        public bool IsFirstPerson { get { return firstPerson && !drivingMode; } }
        // Prima persona stile Brookhaven (icona camera): la camera sale alla
        // testa del player. Vale solo a piedi: in auto resta terza persona.
        // La preferenza sopravvive al reload della scena (PlayerPrefs).
        public bool firstPerson = false;
        private const string CameraModePrefKey = "cameraFirstPerson";
        private float _fpBlend = 0f;

        // Riferimenti risolti lazy: il modello visivo (PlayerHeroRig) puo'
        // arrivare dopo lo Start del rig (istanziamento città asincrono).
        // Nascondere i Renderer NON disattiva il GameObject: Animator, FootIK,
        // SoleGeometry, CharacterController e diagnostica restano attivi.
        private Transform _cachedTarget;
        private PlayerHeroRig _heroRig;
        private Renderer[] _heroRenderers;
        private bool[] _heroRendererWasEnabled;
        private Transform _headBone;
        private bool _headBoneSearched;
        private SoleGeometry _soleProbe;
        private bool _modelHidden;
        private bool _fallbackHeightLogged;
        private float _nextHeroSearch;

        private bool drivingMode;
        private readonly float driveDistance = 12f;
        private readonly float driveHeight = 5f;
        private readonly float drivePitch = 15f;

        // ── Override per interni aperti (negozi/case e concessionaria/officina/garage) ──
        // Owner-keyed: ogni sistema che avvicina la camera dentro un interno usa
        // un owner, cosi' attivare uno non cancella l'altro e ognuno rilascia
        // solo il proprio contributo (niente conteggi che vanno male).
        private readonly HashSet<string> _indoorOwners = new HashSet<string>();
        private bool indoorOverrideActive => _indoorOwners.Count > 0;
        private float indoorDistance;
        private float indoorHeight;
        private float indoorPitch;
        private float indoorTargetDistance;
        private float indoorTargetHeight;
        private float indoorTargetPitch;

        private float yaw = 0f;
        private Vector3 velocity;
        private float _nextPushLog;

        // ── GIORNO 9: spring arm ──
        // La collisione usa una SFERA, non un raggio: un muro visto di taglio
        // o il bordo di un cassetto non bucano piu' la camera. Un solo query
        // fisico per frame, buffer riusato, nessuna allocazione.
        private const float ArmRadius = 0.22f;
        private const float ArmPivotHeight = 1.4f;
        private const int ArmHitSlots = 4;
        private readonly RaycastHit[] _armHits = new RaycastHit[ArmHitSlots];
        // Distanza di protezione dell'uovo del cassetto (RevealDist di
        // ContainerEgg): la camera non si avvicina abbastanza da tagliarlo.
        private const float EggArmClearance = 2.4f;
        // Smoothing separato: la vista segue piu' veloce del corpo, ma con
        // LerpAngle (no giro lungo 359° -> 1°).
        private const float RotationFollow = 12f;
        private float _viewYaw;
        private float _viewPitch;
        private bool _snapView = true;
        // Giroscopio: solo vista, MAI base di marcia (MovementBasis qui sotto).
        private float _gyroYawOffset;

        /// <summary>GIORNO 9 - base di marcia del joystick: solo lo yaw del
        /// drag/teleport. Il giroscopio muove la vista, non la direzione con
        /// cui il personaggio cammina.</summary>
        public Quaternion MovementBasis { get { return Quaternion.Euler(0f, yaw, 0f); } }

        // ── GIORNO 5: head bob ──
        // Piccolo offset della camera pilota dalla FASE DEL PASSO del
        // CharacterWalker (la stessa che genera i passi audio): niente timer
        // parallelo che a 10 fps si disallinea. L'offset si applica DOPO
        // SmoothDamp e si sottrae all'inizio del frame successivo, per non
        // inquinare velocity/LastSmoothGap ne' il raycast anti-ostacolo.
        public const string HeadBobPrefKey = "huntix_head_bob";
        private bool _headBobEnabled = true;
        public bool HeadBobEnabled { get { return _headBobEnabled; } }
        public float bobVerticalM = 0.035f;
        public float bobHorizontalM = 0.018f;
        public float bobRollDeg = 0.35f;
        private CharacterWalker _walker;
        private float _bobWeight;
        private Vector3 _bobApplied;
        private const float BobTwoPi = 6.2831853f;

        /// <summary>Commuta il bob a runtime (dal viewer log Android) e lo
        /// persiste, come il toggle del follower verticale.</summary>
        public bool ToggleHeadBob()
        {
            _headBobEnabled = !_headBobEnabled;
            PlayerPrefs.SetInt(HeadBobPrefKey, _headBobEnabled ? 1 : 0);
            PlayerPrefs.Save();
            OsmDiag.Log("[Camera][Bob] head bob -> " + (_headBobEnabled ? "ON" : "OFF"));
            return _headBobEnabled;
        }

        private const float MinCameraDistance = 4.5f;
        private const float MaxCameraDistance = 30f;
        private const float RayOriginHeight = 5f;

        private float pinchStartDistance;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        /// <summary>GIORNO 10: RealmSceneManager disattiva/riattiva il rig
        /// quando si cambia regno. Al ritorno la vista deve agganciarsi
        /// subito allo yaw corrente, non scivolare dall'angolo vecchio.</summary>
        private void OnEnable()
        {
            _snapView = true;
        }

        private void Start()
        {
            if (target == null)
            {
                GameObject p = GameObject.FindGameObjectWithTag("Player");
                if (p != null) target = p.transform;
            }
            if (target != null)
            {
                yaw = target.eulerAngles.y;
                _viewYaw = yaw;
                _snapView = true;
                _walker = target.GetComponent<CharacterWalker>();
            }
            // Modalita' camera persistente (bottone in-game e bridge Android).
            firstPerson = PlayerPrefs.GetInt(CameraModePrefKey, 0) == 1;
            RefreshHeroReferences();
            ApplyModelVisibility();
            OsmDiag.Log("[Camera][Mode] avvio -> " +
                (firstPerson ? "PRIMA persona" : "TERZA persona"));
            // Il pulsante hamburger e' un root DontDestroyOnLoad indipendente
            // dalla camera: lo si ricontrolla all'avvio e a ogni toggle perche'
            // un reload puo' lasciarlo disattivato (issue 1A/3A).
            HamburgerMenu.EnsureVisible();
            _headBobEnabled = PlayerPrefs.GetInt(HeadBobPrefKey, 1) == 1;
            if (SystemInfo.supportsGyroscope)
            {
                _gyroEnabled = PlayerPrefs.GetInt("gyroCam", 1) == 1;
                SetGyroSensor(_gyroEnabled);
                OsmDiag.Log("[Camera][Gyro] supportato, attivo=" + _gyroEnabled);
            }
            else { _gyroEnabled = false; }
        }

        public void Orbit(float screenDeltaX)
        {
            yaw += screenDeltaX * orbitSpeed * 0.15f;
        }

        public void SetYaw(Quaternion lookRotation)
        {
            yaw = lookRotation.eulerAngles.y;
            // Teleport/ingresso auto/uscita edificio: la vista si aggancia
            // subito, senza scivolare per 80 ms dal vecchio angolo.
            _snapView = true;
        }

        public void ToggleFirstPerson()
        {
            SetFirstPerson(!firstPerson);
        }

        public void SetFirstPerson(bool on)
        {
            firstPerson = on;
            PlayerPrefs.SetInt(CameraModePrefKey, on ? 1 : 0);
            PlayerPrefs.Save();
            RefreshHeroReferences();
            ApplyModelVisibility();
            string msg = (on ? "PRIMA" : "TERZA") + " persona" +
                " (occhio=" + FirstPersonEyeHeight().ToString("F2") + "m dal pivot)";
            City.OSM.OsmDiag.Log("[Camera][Mode] " + msg);
            // Il cambio di vista non deve portare via l'hamburger: lo si
            // riattiva esplicitamente (idempotente) e lo si traccia nel log.
            HamburgerMenu.EnsureVisible();
            // Ponte Unity -> Android: la scelta finisce nel viewer dei log
            // dell'app (nessun logcat), coerente con il comando inverso
            // GameManager.OnEvent("toggleCameraMode").
            Huntix.Bridge.UnityBridge.LogToAndroid("[Camera][Mode]", msg);
        }

        public void SetDrivingMode(bool driving)
        {
            if (drivingMode == driving) return;
            drivingMode = driving;
            ApplyModelVisibility();
        }

        /// <summary>Risolve il modello visivo e l'osso testa. Non nasconde
        /// nulla: i Renderer vengono solo memorizzati con il loro stato
        /// originale, cosi' la rimozione in prima persona e' reversibile.</summary>
        private void RefreshHeroReferences()
        {
            if (target == null) return;
            if (_cachedTarget != target)
            {
                _cachedTarget = target;
                _heroRig = null;
                _heroRenderers = null;
                _heroRendererWasEnabled = null;
                _headBone = null;
                _headBoneSearched = false;
                _soleProbe = null;
                _modelHidden = false;
            }

            if (_heroRig == null)
                _heroRig = target.GetComponentInChildren<PlayerHeroRig>(true);
            if (_heroRig != null)
            {
                if (_heroRenderers == null)
                {
                    var all = _heroRig.GetComponentsInChildren<Renderer>(true);
                    _heroRenderers = new Renderer[all.Length];
                    _heroRendererWasEnabled = new bool[all.Length];
                    for (int i = 0; i < all.Length; i++)
                    {
                        _heroRenderers[i] = all[i];
                        _heroRendererWasEnabled[i] = all[i] != null && all[i].enabled;
                    }
                }
                if (_headBone == null && !_headBoneSearched)
                {
                    var anim = _heroRig.GetComponentInChildren<Animator>(true);
                    if (anim != null) _headBone = anim.GetBoneTransform(HumanBodyBones.Head);
                    _headBoneSearched = _headBone != null;
                }
                if (_soleProbe == null) _soleProbe = _heroRig.GetComponent<SoleGeometry>();
                return;
            }

            // Fallback avatar legacy (senza PlayerHeroRig): prova comunque
            // l'osso testa dell'Animator, mai una costante inventata.
            if (_headBone == null && !_headBoneSearched)
            {
                var anim = target.GetComponentInChildren<Animator>(true);
                if (anim != null) _headBone = anim.GetBoneTransform(HumanBodyBones.Head);
                _headBoneSearched = true;
            }
        }

        private void ApplyModelVisibility()
        {
            if (target == null || _heroRig == null || _heroRenderers == null) return;
            bool hide = firstPerson && !drivingMode;
            if (_modelHidden == hide) return;
            _modelHidden = hide;
            for (int i = 0; i < _heroRenderers.Length; i++)
            {
                var r = _heroRenderers[i];
                if (r != null) r.enabled = _heroRendererWasEnabled[i] && !hide;
            }
            City.OSM.OsmDiag.Log("[Camera][Mode] modello visivo " +
                (hide ? "nascosto" : "ripristinato") +
                ": solo Renderer — Animator/FootIK/SoleGeometry/CC attivi");
        }

        /// <summary>Altezza della camera in prima persona rispetto al pivot
        /// del player. Primaria: posa dell'osso Head (segue IK e animazione).
        /// Secondaria: HeadToSole misurato sui vertici, meno l'offset noto del
        /// pivot (feetAnchor + SoleTouchDrop). Niente costanti numeriche nuove.
        /// </summary>
        private float FirstPersonEyeHeight()
        {
            if (target == null) return 0f;
            if (_headBone == null) RefreshHeroReferences();
            if (_headBone != null)
            {
                float h = _headBone.position.y - target.position.y;
                if (h > 0.3f && h < 3f) return h;
            }
            if (_soleProbe == null) RefreshHeroReferences();
            if (_soleProbe != null && !float.IsNaN(_soleProbe.HeadToSole) && _heroRig != null)
            {
                float pivotAboveSole = -_heroRig.feetAnchor.y + PlayerHeroRig.SoleTouchDrop;
                float h = _soleProbe.HeadToSole - pivotAboveSole;
                if (h > 0.3f && h < 3f) return h;
            }
            if (!_fallbackHeightLogged)
            {
                _fallbackHeightLogged = true;
                City.OSM.OsmDiag.Log("[Camera][Mode] WARNING: armatura/testa non "
                    + "disponibili, camera al pivot del player (occhio=0)");
            }
            return 0f;
        }

        /// <summary>
        /// Telecamera stretta dentro un interno a "casa aperta" (negozi, case,
        /// concessionaria, officina, garage): avvicina la vista dietro al player
        /// cosi' la camera entra nell'edificio insieme a lui, sempre in terza
        /// persona. active=false termina l'override per questo owner e
        /// ripristina gradualmente la distanza quando nessun owner e' attivo.
        /// </summary>
        /// <param name="active">Attiva/disattiva l'override per l'owner.</param>
        /// <param name="owner">Identificativo del sistema chiamante (es. "building", "vehicle-poi").</param>
        /// <param name="distance">Distanza camera-player target.</param>
        /// <param name="height">Altezza camera target.</param>
        /// <param name="pitch">Pitch camera target.</param>
        public void SetIndoorOverride(bool active, string owner = "default",
            float distance = 5.2f, float height = 2.4f, float pitch = 22f)
        {
            if (string.IsNullOrEmpty(owner)) owner = "default";

            if (active)
            {
                bool hadAny = _indoorOwners.Count > 0;
                _indoorOwners.Add(owner);
                indoorTargetDistance = distance;
                indoorTargetHeight = height;
                indoorTargetPitch = pitch;
                if (!hadAny) InitializeIndoorBlend();
            }
            else
            {
                _indoorOwners.Remove(owner);
                if (_indoorOwners.Count == 0)
                {
                    // nessun owner attivo: il blend fara' il restore
                }
            }
        }

        /// <summary>Overload backward-compat (usa owner "default").</summary>
        public void SetIndoorOverride(bool active,
            float distance = 5.2f, float height = 2.4f, float pitch = 22f)
        {
            SetIndoorOverride(active, "default", distance, height, pitch);
        }

        private void InitializeIndoorBlend()
        {
            if (indoorDistance > 0f) return;
            indoorDistance = drivingMode ? driveDistance : distance;
            indoorHeight = drivingMode ? driveHeight : height;
            indoorPitch = drivingMode ? drivePitch : pitch;
        }

        private void UpdateIndoorBlend(float dt)
        {
            float blendSpeed = 5f;
            if (indoorOverrideActive)
            {
                indoorDistance = Mathf.Lerp(indoorDistance, indoorTargetDistance, dt * blendSpeed);
                indoorHeight = Mathf.Lerp(indoorHeight, indoorTargetHeight, dt * blendSpeed);
                indoorPitch = Mathf.Lerp(indoorPitch, indoorTargetPitch, dt * blendSpeed);
            }
            else if (indoorDistance > 0f)
            {
                float baseDist = drivingMode ? driveDistance : distance;
                float baseHeight = drivingMode ? driveHeight : height;
                float basePitch = drivingMode ? drivePitch : pitch;
                indoorDistance = Mathf.Lerp(indoorDistance, baseDist, dt * blendSpeed);
                indoorHeight = Mathf.Lerp(indoorHeight, baseHeight, dt * blendSpeed);
                indoorPitch = Mathf.Lerp(indoorPitch, basePitch, dt * blendSpeed);
            }
        }

        public void ApplyZoom(float delta)
        {
            distance = Mathf.Clamp(distance + delta, MinCameraDistance, MaxCameraDistance);
        }

        private void Update()
        {
            ApplyGyro();
            if (drivingMode) return;
            HandlePinchZoom();
        }

        /// <summary>Rotazione 360° con il giroscopio. Yaw (asse Y del
        /// device): giri il telefono a destra/sinistra (come il busto)
        /// -> guardi intorno. Pitch (asse X): alzi o abbassi il telefono
        /// -> guardi cielo/terreno, anche in diagonale (i due assi si
        /// sommano). Effetto proporzionale-posizionale come il drag:
        /// nessun salto, si accumula mentre muovi il telefono e resta
        /// dov'e' quando lo fermi. Se una delle due rotazioni fosse
        /// invertita, cambia il segno della costante corrispondente.</summary>
        private void ApplyGyro()
        {
            if (!_gyroEnabled || !SystemInfo.supportsGyroscope) return;
            Vector3 rate;
            try { rate = Input.gyro.rotationRateUnbiased; }
            catch (System.Exception) { return; }

            float rY = Mathf.Abs(rate.y) < GyroDeadzone ? 0f : rate.y;
            _gyroRateEma = Mathf.Lerp(_gyroRateEma, rY, GyroSmoothing);
            // rotationRate e' una velocita' angolare instantanea: a 5 fps un
            // delta da 400 ms la moltiplica per 8 e la camera ruota a scatti,
            // senza mai raggiungere il target (camGap alto anche da fermi).
            float gyroDt = Mathf.Min(Time.deltaTime, PlayerController.MaxFrameStepS);
            // GIORNO 9: il giroscopio NON tocca yaw (base di marcia del
            // joystick), ma solo l'offset di vista. Prima ruotava la spring
            // arm e di conseguenza anche la direzione del personaggio:
            // muovendo il telefono il player cambiava strada da solo.
            _gyroYawOffset -= _gyroRateEma * GyroSensitivity * gyroDt;
            _gyroYawOffset = Mathf.Repeat(_gyroYawOffset + 180f, 360f) - 180f;

            float rX = Mathf.Abs(rate.x) < GyroDeadzone ? 0f : rate.x;
            _gyroPitchEma = Mathf.Lerp(_gyroPitchEma, rX, GyroSmoothing);
            _gyroPitchOffset = Mathf.Clamp(
                _gyroPitchOffset - _gyroPitchEma * GyroSensitivity * gyroDt,
                -GyroPitchMax, GyroPitchMax);
        }

        public void SetGyroEnabled(bool on)
        {
            _gyroEnabled = on && SystemInfo.supportsGyroscope;
            SetGyroSensor(_gyroEnabled);
            OsmDiag.Log("[Camera][Gyro] set -> " + _gyroEnabled);
        }

        private void SetGyroSensor(bool on)
        {
            try { Input.gyro.enabled = on; }
            catch (System.Exception) { _gyroEnabled = false; }
            _gyroRateEma = 0f;
            _gyroPitchEma = 0f;
            _gyroPitchOffset = 0f;
            _gyroYawOffset = 0f;
        }

        private void HandlePinchZoom()
        {
            if (Input.touchCount == 2)
            {
                if (MinimapHud.SwallowPinch()) return;
                if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen)
                    return;

                Touch t0 = Input.GetTouch(0);
                Touch t1 = Input.GetTouch(1);
                float currDist = Vector2.Distance(t0.position, t1.position);

                if (t0.phase == TouchPhase.Began || t1.phase == TouchPhase.Began)
                {
                    pinchStartDistance = currDist;
                    return;
                }

                float diff = pinchStartDistance - currDist;
                if (Mathf.Abs(diff) > 10f)
                {
                    ApplyZoom(diff * 0.02f);
                    pinchStartDistance = currDist;
                }
            }
        }

        // ── Diagnostica camminata ([WalkDiag] di PlayerController) ────────────
        /// <summary>Distanza residua tra camera e punto desiderato dopo SmoothDamp.</summary>
        public float LastSmoothGap { get; private set; }
        /// <summary>Distanza camera ↔ player.</summary>
        public float LastTargetDistance { get; private set; }
        /// <summary>True se il raycast anti-ostacolo ha tirato dentro la camera.</summary>
        public bool LastObstaclePull { get; private set; }

        private void LateUpdate()
        {
            if (target == null) return;
            if (_cachedTarget != target)
            {
                RefreshHeroReferences();
                ApplyModelVisibility();
            }
            // L'hero puo' essere instanziato dopo lo Start (chunk asincroni):
            // riprovo una volta al secondo finche' non lo trovo, senza costi
            // per frame e senza toccare il giogo di fisica/IK.
            if (_heroRig == null && Time.time >= _nextHeroSearch)
            {
                _nextHeroSearch = Time.time + 1f;
                RefreshHeroReferences();
                ApplyModelVisibility();
            }
            // GIORNO 5: tolgo il bob del frame precedente PRIMA di SmoothDamp.
            // Altrimenti la camera "insegue" il proprio bob, velocity si sporca
            // e LastSmoothGap non misura piu' il ritardo reale della camera.
            transform.position -= _bobApplied;
            _bobApplied = Vector3.zero;
            LastObstaclePull = false;
            float dt = Mathf.Min(Time.deltaTime, PlayerController.MaxFrameStepS);

            // In guida la camera resta SEMPRE in terza persona alta dietro
            // l'auto (vede il veicolo da sopra): gli override indoor di
            // concessionaria/officina/garage e dei negozi NON devono tirare la
            // camera vicino mentre si guida, altrimenti sembra di passare in
            // prima persona. Gli override si applicano solo a piedi.
            if (!drivingMode)
            {
                if (indoorOverrideActive) InitializeIndoorBlend();
                UpdateIndoorBlend(dt);
            }

            // blend prima/terza persona (solo a piedi): la camera scivola
            // dolcemente alla testa del player.
            float fpTarget = (firstPerson && !drivingMode) ? 1f : 0f;
            _fpBlend = Mathf.Lerp(_fpBlend, fpTarget, dt * 6f);
            if (_fpBlend < 0.01f && fpTarget == 0f) _fpBlend = 0f;

            float d = drivingMode ? driveDistance : distance;
            float h = drivingMode ? driveHeight : height;
            float p = drivingMode ? drivePitch : pitch;

            if (!drivingMode)
            {
                bool blendingIn = indoorOverrideActive && indoorDistance > indoorTargetDistance + 0.05f;
                bool blendingOut = !indoorOverrideActive && indoorDistance > 0f &&
                    indoorDistance < distance - 0.05f;
                if (blendingIn || blendingOut)
                {
                    d = indoorDistance;
                    h = indoorHeight;
                    p = indoorPitch;
                }
            }

            Vector3 offset = Quaternion.Euler(p, yaw, 0f) * new Vector3(0f, h, -d);
            Vector3 desired = target.position + offset;
            float dist = offset.magnitude;

            if (_fpBlend < 0.5f)
            {
                // GIORNO 9: sphere-cast dal torace, non un raggio sin sottile
                // partito 5 m sopra il player (bucava muri di taglio e cornici).
                // Un solo query/frame, buffer riusato, hit PIU' VICINO (regola
                // del Mandato: mai il piu' alto/ultimo di RaycastAll).
                Vector3 armDir = offset.normalized;
                Vector3 start = target.position + Vector3.up * ArmPivotHeight;
                int mask = ~(1 << 8);   // layer 8 = edifici, invariato
                int found = Physics.SphereCastNonAlloc(start, ArmRadius, armDir,
                    _armHits, dist + ArmRadius, mask, QueryTriggerInteraction.Ignore);
                RaycastHit best = default(RaycastHit);
                float bestDist = float.MaxValue;
                bool blocked = false;
                for (int i = 0; i < found && i < ArmHitSlots; i++)
                {
                    RaycastHit hitInfo = _armHits[i];
                    if (hitInfo.collider == null || hitInfo.distance <= 0f) continue;
                    if (hitInfo.collider.transform == target) continue;   // self-hit
                    // RIFLESSO UOVA: in AR indoor la camera non taglia l'uovo
                    // del cassetto. La distanza e' sola lettura, NON rivela
                    // l'uovo e NON tocca il mini-gioco.
                    if (City.Interior.ContainerEgg.DistanceToChosen(hitInfo.point) <= EggArmClearance)
                        continue;
                    if (hitInfo.distance < bestDist)
                    {
                        bestDist = hitInfo.distance;
                        best = hitInfo;
                        blocked = true;
                    }
                }
                if (blocked)
                {
                    float hitDist = Vector3.Distance(target.position, best.point);
                    if (hitDist > MinCameraDistance + 1f)
                    {
                        desired = best.point - armDir * 0.5f;
                        LastObstaclePull = true;
                        // Audit: la camera e' stata tirata dentro da un ostacolo
                        // (radice di "camera in prima persona" dentro un edificio).
                        if (hitDist < dist * 0.8f && Time.time >= _nextPushLog)
                        {
                            _nextPushLog = Time.time + 15f;
                            OsmDiag.Log("[Audit] Camera tirata dentro: " +
                                target.name + " dist " + dist.ToString("F1") + " -> " +
                                hitDist.ToString("F1") + "m hit=" + best.collider.name);
                        }
                    }
                }
            }

            Vector3 toDesired = desired - target.position;
            if (toDesired.magnitude < MinCameraDistance)
            {
                desired = target.position + toDesired.normalized * MinCameraDistance;
            }

            Vector3 firstPos = target.position + Vector3.up * FirstPersonEyeHeight();
            Vector3 finalDesired = Vector3.Lerp(desired, firstPos, _fpBlend);

            Vector3 smoothed = Vector3.SmoothDamp(transform.position, finalDesired,
                ref velocity, smoothTime);
            if (Vector3.Distance(smoothed, finalDesired) > MaxSmoothGap)
            {
                smoothed = finalDesired;
                velocity = Vector3.zero;
            }
            transform.position = smoothed;
            // Diagnostiche PRIMA del bob: devono misurare il ritardo della
            // camera rispetto al punto desiderato, non il tremolio del passo.
            LastSmoothGap = Vector3.Distance(transform.position, finalDesired);
            LastTargetDistance = Vector3.Distance(transform.position, target.position);

            // GIORNO 5 - HEAD BOB: la fase viene dal CharacterWalker, quindi
            // e' la stessa del passo audio. Peso azzerato a piedi fermi, in
            // guida e graduale in prima persona (dove il bob e' piu' visibile).
            float bobTarget = 0f;
            if (_headBobEnabled && !drivingMode && _walker != null)
                // Soglia 0.6 = stessa del rilevamento passi: sotto quella il
                // walker azzera StepPhase e il bob non deve restare "appeso".
                bobTarget = Mathf.Clamp01((_walker.Speed - 0.6f) / 1.6f) *
                            (1f - 0.35f * _fpBlend);
            _bobWeight = Mathf.Lerp(_bobWeight, bobTarget, Mathf.Min(1f, dt * 9f));
            if (_bobWeight > 0.001f)
            {
                float phase = _walker.StepPhase * BobTwoPi;
                Vector3 localBob = new Vector3(
                    Mathf.Cos(phase) * bobHorizontalM,
                    Mathf.Sin(phase) * bobVerticalM, 0f) * _bobWeight;
                // Solo yaw: l'offset resta orizzontale/verticale e non viene
                // ruotato dal pitch della camera (ne' dal giroscopio).
                _bobApplied = Quaternion.Euler(0f, yaw, 0f) * localBob;
                transform.position += _bobApplied;
            }

            float targetYaw = yaw + (_gyroEnabled ? _gyroYawOffset : 0f);
            float viewPitch = Mathf.Lerp(p, 0f, _fpBlend)
                + (_gyroEnabled ? _gyroPitchOffset : 0f);
            // GIORNO 9: smoothing della ROTAZIONE separato da quello della
            // posizione (che resta SmoothDamp 0,12 s + snap 0,35 m). La vista
            // segue a 12/s, piu' rapida del corpo, senza trascinare la spring
            // arm: posizione e rotazione non si inseguono piu' a vicenda.
            if (_snapView)
            {
                _viewYaw = targetYaw;
                _viewPitch = viewPitch;
                _snapView = false;
            }
            else
            {
                float rotBlend = Mathf.Clamp01(dt * RotationFollow);
                _viewYaw = Mathf.LerpAngle(_viewYaw, targetYaw, rotBlend);
                _viewPitch = Mathf.Lerp(_viewPitch, viewPitch, rotBlend);
            }
            float bobRoll = _bobWeight > 0.001f
                ? Mathf.Cos(_walker.StepPhase * BobTwoPi) * bobRollDeg * _bobWeight
                : 0f;
            transform.rotation = Quaternion.Euler(_viewPitch, _viewYaw, -bobRoll);
        }
    }
}
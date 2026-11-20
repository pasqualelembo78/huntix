using System.Collections;
using UnityEngine;
using City.Afterlife;
using City.OSM;

namespace City.Player
{
    [RequireComponent(typeof(CharacterController))]
    public class PlayerController : MonoBehaviour
    {
        /// <summary>Riferimento statico al player della scena (uno solo).</summary>
        public static PlayerController Instance { get; private set; }

        // camminata piu' naturale: ridotta da 4 a 3.2 m/s per non far sembrare
        // il personaggio di fretta durante il passo (la cadenza dell'
        // animazione resta coerente col passo reale).
        public float walkSpeed = 3.2f;
        public float runSpeed = 7.5f;
        public float turnSpeed = 12f;
        public float gravity = -25f;
        public float jumpForce = 9f;
        public float sprintBoost = 2.2f;
        public float sprintDuration = 1.6f;
        public float jumpCooldown = 0.9f;

        // ── Meccaniche per regno (3.4) ──────────────────────────
        // Inferno   : doppio salto + dash
        // Paradiso  : volo (thrust/pitch), gravita' ridotta
        public float doubleJumpForce = 8f;
        public float dashSpeed = 18f;
        public float dashDuration = 0.25f;
        public float dashCooldown = 1.2f;
        public float flightUpSpeed = 10f;
        public float flightGravityScale = 0.25f;

        // ── Pendenza / caduta dal dislivello (altitudine DEM) ─────
        // Inclinazione massima del corpo (gradi) per effetto della pendenza:
        // si "sbilancia" su un dosso ma non cade mai da fermo.
        private const float MaxBodyInclineDeg = 25f;
        // Oltre questa pendenza (gradi) il personaggio SCIVOLA: una salita/
        // discesa troppo ripida lo fa cadere per terra (knockdown) come nella
        // realta' su un pendio ripido.
        private const float SlipSlopeDeg = 60f;
        // Pausa minima tra un episodio di scivolata e il successivo (s).
        private const float SlipCooldown = 3f;
        private float _lastSlipAt = -10f;

        // ── Binding professionale al terreno (GroundSnapper, condiviso NPC) ──
        private GroundFollowState _groundFollow;
        private float _nextGroundLogAt;
        private float _lastGroundedAt = -10f;   // Giorno 7: finestra del magnete in discesa
        // FIX B: l'Animator deve mostrare il salto SOLO quando il salto e'
        // stato davvero chiesto. Senza questo, cadere da un cordolo dopo la
        // finestra del magnete faceva partire la clip Jump (hasExitTime=0).
        private float _jumpAnimHoldUntil = -10f;
        private const float JumpAnimHoldS = 0.8f;
        private const float GroundFollowClearance = 0.02f;

        /// <summary>Passo massimo di integrazione del movimento (s). Durante la
        /// costruzione dei chunk il frame puo' salire a 400 ms: un solo
        /// controller.Move() di 1-2 m fa "inciampare" il player sui cordoli e
        /// lascia la camera indietro. Tutto il movimento viene integrato a
        /// passi di al massimo 50 ms, ripartendo il delta reale su piu' step.
        /// Condiviso con CameraRig per tenere giroscopio e smoothing coerenti.</summary>
        public const float MaxFrameStepS = 0.05f;
        // Oltre 250 ms un delta non e' piu' tempo di gioco ma uno stallo
        // (GC, shader compile, swap): non lo integriamo tutto in una volta.
        private const float MaxFrameDtS = 0.25f;

        // GIORNO 4 - Camminata da gioco serio: la velocita' non e' un interruttore
        // (input -> subito 3.2 m/s) ma uno smorzamento con tempi DEDICATI.
        // Partenza pronta (80 ms), arresto leggermente piu' pesante (120 ms):
        // il personaggio scatta quando premi e si ferma con un po' di inerzia,
        // senza pero' scivolare come sul ghiaccio. Valori in secondi, usati come
        // smoothTime di Mathf.SmoothDamp (frame-rate independent, a differenza
        // del vecchio MoveTowards a 10 m/s^2 che a 10 fps partiva in 0,3 s).
        private const float AccelSmoothS = 0.08f;
        private const float BrakeSmoothS = 0.12f;
        // Deadzone del joystick, condivisa fra il gate di movimento e la rampa
        // di velocita': cosi' all'uscita dal centro la Speed parte da 0 invece
        // di saltare a walkSpeed (3,2 m/s) in un frame.
        private const float MoveDeadzoneSqr = 0.02f;
        // Tetto di sicurezza per la velocita' MISURATA passata all'Animator:
        // copre sprint (7.5*2.2=16.5) e dash (18) con un margine, ma taglia
        // i picchi dovuti a un teleport/respawn (le gambe non impazziscono).
        private const float MaxRealAnimSpeed = 25f;

        // ── Diagnostica camminata ([WalkDiag], una riga/s su AppLog Android) ──
        private float _walkDiagNextAt;
        private int _followCorrections;
        private float _followMaxErr;
        private string _followLastSource = "";

        // GIORNO 2 / REVIEW: ultima superficie di appoggio rilevata dal follower.
        // A differenza di _followLastSource NON viene azzerata dal log 1/s.
        // Teniamo i due valori SEPARATI: la concatenazione "sorgente/nome"
        // avveniva ogni frame in LateUpdate (2-3 stringhe nuove a 60 fps ->
        // GC pressure). Qui solo assegnazioni di riferimento, zero allocazioni;
        // la stringa viene composta dalla telemetria una volta ogni 2 s.
        private GroundSource _surfaceSource = GroundSource.None;
        private string _surfaceName = "";
        /// <summary>Sorgente dell'ultima superficie calpestabile (diagnostica).</summary>
        public GroundSource LastGroundSource { get { return _surfaceSource; } }
        /// <summary>Nome dell'ultima superficie calpestabile (diagnostica).</summary>
        public string LastGroundName { get { return _surfaceName; } }
        private float _lastTargetSpeed;
        private float _lastDirDot;

        /// <summary>INTERRUTTORE DI VERIFICA per il follower verticale.
        /// Messo a `false` il player si affida SOLO al CharacterController
        /// (gravita' + isGrounded), cioe' esattamente il comportamento del ramo
        /// `unity`, che funzionava: nessun sistema scrive piu' la Y del player.
        ///
        /// Serve per la prova A/B sul device: con il follower OFF il player si
        /// affida solo al CharacterController (comportamento del ramo `unity`).
        /// Il valore e' in PlayerPrefs e si commuta a runtime dal pulsante nel
        /// viewer dei log Android ("Follower ON/OFF"), senza ricompilare.
        /// Ogni riga [WalkDiag] nel log riporta lo stato corrente.
        /// Nota: in modalita' piatta (`CityConfig.WorldHeights == Flat`) il
        /// follower dovrebbe essere quasi un no-op (deadzone 2 cm): se l'A/B
        /// da' molta differenza, quello e' il bug.</summary>
        public const string GroundFollowPrefKey = "huntix_ground_follow";
        private static bool? _groundFollowCached;

        /// <summary>Stato corrente, letto da PlayerPrefs una volta e tenuto in cache.</summary>
        public static bool GroundFollowEnabled
        {
            get
            {
                if (!_groundFollowCached.HasValue)
                    _groundFollowCached = PlayerPrefs.GetInt(GroundFollowPrefKey, 1) == 1;
                return _groundFollowCached.Value;
            }
        }

        /// <summary>Commuta il follower a runtime e ritorna il nuovo stato.</summary>
        public static bool ToggleGroundFollow()
        {
            bool next = !GroundFollowEnabled;
            _groundFollowCached = next;
            PlayerPrefs.SetInt(GroundFollowPrefKey, next ? 1 : 0);
            PlayerPrefs.Save();
            return next;
        }

        private int airJumpCount;
        private const int MaxAirJumps = 1;

        private float dashRemain;
        private float canDashAt;
        private Vector3 dashDirection;

        private float knockbackRemain;
        private Vector3 knockbackVel;

        private bool flying;
        private int flightVertical; // +1 sali, -1 scendi, 0 neutro (in volo)

        private float canJumpAt;
        private float sprintRemain;

        private CharacterController controller;
        private Animator animator;
        private CharacterWalker walker;
        private Vector2 moveInput;
        private Vector3 velocity;
        // Ultima direzione orizzontale richiesta: a joystick rilasciato il
        // moveInput torna a zero e move si azzera, quindi senza questo la
        // velocita' orizzontale tronca a zero all'istante mentre currentSpeed
        // (usato dall'animazione) decade ancora -> fermata secca con le
        // gambe che "camminano a posto".
        private Vector3 moveDirection;
        private float currentSpeed;
        // GIORNO 4: stato interno di SmoothDamp (velocita' di variazione di
        // currentSpeed) e velocita' ORIZZONTALE effettivamente prodotta da
        // controller.Move. Quest'ultima e' quella che comanda l'Animator:
        // usare la velocita' richiesta faceva "camminare a posto" contro i muri.
        private float _speedSmoothVel;
        private Vector3 _moveStartPos;
        private float _realHorizontalSpeed;
        private float _realHorizontalMeters;

        // telemetria camminata per le missioni WalkDistance: accumula i metri
        // percorsi e li notifica a ogni metro compiuto (MissionManager mai
        // aggiornato prima: le missioni "Cammina X metri" restavano al 0)
        private float walkTelemetry;

        public bool IsMoving { get; private set; }

        // Blocco input durante la sequenza di morte (death lock): il player
        // resta fermo mentre il corpo crolla; la gravita' continua a valere
        // (utile per la caduta dal palazzo).
        private bool inputLocked;
        public void SetInputLocked(bool locked)
        {
            inputLocked = locked;
            if (locked) Stop();
        }

        /// <summary>True mentre il player e' in modalita' volo (Paradiso).</summary>
        public bool IsFlying { get { return flying; } }

        /// <summary>Regno afterlife corrente (INFERNO/PURGATORIO/PARADISO)
        /// oppure null quando siamo in citta' (arena non attiva). Guida le
        /// meccaniche extra del player.</summary>
        public AfterlifeRealm? Realm
        {
            get
            {
                var mgr = RealmSceneManager.Instance;
                if (mgr == null || mgr.ActiveRealm == null) return null;
                return mgr.ActiveRealmId;
            }
        }

        public void SetMoveInput(Vector2 input)
        {
            if (inputLocked) return;
            moveInput = input;
        }

        public void Stop()
        {
            moveInput = Vector2.zero;
            velocity = Vector3.zero;
            currentSpeed = 0f;
            // Azzera anche lo slancio di SmoothDamp: senza questo, un Stop()
            // seguito da un nuovo input ripartirebbe con la velocita' residua
            // del frame precedente (scatto iniziale).
            _speedSmoothVel = 0f;
            _realHorizontalSpeed = 0f;
            _realHorizontalMeters = 0f;
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            City.OSM.OsmDiag.Log("[PlayerController][Awake] inizio: GO=" + gameObject.name +
                " children=" + transform.childCount +
                " cc=" + (GetComponent<CharacterController>() != null));
            // Hero rig (Remy): installa il modello del giocatore moderno al
            // posto del characterMedium legacy (no-op se il prefab manca).
            PlayerHeroRig.Ensure(gameObject);
            City.OSM.OsmDiag.Log("[PlayerController][Awake] dopo Ensure: rigAttivo=" +
                PlayerHeroRig.ActiveFor(gameObject));
            controller = GetComponent<CharacterController>();
            // Pendenza massima percorribile: default Unity 45 gradi fa
            // murare il player sui pendii reali del DEM (scivola di lato
            // invece di salire). 55 e' il limite dopo il quale scatta la
            // scivolata/knockdown pedagogica in KnockdownOnSteepSlope.
            controller.slopeLimit = 55f;
            // Scalino massimo sormontabile: 0.4 m supera cordoli/marciapiedi
            // e traverse senza inciampare (default 0.3).
            controller.stepOffset = 0.4f;
            // Skin: un filo piu' sottile del default (0.08) attacca meglio ai
            // collider (isGrounded piu' stabile sui giunti di chunk) senza
            // aumentare gli stick sui bordi verticali.
            controller.skinWidth = 0.05f;
            animator = GetComponentInChildren<Animator>();
            City.OSM.OsmDiag.Log("[PlayerController][Awake] animator=" +
                (animator != null ? animator.gameObject.name : "NULL") +
                " children=" + transform.childCount);
            walker = CharacterWalker.AttachIfNeeded(gameObject);
            City.Player.GroundDiag.Ensure(gameObject);
            City.OSM.OsmDiag.Log("[PlayerController][Awake] walker=" +
                (walker != null ? "attaccato a " + walker.gameObject.name : "NULL"));
            // La skin del profilo vale per characterMedium/NPC; l'hero rig usa
            // le proprie texture Mixamo e non va rivestito.
            if (GetComponent<PlayerAppearance>() == null &&
                !PlayerHeroRig.ActiveFor(gameObject))
                gameObject.AddComponent<PlayerAppearance>();
            try { City.Environment.AgeSystem.ApplyTo(gameObject); }
            catch (System.Exception) { }
        }

        /// <summary>Cambia skin del personaggio a runtime (dal profilo Android).</summary>
        public void ApplySkin(string skinName)
        {
            var app = GetComponent<PlayerAppearance>();
            if (app != null) app.Apply(skinName);
        }

        private void Update()
        {
            // mini-gioco di cattura uova attivo: il player e' congelato
            if (City.Economy.EggCaptureMinigame.Instance != null &&
                City.Economy.EggCaptureMinigame.Instance.IsActive)
                return;

            // Delta "di gioco": gli stalli lunghi (build chunk, compilazione
            // shader, GC) non devono tradursi in un balzo del personaggio.
            float frameDt = Mathf.Min(Time.deltaTime, MaxFrameDtS);
            int moveSteps = Mathf.Clamp(Mathf.CeilToInt(frameDt / MaxFrameStepS), 1, 6);
            float stepDt = frameDt / moveSteps;

            Vector3 move = Vector3.zero;
            float targetSpeed = 0f;

            if (moveInput.sqrMagnitude > MoveDeadzoneSqr)
            {
                CameraRig rig = CameraRig.Instance;
                Vector3 forward = rig != null
                    // GIORNO 9: base di marcia = solo yaw del drag, non la
                    // vista smorzata ne' il giroscopio.
                    ? Vector3.ProjectOnPlane(rig.MovementBasis * Vector3.forward, Vector3.up).normalized
                    : transform.forward;
                Vector3 right = Vector3.Cross(Vector3.up, forward);
                Vector3 dir = forward * moveInput.y + right * moveInput.x;
                // FIX: la magnitudo NON deve moltiplicare anche lo spostamento.
                // forward/right sono ortonormali, quindi |dir| = |stick|: senza
                // questa normalize la velocita' reale era |stick|*currentSpeed
                // (doppia magnitudo) e l'Animator, che riceve la velocita'
                // misurata, restava vicino a Idle anche a mezza corsia.
                if (dir.sqrMagnitude > 0.0001f) dir.Normalize();

                move = dir;
                moveDirection = dir;
                // Diagnostica: quanto la direzione di marcia combacia con la camera.
                if (rig != null)
                {
                    Vector3 camFwd = Vector3.ProjectOnPlane(
                        rig.MovementBasis * Vector3.forward, Vector3.up);
                    if (camFwd.sqrMagnitude > 0.0001f && dir.sqrMagnitude > 0.0001f)
                        _lastDirDot = Vector3.Dot(dir.normalized, camFwd.normalized);
                }
                // Rampa continua dalla deadzone al fondo corsa: a meta' stick
                // la velocita' reale e' ~3,2 m/s, esattamente il threshold
                // Walk del BlendTree; a fondo e' 7,5 (Run). Nessun salto
                // 0 -> 3,2 appena il dito esce dal centro.
                targetSpeed = runSpeed * Mathf.InverseLerp(
                    Mathf.Sqrt(MoveDeadzoneSqr), 1f, moveInput.magnitude);

                // Sospensioni del corpo: si inclina lievemente seguendo il
                // pendio (pitch/roll) ma mai oltre MaxBodyInclineDeg, cosi' su
                // un dosso si "sbilancia" senza mai cadere all'indietro.
                Vector3 bodyUp = TileElevation.SlopeUpAtWorld(
                    transform.position, MaxBodyInclineDeg);
                Quaternion targetRotation = Quaternion.LookRotation(dir, bodyUp);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRotation, turnSpeed * frameDt);
            }

            if (sprintRemain > 0f) targetSpeed *= sprintBoost;
            _lastTargetSpeed = targetSpeed; // diagnostica [WalkDiag]

            // GIORNO 4: smorzamento con tempi separati. SmoothDamp e'
            // frame-rate independent e gestisce gli stalli (frameDt e' gia'
            // clampato a MaxFrameDtS) senza il balzo del vecchio rate fisso.
            float smoothTime = targetSpeed > currentSpeed + 0.001f
                ? AccelSmoothS : BrakeSmoothS;
            currentSpeed = Mathf.SmoothDamp(currentSpeed, targetSpeed,
                ref _speedSmoothVel, smoothTime, Mathf.Infinity, frameDt);
            if (currentSpeed < 0f)
            {
                currentSpeed = 0f;
                _speedSmoothVel = 0f;
            }
            float capSpeed = runSpeed * sprintBoost;
            if (currentSpeed > capSpeed)
            {
                currentSpeed = capSpeed;
                _speedSmoothVel = 0f;
            }
            // IsMoving viene ricalcolato DOPO controller.Move() dalla velocita' reale:
            // qui il vecchio valore e' solo un fallback per il frame in cui
            // il CharacterController e' disabilitato (early return sotto).
            IsMoving = currentSpeed > 0.05f;

            if (!controller.enabled) return;

            // GIORNO 4: punto di partenza per misurare lo spostamento REALE.
            // Viene preso qui (non a inizio Update) per escludere qualunque
            // altra scrittura del transform e includere solo controller.Move.
            _moveStartPos = transform.position;

            // Velocita' orizzontale: default camminata, override se dash attivo.
            // moveDirection ritiene l'ultima direzione: al rilascio del
            // joystick currentSpeed cala e il player decelera in modo
            // naturale invece di fermarsi di colpo.
            // NB: si assegnano SOLO x/z, mai y: velocity.y (gravita' o impulso
            // di DoJump) deve sopravvivere fino a controller.Move, altrimenti
            // il salto premuto da fermi verrebbe azzerato a ogni frame.
            Vector3 horiz = moveDirection * currentSpeed;
            velocity.x = horiz.x;
            velocity.z = horiz.z;

            if (flying && Realm == AfterlifeRealm.PARADISO)
            {
                for (int step = 0; step < moveSteps; step++)
                    ApplyFlight(move, stepDt);
            }
            else
            {
                // Substep: la distanza totale resta velocity * frameDt, ma ogni
                // sweep del CharacterController e' corto al massimo 50 ms.
                for (int step = 0; step < moveSteps; step++)
                {
                    if (controller.isGrounded && velocity.y < 0f) velocity.y = -1f;
                    float g = (Realm == AfterlifeRealm.PARADISO) ? gravity * flightGravityScale : gravity;
                    velocity.y += g * stepDt;
                    if (controller.isGrounded)
                    {
                        airJumpCount = 0;
                        flying = false;
                        flightVertical = 0;
                    }

                    // Dash orizzontale (Inferno): sovrascrive il componente orizzontale
                    // con l'impulso direzionale per la sua breve durata.
                    if (dashRemain > 0f)
                    {
                        dashRemain = Mathf.Max(0f, dashRemain - stepDt);
                        velocity.x = dashDirection.x * dashSpeed;
                        velocity.z = dashDirection.z * dashSpeed;
                    }

                    // Knockback (pericoli Inferno): spinta orizzontale breve che
                    // sovrascrive la velocita' per far spiccare il salto/scivolata.
                    if (knockbackRemain > 0f)
                    {
                        knockbackRemain = Mathf.Max(0f, knockbackRemain - stepDt);
                        velocity.x = knockbackVel.x;
                        velocity.z = knockbackVel.z;
                    }

                    controller.Move(velocity * stepDt);
                }

                // Pendio troppo ripido: il personaggio scivola e cade a terra
                // (knockdown + contusione), come su una montagna impervia. Dopo
                // pochi secondi si rialza. Cooldown per non cascare in loop.
                if (controller.isGrounded && !_slipPending &&
                    Time.time >= _lastSlipAt + SlipCooldown)
                {
                    Vector3 n = TileElevation.SurfaceNormalWorld(transform.position);
                    if (Vector3.Angle(Vector3.up, n) >= SlipSlopeDeg)
                    {
                        _lastSlipAt = Time.time;
                        KnockdownOnSteepSlope();
                    }
                }
            }

            // GIORNO 4 - VELOCITA' REALE: all'Animator deve arrivare lo
            // spostamento effettivo, non l'input grezzo ne' currentSpeed
            // (che e' la richiesta GIA' smorzata). Se il CharacterController
            // sbatte contro un muro la richiesta resta alta ma il player e'
            // fermo: passare currentSpeed faceva camminare le gambe a posto
            // (il classico moonwalk). Misuriamo quindi lo spostamento
            // orizzontale prodotto da controller.Move in questo frame.
            Vector3 moved = transform.position - _moveStartPos;
            _realHorizontalMeters = Mathf.Sqrt(moved.x * moved.x + moved.z * moved.z);
            _realHorizontalSpeed = Mathf.Clamp(
                _realHorizontalMeters / Mathf.Max(0.0001f, frameDt),
                0f, MaxRealAnimSpeed);
            // "In movimento" e' lo spostamento reale, non la richiesta:
            // multiplayer e sistema energia non vedano un player fermo
            // come se stesse correndo contro una parete.
            IsMoving = _realHorizontalSpeed > 0.05f;

            if (walker != null)
            {
                walker.SetSpeed(_realHorizontalSpeed);
                // Giorno 8 + FIX B: il controller Mixamo entra in Jump con
                // `IfNot IsGrounded` e hasExitTime=0. Lo step assist e le
                // cadute dai cordoli facevano quindi partire la clip di salto
                // senza che l'utente salitasse. Ora l'Animator e' "in aria"
                // SOLO per un salto davvero richiesto (DoJump), fino a
                // JumpAnimHoldS o fino all'atterraggio. La finestra del
                // magnete del Giorno 7 resta per la FISICA, non per l'anim.
                bool animAir = !controller.isGrounded &&
                    (velocity.y > 0.5f || Time.time < _jumpAnimHoldUntil);
                walker.SetGrounded(controller.enabled && !animAir);
            }
            if (sprintRemain > 0f) sprintRemain = Mathf.Max(0f, sprintRemain - frameDt);
            TrackWalkDistance();
            LogWalkDiagnostics();
        }

        /// <summary>Una riga al secondo nella scatola nera Android: riunisce le
        /// tre misure della camminata (follower, camera, input/velocita') cosi'
        /// si possono confrontare sullo stesso intervallo di tempo.</summary>
        private void LogWalkDiagnostics()
        {
            if (Time.time < _walkDiagNextAt) return;
            _walkDiagNextAt = Time.time + 1f;

            var inv = System.Globalization.CultureInfo.InvariantCulture;
            CameraRig rig = CameraRig.Instance;
            City.OSM.OsmDiag.Log("[WalkDiag] follow=" + (GroundFollowEnabled ? "ON" : "OFF") +
                " corr/s=" + _followCorrections +
                " maxErr=" + _followMaxErr.ToString("F3", inv) +
                " src=" + (string.IsNullOrEmpty(_followLastSource) ? "-" : _followLastSource) +
                " in=" + moveInput.magnitude.ToString("F2", inv) +
                " tgt=" + _lastTargetSpeed.ToString("F2", inv) +
                " cur=" + currentSpeed.ToString("F2", inv) +
                // GIORNO 4: "cur" e' la richiesta smorzata, "real" lo
                // spostamento misurato. Se real << cur il player e' contro
                // un ostacolo (e l'Animator ora non cammina piu' a posto).
                " real=" + _realHorizontalSpeed.ToString("F2", inv) +
                " dot=" + _lastDirDot.ToString("F2", inv) +
                " camGap=" + (rig != null ? rig.LastSmoothGap : 0f).ToString("F2", inv) +
                " camDist=" + (rig != null ? rig.LastTargetDistance : 0f).ToString("F2", inv) +
                " camPush=" + (rig != null && rig.LastObstaclePull ? "Y" : "N") +
                // RIFLESSO UOVA (sola lettura): distanza dal contenitore scelto
                // nell'intero corrente. Non rivela l'uovo, non tocca il
                // mini-gioco: serve solo a leggere l'avvicinamento al cassetto
                // sulla stessa riga in=/tgt=/cur=/real= (MaxValue fuori interni).
                " eggD=" + City.Interior.ContainerEgg.DistanceToChosen(
                    transform.position).ToString("F1", inv));

            _followCorrections = 0;
            _followMaxErr = 0f;
            _followLastSource = "";
        }

        /// <summary>Caduta su un pendio troppo ripido: il personaggio scivola e
        /// cade per terra (brevi contusioni) come nella realta'. Blocca
        /// l'input per un attimo e mostra l'avviso del ricovero. Dopo pochi
        /// secondi e' di nuovo in piedi. Tutto cosmetico/pedagogico: il
        /// CharacterController resta abilitato e il player non "muore".</summary>
        private void KnockdownOnSteepSlope()
        {
            if (_slipPending) return;
            _slipPending = true;

            // rinculo all'indietro sulla verticale del pendio
            Vector3 n = TileElevation.SurfaceNormalWorld(transform.position);
            Vector3 down = new Vector3(n.x, 0f, n.z).normalized;
            if (down.sqrMagnitude < 0.001f) down = -Vector3.forward;
            controller.Move(down * 0.6f);
            velocity.y = 0f;

            var ui = City.Game.Instance != null ? City.Game.Instance.ui : null;
            if (ui != null)
            {
                ui.ShowToast("⚠ Pendio troppo ripido! Hai scivolato e sei caduto. " +
                    "Contusioni lievi... se fai peggio finisci in ospedale!");
            }

            // "rinco": input congelato per 1.2 s, poi si e' di nuovo in piedi.
            // Coroutine ospitata dal player (Game.Instance puo' essere nullo
            // durante i cambi regno/arena -> NRE).
            StartCoroutine(ClearSlip());
        }

        private bool _slipPending;
        private IEnumerator ClearSlip()
        {
            float t = 0f;
            while (t < 1.2f)
            {
                t += Time.unscaledDeltaTime;
                yield return null;
            }
            _slipPending = false;
        }

        /// <summary>Volo (Paradiso): thrust orizzontale con il joystick + salita/
        /// discesa con i pulsanti dedicati. Gravita' quasi assente per
        /// fluttuare; si perde quota solo scendendo attivamente.</summary>
        private void ApplyFlight(Vector3 move, float dt)
        {
            Vector3 forward = transform.forward;
            Vector3 right = transform.right;
            Vector3 dir = forward * moveInput.y + right * moveInput.x;
            // Stessa correzione del movimento a terra: in volo la magnitudo
            // non deve essere applicata due volte.
            if (dir.sqrMagnitude > 0.0001f) dir.Normalize();

            float up;
            if (flightVertical > 0) up = flightUpSpeed;
            else if (flightVertical < 0) up = -flightUpSpeed * 0.8f;
            else up = velocity.y * 0.9f; // mantiene la velocita' verticale attuale

            velocity = new Vector3(dir.x * currentSpeed, up, dir.z * currentSpeed);

            // galleggia: gravita' quasi assente, mai sopra -2 m/s di caduta
            velocity.y += gravity * flightGravityScale * 0.5f * dt;
            if (velocity.y < -2f) velocity.y = -2f;

            controller.Move(velocity * dt);
        }

        /// <summary>Salto: impulso verticale se il player e a terra (o a pochi
        /// centimetri da terra, vedi FeetProbeGrounded). In Inferno e'
        /// disponibile anche un secondo salto in aria (doppio salto).</summary>
        public void DoJump()
        {
            if (controller == null) return;
            // azioni bloccabili col death-lock (inputLocked): da morto non si
            // salta, pure con la cooldown scaduta
            if (inputLocked) { LogJumpReject("inputLocked"); return; }
            if (Time.unscaledTime < canJumpAt) { LogJumpReject("cooldown"); return; }

            bool groundJump = controller.isGrounded || FeetProbeGrounded();
            bool allowAirJump = Realm == AfterlifeRealm.INFERNO && airJumpCount < MaxAirJumps;

            if (groundJump)
            {
                canJumpAt = Time.unscaledTime + jumpCooldown;
                velocity.y = jumpForce;
                airJumpCount = 0;
            }
            else if (allowAirJump)
            {
                canJumpAt = Time.unscaledTime + jumpCooldown * 0.5f;
                velocity.y = doubleJumpForce;
                airJumpCount++;
            }
            else
            {
                LogJumpReject("non a terra (cc.isGrounded=" + controller.isGrounded +
                    ", sonda=" + FeetProbeGrounded() + ")");
                return;
            }
            // Solo un salto REALE mette l'Animator in aria: il Jump fantasma
            // sui marciapiedi non ha piu' modo di innescarsi.
            _jumpAnimHoldUntil = Time.time + JumpAnimHoldS;
        }

        /// <summary>Sonda del suolo sotto i piedi: il CharacterController
        /// .isGrounded a volte resta false per un frame o su superfici appena
        /// sotto la base della capsule (terreno OSM/DEM dinamico), e il salto
        /// veniva rifiutato senza motivo. La sonda parte SOTTO la base della
        /// capsule (mai la auto-hit della CityOSMWorld) e guarda al massimo
        /// GroundProbeTolerance m piu' in basso. Solo lettura: non sposta il
        /// player.</summary>
        private const float GroundProbeTolerance = 0.35f;
        private const float GroundProbeMargin = 0.05f;

        private bool FeetProbeGrounded()
        {
            if (controller == null || !controller.enabled) return false;
            try { Physics.SyncTransforms(); }
            catch (System.Exception) { }
            float feet = transform.position.y -
                (controller.height * 0.5f - controller.center.y);
            Vector3 origin = new Vector3(
                transform.position.x, feet - GroundProbeMargin, transform.position.z);
            RaycastHit hit;
            return Physics.Raycast(origin, Vector3.down, out hit,
                GroundProbeTolerance + GroundProbeMargin, ~0,
                QueryTriggerInteraction.Ignore);
        }

        /// <summary>Diagnostica: quando DoJump viene rifiutato senza saltare il
        /// motivo finisce in logcat Android (OsmDiag) per la scatola nera del
        /// problema "pulsante premuto ma nessun salto".</summary>
        private void LogJumpReject(string reason)
        {
            City.OSM.OsmDiag.Log("[Brookhaven][Jump] rifiutato: " + reason);
        }

        /// <summary>Sprint: breve scatto in piu, utile per superare tratti
        /// lunghi a piedi (ronda/corriere).</summary>
        public void DoSprint()
        {
            if (inputLocked) return;
            sprintRemain = sprintDuration;
        }

        /// <summary>Dash (Inferno): rapida scivolata orizzontale nella direzione
        /// corrente. Cooldown, non funziona in volo.</summary>
        public void DoDash()
        {
            if (inputLocked) return;
            if (Realm != AfterlifeRealm.INFERNO) return;
            if (Time.unscaledTime < canDashAt) return;
            canDashAt = Time.unscaledTime + dashCooldown;
            dashRemain = dashDuration;

            if (moveInput.sqrMagnitude > 0.05f)
            {
                CameraRig rig = CameraRig.Instance;
                Vector3 forward = rig != null
                    // GIORNO 9: base di marcia = solo yaw del drag, non la
                    // vista smorzata ne' il giroscopio.
                    ? Vector3.ProjectOnPlane(rig.MovementBasis * Vector3.forward, Vector3.up).normalized
                    : transform.forward;
                Vector3 right = Vector3.Cross(Vector3.up, forward);
                dashDirection = (forward * moveInput.y + right * moveInput.x).normalized;
                if (dashDirection.sqrMagnitude < 0.01f) dashDirection = transform.forward;
            }
            else
            {
                dashDirection = transform.forward;
            }
        }

        /// <summary>Spinta orizzontale per i pericoli dell'Inferno (es. sfere di
        /// fuoco): un breve impulso che sposta il player, disponibile in ogni
        /// regno cosi' i pericoli hanno effetto anche in citta'.</summary>
        public void ApplyKnockback(Vector3 impulse)
        {
            knockbackVel = new Vector3(impulse.x, 0f, impulse.z);
            knockbackRemain = 0.3f;
        }

        /// <summary>Abilita il volo (Paradiso).</summary>
        public void StartFlight()
        {
            if (inputLocked) return;
            if (Realm != AfterlifeRealm.PARADISO) return;
            flying = true;
        }

        /// <summary>Disattiva il volo: il player ricade sotto gravita'.</summary>
        public void StopFlight()
        {
            flying = false;
            flightVertical = 0;
        }

        /// <summary>Controllo verticale in volo (Paradiso): +1 sale,
        /// -1 scende, 0 torna neutro.</summary>
        public void SetFlightVertical(int input)
        {
            if (inputLocked) { flightVertical = 0; return; }
            flightVertical = flying ? Mathf.Clamp(input, -1, 1) : 0;
        }

        private void LateUpdate()
        {
            GroundFollow();
        }

        /// <summary>
        /// Binding professionale al terreno DEM/OSM (GroundSnapper, condiviso
        /// con gli NPC): tiene i piedi sul piano di appoggio reale. Corre in
        /// LateUpdate (dopo controller.Move) e:
        /// - non tocca il player in ARIA (salto/caduta: la gravita' fa tutto);
        /// - non tocca il volo del Paradiso, la guida e i minigiochi congelati;
        /// - superficie VICINA (sonda corta) = piano su cui si cammina davvero:
        ///   la segue sempre, salita dolce e discesa controllata (basta affondi
        ///   o sollevate quando il DEM/terreno arriva sotto i piedi);
        /// - superficie LONTANA = aiuta solo se il terreno e' arrivato SOPRA di
        ///   noi (sepolti da una tile in quota) o il gap e' modico; un fondo a
        ///   -10m da sotto i piedi senza alcun appoggio vicino non viene toccato
        ///   (tetto non visto / bordo ponte: la fisica ne fa le spese).
        /// </summary>
        private void GroundFollow()
        {
            if (!GroundFollowEnabled) return;
            if (controller == null || !controller.enabled) return;
            if (City.Game.Instance != null && City.Game.Instance.IsDriving) return;
            if (flying) return;   // volo Paradiso: quota pilotata a mano
            if (City.Economy.EggCaptureMinigame.Instance != null &&
                City.Economy.EggCaptureMinigame.Instance.IsActive) return;
            if (velocity.y > 0.5f) return;  // Giorno 7: salto/impulso in corso,
                                            // il follower non deve contrastarlo

            Vector3 pos = transform.position;
            float feetFromPivot = controller.height * 0.5f - controller.center.y;
            float feetY = pos.y - feetFromPivot;
            Vector3 feetPos = new Vector3(pos.x, feetY, pos.z);

            // GIORNO 7: il magnete puo' agire per una finestra breve anche se
            // il CharacterController dice "aria": e' il caso tipico della
            // discesa da un cordolo, dove isGrounded resta false per 2-3 frame
            // e il player faceva un saltellino prima di riattaccarsi al suolo.
            bool grounded = controller.isGrounded || FeetProbeGrounded();
            if (grounded) _lastGroundedAt = Time.time;

            GroundSample s = GroundSnapper.SampleGround(feetPos, transform);
            if (!s.found) return;   // nessun dato: la fisica e' gia' in carico

            float targetFeet = s.height + GroundFollowClearance;
            float err = targetFeet - feetY;

            // Magnetismo in discesa: solo subito dopo un appoggio, su una
            // superficie VICINA (s.near), senza salto in corso e per cadute
            // modiche. Un burrone (> MagnetRange) resta alla gravita': il
            // magnete non deve "incollare" il player durante un volo vero.
            bool magnet = !grounded && s.near &&
                Time.time - _lastGroundedAt <= GroundSnapper.GroundGraceS &&
                err < 0f && -err <= GroundSnapper.MagnetRange;
            if (!grounded && !magnet) return; // aria vera: niente follower

            // GIORNO 2 / REVIEW: salva i due valori separatamente, senza
            // concatenare stringhe ogni frame. Il confronto e' solo tra
            // riferimenti/enum; la telemetria compone il testo ogni 2 s.
            string nm = s.surfaceName ?? "";
            if (s.source != _surfaceSource || nm != _surfaceName)
            {
                _surfaceSource = s.source;
                _surfaceName = nm;
            }

            if (s.near)
            {
                // piano reale sotto i piedi: seguito in ogni caso
            }
            else if (err > 0f)
            {
                // il terreno e' arrivato SOPRA di noi (tile in quota): risali
            }
            else if (err > -GroundSnapper.FallGraceM)
            {
                // float leggero sopra il fondo della colonna: ridiscendi
            }
            else
            {
                // superficie non vista molto piu' in basso (tetto/collo):
                // non trascinare giu', la fisica attuale resta valida
                return;
            }

            float newFeet = GroundSnapper.Follow(ref _groundFollow, feetY,
                targetFeet, Mathf.Min(Time.deltaTime, MaxFrameStepS));
            float deltaY = newFeet - feetY;
            if (Mathf.Abs(deltaY) <= GroundFollowClearance) return;

            // GIORNO 7: step assist (salita <= 0,35 m) e magnete in discesa
            // passano dal CharacterController, NON da transform.position: il
            // passo e' gia' smorzato da GroundSnapper.Follow e la sweep interna
            // del CC resta sincronizzata. Niente teletrasporto sul marciapiede.
            bool stepAssist = err > 0f && err <= GroundSnapper.StepAssistHeight;
            if (stepAssist || magnet)
            {
                controller.Move(new Vector3(0f, deltaY, 0f));
                // La gravita' non deve accumularsi nei ~10 frame dello step:
                // senza questo velocity.y scende sotto -3 m/s e il Move di
                // Update annulla la salita (il player "bobbia" sul cordolo).
                // -1 e' lo stesso ground-stick gia' usato in Update.
                velocity.y = -1f;
            }
            else
            {
                // Correzione grande (sepolti / catch-up DEM): qui serve il
                // disable/enable storico, perche' uno sweep non puo' attraversare
                // un collider alto 1 m in un solo frame. Il costo e' trascurabile
                // perche' si verifica solo su eventi rari.
                Vector3 corrected = new Vector3(pos.x, newFeet + feetFromPivot, pos.z);
                controller.enabled = false;
                transform.position = corrected;
                controller.enabled = true;
                try { Physics.SyncTransforms(); }
                catch (System.Exception) { }
            }

            // Contatore diagnostico: quante correzioni verticali al secondo
            // vengono applicate (e quanto grandi). E' la misura decisiva
            // dell'A/B: con follower OFF questa riga resta a corr/s=0.
            _followCorrections++;
            float absErr = Mathf.Abs(err);
            if (absErr > _followMaxErr) _followMaxErr = absErr;
            _followLastSource = s.source + "/" + s.surfaceName;

            // Diagnostica throttled: solo correzioni significative. `modo`
            // distingue step / magnet / climb / down per il gate su device.
            if (Time.time >= _nextGroundLogAt &&
                (Mathf.Abs(err) > 0.35f || stepAssist || magnet))
            {
                _nextGroundLogAt = Time.time + 6f;
                string modo = stepAssist ? "step" : (magnet ? "magnet"
                    : (err > 0f ? "climb" : "down"));
                City.OSM.OsmDiag.Log("[GroundFollow] player modo=" + modo +
                    " fonte=" + s.source +
                    " su='" + s.surfaceName + "' h=" + s.height.ToString("F2") +
                    " err=" + err.ToString("F2") + " -> piedi=" +
                    newFeet.ToString("F2"));
            }
        }

        /// <summary>Accumula la distanza percorsa e la consegna alle missioni
        /// a ogni metro (batching per non spammare il MissionManager a frame).</summary>
        private void TrackWalkDistance()
        {
            if (!IsMoving) return;
            // GIORNO 4: usiamo i metri REALI percorsi, non velocity*dt.
            // Cosi' le missioni "cammina X metri" non accreditano il player
            // che spinge contro un muro senza muoversi.
            float meters = _realHorizontalMeters;
            if (meters <= 0f) return;
            walkTelemetry += meters;
            if (walkTelemetry >= 1f)
            {
                City.Economy.MissionManager.Instance?.OnPlayerWalked(walkTelemetry);
                walkTelemetry = 0f;
            }
        }
    }
}
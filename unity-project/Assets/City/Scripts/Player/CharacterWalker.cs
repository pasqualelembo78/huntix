using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Animatore del personaggio Kenney characterMedium con doppia modalita':
    ///
    /// 1) ANIMATOR MODE (player): se esiste un Animator CON controller,
    ///    costruisce a runtime un AnimatorController con BlendTree Idle→Walk→Run
    ///    guidato dal parametro float "Speed" + Foot IK per piedi sul terreno DEM.
    ///
    /// 2) PROCEDURAL MODE (NPC): se l'Animator manca o non ha controller,
    ///    usa Animation legacy con curve procedurali generate a runtime
    ///    (stesso codice di prima per retrocompatibilita' NPC).
    ///
    /// PlayerController e NPCController chiamano SetSpeed ogni frame.
    /// </summary>
    public class CharacterWalker : MonoBehaviour
    {
        // ── Interfaccia comune ──
        private float speed;
        private bool grounded = true;
        private bool ready;
        private bool useAnimator;
        private Animator animator;
        private CharacterController cc;
        private SoleGeometry _sole;

        // ── GIORNO 5: fase del ciclo di locomozione ──
        // UN'unica sorgente per head bob (CameraRig) e passi audio: se i due
        // usassero timer separati, a 10 fps scivolerebbero via. Due appoggi
        // per ciclo di clip: StepPhase 0..1 = UN passo, StepCount conta i passi.
        private const float TwoPi = 6.2831853f;
        public float StepPhase { get; private set; }
        public int StepCount { get; private set; }
        /// <summary>Velocita' REALE consegnata all'Animator (Giorno 4).</summary>
        public float Speed { get { return speed; } }
        private int _lastStepIndex = -1;
        private float _procPhase;
        private int _lastAudioStep;
        private AudioSource _stepAudio;
        private static AudioClip[] _stepClips;

        // ── Parametri Animator mode ──
        [Header("Animator Mode (player)")]
        public float walkSpeed = 3.2f;
        public float runSpeed = 7.5f;

        // ── Foot IK ──
        [Header("Foot IK")]
        public bool footIKEnabled = true;
        public float ikRayDistance = 1.5f;
        public float ikSmoothSpeed = 12f;
        public float ikMaxOffset = 0.6f;
        // FIX fluttuazione: prima 1.6 m, il bacino poteva essere sollevato di
        // oltre un metro sopra i piedi ancorati a terra (look "sospeso in
        // aria"). Il tetto è ora 0.25 m: compensa l'affondamento del clip
        // baked (fianchi sotto il livello-piedi) senza staccare il corpo dal
        // suolo. L'inerzia del corpo/salta resta responsabile di ogni quota
        // reale sopra il terreno.
        public float ikMaxLift = 0.25f;
        public LayerMask groundMask = ~0;
        private float _ikWeightL, _ikWeightR;
        private Vector3 _ikPosL, _ikPosR;
        private Quaternion _ikRotL, _ikRotR;
        private float _hipOffsetY;
        private float _hipsBaseY = float.NaN;
        // OnAnimatorIK puo' essere chiamato piu' volte nello stesso frame se il
        // controller ha piu' layer con IK Pass. bodyPosition e' ADDITIVO (viene
        // ricalcolato dall'animazione solo a ogni frame): senza questa guardia
        // lo stesso delta -0,47 verrebbe sommato due volte (-0,94).
        private int _bodyWriteFrame = -1;
        private float _legSpanRest = float.NaN;
        private float _groundDiagAt;
        // Il ray e il riepilogo IK hanno throttle SEPARATI: prima condividevano
        // _groundDiagAt e il ray (chiamato prima) consumava sempre lo slot,
        // quindi [Ground] con hipsY/applied/legSpanRest non scriveva MAI.
        private float _groundRayDiagAt;
        private float _pulseAt;
        private float _ikFiredAt = float.MinValue;
        private bool _diagToastShown;
        private static readonly RaycastHit[] IkHitsBuffer = new RaycastHit[8];

        // ── Cache delle clip procedurali (NPC) ──
        // Le clip walk/run/idle sono IDENTICHE per ogni pedone che condivide lo
        // stesso scheletro (stesso prefab Kenney) e gli stessi parametri: prima
        // se ne generavano 3 per OGNI NPC (migliaia di AnimationClip in
        // memoria durante lo streaming). Ora si generano una volta per firma
        // scheletro+parametri e si condividono: le clip Unity sono risorse
        // condivise e ogni Animation tiene il proprio stato di play. Zero
        // differenza visiva.
        private static readonly Dictionary<string, AnimationClip> ClipCache =
            new Dictionary<string, AnimationClip>();

        // ── Parametri procedural mode (NPC) ──
        private const string IdleState = "Idle";
        private const string WalkState = "Walk";
        private const string RunState = "Run";
        private const string HipsPath = "Root/HipsCtrl/Hips";
        private const string SpinePath = HipsPath + "/Spine";
        private const string ChestPath = SpinePath + "/Chest";
        private const string UpChestPath = ChestPath + "/UpperChest";
        private const string LUpLegPath = HipsPath + "/LeftUpLeg";
        private const string LLegPath = LUpLegPath + "/LeftLeg";
        private const string RUpLegPath = HipsPath + "/RightUpLeg";
        private const string RLegPath = RUpLegPath + "/RightLeg";
        private const string LShoulderPath = UpChestPath + "/LeftShoulder";
        private const string RShoulderPath = UpChestPath + "/RightShoulder";
        private const string LArmPath = LShoulderPath + "/LeftArm";
        private const string RArmPath = RShoulderPath + "/RightArm";
        private const string LForeArmPath = LArmPath + "/LeftForeArm";
        private const string RForeArmPath = RArmPath + "/RightForeArm";

        [Header("Procedural Mode (NPC)")]
        public float legSwingDeg = 30f;
        public float kneeBendDeg = 34f;
        public float armSwingDeg = 22f;
        public float elbowBendDeg = 26f;
        public float runThreshold = 4.2f;
        public float walkCadence = 1.7f;
        public float runCadence = 2.6f;
        public float walkStrideM = 1.1f;
        public float runStrideM = 2.0f;
        public float forwardLeanDeg = 7f;
        public float walkBounceM = 0f;
        public float runBounceM = 0f;
        public float swingSign = 1f;

        private Quaternion _foldL = Quaternion.identity;
        private Quaternion _foldR = Quaternion.identity;
        private bool _foldReady;
        private Animation anim;
        private string current;
        private bool _swingReady;
        private Vector3 _swingAxisL = Vector3.right;
        private Vector3 _swingAxisR = Vector3.right;
        private bool _legAxisReady;
        private Vector3 _hipAxisL = Vector3.right;
        private Vector3 _hipAxisR = Vector3.right;
        private Vector3 _kneeAxisL = Vector3.right;
        private Vector3 _kneeAxisR = Vector3.right;
        private Vector3 _elbowAxisL = Vector3.right;
        private Vector3 _elbowAxisR = Vector3.right;

        public bool IsReady { get { return ready; } }

        public static CharacterWalker AttachIfNeeded(GameObject ownerRoot)
        {
            if (ownerRoot == null) return null;
            CharacterWalker existing = ownerRoot.GetComponentInChildren<CharacterWalker>();
            if (existing != null) return existing;
            // Always add the walker to the player root so that Start() can find
            // the Animator component on the hero (which is a child of the player root).
            // The original logic of attaching to the first SMR GO could place the
            // component under a descendant where the Animator is invisible to
            // GetComponentInChildren (ancestor vs descendant issue -> T-pose).
            var w = ownerRoot.gameObject.AddComponent<CharacterWalker>();
            City.OSM.OsmDiag.Log("[CharacterWalker][Attach] walker aggiunto a " +
                ownerRoot.name + " (child=" + ownerRoot.transform.childCount + ")");
            return w;
        }

        private bool CanDriveProcedural()
        {
            return transform.Find(HipsPath) != null &&
                   transform.Find(LUpLegPath) != null;
        }

        /// <summary>FIX B: GetComponentInChildren prende il PRIMO Animator in
        /// gerarchia, che puo' essere il characterMedium legacy invece di
        /// Remy. Se c'e' l'hero rig restringo la ricerca a lui; per NPC e
        /// fallback legacy (nessun rig) il comportamento resta identico.</summary>
        private Animator ResolveAnimator()
        {
            var heroRig = GetComponentInChildren<PlayerHeroRig>(true);
            if (heroRig != null)
            {
                _sole = heroRig.GetComponentInChildren<SoleGeometry>(true);
                var heroAnimator = heroRig.GetComponentInChildren<Animator>(true);
                if (heroAnimator != null) return heroAnimator;
            }
            return GetComponentInChildren<Animator>(true);
        }

        /// <summary>Spessore scarpa misurato sulla geometria reale: la caviglia
        /// IK deve stare questo tanto sopra il suolo perche' la suola tocchi.
        /// Usato anche da FootIK per non far scendere il piede nel terreno.</summary>
        public float SoleBelowAnkle
        {
            get
            {
                return _sole != null && !float.IsNaN(_sole.SoleBelowAnkle)
                    ? _sole.SoleBelowAnkle : 0f;
            }
        }

        private void Start()
        {
            animator = ResolveAnimator();
            cc = GetComponentInParent<CharacterController>();
            City.OSM.OsmDiag.Log("[CharacterWalker][Start] GO=" + gameObject.name +
                " animator=" + (animator != null) +
                " animatorGO=" + (animator != null ? animator.gameObject.name : "-") +
                " cc=" + (cc != null));

            // Prova a caricare il controller Mixamo da Resources
            var mixamoCtrl = Resources.Load<RuntimeAnimatorController>(
                "Mixamo/PlayerLocomotion");
            City.OSM.OsmDiag.Log("[CharacterWalker][Start] mixamoCtrl=" +
                (mixamoCtrl != null ? mixamoCtrl.name : "NULL") +
                " runtimeCtrl=" +
                (animator != null && animator.runtimeAnimatorController != null
                    ? animator.runtimeAnimatorController.name : "-"));

            if (animator != null)
            {
                if (mixamoCtrl != null)
                {
                    // Player: usa il controller Mixamo con BlendTree + Foot IK
                    useAnimator = true;
                    SetupAnimatorMode(mixamoCtrl);
                }
                else if (animator.runtimeAnimatorController != null)
                {
                    // Fallback: usa il controller esistente (es. PlayerAnim Kenney)
                    useAnimator = true;
                    SetupAnimatorMode(animator.runtimeAnimatorController);
                }
                else if (CanDriveProcedural())
                {
                    // NPC senza controller con scheletro Kenney: distruggi
                    // l'Animator e usa le clip procedurali legacy.
                    City.OSM.OsmDiag.Log("[CharacterWalker][Start] nessun controller, " +
                        "distruzione Animator e BuildProcedural (scheletro Kenney)");
                    DestroyImmediate(animator);
                    useAnimator = false;
                    BuildProcedural();
                }
                else
                {
                    // Animator senza controller valido e senza scheletro Kenney:
                    // NON distruggerlo, altrimenti il modello resta in T-pose
                    // (bind pose) senza alcuna clip che lo muova.
                    City.OSM.OsmDiag.Log("[CharacterWalker][Start] Animator senza " +
                        "controller e senza scheletro Kenney: mantenuto Animator");
                    useAnimator = true;
                    SetupAnimatorMode(animator.runtimeAnimatorController);
                }
            }
            else if (CanDriveProcedural())
            {
                City.OSM.OsmDiag.Log("[CharacterWalker][Start] nessun Animator, " +
                    "BuildProcedural (scheletro Kenney)");
                useAnimator = false;
                BuildProcedural();
            }
            else
            {
                City.OSM.OsmDiag.Log("[CharacterWalker][Start] nessun Animator e " +
                    "nessuno scheletro Kenney: modello non gestibile, " +
                    "resta in bind pose");
                useAnimator = false;
                ready = true;
            }

            // State dump UNA TANTUM del player (cc presente = gira il controller
            // del giocatore): basta una cattura breve all'avvio per capire se il
            // Foot IK puo' scattare. Se isHuman=false o useAnimator=false,
            // OnAnimatorIK non parte mai e l'affondamento visivo e' inevitabile.
            if (cc != null)
            {
                bool isHuman = animator != null && animator.avatar != null &&
                    animator.avatar.isHuman;
                float feetFromPivot = cc.height * 0.5f - cc.center.y;
                City.OSM.OsmDiag.Log("[CharacterWalker][Player] " + gameObject.name +
                    " useAnimator=" + useAnimator +
                    " footIK=" + footIKEnabled +
                    " isHuman=" + isHuman +
                    " animator=" + (animator != null) +
                    " ccH=" + cc.height.ToString("F2") +
                    " ccC=" + cc.center.y.ToString("F2") +
                    " feetFromPivot=" + feetFromPivot.ToString("F2") +
                    " y=" + transform.position.y.ToString("F2"));

                // Diagnosi a schermo: il player sa subito se il Foot IK puo'
                // partire (avatar umanoide + animator mode), senza scavare nel
                // logcat. Se IK=true ma il personaggio e' ancora affondato, il
                // problema e' nel raycast/offset, non nell'attivazione.
                // NB: qui la UI puo' non essere ancora pronta; la toast vera
                // viene riproposta ogni 2s da DiagPlayerPulse (Update) finche'
                // Game.Instance.ui esiste, così non si perde mai.
                bool ikOn = useAnimator && footIKEnabled && isHuman;
                var g = City.Game.Instance;
                if (g != null && g.ui != null)
                    g.ui.ShowToast(ikOn
                        ? "Foot IK attivo (" + feetFromPivot.ToString("F2") +
                            " m): controllo piedi sul terreno"
                        : "Foot IK DISATTIVATO (isHuman=" + isHuman +
                            "): il player non si adatta al terreno");
            }
        }

        /// <summary>Heartbeat periodico SOLO del player (cc != null): log di
        /// stato ogni ~2s indipendente da OnAnimatorIK e dalla cattura del
        /// logcat all'avvio app, così ogni sessione mostra lo stato del
        /// personaggio. Riproporre anche la toast finche' la UI e' pronta.</summary>
        private void DiagPlayerPulse()
        {
            if (cc == null) return;
            float now = UnityEngine.Time.time;
            if (now < _pulseAt) return;
            _pulseAt = now + 2f;
            bool isHuman = animator != null && animator.avatar != null &&
                animator.avatar.isHuman;
            float feetFromPivot = cc.height * 0.5f - cc.center.y;
            float groundRef = transform.position.y - feetFromPivot;
            City.OSM.OsmDiag.Log("[CharacterWalker][Player] " + gameObject.name +
                " mode=" + (useAnimator ? "Animator" : "Procedural") +
                " ik=" + footIKEnabled +
                " isHuman=" + isHuman +
                " ikFired=" + (bool)(now - _ikFiredAt < 5f) +
                " y=" + transform.position.y.ToString("F2") +
                " groundRef=" + groundRef.ToString("F2") +
                " grounded=" + grounded +
                " ccGrounded=" + cc.isGrounded +
                " feetFromPivot=" + feetFromPivot.ToString("F2") +
                // GIORNO 4: velocita' EFFETTIVA consegnata all'Animator.
                // Va confrontata con "real=" di [WalkDiag]: se combaciano il
                // moonwalk e' finito, anche quando il player spinge un muro.
                " spd=" + speed.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture));
            if (_diagToastShown) return;
            var g = City.Game.Instance;
            if (g == null || g.ui == null) return;
            _diagToastShown = true;
            bool ikOn = useAnimator && footIKEnabled && isHuman;
            g.ui.ShowToast(ikOn
                ? "Foot IK attivo (" + feetFromPivot.ToString("F2") +
                    " m): controllo piedi sul terreno"
                : "Foot IK DISATTIVATO (isHuman=" + isHuman +
                    "): il player non si adatta al terreno");
        }

        // ═══════════════════════════════════════════════════════════════
        // ANIMATOR MODE - BlendTree + Foot IK
        // ═══════════════════════════════════════════════════════════════

        private void SetupAnimatorMode(RuntimeAnimatorController controller)
        {
            if (controller == null)
            {
                City.OSM.OsmDiag.Log("[CharacterWalker][Animator] controller null: " +
                    "Animator lasciato nello stato corrente");
                ready = true;
                return;
            }
            animator.applyRootMotion = false;
            animator.runtimeAnimatorController = controller;
            ready = true;

            // Unity consegna OnAnimatorIK solo ai componenti sullo STESSO
            // GameObject dell'Animator. Il walker e' attaccato al player root
            // (cosi' Start() trova l'Animator sul figlio hero) => senza relay
            // il callback non arriva mai e il Foot IK resta morto
            // (ikFired=False, player affondato) anche con l'IK Pass abilitato.
            // Valgono solo i layer con m_IKPass=1: verificato sul controller
            // PlayerLocomotion rigenerato da RemyKitSetup (build hook).
            if (animator.gameObject != gameObject &&
                animator.gameObject.GetComponent<AnimatorIKRelay>() == null)
            {
                var relay = animator.gameObject.AddComponent<AnimatorIKRelay>();
                relay.walker = this;
                City.OSM.OsmDiag.Log("[CharacterWalker][Animator] relay OnAnimatorIK " +
                    "installato su " + animator.gameObject.name);
            }
            else if (animator.gameObject == gameObject)
            {
                City.OSM.OsmDiag.Log("[CharacterWalker][Animator] walker e Animator " +
                    "sullo stesso GO: OnAnimatorIK raggiunge gia' il walker");
            }
            City.OSM.OsmDiag.Log("[CharacterWalker][Animator] Controller assegnato: " +
                controller.name + " footIK=" + footIKEnabled +
                " avatar=" + (animator.avatar != null ? animator.avatar.name : "NULL") +
                " isHuman=" + (animator.avatar != null && animator.avatar.isHuman));
        }

        private void UpdateAnimatorMode()
        {
            if (animator == null) return;
            // Il controller Mixamo usa SOLO "Speed" e "IsGrounded": il vecchio
            // flag booleano "IsMoving" era un no-op (il parametro non esiste
            // neppure in RemyKitSetup.cs). Rimosso per non far credere che
            // ci sia un secondo canale di animazione. Fallback Kenney:
            // SetFloat/SetBool su parametri inesistenti restano no-op.
            animator.SetFloat("Speed", speed);
            animator.SetBool("IsGrounded", grounded);
        }

        // ── Foot IK ──

        private void OnAnimatorIK(int layerIndex)
        {
            RunFootIK(layerIndex);
        }

        /// <summary>Esegue il Foot IK. Pubblico perché Unity consegna
        /// OnAnimatorIK SOLO ai componenti sullo stesso GameObject
        /// dell'Animator: il walker vive sul player root mentre l'Animator
        /// sta sul figlio PlayerHeroRig, quindi un relay sull'Animator GO
        /// (AnimatorIKRelay) inoltra qui il callback.</summary>
        public void RunFootIK(int layerIndex)
        {
            if (!useAnimator || !footIKEnabled || animator == null) return;
            _ikFiredAt = UnityEngine.Time.time;

            FootIKStep(AvatarIKGoal.LeftFoot, ref _ikWeightL, ref _ikPosL, ref _ikRotL);
            FootIKStep(AvatarIKGoal.RightFoot, ref _ikWeightR, ref _ikPosR, ref _ikRotR);

            // groundRef usa il livello PIEDI (non il pivot del CharacterController):
            // sul terreno piatto i piedi stanno a root - feetFromPivot.  Usare il
            // pivot come groundRef produce un offset permanente di -feetFromPivot
            // (tipicamente -1 m) che spinge la pelvi nel terreno e rende visibile
            // l'affondamento durante la camminata.
            float feetFromPivot = (cc != null)
                ? cc.height * 0.5f - cc.center.y
                : 1f;
            float groundRef = transform.position.y - feetFromPivot;

            // Compensazione AFFONDAMENTO clip baked: Idle/Walk/Run Mixamo hanno
            // ogniuno una quota pelvi diversa (-1.5 m in Run se il fix d'import
            // heightFromFeet non si e' applicato); i piedi vengono trascinati
            // sotto il terreno e il vecchio clamp +-0.3 non bastava. Qui si
            // alza il bacino finche' i fianchi stanno a corretto span di gamba
            // (groundRef + legSpan) sopra i piedi fisici.
            if (float.IsNaN(_legSpanRest))
                _legSpanRest = MeasureLegSpan();
            else if (_legSpanRest <= 0.05f || _legSpanRest > 3f)
                _legSpanRest = MeasureLegSpan();
            if (_legSpanRest <= 0.05f || _legSpanRest > 3f) _legSpanRest = 1f;

            // FIX GALLEGGIAMENTO IK: il bacino deve scendere a
            // (groundRef + legSpanRest). Il vecchio Mathf.Max(footLift,
            // bodySinkLift) scartava il valore NEGATIVO di bodySinkLift:
            // con hipsY=1.50 e legSpan=1.04 serviva -0.47, ma max(-0.03,
            // -0.47) = -0.03 -> il bacino restava in alto e i piedi IK
            // (e la scarpa visiva) restavano ~0,45 m sopra il terreno.
            // Ora: se i fianchi sono SOTTO lo span serve salire (positivo),
            // se sono SOPRA serve scendere (negativo) — si prende il valore
            // con il SEGNO GIUSTO, e il clamp simmetrico lo consente.
            // footLift resta un eventuale rialzo da gradino (curb/step).
            float footLift = Mathf.Min(_ikPosL.y, _ikPosR.y) - groundRef;
            float bodySinkLift = 0f;
            Transform hipsBone = animator.GetBoneTransform(HumanBodyBones.Hips);
            if (hipsBone != null && grounded)
            {
                // _legSpanRest e' bacino->suola a gamba distesa. La caviglia IK
                // viene pero' alzata dello spessore della scarpa e abbassata di
                // SoleTouchDrop: il bacino deve stare piu' basso della stessa
                // quantita', altrimenti la gamba dovrebbe allungarsi di 3 cm e
                // l'IK non ce la fa (suola che resta sospesa).
                bodySinkLift = (groundRef + _legSpanRest -
                    PlayerHeroRig.SoleTouchDrop) - hipsBone.position.y;
            }
            float target = (bodySinkLift < 0f)
                ? bodySinkLift               // bacino troppo alto -> SCENDI
                : Mathf.Max(footLift, bodySinkLift);
            float offset = Mathf.Clamp(target, -ikMaxOffset, ikMaxLift);
            _hipOffsetY = Mathf.Lerp(_hipOffsetY, offset, ikSmoothSpeed * Time.deltaTime);

            // FIX COORDINAMENTO IK (galleggiamento): su un avatar HUMANOID
            // Unity ricompone la posa degli ossi DOPO OnAnimatorIK partendo da
            // animator.bodyPosition. Scrivere hipsBone.localPosition (vecchio
            // codice) funzionava solo DENTRO il callback (hipsY=1,04 nel diag)
            // ma veniva annullato al frame renderizzato (SoleCheck=1,53): il
            // personaggio restava sospeso ~0,47 m con l'ombra staccata.
            // Ora il bacino si abbassa scrivendo su bodyPosition, LO STESSO
            // canale usato da FootIK. I due sistemi restano entrambi attivi e
            // i loro delta si SOMMANO (FootIK applica il suo _pelvis dopo
            // aver letto bodyPosition), senza annullarsi ne' accumularsi.
            // _hipOffsetY e' gia' in metri mondo: nessuna conversione locale.
            if (hipsBone != null)
            {
                _hipsBaseY = hipsBone.localPosition.y; // diagnostica invariata
                if (_bodyWriteFrame != Time.frameCount)
                {
                    _bodyWriteFrame = Time.frameCount;
                    Vector3 body = animator.bodyPosition;
                    animator.bodyPosition = body + Vector3.up * _hipOffsetY;
                }
            }

            DiagGround("IK", groundRef, footLift, bodySinkLift, offset, _hipOffsetY,
                hipsBone != null ? hipsBone.position.y : 0f);
        }

        /// <summary>Diagnostica in logcat (throttled per istanza) del
        /// rilevamento terreno: quota piedi di riferimento, lift dai piedi IK,
        /// corpo affondato e offset pelvi applicato, cosi' si vede a colpo
        /// d'occhio se il character resta sotto il suolo e perche'. Emette
        /// una riga ogni GroundDiagInterval su walker (player e NPC).</summary>
        private void DiagGround(string tag, float groundRef, float footLift,
            float bodySinkLift, float targetOffset, float appliedOffset,
            float hipsWorldY)
        {
            float now = UnityEngine.Time.time;
            if (now < _groundDiagAt) return;
            _groundDiagAt = now + 2f;
            City.OSM.OsmDiag.Log("[CharacterWalker][Ground][" + tag + "] " +
                gameObject.name + " y=" + transform.position.y.ToString("F2") +
                " groundRef=" + groundRef.ToString("F2") +
                " footLift=" + footLift.ToString("F2") +
                " bodySinkLift=" + bodySinkLift.ToString("F2") +
                " target=" + targetOffset.ToString("F2") +
                " applied=" + appliedOffset.ToString("F2") +
                " hipsY=" + hipsWorldY.ToString("F2") +
                " legSpanRest=" + (float.IsNaN(_legSpanRest) ? -1f : _legSpanRest).ToString("F2") +
                " base=" + (float.IsNaN(_hipsBaseY) ? -1f : _hipsBaseY).ToString("F2") +
                " grounded=" + grounded);
        }

        /// <summary>Diagnostica del raycast che rileva il terreno sotto il
        /// piede (stesso throttle per-istanza di DiagGround): mostra origine,
        /// numero di hit, quota del punto migliore vs groundRef e peso IK,
        /// cosi' si vede subito se il ray parte sottoterra o non trova il suolo.
        /// Throttle dedicato (non condiviso con DiagGround): entrambi i diag
        /// devono poter scrivere nello stesso istante di campionamento.</summary>
        private void DiagGroundRay(AvatarIKGoal goal, Vector3 origin, int hits,
            Vector3 hitPoint, Vector3 hitNormal, float groundRef,
            float weight, float gap)
        {
            float now = UnityEngine.Time.time;
            if (now < _groundRayDiagAt) return;
            _groundRayDiagAt = now + 2f;
            City.OSM.OsmDiag.Log("[CharacterWalker][GroundRay][" +
                (goal == AvatarIKGoal.LeftFoot ? "L" : "R") + "] " +
                gameObject.name + " originY=" + origin.y.ToString("F2") +
                " hits=" + hits +
                " bestY=" + (gap >= 0f ? hitPoint.y.ToString("F2") : "NONE") +
                " nY=" + hitNormal.y.ToString("F2") +
                " groundRef=" + groundRef.ToString("F2") +
                " gap=" + (gap >= 0f ? gap.ToString("F2") : "-") +
                " w=" + weight.ToString("F2"));
        }

        /// <summary>Span hips->piedi a RIPOSO (gamba distesa). FIX fluttuazione:
        /// la vecchia misura leggeva lo span dal POSE CORRENTE del clip
        /// (ginocchia piegate in Walk/Run), quindi il "corpo affondato" veniva
        /// calibrato su gambe corte e il bacino era innalzato oltre il giusto.
        /// Ora si usa lo span anatomico stabile ricavato dalla capsule del
        /// CharacterController (fianchi in posizione eretta ~0.52 * altezza):
        /// lo stesso valore per ogni clip, nessun sollevamento fantasma.</summary>
        private float MeasureLegSpan()
        {
            // La distanza bacino->suola viene misurata sull'armatura e sui
            // vertici reali (SoleGeometry), non stimata dalla capsula del CC:
            // cc.height=2 m dava 1,04 m mentre lo scheletro Remy in scala
            // 0,43 ha un appoggio reale di ~0,91 m. Il divario di 0,13 m
            // teneva il bacino (e quindi la suola) sospeso sopra l'asfalto.
            if (_sole != null && !float.IsNaN(_sole.HipsToSole) &&
                _sole.HipsToSole > 0.3f && _sole.HipsToSole < 3f)
                return _sole.HipsToSole;
            if (cc != null)
            {
                float standing = cc.height * 0.52f;
                if (standing > 0.4f && standing < 3f) return standing;
            }
            if (animator == null) return 1f;
            Transform hips = animator.GetBoneTransform(HumanBodyBones.Hips);
            Transform fl = animator.GetBoneTransform(HumanBodyBones.LeftFoot);
            Transform fr = animator.GetBoneTransform(HumanBodyBones.RightFoot);
            if (hips == null) return 1f;
            float l = fl != null ? Mathf.Abs(hips.position.y - fl.position.y) : 0f;
            float r = fr != null ? Mathf.Abs(hips.position.y - fr.position.y) : 0f;
            if (fl == null && fr == null) return 1f;
            return (l + r) * (fl != null && fr != null ? 0.5f : 1f);
        }

        private void FootIKStep(AvatarIKGoal goal,
            ref float weight, ref Vector3 pos, ref Quaternion rot)
        {
            Transform footBone = animator.GetBoneTransform(
                goal == AvatarIKGoal.LeftFoot
                    ? HumanBodyBones.LeftFoot
                    : HumanBodyBones.RightFoot);
            Transform hipsBone = animator.GetBoneTransform(HumanBodyBones.Hips);
            if (footBone == null || hipsBone == null)
            {
                weight = Mathf.MoveTowards(weight, 0f, ikSmoothSpeed * Time.deltaTime);
                return;
            }

            Vector3 footWorld = footBone.position;
            float feetFromPivot = (cc != null)
                ? cc.height * 0.5f - cc.center.y
                : 1f;
            float groundRef = transform.position.y - feetFromPivot;

            // Origine del raycast ancorata alla QUOTA FISICA (pivot CC), sempre
            // sopra la superficie, non al footBone: se il clip e' affondato di
            // -1.5 m l'osso sta sotto il terreno e il vecchio ray da foot+0.3
            // partiva sottoterra (targetWeight=0 -> IK mai attivo, affondamento
            // permanente). Siamo gia' sopra il piano: il ray scende sul piede
            // e trova il terreno, poi l'IK solleva il piede.
            Vector3 origin = new Vector3(footWorld.x,
                groundRef + 1.1f, footWorld.z);
            float maxDist = 1.6f;
            Ray ray = new Ray(origin, Vector3.down);
            float targetWeight = 0f;
            Vector3 targetPos = footWorld;
            Quaternion targetRot = footBone.rotation;

            int n = Physics.RaycastNonAlloc(ray, IkHitsBuffer, maxDist, groundMask,
                QueryTriggerInteraction.Ignore);
            int best = -1;
            float bestDist = float.MaxValue;
            for (int i = 0; i < n; i++)
            {
                var h = IkHitsBuffer[i];
                if (h.collider == null) continue;
                // salta il proprio collider (CC e capsule degli NPC): il ray
                // parte sopra il pivot quindi puo' colpire la capsula propria.
                var t = h.collider.transform;
                if (t == transform || t.IsChildOf(transform)) continue;
                // il piano d'appoggio deve avere la normale verso l'alto
                // (altrimenti e' il sotto di un impalcato/ponte): lo scartiamo.
                if (h.normal.y < 0.1f) continue;
                if (h.distance < bestDist) { bestDist = h.distance; best = i; }
            }
            if (best >= 0)
            {
                var h = IkHitsBuffer[best];
                // La target IK e' l'OSSO caviglia, non la suola: la scarpa
                // scende SoleBelowAnkle sotto l'osso. Senza questo termine la
                // caviglia veniva piantata a filo d'asfalto e la suola restava
                // sospesa dello spessore della scarpa (~0,11 m). SoleTouchDrop
                // lascia 3 cm di affondamento voluto (clearance del follower).
                float ankleY = h.point.y + SoleBelowAnkle -
                    PlayerHeroRig.SoleTouchDrop;
                targetPos = new Vector3(h.point.x, ankleY, h.point.z);
                targetRot = Quaternion.FromToRotation(Vector3.up, h.normal) *
                    Quaternion.LookRotation(transform.forward, Vector3.up);
                // peso per quanto il suolo dista dal livello-piedi: a terra ~1,
                // sollevato (salto) cala e l'IK si disattiva senza stirare.
                float gap = Mathf.Abs(h.point.y - groundRef);
                targetWeight = Mathf.Clamp01(1f - gap / 1f);

                DiagGroundRay(goal, origin, n, h.point, h.normal, groundRef,
                    targetWeight, gap);
            }
            else
            {
                DiagGroundRay(goal, origin, n, Vector3.zero, Vector3.up,
                    groundRef, targetWeight, -1f);
            }

            weight = Mathf.MoveTowards(weight, targetWeight, ikSmoothSpeed * Time.deltaTime);
            pos = Vector3.Lerp(pos, targetPos, ikSmoothSpeed * Time.deltaTime);
            rot = Quaternion.Slerp(rot, targetRot, ikSmoothSpeed * Time.deltaTime);

            animator.SetIKPositionWeight(goal, weight);
            animator.SetIKPosition(goal, pos);
            animator.SetIKRotationWeight(goal, weight * 0.8f);
            animator.SetIKRotation(goal, rot);
        }

        // ═══════════════════════════════════════════════════════════════
        // PROCEDURAL MODE - curve legacy per NPC
        // ═══════════════════════════════════════════════════════════════

        private void CalibrateFold()
        {
            Transform lSh = transform.Find(LShoulderPath);
            Transform rSh = transform.Find(RShoulderPath);
            Transform lArm = transform.Find(LArmPath);
            Transform rArm = transform.Find(RArmPath);
            if (lSh == null || rSh == null || lArm == null || rArm == null) return;
            try
            {
                Quaternion lShWorld = lSh.parent != null
                    ? lSh.parent.rotation * lSh.localRotation : lSh.localRotation;
                Quaternion rShWorld = rSh.parent != null
                    ? rSh.parent.rotation * rSh.localRotation : rSh.localRotation;
                Vector3 lDir = (lArm.position - lSh.position).normalized;
                Vector3 rDir = (rArm.position - rSh.position).normalized;
                if (lDir.sqrMagnitude < 0.0001f || rDir.sqrMagnitude < 0.0001f) return;
                Vector3 lTgt = FoldTarget(transform.right * -1f);
                Vector3 rTgt = FoldTarget(transform.right * 1f);
                _foldL = SideFold(lShWorld, lDir, lTgt);
                _foldR = SideFold(rShWorld, rDir, rTgt);
                _foldReady = true;
                _swingAxisL = Quaternion.Inverse(lShWorld) * (transform.right * -1f);
                _swingAxisR = Quaternion.Inverse(rShWorld) * (transform.right * 1f);
                _swingReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione braccia: " + ex);
            }
        }

        private static Quaternion SideFold(Quaternion shWorld, Vector3 dirW, Vector3 tgtW)
        {
            Vector3 dirL = Quaternion.Inverse(shWorld) * dirW;
            Vector3 tgtL = Quaternion.Inverse(shWorld) * tgtW;
            if (dirL.sqrMagnitude < 0.0001f || tgtL.sqrMagnitude < 0.0001f)
                return Quaternion.identity;
            return Quaternion.FromToRotation(dirL.normalized, tgtL.normalized);
        }

        private static Vector3 FoldTarget(Vector3 lateral)
        {
            lateral.y = 0f;
            if (lateral.sqrMagnitude < 0.001f) lateral = Vector3.right;
            lateral.Normalize();
            return (Vector3.down * 0.955f + lateral * 0.30f).normalized;
        }

        private void CalibrateLimbAxes()
        {
            Vector3 lateral = transform.right;
            try
            {
                _hipAxisL = LocalFlexAxis(LUpLegPath, lateral);
                _hipAxisR = LocalFlexAxis(RUpLegPath, lateral);
                _kneeAxisL = LocalFlexAxis(LLegPath, lateral);
                _kneeAxisR = LocalFlexAxis(RLegPath, lateral);
                _elbowAxisL = LocalFlexAxis(LForeArmPath, lateral);
                _elbowAxisR = LocalFlexAxis(RForeArmPath, lateral);
                _legAxisReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione arti: " + ex);
            }
        }

        private Vector3 LocalFlexAxis(string bonePath, Vector3 worldLateral)
        {
            Transform bone = transform.Find(bonePath);
            if (bone == null) return worldLateral;
            Quaternion world = bone.rotation;
            return Quaternion.Inverse(world) * worldLateral;
        }

        private Quaternion HipFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _hipAxisR : _hipAxisL);
        }
        private Quaternion KneeFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _kneeAxisR : _kneeAxisL);
        }
        private Quaternion ElbowFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _elbowAxisR : _elbowAxisL);
        }
        private Quaternion FoldDelta(bool right)
        {
            if (_foldReady) return right ? _foldR : _foldL;
            return Quaternion.Euler(right ? 80f : -80f, 0f, 0f);
        }
        private Quaternion SwingDelta(bool right, float angle)
        {
            if (!_swingReady) return Quaternion.identity;
            return Quaternion.AngleAxis(angle, right ? _swingAxisR : _swingAxisL);
        }

        private void BuildProcedural()
        {
            if (!CanDriveProcedural())
            {
                City.OSM.OsmDiag.Log("[CharacterWalker][Procedural] SKIP su " +
                    gameObject.name + " (né hips né gamba sinistra: " +
                    "nessun clip procedurale può scandire le ossa)");
                ready = true;
                return;
            }
            CalibrateFold();
            CalibrateLimbAxes();
            anim = gameObject.GetComponent<Animation>();
            if (anim == null) anim = gameObject.AddComponent<Animation>();
            anim.AddClip(BuildLocomotionCached("walk", legSwingDeg, kneeBendDeg,
                armSwingDeg, walkCadence, walkBounceM, forwardLeanDeg * 0.4f, 3f),
                WalkState);
            anim.AddClip(BuildLocomotionCached("run", legSwingDeg * 1.5f,
                kneeBendDeg * 1.35f, armSwingDeg * 1.5f, runCadence, runBounceM,
                forwardLeanDeg, 8f), RunState);
            anim.AddClip(BuildIdleCached(), IdleState);
            current = IdleState;
            anim.Play(IdleState);

            if (transform.Find(HipsPath) == null ||
                transform.Find(LUpLegPath) == null)
            {
                Debug.LogWarning("[CharacterWalker] ossa non raggiunte su " +
                    gameObject.name + " hips=" +
                    (transform.Find(HipsPath) != null) + " lLeg=" +
                    (transform.Find(LUpLegPath) != null) + " animator=" +
                    (GetComponentInChildren<Animator>(true) != null));
            }
            City.OSM.OsmDiag.Log("[CharacterWalker][Procedural] build completata su " +
                gameObject.name + " hips=" + (transform.Find(HipsPath) != null) +
                " lLeg=" + (transform.Find(LUpLegPath) != null) +
                " useAnimator=" + useAnimator +
                (transform.Find(HipsPath) == null
                    ? " -> T-POSE (hips assenti)"
                    : " -> OK"));
            ready = true;
        }

        // Firma dello scheletro: la bake legge le localRotation a runtime,
        // quindi due rig con le stesse rest pose producono le stesse clip. La
        // includo nella chiave cosi' un prefab con rig diverso non riusa per
        // errore la clip di un altro.
        private string RigSignature()
        {
            var sb = new System.Text.StringBuilder(96);
            sb.Append(BoneSig(HipsPath)).Append('|')
              .Append(BoneSig(LUpLegPath)).Append('|')
              .Append(BoneSig(LLegPath)).Append('|')
              .Append(BoneSig(LShoulderPath)).Append('|')
              .Append(BoneSig(LArmPath));
            Transform hips = transform.Find(HipsPath);
            sb.Append('|').Append(hips != null
                ? hips.localPosition.y.ToString("F4") : "n");
            return sb.ToString();
        }

        private string BoneSig(string path)
        {
            Transform t = transform.Find(path);
            if (t == null) return "-";
            Quaternion r = t.localRotation;
            return r.x.ToString("F4") + "," + r.y.ToString("F4") + "," +
                   r.z.ToString("F4") + "," + r.w.ToString("F4");
        }

        private AnimationClip BuildLocomotionCached(string tag, float legSwing,
            float kneeBend, float armSwing, float cadence, float bounce,
            float leanDeg, float thighBiasDeg)
        {
            string key = tag + "|" + RigSignature() + "|" + legSwing + "|" +
                kneeBend + "|" + armSwing + "|" + cadence + "|" + bounce +
                "|" + leanDeg + "|" + thighBiasDeg;
            AnimationClip c;
            if (ClipCache.TryGetValue(key, out c) && c != null) return c;
            c = BuildLocomotion(tag, legSwing, kneeBend, armSwing, cadence,
                bounce, leanDeg, thighBiasDeg);
            ClipCache[key] = c;
            return c;
        }

        private AnimationClip BuildIdleCached()
        {
            string key = "idle|" + RigSignature();
            AnimationClip c;
            if (ClipCache.TryGetValue(key, out c) && c != null) return c;
            c = BuildIdle();
            ClipCache[key] = c;
            return c;
        }

        private AnimationClip BuildLocomotion(string tag, float legSwing,
            float kneeBend, float armSwing, float cadence, float bounce,
            float leanDeg, float thighBiasDeg)
        {
            float dur = 1f / Mathf.Max(0.1f, cadence);
            const int steps = 28;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float sinL = Mathf.Sin(ph);
                float sinR = Mathf.Sin(ph + Mathf.PI);

                rot.RotQ(t, LUpLegPath,
                    HipFlex(false, swingSign * (sinL * legSwing + thighBiasDeg)));
                rot.RotQ(t, RUpLegPath,
                    HipFlex(true, swingSign * (sinR * legSwing + thighBiasDeg)));
                float kneeL = Mathf.Clamp01(-sinL) * kneeBend;
                float kneeR = Mathf.Clamp01(-sinR) * kneeBend;
                rot.RotQ(t, LLegPath, KneeFlex(false, swingSign * kneeL));
                rot.RotQ(t, RLegPath, KneeFlex(true, swingSign * kneeR));
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, swingSign * (sinR * armSwing)) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, swingSign * (sinL * armSwing)) * FoldDelta(true));
                float elbowL = Mathf.Clamp01(Mathf.Abs(sinL)) * elbowBendDeg;
                float elbowR = Mathf.Clamp01(Mathf.Abs(sinR)) * elbowBendDeg;
                rot.RotQ(t, LForeArmPath, ElbowFlex(false, swingSign * elbowL));
                rot.RotQ(t, RForeArmPath, ElbowFlex(true, swingSign * elbowR));
                rot.Rot(t, SpinePath, leanDeg, sinR * 6f, sinR * 3f);
                rot.Rot(t, UpChestPath, leanDeg * 0.45f, sinL * 5f, 0f);
                pos.Pos(t, HipsPath, hipY + Mathf.Sin(ph * 2f) * bounce);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        private AnimationClip BuildIdle()
        {
            const float dur = 3.4f;
            const int steps = 20;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float breathe = Mathf.Sin(ph);
                rot.Rot(t, SpinePath, 1.2f * breathe, 0f, 0f);
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, 3f * breathe) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, 3f * breathe) * FoldDelta(true));
                pos.Pos(t, HipsPath, hipY + breathe * 0.004f);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        private Quaternion? BoneRest(string path)
        {
            Transform t = transform.Find(path);
            return t != null ? (Quaternion?)t.localRotation : null;
        }

        // ═══════════════════════════════════════════════════════════════
        // UPDATE COMUNE
        // ═══════════════════════════════════════════════════════════════

        public void SetSpeed(float metersPerSecond)
        {
            speed = metersPerSecond;
        }

        /// <summary>Stato a terra/aria: guida lo stato Jump del controller
        /// (parametro Bool IsGrounded). No-op per il mode procedurale.</summary>
        public void SetGrounded(bool grounded)
        {
            this.grounded = grounded;
        }

        /// <summary>Giorno 8: stato a terra "stabile" consegnato da
        /// PlayerController (tiene conto della finestra del magnete del
        /// Giorno 7). FootIK lo usa per non piantare i piedi in aria quando
        /// isGrounded fa flip per un frame sui cordoli.</summary>
        public bool Grounded { get { return grounded; } }

        /// <summary>GIORNO 5 - Aggiorna fase e contatore passi leggendo la
        /// FONTE dell'animazione: normalizedTime dello stato Animator per il
        /// player (Mixamo), fase della clip procedurale per gli NPC. Due
        /// appoggi per ciclo, rilevati sul cambio di meta-ciclo (0 -> 1).
        /// A 10 fps la fase salta di ~0,2 per frame: il cambio e' comunque
        /// visibile, quindi il passo non viene perso.</summary>
        private void UpdateStepCycle()
        {
            // Solo il player: con 270 NPC per chunk calcolare la fase per
            // tutti sarebbe costo sprecato (nessuno di loro ha camera/audio).
            if (cc == null) return;

            float phase;
            if (useAnimator && animator != null &&
                animator.runtimeAnimatorController != null && speed > 0.15f)
            {
                float norm = animator.GetCurrentAnimatorStateInfo(0).normalizedTime;
                phase = norm - Mathf.Floor(norm);
            }
            else if (!useAnimator && anim != null && current != IdleState &&
                     speed > 0.15f)
            {
                float cad = current == RunState ? runCadence : walkCadence;
                _procPhase += Mathf.Min(Time.deltaTime, 0.1f) * cad;
                _procPhase -= Mathf.Floor(_procPhase);
                phase = _procPhase;
            }
            else
            {
                phase = 0f;
            }

            float p = phase * 2f;
            int idx = Mathf.FloorToInt(p); // 0 oppure 1: un passo ciascuno
            if (speed > 0.6f)
            {
                if (idx != _lastStepIndex)
                {
                    if (_lastStepIndex >= 0) StepCount++;
                    _lastStepIndex = idx;
                }
                StepPhase = p - Mathf.Floor(p);
            }
            else
            {
                StepPhase = 0f;
                _lastStepIndex = -1;
            }

            if (StepCount == _lastAudioStep) return;
            _lastAudioStep = StepCount;
            if (grounded && speed > 0.6f) PlayFootstep();
        }

        /// <summary>Passo corto generato a runtime (4 varianti) senza nuovi
        /// asset audio, sullo stesso pattern del clacson di VehicleController:
        /// rumore filtrato + thump a 88-109 Hz, inviluppo quadratico.</summary>
        private void PlayFootstep()
        {
            if (_stepAudio == null)
            {
                _stepAudio = GetComponent<AudioSource>();
                if (_stepAudio == null) _stepAudio = gameObject.AddComponent<AudioSource>();
                _stepAudio.playOnAwake = false;
                _stepAudio.loop = false;
                _stepAudio.spatialBlend = 0f; // 2D: il passo del player e' sempre udibile
                _stepAudio.volume = 0.32f;
            }
            if (_stepClips == null) _stepClips = BuildFootstepClips();
            _stepAudio.PlayOneShot(_stepClips[StepCount & 3], 0.75f);
        }

        private static AudioClip[] BuildFootstepClips()
        {
            const int sampleRate = 22050;
            const float lenS = 0.085f;
            int n = Mathf.RoundToInt(sampleRate * lenS);
            var clips = new AudioClip[4];
            for (int k = 0; k < clips.Length; k++)
            {
                var data = new float[n];
                var rnd = new System.Random(1701 + k * 313);
                float lp = 0f;
                for (int i = 0; i < n; i++)
                {
                    float t = i / (float)sampleRate;
                    float env = 1f - i / (float)n;
                    env *= env;
                    float noise = (float)(rnd.NextDouble() * 2.0 - 1.0);
                    lp = lp * 0.55f + noise * 0.45f;
                    float thump = Mathf.Sin(TwoPi * (88f + k * 7f) * t) * 0.35f;
                    data[i] = (lp * 0.55f + thump) * env * 0.5f;
                }
                var clip = AudioClip.Create("Passo_" + k, n, 1, sampleRate, false);
                clip.SetData(data, 0);
                clips[k] = clip;
            }
            return clips;
        }

        private void Update()
        {
            if (!ready) return;

            if (useAnimator)
            {
                UpdateAnimatorMode();
            }
            else
            {
                UpdateProceduralMode();
            }

            UpdateStepCycle();
            DiagPlayerPulse();
        }

        private void UpdateProceduralMode()
        {
            if (anim == null) return;
            string want = IdleState;
            if (speed > 0.15f)
                want = speed > runThreshold ? RunState : WalkState;
            if (want != current)
            {
                current = want;
                anim.CrossFade(want, 0.18f);
            }
            AnimationState st = anim[current];
            if (st != null)
            {
                bool running = current == RunState;
                float baseCad = running ? runCadence : walkCadence;
                float stride = running ? runStrideM : walkStrideM;
                float cad = speed / Mathf.Max(0.1f, stride);
                st.speed = Mathf.Clamp(cad / Mathf.Max(0.1f, baseCad), 0.4f, 2.1f);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // ROTBAKER / POSBAKER - curve procedurali legacy
        // ═══════════════════════════════════════════════════════════════

        private sealed class RotBaker
        {
            private readonly Dictionary<string, List<float[]>> keys =
                new Dictionary<string, List<float[]>>();
            private readonly System.Func<string, Quaternion?> restLookup;

            public RotBaker(System.Func<string, Quaternion?> restLookup)
            {
                this.restLookup = restLookup;
            }

            public void Rot(float time, string path, float x, float y, float z)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * Quaternion.Euler(x, y, z);
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            public void RotQ(float time, string path, Quaternion delta)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * delta;
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            public void Flush(AnimationClip clip)
            {
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    List<float[]> list = kv.Value;
                    const string comps = "xyzw";
                    for (int c = 0; c < 4; c++)
                    {
                        AnimationCurve curve = new AnimationCurve();
                        foreach (float[] k in list)
                            curve.AddKey(k[0], k[1 + c]);
                        clip.SetCurve(kv.Key, typeof(Transform),
                            "m_LocalRotation." + comps[c], curve);
                    }
                }
            }
        }

        private sealed class PosBaker
        {
            private readonly Dictionary<string, List<float[]>> keys =
                new Dictionary<string, List<float[]>>();

            public void Pos(float time, string path, float localY)
            {
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, localY });
            }

            public void Flush(AnimationClip clip)
            {
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    AnimationCurve curve = new AnimationCurve();
                    foreach (float[] k in kv.Value)
                        curve.AddKey(k[0], k[1]);
                    clip.SetCurve(kv.Key, typeof(Transform), "m_LocalPosition.y", curve);
                }
            }
        }
    }

    /// <summary>Relay di OnAnimatorIK. Unity consegna il callback IK solo ai
    /// componenti sullo stesso GameObject dell'Animator; CharacterWalker vive
    /// sul player root (Animator sul figlio hero), quindi senza questo bridge
    /// il Foot IK non scatta mai. Installato da SetupAnimatorMode.</summary>
    public class AnimatorIKRelay : MonoBehaviour
    {
        public CharacterWalker walker;

        private void OnAnimatorIK(int layerIndex)
        {
            if (walker != null) walker.RunFootIK(layerIndex);
        }
    }
}

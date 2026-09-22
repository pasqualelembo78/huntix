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

        // ── Parametri Animator mode ──
        [Header("Animator Mode (player)")]
        public float walkSpeed = 3.2f;
        public float runSpeed = 7.5f;

        // ── Foot IK ──
        [Header("Foot IK")]
        public bool footIKEnabled = true;
        public float ikRayDistance = 1.5f;
        public float ikSmoothSpeed = 12f;
        public float ikMaxOffset = 0.3f;
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
        private float _legSpanRest = float.NaN;
        private float _groundDiagAt;
        private float _pulseAt;
        private float _ikFiredAt = float.MinValue;
        private bool _diagToastShown;
        private static readonly RaycastHit[] IkHitsBuffer = new RaycastHit[8];

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

        private void Start()
        {
            animator = GetComponentInChildren<Animator>(true);
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
                " feetFromPivot=" + feetFromPivot.ToString("F2"));
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
            // Il controller Mixamo usa "Speed"/"IsGrounded"; il fallback Kenney
            // usa "IsMoving". SetFloat/SetBool su parametri inesistenti sono
            // no-op (sicuri).
            animator.SetFloat("Speed", speed);
            animator.SetBool("IsMoving", speed > 0.15f);
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

            // lift dai piedi IK (curb/step: il piede appoggia sopra il livello
            // di riferimento) e corpo affondato: massimo dei due, solo positivo;
            // il negativo (davanti a un gradino) resta limitato a ikMaxOffset.
            float footLift = Mathf.Min(_ikPosL.y, _ikPosR.y) - groundRef;
            float bodySinkLift = 0f;
            Transform hipsBone = animator.GetBoneTransform(HumanBodyBones.Hips);
            if (hipsBone != null && grounded)
                bodySinkLift = (groundRef + _legSpanRest) - hipsBone.position.y;
            float target = Mathf.Max(footLift, bodySinkLift);
            float offset = Mathf.Clamp(target, -ikMaxOffset, ikMaxLift);
            _hipOffsetY = Mathf.Lerp(_hipOffsetY, offset, ikSmoothSpeed * Time.deltaTime);

            if (hipsBone != null)
            {
                // Cattura ogni frame: il clip corrente (idle/walk/run) puo'
                // avere un'altezza pelvi diversa; fissarla al primo frame
                // (idle) rendeva la camminata piu' bassa dell'originale.
                _hipsBaseY = hipsBone.localPosition.y;
                Vector3 hp = hipsBone.localPosition;
                hp.y = _hipsBaseY + _hipOffsetY;
                hipsBone.localPosition = hp;
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
        /// cosi' si vede subito se il ray parte sottoterra o non trova il suolo.</summary>
        private void DiagGroundRay(AvatarIKGoal goal, Vector3 origin, int hits,
            Vector3 hitPoint, Vector3 hitNormal, float groundRef,
            float weight, float gap)
        {
            float now = UnityEngine.Time.time;
            if (now < _groundDiagAt) return;
            _groundDiagAt = now + 2f;
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
                targetPos = h.point + Vector3.up * 0.02f;
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
            anim.AddClip(BuildLocomotion("walk", legSwingDeg, kneeBendDeg,
                armSwingDeg, walkCadence, walkBounceM, forwardLeanDeg * 0.4f, 3f), WalkState);
            anim.AddClip(BuildLocomotion("run", legSwingDeg * 1.5f,
                kneeBendDeg * 1.35f, armSwingDeg * 1.5f, runCadence, runBounceM,
                forwardLeanDeg, 8f), RunState);
            anim.AddClip(BuildIdle(), IdleState);
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

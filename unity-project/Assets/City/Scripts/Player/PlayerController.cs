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
            animator = GetComponentInChildren<Animator>();
            City.OSM.OsmDiag.Log("[PlayerController][Awake] animator=" +
                (animator != null ? animator.gameObject.name : "NULL") +
                " children=" + transform.childCount);
            walker = CharacterWalker.AttachIfNeeded(gameObject);
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

            Vector3 move = Vector3.zero;
            float targetSpeed = 0f;

            if (moveInput.sqrMagnitude > 0.02f)
            {
                CameraRig rig = CameraRig.Instance;
                Vector3 forward = rig != null
                    ? Vector3.ProjectOnPlane(rig.transform.forward, Vector3.up).normalized
                    : transform.forward;
                Vector3 right = Vector3.Cross(Vector3.up, forward);
                Vector3 dir = forward * moveInput.y + right * moveInput.x;
                if (dir.sqrMagnitude > 1f) dir.Normalize();

                move = dir;
                moveDirection = dir;
                targetSpeed = Mathf.Lerp(walkSpeed, runSpeed, Mathf.InverseLerp(0.35f, 1f, moveInput.magnitude));

                // Sospensioni del corpo: si inclina lievemente seguendo il
                // pendio (pitch/roll) ma mai oltre MaxBodyInclineDeg, cosi' su
                // un dosso si "sbilancia" senza mai cadere all'indietro.
                Vector3 bodyUp = TileElevation.SlopeUpAtWorld(
                    transform.position, MaxBodyInclineDeg);
                Quaternion targetRotation = Quaternion.LookRotation(dir, bodyUp);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRotation, turnSpeed * Time.deltaTime);
            }

            if (sprintRemain > 0f) targetSpeed *= sprintBoost;

            currentSpeed = Mathf.MoveTowards(currentSpeed, targetSpeed, 10f * Time.deltaTime);
            IsMoving = currentSpeed > 0.05f;

            if (!controller.enabled) return;

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
                ApplyFlight(move);
            }
            else
            {
                if (controller.isGrounded && velocity.y < 0f) velocity.y = -1f;
                float g = (Realm == AfterlifeRealm.PARADISO) ? gravity * flightGravityScale : gravity;
                velocity.y += g * Time.deltaTime;
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
                    dashRemain = Mathf.Max(0f, dashRemain - Time.deltaTime);
                    velocity.x = dashDirection.x * dashSpeed;
                    velocity.z = dashDirection.z * dashSpeed;
                }

                // Knockback (pericoli Inferno): spinta orizzontale breve che
                // sovrascrive la velocita' per far spiccare il salto/scivolata.
                if (knockbackRemain > 0f)
                {
                    knockbackRemain = Mathf.Max(0f, knockbackRemain - Time.deltaTime);
                    velocity.x = knockbackVel.x;
                    velocity.z = knockbackVel.z;
                }

                controller.Move(velocity * Time.deltaTime);

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

            if (walker != null)
            {
                walker.SetSpeed(currentSpeed);
                walker.SetGrounded(controller.enabled && controller.isGrounded);
            }
            if (sprintRemain > 0f) sprintRemain = Mathf.Max(0f, sprintRemain - Time.deltaTime);
            TrackWalkDistance();
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
        private void ApplyFlight(Vector3 move)
        {
            Vector3 forward = transform.forward;
            Vector3 right = transform.right;
            Vector3 dir = forward * moveInput.y + right * moveInput.x;
            if (dir.sqrMagnitude > 1f) dir.Normalize();

            float up;
            if (flightVertical > 0) up = flightUpSpeed;
            else if (flightVertical < 0) up = -flightUpSpeed * 0.8f;
            else up = velocity.y * 0.9f; // mantiene la velocita' verticale attuale

            velocity = new Vector3(dir.x * currentSpeed, up, dir.z * currentSpeed);

            // galleggia: gravita' quasi assente, mai sopra -2 m/s di caduta
            velocity.y += gravity * flightGravityScale * 0.5f * Time.deltaTime;
            if (velocity.y < -2f) velocity.y = -2f;

            controller.Move(velocity * Time.deltaTime);
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
            }
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
                    ? Vector3.ProjectOnPlane(rig.transform.forward, Vector3.up).normalized
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

        /// <summary>Accumula la distanza percorsa e la consegna alle missioni
        /// a ogni metro (batching per non spammare il MissionManager a frame).</summary>
        private void TrackWalkDistance()
        {
            if (!IsMoving) return;
            float meters = new Vector3(velocity.x, 0f, velocity.z).magnitude * Time.deltaTime;
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
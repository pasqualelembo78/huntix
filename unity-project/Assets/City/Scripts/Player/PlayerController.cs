using UnityEngine;
using City.Afterlife;

namespace City.Player
{
    [RequireComponent(typeof(CharacterController))]
    public class PlayerController : MonoBehaviour
    {
        /// <summary>Riferimento statico al player della scena (uno solo).</summary>
        public static PlayerController Instance { get; private set; }

        public float walkSpeed = 4f;
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

        private int airJumpCount;
        private const int MaxAirJumps = 1;

        private float dashRemain;
        private float canDashAt;
        private Vector3 dashDirection;

        private bool flying;
        private int flightVertical; // +1 sali, -1 scendi, 0 neutro (in volo)

        private float canJumpAt;
        private float sprintRemain;

        private CharacterController controller;
        private Animator animator;
        private CharacterWalker walker;
        private Vector2 moveInput;
        private Vector3 velocity;
        private float currentSpeed;

        // telemetria camminata per le missioni WalkDistance: accumula i metri
        // percorsi e li notifica a ogni metro compiuto (MissionManager mai
        // aggiornato prima: le missioni "Cammina X metri" restavano al 0)
        private float walkTelemetry;

        public bool IsMoving { get; private set; }

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
            controller = GetComponent<CharacterController>();
            animator = GetComponentInChildren<Animator>();
            walker = CharacterWalker.AttachIfNeeded(gameObject);
            if (GetComponent<PlayerAppearance>() == null)
                gameObject.AddComponent<PlayerAppearance>();
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
                targetSpeed = Mathf.Lerp(walkSpeed, runSpeed, Mathf.InverseLerp(0.35f, 1f, moveInput.magnitude));

                Quaternion targetRotation = Quaternion.LookRotation(dir, Vector3.up);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRotation, turnSpeed * Time.deltaTime);
            }

            if (sprintRemain > 0f) targetSpeed *= sprintBoost;

            currentSpeed = Mathf.MoveTowards(currentSpeed, targetSpeed, 10f * Time.deltaTime);
            IsMoving = currentSpeed > 0.05f;

            if (!controller.enabled) return;

            // Velocita' orizzontale: default camminata, override se dash attivo
            velocity = move * currentSpeed;

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

                controller.Move(velocity * Time.deltaTime);
            }

            if (animator != null) animator.SetFloat("Speed", currentSpeed);
            if (walker != null) walker.SetSpeed(currentSpeed);
            if (sprintRemain > 0f) sprintRemain = Mathf.Max(0f, sprintRemain - Time.deltaTime);
            TrackWalkDistance();
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

        /// <summary>Salto: impulso verticale se il player e a terra. In Inferno
        /// e' disponibile anche un secondo salto in aria (doppio salto).</summary>
        public void DoJump()
        {
            if (controller == null) return;
            if (Time.unscaledTime < canJumpAt) return;

            bool groundJump = controller.isGrounded;
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
                return;
            }
        }

        /// <summary>Sprint: breve scatto in piu, utile per superare tratti
        /// lunghi a piedi (ronda/corriere).</summary>
        public void DoSprint()
        {
            sprintRemain = sprintDuration;
        }

        /// <summary>Dash (Inferno): rapida scivolata orizzontale nella direzione
        /// corrente. Cooldown, non funziona in volo.</summary>
        public void DoDash()
        {
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

        /// <summary>Abilita il volo (Paradiso).</summary>
        public void StartFlight()
        {
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
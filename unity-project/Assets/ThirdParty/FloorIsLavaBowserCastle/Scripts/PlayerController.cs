using UnityEngine.SceneManagement;
using UnityEngine;
using System;
using System.Collections;

namespace FloorIsLava
{
    public class PlayerController : MonoBehaviour
    {
        Rigidbody rb;
        AudioSource audioSource;

        public AudioClip pickupCoinsAudioClip;

        //Movement
        public float movementForce = 0.1f;
        public float airAccelerartionFactor = 0.5f;
        public ParticleSystem groundParticles;
        internal bool allowPlayerMovement = true;    //for disabling player movement on death

        private float defaultCameraFov;

        //Jump
        public AudioClip jumpAudioClip;
        public float jumpForce = 3;

        private bool isGrounded = false;
        private bool isJumping = false;   //fixes a bug that allows double jump becasue of coyote time and how unity handle OnCollision events

        /// <summary>Esposto per l'avatar (MiaCityAvatar): vera se l'anima e' a contatto.</summary>
        public bool IsGrounded { get { return isGrounded; } }
        /// <summary>Esposto per l'avatar (MiaCityAvatar): vera durante la levitazione hover.</summary>
        public bool IsHovering { get { return isHovering; } }

        public float coyoteTimeFactor = 0.2f;
        private float coyoteTimeCounter;

        public float jumpBufferTimeFactor = 0.2f;
        private float jumpBufferCounter;

        //Doppio salto in aria: come nella citta' (Inferno). Dopo il primo
        //salto si puo' risaltare a mezz'aria una volta, premendo di nuovo.
        [Tooltip("Impulso del salto in aria (doppio salto).")]
        public float doubleJumpForce = 4.5f;
        [Tooltip("Quanti salti in aria sono concessi dopo quello da terra.")]
        public int maxAirJumps = 1;
        private int airJumpCount;

        //Death
        public Camera cameraToFadeToBlack;
        public AudioClip deathAudioClip;
        public AudioClip bruhMomentAudioClip;
        public Material deathMaterial;

        //Victory
        public AudioClip victoryAudioClip;
        internal bool isGameWon = false;

        //UI
        internal int collectedCoins = 0;
        internal float runTime = new();
        internal bool isMute = false;
        private float initialSceneTime;

        //Integrazione afterlife (Floor-Is-Lava come regno INFERNO): quando
        //settato, la morte non ricarica la scena (il flusso afterlife
        //(RealmSceneManager) trasporta l'anima al Purgatorio).
        internal bool suppressSceneReload = false;

        //Input mobile (Joystick Pack): se assegnato, il movimento proviene
        //dal joystick; altrimenti la tastiera/assi di input classici.
        public Joystick joystick;

        //Salto mobile: col nuovo input system (activeInputHandler=2) l'input
        //legacy (Input.GetButton "Jump") non arriva MAI su Android, quindi il
        //salto restava impossibile. Il pulsante "SALTA" del bridge chiama
        //BeginJump/EndJump e qui dentro sostituiscono il tasto/asse.
        private bool _jumpPressed;
        private bool _jumpReleased;

        /// <summary>Il pulsante SALTA (touch) e' stato premuto.</summary>
        public void BeginJump() { _jumpPressed = true; }

        /// <summary>Il pulsante SALTA (touch) e' stato rilasciato: taglia la
        /// salita in corso per variare l'altezza del salto.</summary>
        public void EndJump()
        {
            _jumpPressed = false;
            _jumpReleased = true;
        }

        //--- Guida stabile (afterlife) ---
        [Tooltip("Zona morta d'ingresso del joystick (0.15 = il 15% della corsa non muove).")]
        public float inputDeadZone = 0.15f;
        [Tooltip("Angolo (gradi) attorno all'avanti entro cui l'anima procede DRITTA, ignorando il componente laterale del joystick.")]
        public float straightSteerAngle = 22f;
        [Tooltip("Smorzamento della deriva laterale sull'asse non comandato (0..0.5 per frame).")]
        [Range(0f, 0.5f)] public float lateralDamp = 0.12f;
        [Tooltip("Velocita' orizzontale massima dell'anima (tiene su i pendii senza sbandare).")]
        public float maxSpeed = 11f;
        private Vector2 _moveInput;

        //--- Levitazione dell'anima (afterlife) ---
        [Tooltip("Raggio fisico della sfera (per calcolare la quota di levitazione).")]
        public float ballRadius = 0.5f;
        [Tooltip("Quota di levitazione: distanza desiderata del fondo sfera dal pavimento.")]
        public float hoverHeight = 0.45f;
        [Tooltip("Rigidezza della sospensione verticale (alto = quota tenuta, niente saltelli).")]
        public float hoverStiffness = 55f;
        [Tooltip("Smorzamento verticale: frena subito rimbalzi e urti contro i bordi degli scalini.")]
        public float hoverDamp = 10f;
        [Tooltip("Distanza max del raycast verso il pavimento (oltre = ricade con la gravita').")]
        public float hoverRayDistance = 8f;
        [Tooltip("Partecipa alla levitazione solo se il pavimento trovato sta entro questa distanza dal fondo sfera (evita che l'anima venga 'risucchiata' verso un piano piu' basso quando si e' vicino al bordo).")]
        public float hoverSnapGap = 1.5f;
        [Tooltip("Normale minima del pavimento per levitare (0.5 = piani e pendenze leggere; le rampe ripide tornano al contatto fisico per arrampicarle).")]
        public float hoverNormalSlope = 0.5f;
        private bool isHovering;

        //--- Guardia anti-caduta (modalita' facile) ---
        // Il regno Inferno e' risultato troppo difficile: per ora l'anima NON
        // puo' mai cadere. Funziona come una "barriera ai lati"/pavimento
        // invisibile: mentre l'anima sta bene su un pavimento registriamo il
        // punto sicuro; se l'anima scende di oltre fallGuardTolerance sotto
        // l'ultimo punto sicuro SENZA pavimento sotto (entro fallSearchRange)
        // viene riportata li' con velocita' azzerata. Resta permesso saltare su
        // altre piattaforme e scendere verso pavimenti piu' bassi entro il
        // raggio di ricerca; i fossi/lava non sono mai raggiungibili.
        [Tooltip("Quanti metri sotto l'ultimo punto sicuro l'anima resta (oltre = bloccata e riportata su).")]
        public float fallGuardTolerance = 1.2f;
        [Tooltip("Raggio sotto l'anima entro cui la presenza di un pavimento lascia cadere liberamente (salti/scendite verso piattaforme piu' basse).")]
        public float fallSearchRange = 10f;
        private bool _haveSafeRef;
        private Vector3 _safeRef;

        private bool IsFloor(Collision collision) => collision.gameObject.CompareTag("Floor") || collision.gameObject.CompareTag("FloorVictory");

        void Start()
        {
            audioSource = GetComponent<AudioSource>();
            rb = GetComponent<Rigidbody>();
            var cam0 = Camera.main;
            defaultCameraFov = cam0 != null ? cam0.fieldOfView : 60f;
            initialSceneTime = Time.realtimeSinceStartup;
        }


        void FixedUpdate()
        {
            if (allowPlayerMovement)
            {
                HandlePlayerJump();
                UpdateHover();
                HandlePlayerMovement();
                EnforceFallGuard();
            }
        }

        private void Update()
        {
            if (!isGameWon)
            {
                runTime = (float)Math.Round(Time.realtimeSinceStartup - initialSceneTime, 2);
            }
            
            if (Input.GetKeyDown(KeyCode.M))
            {
                isMute = !isMute;
                audioSource.mute = isMute;
            }
        }

        void HandlePlayerJump()
        {
            //Coyote time allows player to jump a brief moment after being on air.
            //Durante l'hover (levitazione dell'anima) il collider resta sospeso
            //a mezz'aria SENZA contatto fisico: OnCollisionStay non scatta e
            //isGrounded resterebbe falso per sempre, bloccando il salto.
            //Trattare l'hover come grounded in modo che il salto funzioni anche
            //fluttuando sul pavimento.
            if (isGrounded || isHovering)
            {
                coyoteTimeCounter = coyoteTimeFactor;
                airJumpCount = 0;
            }
            else
            {
                coyoteTimeCounter -= Time.deltaTime;
            }

            //Jump buffer allows player to jump for a brief moment before touching the ground
            //(il flag _jumpPressed sostituisce l'input legacy "Jump", che col
            //nuovo input system non arriva mai su Android)
            if (_jumpPressed || Input.GetButton("Jump"))
            {
                jumpBufferCounter = jumpBufferTimeFactor;
                _jumpPressed = false;
            }
            else
            {
                jumpBufferCounter -= Time.deltaTime;
            }

            if (jumpBufferCounter > 0f && !isJumping)
            {
                if (coyoteTimeCounter > 0f)
                {
                    // Primo salto: da terra o hover.
                    rb.AddForce(0, jumpForce, 0, ForceMode.Impulse);

                    if (audioSource != null && jumpAudioClip != null)
                        audioSource.PlayOneShot(jumpAudioClip, 0.1f);
                    isGrounded = false;
                    coyoteTimeCounter = 0f;
                    jumpBufferCounter = 0f;
                    airJumpCount = 1;
                    StartCoroutine(JumpCooldown());
                }
                else if (airJumpCount < maxAirJumps)
                {
                    // Doppio salto: a mezz'aria, azzera prima la verticale
                    // discendente/ascendente residua e dara' un nuovo impulso.
                    Vector3 v = rb.velocity;
                    rb.velocity = new Vector3(v.x, 0f, v.z);
                    rb.AddForce(0, doubleJumpForce, 0, ForceMode.Impulse);

                    if (audioSource != null && jumpAudioClip != null)
                        audioSource.PlayOneShot(jumpAudioClip, 0.1f);
                    coyoteTimeCounter = 0f;
                    jumpBufferCounter = 0f;
                    airJumpCount++;
                    StartCoroutine(JumpCooldown());
                }
            }
        
            if ((_jumpReleased || Input.GetButtonUp("Jump")) && rb.velocity.y > 0f)
            {
                _jumpReleased = false;
                rb.AddForce(new Vector3(0f, -rb.velocity.y * 0.5f, 0f), ForceMode.Impulse);   // Smooth deceleration when releasing the jump
                //_coyoteTimeCounter = 0f;
            }
        }
    
        private IEnumerator JumpCooldown()
        {
            isJumping = true;
            yield return new WaitForSeconds(0.3f);
            isJumping = false;
        }

        /// <summary>Levitazione dell'anima: resta sospesa a quota
        /// hoverHeight dal pavimento con una molla verticale smorzata, al posto
        /// del contatto fisico. Cosi' non saltella sulle giunture delle
        /// piattaforme e "cammina in aria". Se sotto non c'e' pavimento entro
        /// hoverSnapGap (fossi/lava o salti troppo alti) la gravita' fa
        /// ricadere l'anima. Sulla piattaforma di arrivo (FloorVictory) la
        /// vittoria scatta dal raycast, altrimenti con la levitazione non ci
        /// sarebbe mai contatto fisico e il livello non finirebbe.</summary>
        private void UpdateHover()
        {
            isHovering = false;
            if (isGameWon) return;

            // Durante un salto la molla di levitazione va sospesa: subito dopo
            // l'impulso l'anima sale sopra hoverHeight, "error" diventa
            // negativo e forceY (molto negativa, perche' anche il termine
            // -hoverDamp*velocity punta in giu') riporterebbe subito l'anima
            // a quota d'attesa, annullando il salto. Con isJumping attivo si
            // vola liberi e l'hover si riaggancia da solo al rientro.
            if (isJumping) return;

            RaycastHit hit;
            if (Physics.Raycast(rb.position, Vector3.down, out hit, hoverRayDistance,
                    Physics.DefaultRaycastLayers, QueryTriggerInteraction.Ignore))
            {
                var go = hit.collider.gameObject;
                bool isGoal = go.CompareTag("FloorVictory");
                if ((go.CompareTag("Floor") || isGoal) && hit.normal.y > hoverNormalSlope)
                {
                    float gap = hit.distance - ballRadius;
                    if (gap <= hoverSnapGap)
                    {
                        if (isGoal)
                        {
                            HandlePlayerVictory();
                            return;
                        }

                        float error = hoverHeight - gap;
                        float forceY = Mathf.Clamp(hoverStiffness * error - hoverDamp * rb.velocity.y, -180f, 120f);
                        rb.AddForce(0f, forceY, 0f, ForceMode.Acceleration);
                        isHovering = true;
                    }
                }
            }
        }

        /// <summary>Barriera anti-caduta: registra il punto sicuro quando
        /// l'anima sta su un pavimento, poi blocca qualsiasi discesa oltre la
        /// tolleranza se sotto non c'e' pavimento (fossi/lava). Effetto:
        /// il personaggio non puo' MAI cadere, come se ci fosse una barriera
        /// invisibile ai lati.</summary>
        private void EnforceFallGuard()
        {
            if (isGameWon) return;

            if (isGrounded || isHovering)
            {
                _haveSafeRef = true;
                _safeRef = rb.position;
            }
            if (!_haveSafeRef) return;

            // C'e' un pavimento sotto entro il raggio di ricerca? Allora e'
            // una caduta controllata (verso una piattaforma piu' bassa o
            // l'atterraggio): lascia scendere.
            if (FloorBelow(fallSearchRange)) return;

            // Sceso troppo sotto il punto sicuro senza nulla di appoggiabile:
            // barriera! Riporta l'anima SU, mai in lava.
            if (rb.position.y <= _safeRef.y - fallGuardTolerance)
            {
                RescueBall();
            }
        }

        private bool FloorBelow(float maxDist)
        {
            RaycastHit hit;
            if (!Physics.Raycast(rb.position, Vector3.down, out hit, maxDist,
                    Physics.DefaultRaycastLayers, QueryTriggerInteraction.Ignore))
                return false;
            var g = hit.collider.gameObject;
            return g.CompareTag("Floor") || g.CompareTag("FloorVictory");
        }

        private void RescueBall()
        {
            Debug.Log("[InfernoGuard] caduta bloccata: anima riportata al punto sicuro.");
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
            rb.position = _safeRef;
            _safeRef = rb.position;
        }

        /// <summary>Elabora l'input (joystick o tastiera): zona morta, poi
        /// normalizzazione del cerchio e aggancio alla direzione: finche' il
        /// joystick punta quasi dritto (entro straightSteerAngle) il componente
        /// laterale viene ignorato, cosi' andare "avanti" resta dritto e si
        /// svolta solo spingendo chiaramente a sinistra/destra.</summary>
        private Vector2 ProcessInput()
        {
            float rawX = joystick != null ? joystick.Horizontal : Input.GetAxis("Horizontal");
            float rawZ = joystick != null ? joystick.Vertical : Input.GetAxis("Vertical");

            float magnitude = Mathf.Sqrt(rawX * rawX + rawZ * rawZ);
            if (magnitude < inputDeadZone) return Vector2.zero;

            float x = rawX, z = rawZ;
            if (magnitude > 1f)
            {
                x /= magnitude;
                z /= magnitude;
            }

            float snapRatio = Mathf.Tan(straightSteerAngle * Mathf.Deg2Rad);
            if (Mathf.Abs(x) < Mathf.Abs(z) * snapRatio)
                x = 0f;

            return new Vector2(x, z);
        }

        void HandlePlayerMovement()
        {
            //commit die
            if (Input.GetKey(KeyCode.R))
            {
                HandlePlayerDeath();
            }

            bool grounded = isGrounded || isHovering;
            float finalMovementForce = grounded ? movementForce : movementForce * airAccelerartionFactor;

            if (grounded && groundParticles != null && !groundParticles.isPlaying)
                groundParticles.Play();
            else if (!grounded && groundParticles != null && groundParticles.isPlaying)
                groundParticles.Stop();

            _moveInput = ProcessInput();

            //Player movement on the X axis
            if (Mathf.Abs(_moveInput.x) > inputDeadZone)
            {
                rb.AddForce(finalMovementForce * _moveInput.x, 0, 0, ForceMode.VelocityChange);
            }

            //Player movement on the Z axis
            if (Mathf.Abs(_moveInput.y) > inputDeadZone)
            {
                rb.AddForce(0, 0, finalMovementForce * _moveInput.y, ForceMode.VelocityChange);
            }

            //Smorza la deriva sull'asse non comandato (l'anima ha drag 0: senza
            //questo, appena la strada ha una leggera pancia il giocatore sbanda).
            Vector3 v = rb.velocity;
            if (Mathf.Abs(_moveInput.x) <= inputDeadZone)
                v.x *= 1f - lateralDamp;
            if (Mathf.Abs(_moveInput.y) <= inputDeadZone)
                v.z *= 1f - lateralDamp;

            //Limita la velocita' orizzontale: tiene su i pendii e impedisce
            //che una spinta accidentale faccia precipitare l'anima.
            Vector3 horiz = new Vector3(v.x, 0f, v.z);
            float speed = horiz.magnitude;
            if (speed > maxSpeed)
                horiz *= maxSpeed / speed;
            v.x = horiz.x;
            v.z = horiz.z;
            rb.velocity = v;

            float targetFov = rb.velocity.z > 0 ? defaultCameraFov + rb.velocity.z * 1.5f : defaultCameraFov;
            Camera.main.fieldOfView = Mathf.Lerp(Camera.main.fieldOfView, targetFov, Time.fixedDeltaTime);
        }

        private void HandlePlayerDeath()
        {
            if (groundParticles != null && groundParticles.isPlaying)
                groundParticles.Stop();
        
            Invoke("ResetScene", 2.32f);

            var mr = GetComponent<MeshRenderer>();
            if (mr != null && deathMaterial != null) mr.material = deathMaterial;

            if (audioSource != null) audioSource.Stop();
            if (audioSource != null && deathAudioClip != null)
                audioSource.PlayOneShot(deathAudioClip, 1f);


            rb.rotation = Quaternion.Euler(0,90,0); ////makes the player face the camera    
            rb.angularVelocity = Vector3.zero;
            rb.velocity = Vector3.zero;
            rb.AddForce(0, 25, 0, ForceMode.Impulse);
            allowPlayerMovement = false;
        }

        private void HandlePlayerVictory()
        {
            isGameWon = true;
            if (audioSource != null) audioSource.Stop();
            if (audioSource != null && victoryAudioClip != null)
                audioSource.PlayOneShot(victoryAudioClip, 1f);
        }

        private void ResetScene() {
            // Integrazione afterlife: quando la scena e' il regno Inferno del
            // flusso afterlife non ricaricare (la scena viene sostituita dal
            // Purgatorio tramite RealmSceneManager).
            if (suppressSceneReload) return;
            if (cameraToFadeToBlack != null) cameraToFadeToBlack.cullingMask = 0;
            SceneManager.LoadScene(SceneManager.GetActiveScene().name);
        }

        void OnCollisionEnter(Collision collision)
        {
            if (allowPlayerMovement)
            {
                if (collision.gameObject.CompareTag("Death"))
                {
                    HandlePlayerDeath();
                }
                if (!isGameWon && collision.gameObject.CompareTag("FloorVictory"))
                {
                    HandlePlayerVictory();
                }
            }
        }

        void OnCollisionStay(Collision collision)
        {
            if (allowPlayerMovement)
            {
                if (collision.contacts.Length == 0) return;
                var contactPoint = collision.contacts[0];
                if (IsFloor(collision) && contactPoint.normal.y >= 0.34)
                {
                    isGrounded = true;
                }
 
            }
        }

        void OnCollisionExit(Collision collision)
        {
            if (IsFloor(collision))
            {
                isGrounded = false;
                jumpBufferCounter = 0f;
            }
        }

        private void OnTriggerEnter(Collider collider)
        {
            if (allowPlayerMovement && !isGameWon)
            {
                if (collider.gameObject.CompareTag("Coin"))
                {
                    audioSource.PlayOneShot(pickupCoinsAudioClip, 0.5f);
                    collectedCoins++;
                    if (collectedCoins % 10 == 0) TrySyncCoinXp();
                    Destroy(collider.gameObject);
                }

                if (collider.gameObject.CompareTag("BruhMomment"))
                {
                    audioSource.PlayOneShot(bruhMomentAudioClip, 3f);
                }
            }
        }

        private void TrySyncCoinXp()
        {
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                    "CityXpEarned", "{\"xp\":1,\"source\":\"inferno_monete\"}");
            }
            catch (System.Exception) { }
        }
    }
}

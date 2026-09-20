using UnityEngine;
using City.OSM;
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
        // Prima persona stile Brookhaven (icona camera): la camera sale alla
        // testa del player. Vale solo a piedi: in auto resta terza persona.
        public bool firstPerson = false;
        public const float FirstPersonHeight = 1.55f;
        private float _fpBlend = 0f;

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

        private void Start()
        {
            if (target == null)
            {
                GameObject p = GameObject.FindGameObjectWithTag("Player");
                if (p != null) target = p.transform;
            }
            if (target != null) yaw = target.eulerAngles.y;
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
        }

        public void ToggleFirstPerson()
        {
            firstPerson = !firstPerson;
            City.OSM.OsmDiag.Log("[Brookhaven][Camera] ToggleFirstPerson -> " + firstPerson);
        }

        public void SetFirstPerson(bool on)
        {
            firstPerson = on;
        }

        public void SetDrivingMode(bool driving)
        {
            drivingMode = driving;
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

        private void UpdateIndoorBlend()
        {
            float dt = Time.deltaTime;
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
            yaw -= _gyroRateEma * GyroSensitivity * Time.deltaTime;

            float rX = Mathf.Abs(rate.x) < GyroDeadzone ? 0f : rate.x;
            _gyroPitchEma = Mathf.Lerp(_gyroPitchEma, rX, GyroSmoothing);
            _gyroPitchOffset = Mathf.Clamp(
                _gyroPitchOffset - _gyroPitchEma * GyroSensitivity * Time.deltaTime,
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

        private void LateUpdate()
        {
            if (target == null) return;

            // In guida la camera resta SEMPRE in terza persona alta dietro
            // l'auto (vede il veicolo da sopra): gli override indoor di
            // concessionaria/officina/garage e dei negozi NON devono tirare la
            // camera vicino mentre si guida, altrimenti sembra di passare in
            // prima persona. Gli override si applicano solo a piedi.
            if (!drivingMode)
            {
                if (indoorOverrideActive) InitializeIndoorBlend();
                UpdateIndoorBlend();
            }

            // blend prima/terza persona (solo a piedi): la camera scivola
            // dolcemente alla testa del player.
            float fpTarget = (firstPerson && !drivingMode) ? 1f : 0f;
            _fpBlend = Mathf.Lerp(_fpBlend, fpTarget, Time.deltaTime * 6f);
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

            RaycastHit hit;
            if (_fpBlend < 0.5f)
            {
            Vector3 start = target.position + Vector3.up * RayOriginHeight;
            float rayMaxDist = dist + RayOriginHeight;
            int mask = ~(1 << 8);
            if (Physics.Raycast(start, offset.normalized, out hit, rayMaxDist, mask))
            {
                float hitDist = Vector3.Distance(target.position, hit.point);
                if (hitDist > MinCameraDistance + 1f)
                {
                    desired = hit.point - offset.normalized * 0.5f;
                    // Audit: la camera e' stata tirata dentro da un ostacolo
                    // (radice di "camera in prima persona" dentro un edificio).
                    if (hitDist < dist * 0.8f && Time.time >= _nextPushLog)
                    {
                        _nextPushLog = Time.time + 15f;
                        OsmDiag.Log("[Audit] Camera tirata dentro: " +
                            target.name + " dist " + dist.ToString("F1") + " -> " +
                            hitDist.ToString("F1") + "m hit=" + hit.collider.name);
                    }
                }
            }

            }

            Vector3 toDesired = desired - target.position;
            if (toDesired.magnitude < MinCameraDistance)
            {
                desired = target.position + toDesired.normalized * MinCameraDistance;
            }

            Vector3 firstPos = target.position + Vector3.up * FirstPersonHeight;
            Vector3 finalDesired = Vector3.Lerp(desired, firstPos, _fpBlend);

            transform.position = Vector3.SmoothDamp(transform.position, finalDesired, ref velocity, smoothTime);
            float viewPitch = Mathf.Lerp(p, 0f, _fpBlend)
                + (_gyroEnabled ? _gyroPitchOffset : 0f);
            transform.rotation = Quaternion.Euler(viewPitch, yaw, 0f);
        }
    }
}
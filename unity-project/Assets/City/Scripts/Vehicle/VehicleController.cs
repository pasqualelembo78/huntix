using UnityEngine;

namespace City.Vehicle
{
    /// <summary>
    /// Controller di guida con servizi estesi:
    ///   • condizione (0-100): scende coi chilometri percorsi (mirror del
    ///     backend: 150 km per arrivare a 0), riduce velocita' e accelerazione,
    ///     sotto lo 0.5% l'auto non parte piu' (panne: serve l'officina)
    ///   • odometro persistente per veicolo (PlayerPrefs + sync server con
    ///     drive-ping periodici mentre si guida)
    /// </summary>
    public class VehicleController : MonoBehaviour
    {
        public VehicleData data;

        private Rigidbody rb;
        private float currentSpeed;
        private float steerInput;
        private float throttleInput;
        private bool braking;

        public bool IsDriving { get; private set; }

        // Gomma a terra: urtare un cordolo (marciapiede rialzato) ad alta
        // velocita' danneggia la ruota finche' non si riparte (nuova corsa =
        // gomme a posto).
        public bool FlatTire { get; private set; }
        private const float FlatTireImpact = 4.5f;   // m/s di urto contro cordolo
        private const float FlatTireMaxSpeed = 2.5f; // m/s con gomma a terra

        // ── condizione / odometro ──────────────────────────────────
        // mirror di vehicle_services.py: CONDITION_PER_KM = 100/150
        public const float WearPerKm = 100f / 150f;
        private const float PingIntervalSec = 30f;
        private const string OdoKeyPrefix = "vodo_";

        private float baseCondition = 100f;   // condizione nota dal server
        private long baseOdometer;            // odometro corrispondente
        private long storedOdometer;          // totale noto localmente
        private float sessionMeters;          // metri percorsi da StartDriving
        private float nextPing;
        private string vehicleCode = "";

        public float ConditionPercent
        {
            get
            {
                float total = storedOdometer + (long)sessionMeters;
                float worn = (total - baseOdometer) / 1000f * WearPerKm;
                return Mathf.Clamp(baseCondition - worn, 0f, 100f);
            }
        }

        public long TotalOdometerM
        {
            get { return storedOdometer + (long)sessionMeters; }
        }

        /// <summary>L'auto parte solo se ha almeno un filo di vita.</summary>
        public bool CanStart()
        {
            return ConditionPercent > 0.5f && !FlatTire;
        }

        /// <summary>Chiamato da VehicleOwnershipApi quando spawn/stato nuovo.</summary>
        public void SetServiceState(float condition, long odometerM)
        {
            baseCondition = Mathf.Clamp(condition, 0f, 100f);
            storedOdometer = System.Math.Max(odometerM, StoredOdometer(vehicleCode));
            baseOdometer = storedOdometer;
            if (vehicleCode.Length > 0)
                PlayerPrefs.SetString(OdoKeyPrefix + vehicleCode,
                    storedOdometer.ToString());
        }

        /// <summary>Odometro totale salvato per un codice veicolo.</summary>
        public static long StoredOdometer(string code)
        {
            if (string.IsNullOrEmpty(code)) return 0L;
            long v = 0L;
            long.TryParse(PlayerPrefs.GetString(OdoKeyPrefix + code, ""), out v);
            return v;
        }

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            if (rb == null)
            {
                rb = gameObject.AddComponent<Rigidbody>();
                rb.mass = 1200f;
                rb.drag = 0f;
                rb.angularDrag = 3f;
                rb.interpolation = RigidbodyInterpolation.Interpolate;
                rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
            }
            rb.isKinematic = true;
        }

        public void StartDriving()
        {
            IsDriving = true;
            FlatTire = false;       // ogni nuova corsa riparte con le gomme a posto
            rb.isKinematic = false;
            currentSpeed = 0f;
            sessionMeters = 0f;
            nextPing = PingIntervalSec;

            var vi = GetComponentInChildren<VehicleInteract>();
            vehicleCode = vi != null ? vi.vehicleCode : "";
            storedOdometer = StoredOdometer(vehicleCode);
            baseOdometer = storedOdometer;
            if (myStateApi != null)
                myStateApi.SyncBaseState(vehicleCode, this);
        }

        public void StopDriving()
        {
            IsDriving = false;
            FlushOdometer();
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
            rb.isKinematic = true;
            currentSpeed = 0f;
            throttleInput = 0f;
            steerInput = 0f;
        }

        private VehicleOwnershipApi myStateApi
        {
            get { return VehicleOwnershipApi.Instance; }
        }

        /// <summary>Sincronizza il totale col server e salva in locale.</summary>
        private void FlushOdometer()
        {
            long total = TotalOdometerM;
            if (vehicleCode.Length > 0)
            {
                PlayerPrefs.SetString(OdoKeyPrefix + vehicleCode, total.ToString());
                PlayerPrefs.Save();
            }
            if (!IsDriving || total <= baseOdometer) { return; }
            if (myStateApi == null || vehicleCode.Length == 0) return;
            myStateApi.DrivePing(vehicleCode, total, ConditionPercent);
            storedOdometer = total;
            sessionMeters = 0f;
            baseOdometer = total;
        }

        public void SetInput(float throttle, float steer, bool brake)
        {
            throttleInput = throttle;
            steerInput = steer;
            braking = brake;
        }

        private void FixedUpdate()
        {
            if (!IsDriving || data == null) return;

            float condFactor = 0.55f + 0.45f * (ConditionPercent / 100f);
            float accel = data.acceleration * condFactor;
            float maxSpd = FlatTire ? Mathf.Min(FlatTireMaxSpeed, data.maxSpeed)
                                    : data.maxSpeed * condFactor;

            // Accelerazione / frenata
            if (braking)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, 0f, data.brakeForce * Time.fixedDeltaTime);
            }
            else
            {
                float target = throttleInput * maxSpd;
                currentSpeed = Mathf.MoveTowards(currentSpeed, target, accel * Time.fixedDeltaTime);
            }

            // Sterzata proporzionale alla velocita
            float turnFactor = Mathf.Clamp01(Mathf.Abs(currentSpeed) / 3f);
            float turn = steerInput * data.turnSpeed * turnFactor * Time.fixedDeltaTime;
            // con la gomma a terra l'auto tira verso un lato: si sente il danno
            if (FlatTire && Mathf.Abs(currentSpeed) > 0.5f)
                turn += 12f * Time.fixedDeltaTime;
            transform.Rotate(0f, turn, 0f);

            // Movimento
            Vector3 vel = transform.forward * currentSpeed;
            vel.y = rb.velocity.y;
            rb.velocity = vel;

            // Drag
            rb.velocity *= (1f - data.drag * Time.fixedDeltaTime);

            // ── odometro: usura reale per chilometro percorso ──
            sessionMeters += Mathf.Abs(currentSpeed) * Time.fixedDeltaTime;
            if (Time.time >= nextPing)
            {
                nextPing = Time.time + PingIntervalSec;
                FlushOdometer();
            }
        }

        private void OnCollisionEnter(Collision collision)
        {
            if (!IsDriving || FlatTire) return;
            // gli urti fra auto del traffico non bucano le gomme: solo il
            // cordolo (o muri/edifici) ad alta velocita'
            if (collision.collider.GetComponent<VehicleController>() != null) return;
            if (!collision.collider.enabled) return;
            if (collision.relativeVelocity.magnitude < FlatTireImpact) return;

            FlatTire = true;
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast("Gomma a terra! Cammina piano o riparta a piedi.");
        }

        public float GetCurrentSpeedKmh()
        {
            return Mathf.Abs(currentSpeed) * 3.6f;
        }
    }
}

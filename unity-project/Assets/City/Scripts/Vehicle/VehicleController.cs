using UnityEngine;

namespace City.Vehicle
{
    /// <summary>Stato di danno di un veicolo dopo un incidente.</summary>
    public enum VehicleDamage
    {
        /// <summary>In ordine.</summary>
        None = 0,
        /// <summary>Gomma a terra: guida molto piano e tira, ma si arriva in officina.</summary>
        Flat = 1,
        /// <summary>Incidentata (fiancata incrinata): NON parte, serve il carro attrezzi.</summary>
        Wrecked = 2,
        /// <summary>In fiamme: prima i vigili del fuoco, poi il carro attrezzi.</summary>
        Fire = 3,
    }

    /// <summary>
    /// Controller di guida con servizi estesi:
    ///   • condizione (0-100): scende coi chilometri percorsi (mirror del
    ///     backend: 150 km per arrivare a 0), riduce velocita' e accelerazione,
    ///     sotto lo 0.5% l'auto non parte piu' (panne: serve l'officina)
    ///   • danni da incidente (flat/wrecked/fire): persistiti sul server. Un
    ///     urto forte buca la gomma (si guida piano), piu' forte incrina la
    ///     fiancata (non si guida: carro attrezzi), violentissimo incendia
    ///     l'auto (vigili del fuoco prima di tutto)
    ///   • odometro persistente per veicolo (PlayerPrefs + sync server con
    ///     drive-ping periodici mentre si guida)
    /// </summary>
    public class VehicleController : MonoBehaviour
    {
        public VehicleData data;

        private Rigidbody rb;
        private float currentSpeed, steerInput, throttleInput;
        private bool braking;

        public bool IsDriving { get; private set; }
        public VehicleDamage Damage { get; private set; }

        /// <summary>Auto temporanea di un lavoro (es. taxi del Tassista):
        /// non e di proprieta, ma si puo salire e guidare per il lavoro.</summary>
        public bool IsJobVehicle { get; private set; }

        public void SetJobVehicle()
        {
            IsJobVehicle = true;
        }

        /// <summary>Comodita' per il codice esistente: gomma a terra.</summary>
        public bool FlatTire { get { return Damage == VehicleDamage.Flat; } }

        // Soglie di impatto (m/s di velocita' relativa): cordolo/muro delicato
        // buca la gomma, piu' forte = auto incidentata, violentissimo = fuoco.
        private const float FlatImpact = 4.5f;
        private const float WreckImpact = 6.5f;
        private const float FireImpact = 9.5f;
        // con la gomma a terra si puo' solo zoppicare fino all'officina
        private const float FlatTireMaxSpeed = 2.5f;

        // ── condizione / odometro ──────────────────────────────────
        // mirror di vehicle_services.py: CONDITION_PER_KM = 100/150
        public const float WearPerKm = 100f / 150f;
        private const float PingIntervalSec = 30f;
        private const string OdoKeyPrefix = "vodo_";

        private float baseCondition = 100f;
        private long baseOdometer;
        private long storedOdometer;
        private float sessionMeters;
        private float nextPing;
        private string vehicleCode = "";

        // ── ruote ─────────────────────────────────────────────────
        private WheelSpinner spinner;

        // ── fx danni ──────────────────────────────────────────────
        private GameObject fxRoot;
        private Material damageMat;
        private Light flameLight;
        private float fxTime;

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

        /// <summary>
        /// L'auto parte solo con un filo di vita e SENZA danno grave:
        /// incidentata o in fiamme non si guida proprio.
        /// </summary>
        public bool CanStart()
        {
            if (Damage == VehicleDamage.Wrecked) return false;
            if (Damage == VehicleDamage.Fire) return false;
            return ConditionPercent > 0.5f;
        }

        /// <summary>Chiamato da VehicleOwnershipApi quando spawn/stato nuovo.</summary>
        public void SetServiceState(float condition, long odometerM,
            VehicleDamage damage = VehicleDamage.None)
        {
            baseCondition = Mathf.Clamp(condition, 0f, 100f);
            storedOdometer = System.Math.Max(odometerM, StoredOdometer(vehicleCode));
            baseOdometer = storedOdometer;
            if (vehicleCode.Length > 0)
                PlayerPrefs.SetString(OdoKeyPrefix + vehicleCode,
                    storedOdometer.ToString());
            if (Damage != damage)
            {
                Damage = damage;
                ApplyDamageVisual();
            }
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
            spinner = gameObject.GetComponent<WheelSpinner>();
        }

        private void OnDestroy()
        {
            if (damageMat != null) Destroy(damageMat);
        }

        public bool StartDriving()
        {
            // niente partenza da incidentata/in fiamme: ragionaci prima
            if (Damage == VehicleDamage.Wrecked || Damage == VehicleDamage.Fire)
                return false;

            IsDriving = true;
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
            return true;
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

            // Incidentata / in fiamme: motore spento, auto ferma (di norma
            // siamo gia' stati staccati dall'urto con un prompt)
            if (Damage == VehicleDamage.Wrecked || Damage == VehicleDamage.Fire)
            {
                currentSpeed = 0f;
                rb.velocity = new Vector3(0f, rb.velocity.y, 0f);
                return;
            }

            if (braking)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, 0f, data.brakeForce * Time.fixedDeltaTime);
            }
            else
            {
                float target = throttleInput * maxSpd;
                currentSpeed = Mathf.MoveTowards(currentSpeed, target, accel * Time.fixedDeltaTime);
            }

            float turnFactor = Mathf.Clamp01(Mathf.Abs(currentSpeed) / 3f);
            float turn = steerInput * data.turnSpeed * turnFactor * Time.fixedDeltaTime;
            if (FlatTire && Mathf.Abs(currentSpeed) > 0.5f)
                turn += 12f * Time.fixedDeltaTime;
            transform.Rotate(0f, turn, 0f);

            Vector3 vel = transform.forward * currentSpeed;
            vel.y = rb.velocity.y;
            rb.velocity = vel;
            rb.velocity *= (1f - data.drag * Time.fixedDeltaTime);

            if (spinner != null) spinner.Spin(currentSpeed);

            sessionMeters += Mathf.Abs(currentSpeed) * Time.fixedDeltaTime;
            if (Time.time >= nextPing)
            {
                nextPing = Time.time + PingIntervalSec;
                FlushOdometer();
            }
        }

        private void OnCollisionEnter(Collision collision)
        {
            if (!IsDriving) return;
            // gli urti fra auto del traffico non danneggiano: solo cordoli,
            // muri ed edifici (e a velocita' significativa); ora le auto del
            // traffico hanno il collider SOLIDO, quindi anche il taxi/pullman
            // che sbatte sul giocatore non deve causare danni
            if (collision.collider.GetComponent<VehicleController>() != null) return;
            if (collision.collider.GetComponent<TrafficCar>() != null) return;
            if (!collision.collider.enabled) return;

            float speed = collision.relativeVelocity.magnitude;
            VehicleDamage hit;
            if (speed >= FireImpact) hit = VehicleDamage.Fire;
            else if (speed >= WreckImpact) hit = VehicleDamage.Wrecked;
            else if (speed >= FlatImpact) hit = VehicleDamage.Flat;
            else return;

            if ((int)Damage >= (int)hit) return;
            SetDamage(hit);

            if (Damage == VehicleDamage.Wrecked)
            {
                StopCarHard();
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Botta violenta! L'auto e' incidentata: non parte piu'. Serve il carro attrezzi.");
            }
            else if (Damage == VehicleDamage.Fire)
            {
                StopCarHard();
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "INCENDIO! Scendi subito e chiama i vigili del fuoco!");
                if (City.Game.Instance != null)
                    City.Game.Instance.OnVehicleCaughtFire(this);
            }
            else
            {
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast(
                        "Gomma a terra! Vai piano fino all'officina, o chiama il carro attrezzi.");
            }

            if (vehicleCode.Length > 0 && myStateApi != null)
                myStateApi.ReportDamage(vehicleCode, Damage, null);
        }

        private void StopCarHard()
        {
            currentSpeed = 0f;
            throttleInput = 0f;
            steerInput = 0f;
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
        }

        /// <summary>
        /// Imposta il danno (da urto o dall'officina) e ridisegna gli FX.
        /// </summary>
        public void SetDamage(VehicleDamage d)
        {
            if (Damage == d) return;
            Damage = d;
            ApplyDamageVisual();
        }

        /// <summary>Acqua dei pompieri: spegne gradualmente i lampi
        /// (amount 1 = totalmente spenta). La rimozione degli FX avviene
        /// poi con SetDamage(Wrecked).</summary>
        public void QuenchFire(float amount)
        {
            amount = Mathf.Clamp01(amount);
            if (flameLight != null)
                flameLight.intensity = Mathf.Lerp(2.2f, 0f, amount);
            if (flameMat != null)
            {
                Color c = flameMat.color;
                c.a = c.a * (1f - amount);
                flameMat.color = c;
            }
        }

        public float GetCurrentSpeedKmh()
        {
            return Mathf.Abs(currentSpeed) * 3.6f;
        }

        // ── effetti visivi danni ───────────────────────────────────

        private void ApplyDamageVisual()
        {
            RemoveDamageFx();
            if (Damage == VehicleDamage.None) return;

            fxRoot = new GameObject("DamageFx");
            fxRoot.transform.SetParent(transform, false);
            fxRoot.transform.localPosition = Damage == VehicleDamage.Fire
                ? new Vector3(0f, 0.9f, 0.2f)
                : new Vector3(0f, 1.0f, 0.8f);
            damageMat = CreateFxMat(Damage == VehicleDamage.Fire
                ? new Color(0.18f, 0.18f, 0.18f, 0.6f)
                : new Color(0.24f, 0.24f, 0.24f, 0.55f));
            MakeQuad(new Vector3(0f, 1.2f, 0.7f), damageMat);
            if (Damage != VehicleDamage.Flat)
            {
                MakeQuad(new Vector3(0.4f, 1.4f, 0.5f), damageMat);
                MakeQuad(new Vector3(1.0f, 1.5f, 0.8f), damageMat);
            }

            if (Damage == VehicleDamage.Fire)
            {
                var flameMat = CreateFxMat(new Color(1f, 0.45f, 0.05f, 0.95f));
                MakeQuad(new Vector3(0f, 0.55f, 0f), flameMat);
                MakeQuad(new Vector3(0.35f, 0.85f, 0.3f), flameMat);
                MakeQuad(new Vector3(-0.35f, 0.8f, 0.1f), flameMat);
                AddFlameQuadsRegister(flameMat);

                var lgo = new GameObject("FireLight");
                lgo.transform.SetParent(fxRoot.transform, false);
                lgo.transform.localPosition = new Vector3(0f, 1.4f, 0.4f);
                flameLight = lgo.AddComponent<Light>();
                flameLight.type = LightType.Point;
                flameLight.color = new Color(1f, 0.5f, 0.1f);
                flameLight.intensity = 2.2f;
                flameLight.range = 10f;
            }
        }

        private Material flameMat;
        private readonly System.Collections.Generic.List<Renderer>
            damageRenderers = new System.Collections.Generic.List<Renderer>();

        private void MakeQuad(Vector3 localPos, Material mat)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(q.GetComponent<Collider>());
            q.name = "fx";
            q.transform.SetParent(fxRoot.transform, false);
            q.transform.localPosition = localPos;
            q.transform.localScale = new Vector3(0.42f, 0.42f, 1f);
            q.transform.localRotation = Damage == VehicleDamage.Fire
                ? Quaternion.Euler(UnityEngine.Random.Range(-25f, 25f), 0f, 0f)
                : Quaternion.identity;
            var r = q.GetComponent<Renderer>();
            if (r != null)
            {
                r.sharedMaterial = mat;
                damageRenderers.Add(r);
            }
        }

        private void AddFlameQuadsRegister(Material flameMat)
        {
            this.flameMat = flameMat;
        }

        private static Material CreateFxMat(Color c)
        {
            var shader = Shader.Find("Sprites/Default");
            if (shader == null) shader = Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null) shader = Shader.Find("Standard");
            var mat = new Material(shader);
            mat.color = c;
            return mat;
        }

        private void RemoveDamageFx()
        {
            if (fxRoot != null) Destroy(fxRoot);
            fxRoot = null;
            flameLight = null;
            damageMat = null;
            flameMat = null;
            damageRenderers.Clear();
        }

        private void Update()
        {
            if (fxRoot == null) return;
            fxTime += Time.deltaTime;
            float flick = Mathf.Sin(fxTime * 11f) * 0.5f + 0.5f;

            if (damageMat != null)
            {
                Color c = damageMat.color;
                c.a = Damage == VehicleDamage.Fire
                    ? 0.55f + flick * 0.2f
                    : 0.4f + flick * 0.25f;
                damageMat.color = c;
            }
            if (flameMat != null)
            {
                Color c = flameMat.color;
                c.a = 0.75f + flick * 0.25f;
                flameMat.color = c;
            }
            if (flameLight != null)
                flameLight.intensity = 1.8f + flick * 1.2f;
        }
    }
}

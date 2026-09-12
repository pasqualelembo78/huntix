using UnityEngine;
using City.Vehicle.Mechanics;

namespace City.Vehicle
{
    /// <summary>
    /// Motore per mezzi NAUTICI (barca, traghetto, nave) con galleggiamento.
    /// Il corpo galleggia su una superficie d'acqua usando N punti di
    /// spinta (archimede): la barca rolla/beccheggia in base a come le ondate
    /// o il baricentro lo spingono. Input: accel (elica), sterzo (timone),
    /// freno (inverti marcia / ancore).
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public class BoatMotor : MonoBehaviour
    {
        [Header("Prestazioni")]
        public float maxSpeedWater = 12f;    // m/s barca media
        public float acceleration = 2.2f;
        public float turnRate = 40f;         // deg/s
        public float brakeForce = 6f;

        [Header("Galleggiamento (archimede)")]
        public float waterLevel = 0f;         // altezza Y acqua (m)
        public float buoyancyStrength = 30f;  // piu' alto = piu' spinta per metro di immersione
        public float waterDrag = 2.5f;
        public Transform[] buoyPoints;        // punti dove applicare la spinta
        public float draft = 0.6f;            // pescaggio di riposo

        [Header("Parti (opzionale)")]
        public VehiclePartDamageSystem damage;

        private Rigidbody rb;
        private float throttle;
        private float steerInput;
        private float brake;

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            rb.mass = Mathf.Max(400f, rb.mass);
            rb.angularDrag = 4f;
            if (buoyPoints == null || buoyPoints.Length == 0)
            {
                // punti di default: 4 angoli dello scafo
                buoyPoints = new Transform[4];
                float w = 1f, l = 2.2f;
                buoyPoints[0] = MakePoint(new Vector3(-w, 0f, l));
                buoyPoints[1] = MakePoint(new Vector3( w, 0f, l));
                buoyPoints[2] = MakePoint(new Vector3(-w, 0f,-l));
                buoyPoints[3] = MakePoint(new Vector3( w, 0f,-l));
            }
        }

        private Transform MakePoint(Vector3 local)
        {
            var go = new GameObject("Buoy_" + local.ToString());
            go.transform.SetParent(transform, false);
            go.transform.localPosition = local;
            return go.transform;
        }

        public void SetInput(float throttle, float steer, float brake)
        {
            this.throttle = throttle;
            this.steerInput = steer;
            this.brake = brake;
        }

        public float Speed { get { return rb != null ? rb.velocity.magnitude : 0f; } }

        private float Eff(VehiclePartType t)
        {
            if (damage == null) return 1f;
            return damage.GetEffectFactor(t);
        }

        private void FixedUpdate()
        {
            if (rb == null) return;

            // ── galleggiamento: spinta archimedea per punto ──
            for (int i = 0; i < buoyPoints.Length; i++)
            {
                Vector3 wp = buoyPoints[i].position;
                float depth = waterLevel - wp.y + draft;   // immersione oltre il pescaggio
                if (depth <= 0f) { ApplyAirRestPosition(buoyPoints[i], wp); continue; }
                // Forza verso l'alto proporzionale all'immersione
                float force = depth * buoyancyStrength;
                rb.AddForceAtPosition(Vector3.up * force, wp, ForceMode.Force);
                // attrito dell'acqua (smorza il movimento laterale/verticale)
                rb.AddForceAtPosition(-rb.velocity * waterDrag * 0.3f, wp, ForceMode.Force);
            }

            // ── propulsione (elica) ──
            float engEff = Eff(VehiclePartType.Engine) * Eff(VehiclePartType.Driveshaft);
            Vector3 fwd = transform.forward;
            float thrust = throttle * maxSpeedWater * acceleration * engEff;
            rb.AddForce(fwd * thrust, ForceMode.Acceleration);

            // ── timone: gira la barca ──
            float steerEff = Eff(VehiclePartType.Steering);
            float turn = steerInput * turnRate * steerEff * Time.fixedDeltaTime;
            transform.Rotate(0f, turn, 0f);

            // ── freno / inversione ──
            if (brake > 0f)
            {
                rb.AddForce(-fwd * brake * brakeForce * Eff(VehiclePartType.Brake),
                    ForceMode.Acceleration);
            }

            // ── resistenza acqua ──
            float projection = Vector3.Dot(rb.velocity, fwd);
            Vector3 resist = -rb.velocity * waterDrag;
            rb.AddForce(resist, ForceMode.Acceleration);
        }

        private void ApplyAirRestPosition(Transform pt, Vector3 wp)
        {
            // punto fuori dall'acqua: nessuna spinta, ma smorza il rimbalzo
            // verso il basso quando la barca beccheggia fuori dall'acqua
            if (rb.velocity.y < 0f)
            {
                rb.AddForceAtPosition(-rb.velocity * waterDrag, wp, ForceMode.Force);
            }
        }
    }
}

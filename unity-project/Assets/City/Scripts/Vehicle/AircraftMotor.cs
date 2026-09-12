using UnityEngine;
using City.Vehicle.Mechanics;

namespace City.Vehicle
{
    /// <summary>Tipologia di mezzo aereo.</summary>
    public enum AircraftKind
    {
        Plane,        // aereo: motore + portanza da velocità
        Helicopter,   // elicottero: rotore, hover, decollo/atterraggio verticale
        Drone         // drone: quadrirotore, hover stabile
    }

    /// <summary>
    /// Motore per mezzi AEREI (aereo, elicottero, drone).
    ///   • Plane: la portanza cresce con la velocità (più veloce → più sale);
    ///     decollo richiede pista/vite applicata; l'input pitch/roll/yaw + thrust.
    ///   • Helicopter: hover stabile; cyclica = pitch/roll, collettiva = climb;
    ///     autorotazione se motore spento.
    ///   • Drone: hover preciso, control freccette 6-DOF limitato.
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public class AircraftMotor : MonoBehaviour
    {
        public AircraftKind kind = AircraftKind.Helicopter;

        [Header("Comune")]
        public float maxThrust = 12f;
        public float hoverAltitude = 20f;     // altitudine di hover (m, elico/drone)
        public float gravityCompensation = 9.81f;

        [Header("Aereo")]
        public float minSpeedForLift = 12f;    // sotto questa non c'è portanza
        public float liftCoefficient = 0.9f;

        [Header("Elicottero / Drone")]
        public float climbRate = 6f;
        public float pitchRate = 35f;         // deg/s
        public float hoverDamping = 2f;

        [Header("Parti (opzionale)")]
        public VehiclePartDamageSystem damage;

        private Rigidbody rb;
        private float thrustInput;     // collettiva/climb per elico/drone; gas per aereo
        private float pitchInput;      // -1..1 (tilt avanti/indietro)
        private float rollInput;       // -1..1
        private float yawInput;        // -1..1
        private float altitudeHold = -1f;  // -1 = manuale

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            rb.mass = Mathf.Max(300f, rb.mass);
            rb.angularDrag = 4f;
        }

        public void SetKind(AircraftKind k) { kind = k; }

        public void SetInput(float thrust, float pitch, float roll, float yaw,
            float holdAltitude = -1f)
        {
            thrustInput = thrust;
            pitchInput = pitch;
            rollInput = roll;
            yawInput = yaw;
            altitudeHold = holdAltitude;
        }

        public void SetHoverAltitude(float h) { hoverAltitude = h; }

        private float Eff(VehiclePartType t)
        {
            if (damage == null) return 1f;
            return damage.GetEffectFactor(t);
        }

        private bool EngineWorking()
        {
            if (damage == null) return true;
            return !damage.HasBrokenPart(VehiclePartType.Engine);
        }

        private void FixedUpdate()
        {
            if (rb == null) return;
            bool engine = EngineWorking();

            switch (kind)
            {
                case AircraftKind.Plane:   SimulatePlane(engine);   break;
                case AircraftKind.Helicopter: SimulateHelicopter(engine); break;
                case AircraftKind.Drone:   SimulateDrone(engine);   break;
            }
        }

        // ── AEREO ────────────────────────────────────────────────
        private void SimulatePlane(bool engine)
        {
            Vector3 fwd = transform.forward;
            float speed = Vector3.Dot(rb.velocity, fwd);

            // portanza ∝ velocità²
            float lift = 0f;
            if (speed > minSpeedForLift)
                lift = (speed - minSpeedForLift) * liftCoefficient * Eff(VehiclePartType.Bodywork);

            // motore → thrust lungo forward
            if (engine)
                rb.AddForce(fwd * thrustInput * maxThrust, ForceMode.Acceleration);

            // gravità
            rb.AddForce(Physics.gravity * rb.mass, ForceMode.Force);

            // portanza verso l'alto (perpendicolare all'ali, praticamente up)
            rb.AddForce(transform.up * lift * 2f, ForceMode.Acceleration);

            // controllo: pitch (nose up/down), roll, yaw
            rb.AddTorque(transform.right * pitchInput * pitchRate, ForceMode.Acceleration);
            rb.AddTorque(transform.forward * -rollInput * pitchRate, ForceMode.Acceleration);
            rb.AddTorque(transform.up * yawInput * 20f, ForceMode.Acceleration);
        }

        // ── ELICOTTERO ───────────────────────────────────────────
        private void SimulateHelicopter(bool engine)
        {
            // gravity
            rb.AddForce(Physics.gravity * rb.mass, ForceMode.Force);

            // collettiva: thrustInput>0 sale, <0 scende, 0 = hover stabile
            float thrust = gravityCompensation * (1f + thrustInput) * rb.mass * 0.5f;
            if (engine)
                rb.AddForce(transform.up * thrust * 2f, ForceMode.Force);
            else
                // autorotazione: discendi in planata stabile
                rb.AddForce(-Vector3.up * gravityCompensation * 0.3f * rb.mass, ForceMode.Force);

            // cyclica/tail: pitch, roll, yaw
            rb.AddTorque(transform.right * pitchInput * pitchRate, ForceMode.Acceleration);
            rb.AddTorque(transform.forward * -rollInput * pitchRate, ForceMode.Acceleration);
            rb.AddTorque(transform.up * yawInput * 30f, ForceMode.Acceleration);

            // hover assist (se richiesto)
            if (altitudeHold > 0f)
            {
                float diff = altitudeHold - rb.position.y;
                rb.AddForce(Mathf.Clamp(diff, -3f, 3f) * rb.mass * 3f * Vector3.up, ForceMode.Force);
            }

            // damping
            rb.AddForce(-rb.velocity * hoverDamping, ForceMode.Acceleration);
        }

        // ── DRONE ────────────────────────────────────────────────
        private void SimulateDrone(bool engine)
        {
            // gravity
            rb.AddForce(Physics.gravity * rb.mass, ForceMode.Force);

            if (engine)
            {
                // thrust verticale, hover stabile con controllo discreto
                float climb = thrustInput * climbRate;
                float thrust = gravityCompensation * (1f + climb * 0.5f) * rb.mass;
                rb.AddForce(transform.up * thrust, ForceMode.Force);

                // pitch/roll per muoversi orizzontalmente, yaw
                rb.AddTorque(transform.right * pitchInput * pitchRate * 0.6f, ForceMode.Acceleration);
                rb.AddTorque(transform.forward * -rollInput * pitchRate * 0.6f, ForceMode.Acceleration);
                rb.AddTorque(transform.up * yawInput * 20f, ForceMode.Acceleration);
            }
            else
            {
                // perdita di motori → caduta
                rb.AddForce(-rb.velocity * hoverDamping, ForceMode.Acceleration);
            }

            // damping orizzontale per hover fermo
            Vector3 vLocal = transform.InverseTransformDirection(rb.velocity);
            vLocal.x *= 1f - Mathf.Clamp01(hoverDamping * 0.1f * Time.deltaTime);
            vLocal.z *= 1f - Mathf.Clamp01(hoverDamping * 0.1f * Time.deltaTime);
            rb.velocity = transform.TransformDirection(vLocal);
        }
    }
}

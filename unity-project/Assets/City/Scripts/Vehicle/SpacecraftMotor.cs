using UnityEngine;
using City.Vehicle.Mechanics;

namespace City.Vehicle
{
    /// <summary>
    /// Motore per veicoli SPAZIALI (shuttle) con fisica 6-DOF (sei gradi di
    /// libertà: traslazione X/Y/Z + rotazione roll/pitch/yaw, in assenza di
    /// gravità o con gravità ridotta). Pensato come upgrade per la
    /// navigazione spaziale.
    ///
    /// Input: thrust (avanti), strafe (laterale/alto), yaw/pitch/roll.
    /// In spazio non c'è atrito: per fermarsi bisogna contro-spingere.
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public class SpacecraftMotor : MonoBehaviour
    {
        [Header("Traslazione")]
        public float mainThrust = 8f;        // avanti (retro/kill per frenare)
        public float strafeThrust = 4f;      // laterale/alto
        public bool useGravity = false;      // off = spazio puro

        [Header("Rotazione (RCS)")]
        public float yawRate = 45f;          // deg/s
        public float pitchRate = 45f;
        public float rollRate = 60f;

        [Header("Retro-razzi (frenata/retro)")]
        public float retroThrust = 4f;

        [Header("Parti (opzionale)")]
        public VehiclePartDamageSystem damage;

        private Rigidbody rb;
        private Vector3 translateInput;   // x=strafe, y=up/down, z=avanti/retro
        private Vector3 rotateInput;      // x=roll, y=yaw, z=pitch

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            rb.mass = Mathf.Max(500f, rb.mass);
            rb.angularDrag = 0.5f;        // in spazio quasi niente atrito
            rb.drag = 0.05f;
        }

        public void SetTranslation(Vector3 input)
        {
            translateInput = new Vector3(
                Mathf.Clamp(input.x, -1f, 1f),
                Mathf.Clamp(input.y, -1f, 1f),
                Mathf.Clamp(input.z, -1f, 1f));
        }

        public void SetRotation(Vector3 input, bool useDegrees = true)
        {
            // x=roll, y=yaw, z=pitch
            rotateInput = useDegrees
                ? new Vector3(Mathf.Clamp(input.x, -1f, 1f),
                              Mathf.Clamp(input.y, -1f, 1f),
                              Mathf.Clamp(input.z, -1f, 1f))
                : input;
        }

        private float Eff(VehiclePartType t)
        {
            if (damage == null) return 1f;
            return damage.GetEffectFactor(t);
        }

        private void FixedUpdate()
        {
            if (rb == null) return;

            // gravità opzionale
            if (!useGravity)
                rb.AddForce(Physics.gravity * -1f * rb.mass * 0f, ForceMode.Force); // neutro

            // ── traslazione ──
            float thrustEff = Eff(VehiclePartType.Engine)
                            * Eff(VehiclePartType.Driveshaft);
            Vector3 local = new Vector3(
                translateInput.x * strafeThrust,
                translateInput.y * strafeThrust,
                translateInput.z * (translateInput.z > 0 ? mainThrust : retroThrust));
            Vector3 worldForce = transform.TransformDirection(local) * thrustEff;
            rb.AddForce(worldForce * rb.mass, ForceMode.Force);

            // ── rotazione (RCS) ──
            float steerEff = Eff(VehiclePartType.Steering);
            Vector3 ang = new Vector3(
                rotateInput.x * rollRate,
                rotateInput.y * yawRate,
                rotateInput.z * pitchRate) * Mathf.Deg2Rad * steerEff;
            Vector3 worldTorque = transform.TransformDirection(ang);
            rb.AddTorque(worldTorque * rb.mass, ForceMode.Force);
        }

        public float Speed { get { return rb != null ? rb.velocity.magnitude : 0f; } }
    }
}

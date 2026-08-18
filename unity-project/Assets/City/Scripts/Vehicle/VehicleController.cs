using UnityEngine;

namespace City.Vehicle
{
    public class VehicleController : MonoBehaviour
    {
        public VehicleData data;

        private Rigidbody rb;
        private float currentSpeed;
        private float steerInput;
        private float throttleInput;
        private bool braking;

        public bool IsDriving { get; private set; }

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
            rb.isKinematic = false;
            currentSpeed = 0f;
        }

        public void StopDriving()
        {
            IsDriving = false;
            rb.isKinematic = true;
            rb.velocity = Vector3.zero;
            rb.angularVelocity = Vector3.zero;
            currentSpeed = 0f;
            throttleInput = 0f;
            steerInput = 0f;
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

            float maxSpd = data.maxSpeed;

            // Accelerazione / frenata
            if (braking)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, 0f, data.brakeForce * Time.fixedDeltaTime);
            }
            else
            {
                float target = throttleInput * maxSpd;
                currentSpeed = Mathf.MoveTowards(currentSpeed, target, data.acceleration * Time.fixedDeltaTime);
            }

            // Sterzata proporzionale alla velocita
            float turnFactor = Mathf.Clamp01(Mathf.Abs(currentSpeed) / 3f);
            float turn = steerInput * data.turnSpeed * turnFactor * Time.fixedDeltaTime;
            transform.Rotate(0f, turn, 0f);

            // Movimento
            Vector3 vel = transform.forward * currentSpeed;
            vel.y = rb.velocity.y;
            rb.velocity = vel;

            // Drag
            rb.velocity *= (1f - data.drag * Time.fixedDeltaTime);
        }

        public float GetCurrentSpeedKmh()
        {
            return Mathf.Abs(currentSpeed) * 3.6f;
        }
    }
}

using UnityEngine;

namespace City.Player
{
    public class CameraRig : MonoBehaviour
    {
        public static CameraRig Instance;

        public Transform target;
        public float distance = 7f;
        public float height = 3.2f;
        public float pitch = 18f;
        public float smoothTime = 0.12f;
        public float orbitSpeed = 5f;

        private bool drivingMode;
        private readonly float driveDistance = 12f;
        private readonly float driveHeight = 5f;
        private readonly float drivePitch = 15f;

        private float yaw = 0f;
        private Vector3 velocity;

        private const float MinCameraDistance = 2.8f;
        private const float RayOriginHeight = 5f;

        private void Awake()
        {
            Instance = this;
        }

        private void Start()
        {
            if (target == null)
            {
                GameObject p = GameObject.FindGameObjectWithTag("Player");
                if (p != null) target = p.transform;
            }
            if (target != null) yaw = target.eulerAngles.y;
        }

        public void Orbit(float screenDeltaX)
        {
            yaw += screenDeltaX * orbitSpeed * 0.15f;
        }

        public void SetYaw(Quaternion lookRotation)
        {
            yaw = lookRotation.eulerAngles.y;
        }

        public void SetDrivingMode(bool driving)
        {
            drivingMode = driving;
        }

        private void LateUpdate()
        {
            if (target == null) return;

            if (drivingMode)
            {
                float targetYaw = target.eulerAngles.y;
                yaw = Mathf.LerpAngle(yaw, targetYaw, 5f * Time.unscaledDeltaTime);
            }

            float d = drivingMode ? driveDistance : distance;
            float h = drivingMode ? driveHeight : height;
            float p = drivingMode ? drivePitch : pitch;

            Vector3 offset = Quaternion.Euler(p, yaw, 0f) * new Vector3(0f, h, -d);
            Vector3 desired = target.position + offset;
            float dist = offset.magnitude;

            // Il ray parte da 5m sopra il player: abbastanza alto da passare
            // SOPRA la maggior parte degli edifici bassi e non colpire le facce.
            // Layer mask: esclude layer 8 (Buildings) se impostato.
            RaycastHit hit;
            Vector3 start = target.position + Vector3.up * RayOriginHeight;
            float rayMaxDist = dist + RayOriginHeight;
            int mask = ~(1 << 8);
            if (Physics.Raycast(start, offset.normalized, out hit, rayMaxDist, mask))
            {
                float hitDist = Vector3.Distance(target.position, hit.point);
                if (hitDist > MinCameraDistance + 1f)
                    desired = hit.point - offset.normalized * 0.5f;
            }

            // Distanza minima garantita: la camera non viene mai schiacciata
            // troppo vicino al player (no zoom improvviso in 1a persona).
            Vector3 toDesired = desired - target.position;
            if (toDesired.magnitude < MinCameraDistance)
            {
                desired = target.position + toDesired.normalized * MinCameraDistance;
            }

            transform.position = Vector3.SmoothDamp(transform.position, desired, ref velocity, smoothTime);
            transform.rotation = Quaternion.Euler(p, yaw, 0f);
        }
    }
}

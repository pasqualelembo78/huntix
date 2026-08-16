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

        private float yaw = 0f;
        private Vector3 velocity;

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

        private void LateUpdate()
        {
            if (target == null) return;

            Vector3 offset = Quaternion.Euler(pitch, yaw, 0f) * new Vector3(0f, height, -distance);
            Vector3 desired = target.position + offset;
            float dist = offset.magnitude;
            RaycastHit hit;
            Vector3 start = target.position + Vector3.up * 0.8f;
            if (Physics.Raycast(start, offset.normalized, out hit, dist))
            {
                desired = hit.point - offset.normalized * 0.35f;
            }
            transform.position = Vector3.SmoothDamp(transform.position, desired, ref velocity, smoothTime);
            transform.rotation = Quaternion.Euler(pitch, yaw, 0f);
        }
    }
}

using UnityEngine;
using City.OSM;

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

        public void ApplyZoom(float delta)
        {
            distance = Mathf.Clamp(distance + delta, MinCameraDistance, MaxCameraDistance);
        }

        private void Update()
        {
            if (drivingMode) return;
            HandlePinchZoom();
        }

        private void HandlePinchZoom()
        {
            if (Input.touchCount == 2)
            {
                // pinza sulla minimappa = zoom della minimappa (non della
                // camera); mappa espansa aperta = zoom della mappa stessa
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

            // In guida il yaw resta libero: si ruota trascinando la zona
            // destra dello schermo, identico alla camminata

            float d = drivingMode ? driveDistance : distance;
            float h = drivingMode ? driveHeight : height;
            float p = drivingMode ? drivePitch : pitch;

            Vector3 offset = Quaternion.Euler(p, yaw, 0f) * new Vector3(0f, h, -d);
            Vector3 desired = target.position + offset;
            float dist = offset.magnitude;

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

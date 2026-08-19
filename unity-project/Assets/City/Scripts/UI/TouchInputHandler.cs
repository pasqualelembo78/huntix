using UnityEngine;
using City.Player;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    public class TouchInputHandler : MonoBehaviour
    {
        public static TouchInputHandler Instance;

        private const float LongPressDuration = 2f;
        private const float MoveThreshold = 40f;

        private float pressTimer;
        private Vector2 pressStartScreen;
        private bool tracking;
        private Camera mainCam;

        private void Awake()
        {
            Instance = this;
        }

        private void Start()
        {
            mainCam = Camera.main;
        }

        private void Update()
        {
            if (Input.touchCount != 1) { ResetTracking(); return; }
            if (City.Game.Instance != null && City.Game.Instance.IsInInterior) { ResetTracking(); return; }
            if (City.Game.Instance != null && City.Game.Instance.IsDriving) { ResetTracking(); return; }

            Touch t = Input.GetTouch(0);

            switch (t.phase)
            {
                case TouchPhase.Began:
                    pressStartScreen = t.position;
                    pressTimer = 0f;
                    tracking = true;
                    break;

                case TouchPhase.Moved:
                case TouchPhase.Stationary:
                    if (!tracking) break;
                    if (Vector2.Distance(t.position, pressStartScreen) > MoveThreshold)
                    {
                        tracking = false;
                        break;
                    }
                    pressTimer += Time.deltaTime;
                    if (pressTimer >= LongPressDuration)
                    {
                        tracking = false;
                        TryTeleport(t.position);
                    }
                    break;

                case TouchPhase.Ended:
                case TouchPhase.Canceled:
                    ResetTracking();
                    break;
            }
        }

        private void ResetTracking()
        {
            tracking = false;
            pressTimer = 0f;
        }

        private void TryTeleport(Vector2 screenPos)
        {
            if (mainCam == null) mainCam = Camera.main;
            if (mainCam == null) return;

            Ray ray = mainCam.ScreenPointToRay(screenPos);
            int mask = ~(1 << 8);
            if (Physics.Raycast(ray, out RaycastHit hit, 500f, mask))
            {
                Vector3 target = hit.point + Vector3.up * 0.5f;
                var game = City.Game.Instance;
                if (game == null) return;

                UnityBridge.LogToAndroid("TouchInputHandler", $"Teleport a ({target.x:F1},{target.y:F1},{target.z:F1})");
                game.TeleportPlayer(target, Quaternion.LookRotation(ray.direction));
            }
            else
            {
                UnityBridge.LogToAndroid("TouchInputHandler", "Teleport fallito: nessun terreno colpito");
            }
        }
    }
}

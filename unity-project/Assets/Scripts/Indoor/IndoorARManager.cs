using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;
using Huntix.Bridge;
using System.Collections.Generic;

namespace Huntix.Indoor
{
    /// <summary>
    /// Manages AR passthrough for indoor scenes.
    /// When ARCore is available: shows camera background, places store on detected planes.
    /// When ARCore is unavailable: falls back to normal 3D rendering.
    /// </summary>
    public class IndoorARManager : MonoBehaviour
    {
        public static IndoorARManager Instance { get; private set; }

        [Header("AR Components (auto-found if null)")]
        public ARSession arSession;
        public ARSessionOrigin arSessionOrigin;
        public ARRaycastManager arRaycastManager;
        public ARPlaneManager arPlaneManager;

        [Header("Settings")]
        public bool enableARPassthrough = true;
        public float planeSearchTimeout = 10f;
        public LayerMask storeLayer;

        [Header("Status")]
        public bool isARAvailable;
        public bool isARActive;
        public bool isPlaneFound;

        private Camera _arCamera;
        private GameObject _storeRoot;
        private readonly List<ARRaycastHit> _hits = new List<ARRaycastHit>();

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Start()
        {
            StartCoroutine(InitARAsync());
        }

        private System.Collections.IEnumerator InitARAsync()
        {
            // Wait for AR session availability check (async)
            if (ARSession.state == ARSessionState.None ||
                ARSession.state == ARSessionState.CheckingAvailability)
            {
                Debug.Log("[IndoorARManager] Waiting for AR availability check...");
                yield return ARSession.CheckAvailability();
            }

            isARAvailable = ARSession.state == ARSessionState.SessionTracking ||
                            ARSession.state == ARSessionState.SessionInitializing;
            Debug.Log($"[IndoorARManager] AR available: {isARAvailable}");

            if (isARAvailable && enableARPassthrough)
            {
                InitializeAR();
                if (arPlaneManager != null)
                    arPlaneManager.planesChanged += OnPlanesChanged;
            }
            else
                Debug.Log("[IndoorARManager] Running in non-AR mode (3D only)");
        }

        private void OnDisable()
        {
            if (arPlaneManager != null)
                arPlaneManager.planesChanged -= OnPlanesChanged;
        }

        private void InitializeAR()
        {
            // Find AR components if not assigned
            if (arSession == null)
                arSession = FindObjectOfType<ARSession>();
            if (arSessionOrigin == null)
                arSessionOrigin = FindObjectOfType<ARSessionOrigin>();
            if (arRaycastManager == null)
                arRaycastManager = FindObjectOfType<ARRaycastManager>();
            if (arPlaneManager == null)
                arPlaneManager = FindObjectOfType<ARPlaneManager>();

            if (arSession == null || arSessionOrigin == null)
            {
                Debug.LogWarning("[IndoorARManager] AR components not found, disabling AR");
                isARAvailable = false;
                return;
            }

            _arCamera = arSessionOrigin.camera;
            isARActive = true;

            // Enable camera background for passthrough
            var camBackground = _arCamera.GetComponent<ARCameraBackground>();
            if (camBackground == null)
                camBackground = _arCamera.gameObject.AddComponent<ARCameraBackground>();
            camBackground.enabled = true;

            Debug.Log("[IndoorARManager] AR initialized — camera passthrough active");
        }

        private void Update()
        {
            if (!isARActive || !isARAvailable) return;
        }

        private void OnPlanesChanged(ARPlanesChangedEventArgs args)
        {
            if (isPlaneFound) return;

            var planes = new List<ARPlane>();
            planes.AddRange(args.added);
            planes.AddRange(args.updated);

            if (planes.Count > 0)
            {
                isPlaneFound = true;
                Debug.Log($"[IndoorARManager] Found {planes.Count} AR planes");
                UnityBridge.SendMessageToAndroid("IndoorARPlaneFound",
                    $"{{\"count\":{planes.Count}}}");
            }
        }

        /// <summary>
        /// Called by StoreBuilder after building the store.
        /// If AR is active and planes are found, repositions the store onto the AR plane.
        /// </summary>
        public void OnStoreBuilt(GameObject storeRoot)
        {
            _storeRoot = storeRoot;

            if (!isARActive || !isPlaneFound) return;

            // Try to place store on a detected horizontal plane
            var screenCenter = new Vector2(Screen.width / 2f, Screen.height / 2f);
            if (arRaycastManager != null &&
                arRaycastManager.Raycast(screenCenter, _hits, TrackableType.PlaneWithinPolygon))
            {
                var pose = _hits[0].pose;
                storeRoot.transform.position = pose.position;
                storeRoot.transform.rotation = pose.rotation;
                Debug.Log($"[IndoorARManager] Store placed on AR plane at {pose.position}");
            }
        }

        /// <summary>
        /// Returns the AR camera transform (for player camera reference).
        /// </summary>
        public Camera GetARCamera() => _arCamera;

        /// <summary>
        /// Checks if a world position is above any AR plane.
        /// </summary>
        public bool IsAboveARPlane(Vector3 worldPos, float maxDistance = 2f)
        {
            if (!isARActive || arRaycastManager == null) return false;

            var screenPos = _arCamera.WorldToScreenPoint(worldPos + Vector3.down * maxDistance);
            if (screenPos.z < 0) return false;

            return arRaycastManager.Raycast(screenPos, _hits, TrackableType.PlaneWithinPolygon);
        }
    }
}

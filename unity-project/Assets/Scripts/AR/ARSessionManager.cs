using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;
using System.Collections.Generic;

namespace Huntix.AR
{
    public class ARSessionManager : MonoBehaviour
    {
        public static ARSessionManager Instance { get; private set; }

        [Header("AR Components")]
        public ARSession arSession;
        public ARSessionOrigin arSessionOrigin;
        public ARRaycastManager arRaycastManager;

        [Header("Settings")]
        public float captureRadius = 5f;
        public LayerMask groundLayer;

        private bool _isTracking;
        private Pose _lastHitPose;

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
            if (arSession != null)
            {
                arSession.enabled = true;
            }
            _isTracking = false;
        }

        private void Update()
        {
            if (arRaycastManager == null) return;

            var screenCenter = new Vector2(Screen.width / 2, Screen.height / 2);
            var hits = new List<ARRaycastHit>();

            if (arRaycastManager.Raycast(screenCenter, hits, TrackableType.PlaneWithinPolygon))
            {
                _lastHitPose = hits[0].pose;
                _isTracking = true;
            }
            else
            {
                _isTracking = false;
            }
        }

        public bool IsTracking()
        {
            return _isTracking;
        }

        public Pose GetLastHitPose()
        {
            return _lastHitPose;
        }

        public bool TryGetCameraPose(out Pose pose)
        {
            if (arSessionOrigin != null && arSessionOrigin.camera != null)
            {
                pose = new Pose(arSessionOrigin.camera.transform.position, arSessionOrigin.camera.transform.rotation);
                return true;
            }
            pose = Pose.identity;
            return false;
        }

        public void PlaceEgg(Vector3 position, Quaternion rotation)
        {
            Debug.Log($"[ARSessionManager] Placing egg at {position}");
        }

        public float GetDistanceToEgg(Vector3 eggPosition)
        {
            if (arSessionOrigin != null && arSessionOrigin.camera != null)
            {
                return Vector3.Distance(arSessionOrigin.camera.transform.position, eggPosition);
            }
            return float.MaxValue;
        }

        public void OnTrackingLost()
        {
            _isTracking = false;
            Debug.LogWarning("[ARSessionManager] AR tracking lost");
        }

        public void OnTrackingResumed()
        {
            _isTracking = true;
            Debug.Log("[ARSessionManager] AR tracking resumed");
        }
    }
}
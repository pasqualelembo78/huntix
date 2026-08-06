using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;
using System.Collections.Generic;

namespace Huntix.AR
{
    public class GeospatialAnchor : MonoBehaviour
    {
        public static GeospatialAnchor Instance { get; private set; }

        [Header("AR Settings")]
        public float anchorRadius = 5f;
        public LayerMask groundLayer;

        private ARRaycastManager _raycastManager;
        private ARSessionOrigin _sessionOrigin;
        private ARAnchor _currentAnchor;

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
            _raycastManager = FindObjectOfType<ARRaycastManager>();
            _sessionOrigin = FindObjectOfType<ARSessionOrigin>();
        }

        public bool TryCreateAnchor(Vector3 position, Quaternion rotation, out ARAnchor anchor)
        {
            anchor = null;

            if (_sessionOrigin == null) return false;

            var anchorGameObject = new GameObject("GeospatialAnchor");
            anchorGameObject.transform.position = position;
            anchorGameObject.transform.rotation = rotation;

            anchor = anchorGameObject.AddComponent<ARAnchor>();
            Debug.Log($"[GeospatialAnchor] Created anchor at {position}");
            return true;
        }

        public void RemoveAnchor(ARAnchor anchor)
        {
            if (anchor != null)
            {
                Destroy(anchor.gameObject);
                Debug.Log("[GeospatialAnchor] Anchor removed");
            }
        }

        public bool TryRaycast(Vector2 screenPoint, out Vector3 hitPosition)
        {
            hitPosition = Vector3.zero;

            if (_raycastManager == null) return false;

            var hits = new List<ARRaycastHit>();
            if (_raycastManager.Raycast(screenPoint, hits, TrackableType.PlaneWithinPolygon))
            {
                hitPosition = hits[0].pose.position;
                return true;
            }

            return false;
        }

        public Vector3 GetCameraPosition()
        {
            if (_sessionOrigin != null && _sessionOrigin.camera != null)
            {
                return _sessionOrigin.camera.transform.position;
            }
            return Vector3.zero;
        }

    public Quaternion GetCameraRotation()
    {
        if (_sessionOrigin != null && _sessionOrigin.camera != null)
        {
            return _sessionOrigin.camera.transform.rotation;
        }
        return Quaternion.identity;
    }

    // ── Geo → World (proiezione planare equirectangolare, valida fino a ~5km)
    // Sostituibile con ARCore Geospatial (AREarth cameraGeospatialPose) quando
    // disponibile; fallback piano per device/ROM senza ARCore Extensions.
    // offsetMeters = distanza in metri da playerLat/playerLng (nord/est positivi).
    public static Vector3 GeoWorldOffset(
        double lat, double lng, double alt,
        double playerLat, double playerLng, double playerAlt
    )
    {
        const double EARTH = 6371000.0;                 // raggio medio Terra [m]
        double dLat = (lat - playerLat) * System.Math.PI / 180.0;
        double dLng = (lng - playerLng) * System.Math.PI / 180.0;
        double mPerDegLat = EARTH * System.Math.PI / 180.0;
        double mPerDegLng = EARTH * System.Math.PI / 180.0 * System.Math.Cos(playerLat * System.Math.PI / 180.0);
        double y = dLat * mPerDegLat;                  // nord (metri)
        double x = dLng * mPerDegLng;                  // est  (metri)
        double z = (alt - playerAlt);                  // altezza (metri, relativa)
        return new Vector3((float)x, (float)y, (float)z);
    }
}
}
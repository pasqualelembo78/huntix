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

    // ── Geo → World ─────────────────────────────────────────────
    // 1) se ARCore Extensions è presente in runtime, usa AREarth (geospatial reale);
    // 2) altrimenti fallback equirettangolare planare (valido ~5km).
    // Aggiungere il pacchetto com.unity.xr.arcore-extensions in manifest.json per
    // attivare il ramo 1 (offline build con fallback piano).
    // offsetMeters = distanza in metri da playerLat/playerLng (nord/est positivi).
    public static Vector3 GeoWorldOffset(
        double lat, double lng, double alt,
        double playerLat, double playerLng, double playerAlt
    )
    {
        if (TryEarthOffset(lat, lng, playerLat, playerLng, out Vector3 v)) return v;

        const double EARTH = 6371000.0;
        double dLat = (lat - playerLat) * System.Math.PI / 180.0;
        double dLng = (lng - playerLng) * System.Math.PI / 180.0;
        double mPerDegLat = EARTH * System.Math.PI / 180.0;
        double mPerDegLng = EARTH * System.Math.PI / 180.0 * System.Math.Cos(playerLat * System.Math.PI / 180.0);
        double y = dLat * mPerDegLat;
        double x = dLng * mPerDegLng;
        double z = (alt - playerAlt);
        return new Vector3((float)x, (float)y, (float)z);
    }

    private static bool TryEarthOffset(
        double lat, double lng, double playerLat, double playerLng, out Vector3 offset
    )
    {
        offset = Vector3.zero;
        try
        {
            // Reflection: AREarth esiste solo se ARCore Extensions è importato.
            var earthType = System.Type.GetType("UnityEngine.XR.ARCoreExtensions.ARCoreExtensions, Unity.XR.ARCoreExtensions");
            if (earthType == null) return false;
            var earth = UnityEngine.Object.FindObjectOfType(earthType);
            if (earth == null) return false;
            var camPose = earthType.GetProperty("CameraGeospatialPose");
            if (camPose == null) return false;
            var pose = camPose.GetValue(earth);
            var latP = pose.GetType().GetField("Latitude").GetValue(pose);
            var lngP = pose.GetType().GetField("Longitude").GetValue(pose);
            var altP = pose.GetType().GetField("Altitude").GetValue(pose);
            double pl = System.Convert.ToDouble(latP), pn = System.Convert.ToDouble(lngP), pa = System.Convert.ToDouble(altP);
            offset = GeoWorldOffset(lat, lng, 0, pl, pn, pa);
            return true;
        }
        catch { return false; }
    }
}
}
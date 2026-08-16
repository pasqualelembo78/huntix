using UnityEngine;
using UnityEngine.EventSystems;

namespace Huntix.Outdoor
{
    /// <summary>
    /// ExploreInputHandler — intercetta i tap nel mondo Esplora.
    /// 1) se il tocco cade su un marker POI (collider) → OnPoiTapped;
    /// 2) altrimenti → tap su punto vuoto del terreno → OnGroundTapped.
    /// I tap sopra elementi UI vengono ignorati (ricerca, tab, popup).
    /// </summary>
    public class ExploreInputHandler : MonoBehaviour
    {
        public static ExploreInputHandler Instance { get; private set; }

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

        public static ExploreInputHandler EnsureInstance()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("ExploreInputHandler");
            return go.AddComponent<ExploreInputHandler>();
        }

        private void Update()
        {
            if (!Input.GetMouseButtonDown(0)) return;

            // Popup modale aperto: nessun input al mondo.
            if (ExplorePopup.IsOpen) return;

            // Tap su UI (ricerca, categorie, bottoni): lascia gestire all'EventSystem.
            if (EventSystem.current != null && EventSystem.current.IsPointerOverGameObject())
                return;

            var cam = Camera.main;
            if (cam == null) return;

            var ray = cam.ScreenPointToRay(Input.mousePosition);

            // 1) Marker POI: raggio lungo (fino a 20 km) così i POI lontani
            //    restano tappabili anche a 10 km di raggio di ricerca.
            if (Physics.Raycast(ray, out var hit, 20000f))
            {
                var pm = hit.collider.GetComponentInParent<POIMarker>();
                if (pm != null && pm.Poi != null && ExploreManager.Instance != null)
                {
                    ExploreManager.Instance.OnPoiTapped(pm.Poi);
                    return;
                }
            }

            // 2) Terreno vuoto: piano orizzontale dei marker (z = altitudine 0).
            var plane = new Plane(Vector3.forward, Vector3.zero);
            if (plane.Raycast(ray, out float enter))
            {
                var point = ray.GetPoint(enter);
                if (ExploreManager.Instance != null)
                    ExploreManager.Instance.OnGroundTapped(point);
            }
        }
    }
}

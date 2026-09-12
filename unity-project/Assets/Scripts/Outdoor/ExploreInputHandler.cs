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

            // 2) Terreno vuoto: raycast contro il terreno se disponibile,
            //    altrimenti piano orizzontale a y=0.
            float terrainY = GetTerrainYAtRaycast(ray);
            Vector3 point;
            if (terrainY >= 0f)
            {
                // Usa l'altezza del terreno reale raycastata
                point = new Vector3(ray.GetPoint(terrainY).x, terrainY, ray.GetPoint(terrainY).z);
            }
            else
            {
                // Piano orizzontale dei marker (z = altitudine 0).
                var plane = new Plane(Vector3.forward, Vector3.zero);
                if (plane.Raycast(ray, out float enter))
                {
                    point = ray.GetPoint(enter);
                }
                else
                {
                    return;
                }
            }
            if (ExploreManager.Instance != null)
                ExploreManager.Instance.OnGroundTapped(point);
        }

        private float GetTerrainYAtRaycast(Ray ray)
        {
            // Controlla se c'è un Terrain Unity nella scena
            Terrain terrain = Terrain.activeTerrain;
            if (terrain != null)
            {
                // Raycast contro il terreno, escludendo i edifici (layer 8)
                if (Physics.Raycast(ray, out RaycastHit hit, 10000f,
                    ~(1 << 8)))
                {
                    return hit.point.y;
                }
            }
            // Nessun Terrain Unity: restituire -1 per usare il piano piatto
            return -1f;
        }
    }
}

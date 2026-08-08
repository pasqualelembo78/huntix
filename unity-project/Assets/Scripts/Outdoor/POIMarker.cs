using UnityEngine;

namespace Huntix.Outdoor
{
    /// <summary>
    /// POIMarker — marker 3D di un locale sulla mappa AR.
    /// Al tocco (OnMouseDown) apre la pagina del locale via ExploreManager.
    /// </summary>
    public class POIMarker : MonoBehaviour
    {
        [Header("Dati locale")]
        public string osmId;
        public string name;
        public string poiType;
        public string pageType;
        public string url;

        [Header("Visual")]
        public bool billboard = true;
        public Transform icon;

        private ExploreManager.PoiData _data;

        public ExploreManager.PoiData Poi => _data;

        private void Awake()
        {
            // Necessario per la raycast dei tap (InputHandler).
            if (GetComponent<Collider>() == null)
                gameObject.AddComponent<SphereCollider>();
        }

        private void Update()
        {
            if (billboard && Camera.main != null)
            {
                // Mantiene l'icona rivolta alla camera (stile pokemon-go)
                if (icon == null) icon = transform;
                icon.rotation = Quaternion.LookRotation(icon.position - Camera.main.transform.position);
            }
        }

        public void Setup(ExploreManager.PoiData data)
        {
            _data = data;
            osmId = data.id;
            name = data.name;
            poiType = data.poiType;
            pageType = data.pageType;
            url = data.url;
        }
    }
}
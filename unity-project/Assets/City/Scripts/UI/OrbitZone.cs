using UnityEngine;
using UnityEngine.EventSystems;
using City.OSM;

namespace City.UI
{
    public class OrbitZone : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        private bool active;
        private float lastX;

        public bool IsActive { get { return active; } }

        public event System.Action<float> OnDragDelta;

        /// <summary>
        /// Con la mappa espansa aperta lo schermo e' coperto da un overlay
        /// trasparente ai raycast: i tocco sulla mappa non devono ruotare la
        /// camera del personaggio sotto.
        /// </summary>
        private static bool MapOpen()
        {
            return MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen;
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            if (MapOpen()) return;
            active = true;
            lastX = eventData.position.x;
        }

        public void OnDrag(PointerEventData eventData)
        {
            if (!active) return;
            if (MapOpen())
            {
                // l'overlay si e' aperto sotto il dito: non ruotare oltre
                active = false;
                return;
            }
            float dx = eventData.position.x - lastX;
            lastX = eventData.position.x;
            OnDragDelta?.Invoke(dx);
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            active = false;
        }
    }
}

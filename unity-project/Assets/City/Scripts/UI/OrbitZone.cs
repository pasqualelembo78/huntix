using UnityEngine;
using UnityEngine.EventSystems;

namespace City.UI
{
    public class OrbitZone : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        private bool active;
        private float lastX;

        public bool IsActive { get { return active; } }

        public event System.Action<float> OnDragDelta;

        public void OnPointerDown(PointerEventData eventData)
        {
            active = true;
            lastX = eventData.position.x;
        }

        public void OnDrag(PointerEventData eventData)
        {
            if (!active) return;
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

using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace City.UI
{
    public class DynamicJoystick : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        public RectTransform canvasRoot;
        public RectTransform joystickBase;
        public RectTransform joystickHandle;
        public float radius = 120f;

        private Vector2 origin;
        private bool active;

        public Vector2 Value { get; private set; }

        public void Configure(RectTransform canvasRoot, RectTransform joystickBase, RectTransform joystickHandle)
        {
            this.canvasRoot = canvasRoot;
            this.joystickBase = joystickBase;
            this.joystickHandle = joystickHandle;
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            active = true;
            RectTransformUtility.ScreenPointToLocalPointInRectangle(canvasRoot, eventData.position, eventData.pressEventCamera, out origin);
            if (joystickBase != null)
            {
                joystickBase.anchoredPosition = origin;
                joystickBase.gameObject.SetActive(true);
            }
            if (joystickHandle != null) joystickHandle.localPosition = Vector3.zero;
            Value = Vector2.zero;
        }

        public void OnDrag(PointerEventData eventData)
        {
            if (!active) return;
            RectTransformUtility.ScreenPointToLocalPointInRectangle(canvasRoot, eventData.position, eventData.pressEventCamera, out Vector2 pos);
            Vector2 delta = pos - origin;
            if (delta.magnitude > radius) delta = delta.normalized * radius;
            if (joystickHandle != null) joystickHandle.localPosition = delta;
            Value = delta / radius;
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            active = false;
            Value = Vector2.zero;
            if (joystickBase != null) joystickBase.gameObject.SetActive(false);
            if (joystickHandle != null) joystickHandle.localPosition = Vector3.zero;
        }
    }
}

using UnityEngine;
using UnityEngine.EventSystems;

namespace City.UI
{
    /// <summary>
    /// Pedale/sensori di guida tenuto premuto (acceleratore, retromarcia,
    /// sterzo): Held resta vero finche' il dito e' giu'. PointerExit libera
    /// il pedale se il dito scorre fuori, evitando auto impazzite.
    /// </summary>
    public class DrivePedal : MonoBehaviour, IPointerDownHandler, IPointerUpHandler, IPointerExitHandler
    {
        public bool Held { get; private set; }

        public void OnPointerDown(PointerEventData e) { Held = true; }
        public void OnPointerUp(PointerEventData e) { Held = false; }
        public void OnPointerExit(PointerEventData e) { Held = false; }
        public void OnDisable() { Held = false; }
    }
}

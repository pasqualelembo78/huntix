using UnityEngine;

namespace EmptyRoom
{
    // Raycast dal centro dello schermo (crosshair) al click del mouse: se colpisce
    // un Interactable, ne richiama OnInteract().
    public class InteractionManager : MonoBehaviour
    {
        [SerializeField] private float maxDistance = 20f;
        private Camera _cam;

        private void Awake() => _cam = Camera.main;

        private void Update()
        {
            if (!Input.GetMouseButtonDown(0) || _cam == null) return;

            Ray ray = _cam.ScreenPointToRay(Input.mousePosition);
            if (Physics.Raycast(ray, out RaycastHit hit, maxDistance))
            {
                var it = hit.collider.GetComponentInParent<Interactable>();
                if (it != null) it.OnInteract();
            }
        }
    }
}

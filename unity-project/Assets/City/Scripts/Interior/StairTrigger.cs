using UnityEngine;
using City.UI;

namespace City.Interior
{
    /// <summary>
    /// Trigger per le scale. Quando il player entra, mostra l'etichetta.
    /// Al tap su Interact, cambia piano.
    /// </summary>
    public class StairTrigger : MonoBehaviour
    {
        [HideInInspector] public int currentFloor;
        [HideInInspector] public int totalFloors;

        private bool focused;

        public bool IsFocused => focused;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;

            int managerFloor = InteriorManager.Instance != null ? InteriorManager.Instance.GetCurrentFloor() : currentFloor;
            bool canGoUp = managerFloor < totalFloors - 1;
            bool canGoDown = managerFloor > 0;

            string label;
            if (canGoUp && canGoDown)
                label = "SCALE SU/GIU";
            else if (canGoUp)
                label = "SCALE SU";
            else
                label = "SCALE GIU";

            if (UIManager.Instance != null)
                UIManager.Instance.ShowInteract(label);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;

            if (UIManager.Instance != null)
                UIManager.Instance.HideInteract();
        }

        public void Interact()
        {
            if (InteriorManager.Instance == null) return;

            int managerFloor = InteriorManager.Instance.GetCurrentFloor();
            bool canGoUp = managerFloor < totalFloors - 1;
            bool canGoDown = managerFloor > 0;

            if (canGoDown && managerFloor > 0)
                InteriorManager.Instance.ChangeFloor(-1);
            else if (canGoUp)
                InteriorManager.Instance.ChangeFloor(1);
        }
    }
}

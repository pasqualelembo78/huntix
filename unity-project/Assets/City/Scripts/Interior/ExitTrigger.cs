using UnityEngine;
using City.UI;

namespace City.Interior
{
    /// <summary>
    /// Trigger uscita dall'interno. Quando il player è vicino, mostra "USCITA".
    /// Al tap su Interact, esce dall'edificio.
    /// </summary>
    public class ExitTrigger : MonoBehaviour
    {
        private bool focused;

        public bool IsFocused => focused;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            if (InteriorManager.Instance != null)
                InteriorManager.Instance.RegisterInteriorAction(Interact, "USCITA");
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            if (InteriorManager.Instance != null)
                InteriorManager.Instance.UnregisterInteriorAction(Interact);
        }

        public void Interact()
        {
            if (InteriorManager.Instance != null)
                InteriorManager.Instance.ExitInterior();
        }
    }
}

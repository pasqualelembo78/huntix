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
        /// <summary>Per le case a ingresso naturale non viene mostrato il
        /// prompt "USCITA": si esce camminando fuori dalla porta. Il tap
        /// resta disponibile solo negli altri edifici.</summary>
        public bool suppressPrompt;

        private bool focused;

        public bool IsFocused => focused;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            var mgr = InteriorManager.Instance;
            if (suppressPrompt)
            {
                // Case a ingresso naturale: varcare la soglia interna è già
                // l'uscita, senza prompt né tap. La porta prefab riporta il
                // player davanti all'esterno con lo stesso fade di sempre.
                if (mgr != null && mgr.IsInside) mgr.ExitInterior();
                return;
            }
            if (mgr != null)
                mgr.RegisterInteriorAction(Interact, "USCITA");
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

using UnityEngine;
using City.UI;
using City.Vehicle;

namespace City.Interior
{
    /// <summary>
    /// Trigger del bancone di un POI veicolo nell'interno 3D.
    /// Quando il player è vicino mostra la label del POI e al tap apre
    /// l'UI veicolo corrispondente (concessionaria / officina / garage),
    /// delegando al VehiclePoiZone esterno (che possiede delivery point e id).
    /// </summary>
    public class VehicleCounterTrigger : MonoBehaviour
    {
        private bool focused;

        public bool IsFocused => focused;

        private VehiclePoiZone PoiZone =>
            InteriorManager.Instance != null ? InteriorManager.Instance.ActiveVehicleZone : null;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            var zone = PoiZone;
            InteriorManager mgr = InteriorManager.Instance;
            if (mgr != null)
                mgr.RegisterInteriorAction(Interact,
                    zone != null ? zone.Label : "NEGOZIO");
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
            var zone = PoiZone;
            if (zone != null) zone.Interact();
        }
    }
}

using UnityEngine;
using City.World;

namespace City.Vehicle
{
    [RequireComponent(typeof(Collider))]
    public class VehicleInteract : MonoBehaviour
    {
        public VehicleController controller;
        public VehicleData data;
        public string label = "ENTRA";
        public string vehicleCode;

        private bool focused;
        public bool IsFocused => focused;

        private void Awake()
        {
            if (controller == null) controller = GetComponentInParent<VehicleController>();
            if (data == null && controller != null) data = controller.data;
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            string code = !string.IsNullOrEmpty(vehicleCode) ? " [" + vehicleCode + "]" : "";
            if (data != null && !IsOwned())
                label = "COMPRA " + data.vehicleName + " - \u20ac" + data.price + code;
            else
                label = "ENTRA " + (data != null ? data.vehicleName : "") + code;
            Game.Instance.OnVehicleFocusChanged(this);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            Game.Instance.OnVehicleFocusChanged(null);
        }

        public bool IsOwned()
        {
            if (string.IsNullOrEmpty(vehicleCode)) return false;
            return Inventory.Count("vehicle_" + vehicleCode) > 0;
        }

        public bool TryBuy()
        {
            if (data == null) return false;
            if (IsOwned()) return true;
            if (!Wallet.CanAfford(data.price)) return false;
            Wallet.Spend(data.price);
            Inventory.Add("vehicle_" + vehicleCode);
            return true;
        }
    }
}

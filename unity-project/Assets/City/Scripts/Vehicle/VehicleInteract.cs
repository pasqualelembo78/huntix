using UnityEngine;
using City.World;
using City.OSM;

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
        private bool buyPending;

        public bool IsFocused => focused;

        // oltre questa distanza dal veicolo il prompt viene sganciato anche
        // se OnTriggerExit e' andato perso (teletrasporto, collider disattivo,
        // chunk scaricato): era la causa del "COMPRA" incollato sullo schermo
        private const float UnfocusDistSq = 9f * 9f;

        private void Update()
        {
            if (!focused) return;
            var p = Game.Instance != null ? Game.Instance.player : null;
            if (p == null) return;
            if ((p.transform.position - transform.position).sqrMagnitude > UnfocusDistSq)
                Unfocus();
        }

        /// <summary>Sgancia il prompt per distanza (rete di sicurezza).</summary>
        public void Unfocus()
        {
            if (!focused) return;
            focused = false;
            Game.Instance.OnVehicleFocusChanged(this);
        }

        /// <summary>Vera quando il server ha confermato che e' di un ALTRO giocatore.</summary>
        public bool OwnedByOther { get; private set; }

        private void Awake()
        {
            if (controller == null) controller = GetComponentInParent<VehicleController>();
            if (data == null && controller != null) data = controller.data;
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            RefreshLabel();
            Game.Instance.OnVehicleFocusChanged(this);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            Game.Instance.OnVehicleFocusChanged(null);
        }

        private void RefreshLabel()
        {
            string code = !string.IsNullOrEmpty(vehicleCode) ? " [" + vehicleCode + "]" : "";
            string name = data != null ? data.vehicleName : "";
            if (IsOwned())
                label = "ENTRA " + name + code;
            else if (OwnedByOther)
                label = name + " - VENDUTA AD ALTRO GIOCATORE" + code;
            else
                // gli acquisti avvengono SOLO in concessionaria
                label = name + " - in vendita in concessionaria" + code;
        }

        /// <summary>Chiamare dopo Buy/Sell per aggiornare il prompt a schermo.</summary>
        public void NotifyStateChanged()
        {
            if (focused) RefreshLabel();
            if (Game.Instance != null) Game.Instance.OnVehicleFocusChanged(focused ? this : null);
        }

        public bool IsOwned()
        {
            if (string.IsNullOrEmpty(vehicleCode)) return false;
            return Inventory.Count("vehicle_" + vehicleCode) > 0 ||
                VehicleOwnershipApi.IsOwnedSafe(vehicleCode);
        }

        /// <summary>
        /// Acquisto: prima il server (fonte di verita' condivisa), poi il
        /// wallet locale. Se il server rifiuta (auto gia' venduta ad altro
        /// giocatore) niente soldi toccati. Il wallet e' locale ma l'exclusive
        /// dell'auto e' garantita dal codice deterministico + registro server.
        /// </summary>
        public void TryBuy(System.Action<bool> done = null)
        {
            if (data == null || buyPending) { done?.Invoke(false); return; }
            if (IsOwned()) { done?.Invoke(true); return; }
            if (!Wallet.CanAfford(data.price)) { done?.Invoke(false); return; }

            buyPending = true;
            var api = VehicleOwnershipApi.Ensure();
            api.Buy(vehicleCode, (ok, err) =>
            {
                buyPending = false;
                if (!ok)
                {
                    // probabilmente comprata prima da qualcun altro
                    if (err != null && err.Contains("owned"))
                        OwnedByOther = true;
                    RefreshLabel();
                    done?.Invoke(false);
                    return;
                }
                Wallet.Spend(data.price);
                Inventory.Add("vehicle_" + vehicleCode);
                GeoCoord g = WorldOrigin.ToGeo(transform.position);
                api.MarkOwned(vehicleCode, g.lat, g.lng,
                    transform.eulerAngles.y);
                RefreshLabel();
                // aggiorna subito il prompt a schermo (COMPRA -> VENDI/ENTRA)
                NotifyStateChanged();
                done?.Invoke(true);
            });
        }

        /// <summary>Vendita: 60% del prezzo, rimuove possesso locale + server.</summary>
        public void TrySell(System.Action<bool> done = null)
        {
            if (data == null || !IsOwned()) { done?.Invoke(false); return; }
            var api = VehicleOwnershipApi.Ensure();
            api.Sell(vehicleCode, (ok, err) =>
            {
                if (!ok) { done?.Invoke(false); return; }
                Inventory.Remove("vehicle_" + vehicleCode);
                Wallet.Earn(data.price * 6 / 10);
                api.MarkSold(vehicleCode);
                RefreshLabel();
                done?.Invoke(true);
            });
        }
    }
}

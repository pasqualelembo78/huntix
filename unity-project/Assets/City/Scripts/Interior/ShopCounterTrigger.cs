using UnityEngine;
using City.UI;
using City.World;

namespace City.Interior
{
    /// <summary>
    /// Trigger per il bancone del negozio nell'interno 3D.
    /// Quando il player è vicino, mostra "COMPRA". Al tap, apre lo shop UI.
    /// </summary>
    public class ShopCounterTrigger : MonoBehaviour
    {
        [HideInInspector] public Shop shop;

        private bool focused;

        public bool IsFocused => focused;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            if (UIManager.Instance != null && shop != null)
                UIManager.Instance.ShowInteract(shop.shopName);
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
            if (shop != null && Game.Instance != null)
                Game.Instance.OpenShop(shop);
        }
    }
}

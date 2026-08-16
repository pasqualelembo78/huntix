using UnityEngine;

namespace City.World
{
    [RequireComponent(typeof(Collider))]
    public class InteractDoor : MonoBehaviour
    {
        public string label = "ENTRA";
        public Transform destination;
        public Quaternion destinationRotation = Quaternion.identity;
        public bool opensShop;
        public Shop shop;

        private bool focused;

        public bool IsFocused
        {
            get { return focused; }
        }

        public void SetDestination(Transform dst, Quaternion rot)
        {
            destination = dst;
            destinationRotation = rot;
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            Game.Instance.OnDoorFocusChanged(this);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            Game.Instance.OnDoorFocusChanged(this);
        }

        public void Interact()
        {
            if (opensShop && shop != null)
            {
                Game.Instance.OpenShop(shop);
                return;
            }
            if (destination == null) return;
            Game.Instance.TeleportPlayer(destination.position, destinationRotation);
        }
    }
}

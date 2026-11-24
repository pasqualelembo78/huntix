using System.Collections.Generic;
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

        // MafiaUnity (Apache-2.0): latch temporale dopo un teleport riuscito.
        // Il valore predefinito resta false: le porte esistenti non cambiano.
        public bool useDoorLatch = false;

        private static readonly HashSet<InteractDoor> AllDoors = new HashSet<InteractDoor>();
        private static bool _eggGranted;
        private bool focused;
        private float _latchUntil;

        public bool IsFocused
        {
            get { return focused; }
        }

        /// <summary>Inizializzazione passiva: nessun uovo all'avvio.</summary>
        public static void Initialize()
        {
            City.Diagnostics.OpenSourceFeatureBridge.Register(
                "door-latch", on => SetLatchAll(on));
        }

        public static void SetLatchAll(bool on)
        {
            foreach (var door in AllDoors)
                if (door != null) door.useDoorLatch = on;
        }

        private void Awake()
        {
            AllDoors.Add(this);
            useDoorLatch = City.Diagnostics.OpenSourceFeatureBridge.Enabled("door-latch");
        }

        private void OnDestroy()
        {
            AllDoors.Remove(this);
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
            // Solo il ramo teleport ha il latch: "BUSSA" e le porte negozio
            // restano sempre utilizzabili (nessun cambio di comportamento).
            if (useDoorLatch && Time.unscaledTime < _latchUntil) return;
            Game.Instance.TeleportPlayer(destination.position, destinationRotation);
            if (!useDoorLatch) return;
            // Tempo NON scalato: in pausa il latch non puo' rimanere forever.
            _latchUntil = Time.unscaledTime + 1f;
            if (!_eggGranted)
            {
                _eggGranted = true;
                City.Diagnostics.OpenSourceFeatureBridge.Report(
                    "door-latch", "Uovo dietro la porta", "città→porta",
                    "latch 1s dopo teleport", true);
            }
        }
    }
}

using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Porta interna apribile a battente (F3: stanze con porte reali).
    /// Pannello con collider che ruota su un cardine (bordo X negativo della
    /// soglia) di +-100 gradi. Il giocatore la apre/chiude dal pulsante azioni
    /// quando è accanto (stesso meccanismo di StairTrigger/ShopCounterTrigger).
    /// Chiusa blocca il passaggio, aperta libera la stanza.
    /// </summary>
    public class InteriorDoor : MonoBehaviour
    {
        public Transform panel;

        private float _targetYaw = 0f;
        private float _yaw = 0f;
        private bool _open;
        private bool _focused;

        private void Update()
        {
            _yaw = Mathf.MoveTowards(_yaw, _targetYaw, 160f * Time.deltaTime);
            if (panel != null && Mathf.Abs(_yaw) > 0.01f)
                panel.localRotation = Quaternion.Euler(0f, _yaw, 0f);
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            _focused = true;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.RegisterInteriorAction(Toggle, _open ? "CHIUDI PORTA" : "APRI PORTA");
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            _focused = false;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.UnregisterInteriorAction(Toggle);
        }

        public void Toggle()
        {
            _open = !_open;
            _targetYaw = _open ? -100f : 0f;
            var mgr = InteriorManager.Instance;
            if (mgr != null)
                mgr.RegisterInteriorAction(Toggle, _open ? "CHIUDI PORTA" : "APRI PORTA");
            Debug.Log("[InteriorDoor] " + name + " -> " + (_open ? "aperta" : "chiusa"));
            UnityBridge.LogToAndroid("InteriorDoor", _open ? "aperta" : "chiusa");
        }
    }
}

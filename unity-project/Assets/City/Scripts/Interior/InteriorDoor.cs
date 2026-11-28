using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Porta interna apribile a battente (F3: stanze con porte reali).
    /// Pannello con collider che ruota su un cardine (bordo X negativo della
    /// soglia) di +-100 gradi. Con <see cref="autoOpen"/> si apre da sola
    /// all'avvicinarsi: nessun pulsante, nessun riquadro, senza modificare
    /// mesh, pivot o collider dell'asset. Chiusa blocca il passaggio, aperta
    /// libera la stanza.
    /// </summary>
    public class InteriorDoor : MonoBehaviour
    {
        public Transform panel;

        /// <summary>Apertura automatica all'avvicinamento (case visitabili).</summary>
        public bool autoOpen;

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
            if (autoOpen)
            {
                // La porta resta APERTA dopo il passaggio: richiuderla mentre
                // il player è nel varco potrebbe spingerlo contro il muro.
                if (!_open) SetOpen(true);
                return;
            }
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.RegisterInteriorAction(Toggle, _open ? "CHIUDI PORTA" : "APRI PORTA");
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            _focused = false;
            if (autoOpen) return;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.UnregisterInteriorAction(Toggle);
        }

        public void Toggle()
        {
            if (autoOpen) return;
            SetOpen(!_open);
        }

        private void SetOpen(bool open)
        {
            _open = open;
            _targetYaw = open ? -100f : 0f;
            if (!autoOpen)
            {
                var mgr = InteriorManager.Instance;
                if (mgr != null)
                    mgr.RegisterInteriorAction(Toggle, _open ? "CHIUDI PORTA" : "APRI PORTA");
            }
            Debug.Log("[InteriorDoor] " + name + " -> " + (_open ? "aperta" : "chiusa"));
            UnityBridge.LogToAndroid("InteriorDoor", _open ? "aperta" : "chiusa");
        }
    }
}

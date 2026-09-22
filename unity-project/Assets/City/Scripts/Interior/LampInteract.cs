using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// F5 illuminazione: lampade interne interattive. Il giocatore, accanto a
    /// una lampada (Lampada/LampCom1), apre il menu azioni "ACCENDI/SPEGNI
    /// LAMPADA": la luce point unica dell'edificio passa da piena (giorno) a
    /// ridotta e calda (luce notte). Un solo toggle per edificio, costo
    /// runtime praticamente nullo (regola solo l'intensita' della Light).
    /// </summary>
    public class LampInteract : MonoBehaviour
    {
        private bool _night;

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.RegisterInteriorAction(Toggle, "ACCENDI LAMPADA");
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.UnregisterInteriorAction(Toggle);
        }

        public void Toggle()
        {
            var gen = GetComponentInParent<InteriorGenerator>();
            if (gen == null) return;
            _night = !_night;
            gen.SetLightIntensity(_night ? 0.28f : 1f);
            var mgr = InteriorManager.Instance;
            if (mgr != null)
                mgr.RegisterInteriorAction(Toggle, _night ? "SPEGNI LAMPADA" : "ACCENDI LAMPADA");
            var g = Game.Instance;
            if (g != null && g.ui != null)
                g.ui.ShowToast(_night ? "Lampada: luce notte." : "Lampada accesa al massimo.");
            UnityBridge.LogToAndroid("LampInteract", _night ? "notte" : "giorno");
        }
    }
}

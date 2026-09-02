using UnityEngine;

namespace City.Afterlife
{
    /// <summary>
    /// Danno da pozza lavica (Inferno, 3.4): mentre il player resta nel trigger
    /// perde vita/bisogni molto velocemente. Il trigger viene creato da
    /// RealmSceneController.BuildHazard e qui aggiunto come gestore.
    /// </summary>
    public class LavaDamage : MonoBehaviour
    {
        private float _nextTick;

        private void OnTriggerStay(Collider other)
        {
            if (other == null) return;
            if (!other.CompareTag("Player")) return;

            // delimita la frequenza: danno una volta al secondo
            if (Time.unscaledTime < _nextTick) return;
            _nextTick = Time.unscaledTime + 1f;

            var pc = other.GetComponentInParent<City.Player.PlayerController>();
            if (pc == null) return;

            City.NPC.FamilyManager.Hurt(12);
            var g = City.Game.Instance;
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udd25 Lava! Subisci danni.");
        }
    }
}
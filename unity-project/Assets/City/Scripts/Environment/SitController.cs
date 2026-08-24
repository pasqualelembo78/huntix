using UnityEngine;
using City.Player;

namespace City.Environment
{
    /// <summary>
    /// Gestisce la seduta del player su panchine: blocca il controller,
    /// fissa la posa sul sedile, rigenera energia lentamente e si alza al
    /// tap sullo schermo o dopo 90 secondi di sicurezza.
    /// </summary>
    public class SitController : MonoBehaviour
    {
        private static SitController _instance;

        public static bool IsSitting
        {
            get { return _instance != null && _instance._sitting; }
        }

        private bool _sitting;
        private Transform _player;
        private MonoBehaviour _disabledCtrl;
        private float _sitStart;
        private float _nextRegen;

        public static void Sit(Transform player, Vector3 seatPos, Quaternion facing)
        {
            if (_instance == null)
            {
                var go = new GameObject("SitController");
                DontDestroyOnLoad(go);
                _instance = go.AddComponent<SitController>();
            }
            _instance.Begin(player, seatPos, facing);
        }

        public static void StandUp()
        {
            if (_instance != null) _instance.End();
        }

        private void Begin(Transform player, Vector3 seatPos, Quaternion facing)
        {
            if (player == null) return;
            if (_sitting) End();

            _player = player;
            var pc = player.GetComponent<PlayerController>();
            if (pc != null)
            {
                pc.enabled = false;
                _disabledCtrl = pc;
            }
            // sedile: leggermente sopra la seduta, orientato come la panchina
            player.position = seatPos + Vector3.up * -0.35f;
            player.rotation = facing;
            _sitting = true;
            _sitStart = Time.unscaledTime;
            _nextRegen = 0f;
            Toast("\ud83e\ude91 Seduto: recuperi energia. Tocca lo schermo per alzarti");
        }

        private void End()
        {
            if (!_sitting) return;
            _sitting = false;
            if (_disabledCtrl != null) _disabledCtrl.enabled = true;
            _disabledCtrl = null;
            if (_player != null)
            {
                // rialzati accanto alla panchina, non dentro di essa
                _player.position += _player.forward * 0.9f + Vector3.up * 0.4f;
            }
            _player = null;
        }

        private void Update()
        {
            if (!_sitting || _player == null) return;

            // rigenerazione lenta da seduto (+1 ogni 1,2 s)
            if (Time.unscaledTime >= _nextRegen)
            {
                _nextRegen = Time.unscaledTime + 1.2f;
                if (EnergySystem.Value < EnergySystem.MaxValue)
                    EnergySystem.Restore(1);
            }
            if (Time.unscaledTime - _sitStart > 90f) End();
        }

        private void Toast(string msg)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
                City.Game.Instance.ui.ShowToast(msg);
        }
    }
}

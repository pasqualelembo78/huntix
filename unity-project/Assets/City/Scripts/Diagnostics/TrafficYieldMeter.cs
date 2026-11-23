// Misura diagnostica, non policy di guida: conta i casi in cui un'auto di
// traffico sta gia' frenando davanti al player o a un pedone (il comportamento
// esiste in CarAgent.CheckObstacle). Serve a confrontare con numeri reali un
// eventuale futuro overlay di precedenza crosswalk derivato da FuzzyTown
// (Apache 2.0, Copyright (c) 2021 Petrie van Zyl): nessun codice di quel repo
// e' stato copiato, qui si registra solo il comportamento Huntix attuale.
using UnityEngine;

namespace City.Diagnostics
{
    public static class TrafficYieldMeter
    {
        private const int FlushEvery = 25;
        private static int _events;
        private static int _player;
        private static int _pedestrian;
        private static float _nearest = float.MaxValue;
        private static float _speedSum;

        /// <summary>Chiamato da CarAgent.CheckObstacle quando l'auto ha già
        /// deciso di rallentare: non modifica velocità, corsia o semafori.</summary>
        public static void Record(bool isPlayer, float distance, float speed)
        {
            _events++;
            if (isPlayer) _player++; else _pedestrian++;
            _nearest = Mathf.Min(_nearest, distance);
            _speedSum += speed;
            if (_events >= FlushEvery) Flush();
        }

        private static void Flush()
        {
            if (_events == 0) return;
            var telemetry = SessionTelemetry.Instance;
            if (telemetry != null)
            {
                telemetry.Mark("crosswalk: frenate={0} player={1} pedoni={2} " +
                    "dMin={3:F1}m vMedia={4:F1}km/h",
                    _events, _player, _pedestrian,
                    _nearest == float.MaxValue ? 0f : _nearest,
                    (_speedSum / _events) * 3.6f);
            }
            _events = 0;
            _player = 0;
            _pedestrian = 0;
            _nearest = float.MaxValue;
            _speedSum = 0f;
        }

        /// <summary>Riepilogo a richiesta: non interferisce con il beacon di
        /// SessionTelemetry e non aggiunge eventi bridge/uova.</summary>
        public static string SnapshotAndReset()
        {
            string s = "crosswalk: frenate=" + _events + " player=" + _player +
                " pedoni=" + _pedestrian;
            Flush();
            return s;
        }
    }
}

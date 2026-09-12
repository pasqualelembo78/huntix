using UnityEngine;

namespace City.Environment
{
    /// <summary>
    /// Gestore simulazione a tick fisso (20 tick/sec, pattern LifeVerse).
    /// Il gioco avanza indipendentemente dal frame rate: NPC, bisogni,
    /// carburante, economy vengono tickati a intervalli costanti.
    /// Il SimulationManager è un servizio MonoBehaviour che gira su
    /// DontDestroyOnLoad (come DayNightManager).
    /// </summary>
    public class SimulationManager : MonoBehaviour
    {
        public static SimulationManager Instance { get; private set; }

        /// <summary>Tick rate della simulazione (tick al secondo).</summary>
        public const float TickRate = 20f;
        /// <summary>Intervallo tra due tick (secondi reali).</summary>
        public const float TickInterval = 1f / TickRate;

        /// <summary>Velocita' della simulazione (1=normale, 3=veloce, 20=sonno).</summary>
        public float SpeedMultiplier { get; set; } = 1f;

        /// <summary>Giorni totali di gioco (da DayChangedEvent).</summary>
        public int DayCount { get; private set; } = 1;

        private float _accumulator;
        private int _lastHour = -1;

        public static SimulationManager Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("SimulationManager");
            DontDestroyOnLoad(go);
            return go.AddComponent<SimulationManager>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Update()
        {
            if (SpeedMultiplier <= 0f) return;

            _accumulator += Time.deltaTime * SpeedMultiplier;
            int safety = 0;
            while (_accumulator >= TickInterval && safety < 10)
            {
                _accumulator -= TickInterval;
                Tick(TickInterval);
                safety++;
            }
            if (safety >= 10) _accumulator = 0f;
        }

        private void Tick(float dt)
        {
            // pubblica cambio ora (ogni minuto game = 3 sec reali a 1x)
            var dnm = DayNightManager.Instance;
            if (dnm != null)
            {
                int h = (int)dnm.ClockHours;
                if (h != _lastHour)
                {
                    _lastHour = h;
                    EventBus.Publish(new HourChangedEvent(h));
                    if (h == 0)
                    {
                        DayCount++;
                        EventBus.Publish(new DayChangedEvent(DayCount));
                    }
                }
            }
        }
    }
}

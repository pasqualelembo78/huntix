using UnityEngine;

namespace City.Diagnostics
{
    /// Adaptive quality governor: misura il frame time EMA e scala da solo il
    /// carico (raggio cull veicoli, raggio render/animazione NPC) per tenere
    /// il frame budget anche sui device lenti. Deterministico-safe: tocca solo
    /// attivazione runtime, mai la generazione (i codici venduti restano
    /// identici su ogni client).
    public class PerformanceGovernor : MonoBehaviour
    {
        public static PerformanceGovernor Instance { get; private set; }

        private const int MaxTier = 4;
        // degrada se ema>Floor per UpConfirm s; risale se ema<Target per
        // ImproveRamp s (isteresi per non oscillare a ogni incrocio)
        private const float FloorMs = 60f;
        private const float TargetMs = 33f;
        private const float UpConfirmSeconds = 3f;
        private const float ImproveRampSeconds = 6f;

        private static readonly float[] VehCullTier =
            { 260f, 200f, 150f, 110f, 80f };
        private static readonly float[] NpcRenderTier =
            { 280f, 230f, 180f, 140f, 110f };
        private static readonly float[] NpcAnimTier =
            { 160f, 120f, 90f, 65f, 45f };

        public int Tier { get; private set; }

        /// <summary>Frame time mediato usato anche dall'Uovo del budget: e' la
        /// stessa misura che governa la qualita', quindi il premio premia
        /// esattamente cio' che il gioco considera "fluido".</summary>
        public float EmaMs => _emaMs;

        public float VehicleCullRadius => VehCullTier[Mathf.Clamp(Tier, 0, MaxTier)];
        public float NpcRenderRadius => NpcRenderTier[Mathf.Clamp(Tier, 0, MaxTier)];
        public float NpcAnimRadius => NpcAnimTier[Mathf.Clamp(Tier, 0, MaxTier)];

        private float _emaMs;
        private float _overloadSeconds;
        private float _goodSeconds;
        private bool _started;

        public static PerformanceGovernor Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("PerformanceGovernor");
            DontDestroyOnLoad(go);
            return go.AddComponent<PerformanceGovernor>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Update()
        {
            if (!_started && Time.realtimeSinceStartup > 3f) _started = true;
            if (!_started) return;
            float rawDt = Time.unscaledDeltaTime;
            // Un singolo stall non e' carico SOSTENUTO: il 23/11 un frame da
            // 20690 ms ha portato l'EMA a 2108 ms e fatto salire il tier al
            // massimo in 4 frame, degradando la qualita' per oltre un secondo
            // dopo che il gioco era gia' tornato a 30 fps. Clampiamo sia il
            // campione sia l'integrazione dei secondi, senza buttarlo via:
            // lo stall resta visibile nel beacon di SessionTelemetry.
            float dt = Mathf.Min(rawDt, 0.25f);
            float ms = Mathf.Min(rawDt * 1000f, 250f);
            _emaMs = _emaMs <= 0f ? ms : Mathf.Lerp(_emaMs, ms, 0.1f);

            bool overload = _emaMs > FloorMs;
            bool smooth = _emaMs < TargetMs;
            if (overload)
            {
                _goodSeconds = 0f;
                // UpConfirmSeconds e' in SECONDI: contare i frame faceva
                // raggiungere il tier massimo in 3 frame (0,4 s a 8 fps nel
                // log 23/11) invece che nei 3 secondi previsti.
                _overloadSeconds += dt;
                if (_overloadSeconds >= UpConfirmSeconds && Tier < MaxTier)
                {
                    _overloadSeconds = 0f;
                    SetTier(Tier + 1);
                }
            }
            else
            {
                _overloadSeconds = 0f;
                if (smooth)
                {
                    _goodSeconds += dt;
                    if (_goodSeconds >= ImproveRampSeconds && Tier > 0)
                    {
                        _goodSeconds = 0f;
                        SetTier(Tier - 1);
                    }
                }
            }
        }

        private void SetTier(int t)
        {
            Tier = t;
            var tel = SessionTelemetry.Instance;
            if (tel != null)
                tel.Mark("GOVERNOR tier={0} vehCull={1:F0} npcRender={2:F0} npcAnim={3:F0} ema={4:F0}ms",
                    Tier, VehicleCullRadius, NpcRenderRadius, NpcAnimRadius, _emaMs);
        }
    }
}

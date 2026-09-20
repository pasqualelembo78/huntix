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

        public float VehicleCullRadius => VehCullTier[Mathf.Clamp(Tier, 0, MaxTier)];
        public float NpcRenderRadius => NpcRenderTier[Mathf.Clamp(Tier, 0, MaxTier)];
        public float NpcAnimRadius => NpcAnimTier[Mathf.Clamp(Tier, 0, MaxTier)];

        private float _emaMs;
        private int _upCount;
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
            float ms = Time.unscaledDeltaTime * 1000f;
            _emaMs = _emaMs <= 0f ? ms : Mathf.Lerp(_emaMs, ms, 0.1f);

            bool overload = _emaMs > FloorMs;
            bool smooth = _emaMs < TargetMs;
            if (overload)
            {
                _goodSeconds = 0f;
                _upCount++;
                if (_upCount >= UpConfirmSeconds && Tier < MaxTier)
                {
                    _upCount = 0;
                    SetTier(Tier + 1);
                }
            }
            else
            {
                _upCount = 0;
                if (smooth)
                {
                    _goodSeconds += Time.unscaledDeltaTime;
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

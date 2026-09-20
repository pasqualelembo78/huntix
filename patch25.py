import sys

def read(path):
    with open(path, encoding="utf-8") as f: return f.read()
def write(path, s):
    with open(path, "w", encoding="utf-8") as f: f.write(s)

def a(s, old, new, tag):
    if new in s:
        print("  =", tag, "(gia applicato)")
        return s
    assert s.count(old) == 1, tag + " (introvabile/multi)"
    s = s.replace(old, new)
    print("  +", tag)
    return s

base = "/root/giochi/huntix/unity-project/Assets/City/Scripts"

print("1) PerformanceGovernor.cs")
pg = '''using UnityEngine;

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
'''
write(base + "/Diagnostics/PerformanceGovernor.cs", pg)

print("2) ChunkVehiclePopulator cull dinamico")
p2 = base + "/Vehicle/Traffic/ChunkVehiclePopulator.cs"
s = read(p2)
s = a(s,
    "            float r2 = CullRadius * CullRadius;\n",
    "            float cr = CullRadius;\n"
    "            var pg = City.Diagnostics.PerformanceGovernor.Instance;\n"
    "            if (pg != null) cr = pg.VehicleCullRadius;\n"
    "            float r2 = cr * cr;\n",
    "veh cull dyn")
write(p2, s)

print("3) NPCController")
p3 = base + "/NPC/NPCController.cs"
s = read(p3)
s = a(s,
    "        private Camera _camCache;\n        private float _camRefreshAt;\n",
    "        private Camera _camCache;\n        private float _camRefreshAt;\n        private float _lastCamDistSqr = float.MaxValue;\n",
    "npc field")
s = a(s,
    "                float dsqr = (_camCache.transform.position - transform.position).sqrMagnitude;\n                shouldCull = dsqr > CullDistSqr;\n",
    "                float dsqr = (_camCache.transform.position - transform.position).sqrMagnitude;\n                _lastCamDistSqr = dsqr;\n                var pg = City.Diagnostics.PerformanceGovernor.Instance;\n                float r = pg != null ? pg.NpcRenderRadius : 280f;\n                shouldCull = dsqr > r * r;\n",
    "npc cull")
s = a(s,
    "            UpdateCull();\n            UpdateNameTag();\n            if (_down) return;\n",
    "            UpdateCull();\n            UpdateNameTag();\n            if (_down) return;\n            if (_culled) return;  // oltre il raggio render: niente simulazione ne' anim\n",
    "npc early")
s = a(s,
    "            if (walker != null) walker.SetSpeed(s);\n            if (animator != null) animator.SetFloat(\"Speed\", s);\n",
    "            var pg = City.Diagnostics.PerformanceGovernor.Instance;\n            float ar = pg != null ? pg.NpcAnimRadius : 160f;\n            bool animate = _lastCamDistSqr <= ar * ar;\n            if (walker != null)\n            {\n                if (walker.enabled != animate) walker.enabled = animate;\n                if (animate) walker.SetSpeed(s);\n            }\n            if (animator != null)\n            {\n                if (animator.speed > 0f != animate) animator.speed = animate ? 1f : 0f;\n                if (animate) animator.SetFloat(\"Speed\", s);\n            }\n",
    "npc anim")
write(p3, s)

print("4) ChunkManager serializzazione")
p4 = base + "/OSM/ChunkManager.cs"
s = read(p4)
s = a(s,
    "        public const int ExpectedChunkCount = (2 * LoadRadius + 1) * (2 * LoadRadius + 1);\n",
    "        public const int ExpectedChunkCount = (2 * LoadRadius + 1) * (2 * LoadRadius + 1);\n\n        /// <summary>Build coroutine attive: max 1 per volta. Senza serializzazione\n        /// N chunk in volo consumano ognuno fino a BuildBudgetMs di main-thread\n        /// (25 x 12ms = 300ms/frame) -> frame da 2115ms e fps=0 durante la build.</summary>\n        private int _activeBuilds;\n",
    "cm counter")
s = a(s,
    "                _retryAt.Remove(c);\n                _inFlight.Add(c);\n                OsmDiag.Log(\"[ChunkManager] retry build chunk \" + c.x + \",\" + c.y);\n                StartCoroutine(BuildChunkCoroutine(c));\n",
    "                if (_activeBuilds >= 1) break;\n                _retryAt.Remove(c);\n                _inFlight.Add(c);\n                _activeBuilds++;\n                OsmDiag.Log(\"[ChunkManager] retry build chunk \" + c.x + \",\" + c.y);\n                StartCoroutine(BuildChunkCoroutine(c));\n",
    "cm retry")
s = a(s,
    "            while (_pending.Count > 0 && clock.ElapsedMilliseconds < BuildBudgetMs)\n            {\n                var c = _pending[0];\n                _pending.RemoveAt(0);\n                OsmDiag.Log(\"[ChunkManager] === BUILD START === chunk \" + c.x + \",\" + c.y + \" pending=\" + _pending.Count);\n                StartCoroutine(BuildChunkCoroutine(c));\n            }\n",
    "            while (_activeBuilds < 1 && _pending.Count > 0 &&\n                clock.ElapsedMilliseconds < BuildBudgetMs)\n            {\n                var c = _pending[0];\n                _pending.RemoveAt(0);\n                _activeBuilds++;\n                OsmDiag.Log(\"[ChunkManager] === BUILD START === chunk \" + c.x + \",\" + c.y + \" pending=\" + _pending.Count);\n                StartCoroutine(BuildChunkCoroutine(c));\n            }\n",
    "cm pump")
s = a(s,
    "                _inFlight.Remove(c);\n                yield break;\n",
    "                _inFlight.Remove(c);\n                _activeBuilds--;\n                yield break;\n",
    "cm exits")
s = a(s,
    "            _inFlight.Remove(c);\n        }\n",
    "            _inFlight.Remove(c);\n            _activeBuilds--;\n        }\n",
    "cm final")
s = a(s,
    "            StopAllCoroutines();\n            _tickLoop = null;\n            _pending.Clear();\n",
    "            StopAllCoroutines();\n            _tickLoop = null;\n            _activeBuilds = 0;\n            _pending.Clear();\n",
    "cm unloadall")
write(p4, s)

print("5) SessionTelemetry.Ensure crea il governor")
p5 = base + "/Diagnostics/SessionTelemetry.cs"
s = read(p5)
s = a(s,
    "            var go = new GameObject(\"SessionTelemetry\");\n            DontDestroyOnLoad(go);\n            return go.AddComponent<SessionTelemetry>();\n",
    "            var go = new GameObject(\"SessionTelemetry\");\n            DontDestroyOnLoad(go);\n            PerformanceGovernor.Ensure();\n            return go.AddComponent<SessionTelemetry>();\n",
    "tel ensure")
write(p5, s)

print("OK patch25 applicata")
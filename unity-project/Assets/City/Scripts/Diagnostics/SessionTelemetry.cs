using UnityEngine;
using City.Player;
using City.Vehicle;

namespace City.Diagnostics
{
    // Telemetria della sessione AR virtuale (Miacitta) verso AppLog Android
    // (tag "AR"): beacon periodico con fps, tempi frame, memoria, posizione
    // player, camera, conteggio veicoli attivi; piu' marker di evento
    // (mondo pronto, guida in/out, teleport). Scatola nera per analisi.
    public class SessionTelemetry : MonoBehaviour
    {
        public static SessionTelemetry Instance { get; private set; }

        private const float BeaconInterval = 2f;

        private float beaconTimer;
        private float frameAccumMs;
        private int frameCount;
        private float bestFrameMs;
        private float worstFrameMs;
        private bool started;
        private readonly System.Text.StringBuilder sb =
            new System.Text.StringBuilder(256);

        public static SessionTelemetry Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("SessionTelemetry");
            DontDestroyOnLoad(go);
            PerformanceGovernor.Ensure();
            return go.AddComponent<SessionTelemetry>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Start()
        {
            started = true;
            Mark("SCENA avviata ver={0} quality={1} vsync={2} res={3}x{4} dev={5}",
                Application.version,
                QualitySettings.names[QualitySettings.GetQualityLevel()],
                QualitySettings.vSyncCount, Screen.width, Screen.height,
                SystemInfo.deviceModel);
        }

        public void Mark(string format, params object[] args)
        {
            if (!started) return;
            try
            {
                string msg = string.Format(format, args);
                Log("[T+" + Time.time.ToString("F1") + "s] " + msg);
            }
            catch (System.Exception e)
            {
                Log("[mark error] " + e.GetType().Name + ": " + e.Message);
            }
        }

        public void WorldReady(int roads, int buildings, int trees, int parks)
        {
            Mark("MONDO pronto: strade={0} edifici={1} alberi={2} parchi={3}",
                roads, buildings, trees, parks);
        }

        public void Driving(bool enter, string name, string code)
        {
            Mark("GUIDA {0} {1} [{2}]",
                enter ? "inizia" : "finisce", name, code);
        }

        private void Update()
        {
            float dt = Time.unscaledDeltaTime;
            float ms = dt * 1000f;
            frameAccumMs += ms;
            frameCount++;
            if (ms < bestFrameMs || frameCount == 1) bestFrameMs = ms;
            if (ms > worstFrameMs) worstFrameMs = ms;

            beaconTimer -= dt;
            if (beaconTimer <= 0f)
            {
                beaconTimer = BeaconInterval;
                Beacon();
            }
        }

        private void Beacon()
        {
            try
            {
                float fps = frameAccumMs > 0f
                    ? frameCount / (frameAccumMs / 1000f) : 0f;
                sb.Length = 0;
                sb.Append("[T+").Append(Time.time.ToString("F0")).Append("s] ");
                sb.Append("fps=").Append(fps.ToString("F0"));
                sb.Append(" frame=")
                  .Append((Time.unscaledDeltaTime * 1000f).ToString("F1"))
                  .Append("ms");
                sb.Append(" best=").Append(bestFrameMs.ToString("F2"));
                sb.Append(" worst=").Append(worstFrameMs.ToString("F1"));
                sb.Append(" ram=")
                  .Append((UnityEngine.Profiling.Profiler
                      .GetTotalReservedMemoryLong() / 1048576L)
                      .ToString("F0")).Append("MB");

                var game = City.Game.Instance;
                if (game != null && game.player != null)
                {
                    Vector3 pp = game.player.transform.position;
                    sb.Append(" player=(").Append(pp.x.ToString("F0"))
                      .Append(",").Append(pp.z.ToString("F0")).Append(")");
                    sb.Append(" heading=")
                      .Append(Mathf.Repeat(
                          game.player.transform.eulerAngles.y, 360f)
                          .ToString("F0"));
                }
                if (game != null && game.IsDriving &&
                    game.CurrentVehicle != null)
                    sb.Append(" inAuto=1");

                var cam = Camera.main;
                if (cam != null)
                {
                    sb.Append(" camY=")
                      .Append(Mathf.Repeat(cam.transform.eulerAngles.y, 360f)
                          .ToString("F0"));
                    var rig = CameraRig.Instance;
                    if (rig != null)
                        sb.Append(" camD=").Append(rig.distance.ToString("F1"));
                }

                var vp = ChunkVehiclePopulator.Instance;
                if (vp != null)
                    sb.Append(" vehOn=").Append(vp.CountActive())
                      .Append("/").Append(vp.CountTotal());

                if (fps < 15f)
                    sb.Append(" LAG");

                Log(sb.ToString());

                frameAccumMs = 0f;
                frameCount = 0;
                bestFrameMs = float.MaxValue;
                worstFrameMs = 0f;
            }
            catch (System.Exception e)
            {
                Log("[beacon error] " + e.GetType().Name + ": " + e.Message);
                frameAccumMs = 0f;
                frameCount = 0;
                bestFrameMs = float.MaxValue;
                worstFrameMs = 0f;
            }
        }

        private static void Log(string msg)
        {
            UnityEngine.Debug.Log("[AR] " + msg);
            try { Huntix.Bridge.UnityBridge.LogToAndroid("AR", msg); }
            catch (System.Exception) {}
        }
    }
}

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

        /// <summary>True dopo MONDO pronto: l'Uovo del budget si arma solo
        /// quando la citta' e' realmente caricata, non durante la build.</summary>
        public static bool WorldIsReady { get; private set; }

        private const float BeaconInterval = 2f;

        private float beaconTimer;
        private float frameAccumMs;
        private int frameCount;
        private float bestFrameMs;
        private float worstFrameMs;
        private bool started;
        private readonly System.Text.StringBuilder sb =
            new System.Text.StringBuilder(512); // Giorno 2: beacon piu' lungo

        // GIORNO 2: istogramma dei frame. best/worst non dicono QUANTO SPESSO
        // il frame e' cattivo: con questi bucket si vede subito se i 400 ms
        // sono un episodio o la regola. Bucket: <16 / <33 / <50 / <100 /
        // <400 / >=400 ms (ultimo = "frame mostruoso").
        private int _h16, _h33, _h50, _h100, _h400, _hOver;
        // ATTENZIONE: e' memoria RISERVATA da Unity (managed+native), NON il
        // RSS/PSS del processo che l'OOM killer Android guarda. Utile come
        // contesto; per la caccia a LOW_MEMORY servira' il valore di sistema
        // via bridge (Giorno 3 / 18).
        private long _peakRamMb;

        // GIORNO 2: contatore uova. indoor = contenitore/cassetto o player
        // dentro un edificio; outdoor = uova sparse nei chunk. Riflesso uova
        // del Mandato: distingue finalmente le due cacce.
        private static int _eggsIndoor;
        private static int _eggsOutdoor;

        /// <summary>Chiamato da Game.OnEggCollected per l'istogramma uova.
        /// Statico perche' la cattura puo' avvenire prima che il beacon parta:
        /// se Instance e' null il conteggio resta in memoria e appare al
        /// primo beacon utile.</summary>
        public static void EggCollected(bool indoor)
        {
            if (indoor) _eggsIndoor++;
            else _eggsOutdoor++;
        }

        private void ResetFrameBuckets()
        {
            _h16 = _h33 = _h50 = _h100 = _h400 = _hOver = 0;
        }

        public static SessionTelemetry Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("SessionTelemetry");
            DontDestroyOnLoad(go);
            PerformanceGovernor.Ensure();
            City.Economy.BudgetEggWatcher.Ensure();
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
            WorldIsReady = true;
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

            // GIORNO 2: un solo confronto per frame, costo trascurabile.
            if (ms < 16f) _h16++;
            else if (ms < 33f) _h33++;
            else if (ms < 50f) _h50++;
            else if (ms < 100f) _h100++;
            else if (ms < 400f) _h400++;
            else _hOver++;

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
                long ramMb = UnityEngine.Profiling.Profiler
                    .GetTotalReservedMemoryLong() / 1048576L;
                if (ramMb > _peakRamMb) _peakRamMb = ramMb;
                sb.Append(" ram=").Append(ramMb).Append("MB")
                  .Append(" peak=").Append(_peakRamMb).Append("MB");
                // Profiler interno: medie/picchi dei 2 secondi precedenti.
                // Nessun marker Unity Profiler: nella build non sarebbe
                // leggibile dal viewer in-app; qui tutto passa da UnityBridge.
                sb.Append(' ').Append(PerfProbe.SnapshotAndReset());

                // GIORNO 2: istogramma + contatore uova (vede anche le uova
                // catturate prima che il beacon partisse).
                sb.Append(" hist=16:").Append(_h16)
                  .Append("/33:").Append(_h33)
                  .Append("/50:").Append(_h50)
                  .Append("/100:").Append(_h100)
                  .Append("/400:").Append(_h400)
                  .Append("/X:").Append(_hOver);
                sb.Append(" uova=I").Append(_eggsIndoor)
                  .Append("/O").Append(_eggsOutdoor);

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
                    // GIORNO 2 / REVIEW: il follower salva sorgente e nome
                    // separati (zero allocazioni per frame); qui, ogni 2 s,
                    // li componiamo in un'unica stringa. Nessun raycast nuovo.
                    string surfName = game.player.LastGroundName;
                    if (game.player.LastGroundSource != City.OSM.GroundSource.None ||
                        !string.IsNullOrEmpty(surfName))
                    {
                        if (!string.IsNullOrEmpty(surfName) && surfName.Length > 24)
                            surfName = surfName.Substring(0, 24);
                        sb.Append(" ground=").Append(game.player.LastGroundSource)
                          .Append('/').Append(string.IsNullOrEmpty(surfName) ? "-" : surfName);
                    }
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
                ResetFrameBuckets();
            }
            catch (System.Exception e)
            {
                Log("[beacon error] " + e.GetType().Name + ": " + e.Message);
                frameAccumMs = 0f;
                frameCount = 0;
                bestFrameMs = float.MaxValue;
                worstFrameMs = 0f;
                ResetFrameBuckets();
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

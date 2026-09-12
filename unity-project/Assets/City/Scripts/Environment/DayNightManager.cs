using UnityEngine;
using Huntix.Bridge;

namespace City.Environment
{
    /// <summary>
    /// Ciclo giorno/notte stile Brookhaven: cerca il Sole (luce direzionale)
    /// nella scena e, se manca, ne crea uno. Lo fa girare su una giornata che
    /// dura DayLengthSeconds secondi reali e regola cielo e ambientazione
    /// (alba/tramonto morbidi, notte fredda con nebbia). Servizio persistente
    /// creato da Game.Ensure e DontDestroyOnLoad.
    /// </summary>
    public class DayNightManager : MonoBehaviour
    {
        public static DayNightManager Instance { get; private set; }

        /// <summary>Durata in secondi reali di 24 ore in-game.</summary>
        public static float DayLengthSeconds = 600f;

        private float clockHours;

        /// <summary>Ora del giorno (0-24), per le routine quotidiane
        /// degli NPC.</summary>
        public float ClockHours { get { return clockHours; } }
        private Light sun;
        private Material skyMat;

        private readonly Color dayAmbient = new Color(0.72f, 0.78f, 0.9f);
        private readonly Color nightAmbient = new Color(0.1f, 0.13f, 0.22f);
        private readonly Color daySky = Color.white;
        private readonly Color nightSky = new Color(0.03f, 0.04f, 0.09f);
        private readonly Color nightFog = new Color(0.06f, 0.07f, 0.12f);

        public static DayNightManager Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("DayNightManager");
            DontDestroyOnLoad(go);
            return go.AddComponent<DayNightManager>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            clockHours = 10f; // giro partendo di mattina
        }

        private void Start()
        {
            var all = FindObjectsOfType<Light>();
            foreach (var l in all)
            {
                if (l.type == LightType.Directional)
                {
                    sun = l;
                    break;
                }
            }
            if (sun == null)
            {
                var so = new GameObject("SunDirectional");
                sun = so.AddComponent<Light>();
                sun.type = LightType.Directional;
                sun.shadows = LightShadows.Soft;
                sun.shadowStrength = 0.55f;
                sun.color = new Color(1f, 0.95f, 0.85f);
            }
            sun.transform.SetParent(transform, false);
            RenderSettings.sun = sun;

            skyMat = RenderSettings.skybox;
            if (skyMat == null)
            {
                var dayTex = Resources.Load<Texture2D>("Skyboxes/skybox-day");
                var shader = Shader.Find("Skybox/Panoramic");
                if (shader == null) shader = Shader.Find("Skybox/Cubemap");
                if (dayTex != null && shader != null)
                {
                    skyMat = new Material(shader);
                    skyMat.SetTexture("_MainTex", dayTex);
                    RenderSettings.skybox = skyMat;
                    UnityBridge.LogToAndroid("DayNight", "Kenney skybox applied (chunked)");
                }
            }
        }

        private int _lastHour = -1;

        private void Update()
        {
            clockHours = (clockHours + Time.deltaTime * (24f / DayLengthSeconds)) % 24f;

            // pubblica cambio ora (pattern EventBus)
            int h = (int)clockHours;
            if (h != _lastHour)
            {
                _lastHour = h;
                EventBus.Publish(new HourChangedEvent(h));
                if (h == 0)
                    EventBus.Publish(new DayChangedEvent(0)); // day number gestito da SimulationManager
            }

            // quota solare: 1 a mezzogiorno, ~0 alba/tramonto, negativa di notte
            float alt = Mathf.Sin((clockHours - 6f) / 12f * Mathf.PI);
            float dayFactor = Mathf.Clamp01(alt);

            if (sun != null)
            {
                float elev = alt * 55f;
                sun.transform.rotation = Quaternion.Euler(90f - elev, 210f, 0f);
                sun.intensity = Mathf.Lerp(0.02f, 1.15f, dayFactor);
                sun.color = Color.Lerp(
                    new Color(0.55f, 0.65f, 1f),
                    new Color(1f, 0.96f, 0.88f),
                    dayFactor);
            }

            // livello giorno/notte con alba e tramonto morbidi (5.5..19.5)
            float dayNight;
            if (clockHours >= 5.5f && clockHours <= 19.5f)
            {
                float fade = Mathf.SmoothStep(5.5f, 8f, clockHours) *
                    (1f - Mathf.SmoothStep(17.5f, 19.5f, clockHours));
                dayNight = Mathf.Lerp(0.15f, 1f, fade);
            }
            else
            {
                dayNight = 0.1f;
            }

            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Flat;
            RenderSettings.ambientLight = Color.Lerp(nightAmbient, dayAmbient, dayNight);
            RenderSettings.ambientIntensity = Mathf.Lerp(0.3f, 0.9f, dayNight);

            if (skyMat != null)
            {
                Color skyCol = Color.Lerp(nightSky, daySky, dayNight);
                if (skyMat.HasProperty("_Tint"))
                    skyMat.SetColor("_Tint", skyCol);
                else
                    skyMat.color = skyCol;
            }

            // nebbia leggera sempre: di giorno fonde l'orizzonte col cielo ed
            // evita il bordo netto tra rilievi/bacini colorati e lo sfondo.
            bool isNight = clockHours < 5.5f || clockHours > 19.5f;
            RenderSettings.fog = true;
            RenderSettings.fogColor = isNight ? nightFog : new Color(0.78f, 0.84f, 0.92f);
            RenderSettings.fogMode = FogMode.Exponential;
            RenderSettings.fogDensity = isNight ? 0.0012f : 0.0009f;
        }
    }
}

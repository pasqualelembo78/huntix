using UnityEngine;
using City.OSM;
using Huntix.Bridge;

namespace City.Diagnostics
{
    /// <summary>Profili grafici selezionabili a caldo dal menu (Low / Medium /
    /// High). Non sovrascrive il PerformanceGovernor: il profilo sceglie il
    /// tetto qualitativo dei QualitySettings di progetto, il governor adatta
    /// poi i raggi di cull/animazione in base al frame time reale. Nessun
    /// cambio di dipendenze o di livelli Unity: usa i 6 livelli gia' presenti
    /// (Very Low=0, Low=1, Medium=2, High=3, Very High=4, Ultra=5).</summary>
    public class QualityProfileSelector : MonoBehaviour
    {
        public enum Profile { Low = 0, Medium = 1, High = 2 }

        public static QualityProfileSelector Instance { get; private set; }

        private const string PrefsKey = "huntix_city_quality_profile";
        private static readonly int[] UnityLevel = { 1, 2, 3 };

        public Profile Current { get; private set; }
        private bool applyStoredOnStart;

        public static QualityProfileSelector Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("QualityProfileSelector");
            DontDestroyOnLoad(go);
            return go.AddComponent<QualityProfileSelector>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            // Nessuna scelta salvata = NON toccare la qualita' configurata nel
            // progetto (oggi Ultra). Evita regressioni visive silenziose su
            // dispositivi gia' a posto: il profilo viene applicato solo dopo
            // un cambio esplicito dell'utente dal menu.
            int stored = PlayerPrefs.GetInt(PrefsKey, -1);
            if (stored >= 0 && stored < UnityLevel.Length)
            {
                Current = (Profile)stored;
                applyStoredOnStart = true;
            }
            else
            {
                int level = QualitySettings.GetQualityLevel();
                Current = level <= 1 ? Profile.Low
                        : level == 2 ? Profile.Medium : Profile.High;
            }
        }

        private void Start()
        {
            if (applyStoredOnStart) Apply(Current, false);
        }

        public void Cycle()
        {
            int next = ((int)Current + 1) % UnityLevel.Length;
            Apply((Profile)next, true);
        }

        public void Apply(Profile profile, bool notify)
        {
            Current = (Profile)Mathf.Clamp((int)profile, 0, UnityLevel.Length - 1);
            PlayerPrefs.SetInt(PrefsKey, (int)Current);
            PlayerPrefs.Save();
            QualitySettings.SetQualityLevel(UnityLevel[(int)Current], true);

            string name = Current.ToString();
            OsmDiag.Log("[Quality] profilo=" + name +
                " unityLevel=" + QualitySettings.GetQualityLevel());

            if (notify)
            {
                long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                string json = "{\"profile\":\"" + name.ToLowerInvariant() +
                    "\",\"unityLevel\":" + QualitySettings.GetQualityLevel() +
                    ",\"tsMt\":" + ts + "}";
                try { SendMessageToAndroid("CityQualityChanged", json); }
                catch (System.Exception e)
                {
                    OsmDiag.Log("[Quality] CityQualityChanged failed: " + e.Message);
                }
                // Riflesso della caccia alle uova: il primo cambio di qualita'
                // sblocca un indizio uovo, salvato nel profilo con posizione.
                if (Game.Instance != null)
                    Game.Instance.OnQualityProfileApplied((int)Current, name);
            }
        }

        private static void SendMessageToAndroid(string eventName, string json)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            UnityBridge.SendMessageToAndroid(eventName, json);
#endif
        }
    }
}

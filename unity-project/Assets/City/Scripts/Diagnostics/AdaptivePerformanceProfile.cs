using UnityEngine;

namespace City.Diagnostics
{
    /// <summary>
    /// Profilo di prestazioni ADATTIVO per tutta l'app (non solo MiCittà).
    ///
    /// Non sostituisce PerformanceGovernor (che già adatta i raggi di cull
    /// di veicoli/NPC in base al frame time) né QualityProfileSelector (che
    /// applica il livello qualitativo scelto dall'utente). Legge entrambi e ne
    /// deriva un solo tier condiviso, poi espone parametri conservativi usati
    /// dai sistemi esistenti:
    ///   - budget di costruzione chunk (mai sopra i 12 ms storici);
    ///   - numero di file di prefetch tenuti in RAM (8 -> 4 su RAM bassa);
    ///   - densità veicoli runtime (1.0 -> 0.8, i codici venduti non cambiano);
    ///   - ombre dei building lontani (LOD >= 1 solo su RAM bassa).
    ///
    /// Regole del Mandato rispettate: LoadRadius, step terreno, collider LOD0,
    /// kit edifici e contatori di traffico predefiniti NON vengono toccati.
    /// Ogni cambio di tier passa da bridge bidirezionale e lascia un uovo con
    /// posizione testuale/coordinate (Riflesso della caccia alle uova).
    /// </summary>
    public class AdaptivePerformanceProfile : MonoBehaviour
    {
        public enum Tier { Low = 0, Medium = 1, High = 2 }

        public static AdaptivePerformanceProfile Instance { get; private set; }

        private const string AutoPrefKey = "huntix_adaptive_performance";
        private const string AndroidQualityPref = "city_quality";
        private const string UnityQualityPref = "huntix_city_quality_profile";

        private const int LowMemoryMb = 3072;
        private const int MediumMemoryMb = 6144;

        public Tier Current { get; private set; } = Tier.High;
        public bool AutoEnabled { get; private set; } = true;
        public int MemoryMb { get; private set; }

        /// <summary>Budget build chunk: 8/10/12 ms. Il massimo resta 12, il
        /// valore storico di ChunkManager: nessun peggioramento del caricamento
        /// sui device che reggono il profilo High.</summary>
        public int ChunkBuildBudgetMs => Current == Tier.Low ? 8
                                       : Current == Tier.Medium ? 10 : 12;

        /// <summary>Prefetch in RAM: 8 file come oggi su High, ridotto solo
        /// quando la memoria e' davvero scarsa.</summary>
        public int PrefetchKeep => Current == Tier.Low ? 4
                                 : Current == Tier.Medium ? 6 : 8;

        /// <summary>Moltiplicatore del numero di auto attive: deterministico-
        /// safe, cambia solo quante auto vengono attivate a runtime.</summary>
        public float VehicleDensity => Current == Tier.Low ? 0.8f
                                     : Current == Tier.Medium ? 0.9f : 1f;

        /// <summary>Su RAM bassa le ombre dei palazzi lontani (LOD >= 1) non
        /// servono al gameplay e costano draws: spente solo a distanza.</summary>
        public bool DistantBuildingShadows => Current != Tier.Low;

        private bool _notified;
        private bool _restoring;
        private float _nextCheck;

        public static AdaptivePerformanceProfile Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("AdaptivePerformanceProfile");
            DontDestroyOnLoad(go);
            return go.AddComponent<AdaptivePerformanceProfile>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            AutoEnabled = PlayerPrefs.GetInt(AutoPrefKey, 1) != 0;
            MemoryMb = SystemInfo.systemMemorySize;
            Evaluate(true);
        }

        private void Start()
        {
            // Si aggancia al governatore gia' esistente senza duplicarlo.
            PerformanceGovernor.Ensure();
        }

        private void Update()
        {
            if (Time.unscaledTime < _nextCheck) return;
            _nextCheck = Time.unscaledTime + 2f;
            Evaluate(false);
        }

        /// <summary>Il toggle dell'utente arriva da Android via bridge; uno
        /// spawn di uovo dedicato viene concesso solo al primo miglioramento.</summary>
        public void ApplyUserSetting(bool enabled)
        {
            AutoEnabled = enabled;
            PlayerPrefs.SetInt(AutoPrefKey, enabled ? 1 : 0);
            PlayerPrefs.Save();
            Evaluate(true);
            // Sempre: anche se il tier non cambia, Android deve aggiornare il
            // proprio specchio del toggle (unico proprietario della preferenza).
            NotifyAndroid();
        }

        /// <summary>Ripristino passivo da Android (BridgeActivity.setMode):
        /// non notifica, non spawna uova, non riscrive preferenze altrui.</summary>
        public void ApplyRestored(bool enabled)
        {
            _restoring = true;
            try
            {
                AutoEnabled = enabled;
                Evaluate(true);
                _notified = true;
            }
            finally { _restoring = false; }
        }

        private void Evaluate(bool force)
        {
            Tier next;
            int mem = MemoryMb > 0 ? MemoryMb : 4096;
            if (!AutoEnabled)
            {
                next = Tier.High;
            }
            else
            {
                string saved = PlayerPrefs.GetString(AndroidQualityPref, "");
                int unityProfile = PlayerPrefs.GetInt(UnityQualityPref, -1);
                bool userLow = saved == "low" || unityProfile == 0;

                var governor = PerformanceGovernor.Instance;
                int governorTier = governor != null ? governor.Tier : 0;

                if (userLow || mem < LowMemoryMb || governorTier >= 3)
                    next = Tier.Low;
                else if (mem < MediumMemoryMb || governorTier >= 2)
                    next = Tier.Medium;
                else
                    next = Tier.High;
            }

            if (!force && next == Current) return;
            var old = Current;
            Current = next;

            var tel = SessionTelemetry.Instance;
            if (tel != null)
                tel.Mark("[AdaptivePerf] tier={0} (was {1}) mem={2}MB auto={3} " +
                         "chunkBudget={4}ms prefetch={5} cars={6:F2}",
                    Current, old, mem, AutoEnabled, ChunkBuildBudgetMs,
                    PrefetchKeep, VehicleDensity);

            if (_restoring) return;
            if (force && !_notified)
            {
                _notified = true;
                NotifyAndroid();
            }
            else if (next > old)
            {
                NotifyAndroid();
                if (Game.Instance != null && next == Tier.High)
                    Game.Instance.OnAdaptivePerformanceImproved();
            }
        }

        private static void NotifyAndroid()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                string json = "{\"tier\":\"" + Instance.Current.ToString().ToLowerInvariant() +
                    "\",\"memMb\":" + Instance.MemoryMb +
                    ",\"auto\":" + (Instance.AutoEnabled ? 1 : 0) +
                    ",\"tsMt\":" + System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("CityPerfTierChanged", json);
            }
            catch (System.Exception e)
            {
                City.OSM.OsmDiag.Log("[AdaptivePerf] CityPerfTierChanged failed: " + e.Message);
            }
#endif
        }
    }
}

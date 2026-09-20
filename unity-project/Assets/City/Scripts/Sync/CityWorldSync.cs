using System;
using System.Collections.Generic;
using UnityEngine;
using City.World;
using City.Environment;
using City.Economy;
using City.NPC;
using City.Player;
using Huntix.Bridge;

namespace City.Sync
{
    /// <summary>
    /// CityWorldSync — ponte bidirezionale FORTE tra MiCittà (Unity) e il
    /// profilo generale Huntix (Android), pilotato dallo snapshot completo
    /// dello stato città.
    ///
    /// Spin-up:
    ///  1. All'avvio della scena City (Game.Start → Ensure) chiede ad Android
    ///     l'ultimo snapshot salvato. Se il salvataggio LOCALE è vergine
    ///     (install fresca / Cache Unity pulita) lo RISTABILISCE: soldi,
    ///     energia, sonno, biografia, uova bestiario, XP lavori, persone
    ///     conosciute, famiglia, casa, skin, pet, contatore pulizia/sospetto.
    ///  2. In sessione esporta ad Android lo snapshot ogni volta che cambia
    ///     (eventi Wallet/Energia/Famiglia/Relazioni + poll periodico su tutto
    ///     il resto: sonno, età, bisogni, missioni) e sempre alla uscita
    ///     (UIManager.ConfirmExit → FlushNow prima di ExitCityToHome).
    ///
    /// Unity resta la fonte di verità della sessione; Android il mirror
    /// persistente e la base per il profilo generale (XP/MVC/skin vivono già
    /// lato Android). Il merge è idempotente e non distruttivo.
    /// </summary>
    public class CityWorldSync : MonoBehaviour
    {
        public static CityWorldSync Instance;

        private const int SnapshotVer = 1;
        private static readonly float PollInterval = 4f;   // esporta se cambia
        private static readonly float RestoreDelay = 1.2f; // attesa primo frame

        private float _timer;
        private string _lastExportedJson = "";
        private bool _restoreDone;
        private bool _restoring;

        public static CityWorldSync Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("CityWorldSync");
            DontDestroyOnLoad(go);
            return go.AddComponent<CityWorldSync>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            try { Wallet.OnChanged += OnAnyState; } catch (System.Exception) { }
            try { EnergySystem.OnChanged += OnAnyState; } catch (System.Exception) { }
            try { FamilyManager.OnFamilyChanged += OnAnyState; } catch (System.Exception) { }
            try { RelationshipManager.OnChanged += OnAnyState; } catch (System.Exception) { }
        }

        private void OnDestroy()
        {
            if (Instance != this) return;
            Instance = null;
            try { Wallet.OnChanged -= OnAnyState; } catch (System.Exception) { }
            try { EnergySystem.OnChanged -= OnAnyState; } catch (System.Exception) { }
            try { FamilyManager.OnFamilyChanged -= OnAnyState; } catch (System.Exception) { }
            try { RelationshipManager.OnChanged -= OnAnyState; } catch (System.Exception) { }
        }

        private void Start()
        {
            _timer = PollInterval; // primo export subito dopo il restore
            Invoke(nameof(RequestRestore), RestoreDelay);
        }

        private void Update()
        {
            _timer -= Time.unscaledDeltaTime;
            if (_timer > 0f) return;
            _timer = PollInterval;
            TryPush();
        }

        private void OnAnyState(int _)
        {
            TryPush();
        }

        private void OnAnyState(string _)
        {
            TryPush();
        }

        private void OnAnyState()
        {
            TryPush();
        }

        // ── export ──────────────────────────────────────────────

        /// <summary>Snapshot corrente dello stato città in formato {"ver":..,...}.</summary>
        public static string BuildSnapshot()
        {
            var j = new Snapshot();
            long ticksUtc = DateTime.UtcNow.Ticks;
            j.ts = (long)(ticksUtc / TimeSpan.TicksPerMillisecond);

            j.money = Wallet.Money;
            j.bank = PlayerPrefs.GetInt("city_bank", 0);
            j.energy = EnergySystem.Value;
            j.sleep = SleepSystem.Value;
            j.age = FamilyManager.AgeYears;
            j.isDead = FamilyManager.IsDead;

            j.hunger = FamilyManager.hunger;
            j.thirst = FamilyManager.thirst;
            j.fatigue = FamilyManager.fatigue;
            j.daysHungry = FamilyManager.daysGoingHungry;
            j.daysThirsty = FamilyManager.daysGoingThirsty;
            j.daysTired = FamilyManager.daysGoingTired;

            var dex = EggDex.Found;
            if (dex != null && dex.Count > 0)
                j.eggDex = string.Join(";", dex);

            var jobs = new List<string>();
            foreach (JobManager.JobType t in System.Enum.GetValues(typeof(JobManager.JobType)))
            {
                int xp = JobManager.Xp(t);
                if (xp > 0) jobs.Add(t + "=" + xp);
            }
            j.jobs = string.Join(";", jobs);

            j.peopleKnown = RelationshipManager.KnownCount;
            j.relationships = RelationshipManager.ExportSnapshot();
            j.family = FamilyManager.ExportStoreJson();

            j.cleanCount = PlayerPrefs.GetInt("city_clean_count", 0);
            j.suspicion = ChaosTracker.Suspicion;
            j.home = BuildHome();
            j.skin = PlayerAppearance.SavedSkin;
            j.pet = PetController.SavedPet;

            if (MissionManager.Instance != null)
            {
                j.missionsActive = MissionManager.Instance.ActiveCount;
                j.missionsCompleted = MissionManager.Instance.CompletedCount;
            }

            return JsonUtility.ToJson(j);
        }

        [Serializable]
        private class Snapshot
        {
            public int ver = SnapshotVer;
            public long ts;
            public int money;
            public int bank;
            public int energy;
            public int sleep;
            public int age;
            public bool isDead;
            public int hunger;
            public int thirst;
            public int fatigue;
            public int daysHungry;
            public int daysThirsty;
            public int daysTired;
            public string eggDex = "";
            public string jobs = "";
            public int peopleKnown;
            public string relationships = "";
            public string family = "";
            public int cleanCount;
            public int suspicion;
            public string home = "";
            public string skin = "";
            public string pet = "";
            public int missionsActive;
            public int missionsCompleted;
        }

        private static string BuildHome()
        {
            if (!HomeSystem.OwnsHome) return "";
            var parts = new List<string>();
            parts.Add(HomeSystem.HomeName);
            parts.Add(PlayerPrefs.GetFloat(HomeSystem.KeyLat, 0f).ToString("F6",
                System.Globalization.CultureInfo.InvariantCulture));
            parts.Add(PlayerPrefs.GetFloat(HomeSystem.KeyLng, 0f).ToString("F6",
                System.Globalization.CultureInfo.InvariantCulture));
            parts.Add(PlayerPrefs.GetString("city_home_garage", ""));
            parts.Add(PlayerPrefs.GetString("city_home_garage_model", ""));
            // Stanze/mobili della casa (forward-compat, oggi vuote): quando un
            // futuro incremento arrederà l'interno, qui arriverà il riepilogo
            // (es. "cucina:cassetto=televisore;salotto:divano"). Unity fornisce
            // lo slot, Android lo conserva nel profilo e lo ritorna per sync.
            parts.Add(PlayerPrefs.GetString("city_home_rooms", ""));
            return string.Join("|", parts);
        }

        /// <summary>Invia lo snapshot ad Android solo se è cambiato dall'ultimo.</summary>
        public void TryPush()
        {
            if (!enabled) return;
            if (!_restoreDone) return;   // non esportare mentre si sta ripristinando
            if (_restoring) return;      // mai uno snapshot semi-applicato
            try
            {
                string json = BuildSnapshot();
                if (json == _lastExportedJson) return;
                _lastExportedJson = json;
                UnityBridge.PushCityStateSnapshot(json);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[CityWorldSync] TryPush: " + e.Message);
            }
        }

        /// <summary>Esportazione immediata e forzata (usata prima dell'uscita).</summary>
        public static void FlushNow()
        {
            if (Instance == null) return;
            try
            {
                string json = BuildSnapshot();
                Instance._lastExportedJson = json;
                UnityBridge.PushCityStateSnapshot(json);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[CityWorldSync] FlushNow: " + e.Message);
            }
        }

        // ── restore (Android → Unity, solo se il salvataggio è vergine) ──

        private static bool IsStateFresh()
        {
            return Wallet.Money <= Wallet.StartMoney &&
                   RelationshipManager.KnownCount == 0 &&
                   EggDex.TotalFound == 0 &&
                   FamilyManager.IsFreshFamily();
        }

        private void RequestRestore()
        {
            if (_restoreDone) return;
            _restoreDone = true;
            try
            {
                string snap = UnityBridge.GetCityStateSnapshot();
                if (string.IsNullOrEmpty(snap) || snap == "{}")
                {
                    // primo avvio assoluto: nessuno snapshot lato Android,
                    // allineamento vuoto (il check nei log dell'app lo conferma).
                    UnityBridge.RequestCityStateCheck();
                    return;
                }
                if (IsStateFresh())
                {
                    _restoring = true;
                    bool ok = ApplySnapshot(snap);
                    _restoring = false;
                    Debug.Log("[CityWorldSync] Restore da Android: " +
                        (ok ? "applicato" : "niente da applicare"));
                }
                else
                {
                    Debug.Log("[CityWorldSync] Salvataggio locale presente: " +
                        "Unity resta la fonte di verità (ri-export).");
                }
                UnityBridge.RequestCityStateCheck();
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[CityWorldSync] RequestRestore: " + e.Message);
            }
        }

        /// <summary>Applica uno snapshot (merge non distruttivo) sui sistemi.</summary>
        public static bool ApplySnapshot(string json)
        {
            if (string.IsNullOrEmpty(json)) return false;
            Snapshot j;
            try { j = JsonUtility.FromJson<Snapshot>(json); }
            catch (System.Exception) { return false; }
            if (j == null) return false;

            // soldi: accredita la differenza (rispetta il contratto server).
            if (j.money > 0) Wallet.Earn(Mathf.Max(0, j.money - Wallet.Money));
            if (j.energy > 0 && j.energy <= EnergySystem.MaxValue)
                EnergySystem.Set(j.energy);
            if (j.sleep > 0 && j.sleep <= SleepSystem.MaxSleep)
                SleepSystem.Set(j.sleep);

            // biografia/bisogni solo se più avanzati dell'ora.
            if (j.age > FamilyManager.AgeYears) FamilyManager.AgeYears = j.age;
            if (j.hunger > 0) FamilyManager.hunger = j.hunger;
            if (j.thirst > 0) FamilyManager.thirst = j.thirst;
            if (j.fatigue > 0) FamilyManager.fatigue = j.fatigue;
            if (j.daysHungry > 0) FamilyManager.daysGoingHungry = j.daysHungry;
            if (j.daysThirsty > 0) FamilyManager.daysGoingThirsty = j.daysThirsty;
            if (j.daysTired > 0) FamilyManager.daysGoingTired = j.daysTired;
            if (j.isDead) FamilyManager.IsDead = true;

            // bestiario: unione (mai perdere voci).
            if (!string.IsNullOrEmpty(j.eggDex))
            {
                foreach (var entry in j.eggDex.Split(';'))
                {
                    if (string.IsNullOrEmpty(entry)) continue;
                    int dot = entry.IndexOf('.');
                    if (dot <= 0 || dot >= entry.Length - 1) continue;
                    EggDex.Record(entry.Substring(0, dot), entry.Substring(dot + 1));
                }
            }

            // XP lavori: solo se il locale è a zero (l'XP locale vince).
            if (!string.IsNullOrEmpty(j.jobs) && NoJobXpLocal())
            {
                foreach (var entry in j.jobs.Split(';'))
                {
                    if (string.IsNullOrEmpty(entry)) continue;
                    int eq = entry.IndexOf('=');
                    if (eq <= 0 || eq >= entry.Length - 1) continue;
                    string name = entry.Substring(0, eq);
                    int xp;
                    if (!int.TryParse(entry.Substring(eq + 1), out xp) || xp <= 0) continue;
                    foreach (JobManager.JobType t in System.Enum.GetValues(typeof(JobManager.JobType)))
                    {
                        if (t.ToString() == name)
                            PlayerPrefs.SetInt("city_job_xp_" + t, xp);
                    }
                }
                PlayerPrefs.Save();
            }

            // relazioni: merge col max.
            RelationshipManager.ImportSnapshot(j.relationships);

            // famiglia: solo se la famiglia locale è pure vergine.
            if (!string.IsNullOrEmpty(j.family))
                FamilyManager.RestoreFamilyStore(j.family);

            // casa: ricostruisce le chiavi se non se ne possiede una.
            if (string.IsNullOrEmpty(HomeSystem.HomeName) &&
                !string.IsNullOrEmpty(j.home))
            {
                var parts = j.home.Split('|');
                if (parts.Length >= 3)
                {
                    PlayerPrefs.SetString("city_home_name", parts[0]);
                    float lat, lng;
                    if (float.TryParse(parts[1],
                            System.Globalization.NumberStyles.Float,
                            System.Globalization.CultureInfo.InvariantCulture, out lat) &&
                        float.TryParse(parts[2],
                            System.Globalization.NumberStyles.Float,
                            System.Globalization.CultureInfo.InvariantCulture, out lng))
                    {
                        PlayerPrefs.SetFloat(HomeSystem.KeyLat, lat);
                        PlayerPrefs.SetFloat(HomeSystem.KeyLng, lng);
                    }
                    if (parts.Length >= 5)
                    {
                        PlayerPrefs.SetString("city_home_garage", parts[3]);
                        PlayerPrefs.SetString("city_home_garage_model", parts[4]);
                    }
                    // Stanze/mobili: forward-compat (potrebbe essere vuoto oggi).
                    if (parts.Length >= 6)
                        PlayerPrefs.SetString("city_home_rooms", parts[5]);
                    PlayerPrefs.Save();
                }
            }

            // skin/pet: solo se il locale è il default.
            const string skinDefault = "humanMaleA";
            if (PlayerAppearance.SavedSkin == skinDefault ||
                string.IsNullOrEmpty(PlayerAppearance.SavedSkin))
            {
                if (!string.IsNullOrEmpty(j.skin) && j.skin != skinDefault)
                    PlayerPrefs.SetString(PlayerAppearance.PrefKey, j.skin);
            }
            if (string.IsNullOrEmpty(PetController.SavedPet) || PetController.SavedPet == "none")
            {
                if (!string.IsNullOrEmpty(j.pet) && j.pet != "none")
                    PlayerPrefs.SetString(PetController.PrefKey, j.pet);
            }
            PlayerPrefs.Save();

            // contatori città-partecipativa.
            if (j.cleanCount > PlayerPrefs.GetInt("city_clean_count", 0))
                PlayerPrefs.SetInt("city_clean_count", j.cleanCount);
            if (j.suspicion > 0 && j.suspicion > ChaosTracker.Suspicion)
                PlayerPrefs.SetInt("city_suspicion", j.suspicion);
            PlayerPrefs.Save();

            // importa anche l'energia del profilo Huntix (sync psoplevemente).
            try { UnityBridge.SyncEnergyFromCity(EnergySystem.Value); } catch (System.Exception) { }
            return true;
        }

        private static bool NoJobXpLocal()
        {
            foreach (JobManager.JobType t in System.Enum.GetValues(typeof(JobManager.JobType)))
                if (JobManager.Xp(t) > 0) return false;
            return true;
        }
    }
}
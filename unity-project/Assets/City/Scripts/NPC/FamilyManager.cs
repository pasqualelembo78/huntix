using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.UI;
using TMPro;
using City.Economy;
using City.World;
using City.NPC;
using City.Afterlife;

namespace City.NPC
{
    /// <summary>
    /// Struttura famiglia: fidanzamento, matrimonio, divorzio (consensuale
    /// o contestato) e figli. Lo stato e persistito su JSON in
    /// persistentDataPath, come RelationshipManager. Il riferimento a un NPC
    /// e il characterId del cittadino o del cittadino-partner.
    ///
    /// Ciclo di vita (meccanica sim):
    ///   - Fidanzamento  : serve almeno 40 punti amicizia con il personaggio.
    ///   - Matrimonio    : serve essere fidanzati e almeno 60 punti. Bonus:
    ///                     +25% soldi dalle missioni del coniuge, +XP, +15
    ///                     energia massima.
    ///   - Divorzio      : solo se sposati. Consensuale (lieve) oppure
    ///                     contestato (pesante: -35 punti, blocchi, cooldown).
    ///   - Figli         : solo se sposati da almeno 3 giorni. Il figlio e un
    ///                     NPC visibile in scena vicino al coniuge, cresce nel
    ///                     tempo. Max 3.
    /// </summary>
    public static class FamilyManager
    {
        // ---- soglie sim ----
        public const int EngageNeedPts = 40;   // punti per fidanzarsi
        public const int MarryNeedPts = 60;    // punti per sposarsi
        public const int DaysToKidMin = 3;     // giorni di matrimonio per figlio
        public const int MaxChildren = 3;
        public const int ConsensualPenalty = 10;
        public const int ContestedPenalty = 35;
        public const int RemarryBlockDays = 7;     // consensuale
        public const int ContestedBlockDays = 14;  // contestato
        public const int SpouseMissionBlockDays = 10;
        public const float SpouseBonusMult = 1.25f; // +25% dalle missioni
        public const int MarryXpBonus = 15;
        public const int SpouseXpPerMission = 2;
        public const int EnergyBonus = 15;          // energia max extra
        public const int MaxAge = 100;                 // eta massima
        public const int AdoptCost = 500;            // costo adozione di un orfano
        public const int FosterXpBonus = 20;         // XP per essere adottati
        public const int MaxOrphansOnScene = 2;      // orfani spawnabili in scena

        public const float NeedsDecreasePerDay = 5f; // fame/sete/stanchezza persi al giorno
        public const int NeedsCriticalDays = 3;        // giorni a 0 prima della morte
        public const int NeedsMax = 100;               // massimo dei bisogni
        public const float TickSeconds = 6f;       // periodo del tick di gioco
        public const int DaysPerYear = 5;            // 1 anno = 5 giorni reali di vita

        public static int hunger = NeedsMax;          // 0-100
        public static int thirst = NeedsMax;          // 0-100
        public static int fatigue = NeedsMax;         // 0-100
        public static int daysGoingHungry;
        public static int daysGoingThirsty;
        public static int daysGoingTired;
        [System.NonSerialized] public static DeathType lastDeathType;
        [System.NonSerialized] public static System.DateTime deathTime;

        public enum DeathType
        {
            STARVATION,
            DEHYDRATION,
            EXHAUSTION,
            OLD_AGE,
            INCIDENTE,
            CADUTA,
            MALATTIA
        }


        [Serializable]
        private class Store
        {
            public PartnerInfo partner;
            public FianceInfo fiance;
            public List<ChildInfo> children = new List<ChildInfo>();
            public List<BreakInfo> breaks = new List<BreakInfo>();
            public FosterInfo foster;
            public string playerBornDay;
        }

        [Serializable]
        public class PartnerInfo
        {
            public string charId;
            public string name;
            public string weddingDay;
        }

        [Serializable]
        public class FianceInfo
        {
            public string charId;
            public string name;
            public string engagedDay;
        }

        [Serializable]
        public class ChildInfo
        {
            public string charId;
            public string name;
            public string bornDay;
            public int ageYears;
            public int birthWeek; // per crescita nel tempo
            public bool adopted;      // via adozione di un orfano
            public string adoptedDay; // giorno adozione (se adottato)
        }

        [Serializable]
        public class FosterInfo
        {
            public string charId1;
            public string name1;
            public string charId2;
            public string name2;
            public string day; // giorno in cui ti hanno adottato
        }

        [Serializable]
        public class BreakInfo
        {
            public string charId;
            public string day;        // giorno del divorzio
            public string blockedUntil; // giorno per rimatrimonio blocchi
            public string missionBlockUntil; // coniuge chiude missioni
            public bool contested;
            public int ptsLost;
        }

        private static Store _store;
        private static readonly List<string> _childNames = new List<string>
        {
            "Leo", "Luna", "Sofia", "Emma", "Noah", "Liam", "Alice", "Ginevra",
            "Marco", "Giulia", "Diego", "Francesca", "Edoardo", "Bianca",
            "Tommaso", "Vittoria", "Matteo", "Aurora", "Riccardo", "Camilla"
        };

        public static event Action OnFamilyChanged;

        private static string FilePath =>
            Path.Combine(Application.persistentDataPath, "huntix_family.json");

        // ---- accesso stato ----
        public static PartnerInfo Partner
        {
            get { EnsureLoaded(); return _store.partner; }
        }

        public static FianceInfo Fiance
        {
            get { EnsureLoaded(); return _store.fiance; }
        }

        public static List<ChildInfo> Children
        {
            get { EnsureLoaded(); return _store.children; }
        }

        public static bool IsEngaged { get { return Fiance != null; } }
        public static bool IsMarried { get { return Partner != null; } }

        public static int ChildrenCount
        {
            get { EnsureLoaded(); return _store.children == null ? 0 : _store.children.Count; }
        }

        /// <summary>Se il characterId e il coniuge attuale.</summary>
        public static bool IsSpouse(string charId)
        {
            if (string.IsNullOrEmpty(charId)) return false;
            var p = Partner;
            return p != null && p.charId == charId;
        }

        /// <summary>Giorni (solari) trascorsi dal matrimonio.</summary>
        public static int DaysMarried()
        {
            var p = Partner;
            if (p == null || string.IsNullOrEmpty(p.weddingDay)) return 0;
            DateTime w;
            if (!DateTime.TryParse(p.weddingDay, out w)) return 0;
            return (DateTime.UtcNow.Date - w.Date).Days;
        }

        // ---- fidanzamento ----
        /// <summary>Puoi fidanzarti con questo personaggio?</summary>
        public static bool CanEngage(string charId)
        {
            if (string.IsNullOrEmpty(charId)) return false;
            if (IsMarried || IsEngaged) return false;
            if (RelationshipManager.Points(charId) < EngageNeedPts) return false;
            if (IsBreakBlocked(charId, RemarryBlockDays)) return false;
            return true;
        }

        public static void Engage(string charId, string name)
        {
            if (!CanEngage(charId)) return;
            EnsureLoaded();
            _store.fiance = new FianceInfo { charId = charId, name = name,
                engagedDay = Today() };
            Save(); Notify();
        }

        public static void BreakEngagement()
        {
            EnsureLoaded();
            if (_store.fiance == null) return;
            _store.fiance = null;
            Save(); Notify();
        }

        // ---- matrimonio ----
        public static bool CanMarry(string charId)
        {
            var f = Fiance;
            if (f == null || f.charId != charId) return false;
            if (RelationshipManager.Points(charId) < MarryNeedPts) return false;
            return true;
        }

        public static void Marry(string charId, string name)
        {
            if (!CanMarry(charId)) return;
            EnsureLoaded();
            _store.partner = new PartnerInfo { charId = charId, name = name,
                weddingDay = Today() };
            _store.fiance = null;
            // bonus matrimoniali: XP una tantum + contatore coniuge
            PlayerPrefs.SetInt("family_wedding_xp",
                PlayerPrefs.GetInt("family_wedding_xp", 0) + MarryXpBonus);
            PlayerPrefs.Save();
            Save(); Notify();
        }

        // ---- divorzio ----
        public static bool CanDivorce()
        {
            return IsMarried;
        }

        /// <summary>Divorzio. Se consensuale la penale e lieve; se contestato
        /// e pesante: punti bruciati, blocco rimatrimonio lungo e il coniuge
        /// smette di offrire missioni per un po.</summary>
        public static void Divorce(bool consensual)
        {
            var p = Partner;
            if (p == null) return;
            EnsureLoaded();

            string today = Today();
            int penalty = consensual ? ConsensualPenalty : ContestedPenalty;
            // spostare anche l per gli eventi rotta
            RelationshipManager.RemovePoints(p.charId, penalty);

            string blockedUntil = today;
            string missionBlockUntil = today;
            try
            {
                blockedUntil = DateTime.UtcNow.Date
                    .AddDays(consensual ? RemarryBlockDays : ContestedBlockDays)
                    .ToString("yyyy-MM-dd");
            }
            catch (Exception) { }

            if (consensual)
            {
                // consensuale: nessun blocco missioni, breve cooldown
                _store.breaks.Add(new BreakInfo { charId = p.charId, day = today,
                    blockedUntil = blockedUntil, missionBlockUntil = "",
                    contested = false, ptsLost = penalty });
            }
            else
            {
                // contestato: blocco missioni del coniuge + cooldown lungo
                try
                {
                    missionBlockUntil = DateTime.UtcNow.Date
                        .AddDays(SpouseMissionBlockDays).ToString("yyyy-MM-dd");
                }
                catch (Exception) { }
                _store.breaks.Add(new BreakInfo { charId = p.charId, day = today,
                    blockedUntil = blockedUntil, missionBlockUntil = missionBlockUntil,
                    contested = true, ptsLost = penalty });
            }

            _store.partner = null;
            Save(); Notify();
        }

        private static bool IsBreakBlocked(string charId, int fallbackDays)
        {
            if (_store == null || _store.breaks == null) return false;
            if (_store.breaks.Count == 0) return false;
            string today = Today();
            for (int i = 0; i < _store.breaks.Count; i++)
            {
                var b = _store.breaks[i];
                if (b == null || b.charId != charId) continue;
                if (string.IsNullOrEmpty(b.blockedUntil)) continue;
                try
                {
                    DateTime until = DateTime.Parse(b.blockedUntil);
                    if (DateTime.UtcNow.Date <= until) return true;
                }
                catch (Exception) { }
            }
            return false;
        }

        /// <summary>Il coniuge ex chiude le missioni? (solo divorzio contestato)</summary>
        public static bool IsMissionBlocked(string charId)
        {
            if (_store == null || _store.breaks == null) return false;
            if (string.IsNullOrEmpty(charId)) return false;
            string today = Today();
            for (int i = 0; i < _store.breaks.Count; i++)
            {
                var b = _store.breaks[i];
                if (b == null || b.charId != charId) continue;
                if (string.IsNullOrEmpty(b.missionBlockUntil)) continue;
                try
                {
                    DateTime until = DateTime.Parse(b.missionBlockUntil);
                    if (DateTime.UtcNow.Date <= until) return true;
                }
                catch (Exception) { }
            }
            return false;
        }

        // ---- figli ----
        public static bool CanHaveChild()
        {
            if (!IsMarried) return false;
            if (ChildrenCount >= MaxChildren) return false;
            return DaysMarried() >= DaysToKidMin;
        }

        /// <summary>Crea un figlio: aggiunge il record e restituisce i dati
        /// del nuovo figlio (il chiamante lo farà apparire in scena).</summary>
        public static ChildInfo AddChild(string parentDbId)
        {
            EnsureLoaded();
            string charId = "kid_" + Mathf.Abs(parentDbId.GetHashCode()) +
                "_" + _store.children.Count;
            string name = _childNames[
                Mathf.Abs((parentDbId.Length + _store.children.Count).GetHashCode()) %
                _childNames.Count];
            var c = new ChildInfo
            {
                charId = charId,
                name = name,
                bornDay = Today(),
                ageYears = 0,
                birthWeek = (int)(DateTime.UtcNow.Ticks / TimeSpan.TicksPerDay),
                adopted = false
            };
            _store.children.Add(c);
            Save(); Notify();
            return c;
        }

        // ---- adozione (figlio) ----
        /// <summary>Puoi adottare un orfano?</summary>
        public static bool CanAdopt()
        {
            if (ChildrenCount >= MaxChildren) return false;
            if (Wallet.Money < AdoptCost) return false;
            return true;
        }

        /// <summary>Adotta un orfano: accetta un nome e un eta. Spende il
        /// costo di adozione dal portafoglio del player.</summary>
        public static ChildInfo AdoptChild(string name, int age)
        {
            EnsureLoaded();
            if (!CanAdopt()) return null;
            string id = "adopt_" + _store.children.Count + "_" + Today().GetHashCode();
            var c = new ChildInfo
            {
                charId = id,
                name = name,
                bornDay = Today(),
                ageYears = Mathf.Clamp(age, 1, 17),
                birthWeek = (int)(DateTime.UtcNow.Ticks / TimeSpan.TicksPerDay),
                adopted = true,
                adoptedDay = Today()
            };
            Wallet.Spend(AdoptCost);
            _store.children.Add(c);
            Save(); Notify();
            return c;
        }

        // ---- essere adottati (genitori adottivi del player) ----
        public static FosterInfo Foster
        {
            get { EnsureLoaded(); return _store.foster; }
        }

        public static bool IsFostered
        {
            get { return Foster != null; }
        }

        /// <summary>Puoi essere adottato da una coppia NPC?</summary>
        public static bool CanBeFostered()
        {
            if (IsFostered) return false;
            if (IsMarried || IsEngaged) return false;
            return true;
        }

        /// <summary>Il player viene adottato dalla coppia (genitori adottivi).</summary>
        public static void SetFoster(string charId1, string name1,
            string charId2, string name2)
        {
            EnsureLoaded();
            _store.foster = new FosterInfo
            {
                charId1 = charId1, name1 = name1,
                charId2 = charId2, name2 = name2,
                day = Today()
            };
            PlayerPrefs.SetInt("family_foster_xp",
                PlayerPrefs.GetInt("family_foster_xp", 0) + FosterXpBonus);
            PlayerPrefs.Save();
            Save(); Notify();
        }

        /// <summary>Fa crescere i figli nel tempo (chiamato periodicamente):
        /// un anno ogni DaysPerYear(=5) giorni solari reali trascorsi dalla nascita,
        /// fino a 18 anni. Stesso ritmo del player.</summary>
        public static void AgeChildren()
        {
            EnsureLoaded();
            if (_store.children == null || _store.children.Count == 0) return;
            bool changed = false;
            for (int i = 0; i < _store.children.Count; i++)
            {
                var c = _store.children[i];
                if (c == null) continue;
                DateTime born;
                if (!DateTime.TryParse(c.bornDay, out born)) continue;
                int days = (DateTime.UtcNow.Date - born.Date).Days;
                int years = Mathf.Clamp(days / DaysPerYear, 0, 18);
                if (years != c.ageYears)
                {
                    c.ageYears = years;
                    changed = true;
                }
            }
            if (changed) { Save(); Notify(); }
        }

        private static string PlayerBornDay
        {
            get
            {
                EnsureLoaded();
                if (string.IsNullOrEmpty(_store.playerBornDay))
                {
                    _store.playerBornDay = Today();
                    Save();
                }
                return _store.playerBornDay;
            }
        }

        /// <summary>Imposta l'età iniziale scelta dal player all'avvio,
        /// retrodatando il giorno di nascita di years * DaysPerYear giorni.</summary>
        public static void SetInitialAge(int years)
        {
            if (years < 0) years = 0;
            EnsureLoaded();
            _store.playerBornDay = DateTime.UtcNow.Date
                .AddDays(-(years * DaysPerYear))
                .ToString("yyyy-MM-dd");
            AgeYears = years;
            Save();
        }

        /// <summary>True se il player ha già scelto l'età iniziale in questa partita.</summary>
        public static bool HasChosenAge
        {
            get { return UnityEngine.PlayerPrefs.GetInt("huntix_age_chosen", 0) == 1; }
        }

        public static void MarkAgeChosen()
        {
            UnityEngine.PlayerPrefs.SetInt("huntix_age_chosen", 1);
            UnityEngine.PlayerPrefs.Save();
        }

        /// <summary>Sesso del player letto dal profilo Huntix (fonte di verità).
        /// true = femmina, false = maschio. Default maschio.
        /// Non si salva nel Store locale: vale la scelta fatta in registrazione.</summary>
        public static bool IsFemale { get; private set; }

        /// <summary>Applica il sesso scelto in registrazione Huntix.</summary>
        public static void SetProfileGender(bool female)
        {
            IsFemale = female;
        }

        private static string Today()
        {
            return DateTime.UtcNow.ToString("yyyy-MM-dd");
        }

        private static void EnsureLoaded()
        {
            if (_store != null) return;
            try
            {
                if (File.Exists(FilePath))
                    _store = JsonUtility.FromJson<Store>(File.ReadAllText(FilePath));
            }
            catch (Exception) { }
            if (_store == null) _store = new Store();
            if (_store.children == null) _store.children = new List<ChildInfo>();
            if (_store.breaks == null) _store.breaks = new List<BreakInfo>();
        }

        private static void Save()
        {
            try
            {
                File.WriteAllText(FilePath, JsonUtility.ToJson(_store));
            }
            catch (Exception e)
            {
                Debug.LogWarning("[Family] save: " + e.Message);
            }
        }

        private static void Notify()
        {
            var h = OnFamilyChanged;
            if (h != null) h();
        }

        /// <summary>Avvia il tick di crescita dei figli.</summary>

        public static int AgeYears { get; set; }
        public static bool IsDead { get; set; }

        public static void UpdateNeeds()
        {
            if (IsDead) return;
            if (hunger > 0) hunger = System.Math.Max(0, hunger - (int)NeedsDecreasePerDay);
            if (thirst > 0) thirst = System.Math.Max(0, thirst - (int)NeedsDecreasePerDay);
            if (fatigue > 0) fatigue = System.Math.Max(0, fatigue - (int)NeedsDecreasePerDay);

            if (hunger <= 0) daysGoingHungry++; else daysGoingHungry = 0;
            if (thirst <= 0) daysGoingThirsty++; else daysGoingThirsty = 0;
            if (fatigue <= 0) daysGoingTired++; else daysGoingTired = 0;

            if (daysGoingHungry >= NeedsCriticalDays && CanDie())
                Die(DeathType.STARVATION);
            else if (daysGoingThirsty >= NeedsCriticalDays && CanDie())
                Die(DeathType.DEHYDRATION);
            else if (daysGoingTired >= NeedsCriticalDays && CanDie())
                Die(DeathType.EXHAUSTION);
        }

        public static bool CanDie()
        {
            return AgeYears >= 12;
        }

        /// <summary>Danno diretto ai bisogni (es. pozze laviche dell'Inferno):
        /// riduce fame/sete/fatica. Se restano a zero, il tick sto piu' portare
        /// alla morte per inedia come previsto dal ciclo di vita.</summary>
        public static void Hurt(int amount)
        {
            if (IsDead || amount <= 0) return;
            hunger = System.Math.Max(0, hunger - amount);
            thirst = System.Math.Max(0, thirst - amount);
            fatigue = System.Math.Max(0, fatigue - amount);
            if (hunger <= 0) daysGoingHungry++; else daysGoingHungry = System.Math.Max(0, daysGoingHungry - 1);
            if (thirst <= 0) daysGoingThirsty++; else daysGoingThirsty = System.Math.Max(0, daysGoingThirsty - 1);
            if (fatigue <= 0) daysGoingTired++; else daysGoingTired = System.Math.Max(0, daysGoingTired - 1);
        }

        public static void Die(DeathType type)
        {
            if (IsDead) return;
            IsDead = true;
            deathTime = System.DateTime.UtcNow;
            lastDeathType = type;
            ShowToast("MORTE: " + DeathMessage(type));
            FamilyHost.Instance?.BeginAfterlife(type);
        }

        public static string DeathMessage(DeathType type)
        {
            switch (type)
            {
                case DeathType.STARVATION:
                    return "Sei deceduto/a per fame protratta. Il corpo viene trovato dai familiari.";
                case DeathType.DEHYDRATION:
                    return "Morte per sete. Nessun liquido consumato per 3 giorni.";
                case DeathType.EXHAUSTION:
                    return "Esaurimento fisico prolungato. Il corpo cede.";
                case DeathType.OLD_AGE:
                    return "Morte naturale per vecchiaia all'" + AgeYears + " anni. L'eredita passa ai familiari.";
                case DeathType.INCIDENTE:
                    return "Morte in un incidente improvviso. La famiglia piange la perdita.";
                case DeathType.CADUTA:
                    return "Morte per caduta dall'altezza. Incidentale e improvvisa.";
                case DeathType.MALATTIA:
                default:
                    return "Morte per malattia naturale, all'" + AgeYears + " anni.";
            }
        }

        public static void ShowToast(string message)
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(message);
        }

        public static void ResetPlayerState()
        {
            hunger = NeedsMax;
            thirst = NeedsMax;
            fatigue = NeedsMax;
            daysGoingHungry = 0;
            daysGoingThirsty = 0;
            daysGoingTired = 0;
            AgeYears = 0;
            IsDead = false;
            _ageSeconds = 0f;
            _store.playerBornDay = Today();
            Save();
        }

        // ── Sync con il profilo Huntix (unico player, unico universo) ──
        // Miacitta non ha un proprio XP separato: ogni XP guadagnato qui
        // (fidanzamento, matrimonio, figli, foster, missioni, reincarnazione)
        // viene accreditato sul profilo Huntix, la sola fonte di verita'.
        // Su editor (senza Android) i metodi sono no-op e tutto resta locale.

        /// <summary>Accredita XP al profilo Huntix, con sorgente leggibile nei log.</summary>
        public static void SyncXpToHuntix(int xpAmount, string source)
        {
            if (xpAmount <= 0) return;
            try
            {
                string json = "{\"xp\":" + xpAmount + ",\"source\":\"" + source + "\"}";
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("CityXpEarned", json);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[Family] SyncXpToHuntix fallito: " + e.Message);
            }
        }

        /// <summary>Invia l'energia del player al profilo Huntix.</summary>
        public static void SyncEnergyToHuntix(int energy)
        {
            try
            {
                string json = "{\"energy\":" + energy + "}";
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("CityEnergyUpdate", json);
            }
            catch (System.Exception) { }
        }

        /// <summary>
        /// Raccoglie e svuota gli XP famiglia accumulati localmente (matrimonio,
        /// figli/nascita, foster) e li accredita sul profilo Huntix.
        /// Chiamato periodicamente e alla reincarnazione.
        /// </summary>
        public static void FlushCityXpToHuntix()
        {
            int xp = PlayerPrefs.GetInt("family_wedding_xp", 0)
                   + PlayerPrefs.GetInt("family_foster_xp", 0)
                   + PlayerPrefs.GetInt("family_spouse_xp", 0);
            if (xp <= 0) return;
            PlayerPrefs.SetInt("family_wedding_xp", 0);
            PlayerPrefs.SetInt("family_foster_xp", 0);
            PlayerPrefs.SetInt("family_spouse_xp", 0);
            PlayerPrefs.Save();
            SyncXpToHuntix(xp, "famiglia");
        }

        /// <summary>
        /// Alla reincarnazione la vita ricomincia con un eventuale nuovo nome.
        /// Il profilo Huntix (un solo player) lo adotta, così il nome resta
        /// coerente in classifica e in tutti i moduli.
        /// </summary>
        public static void SyncReincarnationToHuntix(string newName)
        {
            try
            {
                FlushCityXpToHuntix();
                // Bonus di reincarnazione XP
                string json = "{\"name\":\"" + newName + "\",\"xp\":25}";
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("PlayerReincarnated", json);
                // Fallback diretto sul nome
                Huntix.Bridge.UnityBridge.SetPlayerNameFromCity(newName);
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[Family] SyncReincarnationToHuntix: " + e.Message);
            }
        }

        private static float _ageSeconds;
        /// <summary>Avanza l'invecchiamento del player: come i figli, un anno
        /// ogni DaysPerYear(=5) giorni solari reali dal giorno di nascita.
        /// Al raggiungimento di MaxAge (=100) innesca la morte per vecchiaia.</summary>
        public static void AgingTick()
        {
            if (IsDead) return;
            DateTime born;
            if (!DateTime.TryParse(PlayerBornDay, out born)) return;
            int days = (DateTime.UtcNow.Date - born.Date).Days;
            AgeYears = System.Math.Max(0, days / DaysPerYear);
            if (AgeYears >= MaxAge)
                Die(DeathType.OLD_AGE);
        }

        public static void Ensure()
        {
            if (FamilyHost.Instance != null) return;
            var go = new GameObject("FamilyHost");
            UnityEngine.Object.DontDestroyOnLoad(go);
            go.AddComponent<FamilyHost>();
        }

        private sealed class FamilyHost : MonoBehaviour
        {
            public static FamilyHost Instance;
            private float _timer;

            private void Awake() { Instance = this; }

            private void Update()
            {
                _timer += Time.unscaledDeltaTime;
                if (_timer < 6f) return;
                _timer = 0f;
                AgeChildren();
                FamilyManager.UpdateNeeds();
                FamilyManager.AgingTick();
                TickAfterlife();
                // Unico player: gli XP famiglia accumulati in Miacitta vanno sul
                // profilo Huntix (fonte di verita') e alimentano livello/classifica.
                try { FamilyManager.FlushCityXpToHuntix(); }
                catch (System.Exception) { }
            }

            private float _afterlifeTimer;
            private int _afterlifeStep;

            private void EnterRealm(City.Afterlife.AfterlifeRealm realm)
            {
                City.Afterlife.RealmSceneManager.Ensure().EnterRealm(realm);
            }

            public void BeginAfterlife(DeathType type)
            {
                _afterlifeTimer = 0f;
                _afterlifeStep = 1;
                EnterRealm(City.Afterlife.AfterlifeRealm.INFERNO);
                ShowToast("INFERNO: " + FamilyManager.DeathMessage(type) + " Ti risvegli nell'Inferno. Fuoco ovunque.");
            }

            private void TickAfterlife()
            {
                if (_afterlifeStep == 0) return;
                _afterlifeTimer += Time.unscaledDeltaTime;
                if (_afterlifeStep == 1 && _afterlifeTimer >= 4f)
                {
                    _afterlifeStep = 2; _afterlifeTimer = 0f;
                    EnterRealm(City.Afterlife.AfterlifeRealm.PURGATORIO);
                    ShowToast("PURGATORIO: Supera gli ostacoli per redimerti. Atmosfera densa e grigia.");
                }
                else if (_afterlifeStep == 2 && _afterlifeTimer >= 4f)
                {
                    _afterlifeStep = 3; _afterlifeTimer = 0f;
                    EnterRealm(City.Afterlife.AfterlifeRealm.PARADISO);
                    ShowToast("PARADISO: Purificato. Entri nel Paradiso. Atmosfera serena, XP raddoppiato. Reincarnazione tra poco...");
                }
                else if (_afterlifeStep == 3 && _afterlifeTimer >= 4f)
                {
                    _afterlifeStep = 4; _afterlifeTimer = 0f;
                    City.Afterlife.RealmSceneManager.Ensure().ReturnToCity();
                    FamilyManager.ResetPlayerState();
                    // Reincarnazione: nuova vita, nuovo nome. Il profilo Huntix
                    // (un solo player) viene aggiornato con il nuovo nome e ritrova
                    // la stessa XP accumulata nella vita precedente. Il sesso resta
                    // quello scelto in registrazione (profilo Huntix).
                    string newName = FamilyManager.IsFemale ? "Giulia" : "Marco";
                    FamilyManager.SyncReincarnationToHuntix(newName);
                    long huntixXp = Huntix.Bridge.UnityBridge.GetPlayerXp();
                    ShowToast("Nasci nuovamente come " + newName + ". Nuova vita! XP totale: " + huntixXp);
                }
            }

            private void OnDestroy() { if (Instance == this) Instance = null; }
        }
    }
}

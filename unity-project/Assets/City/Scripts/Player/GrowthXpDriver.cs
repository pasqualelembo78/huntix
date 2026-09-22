using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// GROWTH XP DRIVER — collega il sistema XP/livello GIA' ESISTENTE alla
    /// crescita fisica del personaggio (FASE 2).
    ///
    /// NON riscrive nulla: legge il livello dal SINGOLO profilo Huntix
    /// (un'unica XP condivisa, via bridge Android -> UnityBridge.GetPlayerLevel)
    /// e lo mappa sulle 4 dimensioni di GrowthMorphProfile che poi vengono
    /// applicate, a transizione morbida, dal GrowthMorphController (morph).
    ///
    /// Vincoli rispettati:
    ///  - XP/livello: INTATTI (nessuna scrittura, sola lettura via bridge);
    ///  - se manca il bridge (editor) o il profilo, resta "Giocatore"/livello 1
    ///    e l'aspetto resta quello base;
    ///  - senza morph cotti e' un no-op silenzioso (come il controller).
    ///
    /// Mapping (curva progressive):
    ///   livello 1            -> sei un uovo appena schiuso (basso, esile,
    ///                            testa grande, bambino);
    ///   livello MAX (cap)    -> adulto slanciato e robusto.
    /// Le 4 curve sono monotone e "smussate" (SmoothStep) per una crescita
    /// credibile e non a scatti.
    /// </summary>
    public class GrowthXpDriver : MonoBehaviour
    {
        /// <summary>Livello a cui la crescita fisica e' "completa" (cap).</summary>
        public const int MaxLevel = 50;

        // Estremi delle dimensioni a livello 1 e a cap (0..1).
        private static readonly float Age1 = 0.35f, AgeMax = 0.62f;
        private static readonly float Height1 = 0.38f, HeightMax = 0.68f;
        private static readonly float Proportion1 = 0.36f, ProportionMax = 0.64f;
        private static readonly float Shape1 = 0.40f, ShapeMax = 0.62f;

        [Header("Bridging")]
        [Tooltip("Intervallo (s) di rilettura del livello dal profilo Huntix. "
            + "Tanto vale essere leggeri: il livello cambia raramente.")]
        public float pollSeconds = 3f;

        [Header("Debug / test")]
        [Tooltip("Override manuale del livello (0 = usa il profilo dal bridge). "
            + "Per provare le fasi di crescita senza aspettare l'XP reale.")]
        [Range(0, 200)]
        public int debugLevelOverride = 0;

        private GrowthMorphController _morph;
        private int _lastLevel = -1;
        private float _timer;

        /// <summary>Installa il driver sull'hero rig (idempotente, safe).</summary>
        public static GrowthXpDriver Ensure(GameObject root)
        {
            if (root == null) return null;
            var existing = root.GetComponentInChildren<GrowthXpDriver>(true);
            if (existing != null) return existing;
            var driver = root.AddComponent<GrowthXpDriver>();
            OsmDiag.Log("[GrowthXpDriver][Ensure] driver installato su " + root.name);
            return driver;
        }

        /// <summary>Livello corrente: override debug se > 0, altrimenti il
        /// livello unico del player (bridge Android).</summary>
        public int CurrentLevel
        {
            get
            {
                if (debugLevelOverride > 0) return debugLevelOverride;
                return Huntix.Bridge.UnityBridge.GetPlayerLevel();
            }
        }

        /// <summary>Mappa livello -> profilo morph (0.5 = aspetto base).</summary>
        public static GrowthMorphProfile ProfileFromLevel(int level)
        {
            int lvl = Mathf.Max(1, level);
            float t = Mathf.Clamp01((lvl - 1f) / (Mathf.Max(1, MaxLevel - 1f)));
            float s = t * t * (3f - 2f * t); // SmoothStep
            GrowthMorphProfile p;
            p.age = Mathf.Lerp(Age1, AgeMax, s);
            p.height = Mathf.Lerp(Height1, HeightMax, s);
            p.proportion = Mathf.Lerp(Proportion1, ProportionMax, s);
            p.shape = Mathf.Lerp(Shape1, ShapeMax, s);
            return p;
        }

        /// <summary>Profilo registrato come "atteso" (per debug/UI).</summary>
        public GrowthMorphProfile ExpectedProfile()
        {
            return ProfileFromLevel(CurrentLevel);
        }

        /// <summary>Snapshot JSON dell'aspetto raggiunto (bridge Unity→Android:
        /// ogni componente Unity espone un evento bidirezionale).</summary>
        public static string SnapshotJson(int level, GrowthMorphProfile p)
        {
            return "{\"level\":" + level +
                ",\"age\":" + p.age.ToString("F3", System.Globalization.CultureInfo.InvariantCulture) +
                ",\"height\":" + p.height.ToString("F3", System.Globalization.CultureInfo.InvariantCulture) +
                ",\"proportion\":" + p.proportion.ToString("F3", System.Globalization.CultureInfo.InvariantCulture) +
                ",\"shape\":" + p.shape.ToString("F3", System.Globalization.CultureInfo.InvariantCulture) + "}";
        }

        /// <summary>Applica subito il livello corrente (da chiamare anche a
        /// inizio gioco e quando si sale di livello).</summary>
        public void ApplyNow()
        {
            int lvl = CurrentLevel;

            // Riflesso uova: al passaggio di un livello chiave l'uovo di
            // crescita "si schiude" davanti al personaggio (effetto visivo
            // puro; la registrazione in inventario avviene lato Android).
            var crossed = GrowthEggEffect.CrossedKeyLevels(_lastLevel, lvl);
            for (int i = 0; i < crossed.Count; i++)
                GrowthEggEffect.Spawn(transform, crossed[i], i);

            _lastLevel = lvl;

            if (_morph == null)
            {
                _morph = gameObject.GetComponentInChildren<GrowthMorphController>(true);
            }
            if (_morph != null)
            {
                // Quando c'e' il driver XP, l'aspetto deriva dall'XP (un'unica
                // fonte): disattiva la persistenza PlayerPrefs del controller.
                _morph.persistToPrefs = false;
                _morph.ApplyProfile(ProfileFromLevel(lvl));
                OsmDiag.Log("[GrowthXpDriver] applicato livello " + lvl + " -> " +
                    ProfileFromLevel(lvl));
            }
            else
            {
                OsmDiag.Log("[GrowthXpDriver] morph non disponibili: solo dell'uovo di crescita.");
            }

            // Bridge bidirezionale (mandato): ogni componente Unity espone
            // anche l'evento verso Android. Qui notifichiamo l'aspetto
            // raggiunto (leggero, solo a cambio livello/debug).
            try
            {
                Huntix.Bridge.UnityBridge.SendMessageToAndroid("GrowthStateSync",
                    SnapshotJson(lvl, ProfileFromLevel(lvl)));
            }
            catch (System.Exception ex)
            {
                OsmDiag.Log("[GrowthXpDriver] bridge GrowthStateSync non disponibile: " + ex.Message);
            }
        }

        public void ApplyTo(GrowthMorphController morph)
        {
            if (morph == null) return;
            _morph = morph;
            ApplyNow();
        }

        // Flag una-tantum: l'uovo "di benvenuto" per chi ha gia' superato un
        // livello chiave prima dell'introduzione del sistema viene mostrato
        // una sola volta, non a ogni avvio.
        private const string KeyWelcomeShown = "huntix_growth_egg_welcome_shown";

        private void Start()
        {
            _timer = 0f;
            _lastLevel = -1;
            ApplyNow();

            // Backfill uova: chi entra con un livello gia' saltato (feature
            // appena introdotta) vede una volta l'uovo dell'ultimo livello
            // chiave raggiunto (la registrazione in inventario avviene comunque
            // lato Android via GrowthStateSync a ogni salvataggio).
            if (PlayerPrefs.GetInt(KeyWelcomeShown, 0) == 0)
            {
                int lvl = CurrentLevel;
                int highestKey = 0;
                for (int i = 0; i < GrowthEggEffect.KeyLevels.Length; i++)
                {
                    int k = GrowthEggEffect.KeyLevels[i];
                    if (k <= lvl && k > highestKey) highestKey = k;
                }
                if (highestKey > 0)
                {
                    GrowthEggEffect.Spawn(transform, highestKey);
                    OsmDiag.Log("[GrowthXpDriver] uovo di benvenuto (backfill) livello " + highestKey);
                }
                PlayerPrefs.SetInt(KeyWelcomeShown, 1);
                PlayerPrefs.Save();
            }
        }

        private void Update()
        {
            if (pollSeconds <= 0.001f || _morph == null) return;
            _timer += Time.deltaTime;
            if (_timer < pollSeconds) return;
            _timer = 0f;

            int lvl = CurrentLevel;
            if (lvl != _lastLevel)
            {
                _lastLevel = lvl;
                ApplyNow();
            }
        }
    }
}
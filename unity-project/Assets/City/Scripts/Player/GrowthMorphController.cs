using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// MORPH / BLEND SHAPE CONTROLLER — Sistema di crescita professionale.
    ///
    /// Pipeline cablata (fase 2):
    ///   XP / Level esistente (bridge Android)  ->  GrowthXpDriver  ->  QUESTO controller  ->  Character
    ///
    /// FASE 2 = il driver legge l'unico livello del player (il sistema XP
    /// esistente resta INTATTO, nessuna scrittura) e lo mappa sul profilo 4D;
    /// FASE 1 = test isolato sul personaggio attuale (PlayerHero/Remy, Mixamo).
    /// Il controller:
    ///
    ///  1. trova gli SkinnedMeshRenderer dell'hero rig e sostituisce la
    ///     mesh con la variante "morphizzata" cotta da RemyMorphBaker
    ///     (Assets/Resources/Morphs/Remy/&lt;MeshName&gt;.asset): STESSA ossatura,
    ///     stesse bindpose, stessi materiali -> rig/animazioni/controller
    ///     invariati, nessun cambio di modello (solo blend shapes aggiunte).
    ///  2. mappa le 4 dimensioni di GrowthMorphProfile (Age, Height,
    ///     BodyProportion, BodyShape) sui pesi delle blend shape con
    ///     transizioni progressive e senza sostituzioni brusche.
    ///  3. salva/ripristina l'aspetto raggiunto via PlayerPrefs SOLO se
    ///     persistToPrefs (FASE 1 standalone); con il GrowthXpDriver attivo
    ///     l'autorita' sull'aspetto e' l'XP e la persistenza e' spenta.
    ///
    /// Se i morph non sono stati cotti (bake mai eseguito) il controller
    /// resta un no-op silenzioso: il gioco continua esattamente come prima.
    /// </summary>
    public class GrowthMorphController : MonoBehaviour
    {
        public const string MorphResourcesPrefix = "Morphs/Remy/";
        private const string MorphProbeMesh = "Body";

        // Nomi delle blend shape prodotte dal baker (stesse convenzioni).
        public const string MorphAgeYoung = "Age_Young";
        public const string MorphAgeOld = "Age_Old";
        public const string MorphHeightShort = "Height_Short";
        public const string MorphHeightTall = "Height_Tall";
        public const string MorphLegsShort = "Prop_LegsShort";
        public const string MorphLegsLong = "Prop_LegsLong";
        public const string MorphArmsShort = "Prop_ArmsShort";
        public const string MorphArmsLong = "Prop_ArmsLong";
        public const string MorphHeadBig = "Prop_HeadBig";
        public const string MorphHeadSmall = "Prop_HeadSmall";
        public const string MorphShapeLean = "Shape_Lean";
        public const string MorphShapeStout = "Shape_Stout";

        private const string KeyAge = "huntix_growth_age";
        private const string KeyHeight = "huntix_growth_height";
        private const string KeyProportion = "huntix_growth_proportion";
        private const string KeyShape = "huntix_growth_shape";
        private const string KeyVersion = "huntix_growth_ver";
        private const int Version = 1;

        [Header("Transizioni")]
        [Tooltip("Velocita' di transizione morbida dei pesi (0..1).")]
        [Range(0f, 1f)]
        public float smoothSpeed = 0.05f;

        [Header("Test isolato (FASE 1)")]
        [Tooltip("Quando true fa un ciclo automatico delle 4 dimensioni di "
            + "crescita in loop, per verificare a colpo d'occhio che i morph "
            + "funzionano sul personaggio attuale. Non tocca il sistema XP.")]
        public bool testAutoCycle = false;

        [Tooltip("Secondi completi di un ciclo di test (se testAutoCycle).")]
        public float testCycleSeconds = 10f;

        [Header("Persistenza")]
        [Tooltip("Quando TRUE l'aspetto viene salvato/ripristinato via "
            + "PlayerPrefs (FASE 1, aspetto libero). Quando pilotato dal "
            + "GrowthXpDriver (FASE 2) deve essere FALSE: l'autorita' "
            + "sull'aspetto e' l'XP/livello, non il salvataggio locale.")]
        public bool persistToPrefs = true;

        private struct MorphSlot
        {
            public SkinnedMeshRenderer smr;
            public int index;
        }

        private readonly List<MorphSlot> _ageYoung = new List<MorphSlot>();
        private readonly List<MorphSlot> _ageOld = new List<MorphSlot>();
        private readonly List<MorphSlot> _heightShort = new List<MorphSlot>();
        private readonly List<MorphSlot> _heightTall = new List<MorphSlot>();
        private readonly List<MorphSlot> _legsShort = new List<MorphSlot>();
        private readonly List<MorphSlot> _legsLong = new List<MorphSlot>();
        private readonly List<MorphSlot> _armsShort = new List<MorphSlot>();
        private readonly List<MorphSlot> _armsLong = new List<MorphSlot>();
        private readonly List<MorphSlot> _headBig = new List<MorphSlot>();
        private readonly List<MorphSlot> _headSmall = new List<MorphSlot>();
        private readonly List<MorphSlot> _shapeLean = new List<MorphSlot>();
        private readonly List<MorphSlot> _shapeStout = new List<MorphSlot>();

        private GrowthMorphProfile _target;
        private GrowthMorphProfile _current;
        private bool _hasMorphs;
        private bool _dirty;

        public bool HasMorphs { get { return _hasMorphs; } }

        /// <summary>Vero se esistono morph cotti per il modello (no-op se no).</summary>
        public static bool HasMorphData()
        {
            Mesh probe = Resources.Load<Mesh>(MorphResourcesPrefix + MorphProbeMesh);
            return probe != null && probe.blendShapeCount > 0;
        }

        /// <summary>
        /// Assicura un controller sull'hero rig (idempotente). Viene chiamato
        /// da PlayerHeroRig.Ensure: se i morph non sono cotti non fa nulla.
        /// </summary>
        public static GrowthMorphController Ensure(GameObject root)
        {
            if (root == null) return null;
            var existing = root.GetComponentInChildren<GrowthMorphController>(true);
            if (existing != null) return existing;
            if (!HasMorphData()) return null;
            var ctrl = root.AddComponent<GrowthMorphController>();
            OsmDiag.Log("[GrowthMorphController][Ensure] controller installato su " +
                root.name + " (morph cotti rilevati)");
            return ctrl;
        }

        public void ResetDefaults()
        {
            _target = GrowthMorphProfile.Neutral;
            _current = _target;
            _dirty = true;
            SaveProfile();
        }

        public void ApplyProfile(GrowthMorphProfile profile)
        {
            _target = profile.Clamped();
            _dirty = true;
        }

        public GrowthMorphProfile GetProfile()
        {
            return _target;
        }

        public void SaveProfile()
        {
            if (!persistToPrefs) return;
            PlayerPrefs.SetFloat(KeyAge, _target.age);
            PlayerPrefs.SetFloat(KeyHeight, _target.height);
            PlayerPrefs.SetFloat(KeyProportion, _target.proportion);
            PlayerPrefs.SetFloat(KeyShape, _target.shape);
            PlayerPrefs.SetInt(KeyVersion, Version);
            PlayerPrefs.Save();
        }

        private GrowthMorphProfile LoadProfile()
        {
            if (PlayerPrefs.GetInt(KeyVersion, 0) != Version)
                return GrowthMorphProfile.Neutral;
            GrowthMorphProfile p;
            p.age = PlayerPrefs.GetFloat(KeyAge, 0.5f);
            p.height = PlayerPrefs.GetFloat(KeyHeight, 0.5f);
            p.proportion = PlayerPrefs.GetFloat(KeyProportion, 0.5f);
            p.shape = PlayerPrefs.GetFloat(KeyShape, 0.5f);
            return p.Clamped();
        }

        private void Awake()
        {
            _target = LoadProfile();
            _current = _target;
        }

        private void Start()
        {
            BindMeshSlots();
            if (!_hasMorphs)
            {
                OsmDiag.Log("[GrowthMorphController] nessun morph caricato su " +
                    gameObject.name + ": sistema disattivo (bake non eseguito).");
                return;
            }
            OsmDiag.Log("[GrowthMorphController] morph attivi su " + gameObject.name +
                " età/altezza/proporzioni/corporatura" + (persistToPrefs ? " + PERSISTENZA PlayerPrefs." : "."));
            if (persistToPrefs) ApplyProfile(LoadProfile());
        }

        /// <summary>
        /// Scansiona gli SMR dell'hero rig: per ognuno carica la variante
        /// morphizzata (se presente) e registra i canali blend shape.
        /// </summary>
        private void BindMeshSlots()
        {
            var smrs = GetComponentsInChildren<SkinnedMeshRenderer>(true);
            for (int r = 0; r < smrs.Length; r++)
            {
                var smr = smrs[r];
                if (smr == null) continue;
                Mesh baked = LoadMorphMeshFor(smr);
                if (baked == null) continue;

                smr.sharedMesh = baked;
                _hasMorphs = true;

                Register(smr, baked, MorphAgeYoung, _ageYoung);
                Register(smr, baked, MorphAgeOld, _ageOld);
                Register(smr, baked, MorphHeightShort, _heightShort);
                Register(smr, baked, MorphHeightTall, _heightTall);
                Register(smr, baked, MorphLegsShort, _legsShort);
                Register(smr, baked, MorphLegsLong, _legsLong);
                Register(smr, baked, MorphArmsShort, _armsShort);
                Register(smr, baked, MorphArmsLong, _armsLong);
                Register(smr, baked, MorphHeadBig, _headBig);
                Register(smr, baked, MorphHeadSmall, _headSmall);
                Register(smr, baked, MorphShapeLean, _shapeLean);
                Register(smr, baked, MorphShapeStout, _shapeStout);
            }
        }

        private static Mesh LoadMorphMeshFor(SkinnedMeshRenderer smr)
        {
            if (smr.sharedMesh == null) return null;
            string name = smr.sharedMesh.name;
            if (string.IsNullOrEmpty(name)) return null;
            return Resources.Load<Mesh>(MorphResourcesPrefix + name);
        }

        private static void Register(SkinnedMeshRenderer smr, Mesh mesh,
            string morphName, List<MorphSlot> slots)
        {
            int idx = mesh.GetBlendShapeIndex(morphName);
            if (idx < 0) return;
            MorphSlot slot;
            slot.smr = smr;
            slot.index = idx;
            slots.Add(slot);
        }

        private void Update()
        {
            if (!_hasMorphs) return;

            if (testAutoCycle) StepTestCycle();

            // Transizione morbida verso il profilo target.
            float k = Mathf.Clamp01(smoothSpeed);
            if (k <= 0f)
            {
                _current = _target;
                _dirty = true;
            }
            else
            {
                GrowthMorphProfile c = _current;
                c.age = Mathf.Lerp(c.age, _target.age, k);
                c.height = Mathf.Lerp(c.height, _target.height, k);
                c.proportion = Mathf.Lerp(c.proportion, _target.proportion, k);
                c.shape = Mathf.Lerp(c.shape, _target.shape, k);
                if (Differs(c, _current) || Differs(_target, c))
                    _dirty = true;
                _current = c;
            }

            if (_dirty)
            {
                _dirty = false;
                ApplyWeights();
            }
        }

        private static bool Differs(GrowthMorphProfile a, GrowthMorphProfile b)
        {
            return Mathf.Abs(a.age - b.age) > 0.0005f ||
                Mathf.Abs(a.height - b.height) > 0.0005f ||
                Mathf.Abs(a.proportion - b.proportion) > 0.0005f ||
                Mathf.Abs(a.shape - b.shape) > 0.0005f;
        }

        /// <summary>Peso 0..100 dalle due polarita' di una dimensione.</summary>
        private static float W(float v)
        {
            return Mathf.Clamp01(Mathf.Abs(v - 0.5f) * 2f) * 100f;
        }

        private void ApplyWeights()
        {
            float age = _current.age;
            float height = _current.height;
            float prop = _current.proportion;
            float shape = _current.shape;

            SetGroup(_ageYoung, age < 0.5f ? W(age) : 0f);
            SetGroup(_ageOld, age > 0.5f ? W(age) : 0f);

            SetGroup(_heightShort, height < 0.5f ? W(height) : 0f);
            SetGroup(_heightTall, height > 0.5f ? W(height) : 0f);

            SetGroup(_legsShort, prop < 0.5f ? W(prop) : 0f);
            SetGroup(_legsLong, prop > 0.5f ? W(prop) : 0f);
            SetGroup(_armsShort, prop < 0.5f ? W(prop) : 0f);
            SetGroup(_armsLong, prop > 0.5f ? W(prop) : 0f);
            SetGroup(_headBig, prop < 0.5f ? W(prop) : 0f);
            SetGroup(_headSmall, prop > 0.5f ? W(prop) : 0f);

            SetGroup(_shapeLean, shape < 0.5f ? W(shape) : 0f);
            SetGroup(_shapeStout, shape > 0.5f ? W(shape) : 0f);
        }

        private static void SetGroup(List<MorphSlot> slots, float weight)
        {
            for (int i = 0; i < slots.Count; i++)
            {
                MorphSlot s = slots[i];
                if (s.smr == null) continue;
                s.smr.SetBlendShapeWeight(s.index, weight);
            }
        }

        /// <summary>
        /// Test isolato: fa girare le 4 dimensioni con un'onda a dente di sega
        /// senza collegarsi a nessun sistema esterno (XP/level!).
        /// </summary>
        private void StepTestCycle()
        {
            float t = testCycleSeconds <= 0.001f ? 0f :
                (Time.time % testCycleSeconds) / testCycleSeconds;
            // 4 quadranti da 0.25 della dimensione.
            GrowthMorphProfile p = _target;
            if (t < 0.25f)
                p.age = Mathf.Repeat(t / 0.25f, 1f);
            else if (t < 0.5f)
                p.height = Mathf.Repeat((t - 0.25f) / 0.25f, 1f);
            else if (t < 0.75f)
                p.proportion = Mathf.Repeat((t - 0.5f) / 0.25f, 1f);
            else
                p.shape = Mathf.Repeat((t - 0.75f) / 0.25f, 1f);
            ApplyProfile(p);
        }
    }
}
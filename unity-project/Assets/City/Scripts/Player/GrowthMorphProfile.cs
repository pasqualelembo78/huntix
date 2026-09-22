using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Aspetto di crescita del personaggio, in 4 dimensioni normalizzate
    /// 0..1 (0.5 = aspetto base attuale del modello, senza morph applicati).
    ///
    ///   age        : 0 = giovane/infantile, 1 = anziano
    ///   height     : 0 = basso,             1 = alto
    ///   proportion : 0 = compatto (arti corti, testa grande),
    ///                1 = slanciato (arti lunghi, testa piccola)
    ///   shape      : 0 = esile,             1 = robusto/tarchiato
    ///
    /// Crescita "fisica" reale tramite Blend Shapes (non Transform.localScale):
    /// i valori vengono mappati dal GrowthMorphController sui morph target
    /// della mesh (bake da RemyMorphBaker). Il profilo e' calcolato dal
    /// GrowthXpDriver a partire dal livello XP esistente (FASE 2) oppure puo'
    /// camminare autonomo (FASE 1, test isolato). Il sistema XP resta intatto.
    /// </summary>
    [System.Serializable]
    public struct GrowthMorphProfile
    {
        [Range(0f, 1f)] public float age;
        [Range(0f, 1f)] public float height;
        [Range(0f, 1f)] public float proportion;
        [Range(0f, 1f)] public float shape;

        /// <summary>Aspetto base (nessun morph): tutte le dimensioni a 0.5.</summary>
        public static GrowthMorphProfile Neutral
        {
            get
            {
                return new GrowthMorphProfile
                {
                    age = 0.5f,
                    height = 0.5f,
                    proportion = 0.5f,
                    shape = 0.5f
                };
            }
        }

        public GrowthMorphProfile Clamped()
        {
            GrowthMorphProfile p = this;
            p.age = Mathf.Clamp01(p.age);
            p.height = Mathf.Clamp01(p.height);
            p.proportion = Mathf.Clamp01(p.proportion);
            p.shape = Mathf.Clamp01(p.shape);
            return p;
        }

        public override string ToString()
        {
            return "Growth[age=" + age.ToString("F2") +
                " height=" + height.ToString("F2") +
                " proportion=" + proportion.ToString("F2") +
                " shape=" + shape.ToString("F2") + "]";
        }
    }
}
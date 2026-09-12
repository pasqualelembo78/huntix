using System;
using UnityEngine;

namespace City.Environment
{
    /// <summary>
    /// Crescita del personaggio (livello 4): si nasce Bambino e si cresce
    /// giocando (1 anno ogni minuto reale) fino ad Adulto. Scala SOLO il
    /// modello visivo (SkinnedMeshRenderer), mai la fisica del
    /// CharacterController, cosi' il movimento resta invariato.
    /// </summary>
    public static class AgeSystem
    {
        public const string Key = "city_age";
        public const int MaxAge = 30;
        public const float SecondsPerYear = 60f;

        public static int Value
        {
            get { return Mathf.Clamp(PlayerPrefs.GetInt(Key, 0), 0, MaxAge); }
        }

        /// <summary>0 = Bambino, 1 = Ragazzo, 2 = Adulto.</summary>
        public static int StageOf(int age)
        {
            if (age <= 5) return 0;
            if (age <= 11) return 1;
            return 2;
        }

        public static string StageName(int stage)
        {
            if (stage == 0) return "Bambino";
            if (stage == 1) return "Ragazzo";
            return "Adulto";
        }

        public static float ScaleOf(int stage)
        {
            if (stage == 0) return 0.62f;
            if (stage == 1) return 0.80f;
            return 1f;
        }

        private static float _acc;
        private static int _lastStage = -1;

        public static void Tick(float dt)
        {
            if (dt <= 0f) return;
            _acc += dt;
            if (_acc >= SecondsPerYear)
            {
                _acc -= SecondsPerYear;
                int a = Mathf.Min(MaxAge, Value + 1);
                if (a != Value)
                {
                    PlayerPrefs.SetInt(Key, a);
                    PlayerPrefs.Save();
                }
            }
            int stage = StageOf(Value);
            if (stage != _lastStage)
            {
                _lastStage = stage;
                Game g = Game.Instance;
                if (g != null && g.player != null)
                {
                    try { ApplyTo(g.player.gameObject); }
                    catch (Exception) { }
                    if (g.ui != null)
                        g.ui.ShowToast(StageName(stage) +
                            ": sei cresciuto! (eta " + Value + " anni)");
                }
            }
        }

        /// <summary>Scala SOLO il modello visivo (SkinnedMeshRenderer), senza
        /// toccare la fisica del CharacterController.</summary>
        public static void ApplyTo(GameObject root)
        {
            if (root == null) return;
            float s = ScaleOf(StageOf(Value));
            var smrs = root.GetComponentsInChildren<SkinnedMeshRenderer>();
            if (smrs.Length == 0) return;
            for (int i = 0; i < smrs.Length; i++)
                smrs[i].transform.localScale = new Vector3(s, s, s);
        }
    }
}

// Adattato da SanAndreasUnity — Copyright (c) 2015 James King, MIT.
// Sorgente: Assets/Scripts/Behaviours/OutOfRangeDestroyer.cs del repository
// https://github.com/in0finite/SanAndreasUnity (licenza integrale in
// THIRD_PARTY_NOTICES.md, sezione 1). Non deriva dal fork proprietario
// Grand-Road-Auto e non carica alcun dato/asset GTA.
// Modifiche Huntix: non distrugge nulla; disattiva solo il render delle uova
// con isteresi e grazia temporale, opt-in (default OFF), un solo controllo al
// secondo. Nessun premio, nessuna valuta, nessun cambio dei raggi esistenti.
using System.Collections.Generic;
using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// Culling delle uova lontane con tolleranza: un uovo resta visibile per
    /// 3 s dopo che il player esce dal raggio e rientra subito a 70 m.
    /// La vecchia scansione per frame resta il comportamento predefinito.
    /// </summary>
    public static class EggCullGrace
    {
        public const string Id = "egg-cull";
        public const float DeactivateDistance = 60f;
        public const float ReactivateDistance = 70f;
        public const float GraceSeconds = 3f;
        public const float TickSeconds = 1f;

        private static readonly Dictionary<GameObject, float> OutsideSince =
            new Dictionary<GameObject, float>();
        private static readonly Dictionary<GameObject, bool> Shown =
            new Dictionary<GameObject, bool>();
        private static float _nextTick;
        private static float _nextLog;

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled(Id);
            City.Diagnostics.OpenSourceFeatureBridge.Register(Id, on =>
            {
                Enabled = on;
                OutsideSince.Clear();
                Shown.Clear();
                _nextTick = 0f;
            });
        }

        /// <summary>Un solo controllo al secondo; ritorna true se lo stato
        /// di almeno un uovo e' cambiato (solo log throttled, niente bridge
        /// per uovo: il riflesso uova avviene all'attivazione dell'opzione).</summary>
        public static bool Tick(List<GameObject> eggs, Transform player, float now)
        {
            if (player == null || now < _nextTick) return false;
            _nextTick = now + TickSeconds;
            bool changed = false;
            Vector3 pp = player.position;
            for (int i = eggs.Count - 1; i >= 0; i--)
            {
                var egg = eggs[i];
                if (egg == null) { OutsideSince.Remove(egg); Shown.Remove(egg); continue; }
                float d = Vector3.Distance(egg.transform.position, pp);
                bool shown;
                if (!Shown.TryGetValue(egg, out shown)) shown = egg.activeSelf;
                if (shown && d > DeactivateDistance)
                {
                    float since;
                    if (!OutsideSince.TryGetValue(egg, out since)) { OutsideSince[egg] = now; continue; }
                    if (now - since >= GraceSeconds)
                    {
                        egg.SetActive(false);
                        Shown[egg] = false;
                        OutsideSince.Remove(egg);
                        changed = true;
                    }
                }
                else if (shown && d <= DeactivateDistance)
                {
                    // Rientro nel raggio: azzera sempre la tolleranza, altrimenti
                    // una seconda uscita verrebbe conteggiata dal primo timer.
                    OutsideSince.Remove(egg);
                }
                else if (!shown && d < ReactivateDistance)
                {
                    egg.SetActive(true);
                    Shown[egg] = true;
                    OutsideSince.Remove(egg);
                    changed = true;
                }
                else if (!shown && d >= ReactivateDistance)
                {
                    OutsideSince.Remove(egg);
                }
            }
            if (changed && now >= _nextLog)
            {
                _nextLog = now + 10f;
                Debug.Log("[EggCull] Culling con grazia: stato uova aggiornato (1 Hz)");
            }
            return changed;
        }
    }
}

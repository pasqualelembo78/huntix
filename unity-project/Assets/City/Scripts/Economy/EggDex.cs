using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// EggDex — bestiario/registro delle uova scoperte dal cacciatore in
    /// Miacitta. Tiene traccia (persistita in PlayerPrefs) delle combinazioni
    /// tipo × rarita' trovate, così il giocatore ha un obiettivo di collezione:
    /// completare tutte le voci del registro.
    ///
    /// Ogni voce e' "tipo.rarita'" (es. "Bosco.Uncommon"). Il progresso totale
    /// e' consultabile via TotalFound / TotalEntries.
    /// </summary>
    public static class EggDex
    {
        private const string PrefKey = "huntix_eggdex";
        private static HashSet<string> _found;
        private static bool _loaded;

        public static IReadOnlyCollection<string> Found
        {
            get { EnsureLoaded(); return _found; }
        }

        public static int TotalFound
        {
            get { EnsureLoaded(); return _found.Count; }
        }

        /// <summary>Numero totale di voci possibili (tipi × rarita').</summary>
        public static int TotalEntries
        {
            get
            {
                return (int)EggTypeCount * (int)RarityCount;
            }
        }

        public static int EggTypeCount
        {
            get { return System.Enum.GetValues(typeof(EggController.EggType)).Length; }
        }

        public static int RarityCount
        {
            get { return System.Enum.GetValues(typeof(EggController.Rarity)).Length; }
        }

        /// <summary>Registra una scoperta. Ritorna true se era nuova (prima volta).</summary>
        public static bool Record(EggController egg)
        {
            if (egg == null) return false;
            EnsureLoaded();
            string key = Key(egg.eggType, egg.rarity);
            if (_found.Contains(key)) return false;
            _found.Add(key);
            Save();
            return true;
        }

        public static void Record(string eggType, string rarity)
        {
            EnsureLoaded();
            string key = eggType + "." + rarity;
            if (_found.Contains(key)) return;
            _found.Add(key);
            Save();
        }

        /// <summary>True se la combinazione tipo/rarita' e' gia' stata scoperta.</summary>
        public static bool Has(EggController.EggType t, EggController.Rarity r)
        {
            EnsureLoaded();
            return _found.Contains(Key(t, r));
        }

        private static string Key(EggController.EggType t, EggController.Rarity r)
        {
            return t + "." + r;
        }

        private static void EnsureLoaded()
        {
            if (_loaded) return;
            _loaded = true;
            _found = new HashSet<string>();
            try
            {
                string raw = UnityEngine.PlayerPrefs.GetString(PrefKey, "");
                if (string.IsNullOrEmpty(raw)) return;
                string[] parts = raw.Split(';');
                foreach (var p in parts)
                    if (!string.IsNullOrEmpty(p)) _found.Add(p);
            }
            catch (Exception) { }
        }

        private static void Save()
        {
            if (_found == null) return;
            try
            {
                UnityEngine.PlayerPrefs.SetString(PrefKey, string.Join(";", _found));
                UnityEngine.PlayerPrefs.Save();
            }
            catch (Exception) { }
        }
    }
}

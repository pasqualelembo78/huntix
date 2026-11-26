using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// Specchio Unity delle abilità dei mestieri salvate su Android.
    /// Android è la fonte di verità: i livelli arrivano nel JSON di setMode
    /// (BridgeActivity) e in caldo con CitySkillsUpdated. Qui non si guadagnano
    /// punti: servono solo da gate per i furti e per la UI della concessionaria.
    /// </summary>
    public static class CitySkills
    {
        public const int MaxLevel = 10;
        public const string ThiefId = "mastro_ladro";
        public const int ApartmentTheftLevel = 10;

        private const string PrefLevels = "city_skill_levels_unity";
        private const string PrefPoints = "city_skill_points_unity";

        private static readonly Dictionary<string, int> Levels =
            new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        private static bool _loaded;
        private static int _points;

        public static int Points
        {
            get { Ensure(); return _points; }
        }

        private static void Ensure()
        {
            if (_loaded) return;
            _loaded = true;
            _points = PlayerPrefs.GetInt(PrefPoints, 0);
            string packed = PlayerPrefs.GetString(PrefLevels, "");
            Parse(packed);
        }

        private static void Parse(string packed)
        {
            if (string.IsNullOrEmpty(packed)) return;
            foreach (var pair in packed.Split(';'))
            {
                if (string.IsNullOrEmpty(pair)) continue;
                int colon = pair.IndexOf(':');
                if (colon <= 0 || colon >= pair.Length - 1) continue;
                string id = pair.Substring(0, colon);
                int level;
                if (!int.TryParse(pair.Substring(colon + 1), out level)) continue;
                Levels[id] = Mathf.Clamp(level, 0, MaxLevel);
            }
        }

        /// <summary>Ripristino da Android (setMode o evento caldo).</summary>
        public static void Restore(string packed, int points)
        {
            Levels.Clear();
            Parse(packed);
            _points = Mathf.Max(0, points);
            _loaded = true;
            PlayerPrefs.SetString(PrefLevels, packed ?? "");
            PlayerPrefs.SetInt(PrefPoints, _points);
            PlayerPrefs.Save();
        }

        public static int Level(string id)
        {
            Ensure();
            int level;
            return Levels.TryGetValue(id ?? "", out level) ? level : 0;
        }

        public static int ThiefLevel { get { return Level(ThiefId); } }

        /// <summary>Livello di Mastro Ladro necessario per quel veicolo.
        /// La scala segue il prezzo di listino, così bici/moto partono dal
        /// livello 1 e le auto di lusso restano per il livello 8.</summary>
        public static int TheftRequiredLevel(string model, int price)
        {
            string name = (model ?? "").ToLowerInvariant();
            if (name.Contains("bici") || name.Contains("scooter") ||
                name.Contains("moto") || price <= 25) return 1;
            if (price <= 60) return 2;   // utilitarie
            if (price <= 100) return 4;  // SUV / furgoni
            if (price <= 130) return 6;  // sportive e berline di lusso
            return 8;                    // Pullman, mezzi speciali
        }

        public static string TheftRequirementLabel(string model, int price)
        {
            int required = TheftRequiredLevel(model, price);
            return "Mastro Ladro Lv." + required +
                (ThiefLevel >= required ? " ✅" : " 🔒");
        }
    }
}

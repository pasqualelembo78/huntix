using System.Collections.Generic;
using UnityEngine;

namespace City.World
{
    public static class Inventory
    {
        private static readonly Dictionary<string, int> Items = new Dictionary<string, int>();
        private const string VEHICLE_PREFIX = "vehicle_";

        public static void Add(string itemName, int count = 1)
        {
            if (!Items.ContainsKey(itemName)) Items[itemName] = 0;
            Items[itemName] += count;

            // Persisti veicoli in PlayerPrefs
            if (itemName.StartsWith(VEHICLE_PREFIX))
                PlayerPrefs.SetInt(itemName, Items[itemName]);
        }

        public static int Count(string itemName)
        {
            if (Items.TryGetValue(itemName, out int c)) return c;

            // Prova a caricare da PlayerPrefs (per veicoli)
            if (itemName.StartsWith(VEHICLE_PREFIX))
            {
                int saved = PlayerPrefs.GetInt(itemName, 0);
                if (saved > 0)
                {
                    Items[itemName] = saved;
                    return saved;
                }
            }
            return 0;
        }

        public static bool Has(string itemName)
        {
            return Count(itemName) > 0;
        }
    }
}

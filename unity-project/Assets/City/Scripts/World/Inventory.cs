using System.Collections.Generic;
using UnityEngine;

namespace City.World
{
    public static class Inventory
    {
        private static readonly Dictionary<string, int> Items = new Dictionary<string, int>();
        private const string VEHICLE_PREFIX = "vehicle_";
        private const string ITEM_KEY_PREFIX = "inv_";

        public static void Add(string itemName, int count = 1)
        {
            if (string.IsNullOrEmpty(itemName)) return;
            if (!Items.ContainsKey(itemName)) Items[itemName] = 0;
            Items[itemName] += count;

            // Persisti QUALSIASI item (veicoli e oggetti da negozio): prima
            // solo i "vehicle_" restavano tra un'avvio e l'altro, quindi
            // comprare una mela scalava i soldi ma l'oggetto spariva al riavvio.
            PlayerPrefs.SetInt(Pref(itemName), Items[itemName]);
            PlayerPrefs.Save();
        }

        public static int Count(string itemName)
        {
            if (string.IsNullOrEmpty(itemName)) return 0;
            if (Items.TryGetValue(itemName, out int c)) return c;

            // Prova a caricare da PlayerPrefs (persistito con prefisso "inv_")
            int saved = PlayerPrefs.GetInt(Pref(itemName), 0);
            if (saved > 0)
            {
                Items[itemName] = saved;
                return saved;
            }
            return 0;
        }

        public static bool Has(string itemName)
        {
            return Count(itemName) > 0;
        }

        /// <summary>Rimuove un item (es. vendita veicolo). Ritorna false se assente.</summary>
        public static bool Remove(string itemName, int count = 1)
        {
            if (string.IsNullOrEmpty(itemName)) return false;
            if (!Items.TryGetValue(itemName, out int c) || c < count) return false;
            c -= count;
            if (c <= 0) Items.Remove(itemName);
            else Items[itemName] = c;

            if (c <= 0)
            {
                PlayerPrefs.DeleteKey(Pref(itemName));
                PlayerPrefs.Save();
            }
            else
            {
                PlayerPrefs.SetInt(Pref(itemName), c);
                PlayerPrefs.Save();
            }
            return true;
        }

        private static string Pref(string itemName)
        {
            // I vehicle_ erano salvati col nome nudo: manteniamo quel formato
            // per retrocompatibilita' coi salvataggi esistenti.
            if (itemName.StartsWith(VEHICLE_PREFIX)) return itemName;
            return ITEM_KEY_PREFIX + itemName;
        }
    }
}

using System.Collections.Generic;

namespace City.World
{
    public static class Inventory
    {
        private static readonly Dictionary<string, int> Items = new Dictionary<string, int>();

        public static void Add(string itemName, int count = 1)
        {
            if (!Items.ContainsKey(itemName)) Items[itemName] = 0;
            Items[itemName] += count;
        }

        public static int Count(string itemName)
        {
            return Items.TryGetValue(itemName, out int c) ? c : 0;
        }
    }
}

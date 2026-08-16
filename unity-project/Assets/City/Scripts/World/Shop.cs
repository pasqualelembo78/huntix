using System;
using System.Collections.Generic;

namespace City.World
{
    [Serializable]
    public class ShopItem
    {
        public string name;
        public int price;

        public ShopItem(string name, int price)
        {
            this.name = name;
            this.price = price;
        }
    }

    public class Shop : UnityEngine.MonoBehaviour
    {
        public string shopName = "Negozio";
        public List<ShopItem> items = new List<ShopItem>();
    }
}

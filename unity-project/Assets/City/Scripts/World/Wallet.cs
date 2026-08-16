using System;
using UnityEngine;

namespace City.World
{
    public static class Wallet
    {
        public const string Key = "city_money";
        public const int StartMoney = 100;

        public static event Action<int> OnChanged;

        public static int Money
        {
            get { return PlayerPrefs.GetInt(Key, StartMoney); }
            private set
            {
                PlayerPrefs.SetInt(Key, Mathf.Max(0, value));
                PlayerPrefs.Save();
                OnChanged?.Invoke(Money);
            }
        }

        public static bool CanAfford(int price)
        {
            return Money >= price;
        }

        public static void Spend(int amount)
        {
            Money -= amount;
        }

        public static void Earn(int amount)
        {
            Money += amount;
        }
    }
}

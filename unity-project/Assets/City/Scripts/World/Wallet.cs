using System;
using UnityEngine;

namespace City.World
{
    /// <summary>
    /// Wallet della citta' (soldi del player), FASE 6.
    ///
    /// La superficie API resta IDENTICA alle versioni precedenti
    /// (Money / CanAfford / TrySpend / Spend / Earn), quindi i ~30 punti
    /// del codebase che la chiamano NON cambiano.
    ///
    /// Il saldo e' ora AUTHORITATIVE sul server (tabella city_wallet) per
    /// chi e' autenticato con JWT: cambia telefono e ritrovi i tuoi soldi.
    /// La classe espone una cache ottimistica in memoria per la reattivita'
    /// UI, e delega la sincronizzazione HTTP a WalletManager (MonoBehaviour)
    /// che valida/accoda + invia.

    /// Quando il player NON e' autenticato il comportamento ricade su
    /// PlayerPrefs (la cache locale), come prima, cosi' il gioco funziona
    /// anche offline o in anonimo.
    /// </summary>
    public static class Wallet
    {
        public const string Key = "city_money";
        public const int StartMoney = 300;

        public static event Action<int> OnChanged;

        // Cache ottimistica in memoria. Al primo avvio si semina da PlayerPrefs.
        private static int _cache = PlayerPrefs.GetInt(Key, StartMoney);

        /// <summary>Saldo visibile al player (cache ottimistica).</summary>
        public static int Money
        {
            get { return _cache; }
            private set
            {
                _cache = Mathf.Max(0, value);
                PlayerPrefs.SetInt(Key, _cache);
                PlayerPrefs.Save();
                try { OnChanged?.Invoke(_cache); } catch (Exception) { }
            }
        }

        /// <summary>True se il saldo basta a coprire [price].</summary>
        public static bool CanAfford(int price)
        {
            return _cache >= price;
        }

        /// <summary>Spende [amount] se il saldo lo consente. True se ok.</summary>
        public static bool TrySpend(int amount)
        {
            if (amount <= 0) return true;
            if (!CanAfford(amount)) return false;
            Money -= amount;
            WalletManager.EnqueueDelta(-amount);
            return true;
        }

        /// <summary>Spende [amount] (senza controllo saldo, mai sotto zero).</summary>
        public static void Spend(int amount)
        {
            if (amount <= 0) return;
            Money -= amount;
            WalletManager.EnqueueDelta(-amount);
        }

        /// <summary>Accredita [amount] al wallet.</summary>
        public static void Earn(int amount)
        {
            if (amount <= 0) return;
            Money += amount;
            WalletManager.EnqueueDelta(+amount);
        }

        // ─── hook interni per WalletManager ─────────────────────
        /// <summary>Adotta il saldo AUTHORITATIVE tornato dal server (load o
        /// riconciliazione). Azzera il delta pendente.</summary>
        internal static void ApplyServerBalance(int money)
        {
            Money = money;
            // il delta pendente e' stato incorporato nel saldo authoritative
            WalletManager.ResetPendingDelta();
        }

        /// <summary>Saldo ri-lettura da PlayerPrefs (offline seed).</summary>
        internal static int LocalCache
        {
            get { return Mathf.Max(0, PlayerPrefs.GetInt(Key, StartMoney)); }
        }
    }
}
using UnityEngine;

namespace City.Environment
{
    /// <summary>
    /// Contatore di "sospetto" per il caos volontario (cestini calciati,
    /// vetrine rotte, pedoni travolti): ogni azione aggiunge punti e a
    /// soglia raggiunta arriva la multa con relativo toast.
    /// </summary>
    public static class ChaosTracker
    {
        private const string Key = "city_suspicion";
        private const int Threshold = 4;

        public static int Suspicion
        {
            get { return PlayerPrefs.GetInt(Key, 0); }
        }

        public static void AddChaos(int pts)
        {
            int s = Suspicion + Mathf.Max(1, pts);
            if (s >= Threshold)
            {
                int fine = 15 * (1 + UnityEngine.Random.Range(0, 2));   // 15 o 25
                // senza soldi la multa resta impagata: nessun debito
                if (City.World.Wallet.Money >= fine)
                    City.World.Wallet.Spend(fine);
                s -= Threshold;
                Toast("\ud83d\udea8 Telecamere della citta': multa di " +
                    fine + "\u20ac per atti vandalici!");
            }
            PlayerPrefs.SetInt(Key, s);
            PlayerPrefs.Save();
        }

        private static void Toast(string msg)
        {
            if (City.Game.Instance != null && City.Game.Instance.ui != null)
                City.Game.Instance.ui.ShowToast(msg);
        }
    }
}

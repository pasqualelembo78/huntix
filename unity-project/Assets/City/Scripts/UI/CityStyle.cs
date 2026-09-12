using UnityEngine;

namespace City.UI
{
    /// <summary>
    /// Selettore di stile degli edifici: se UseQuaternius e' true gli edifici
    /// non visitabili usano i modelli high-quality Quaternius (facciate
    /// texturizzate), altrimenti tornano ai prefab Kenney. Il valore e'
    /// persistito in PlayerPrefs e puo' essere commutato a caldo dall'Hamburger
    /// menu oppure all'avvio. Richiede il rigenerare dei chunk per applicarlo.
    /// </summary>
    public static class CityStyle
    {
        private const string Key = "huntix.citystyle.quaternius";

        public static bool UseQuaternius
        {
            get { return PlayerPrefs.GetInt(Key, 1) != 0; }
            set { PlayerPrefs.SetInt(Key, value ? 1 : 0); }
        }
    }
}

using UnityEngine;

namespace City.UI
{
    /// <summary>
    /// Selettore di stile degli edifici: se UseQuaternius e' true gli edifici
    /// usano i modelli high-quality Quaternius (facciate texturizzate, FBX
    /// ~11x piu' grandi e 29 MB di texture in piu' rispetto al kit Kenney),
    /// altrimenti tornano ai prefab Kenney leggeri del ramo `unity`, che
    /// girava fluido.
    ///
    /// DEFAULT = Kenney (false) per la fluidita'. Il giocatore puo' tornare ai
    /// Quaternius con UN CLICK dal menu hamburger ("Stile edifici"): la scelta
    /// viene persistita in PlayerPrefs e il menu rigenera subito i chunk.
    /// </summary>
    public static class CityStyle
    {
        private const string Key = "huntix.citystyle.quaternius";

        public static bool UseQuaternius
        {
            get { return PlayerPrefs.GetInt(Key, 0) != 0; }
            set { PlayerPrefs.SetInt(Key, value ? 1 : 0); }
        }

        /// <summary>Cambia stile e persiste subito il valore.</summary>
        public static void Set(bool useQuaternius)
        {
            UseQuaternius = useQuaternius;
            PlayerPrefs.Save();
        }
    }
}

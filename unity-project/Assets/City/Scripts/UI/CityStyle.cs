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
    /// DEFAULT = Kenney (false) per la fluidita'. Il caricamento Quaternius e'
    /// stato riabilitato su richiesta del proprietario (24/11): il pulsante del
    /// menu hamburger torna a commutare i due kit e ricarica la mappa.
    /// </summary>
    public static class CityStyle
    {
        private const string Key = "huntix.citystyle.quaternius";

        /// <summary>Riabilitato il 24/11 su richiesta esplicita del proprietario,
        /// per poter provare Quaternius su dispositivo. Nessun asset e' stato
        /// eliminato: basta il pulsante "Stile edifici" nel menu hamburger.
        /// Il default resta Kenney (PlayerPrefs assente = 0): cambiare il
        /// predefinito richiede una decisione separata dopo le misure.</summary>
        public const bool QuaterniusAllowed = true;

        public static bool CanUseQuaternius { get { return QuaterniusAllowed; } }

        public static bool UseQuaternius
        {
            get { return QuaterniusAllowed && PlayerPrefs.GetInt(Key, 0) != 0; }
            set { PlayerPrefs.SetInt(Key, value && QuaterniusAllowed ? 1 : 0); }
        }

        /// <summary>Cambia stile e persiste subito il valore.</summary>
        public static void Set(bool useQuaternius)
        {
            UseQuaternius = useQuaternius;
            PlayerPrefs.Save();
        }
    }
}

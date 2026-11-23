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
    /// inoltre inibito da QuaterniusAllowed=false: asset e codice restano nel
    /// progetto, ma non vengono usati nemmeno se un vecchio PlayerPrefs dice 1.
    /// Il menu hamburger mostra lo stato e spiega perche' il toggle e' chiuso.
    /// </summary>
    public static class CityStyle
    {
        private const string Key = "huntix.citystyle.quaternius";

        /// <summary>Interruttore di sicurezza: i prefab e le texture Quaternius
        /// RESTANO nel progetto e il codice di caricamento resta intatto, ma non
        /// vengono mai caricati finche' questo valore e' false. Serve a evitare
        /// che un vecchio PlayerPrefs (=1) riporti i 29 MB di texture high-quality
        /// su un dispositivo che aveva gia' scelto Quaternius. Per riabilitarli
        /// basta portare questo valore a true: nessun asset viene eliminato.</summary>
        public const bool QuaterniusAllowed = false;

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

using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Modalita' altimetrica del mondo OSM.
    ///
    /// Dopo mesi di problemi di "player sospeso in aria / sprofondato" nella
    /// citta', il progetto e' tornato al comportamento del ramo `unity`
    /// (altitudine zero ovunque, personaggio con i piedi per terra). Il codice
    /// DEM/SRTM completo NON e' stato cancellato: resta compilato ma spento.
    ///
    /// Per riattivare l'altitudine reale (seconda chance, ramo `altitudine`):
    /// cambiare il valore di `WorldHeights` qui sotto e ricompilare.
    /// Il ramo git `altitudine` (tag backup-altitudine-20260923) conserva
    /// l'implementazione integrale della quota DEM.
    /// </summary>
    public enum WorldHeightMode
    {
        /// <summary>
        /// TUTTE le quote a 0 (comportamento storico del ramo unity):
        /// strade, terreno, edifici, spawn, NPC e veicoli allo stesso piano.
        /// Nessuna altitudine: il CharacterController cammina sempre sul
        /// piano fisico, zero fluttuazione.
        /// </summary>
        Flat = 0,

        /// <summary>
        /// Altimetria reale DEM/SRTM (comportamento ramo `altitudine`).
        /// Terreno e strade seguono il dislivello vero del territorio.
        /// </summary>
        Dem = 1,

        /// <summary>
        /// IBRIDO futuro (montagne): pianure/colline a quota 0, montagne reali
        /// solo SOPRA la soglia `MountainThresholdM`. Predisposto ma disattivo:
        /// serve rifinire lo smussamento dei confini piatto-montagna prima di
        /// usarlo per la camminata.
        /// </summary>
        Threshold = 2
    }

    public static class CityConfig
    {
        /// <summary>SINGOLO INTERRUTTORE dell'altimetria. Cambiare qui per
        /// passare da mondo piatto a DEM reale (o montagne ibride).</summary>
        public const WorldHeightMode WorldHeights = WorldHeightMode.Flat;

        /// <summary>Soglia (m s.l.m.) oltre la quale in modalita' Threshold il
        /// territorio torna reale: sotto resta a 0.</summary>
        public const float MountainThresholdM = 120f;

        /// <summary>Applica la modalita' attuale a una quota DEM grezza (m s.l.m.).
        /// UNICO punto di conversione: terreno, strade, edifici, spawn, NPC,
        /// veicoli e uova campionano tutti qui, cosi' collider fisico e
        /// superficie visiva non possono mai divergere (causa storica di
        /// player sospeso/affondato).</summary>
        public static float ApplyMode(float dem)
        {
            switch (WorldHeights)
            {
                case WorldHeightMode.Flat:
                    return 0f;
                case WorldHeightMode.Threshold:
                    return dem > MountainThresholdM ? dem : 0f;
                default:
                    return dem;
            }
        }
    }
}
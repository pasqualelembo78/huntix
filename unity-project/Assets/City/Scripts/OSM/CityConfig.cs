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
    /// 2026-09-30 — le CAUSE dei problemi sono state trovate e corrette, quindi
    /// `Dem` e' di nuovo una scommessa sensata (non piu' un rischio):
    ///  1. lo snap al terreno prendeva l'hit PIU' ALTO della colonna invece del
    ///     piu' vicino (piazzava il player sui tetti) — ora un solo helper,
    ///     `CityChunkedWorld.TryGroundColumn`;
    ///  2. la griglia DEM era campionata ogni 1000 m, cio' una cella per chunk:
    ///     ogni chunk era un piano con una piega secca al confine. Ora e' a
    ///     250 m e allineata ai confini dei chunk (5x5 celle per chunk);
    ///  3. i void SRTM finivano come 0 = livello del mare (scogliere
    ///     artificiali sulle tile costiere). Ora si riempiono col vicino;
    ///  4. il terreno era una mesh a 10 m (20k triangoli di collider per chunk,
    ///     senza LOD) e le tile senza DEM costruivano il "terreno" con le
    ///     ALTEZZE DEGLI EDIFICI. Entrambe risolte.
    ///
    /// Per riattivare l'altitudine reale: `WorldHeights = WorldHeightMode.Dem`
    /// qui sotto e ricompilare. I dati lato server sono gia' rigenerati nel
    /// formato nuovo (vedi AGENTS.md, "Griglia DEM: passo e allineamento").
    /// Attenzione a `TileClient.CacheVersion`: cambiare formato geo senza
    /// bumparlo fa riusare ai device le `.v*.geo.json` vecchie in memoria.
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
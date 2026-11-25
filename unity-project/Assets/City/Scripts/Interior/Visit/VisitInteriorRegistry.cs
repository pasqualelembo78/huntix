using System;
using System.Globalization;

namespace City.Interior.Visit
{
    /// <summary>
    /// Catalogo configurabile delle case visitabili collegate a edifici OSM reali.
    ///
    /// Il registro è l'unico punto dove si dichiara QUALE id OSM diventa una casa
    /// apribile: tutti gli altri edifici conservano il comportamento attuale
    /// (prefab esterno + interno universale). Aggiungere una casa = aggiungere
    /// una `Spec`, senza toccare geo/graph, TileClient.CacheVersion o i builder
    /// esistenti. Le tipologie riutilizzano `UniversalInteriorRegistry.Kinds`,
    /// così nuove categorie (bar, ristorante, ufficio, hotel...) funzionano
    /// con lo stesso meccanismo.
    ///
    /// Riflesso della caccia alle uova (Mandato): ogni casa dichiara i propri
    /// nascondigli con posizione testuale "casa→stanza→contenitore".
    /// </summary>
    public static class VisitInteriorRegistry
    {
        public const string Tag = "VisitInterior";
        public const string PrefabResource = "HouseInteriors/CasaVisitabileCC0";

        [Serializable]
        public sealed class Spec
        {
            public long osmId;
            public string kind = "casa";
            public string label = "Casa visitabile";
            public string note = "";
            // Coordinate della PORTA (non del centro dell'edificio): sono
            // quelle che la mappa espansa usa per TELEPORTA/FRECCETTA.
            // La porta e' sul lato +Z locale (rotazione 318°), ~6,9 m
            // a nord-ovest dal centro OSM.
            public double lat;
            public double lng;
            public double centerLat;
            public double centerLng;
        }

        /// <summary>
        /// Case attivate. La prima è un edificio reale di Roma (id OSM
        /// 204023010, centro 41.90601 / 12.49579, impronta 21,3 x 13,5 m,
        /// chunk IT_087_061 / (878,619), ~360 m dallo spawn predefinito).
        /// </summary>
        private static readonly Spec[] Configured =
        {
            new Spec
            {
                osmId = 204023010L,
                kind = "casa",
                label = "Casa di Roma",
                note = "Via Flavia 3, Sallustiano, Roma — edificio OSM 204023010; "
                     + "porta lato nord-ovest; soggiorno, cucina, camera, bagno",
                lat = 41.906056,
                lng = 12.495734,
                centerLat = 41.90601,
                centerLng = 12.49579
            }
        };

        public static Spec[] All { get { return Configured; } }

        public static bool TryGet(long osmId, out Spec spec)
        {
            for (int i = 0; i < Configured.Length; i++)
            {
                if (Configured[i] == null || Configured[i].osmId != osmId) continue;
                spec = Configured[i];
                return true;
            }
            spec = null;
            return false;
        }

        public static bool IsConfigured(long osmId)
        {
            Spec unused;
            return TryGet(osmId, out unused);
        }

        /// <summary>Chiave stabile dell'edificio, indipendente dal rebase del
        /// mondo e dalla rigenerazione dei chunk (usa l'id OSM, non l'istanza).</summary>
        public static string StableKey(long osmId)
        {
            return "casa-osm-" + osmId.ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>Le categorie supportate sono quelle già note al catalogo
        /// universale: il prefab della casa resta riutilizzabile per tutte.</summary>
        public static bool IsSupportedKind(string kind)
        {
            return UniversalInteriorRegistry.IsKnown(kind);
        }

        /// <summary>
        /// Punto di accesso serializzato per il ponte Android: posizione della
        /// porta, etichetta e nascondigli. Viaggia nello snapshot CityStateSync
        /// (houseTargets) e viene ritrasmesso a Unity con "setTargetCoord"
        /// quando l'app chiede "vai alla casa".
        /// </summary>
        public static string TargetsJson()
        {
            var sb = new System.Text.StringBuilder("[");
            for (int i = 0; i < Configured.Length; i++)
            {
                Spec s = Configured[i];
                if (s == null) continue;
                if (sb.Length > 1) sb.Append(',');
                sb.Append("{\"key\":\"").Append(Escape(StableKey(s.osmId)))
                  .Append("\",\"osmId\":").Append(s.osmId.ToString(CultureInfo.InvariantCulture))
                  .Append(",\"kind\":\"").Append(Escape(s.kind ?? "casa"))
                  .Append("\",\"label\":\"").Append(Escape(s.label ?? ""))
                  .Append("\",\"lat\":").Append(s.lat.ToString("R", CultureInfo.InvariantCulture))
                  .Append(",\"lng\":").Append(s.lng.ToString("R", CultureInfo.InvariantCulture))
                  .Append(",\"centerLat\":").Append(s.centerLat.ToString("R", CultureInfo.InvariantCulture))
                  .Append(",\"centerLng\":").Append(s.centerLng.ToString("R", CultureInfo.InvariantCulture))
                  .Append(",\"note\":\"").Append(Escape(s.note ?? ""))
                  .Append("\"}");
            }
            sb.Append(']');
            return sb.ToString();
        }

        private static string Escape(string value)
        {
            return (value ?? "").Replace("\\", "\\\\").Replace("\"", "\\\"");
        }
    }
}

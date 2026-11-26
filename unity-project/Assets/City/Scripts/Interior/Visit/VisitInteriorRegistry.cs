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

        /// <summary>Casa consigliata dal pulsante CASA: la copia di Foggia,
        /// vicina allo spawn. Roma resta nel registro ed e' selezionabile
        /// dall'elenco destinazioni (osmId esplicito).</summary>
        public const long DefaultOsmId = 405228771L;

        [Serializable]
        public sealed class Spec
        {
            public long osmId;
            public string kind = "casa";
            public string label = "Casa visitabile";
            public string note = "";
            // Coordinate della PORTA (non del centro dell'edificio): sono
            // quelle che la mappa espansa usa per TELEPORTA/FRECCETTA e la
            // fonte di verita' per scegliere il lato del portale nel mondo.
            // Non si deduce piu' dalla rotazione del prefab: per Foggia il
            // lato +Z locale dava una porta a nord-est, mentre quella vera
            // e' a ovest verso Via Silvio Pellico.
            public double lat;
            public double lng;
            public double centerLat;
            public double centerLng;
            /// <summary>Roma conserva gli id uovo storici: i salvataggi già
            /// esistenti non vedono le uova rimontare. Le case nuove usano id
            /// con prefisso OSM, così ogni casa ha le PROPRIE uova.</summary>
            public bool legacyEggIds;
        }

        /// <summary>
        /// Case attivate. La prima è la copia di Foggia (Via Silvio Pellico 53)
        /// voluta per avere la casa navigabile vicina al giocatore; Roma resta
        /// la seconda Spec, intatta e selezionabile dalla stessa mappa.
        /// </summary>
        private static readonly Spec[] Configured =
        {
            new Spec
            {
                osmId = 405228771L,
                kind = "casa",
                label = "Casa di Foggia",
                note = "Via Silvio Pellico 53, Foggia — edificio OSM 405228771; "
                     + "porta sul lato ovest verso Via Silvio Pellico; "
                     + "soggiorno, cucina, camera, bagno",
                lat = 41.4530015,
                lng = 15.5344424,
                centerLat = 41.4530382,
                centerLng = 15.5346125
            },
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
                centerLng = 12.49579,
                legacyEggIds = true
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

        /// <summary>Direzione della porta rispetto al centro dell'edificio,
        /// espressa nel sistema locale del prefab (metri, X=east/Z=north
        /// ruotati di -yaw). La usa BuildingPlacer per mettere il portale sul
        /// lato indicato dalle coordinate, non sempre sul +Z locale: il +Z
        /// della rotazione OSM puntava dalla parte sbagliata della casa.</summary>
        public static bool TryDoorLocalOffset(Spec spec, float yawDeg,
            out float x, out float z)
        {
            x = 0f;
            z = 0f;
            if (spec == null) return false;
            double midLat = (spec.lat + spec.centerLat) * 0.5;
            double east = (spec.lng - spec.centerLng) * 111320.0 *
                          Math.Cos(midLat * Math.PI / 180.0);
            double north = (spec.lat - spec.centerLat) * 110540.0;
            double rad = yawDeg * Math.PI / 180.0;
            double c = Math.Cos(rad), s = Math.Sin(rad);
            x = (float)(east * c - north * s);
            z = (float)(east * s + north * c);
            return Math.Abs(x) + Math.Abs(z) > 0.01;
        }

        /// <summary>Chiave stabile dell'edificio, indipendente dal rebase del
        /// mondo e dalla rigenerazione dei chunk (usa l'id OSM, non l'istanza).</summary>
        public static string StableKey(long osmId)
        {
            return "casa-osm-" + osmId.ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>Id dell'uovo nel nascondiglio: stabile per edificio e
        /// NASCONDIGLIO, così due case non condividono lo stesso premio. Roma
        /// mantiene gli id storici (save già esistenti intatti); Foggia e le
        /// case future usano il prefisso OSM. Nessun premio duplicato.</summary>
        public static string EggId(long osmId, string baseId)
        {
            if (string.IsNullOrEmpty(baseId)) return baseId;
            Spec spec;
            if (TryGet(osmId, out spec) && spec != null && spec.legacyEggIds)
                return baseId;
            return StableKey(osmId) + ":" + baseId;
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

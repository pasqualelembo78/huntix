using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Zona interattiva di un POI veicolo estratto da OSM:
    ///   • Dealer  — concessionaria: unico luogo dove comprare/vendere auto
    ///   • Repair  — officina: riparazioni e installazione antifurti
    ///   • Garage  — ricovero auto: al coperto i furti sono impossibili
    /// Il trigger avvisa Game (stesso schema di InteractDoor) e il prompt
    /// a schermo apre l'UI corrispondente con il tasto di interazione.
    /// </summary>
    [RequireComponent(typeof(Collider))]
    public class VehiclePoiZone : MonoBehaviour
    {
        public enum PoiKind { Dealer, Repair, Garage }

        public PoiKind kind;
        public string poiId = "";
        public string poiName = "";

        /// <summary>Punto dove compare l'auto comprata/ritirata/consegnata.</summary>
        public Transform deliveryPoint;

        private bool focused;

        public bool IsFocused { get { return focused; } }

        public string Label
        {
            get
            {
                string nm = string.IsNullOrEmpty(poiName)
                    ? DefaultName() : poiName;
                switch (kind)
                {
                    case PoiKind.Dealer: return "\uD83D\uDE97 CONCESSIONARIA - " + nm;
                    case PoiKind.Repair: return "\uD83D\uDD27 OFFICINA - " + nm;
                    default: return "\uD83C\uDE51 GARAGE - " + nm;
                }
            }
        }

        public string DefaultName()
        {
            return kind == PoiKind.Dealer ? "Auto Usato e Nuovo"
                : kind == PoiKind.Repair ? "Riparazioni"
                : "Parcheggio Coperto";
        }

        private void Start()
        {
            // registra la posizione nel registro client (per "officina piu'
            // vicina" e distanze garage): la posizione world e' gia' valida
            // perche' il root chunk e' stato posizionato prima della Populate
            GeoCoord g = WorldOrigin.ToGeo(deliveryPoint != null
                ? deliveryPoint.position : transform.position);
            VehiclePoiRegistry.Register(this, g);
        }

        private void OnDisable()
        {
            // chunk scaricato o LOD spento mentre il player e' dentro:
            // rilascia il focus o il prompt resterebbe bloccato
            if (focused && Game.Instance != null)
            {
                focused = false;
                Game.Instance.OnPoiZoneFocusChanged(this);
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            Game.Instance.OnPoiZoneFocusChanged(this);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            Game.Instance.OnPoiZoneFocusChanged(this);
        }

        public void Interact()
        {
            switch (kind)
            {
                case PoiKind.Dealer:
                    DealershipUI.Open(this);
                    break;
                case PoiKind.Repair:
                    OfficinaUI.Open(this);
                    break;
                default:
                    GarageUI.Open(this);
                    break;
            }
        }

        // ── helper statici di contesto ─────────────────────────────

        /// <summary>Zona su cui il player ha attualmente focus (o null).</summary>
        public static VehiclePoiZone FocusedZone
        {
            get { return Game.Instance != null ? Game.Instance.CurrentPoiZone : null; }
        }

        /// <summary>Il player e' dentro una zona del tipo indicato.</summary>
        public static bool PlayerIn(PoiKind kind)
        {
            var z = FocusedZone;
            return z != null && z.kind == kind;
        }
    }
}

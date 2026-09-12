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
        public enum PoiKind { Dealer, Repair, Garage, Hospital, Ramp, School, Bar, Bank, Fuel }

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
                    case PoiKind.Dealer: return "CONCESSIONARIA - " + nm;
                    case PoiKind.Repair: return "OFFICINA - " + nm;
                    case PoiKind.Hospital: return "OSPEDALE - " + nm;
                    case PoiKind.Ramp: return "SOTTERRANEO - " + nm;
                    case PoiKind.School: return "SCUOLA - " + nm;
                    case PoiKind.Bar: return "BAR - " + nm;
                    case PoiKind.Bank: return "BANCA - " + nm;
                    case PoiKind.Fuel: return "DISTRIBUTORE - " + nm;
                    default: return "GARAGE - " + nm;
                }
            }
        }

        public string DefaultName()
        {
            return kind == PoiKind.Dealer ? "Auto Usato e Nuovo"
                : kind == PoiKind.Repair ? "Riparazioni"
                : kind == PoiKind.Hospital ? "Ospedale"
                : kind == PoiKind.School ? "Scuola"
                : kind == PoiKind.Bar ? "Bar"
                : kind == PoiKind.Bank ? "Banca / ATM"
                : kind == PoiKind.Fuel ? "Distributore Benzina"
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
            ForceUnfocus();
        }

        /// <summary>Rilascia il focus a forza: col collider del player spento
        /// in auto/taxi OnTriggerExit non scatta mai e il prompt + la camera
        /// indoor (owner "vehicle-poi") resterebbero bloccati ovunque.</summary>
        public void ForceUnfocus()
        {
            if (!focused)
            {
                ClearCameraOverrides();
                return;
            }
            focused = false;
            if (Game.Instance != null)
                Game.Instance.OnPoiZoneFocusChanged(this);
            ClearCameraOverrides();
        }

        /// <summary>Rilascia il focus di tutte le zone POI aperte (usato in
        /// ingresso veicolo/taxi).</summary>
        public static void ReleaseFocusedZone()
        {
            var zones = UnityEngine.Object.FindObjectsOfType<VehiclePoiZone>();
            for (int i = 0; i < zones.Length; i++)
                if (zones[i] != null) zones[i].ForceUnfocus();
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = true;
            Game.Instance.OnPoiZoneFocusChanged(this);
            ApplyCameraOverrides();
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            ForceUnfocus();
        }

        /// <summary>
        /// Interni a "casa aperta" (concessionaria/officina/garage): la camera
        /// terza persona si avvicina al player così entra nel capannone con lui
        /// invece di rimanere fuori a guardare il tetto.
        /// </summary>
        private void ApplyCameraOverrides()
        {
            if (kind != PoiKind.Dealer && kind != PoiKind.Repair &&
                kind != PoiKind.Garage)
                return;
            var rig = Game.Instance != null ? Game.Instance.rig : null;
            if (rig == null) return;
            // parametri stretti per stare dentro il capannone
            float dist = kind == PoiKind.Garage ? 4.8f : 5.2f;
            rig.SetIndoorOverride(true, "vehicle-poi", dist, 2.4f, 22f);
        }

        private void ClearCameraOverrides()
        {
            var rig = Game.Instance != null ? Game.Instance.rig : null;
            if (rig == null) return;
            rig.SetIndoorOverride(false, "vehicle-poi");
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
                case PoiKind.Ramp:
                    UndergroundUI.Enter(this);
                    break;
                case PoiKind.Fuel:
                    RefuelVehicle();
                    break;
                default:
                    GarageUI.Open(this);
                    break;
            }
        }

        /// <summary>Riempie il serbatoio del veicolo corrente (o di quello
        /// piu vicino) se il player e' in una zona distributore benzina.</summary>
        private void RefuelVehicle()
        {
            var game = Game.Instance;
            if (game == null) return;

            // se e' in guida, riempie quello che guida
            VehicleController vc = null;
            if (game.IsDriving && game.CurrentVehicle != null)
                vc = game.CurrentVehicle;
            else
            {
                // altrimenti cerca il veicolo piu vicino
                var player = game.player;
                if (player != null)
                {
                    float best = 15f;
                    foreach (var v in FindObjectsOfType<VehicleController>())
                    {
                        float d = Vector3.Distance(
                            player.transform.position, v.transform.position);
                        if (d < best) { best = d; vc = v; }
                    }
                }
            }

            if (vc == null)
            {
                if (game.ui != null)
                    game.ui.ShowToast("Nessun veicolo da rifornire vicino!");
                return;
            }

            if (vc.FuelPercent >= 99f)
            {
                if (game.ui != null)
                    game.ui.ShowToast("Serbatoio gia' pieno!");
                return;
            }

            float pct = vc.FuelPercent;
            int liters = Mathf.CeilToInt(
                (100f - pct) * 0.01f * VehicleController.FuelMax);
            int cost = Mathf.Max(1, Mathf.CeilToInt(liters * 0.5f));
            if (!City.World.Wallet.TrySpend(cost))
            {
                if (game.ui != null)
                    game.ui.ShowToast("Non hai abbastanza soldi per il pieno ("
                        + cost + " €).");
                return;
            }
            vc.Refuel(VehicleController.FuelMax);
            if (game.ui != null)
                game.ui.ShowToast("Serbatoio pieno! +" + liters + "L ("
                    + cost + " €)");
            OsmDiag.Log("[Vehicle] Rifornimento completato al distributore: " +
                (vc.data != null ? vc.data.vehicleName : vc.name));
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

        /// <summary>
        /// Vero se il player e' dentro una "casa aperta" veicolo (concessionaria,
        /// officina o garage): queste strutture NON hanno interno in prima persona
        /// (si entra a piedi in terza persona e si interagisce dal menu). Quando
        /// il player e' dentro una di esse, un eventuale BuildingEntrance che
        /// scatterebbe cambierebbe la vista in prima persona: va quindi ignorato.
        /// Controlla anche l'OverlapSphere per robustezza (focus puo' essersi
        /// perso se il trigger di zona non ha aggiornato currentPoiZone).
        /// </summary>
        public static bool PlayerInOpenRoomVehicle()
        {
            var z = FocusedZone;
            if (z != null &&
                (z.kind == PoiKind.Dealer || z.kind == PoiKind.Repair ||
                 z.kind == PoiKind.Garage))
                return true;

            var player = Game.Instance != null ? Game.Instance.player : null;
            if (player != null)
            {
                var hits = UnityEngine.Physics.OverlapSphere(
                    player.transform.position, 3f,
                    ~0, QueryTriggerInteraction.Collide);
                for (int i = 0; i < hits.Length; i++)
                {
                    var zone = hits[i].GetComponent<VehiclePoiZone>();
                    if (zone != null && zone.enabled &&
                        (zone.kind == PoiKind.Dealer || zone.kind == PoiKind.Repair ||
                         zone.kind == PoiKind.Garage))
                        return true;
                }
            }
            return false;
        }
    }
}

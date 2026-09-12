using System.Collections;
using UnityEngine;
using City.UI;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    [RequireComponent(typeof(Collider))]
    public class BuildingEntrance : MonoBehaviour
    {
        public string buildingName = "Edificio";
        public string buildingType = "house";
        public float buildingWidth = 8f;
        public float buildingDepth = 8f;
        public float buildingHeight = 8f;
        public int floorCount = 1;
        public Shop shop;

        /// <summary>Zona POI veicolo associata (concessionaria/officina/garage):
        /// passata all'interno per il bancone che apre i menu veicolo.</summary>
        public City.Vehicle.VehiclePoiZone poiZone;

        /// <summary>True se l'edificio è costruito IN-PLACE (guscio reale con
        /// porta e finestre): si entra/esce attraversando fisicamente la porta,
        /// senza tooltip/tap/teletrasporto.</summary>
        public bool inPlace;

        /// <summary>Per gli ingresso in-place: true per il trigger TUTTO DENTRO
        /// (che marchia il giocatore come "dentro"), false per il trigger TUTTO
        /// FUORI (che lo marchia "fuori" quando riattraversa la porta).</summary>
        public bool isExit;

        private bool focused;
        public bool IsFocused => focused;

        public bool IsShop => buildingType == "shop";

        private bool autoEntryStarted;
        private bool _enteringPrefab;

        private static void LogEntrance(string msg)
        {
            Debug.Log("[BuildingEntrance] " + msg);
            UnityBridge.LogToAndroid("BuildingEntrance", msg);
        }

        private void Awake()
        {
            var rb = gameObject.GetComponent<Rigidbody>();
            if (rb == null)
            {
                rb = gameObject.AddComponent<Rigidbody>();
                rb.isKinematic = true;
                rb.useGravity = false;
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;

            // Player NON ancora spawnato (gate di avvio: CC spento, in attesa
            // dei primi chunk): ignora i trigger porta. Un edificio puo' coprire
            // l'origine e il suo trigger d'ingresso includere lo spawn, marcando
            // il giocatore come "dentro" una casa prima ancora di muoversi
            // (finto ingresso automatico alla partenza).
            var cw = City.OSM.CityChunkedWorld.Instance;
            if (cw != null && !cw.PlayerSpawned) return;

            var mgr = InteriorManager.Instance;

            // Edificio IN-PLACE: il passaggio fisico attraverso la porta è
            // l'ingresso/uscita. Il trigger "entry" (dentro) marca dentro, il
            // trigger "exit" (fuori) marca fuori quando ci si affaccia.
            if (inPlace)
            {
                if (isExit)
                {
                    // Teleport alla soglia con CC riabilitato: si attraversano
                    // entrambi i trigger insieme → falso "uscito" un attimo dopo
                    // l'ingresso. Se il player è ancora chiaramente DENTRO
                    // l'impronta non è uscito davvero (l'uscita vera viene
                    // comunque rilevata dal ticker geometrico oltre il muro).
                    var gen = GetComponentInParent<InteriorGenerator>();
                    if (gen != null && gen.DeepInside(other.transform.position))
                        return;
                    if (mgr != null && mgr.IsInside) mgr.MarkOutside(this);
                }
                else
                {
                    if (mgr != null && !mgr.IsInside) mgr.MarkInside(this);
                }
                return;
            }

            if (mgr != null && mgr.IsInside)
            {
                focused = false;
                if (Game.Instance != null)
                    Game.Instance.OnEntranceFocusChanged(this);
                return;
            }
            LogEntrance("OnTriggerEnter: " + buildingName + " (" + buildingType + ")");
            if (autoEntryStarted) return;
            focused = true;
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(this);
            ApplyCameraOverride(true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (inPlace) return; // l'uscita è gestita dal trigger "exit" fuori

            focused = false;
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(this);
            ApplyCameraOverride(false);
        }

        public void StartAutoEntry()
        {
            if (inPlace) return;
            if (autoEntryStarted) return;
            autoEntryStarted = true;
            Enter();
            autoEntryStarted = false;
        }

        public void Interact()
        {
            if (inPlace)
            {
                LogEntrance("in-place: si entra attraversando la porta, tappare non serve (" + buildingName + ")");
                return;
            }
            if (autoEntryStarted) return;
            Enter();
        }

        private void Enter()
        {
            if (City.Vehicle.VehiclePoiZone.PlayerInOpenRoomVehicle())
            {
                LogEntrance("ingresso ignorato: dentro zona aperta veicolo (concessionaria/officina/garage, vista 3a persona)");
                return;
            }
            if (inPlace)
            {
                LogEntrance("in-place: si entra attraversando la porta, tappare non serve (" + buildingName + ")");
                return;
            }
            // Edifici PREFAB (Quaternius): interno reale nel mondo, ingresso con
            // fade scenografico (esterno nascosto, player dentro l'impronta).
            var gen = GetComponentInParent<InteriorGenerator>();
            if (gen == null || !gen.IsPrefabExterior)
            {
                LogEntrance("entrata non supportata: edificio senza interno prefab (" + buildingName + ")");
                return;
            }
            StartCoroutine(EnterPrefab(gen));
        }

        private IEnumerator EnterPrefab(InteriorGenerator gen)
        {
            // flag anti doppio-ingresso: un doppio tap su "APRI" (o un tap +
            // auto-entry nello stesso frame) avviava due coroutine sullo stesso
            // ScreenFader -> doppio teleport e possibile fade nero bloccato.
            if (_enteringPrefab) yield break;
            _enteringPrefab = true;
            try
            {
                Game g = Game.Instance;
                if (g == null || g.player == null) yield break;
                var mgr = InteriorManager.Instance;
                if (mgr != null && mgr.IsInside) yield break;

                var fader = g.fader;
                if (fader != null) fader.gameObject.SetActive(true);
                if (fader != null) fader.FadeToBlack(null);

                if (!gen.InteriorBuilt) gen.BuildInteriorNow();
                gen.SetPrefabExteriorVisible(false);

                // dentro l'impronta, poco oltre la soglia, rivolto verso la stanza
                Vector3 pos = gen.transform.TransformPoint(gen.PrefabEnterLocal);
                Quaternion rot = gen.transform.rotation * Quaternion.Euler(0f, 180f, 0f);
                SetPlayerPos(pos, rot, g);

                if (mgr != null) mgr.MarkInside(this);
                if (fader != null)
                {
                    yield return new WaitForSeconds(fader.duration);
                    fader.FadeFromBlack(null);
                    yield return new WaitForSeconds(fader.duration);
                    fader.gameObject.SetActive(false);
                }
            }
            finally
            {
                _enteringPrefab = false;
            }
        }

        private static void SetPlayerPos(Vector3 pos, Quaternion rot, Game g)
        {
            var player = g.player;
            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = pos;
            player.transform.rotation = rot;
            if (cc != null) cc.enabled = true;
            player.Stop();
            if (g.rig != null) g.rig.SetYaw(rot);
        }

        private void ApplyCameraOverride(bool active)
        {
            if (City.Vehicle.VehiclePoiZone.PlayerInOpenRoomVehicle()) return;
            var rig = Game.Instance != null ? Game.Instance.rig : null;
            if (rig == null) return;
            float dist = 6.0f;
            float height = 2.6f;
            float pitch = 20f;
            rig.SetIndoorOverride(active, "building", dist, height, pitch);
        }

        private void OnDisable()
        {
            if (focused) ApplyCameraOverride(false);
        }
    }
}
